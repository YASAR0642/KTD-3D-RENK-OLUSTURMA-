package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;
import com.ktd.analyzer.gpr.GprProtocol;
import java.util.List;

/** Four A-scan trace profiles, sampled from the actual visible radargram. */
public final class GprTracePanelsView extends View {
    private final GprRadargramView radar;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);

    private int activePanel=-1;
    private float startX, startGain;
    @Override public boolean onTouchEvent(MotionEvent e){
        if(getHeight()<=0)return false;
        int panel=Math.max(0,Math.min(3,(int)(4*e.getY()/getHeight())));
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                activePanel=panel;startX=e.getX();startGain=radar.getDepthGain(panel);return true;
            case MotionEvent.ACTION_MOVE:
                if(activePanel>=0){
                    float delta=(e.getX()-startX)/Math.max(1f,getWidth());
                    radar.setDepthGain(activePanel,Math.max(0f,Math.min(10f,startGain+delta*10f)));
                    invalidate();return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                performClick();activePanel=-1;return true;
            case MotionEvent.ACTION_CANCEL:
                activePanel=-1;return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public GprTracePanelsView(Context c,GprRadargramView r){super(c);radar=r;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float dp=getResources().getDisplayMetrics().density;
        int w=getWidth(),h=getHeight();
        pen.setStyle(Paint.Style.FILL);pen.setColor(Color.rgb(1,6,9));canvas.drawRect(0,0,w,h,pen);
        List<GprProtocol.Trace> tr=radar.snapshot();
        for(int box=0;box<4;box++){
            float y0=box*h/4f,y1=(box+1)*h/4f;
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp*.7f);pen.setColor(Color.rgb(60,94,110));
            canvas.drawRect(1,y0+1,w-1,y1-1,pen);
            float centerX=w*.5f;
            pen.setColor(Color.rgb(138,50,49));canvas.drawLine(centerX,y0+6*dp,centerX,y1-8*dp,pen);
            pen.setStyle(Paint.Style.FILL);pen.setTypeface(android.graphics.Typeface.MONOSPACE);
            pen.setTextSize(dp*10f);pen.setColor(Color.rgb(185,215,230));
            canvas.drawText("G"+(box+1)+"  "+String.format(java.util.Locale.US,"%.1f×",radar.getDepthGain(box)),4*dp,y0+12*dp,pen);
            if(tr.isEmpty())continue;
            // Each panel is a successive 25% depth interval, not a different trace location.
            int at=tr.size()-1;
            short[] samples=tr.get(at).samples;
            int count=samples.length;
            if(radar.hasTimeAxis()&&Double.isFinite(radar.getTimeWindowNs()))
                count=Math.max(2,Math.min(count,1+(int)(radar.getTimeWindowNs()/radar.getSampleIntervalNs())));
            // Shared amplitude reference avoids per-panel auto-normalization making every trace identical.
            int max=1;
            for(GprProtocol.Trace t:tr){
                for(int i=0;i<Math.min(count,t.samples.length);i++)max=Math.max(max,Math.abs((int)t.samples[i]));
            }
            int start=box*count/4;
            int end=Math.max(start+1,(box+1)*count/4);
            float lim=Math.max(2,(y1-y0)-24*dp);
            float halfW=(w-16*dp)*.43f;
            float oldX=centerX,oldY=y0+18*dp;
            pen.setStyle(Paint.Style.STROKE);pen.setColor(Color.WHITE);pen.setStrokeWidth(dp*.9f);
            for(int i=start;i<end;i++){
                float xx=centerX+Math.max(-halfW,Math.min(halfW,samples[i]*halfW*radar.getDepthGain(box)/max));
                float yy=y0+17*dp+lim*(i-start)/Math.max(1,end-start-1);
                if(i>start)canvas.drawLine(oldX,oldY,xx,yy,pen);
                oldX=xx;oldY=yy;
            }
        }
    }
}
