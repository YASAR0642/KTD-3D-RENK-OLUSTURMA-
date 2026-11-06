package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.ktd.analyzer.data.SensorFrame;

import java.util.List;

/**
 * FINAL hızlı canlı tarama.
 * Her gerçek Bluetooth paketi alttan girer ve yukarı akar. Anomali merkezi sabittir;
 * yalnız yatayda sağa/sola genişler. Böylece dikey boy sahte olarak büyütülmez.
 * Renkler 3D yüzeyle aynı ColorMaps paletinden, opak ve canlı çizilir.
 */
public final class UltrasoundView extends View {
    private com.ktd.analyzer.PerfMonitor perfMonitor;
    public void setPerfMonitor(com.ktd.analyzer.PerfMonitor p){perfMonitor=p;}

    private static final int N = 2400;
    private final float[] d = new float[N];
    private final boolean[] used = new boolean[N];
    private int head = 0, count = 0, palette = 0;
    private float maxAbs = 18f;
    private float displayGain=1f;
    private boolean gprStyle=false;
    private float broadAnomalyBoost=2f;
    private boolean mineralFilterEnabled=false;
    // Magnetic-filter controls do not modify raw sensor samples.
    public void setBroadAnomalyBoost(float v){ broadAnomalyBoost=Math.max(1f,Math.min(5f,v));invalidate(); }
    public void setMineralFilterEnabled(boolean v){mineralFilterEnabled=v;invalidate();}
    public void setGprStyle(boolean value){gprStyle=value;invalidate();}
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public UltrasoundView(Context c) {
        super(c);
        ColorMaps.configure(c);
        setBackgroundColor(Color.rgb(3,9,14));
    }

    public void setGain(float v){displayGain=Math.max(.2f,Math.min(3f,v));invalidate();}
    public void setPalette(int v) { palette = v; invalidate(); }

    public void clear() {
        for (int i = 0; i < N; i++) used[i] = false;
        head = 0; count = 0; maxAbs = 18f; invalidate();
    }

    public void add(SensorFrame f,float delta){append(delta);postInvalidateOnAnimation();}
    public void addBatch(List<SensorFrame> frames,List<Float> deltas){
        if(deltas==null)return;
        int n=frames==null?deltas.size():Math.min(frames.size(),deltas.size());
        for(int i=0;i<n;i++)append(deltas.get(i));
        if(n>0)postInvalidateOnAnimation();
    }

    private void append(float delta){
        d[head]=delta; used[head]=true;
        head=(head+1)%N; count=Math.min(N,count+1);
        // Hızlı tepki: eski tepe değeri çabuk sönsün, yeni anomali renk skalasını hemen kullansın.
        maxAbs=Math.max(18f,maxAbs*.975f);
        maxAbs=Math.max(maxAbs,Math.abs(delta));
    }

    /**
     * Continuous waterfall display. New sensor samples start at the BOTTOM. Older
     * rows advance upwards and disappear beyond the top edge, never accumulating.
     * Pixel-space sample pitch stays fixed so new samples never stretch the whole
     * history (the previous implementation recalculated its scale every frame).
     */
    @Override protected void onDraw(Canvas c){
        long monitorDrawStart=perfMonitor==null?0:perfMonitor.drawBegin();
        try {

        super.onDraw(c);
        final int w=getWidth(), h=getHeight();
        if(w<=0||h<=0||count==0)return;
        final int cols=144;
        final int rows=Math.max(1,Math.min(h,1024));
        final float pixelsPerSample=gprStyle ? 12.0f : 288.0f; // NORMAL: v3.9.22 mevcut hizin %200 uzerine cikti (3 kat); GPR ayni
        final float scale=(float)h/rows;
        android.graphics.Bitmap bm=android.graphics.Bitmap.createBitmap(cols,rows,android.graphics.Bitmap.Config.RGB_565);
        // Match the visible step size to physical pixels, regardless of screen height.
        final float rowPitch=pixelsPerSample/Math.max(.01f,scale);
        final int background=Color.rgb(3,9,14);
        for(int y=0;y<rows;y++){
            float age=(rows-1-y)/rowPitch; // alt = yeni veri, ust = eski veri
            if(age>count-1){
                for(int x=0;x<cols;x++)bm.setPixel(x,y,background);
                continue;
            }
            int j=(int)age;
            float t=age-j;
            float a=d[(head-1-j+N)%N];
            float b=d[(head-1-Math.min(count-1,j+1)+N)%N];
            // Smoothstep eliminates visible horizontal cell boundaries.
            t=t*t*(3f-2f*t);
            float signal=a+(b-a)*t;
            // GPR-style view only: derive a low-frequency trend and a smoothed broad
            // anomaly from measured magnetic samples. NEVER synthesize depth layers.
            // A trailing window avoids future sample leakage during live acquisition.
            if(gprStyle){
                int nearest=(int)Math.floor(age);
                int window=Math.min(24,count);
                float baseline=0f, broad=0f;
                int bn=0, sn=0;
                for(int k=0;k<window;k++){
                    int idx=(head-1-Math.min(count-1,nearest+k)+N)%N;
                    baseline+=d[idx]; bn++;
                }
                if(bn>0)baseline/=bn;
                int smoothWindow=Math.min(9,count);
                for(int k=0;k<smoothWindow;k++){
                    int idx=(head-1-Math.min(count-1,nearest+k)+N)%N;
                    broad+=d[idx]; sn++;
                }
                if(sn>0)broad/=sn;
                float source=signal;
                // Trend suppression reduces slowly changing geological background,
                // but can also suppress very large legitimate targets.
                if(mineralFilterEnabled)source-=baseline*.85f;
                float broadPart=mineralFilterEnabled?(broad-baseline*.85f):broad;
                // Noise gate: do not amplify tiny changes near the reference.
                float noiseGate=Math.max(.8f,maxAbs*.035f);
                float extra=Math.signum(broadPart)*Math.max(0f,Math.abs(broadPart)-noiseGate);
                signal=source+(broadAnomalyBoost-1f)*extra;
            }
            float magnitude=Math.min(1f,Math.abs(signal)*displayGain/Math.max(1f,maxAbs));
            float breadth=.15f+.16f*magnitude;
            for(int x=0;x<cols;x++){
                float nx=(x+.5f)/cols;
                // Low-amplitude lateral drift makes the trace continuous and fluid;
                // it does not alter the measured value or depth computation.
                float drift=.012f*(float)Math.sin(age*.11f)+.006f*(float)Math.sin(age*.035f);
                float q=(nx-.5f-drift)/Math.max(.05f,breadth);
                float envelope=(float)Math.exp(-2.6f*q*q);
                float local=signal*envelope*displayGain;
                int color;
                if(gprStyle){
                    float intensity=Math.min(1f,Math.abs(local)/Math.max(1f,maxAbs));
                    color=intensity<.025f?Color.rgb(5,14,18):ColorMaps.color(palette,local,maxAbs);
                }else{
                    color=envelope<.02f?Color.rgb(5,14,18):ColorMaps.color(palette,local,maxAbs);
                }
                bm.setPixel(x,y,color);
            }
        }
        p.setFilterBitmap(true);
        c.drawBitmap(bm,null,new android.graphics.Rect(0,0,w,h),p);
        bm.recycle();
    
        } finally {if(perfMonitor!=null)perfMonitor.drawEnd(monitorDrawStart);}
    }
}
