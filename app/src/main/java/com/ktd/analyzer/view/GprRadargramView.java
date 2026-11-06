package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import com.ktd.analyzer.gpr.GprProtocol;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** GPR B-scan: continuous A-scan traces, calibrated time/depth when META exists. */
public final class GprRadargramView extends View {
    public static final int FILTER_NONE=0, FILTER_DC=1, FILTER_BACKGROUND=2, FILTER_VERTICAL=3;
    public static final int PALETTE_GRAY=0, PALETTE_DIVERGING=1, PALETTE_SEPIA=2;
    private static final int MAX_TRACES=180; // Bounded rolling display; acquisition has no trace-count limit
    private static final int VISIBLE_TRACES=180; // Fixed-width rolling radargram, new traces enter at right
    private static final double USER_GPR_DEPTH_DISPLAY_SCALE=10000.0; // Requested display scale only, NOT a measured physical calibration
    private final ArrayDeque<GprProtocol.Trace> traces=new ArrayDeque<>();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int nSamples=0, maxAbsolute=100, filterMode=0, palette=0;
    private double dtNs=Double.NaN, dxMeters=Double.NaN, epsilonR=9, timeWindowNs=Double.NaN;
    private float gain=1f;
    private final float[] depthGains={1f,1f,1f,1f};
    private int antennaMhz=300;
    private boolean estimatedTime=false;
    private boolean demo=false, hyperbolicContrast=false, autoContrast=true;
    private long lastSequence=-1;

    public GprRadargramView(Context context){
        super(context);
        label.setTypeface(android.graphics.Typeface.MONOSPACE);
        label.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);
        label.setColor(Color.rgb(205,224,238));
    }
    public void clear(){traces.clear();nSamples=0;lastSequence=-1;invalidate();}
    public void resetSession(){clear();dtNs=Double.NaN;dxMeters=Double.NaN;demo=false;maxAbsolute=100;invalidate();}
    public void setGain(float value){gain=Math.max(0f,Math.min(10f,value));invalidate();}
    public float getGain(){return gain;}
    public void setDepthGain(int section,float value){if(section>=0&&section<4){depthGains[section]=Math.max(0f,Math.min(10f,value));invalidate();}}
    public float getDepthGain(int section){return depthGains[Math.max(0,Math.min(3,section))];}
    public void setAntennaMhz(int mhz){antennaMhz=mhz;invalidate();}
    public void setEstimatedTime(boolean value){estimatedTime=value;invalidate();}
    public void setEpsilon(double value){epsilonR=Math.max(1,Math.min(50,value));invalidate();}
    public double getEpsilon(){return epsilonR;}
    public void setFilterMode(int f){filterMode=Math.max(0,Math.min(3,f));invalidate();}
    public int getFilterMode(){return filterMode;}
    public void setPalette(int v){palette=Math.max(0,Math.min(2,v));invalidate();}
    public int getPalette(){return palette;}
    public void setHyperbolaEnhance(boolean v){hyperbolicContrast=v;invalidate();}
    public void setAutoContrast(boolean v){autoContrast=v;invalidate();}
    public void setTimeWindowNs(double ns){timeWindowNs=ns>0?ns:Double.NaN;invalidate();}
    public double getTimeWindowNs(){return timeWindowNs;}
    public void setMeta(GprProtocol.Meta m){if(m!=null){dtNs=m.nsPerSample;dxMeters=m.dxMeters;demo=m.isDemo();invalidate();}}
    public void setImportedMeta(double ns,double dx,boolean isDemo){dtNs=ns;dxMeters=dx;demo=isDemo;invalidate();}
    public boolean isDemo(){return demo;}
    public double getSampleIntervalNs(){return dtNs;}
    public double getStepMeters(){return dxMeters;}
    public int traceCount(){return traces.size();}
    public int samplesPerTrace(){return nSamples;}
    public long lastSequence(){return lastSequence;}
    public boolean hasTimeAxis(){return Double.isFinite(dtNs)&&dtNs>0&&!demo;}
    public List<GprProtocol.Trace> snapshot(){return new ArrayList<>(traces);}
    public void add(GprProtocol.Trace t){
        if(t==null||t.samples.length<16)return;
        if(nSamples!=0&&nSamples!=t.samples.length)clear();
        nSamples=t.samples.length;lastSequence=t.sequence;
        int peak=1;for(short s:t.samples)peak=Math.max(peak,Math.abs((int)s));
        maxAbsolute=Math.max(8,Math.round(maxAbsolute*.90f+peak*.10f));
        if(traces.size()>=MAX_TRACES)traces.removeFirst();
        traces.addLast(t);invalidate();
    }
    private int shade(double signed){
        double v=Math.max(-1,Math.min(1,signed));
        if(palette==PALETTE_GRAY){
            int grey=Math.min(255,Math.max(0,(int)Math.round(128+125*v)));
            return Color.rgb(grey,grey,grey);
        }
        if(palette==PALETTE_SEPIA){
            int g=Math.min(255,Math.max(0,(int)Math.round(120+125*v)));
            return Color.rgb(Math.min(255,(int)(g*1.10)),Math.min(255,(int)(g*.88)),(int)(g*.63));
        }
        if(v>0){double q=Math.pow(v,.83);return Color.rgb((int)(30+225*q),(int)(20+100*(1-q)),(int)(60*(1-q)));}
        double q=Math.pow(-v,.83);return Color.rgb((int)(30*(1-q)),(int)(45+180*q),(int)(65+190*q));
    }
    private float[][] processed(List<GprProtocol.Trace> items,int rowCount,int visibleSamples){
        int n=items.size();float[][] result=new float[n][rowCount];
        for(int x=0;x<n;x++){
            short[] original=items.get(x).samples;
            double avg=0;
            if(filterMode==FILTER_DC)for(short v:original)avg+=v;
            if(filterMode==FILTER_DC)avg/=original.length;
            for(int y=0;y<rowCount;y++){
                int index=(int)Math.round(y*(visibleSamples-1.0)/Math.max(1,rowCount-1));
                float v=(float)(original[index]-avg);
                if(filterMode==FILTER_VERTICAL){
                    int earlier=Math.max(0,index-2),later=Math.min(original.length-1,index+2);
                    v=(original[later]-original[earlier])*1.7f;
                }
                result[x][y]=v;
            }
        }
        if(filterMode==FILTER_BACKGROUND&&n>4){
            for(int y=0;y<rowCount;y++){
                double mean=0;for(int x=0;x<n;x++)mean+=result[x][y];mean/=n;
                for(int x=0;x<n;x++)result[x][y]-=mean;
            }
        }
        return result;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(2,7,10));
        int w=getWidth(),h=getHeight();
        float dp=getResources().getDisplayMetrics().density;
        int left=(int)(52*dp),right=(int)(68*dp),top=(int)(28*dp),bottom=(int)(8*dp);
        int ww=Math.max(1,w-left-right),hh=Math.max(1,h-top-bottom);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);canvas.drawRect(left,top,left+ww,top+hh,p);
        List<GprProtocol.Trace> items=snapshot();
        if(items.size()>VISIBLE_TRACES)items=items.subList(items.size()-VISIBLE_TRACES,items.size());
        int visibleN=items.size();
        int visibleSamples=nSamples;
        if(hasTimeAxis()&&Double.isFinite(timeWindowNs))
            visibleSamples=Math.max(2,Math.min(nSamples,1+(int)Math.floor(timeWindowNs/dtNs)));
        if(visibleN>0&&visibleSamples>=2){
            int rows=Math.min(visibleSamples,600);
            float[][] sampled=processed(items,rows,visibleSamples);
            int peak=maxAbsolute;
            if(autoContrast){
                // Contrast scale is visual only; the raw measurements are untouched.
                int[] hist=new int[items.size()*rows];int k=0;
                for(int i=0;i<items.size();i++)for(int y=0;y<rows;y++)hist[k++]=(int)Math.abs(sampled[i][y]);
                java.util.Arrays.sort(hist);
                peak=Math.max(8,hist[(int)((hist.length-1)*.96)]);
            }
            int[] pixels=new int[visibleN*rows];
            double scale=Math.max(1,peak)/Math.max(.0001f,gain);
            for(int x=0;x<visibleN;x++)for(int y=0;y<rows;y++){
                double a=sampled[x][y];
                if(hyperbolicContrast){
                    double a0=sampled[Math.max(0,x-1)][y],a1=sampled[Math.min(visibleN-1,x+1)][y];
                    a+=(a-(a0+a1)*.5)*.48;
                }
                int section=Math.min(3,(int)((long)y*4/rows));
                float localGain=depthGains[section];
                double response=a/scale*localGain;
                pixels[y*visibleN+x]=(gain<=0.0001f || localGain<=0.0001f ? Color.rgb(128,128,128) : shade(response));
            }
            Bitmap bmp=Bitmap.createBitmap(pixels,visibleN,rows,Bitmap.Config.ARGB_8888);
            p.setFilterBitmap(true);p.setStyle(Paint.Style.FILL);
            // Preserve a fixed x-step; leave unfilled space black and scroll left after 180 traces.
            int drawnW=Math.max(1,Math.round(ww*(visibleN/(float)VISIBLE_TRACES)));
            canvas.drawBitmap(bmp,new Rect(0,0,visibleN,rows),new Rect(left,top,left+drawnW,top+hh),p);
            bmp.recycle();
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*.65f);p.setColor(Color.rgb(85,111,125));
        canvas.drawRect(left,top,left+ww,top+hh,p);
        // PC-style right-hand MT* scale. An unknown SGY sample interval is NOT
        // silently treated as measured time; its display fallback is explicitly starred.
        boolean measuredNs=hasTimeAxis();
        double visibleTimeNs=measuredNs ? (visibleSamples-1)*dtNs : Double.NaN;
        if(!Double.isFinite(visibleTimeNs) || visibleTimeNs<=0)
            visibleTimeNs=(Double.isFinite(timeWindowNs)&&timeWindowNs>0)?timeWindowNs:150.0;
        // PC screenshot scale: 150 ns -> approximately 10 m (epsilon_r ~5.05).
        // This fallback is a display reference only, never a claim of measured depth.
        double displayEpsilon=measuredNs?epsilonR:5.05;
        double maxDepth=0.299792458*visibleTimeNs/(2.0*Math.sqrt(displayEpsilon));
        float axisX=left+ww;
        p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);
        canvas.drawRect(axisX+1,top-4*dp,w,top+hh+2*dp,p);
        label.setTextAlign(Paint.Align.CENTER);label.setColor(Color.rgb(118,234,229));
        canvas.drawText(measuredNs ? "MT" : "MT*",axisX+right*.5f,top-9*dp,label);
        final int steps=12;
        for(int i=0;i<=steps;i++){
            float y=top+hh*i/(float)steps;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*.55f);
            p.setColor(Color.argb(45,190,215,235));
            canvas.drawLine(left,y,axisX,y,p);
            p.setColor(Color.rgb(61,83,96));canvas.drawLine(axisX+1,y,w,y,p);
            label.setTextAlign(Paint.Align.RIGHT);label.setColor(Color.rgb(205,224,238));
            canvas.drawText(String.format(Locale.US,"%.2f",maxDepth*i/steps),w-3*dp,y+(i==0?10*dp:i==steps?-2*dp:4*dp),label);
            // Keep the sample index left-side as a navigation aid, separate from metres.
            int index=(int)Math.round((visibleSamples-1)*i/(double)steps);
            label.setTextAlign(Paint.Align.RIGHT);label.setColor(Color.rgb(194,219,235));
            canvas.drawText(Integer.toString(index),left-5*dp,y+(i==0?10*dp:i==steps?-2*dp:4*dp),label);
        }
        for(int x=1;x<8;x++){
            float cx=left+x*ww/8f;
            p.setColor(Color.argb(65,145,168,183));
            canvas.drawLine(cx,top,cx,top+hh,p);
        }

    }
}
