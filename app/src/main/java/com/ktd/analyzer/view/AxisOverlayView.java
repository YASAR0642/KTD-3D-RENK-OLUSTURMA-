package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.opengl.Matrix;
import android.view.View;

import com.ktd.analyzer.data.ScanData;

import java.util.Locale;

/** Voxler tarzı sahneye bağlı X/Y/Z eksenleri. */
public final class AxisOverlayView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ScanData data;
    private boolean volumeMode=false;
    // Sadece yeterli model uyumunda metre etiketleri: ham Z/CSV derinliğine yazılmaz.
    private float modelDepthMeters=Float.NaN, modelAxisMeters=Float.NaN;
    private float displayDepthRange=5f;
    private float yaw=-34f,pitch=42f,zoom=1f,panX=0f,panY=0f;
    private final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];

    private static final int X_RED=Color.rgb(232,42,38);
    private static final int Y_GREEN=Color.rgb(40,220,76);
    private static final int Z_BLUE=Color.rgb(42,82,255);

    public AxisOverlayView(Context c){super(c);setClickable(false);setFocusable(false);setWillNotDraw(false);}
    public void setData(ScanData d){data=d;invalidate();}
    public void setVolumeMode(boolean v){volumeMode=v;invalidate();}
    public void setModelDepthMeters(float estimate,float axisMax){
        modelDepthMeters=estimate;modelAxisMeters=axisMax;invalidate();
    }
    public void setSpread(float ignored){invalidate();}
    public void setDisplayDepthRange(float meters){displayDepthRange=Math.max(1f,meters);invalidate();}
    public void setCamera(float y,float pi,float z){setCamera(y,pi,z,0f,0f);} public void setCamera(float y,float pi,float z,float px,float py){yaw=y;pitch=pi;zoom=z;panX=px;panY=py;invalidate();}

    /** Yatay alanın en uzun kenarını yaklaşık 2.2 model birimine sığdıran fiziksel ölçek. */
    public static float modelUnitsPerMeter(ScanData d){
        if(d==null)return .8f;
        float x=Math.max(.01f,(d.cols-1)*d.stepCm/100f);
        float y=Math.max(.01f,(d.rows-1)*d.stepCm/100f);
        return 2.20f/Math.max(.30f,Math.max(x,y));
    }

    /**
     * 3D/çekim tarafında göreli derinlik bilgisini göstermek için kullanılan
     * eski fiziksel Z ölçeği. 4D geometri artık bunu KULLANMAZ.
     */
    public static float depthExtentFor(ScanData d){return depthExtentFor(d,0f);}
    public static float depthExtentFor(ScanData d,float displayRange){
        if(d==null)return .90f;
        float m=displayRange>0f?displayRange:d.maxDepthMeters();
        if(m<=0f)return .90f;
        // Uzun derinliklerde eksen fiziksel metreyi sıkıştırarak ekrana sığdırır.
        // Etiketler gerçek metre olarak kalır.
        float raw=(float)(0.82f+0.55f*Math.log10(1f+m));
        return Math.max(1.18f,Math.min(3.30f,raw*1.58f));
    }

    /**
     * 4D/Voxler Z ekseni cihazın gerçek C derinliğine bağlanır.
     * Görsel oran, çok derin ölçümlerde ekranı bozmasın diye sınırlı sıkıştırılır;
     * eksen etiketi yine gerçek metre değerini gösterir.
     */
    public static float volumeExtentFor(ScanData d){return volumeExtentFor(d,0f);}
    public static float volumeExtentFor(ScanData d,float displayRange){
        if(d==null)return .62f;
        float depth=displayRange>0f?displayRange:d.maxDepthMeters();
        if(depth<=0f)return .62f;
        // 25-30 m gibi büyük derinliklerde de hacim kutusu ekrana sığsın.
        float raw=(float)(0.52f+0.48f*Math.log10(1f+depth));
        return Math.max(.82f,Math.min(2.60f,raw*1.60f));
    }

    public static float cameraDistanceFor(ScanData d,boolean volume){return cameraDistanceFor(d,volume,0f);}
    public static float cameraDistanceFor(ScanData d,boolean volume,float range){
        if(!volume)return 4.8f+Math.max(0f,depthExtentFor(d,range)-1.2f)*.13f;
        return 5.15f+Math.max(0f,volumeExtentFor(d,range)-.62f)*.45f;
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(data==null||getWidth()<=1||getHeight()<=1)return;
        buildMvp();

        final float xmin=volumeMode?-1.15f:-1.22f;
        final float xmax=volumeMode? 1.15f: 1.22f;
        final float ymax=volumeMode? 1.05f: 1.02f;
        final float ymin=volumeMode?-1.05f:-1.02f;
        final float zExtent=volumeMode?volumeExtentFor(data,displayDepthRange):depthExtentFor(data,displayDepthRange);

        /*
         * KTD saha kuralı (FINAL):
         *   X = cihazla İLERİ tarama yönü (bir hattın başlangıcından sonuna),
         *   Y = YAN SIRAYA geçiş yönü.
         *
         * Matrisimizde satırlar ileri hattı, sütunlar yan sırayı temsil eder.
         * Sol/sağ başlangıç yalnız Y yönünün hangi kenardan başladığını değiştirir;
         * X her zaman başlangıç çizgisinden ileri doğru gider. Böylece 2D grid,
         * 3D yüzey ve 4D hacimde aynı anomali aynı X/Y koordinatında kalır.
         */
        final boolean startRight=data.startCorner==1;
        final float ox=startRight?xmax:xmin;
        final float oy=ymin;
        final float lateralEnd=startRight?xmin:xmax;
        final float forwardEnd=ymax;

        float[] o=project(ox,oy,0f);
        float[] ex=project(ox,forwardEnd,0f);      // X = ileri
        float[] ey=project(lateralEnd,oy,0f);     // Y = yan sıra
        if(!visible(o)||!visible(ex)||!visible(ey))return;

        int xTicks=Math.max(1,data.rows-1);
        int yTicks=Math.max(1,data.cols-1);
        final float stepM=Math.max(1,data.stepCm)/100f;

        drawAxis(c,o,ex,X_RED,"X",xTicks,(i,n)->fmtXY(stepM*i),-1f,true);
        drawAxis(c,o,ey,Y_GREEN,"Y",yTicks,(i,n)->fmtXY(stepM*i),1f,true);

        if(!data.hasDepthData()){
            boolean modelReady=false; // Unverified inverse signal depth is not physical depth
            if(modelReady){
                float[] ez=project(ox,oy,-zExtent);
                if(visible(ez))drawDepthAxis(c,o,ez,Z_BLUE,modelAxisMeters);
                p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(sp(10));p.setFakeBoldText(true);p.setColor(Color.rgb(245,190,85));
                // Kullanıcının isteği: sahne içi uzun açıklama kaldırıldı.
                p.setFakeBoldText(false);
            }else if(volumeMode){
                float[] ez=project(ox,oy,-zExtent);
                if(visible(ez))drawDepthAxis(c,o,ez,Z_BLUE,displayDepthRange);
                p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(sp(10));p.setFakeBoldText(true);p.setColor(Color.rgb(245,190,85));
                // Sahne altı açıklama kaldırıldı; relatif eksende yüzde işaretleri kalır.
                p.setFakeBoldText(false);
            }else{
                // 3D'de de cetvel her zaman gorunur, tarama bos olsa bile.
                float[] ez=project(ox,oy,-zExtent);
                if(visible(ez))drawDepthAxis(c,o,ez,Z_BLUE,displayDepthRange);
            }
            return;
        }
        if(volumeMode){
            float[] ez=project(ox,oy,-zExtent);
            if(!visible(ez))return;
            drawDepthAxis(c,o,ez,Z_BLUE,displayDepthRange);
            p.setStyle(Paint.Style.FILL);p.setFakeBoldText(true);p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(sp(10.5f));p.setColor(Z_BLUE);p.setAlpha(255);
            c.drawText("0",o[0]-dp(5),o[1]-dp(5),p);p.setFakeBoldText(false);
        }else{
            float[] ez=project(ox,oy,-zExtent);
            if(!visible(ez))return;
            drawDepthAxis(c,o,ez,Z_BLUE,displayDepthRange);
            p.setStyle(Paint.Style.FILL);p.setFakeBoldText(true);p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(sp(10.5f));p.setColor(Z_BLUE);p.setAlpha(255);
            c.drawText("0",o[0]-dp(5),o[1]-dp(5),p);p.setFakeBoldText(false);
        }
    }

    private void buildMvp(){
        float aspect=getWidth()/(float)Math.max(1,getHeight());
        float dist=cameraDistanceFor(data,volumeMode,displayDepthRange);
        float far=Math.max(80f,dist+(volumeMode?volumeExtentFor(data,displayDepthRange):depthExtentFor(data,displayDepthRange))*4f+12f);
        Matrix.perspectiveM(proj,0,volumeMode?42f:40f,aspect,.08f,far);
        Matrix.setLookAtM(view,0,0,0,dist,0,0,0,0,1,0);Matrix.translateM(view,0,panX,panY,(volumeMode?volumeExtentFor(data,displayDepthRange):depthExtentFor(data,displayDepthRange))*.36f);
        Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,pitch,1,0,0);Matrix.rotateM(model,0,yaw,0,1,0);Matrix.scaleM(model,0,zoom,zoom,zoom);
        Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,proj,0,mv,0);
    }

    private float[] project(float x,float y,float z){
        float[] in={x,y,z,1f},out=new float[4];Matrix.multiplyMV(out,0,mvp,0,in,0);
        if(Math.abs(out[3])<1e-5f)return new float[]{-9999,-9999,-1};
        float nx=out[0]/out[3],ny=out[1]/out[3],nz=out[2]/out[3];
        return new float[]{(nx*.5f+.5f)*getWidth(),(1f-(ny*.5f+.5f))*getHeight(),nz};
    }
    private static boolean visible(float[] q){return q!=null&&q.length>=3&&q[0]>-5000&&q[1]>-5000;}

    private void drawAxis(Canvas c,float[] a,float[] b,int color,String name,int ticks,LabelProvider labels,float labelSide,boolean skipOrigin){
        float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<8f)return;
        float ux=dx/len,uy=dy/len,nx=-uy,ny=ux;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(2.5f));p.setColor(color);p.setAlpha(250);c.drawLine(a[0],a[1],b[0],b[1],p);drawArrow(c,b[0],b[1],ux,uy,color);
        p.setStrokeWidth(dp(1.15f));p.setTextSize(sp(axisTextSp(ticks)));p.setTextAlign(Paint.Align.CENTER);p.setColor(color);p.setAlpha(245);
        for(int i=0;i<=ticks;i++){
            // Küçük telefon ekranında projeksiyon daralınca tik sayısı da seyreltilir.
            int labelStep=Math.max(1,(int)Math.ceil((ticks*dp(78f))/len));
            if(i!=0 && i!=ticks && i%labelStep!=0)continue;
            if(len<dp(145f)&&i!=ticks)continue;
            float t=i/(float)ticks,x=a[0]+dx*t,y=a[1]+dy*t,tick=dp(i==0||i==ticks?5.5f:4.0f);
            c.drawLine(x-nx*tick,y-ny*tick,x+nx*tick,y+ny*tick,p);
            if(skipOrigin&&i==0)continue;
            float off=dp(15f)*labelSide;c.drawText(labels.label(i,ticks),x+nx*off,y+ny*off+sp(3f),p);
        }
        p.setStyle(Paint.Style.FILL);p.setTextSize(sp(12.5f));p.setFakeBoldText(true);p.setTextAlign(Paint.Align.CENTER);p.setColor(color);p.setAlpha(255);c.drawText(name,b[0]+ux*dp(12f),b[1]+uy*dp(12f)+sp(4f),p);p.setFakeBoldText(false);
    }

    /** 3D yüzeyde Z ekseni metre değil, ham sensörün referans farkını (Δ) gösterir. */
    private void drawDeltaAxis(Canvas c,float[] a,float[] b,int color,float maxAbs){
        float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<8f)return;
        float ux=dx/len,uy=dy/len,nx=-uy,ny=ux;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(2.3f));p.setColor(color);p.setAlpha(250);
        c.drawLine(a[0],a[1],b[0],b[1],p);drawArrow(c,b[0],b[1],ux,uy,color);
        float m=Math.max(10f,maxAbs);
        p.setStrokeWidth(dp(1.1f));p.setTextSize(sp(9.0f));p.setTextAlign(Paint.Align.CENTER);p.setColor(color);
        for(int i=0;i<=4;i++){
            float t=i/4f,x=a[0]+dx*t,y=a[1]+dy*t,tick=dp(4.0f);
            c.drawLine(x-nx*tick,y-ny*tick,x+nx*tick,y+ny*tick,p);
            float v=-m+2f*m*t;
            c.drawText(String.format(Locale.US,"%.0f",v),x+nx*dp(22f),y+ny*dp(22f)+sp(3f),p);
        }
        p.setStyle(Paint.Style.FILL);p.setTextSize(sp(12.5f));p.setFakeBoldText(true);p.setTextAlign(Paint.Align.CENTER);
        p.setColor(color);p.setAlpha(255);c.drawText("Δ",b[0]+ux*dp(12f),b[1]+uy*dp(12f)+sp(4f),p);p.setFakeBoldText(false);
    }

    /**
     * 4D'de Z fiziksel metre olarak türetilmez. 0 yüzeydir; alt işaretler yalnız
     * göreli hacim katmanlarını gösterir. Böylece güçlü yüzey anomalisi sahte derinlik
     * olarak yorumlanmaz.
     */
    private void drawRelativeVolumeAxis(Canvas c,float[] a,float[] b,int color){
        float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<8f)return;
        float ux=dx/len,uy=dy/len,nx=-uy,ny=ux;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(2.5f));p.setColor(color);p.setAlpha(250);
        c.drawLine(a[0],a[1],b[0],b[1],p);drawArrow(c,b[0],b[1],ux,uy,color);
        p.setStrokeWidth(dp(1.1f));p.setTextSize(sp(8.5f));p.setTextAlign(Paint.Align.CENTER);p.setColor(color);
        for(int i=1;i<=4;i++){
            float t=i/4f,x=a[0]+dx*t,y=a[1]+dy*t,tick=dp(4f);
            c.drawLine(x-nx*tick,y-ny*tick,x+nx*tick,y+ny*tick,p);
            c.drawText((i*25)+"%",x+nx*dp(22f),y+ny*dp(22f)+sp(3f),p);
        }
        p.setStyle(Paint.Style.FILL);p.setTextSize(sp(12.5f));p.setFakeBoldText(true);p.setTextAlign(Paint.Align.CENTER);
        p.setColor(color);p.setAlpha(255);c.drawText("Z",b[0]+ux*dp(12f),b[1]+uy*dp(12f)+sp(4f),p);p.setFakeBoldText(false);
    }

    /** Z yalnız 0'dan eksi derinliğe gider; 0.4/0.8 gibi otomatik kesirler üretilmez. */
    private void drawDepthAxis(Canvas c,float[] a,float[] b,int color,float maxDepth){
        float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<8f)return;
        float ux=dx/len,uy=dy/len,nx=-uy,ny=ux;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(2.5f));p.setColor(color);p.setAlpha(250);c.drawLine(a[0],a[1],b[0],b[1],p);drawArrow(c,b[0],b[1],ux,uy,color);
        if(maxDepth>0f){
            float step=maxDepth<=8f?.5f:niceDepthStep(maxDepth);
            p.setStrokeWidth(dp(1.15f));p.setTextSize(sp(9.5f));p.setTextAlign(Paint.Align.CENTER);p.setColor(color);
            for(float d=step;d<=maxDepth+1e-4f;d+=step){
                float t=Math.min(1f,d/maxDepth),x=a[0]+dx*t,y=a[1]+dy*t,tick=dp(4.5f);
                c.drawLine(x-nx*tick,y-ny*tick,x+nx*tick,y+ny*tick,p);
                c.drawText(fmtDepth(d),x+nx*dp(22f),y+ny*dp(22f)+sp(3f),p);
            }
        }
        p.setStyle(Paint.Style.FILL);p.setTextSize(sp(12.5f));p.setFakeBoldText(true);p.setTextAlign(Paint.Align.CENTER);p.setColor(color);p.setAlpha(255);c.drawText("Z (görsel)",b[0]+ux*dp(17f),b[1]+uy*dp(17f)+sp(4f),p);p.setFakeBoldText(false);
    }

    private static float niceDepthStep(float max){
        if(max<=0f)return 1f;
        float target=max/4f; // Daha ferah Z metre araliklari: yaklasik 4 etiket
        float[] base={0.1f,0.2f,0.25f,0.5f,1f,2f,3f,4f,5f,10f,15f,20f,25f,30f,40f,50f,75f,100f};
        for(float b:base)if(target<=b)return b;
        return (float)Math.ceil(target/50f)*50f;
    }
    private static float axisTextSp(int ticks){return Math.max(6.5f,10f-Math.max(0,ticks-5)*.22f);}

    private void drawArrow(Canvas c,float x,float y,float ux,float uy,int color){
        float nx=-uy,ny=ux,back=dp(11f),side=dp(5.5f);Path path=new Path();path.moveTo(x,y);path.lineTo(x-ux*back+nx*side,y-uy*back+ny*side);path.lineTo(x-ux*back-nx*side,y-uy*back-ny*side);path.close();p.setStyle(Paint.Style.FILL);p.setColor(color);p.setAlpha(255);c.drawPath(path,p);
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
    private static String fmtXY(float v){return String.format(Locale.US,"%.2f",v);}
    private static String fmtDepth(float v){return Math.abs(v-Math.round(v))<.001f?String.format(Locale.US,"%.0f",v):String.format(Locale.US,"%.2f",v);}
    private interface LabelProvider{String label(int i,int n);}
}
