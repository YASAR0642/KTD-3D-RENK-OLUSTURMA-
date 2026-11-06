package com.ktd.analyzer.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Geometrik manyetik dipol ters çözümünün deneysel, denetlenebilir uygulaması.
 * Sadece tarama boyunca aynı sabit eksende ölçülmüş genlikler kullanılır.
 * Bilinmeyen hedefin manyetik momenti ve zemin özellikleri bu ölçümden ayrıştırılamaz.
 * Çıktı koşullu model tahminidir; fiziksel ölçüm veya oda/tünel teşhisi değildir.
 */
public final class MagneticDepthEstimator {
    private MagneticDepthEstimator() {}
    private static final int P=6;
    private static final int MIN_SAMPLES=9;

    public enum State { ESTIMATE, INSUFFICIENT, SATURATED, UNRESOLVED, POOR_FIT }
    public static final class Result {
        public final State state;
        public final double depthMeters, lowMeters, highMeters, xMeters, yMeters;
        public final double explainedVariance, shapeGain, surveyWidthMeters;
        public final int samples;
        public final String reason;
        private Result(State state,String reason,int n,double depth,double low,double high,
                       double x,double y,double explained,double gain,double width){
            this.state=state;this.reason=reason;this.samples=n;this.depthMeters=depth;
            this.lowMeters=low;this.highMeters=high;this.xMeters=x;this.yMeters=y;
            this.explainedVariance=explained;this.shapeGain=gain;this.surveyWidthMeters=width;
        }
        public boolean hasEstimate(){return state==State.ESTIMATE;}
        public String summaryTR(){
            if(!hasEstimate())return "Derinlik belirlenemedi: "+reason;
            return String.format(Locale.forLanguageTag("tr-TR"),
                "Koşullu dipol-model tahmini: %.2f m (model aralığı %.2f–%.2f m). " +
                "Konum X %.2f m / Y %.2f m. Model uyumu %% %.0f; zemin eğilimine göre iyileşme %% %.0f. " +
                "Bu bir kazı derinliği ölçümü değildir.",depthMeters,lowMeters,highMeters,xMeters,yMeters,
                explainedVariance*100.0,shapeGain*100.0);
        }
    }
    private static Result fail(State state,String reason,int n,double width){
        return new Result(state,reason,n,Double.NaN,Double.NaN,Double.NaN,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,width);
    }

    private static final class Sample {
        final double x,y,value;
        Sample(double x,double y,double z){this.x=x;this.y=y;this.value=z;}
    }
    private static final class Fit {
        double sse,depth,cx,cy;
        double[] coef;
        Fit(double err,double d,double x,double y,double[] c){sse=err;depth=d;cx=x;cy=y;coef=c;}
    }

    public static Result analyze(ScanData d){
        if(d==null||d.rows<3||d.cols<3||d.count<MIN_SAMPLES)
            return fail(State.INSUFFICIENT,"Model analizi için en az 3×3 konumsal ölçüm gerekir; CSV ve 2D/3D görüntüleme kısıtlanmaz.",d==null?0:d.count,0);
        List<Sample> samples=new ArrayList<>();
        int negatives=0,upper=0,positive=0,zeroStart=0,startCount=0;
        boolean plausibleTenBit=true;
        for(int r=0;r<d.rows;r++)for(int c=0;c<d.cols;c++)if(d.filled[r][c]){
            float z=d.raw[r][c];if(!Float.isFinite(z))continue;
            if(z<0||z>1023.01f)plausibleTenBit=false;
            if(z<=1)negatives++;
            if(z>=1022&&z<=1023.01f)upper++;
            if(z>0)positive++;
            if(r==d.rows-1){startCount++;if(z==0f)zeroStart++;}
        }
        // X=0 sütunundaki sıfırın tüm hatlarda aynı olması, kaynak programın
        // placeholder verisi olabilir; bunu hedef anomalisi olarak işleme.
        boolean skipStartRow=zeroStart>=4&&zeroStart>=Math.ceil(startCount*.75);
        int ignoredStart=0;
        int clipped=0;
        double xmin=Double.POSITIVE_INFINITY,xmax=-Double.POSITIVE_INFINITY;
        double ymin=Double.POSITIVE_INFINITY,ymax=-Double.POSITIVE_INFINITY;
        for(int r=0;r<d.rows;r++)for(int c=0;c<d.cols;c++)if(d.filled[r][c]){
            float val=d.raw[r][c];if(!Float.isFinite(val))continue;
            if(skipStartRow&&r==d.rows-1&&val==0f){ignoredStart++;continue;}
            if(plausibleTenBit&&(val<=1||val>=1022))clipped++;
            double x=Float.isFinite(d.x[r][c])?d.x[r][c]:(d.rows-1-r)*d.stepCm/100.0;
            double y=Float.isFinite(d.y[r][c])?d.y[r][c]:c*d.stepCm/100.0;
            if(!Double.isFinite(x)||!Double.isFinite(y))continue;
            samples.add(new Sample(x,y,val));
            xmin=Math.min(xmin,x);xmax=Math.max(xmax,x);
            ymin=Math.min(ymin,y);ymax=Math.max(ymax,y);
        }
        int n=samples.size();
        double spanX=xmax-xmin,spanY=ymax-ymin;
        double maxSpan=Math.max(spanX,spanY),minSpan=Math.min(spanX,spanY);
        if(n<MIN_SAMPLES||!Double.isFinite(maxSpan)||minSpan<.85||maxSpan<1.0)
            return fail(State.INSUFFICIENT,"Alan küçük veya örnekleme yetersiz; koordinat/adım ölçüsünü kontrol edin.",n,maxSpan);
        if((n<64&&clipped>0) || (double)clipped/n>.06)
            return fail(State.SATURATED,"ADC sınırında veri var; küçük ızgarada bu durum derinlik tahminini bozabilir.",n,maxSpan);

        double[] values=new double[n];for(int i=0;i<n;i++)values[i]=samples.get(i).value;
        double[] sorted=values.clone();Arrays.sort(sorted);
        double median=sorted[n/2];double p10=sorted[n/10],p90=sorted[(n*9)/10];
        if(p90-p10<3)
            return fail(State.UNRESOLVED,"Anomali değişimi gürültü seviyesinden ayırt edilemiyor.",n,maxSpan);
        double centerX=(xmin+xmax)/2,centerY=(ymin+ymax)/2;
        double baseSse=fitPlaneSse(samples,centerX,centerY,maxSpan);
        if(!Double.isFinite(baseSse)||baseSse<1e-8)
            return fail(State.UNRESOLVED,"Ölçüm yalnız düz zemin değişimi gibi görünüyor.",n,maxSpan);
        double mean=0;for(double v:values)mean+=v;mean/=n;
        double totalSse=0;for(double v:values)totalSse+=(v-mean)*(v-mean);
        if(totalSse<=1e-8)return fail(State.UNRESOLVED,"Bütün sinyaller aynı.",n,maxSpan);

        final double minDepth=Math.max(.15,d.stepCm/100.0*.55);
        final double maxDepth=Math.min(30,maxSpan*.82);
        if(minDepth>=maxDepth)return fail(State.INSUFFICIENT,"Derinliği sınırlayacak yatay tarama alanı yok.",n,maxSpan);
        Fit best=null;
        List<Fit> fits=new ArrayList<>();
        for(int ix=1;ix<=9;ix++)for(int iy=1;iy<=9;iy++){
            double cx=xmin+spanX*ix/10.0;
            double cy=ymin+spanY*iy/10.0;
            for(int id=0;id<24;id++){
                double depth=minDepth*Math.pow(maxDepth/minDepth,id/23.0);
                Fit fit=fit(samples,cx,cy,depth,centerX,centerY,maxSpan);
                if(fit==null)continue;
                fits.add(fit);
                if(best==null||fit.sse<best.sse)best=fit;
            }
        }
        if(best==null)return fail(State.UNRESOLVED,"Dipol denklemine kararlı uyum bulunamadı.",n,maxSpan);
        // Optimum çevresinde daha sık bir yerel tarama; uç değerlere dayanıp yapay bir
        // derinlik çıkarılmasını önlemek için merkezler saha içinde sınırlanır.
        for(int iteration=0;iteration<2;iteration++){
            Fit seed=best;
            double stepx=spanX/(iteration==0?25.0:65.0);
            double stepy=spanY/(iteration==0?25.0:65.0);
            for(int ix=-2;ix<=2;ix++)for(int iy=-2;iy<=2;iy++)for(int iz=-2;iz<=2;iz++){
                double cx=seed.cx+ix*stepx,cy=seed.cy+iy*stepy;
                if(cx<xmin+spanX*.08||cx>xmax-spanX*.08||cy<ymin+spanY*.08||cy>ymax-spanY*.08)continue;
                double z=seed.depth*Math.exp(iz*(iteration==0?.13:.045));
                if(z<minDepth||z>maxDepth)continue;
                Fit f=fit(samples,cx,cy,z,centerX,centerY,maxSpan);
                if(f!=null){fits.add(f);if(f.sse<best.sse)best=f;}
            }
        }
        double explained=Math.max(0,1-best.sse/totalSse);
        double gain=Math.max(0,1-best.sse/baseSse);
        if(explained<(n<64?.86:.76)||gain<(n<64?.60:.45))
            return fail(State.POOR_FIT,"Ölçülen şekil tek manyetik dipol modeline yeterince uymuyor (uyum %"+
                Math.round(explained*100)+").",n,maxSpan);
        if(best.depth<minDepth*1.07||best.depth>maxDepth*.94)
            return fail(State.UNRESOLVED,"Derinlik en iyi uyumda model sınırına dayanıyor; tarama açıklığı yetersiz.",n,maxSpan);
        // Çok farklı derinliklerde benzer hata varsa problem tekil olarak çözülemiyor.
        double lo=best.depth,hi=best.depth;
        for(Fit f:fits)if(f.sse<=best.sse*1.035){lo=Math.min(lo,f.depth);hi=Math.max(hi,f.depth);}
        if(hi/Math.max(lo,.0001)>(n<64?1.65:2.0)||hi-lo>Math.max(n<64?.6:2.0,best.depth*(n<64?.5:.65)))
            return fail(State.UNRESOLVED,"Aynı anomalinin farklı derinlik çözümleri birbirine çok yakın uyuyor.",n,maxSpan);
        // En iyi eğri için noktasal kesinlik iddia etmek yerine model duyarlılığı
        // aralığını asgari %20 belirsizlikle genişlet.
        lo=Math.max(0,Math.min(lo,best.depth*(n<64?.55:.8)));hi=Math.max(hi,best.depth*(n<64?1.45:1.2));
        return new Result(State.ESTIMATE,"Yalnız izole dipol varsayımı altında model uyumu",n,
            best.depth,lo,hi,best.cx,best.cy,explained,gain,maxSpan);
    }
    private static double fitPlaneSse(List<Sample> data,double x0,double y0,double span){
        double[][] gram=new double[3][3];double[] rhs=new double[3];double yy=0;
        for(Sample q:data){double[] a={1,(q.x-x0)/span,(q.y-y0)/span};
            yy+=q.value*q.value;
            for(int i=0;i<3;i++){rhs[i]+=a[i]*q.value;for(int j=0;j<3;j++)gram[i][j]+=a[i]*a[j];}}
        double[] sol=solve(gram,rhs);if(sol==null)return Double.NaN;
        double dot=0;for(int i=0;i<3;i++)dot+=sol[i]*rhs[i];return Math.max(0,yy-dot);
    }
    private static Fit fit(List<Sample> data,double cx,double cy,double depth,
                           double x0,double y0,double span){
        double[][] gram=new double[P][P];double[] rhs=new double[P];double yy=0;
        double d2=depth*depth;
        for(Sample q:data){
            double dx=q.x-cx,dy=q.y-cy;
            double r2=dx*dx+dy*dy;
            double denom=(r2+d2);double inv=depth*depth*depth/(denom*denom*Math.sqrt(denom));
            double[] a={1,(q.x-x0)/span,(q.y-y0)/span,
                (2*d2-r2)*inv,3*depth*dx*inv,3*depth*dy*inv};
            yy+=q.value*q.value;
            for(int i=0;i<P;i++){rhs[i]+=a[i]*q.value;
                for(int j=0;j<=i;j++)gram[i][j]+=a[i]*a[j];}
        }
        for(int i=0;i<P;i++)for(int j=i+1;j<P;j++)gram[i][j]=gram[j][i];
        double[] sol=solve(gram,rhs);if(sol==null)return null;
        double dot=0;for(int i=0;i<P;i++)dot+=sol[i]*rhs[i];
        double err=Math.max(0,yy-dot);
        if(!Double.isFinite(err))return null;
        return new Fit(err,depth,cx,cy,sol);
    }
    private static double[] solve(double[][] a,double[] b){
        int n=b.length;double[][] m=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(a[i],0,m[i],0,n);m[i][n]=b[i];}
        for(int k=0;k<n;k++){
            int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(m[i][k])>Math.abs(m[pivot][k]))pivot=i;
            double[] tmp=m[k];m[k]=m[pivot];m[pivot]=tmp;
            if(Math.abs(m[k][k])<1e-12)return null;
            double v=m[k][k];for(int j=k;j<=n;j++)m[k][j]/=v;
            for(int i=0;i<n;i++)if(i!=k){double t=m[i][k];
                for(int j=k;j<=n;j++)m[i][j]-=t*m[k][j];}
        }
        double[] ans=new double[n];for(int i=0;i<n;i++)ans[i]=m[i][n];return ans;
    }
}
