package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.MotionEvent;
import com.ktd.analyzer.gpr.GprProtocol;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Magnetic GPR-look view used ONLY in 3D Live Scan > GPR View. */
public final class MagneticRadargramView extends View {
    private com.ktd.analyzer.PerfMonitor perfMonitor;
    public void setPerfMonitor(com.ktd.analyzer.PerfMonitor p){perfMonitor=p;}

    public static final int FILTER_NONE=0, FILTER_DC=1, FILTER_BACKGROUND=2, FILTER_VERTICAL=3;
    public static final int PALETTE_GRAY=0, PALETTE_DIVERGING=1, PALETTE_SEPIA=2;
    private static final int MAX_TRACES=10000;
    private static final int MAX_VISIBLE_TRACES=180;
    private boolean browseEnabled=true;
    private int browseOffset=0; // 0 = latest; positive = earlier history
    private float dragX;
    public void setBrowseEnabled(boolean enabled){browseEnabled=enabled;if(!enabled)browseOffset=0;invalidate();}
    private int windowTraces=48; // Resized from measured Android frame inter-arrival cadence.
    private final ArrayDeque<GprProtocol.Trace> traces=new ArrayDeque<>();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int nSamples=0, maxAbsolute=16000, filterMode=0, palette=0;
    private static final double CM_PER_TRACE=7.5; // 180 cm / 24 measurements, field-calibrated example
    private double surveyPositionCm=0;
    public void setSurveyPositionCm(double cm){surveyPositionCm=Math.max(0,cm);invalidate();}
    private double dtNs=Double.NaN, dxMeters=Double.NaN, epsilonR=9, timeWindowNs=Double.NaN;
    private float gain=1f;
    private final float[] depthGains={1f,1f,1f,1f,1f};
    private int antennaMhz=300;
    private boolean estimatedTime=false;
    private boolean demo=false, hyperbolicContrast=false, autoContrast=false;
    private long lastSequence=-1;

    public MagneticRadargramView(Context context){
        super(context);
        ColorMaps.configure(context);
        label.setTypeface(android.graphics.Typeface.MONOSPACE);
        label.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);
        label.setColor(Color.rgb(205,224,238));
    }
    public void clear(){traces.clear();nSamples=0;lastSequence=-1;browseOffset=0;invalidate();}
    public void resetSession(){clear();dtNs=Double.NaN;dxMeters=Double.NaN;demo=false;maxAbsolute=16000;invalidate();}
    public void setGain(float value){gain=Math.max(0f,Math.min(10f,value));invalidate();}
    public float getGain(){return gain;}
    public void setDepthGain(int section,float value){if(section>=0&&section<5){depthGains[section]=Math.max(0f,Math.min(10f,value));invalidate();}}
    public float getDepthGain(int section){return depthGains[Math.max(0,Math.min(4,section))];}
    public void setAntennaMhz(int mhz){antennaMhz=mhz;invalidate();}
    public void setEstimatedTime(boolean value){estimatedTime=value;invalidate();}
    public void setEpsilon(double value){epsilonR=Math.max(1,Math.min(50,value));invalidate();}
    public double getEpsilon(){return epsilonR;}
    public void setFilterMode(int f){filterMode=Math.max(0,Math.min(3,f));invalidate();}
    public int getFilterMode(){return filterMode;}
    public void setPalette(int v){palette=Math.max(0,Math.min(ColorMaps.count()-1,v));invalidate();}
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
    public void setWindowTraces(int value){int target=Math.max(48,Math.min(MAX_VISIBLE_TRACES,value));if(windowTraces!=target){windowTraces=target;invalidate();}}
    public int samplesPerTrace(){return nSamples;}
    public long lastSequence(){return lastSequence;}
    public boolean hasTimeAxis(){return Double.isFinite(dtNs)&&dtNs>0&&!demo;}
    public List<GprProtocol.Trace> snapshot(){
        // Return the currently viewed window without copying the full recording for every frame.
        ArrayList<GprProtocol.Trace> result=new ArrayList<>();
        int end=Math.max(0,traces.size()-browseOffset),start=Math.max(0,end-windowTraces);
        int index=0;
        for(GprProtocol.Trace t:traces){if(index>=end)break;if(index>=start)result.add(t);index++;}
        return result;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!browseEnabled || traces.size()<=windowTraces)return super.onTouchEvent(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN: dragX=event.getX();return true;
            case MotionEvent.ACTION_MOVE:
                float dx=event.getX()-dragX;
                float step=getWidth()/(float)Math.max(1,windowTraces);
                int shift=(int)(dx/Math.max(2f,step));
                if(shift!=0){browseOffset=Math.max(0,Math.min(traces.size()-windowTraces,browseOffset+shift));dragX+=shift*Math.max(2f,step);invalidate();}
                return true;
            case MotionEvent.ACTION_UP:performClick();return true;
            case MotionEvent.ACTION_CANCEL:return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public void add(GprProtocol.Trace t){
        if(t==null||t.samples.length<16)return;
        if(nSamples!=0&&nSamples!=t.samples.length)clear();
        nSamples=t.samples.length;lastSequence=t.sequence;
        // Fixed color scaling: recorded traces never fade or change with later peaks.
        if(traces.size()>=MAX_TRACES)traces.removeFirst();
        traces.addLast(t);if(!browseEnabled)browseOffset=0;invalidate();
    }
    // Five immediately recognizable colors in every GMR palette:
    // strong negative = blue, weak negative = cyan, ground = neutral gray,
    // weak positive = yellow, strong positive = red.
    // A palette variation changes shade/contrast, NOT polarity or threshold.
    private static final int GMR_GRAY=Color.rgb(104,110,116);
    private static int blend(int a,int b,float t){
        t=Math.max(0f,Math.min(1f,t));
        return Color.rgb(
            Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
            Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
            Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    private int shade(double signed){
        if(palette>=ColorMaps.COUNT)return ColorMaps.color(palette,(float)(signed*100f),100f);
        if(Math.abs(signed)<0.001)return GMR_GRAY;
        // GMR radargramin normalize edilmis gorsel sinyaline de ortak tercihler uygulanir.
        int controlled=ColorMaps.color(1,(float)(signed*100f),100f);
        if(Math.abs(signed)>0.12)return controlled;
        // Variations: classic, vivid, pastel, high contrast, deep.
        final int[] blue={0xff1749dc,0xff0044ff,0xff4675ed,0xff0023b9,0xff172b95};
        final int[] cyan={0xff37c9e9,0xff00e6ff,0xff88e6f5,0xff00c9fb,0xff1396cb};
        final int[] yellow={0xffffdc32,0xffffed00,0xffffe876,0xffffff00,0xffe9be22};
        final int[] red={0xfff03737,0xffff2424,0xffef6060,0xffef0000,0xffc51d2b};
        int mode=Math.max(0,Math.min(4,palette));
        float strength=(float)Math.min(1.0,Math.abs(signed));
        // Distinct intermediate cyan/yellow remain visible instead of
        // reducing eleven choices to almost identical two-color displays.
        // No trace age, time, or horizontal x coordinate enters this mapping.
        if(signed<0){
            if(strength<0.32f)return blend(GMR_GRAY,cyan[mode],strength/0.32f);
            return blend(cyan[mode],blue[mode],Math.min(1f,(strength-0.32f)/0.45f));
        }
        if(strength<0.32f)return blend(GMR_GRAY,yellow[mode],strength/0.32f);
        return blend(yellow[mode],red[mode],Math.min(1f,(strength-0.32f)/0.45f));
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
                if(filterMode==FILTER_VERTICAL){int e=Math.max(0,index-2),l=Math.min(original.length-1,index+2);v=(original[l]-original[e])*1.7f;}
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
        long monitorDrawStart=perfMonitor==null?0:perfMonitor.drawBegin();
        try {

        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(2,7,10));
        int w=getWidth(),h=getHeight();
        float dp=getResources().getDisplayMetrics().density;
        int left=(int)(65*dp),right=(int)(57*dp),top=(int)(28*dp),bottom=(int)(8*dp);
        int ww=Math.max(1,w-left-right),hh=Math.max(1,h-top-bottom);
        p.setStyle(Paint.Style.FILL);p.setColor(GMR_GRAY);canvas.drawRect(left,top,left+ww,top+hh,p);
        List<GprProtocol.Trace> items=snapshot();
        int visibleN=items.size();
        int visibleSamples=nSamples;
        if(hasTimeAxis()&&Double.isFinite(timeWindowNs)) visibleSamples=Math.max(2,Math.min(nSamples,1+(int)Math.floor(timeWindowNs/dtNs)));
        if(visibleN>0&&visibleSamples>=2){
            int rows=Math.min(visibleSamples,600);
            int savedFilter=filterMode;filterMode=FILTER_NONE;
            float[][] sampled=processed(items,rows,visibleSamples);
            filterMode=savedFilter;
            int peak=16000;
            if(false && autoContrast){
                int[] hist=new int[items.size()*rows];int k=0;
                for(int i=0;i<items.size();i++)for(int y=0;y<rows;y++)hist[k++]=(int)Math.abs(sampled[i][y]);
                java.util.Arrays.sort(hist);peak=Math.max(8,hist[(int)((hist.length-1)*.96)]);
            }
            int[] pixels=new int[visibleN*rows];
            double scale=Math.max(1,peak)/Math.max(.0001f,gain);
            for(int x=0;x<visibleN;x++)for(int y=0;y<rows;y++){
                double a=sampled[x][y];
                if(false && hyperbolicContrast){double a0=sampled[Math.max(0,x-1)][y],a1=sampled[Math.min(visibleN-1,x+1)][y];a+=(a-(a0+a1)*.5)*.48;}
                int section=Math.min(4,(int)((long)y*5/rows));
                float localGain=depthGains[section];
                double response=a/scale*localGain;
                // Soft clipping preserves visible detail at high manual gains.
                response=Math.tanh(response*.83);
                // Retain polarity and genuine relative contrast; avoid negative-side clipping.
                pixels[y*visibleN+x]=(gain<=0.0001f||localGain<=0.0001f?GMR_GRAY:shade(response));
            }
            Bitmap bmp=Bitmap.createBitmap(pixels,visibleN,rows,Bitmap.Config.ARGB_8888);
            p.setFilterBitmap(true);p.setStyle(Paint.Style.FILL);
            // Fixed trace pitch. Old traces leave from left after 180; no rescaling during playback.
            int drawnW=Math.max(1,Math.round(ww*(visibleN/(float)windowTraces)));
            // Phase 1: first observation is the LEFTMOST column and the screen
            // fills toward RIGHT. Phase 2: when full, the oldest exits LEFT,
            // newest remains at RIGHT. Each stored trace keeps its original color.
            // No fading, per-frame rescaling, reversal, or horizontal mirroring.
            p.setAlpha(255);
            canvas.drawBitmap(bmp,new Rect(0,0,visibleN,rows),
                    new Rect(left,top,left+drawnW,top+hh),p);
            bmp.recycle();
        }
        // Horizontal axis is the received observation index, not measured distance.
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);
        label.setColor(Color.rgb(238,237,178));
        int firstSample=Math.max(0,traces.size()-browseOffset-visibleN);
        int stepLabels=Math.max(1,windowTraces/4);
        for(int i=0;i<visibleN;i+=stepLabels){
            float xx=left+ww*(i/(float)windowTraces);
            canvas.drawText(Integer.toString(firstSample+i+1),xx,top-8*dp,label);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*.65f);p.setColor(Color.rgb(85,111,125));
        canvas.drawRect(left,top,left+ww,top+hh,p);
        // Left: observation number. Right: the project 800/Z display scale (0..10 m).
        // It is not an independently measured GPR time-of-flight depth.
        float axisX=left+ww;
        p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);canvas.drawRect(axisX,top-4*dp,w,top+hh+2*dp,p);
        label.setTextAlign(Paint.Align.CENTER);label.setColor(Color.rgb(118,234,229));
        canvas.drawText("MT",axisX+right*.5f,top-9*dp,label);
        canvas.drawText("ÖRNEK",left*.5f,top-9*dp,label);
        label.setTextAlign(Paint.Align.RIGHT);label.setColor(Color.rgb(238,237,178));
        canvas.drawText(Integer.toString(firstSample+Math.max(1,visibleN)),left-4*dp,top+14*dp,label);
        // Surface reference band: 0 to 0.25 m on the fixed 10 m ruler.
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(38,56,183,117));
        canvas.drawRect(left,top,left+ww,top+hh*(0.25f/10f),p);
        final int steps=10;
        for(int i=0;i<=steps;i++){
            float y=top+hh*i/(float)steps;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*.55f);p.setColor(Color.argb(45,190,215,235));canvas.drawLine(left,y,axisX,y,p);
            p.setColor(Color.rgb(61,83,96));canvas.drawLine(axisX,y,w,y,p);
            double meter=10.0*i/steps;
            label.setTextAlign(Paint.Align.RIGHT);label.setColor(Color.rgb(205,224,238));
            canvas.drawText(String.format(java.util.Locale.US,"%.0f",meter),w-3*dp,y+(i==0?10*dp:i==steps?-2*dp:4*dp),label);
        }
        for(int x=1;x<8;x++){float cx=left+x*ww/8f;p.setColor(Color.argb(65,145,168,183));canvas.drawLine(cx,top,cx,top+hh,p);}    
    
        } finally {if(perfMonitor!=null)perfMonitor.drawEnd(monitorDrawStart);}
    }
}
