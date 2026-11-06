package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.ktd.analyzer.Prefs;
import com.ktd.analyzer.data.ScanData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Profesyonel, ışıklandırılmış yoğun 3D yüzey renderer'ı. Renk=anomali, Z=cihazın gerçek derinliği. */
public final class ProSurfaceGLView extends GLSurfaceView {
    public static final int ADVANCED=0, SURFACE=1, CONTOUR=2;
    private final SurfaceRenderer renderer;
    private final ScaleGestureDetector scaler;
    private AxisOverlayView axisOverlay;
    private float lastX,lastY,lastMidX,lastMidY;

    public ProSurfaceGLView(Context c){
        super(c);
        ColorMaps.configure(c);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,16,0);
        renderer=new SurfaceRenderer(c);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){renderer.zoom=clamp(renderer.zoom*d.getScaleFactor(),.48f,3.2f);syncAxes();requestRender();return true;}
        });
    }
    public void setDisplayDepthRange(float meters){renderer.displayRange=Math.max(1f,meters);renderer.dirty=true;if(axisOverlay!=null)axisOverlay.setDisplayDepthRange(meters);requestRender();}
    public void setData(ScanData d){renderer.data=d;renderer.dirty=true;if(axisOverlay!=null)axisOverlay.setData(d);requestRender();}
    public void notifyDataChanged(){renderer.dirty=true;requestRender();}
    public void setPalette(int p){renderer.palette=p;renderer.dirty=true;requestRender();}
    public void setSmoothing(boolean on){renderer.smoothing=on;renderer.dirty=true;requestRender();}
    public void setWireframe(boolean on){renderer.showWireframe=on;requestRender();}
    public void setDisplayGain(float g){renderer.displayGain=clamp(g,.2f,3f);renderer.dirty=true;requestRender();}
    /** Derinlik katmanı seçimi (metre). */
    public void setDepthSliceMeters(float v){renderer.depthSliceMeters=Math.max(0f,v);renderer.dirty=true;requestRender();}
    public void setModelDepthMeters(float depth,float axisMax){
        renderer.modelDepthMeters=depth;renderer.modelAxisMeters=axisMax;
        renderer.dirty=true;if(axisOverlay!=null)axisOverlay.setModelDepthMeters(depth,axisMax);requestRender();
    }
    /** Geriye uyumluluk için eski API: oranı derinlik katmanına dönüştürür. */
    public void setSectionLevel(float v){float md=renderer.data==null?0f:renderer.data.maxDepthMeters();renderer.depthSliceMeters=Math.max(0f,Math.min(md,md*clamp(v,0f,.90f)));renderer.dirty=true;requestRender();}
    public void setMode(int m){renderer.mode=m;renderer.dirty=true;if(m==CONTOUR){renderer.yaw=0;renderer.pitch=88;}requestRender();}
    public void resetCamera(){renderer.yaw=-34;renderer.pitch=42;renderer.zoom=1.18f;renderer.panX=0;renderer.panY=0;syncAxes();requestRender();}
    public void setAxisOverlay(AxisOverlayView v){axisOverlay=v;if(v!=null){v.setVolumeMode(false);v.setData(renderer.data);syncAxes();}}
    private void syncAxes(){if(axisOverlay!=null)axisOverlay.setCamera(renderer.yaw,renderer.pitch,renderer.zoom,renderer.panX,renderer.panY);}

    @Override public boolean onTouchEvent(MotionEvent e){
        scaler.onTouchEvent(e);
        final int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            lastX=e.getX();lastY=e.getY();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){
            lastMidX=(e.getX(0)+e.getX(1))*.5f;
            lastMidY=(e.getY(0)+e.getY(1))*.5f;
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2){
                float mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f;
                float unit=2.8f/Math.max(1f,Math.min(getWidth(),getHeight()));
                renderer.panX+=(mx-lastMidX)*unit/Math.max(.48f,renderer.zoom);
                renderer.panY-=(my-lastMidY)*unit/Math.max(.48f,renderer.zoom);
                lastMidX=mx;lastMidY=my;syncAxes();requestRender();return true;
            }
            renderer.yaw=wrap(renderer.yaw+(e.getX()-lastX)*.35f);
            renderer.pitch=wrap(renderer.pitch+(e.getY()-lastY)*.35f);
            lastX=e.getX();lastY=e.getY();syncAxes();requestRender();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP && e.getPointerCount()>=2){
            int keep=e.getActionIndex()==0?1:0;
            lastX=e.getX(keep);lastY=e.getY(keep);
        }
        return true;
    }
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    private static float wrap(float v){v%=360f;if(v>180f)v-=360f;if(v<-180f)v+=360f;return v;}

    private static final class SurfaceRenderer implements GLSurfaceView.Renderer{
        volatile ScanData data; volatile int palette=0,mode=ADVANCED; volatile boolean dirty=true;
        volatile boolean smoothing=true,showWireframe=true;
        volatile float yaw=-34,pitch=42,zoom=1,panX=0,panY=0,depthSliceMeters=0f,displayRange=5f;
        volatile float modelDepthMeters=Float.NaN,modelAxisMeters=Float.NaN,displayGain=1f;
        private final Context ctx;
        private int width=1,height=1,program,posLoc,normLoc,colorLoc,mvpLoc,modelLoc;
        private FloatBuffer triPos,triNorm,triColor,linePos; private int triCount,lineCount;
        private final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];

        SurfaceRenderer(Context c){ctx=c.getApplicationContext();}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(.018f,.035f,.05f,1f);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            program=createProgram(VS,FS);posLoc=GLES20.glGetAttribLocation(program,"aPos");normLoc=GLES20.glGetAttribLocation(program,"aNorm");colorLoc=GLES20.glGetAttribLocation(program,"aColor");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");modelLoc=GLES20.glGetUniformLocation(program,"uModel");
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}
        @Override public void onDrawFrame(GL10 gl){
            if(dirty){buildMesh();dirty=false;}
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);
            float aspect=width/(float)Math.max(1,height);float camera=AxisOverlayView.cameraDistanceFor(data,false,displayRange);float far=Math.max(120f,camera+AxisOverlayView.depthExtentFor(data,displayRange)*4f+20f);Matrix.perspectiveM(proj,0,40f,aspect,.08f,far);Matrix.setLookAtM(view,0,0,0,camera,0,0,0,0,1,0);Matrix.translateM(view,0,panX,panY,AxisOverlayView.depthExtentFor(data,displayRange)*.36f);Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,pitch,1,0,0);Matrix.rotateM(model,0,yaw,0,1,0);Matrix.scaleM(model,0,zoom,zoom,zoom);Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,proj,0,mv,0);
            GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniformMatrix4fv(modelLoc,1,false,model,0);
            if(triPos!=null&&triCount>0)drawTriangles(); if(linePos!=null&&lineCount>0&&mode!=CONTOUR&&showWireframe)drawLines();
        }
        private void drawTriangles(){
            triPos.position(0);triNorm.position(0);triColor.position(0);
            GLES20.glEnableVertexAttribArray(posLoc);GLES20.glEnableVertexAttribArray(normLoc);GLES20.glEnableVertexAttribArray(colorLoc);
            GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,triPos);GLES20.glVertexAttribPointer(normLoc,3,GLES20.GL_FLOAT,false,0,triNorm);GLES20.glVertexAttribPointer(colorLoc,4,GLES20.GL_FLOAT,false,0,triColor);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,triCount);
            GLES20.glDisableVertexAttribArray(posLoc);GLES20.glDisableVertexAttribArray(normLoc);GLES20.glDisableVertexAttribArray(colorLoc);
        }
        private void drawLines(){
            linePos.position(0);GLES20.glEnableVertexAttribArray(posLoc);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,linePos);GLES20.glDisableVertexAttribArray(normLoc);GLES20.glDisableVertexAttribArray(colorLoc);GLES20.glVertexAttrib3f(normLoc,0,0,1);GLES20.glVertexAttrib4f(colorLoc,.78f,.88f,.93f,.34f);GLES20.glLineWidth(1f);GLES20.glDrawArrays(GLES20.GL_LINES,0,lineCount);GLES20.glDisableVertexAttribArray(posLoc);
        }
        private void buildMesh(){
            ScanData d=data;if(d==null||d.count==0){triPos=triNorm=triColor=linePos=null;triCount=lineCount=0;return;}
            DenseField f=DenseField.from(d,68,smoothing);int W=f.w,H=f.h;float scale=Math.max(12f,f.robustAbs);
            float[] z=new float[W*H];
            float maxDepth=Math.max(.05f,f.maxDepth);
            boolean depthAvailable=d.hasDepthData();
            float zExtent=AxisOverlayView.depthExtentFor(d,displayRange);
            boolean modelReady=!depthAvailable && Float.isFinite(modelDepthMeters) && modelDepthMeters>0f && Float.isFinite(modelAxisMeters)&&modelAxisMeters>=modelDepthMeters;
            float gain=Prefs.heightGain(ctx)*displayGain;
            float emboss=(mode==SURFACE?.025f:.040f)*gain;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int i=y*W+x;
                if(!f.valid[i]){z[i]=0f;continue;}
                if(mode==CONTOUR){z[i]=0f;continue;}
                float depthM=f.depthValid[i]?Math.max(0f,Math.min(maxDepth,Math.abs(f.depth[i]))):0f;
                float baseZ=-clamp(depthM/displayRange,0f,1f)*zExtent;
                float relief=clamp(f.value[i]/scale,-1.0f,1.0f)*emboss;
                // V3D ham dosyasında C/metre yoksa anomali rölyefini yine göster;
                // bu Z yüksekliği fiziki derinlik değildir.
                z[i]=depthAvailable?(baseZ+relief):(modelReady ? -zExtent*clamp(modelDepthMeters/modelAxisMeters,0f,1f)+relief : clamp(f.value[i]/scale,-1.15f,1.15f)*.44f*gain);
            }
            float[] nx=new float[W*H],ny=new float[W*H],nz=new float[W*H];
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=y*W+x;if(!f.valid[i]){nz[i]=1;continue;}float zl=z[y*W+Math.max(0,x-1)],zr=z[y*W+Math.min(W-1,x+1)],zu=z[Math.max(0,y-1)*W+x],zd=z[Math.min(H-1,y+1)*W+x];float dx=(zr-zl)*.5f,dy=(zd-zu)*.5f;float ax=-dx*18f,ay=dy*18f,az=1f;float len=(float)Math.sqrt(ax*ax+ay*ay+az*az);nx[i]=ax/len;ny[i]=ay/len;nz[i]=az/len;}
            ArrayList<Float> p=new ArrayList<>(),n=new ArrayList<>(),c=new ArrayList<>(),lines=new ArrayList<>();
            float focusDepth=modelReady?0f:Math.max(0f,Math.min(maxDepth,depthSliceMeters));
            float band=Math.max(.25f,Math.min(Math.max(.45f,maxDepth*.22f),Math.max(d.stepCm/100f,maxDepth*.32f)));
            for(int y=0;y<H-1;y++)for(int x=0;x<W-1;x++){
                int a=y*W+x,b=a+1,d0=(y+1)*W+x,e=d0+1;
                if(!(f.valid[a]&&f.valid[b]&&f.valid[d0]&&f.valid[e]))continue;
                if(passDepthSlice(f,focusDepth,band,a,b,e))addTri(p,n,c,f,z,nx,ny,nz,W,H,a,b,e,scale);
                if(passDepthSlice(f,focusDepth,band,a,e,d0))addTri(p,n,c,f,z,nx,ny,nz,W,H,a,e,d0,scale);
            }
            int step=Math.max(2,W/16);
            for(int y=0;y<H;y+=step)for(int x=0;x<W-1;x++){
                int a=y*W+x,b=a+1;
                if(f.valid[a]&&f.valid[b]&&passDepthSlice(f,focusDepth,band,a,b)){
                    addLine(lines,posX(x,W),posY(y,H),z[a],posX(x+1,W),posY(y,H),z[b]);
                }
            }
            for(int x=0;x<W;x+=step)for(int y=0;y<H-1;y++){
                int a=y*W+x,b=(y+1)*W+x;
                if(f.valid[a]&&f.valid[b]&&passDepthSlice(f,focusDepth,band,a,b)){
                    addLine(lines,posX(x,W),posY(y,H),z[a],posX(x,W),posY(y+1,H),z[b]);
                }
            }
            triPos=buffer(p);triNorm=buffer(n);triColor=buffer(c);linePos=buffer(lines);triCount=p.size()/3;lineCount=lines.size()/3;
        }
        private static boolean passDepthSlice(DenseField f,float focusDepth,float band,int... ids){
            if(f==null||ids==null||ids.length==0)return false;
            // 0 metre = tüm anomaliler. Derinlik yokken 3D anomali yüzeyi gizlenmez.
            if(focusDepth<=0f||f.maxDepth<=0f)return true;
            float sum=0f;int n=0;
            for(int i:ids){
                if(i<0||i>=f.value.length||!f.valid[i]||!f.depthValid[i])continue;
                sum+=Math.abs(f.depth[i]);n++;
            }
            if(n==0)return false;
            float avg=sum/n;
            return Math.abs(avg-focusDepth)<=band;
        }
        private void addTri(ArrayList<Float> p,ArrayList<Float> n,ArrayList<Float> c,DenseField f,float[] z,float[] nx,float[] ny,float[] nz,int W,int H,int ia,int ib,int ic,float scale){addV(p,n,c,f,z,nx,ny,nz,W,H,ia,scale);addV(p,n,c,f,z,nx,ny,nz,W,H,ib,scale);addV(p,n,c,f,z,nx,ny,nz,W,H,ic,scale);}
        private void addV(ArrayList<Float> p,ArrayList<Float> n,ArrayList<Float> c,DenseField f,float[] z,float[] nx,float[] ny,float[] nz,int W,int H,int i,float scale){int y=i/W,x=i%W;p.add(posX(x,W));p.add(posY(y,H));p.add(z[i]);n.add(nx[i]);n.add(ny[i]);n.add(nz[i]);int col=ColorMaps.color(palette,f.value[i],palette==13?120f:scale);c.add(Color.red(col)/255f);c.add(Color.green(col)/255f);c.add(Color.blue(col)/255f);float pa=Color.alpha(col)/255f;c.add(pa*(mode==CONTOUR?.98f:.96f));}
        private static float posX(int x,int W){return -1.22f+2.44f*x/Math.max(1f,W-1f);}private static float posY(int y,int H){return 1.02f-2.04f*y/Math.max(1f,H-1f);}private static void addLine(ArrayList<Float>a,float x0,float y0,float z0,float x1,float y1,float z1){a.add(x0);a.add(y0);a.add(z0+.006f);a.add(x1);a.add(y1);a.add(z1+.006f);}private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
        private static FloatBuffer buffer(ArrayList<Float>a){ByteBuffer b=ByteBuffer.allocateDirect(a.size()*4).order(ByteOrder.nativeOrder());FloatBuffer f=b.asFloatBuffer();for(Float v:a)f.put(v);f.position(0);return f;}
        private static int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}private static int createProgram(String vs,String fs){int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
        private static final String VS="attribute vec3 aPos;attribute vec3 aNorm;attribute vec4 aColor;uniform mat4 uMVP;uniform mat4 uModel;varying vec3 vN;varying vec4 vC;varying vec3 vP;void main(){vec4 wp=uModel*vec4(aPos,1.0);vP=wp.xyz;vN=normalize(mat3(uModel)*aNorm);vC=aColor;gl_Position=uMVP*vec4(aPos,1.0);}";
        private static final String FS="precision mediump float;varying vec3 vN;varying vec4 vC;varying vec3 vP;void main(){vec3 L=normalize(vec3(-0.35,0.55,0.78));vec3 V=normalize(vec3(0.0,0.0,1.0));float diff=max(dot(normalize(vN),L),0.0);float spec=pow(max(dot(reflect(-L,normalize(vN)),V),0.0),20.0);float light=0.34+0.72*diff+0.20*spec;gl_FragColor=vec4(vC.rgb*light,vC.a);}";
    }
}
