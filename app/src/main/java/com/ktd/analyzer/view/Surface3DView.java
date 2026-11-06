package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.ktd.analyzer.Prefs;
import com.ktd.analyzer.data.ScanData;

public final class Surface3DView extends View {
    public static final int ADVANCED=0,SURFACE=1,CONTOUR=2,VLAYER=3,WIREFRAME=4;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private ScanData data; private int palette=0,mode=ADVANCED; private float yaw=-35f,pitch=56f,zoom=1f;private float lastX,lastY;private final ScaleGestureDetector scaler;
    public Surface3DView(Context c){super(c);ColorMaps.configure(c);setBackgroundColor(Color.rgb(5,11,16));palette=Prefs.palette(c);scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.55f,Math.min(3f,zoom*d.getScaleFactor()));invalidate();return true;}});}
    public void setData(ScanData d){data=d;invalidate();}
    public void setPalette(int p){palette=p;invalidate();}
    public void setMode(int m){mode=m;invalidate();}
    public void resetCamera(){yaw=-35;pitch=56;zoom=1;invalidate();}
    protected void onDraw(Canvas c){super.onDraw(c);if(data==null){drawEmpty(c);return;}if(mode==CONTOUR){drawContour(c);return;}if(mode==VLAYER){drawVLayer(c);return;}drawSurface(c);}
    private void drawEmpty(Canvas c){paint.setColor(Color.rgb(120,150,170));paint.setTextSize(32);paint.setTextAlign(Paint.Align.CENTER);c.drawText("3D tarama alanı",getWidth()/2f,getHeight()/2f,paint);}
    private float val(int r,int co){if(data==null||r<0||co<0||r>=data.rows||co>=data.cols||!data.filled[r][co])return 0;float d=data.delta[r][co];return Math.abs(d)<=10?0:d;}
    private float[] project(float x,float y,float z){double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);float x1=(float)(x*Math.cos(yr)-y*Math.sin(yr));float y1=(float)(x*Math.sin(yr)+y*Math.cos(yr));float y2=(float)(y1*Math.cos(pr)-z*Math.sin(pr));float s=Math.min(getWidth(),getHeight())*.36f*zoom;return new float[]{getWidth()/2f+x1*s,getHeight()*.54f+y2*s};}
    private void drawSurface(Canvas c){int R=data.rows,C=data.cols;float max=data.maxAbs();float gain=Prefs.heightGain(getContext());boolean fill=mode!=WIREFRAME;for(int sum=0;sum<R+C-2;sum++){for(int r=0;r<R-1;r++){int co=sum-r;if(co<0||co>=C-1)continue;float x0=-1f+2f*co/(C-1f),x1=-1f+2f*(co+1)/(C-1f);float y0=-1f+2f*r/(R-1f),y1=-1f+2f*(r+1)/(R-1f);float z00=val(r,co)/max*.72f*gain,z10=val(r,co+1)/max*.72f*gain,z11=val(r+1,co+1)/max*.72f*gain,z01=val(r+1,co)/max*.72f*gain;float[] a=project(x0,y0,z00),b=project(x1,y0,z10),d=project(x0,y1,z01),e=project(x1,y1,z11);Path p=new Path();p.moveTo(a[0],a[1]);p.lineTo(b[0],b[1]);p.lineTo(e[0],e[1]);p.lineTo(d[0],d[1]);p.close();float avg=(val(r,co)+val(r,co+1)+val(r+1,co+1)+val(r+1,co))/4f;if(fill){paint.setStyle(Paint.Style.FILL);int pc=ColorMaps.color(palette,avg,palette==13?120f:max);paint.setColor(pc);paint.setAlpha(Math.min(Math.min(mode==ADVANCED?235:215,ColorMaps.recommendedAlpha(palette)),Color.alpha(pc)));c.drawPath(p,paint);}if(Prefs.grid(getContext())||mode==WIREFRAME){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(mode==WIREFRAME?2.4f:1.1f);paint.setColor(Color.argb(mode==WIREFRAME?230:165,240,247,252));c.drawPath(p,paint);}}}paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(Color.argb(180,24,216,255));drawBox(c);paint.setStyle(Paint.Style.FILL);}
    private void drawBox(Canvas c){float[][] q={project(-1,-1,0),project(1,-1,0),project(1,1,0),project(-1,1,0)};Path p=new Path();p.moveTo(q[0][0],q[0][1]);for(int i=1;i<4;i++)p.lineTo(q[i][0],q[i][1]);p.close();c.drawPath(p,paint);}
    private void drawContour(Canvas c){float pad=28,max=data.maxAbs();float cw=(getWidth()-pad*2)/data.cols,ch=(getHeight()-pad*2)/data.rows;for(int r=0;r<data.rows;r++)for(int co=0;co<data.cols;co++){float v=val(r,co);paint.setStyle(Paint.Style.FILL);paint.setColor(ColorMaps.color(palette,v,palette==13?120f:max));c.drawRect(pad+co*cw,pad+r*ch,pad+(co+1)*cw+1,pad+(r+1)*ch+1,paint);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(Color.argb(145,255,255,255));for(int r=0;r<=data.rows;r++)c.drawLine(pad,pad+r*ch,getWidth()-pad,pad+r*ch,paint);for(int co=0;co<=data.cols;co++)c.drawLine(pad+co*cw,pad,pad+co*cw,getHeight()-pad,paint);}
    private void drawVLayer(Canvas c){int row=Math.max(0,Math.min(data.rows-1,data.rows/2));float max=data.maxAbs();float pad=36,base=getHeight()*.72f,w=(getWidth()-pad*2)/Math.max(1,data.cols-1);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);Path line=new Path();for(int co=0;co<data.cols;co++){float v=val(row,co);float y=base-v/max*getHeight()*.38f*Prefs.heightGain(getContext());float x=pad+co*w;if(co==0)line.moveTo(x,y);else line.lineTo(x,y);paint.setColor(ColorMaps.color(palette,v,palette==13?120f:max));c.drawCircle(x,y,7,paint);}paint.setColor(Color.WHITE);c.drawPath(line,paint);paint.setStrokeWidth(1);paint.setColor(Color.argb(110,255,255,255));c.drawLine(pad,base,getWidth()-pad,base,paint);}
    public boolean onTouchEvent(MotionEvent e){scaler.onTouchEvent(e);if(e.getPointerCount()>1)return true;switch(e.getAction()){case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();return true;case MotionEvent.ACTION_MOVE:float dx=e.getX()-lastX,dy=e.getY()-lastY;yaw+=dx*.35f;pitch=Math.max(15,Math.min(82,pitch+dy*.25f));lastX=e.getX();lastY=e.getY();invalidate();return true;}return true;}
}
