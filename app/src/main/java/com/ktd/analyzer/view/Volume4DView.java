package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.ktd.analyzer.data.ScanData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * KTD 4D Isosurface.
 *
 * X/Y konumu ve anomali şekli ham Z sensör alanından gelir.
 * Cihazın C kanalı bağımsız ölçüm olarak doğrulanmasa da sayısal metre bildirir; 4D yüzey bu değeri
 * yalnız Z MERKEZİ olarak kullanır. Anomali genliği Z boyunca uzatma yapmaz.
 *
 * Böylece küçük ama güçlü bir yüzey anomalisi uzun bir kolon olmaz:
 *   - XY ayak izi = gerçek grid anomalisi
 *   - Z merkezi   = cihaz C derinliği
 *   - Z kalınlığı = sınırlı Hacim Yayılımı
 */
public final class Volume4DView extends GLSurfaceView {
    private final Renderer4D renderer;
    private final ScaleGestureDetector scaler;
    private AxisOverlayView axisOverlay;
    private float lastX,lastY,lastMidX,lastMidY;

    public Volume4DView(Context c){
        super(c);ColorMaps.configure(c);setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,8,16,0);
        renderer=new Renderer4D();setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);
        scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){renderer.zoom=clamp(renderer.zoom*d.getScaleFactor(),.24f,6.0f);syncAxes();requestRender();return true;}});
    }
    public void setDisplayDepthRange(float meters){renderer.displayRange=Math.max(1f,meters);renderer.dirty=true;if(axisOverlay!=null)axisOverlay.setDisplayDepthRange(meters);requestRender();}
    public void setData(ScanData d){renderer.data=d;renderer.dirty=true;if(axisOverlay!=null)axisOverlay.setData(d);requestRender();}
    public void notifyDataChanged(){renderer.dirty=true;requestRender();}
    public void setPalette(int p){renderer.palette=p;renderer.dirty=true;requestRender();}
    public void setSmoothing(boolean on){renderer.smoothing=on;renderer.dirty=true;requestRender();}
    public void setWireframe(boolean on){renderer.showWireframe=on;requestRender();}
    public void setThreshold(float v){renderer.threshold=clamp(v,.03f,.92f);renderer.dirty=true;requestRender();}
    public void setDepthFocusMeters(float v){renderer.depthFocusMeters=Math.max(0f,v);renderer.dirty=true;requestRender();}
    public void setModelDepthMeters(float depth,float maxDepth){
        renderer.modelDepthMeters=depth;renderer.modelAxisMeters=maxDepth;renderer.dirty=true;
        if(axisOverlay!=null)axisOverlay.setModelDepthMeters(depth,maxDepth);
        requestRender();
    }
    /** Sadece çizim opaklığı; ölçüm ve derinlik verisini değiştirmez. */
    public void setOpacity(float v){renderer.groundOpacity=clamp(v,.02f,.35f);renderer.dirty=true;requestRender();}
    /** Anomali sınırlarının görsel keskinliği (veri korunur). */
    public void setSharpness(float v){renderer.sharpness=clamp(v,.70f,2.50f);renderer.dirty=true;requestRender();}
    public void setSpread(float v){renderer.spread=clamp(v,.60f,1.60f);renderer.dirty=true;requestRender();}
    public void setLayers(int v){renderer.layers=Math.max(1,Math.min(6,v));renderer.dirty=true;requestRender();}
    public void resetCamera(){renderer.yaw=-28;renderer.pitch=25;renderer.zoom=1.18f;renderer.panX=0;renderer.panY=0;syncAxes();requestRender();}
    public void setAxisOverlay(AxisOverlayView v){axisOverlay=v;if(v!=null){v.setVolumeMode(true);v.setData(renderer.data);v.setSpread(renderer.spread);syncAxes();}}
    private void syncAxes(){if(axisOverlay!=null)axisOverlay.setCamera(renderer.yaw,renderer.pitch,renderer.zoom,renderer.panX,renderer.panY);}
    @Override public boolean onTouchEvent(MotionEvent e){
        scaler.onTouchEvent(e);
        final int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){
            lastMidX=(e.getX(0)+e.getX(1))*.5f;lastMidY=(e.getY(0)+e.getY(1))*.5f;return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2){
                float mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f;
                float unit=2.8f/Math.max(1f,Math.min(getWidth(),getHeight()));
                renderer.panX+=(mx-lastMidX)*unit/Math.max(.24f,renderer.zoom);
                renderer.panY-=(my-lastMidY)*unit/Math.max(.24f,renderer.zoom);
                lastMidX=mx;lastMidY=my;syncAxes();requestRender();return true;
            }
            renderer.yaw=wrap(renderer.yaw+(e.getX()-lastX)*.38f);
            renderer.pitch=wrap(renderer.pitch+(e.getY()-lastY)*.38f);
            lastX=e.getX();lastY=e.getY();syncAxes();requestRender();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP && e.getPointerCount()>=2){
            int keep=e.getActionIndex()==0?1:0;lastX=e.getX(keep);lastY=e.getY(keep);
        }
        return true;
    }
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    private static float wrap(float v){v%=360f;if(v>180f)v-=360f;if(v<-180f)v+=360f;return v;}

    private static final class Renderer4D implements GLSurfaceView.Renderer{
        volatile ScanData data;volatile int palette=0;volatile boolean dirty=true;volatile float yaw=-28,pitch=25,zoom=1,panX=0,panY=0;
        volatile boolean smoothing=true,showWireframe=true;
        volatile float threshold=.35f,spread=1.0f,depthFocusMeters=0f,displayRange=5f;volatile int layers=2;
        volatile float groundOpacity=.10f,sharpness=1.50f;
        volatile float modelDepthMeters=Float.NaN,modelAxisMeters=Float.NaN;
        private int width=1,height=1,program,posLoc,normLoc,colorLoc,mvpLoc,modelLoc;
        private final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
        private Mesh[] meshes=new Mesh[0];private Mesh groundShell;private FloatBuffer box,innerGrid;private int boxCount,gridCount;

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){GLES20.glClearColor(.016f,.032f,.045f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_BLEND);program=createProgram(VS,FS);posLoc=GLES20.glGetAttribLocation(program,"aPos");normLoc=GLES20.glGetAttribLocation(program,"aNorm");colorLoc=GLES20.glGetAttribLocation(program,"aColor");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");modelLoc=GLES20.glGetUniformLocation(program,"uModel");buildBox(.62f);}
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}
        @Override public void onDrawFrame(GL10 gl){if(dirty){buildMeshes();dirty=false;}GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);float aspect=width/(float)Math.max(1,height);float camera=AxisOverlayView.cameraDistanceFor(data,true,displayRange);float far=Math.max(80f,camera+AxisOverlayView.volumeExtentFor(data,displayRange)*4f+12f);Matrix.perspectiveM(proj,0,42f,aspect,.08f,far);Matrix.setLookAtM(view,0,0,0,camera,0,0,0,0,1,0);Matrix.translateM(view,0,panX,panY,AxisOverlayView.volumeExtentFor(data,displayRange)*.36f);Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,pitch,1,0,0);Matrix.rotateM(model,0,yaw,0,1,0);Matrix.scaleM(model,0,zoom,zoom,zoom);Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,proj,0,mv,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniformMatrix4fv(modelLoc,1,false,model,0);
            // İç çekirdek önce opak; dış izoyüzeyler ve doğal zemin sonra saydam.
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDepthMask(true);
            for(Mesh m:meshes)if(m.opacity>=.99f)drawMesh(m);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            for(Mesh m:meshes)if(m.opacity<.99f)drawMesh(m);
            if(groundShell!=null)drawMesh(groundShell);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
            if(showWireframe){drawInnerGrid();drawBox();}}

        private void drawMesh(Mesh m){if(m==null||m.count==0)return;m.pos.position(0);m.norm.position(0);m.col.position(0);GLES20.glEnableVertexAttribArray(posLoc);GLES20.glEnableVertexAttribArray(normLoc);GLES20.glEnableVertexAttribArray(colorLoc);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,m.pos);GLES20.glVertexAttribPointer(normLoc,3,GLES20.GL_FLOAT,false,0,m.norm);GLES20.glVertexAttribPointer(colorLoc,4,GLES20.GL_FLOAT,false,0,m.col);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);GLES20.glDisableVertexAttribArray(posLoc);GLES20.glDisableVertexAttribArray(normLoc);GLES20.glDisableVertexAttribArray(colorLoc);}
        /** Kutunun içindeki yatay derinlik düzlemleri ve dikey kesit çizgileri. */
        private void drawInnerGrid(){
            if(innerGrid==null||gridCount==0)return;
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            innerGrid.position(0);
            GLES20.glEnableVertexAttribArray(posLoc);
            GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,innerGrid);
            GLES20.glDisableVertexAttribArray(normLoc);
            GLES20.glDisableVertexAttribArray(colorLoc);
            GLES20.glVertexAttrib3f(normLoc,0,0,1);
            GLES20.glVertexAttrib4f(colorLoc,.42f,.64f,.66f,.22f);
            GLES20.glLineWidth(1.0f);
            GLES20.glDrawArrays(GLES20.GL_LINES,0,gridCount);
            GLES20.glDisableVertexAttribArray(posLoc);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
        }
        private void drawBox(){box.position(0);GLES20.glEnableVertexAttribArray(posLoc);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,box);GLES20.glDisableVertexAttribArray(normLoc);GLES20.glDisableVertexAttribArray(colorLoc);GLES20.glVertexAttrib3f(normLoc,0,0,1);GLES20.glVertexAttrib4f(colorLoc,.58f,.66f,.72f,1f);GLES20.glLineWidth(1.5f);GLES20.glDrawArrays(GLES20.GL_LINES,0,boxCount);GLES20.glDisableVertexAttribArray(posLoc);}

        private void buildMeshes(){
            ScanData d=data;if(d==null||d.count==0){
                meshes=new Mesh[0];
                buildBox(AxisOverlayView.volumeExtentFor(d,displayRange));
                groundShell=buildGroundShell(AxisOverlayView.volumeExtentFor(d,displayRange));
                return;
            }
            DenseField dense=DenseField.from(d,44,smoothing);float posMax=0,negMax=0;for(int i=0;i<dense.value.length;i++)if(dense.valid[i]){float v=dense.value[i];if(v>posMax)posMax=v;if(-v>negMax)negMax=-v;}
            ArrayList<Mesh> out=new ArrayList<>();
            int n=Math.max(1,Math.min(6,layers));
            for(int li=0;li<n;li++){
                float t=n==1?0f:li/(float)(n-1);
                float level=threshold+(.86f-threshold)*t;
                // Dış kütle saydam; merkez yoğun ve opak. Güçlü anomali içeride görünür.
                float a=1f; // Tam dolu anomali yüzeyleri: dış katmanları da opak çiz.
                if(posMax>10.5f)out.add(buildIso(dense,d,true,posMax,level,a));
                if(negMax>10.5f)out.add(buildIso(dense,d,false,negMax,level,a));
            }
            buildBox(AxisOverlayView.volumeExtentFor(d,displayRange));
            // Opak çekirdeği öne almak için diziyi kuvvet sırasına göre ters çevir.
            java.util.Collections.reverse(out);
            meshes=out.toArray(new Mesh[0]);
            groundShell=buildGroundShell(AxisOverlayView.volumeExtentFor(d,displayRange));
        }

        private Mesh buildIso(DenseField dense,ScanData source,boolean positive,float signMax,float levelFrac,float alpha){
            final int NX=layers>=5?22:(layers>=3?26:30);
            final int NY=NX;
            final int NZ=layers>=5?17:(layers>=3?20:24);
            final float ZMAX=AxisOverlayView.volumeExtentFor(source,displayRange);
            final boolean realDepth=source.hasDepthData();
            final boolean modelReady=false; // No unverified model metre on axes
            final float maxDepth=realDepth?Math.max(.05f,source.maxDepthMeters()):
                    modelReady?modelAxisMeters:1f;
            final float spread01=clamp((spread-.60f)/1.00f,0f,1f);
            final float focusDepth=clamp(depthFocusMeters,0f,maxDepth);

            // Kalınlık anomali genliğinden değil fiziksel grid adımından türetilir.
            // 30 cm gridde yaklaşık 0.13..0.30 m sigma aralığı; küçük XY hedefi
            // derin bir kolona dönüşmez.
            final float stepM=Math.max(.05f,source.stepCm/100f);
            // Volume thickness in C-index units: depends on index scale, not invented metres.
            final float sigmaIndex=Math.max(.08f,maxDepth*(.065f+.11f*spread01));
            final float sigmaNorm=clamp(sigmaIndex/maxDepth,.025f,.28f);

            float iso=signMax*levelFrac;
            ArrayList<Float> pos=new ArrayList<>(),norm=new ArrayList<>();
            for(int z=0;z<NZ-1;z++)for(int y=0;y<NY-1;y++)for(int x=0;x<NX-1;x++){
                P[] p=new P[8];float[] v=new float[8];int q=0;
                for(int dz=0;dz<=1;dz++)for(int dy=0;dy<=1;dy++)for(int dx=0;dx<=1;dx++){
                    int xx=x+dx,yy=y+dy,zz=z+dz;
                    float u=xx/(NX-1f),vv=yy/(NY-1f);
                    float px=-1.12f+2.24f*u,py=1.02f-2.04f*vv;
                    float pz=-ZMAX*zz/(NZ-1f);
                    p[q]=new P(px,py,pz);

                    // XY anomali şiddeti
                    float raw=sampleDense(dense,u,vv);
                    float base=positive?Math.max(0f,raw):Math.max(0f,-raw);
                    float bn=clamp(base/Math.max(1f,signMax),0f,1f);

                    // Z merkezi yalnız türetilmemiş C kanalından; tahmini Neutral modelinden DEĞİL.
                    // Z=0,C=0 noktası yüzey merkezidir.
                    float depthM=sampleDenseDepth(dense,u,vv);
                    // Gerçek bir C değeri/interpolasyonu yoksa sahte yüzey derinliği üretme.
                    if(!Float.isFinite(depthM)){
                        // Ham anomali verisiyle yalnız göreli hacim çizimi; metre üretmez.
                        depthM=maxDepth*.5f; // No valid C: display centre in C-index coordinates
                    }
                    depthM=clamp(depthM,0f,maxDepth);
                    float centerNorm=realDepth?clamp(depthM/displayRange,0f,1f):clamp(depthM/maxDepth,0f,1f);

                    float zNorm=clamp((-pz)/Math.max(.001f,ZMAX),0f,1f);
                    float dzNorm=(zNorm-centerNorm)/sigmaNorm;
                    float depthKernel=(float)Math.exp(-.5f*dzNorm*dzNorm);
                    float focusSigma=Math.max(.22f,Math.min(Math.max(.38f,maxDepth*.18f),Math.max(.30f,sigmaIndex*1.8f)));
                    float df=(depthM-focusDepth)/focusSigma;
                    float focusKernel=!realDepth&&!modelReady?1f:
                            depthFocusMeters<=0f?1f:(float)Math.exp(-.5f*df*df);

                    float shaped=(float)Math.pow(bn,.90f*sharpness);
                    if(positive){
                        // Mineral benzeri zayif/orta pozitif alanlar 4D hacimde gereksiz yayilmasin.
                        // Guclu metal benzeri cekirdekler korunur; zayif genis alanlar bastirilir.
                        if(bn<0.22f) shaped*=0.08f;
                        else if(bn<0.34f) shaped*=0.18f;
                        else if(bn<0.46f) shaped*=0.35f;
                        else if(bn<0.58f) shaped*=0.55f;
                    }
                    v[q]=signMax*shaped*depthKernel*focusKernel;
                    q++;
                }
                int[][] t={{0,1,3,7},{0,3,2,7},{0,2,6,7},{0,6,4,7},{0,4,5,7},{0,5,1,7}};
                for(int[] a:t)polygoniseTet(p,v,a,iso,pos,norm);
            }
            // FINAL: 4D yüzey tek renk değildir. 3D ile aynı ColorMaps paletini
            // her vertex'in gerçek XY ham anomali değerine uygular; yüzey opaktır.
            ArrayList<Float> colors=new ArrayList<>();
            float colorScale=Math.max(12f,dense.robustAbs);
            for(int i=0;i+2<pos.size();i+=3){
                float px=pos.get(i),py=pos.get(i+1);
                float u=clamp((px+1.12f)/2.24f,0f,1f);
                float vv=clamp((1.02f-py)/2.04f,0f,1f);
                float raw=sampleDense(dense,u,vv);
                int col=ColorMaps.color(palette,raw,palette==13?120f:colorScale);
                colors.add(Color.red(col)/255f);
                colors.add(Color.green(col)/255f);
                colors.add(Color.blue(col)/255f);
                colors.add(alpha);
            }
            return new Mesh(buffer(pos),buffer(norm),buffer(colors),pos.size()/3,alpha);
        }

        private static float sampleDense(DenseField d,float u,float v){
            float gx=u*(d.w-1),gy=v*(d.h-1);
            int x0=(int)Math.floor(gx),y0=(int)Math.floor(gy),x1=Math.min(d.w-1,x0+1),y1=Math.min(d.h-1,y0+1);
            x0=Math.max(0,x0);y0=Math.max(0,y0);float tx=gx-x0,ty=gy-y0;
            float a=lerp(d.get(x0,y0),d.get(x1,y0),tx),b=lerp(d.get(x0,y1),d.get(x1,y1),tx);
            return lerp(a,b,ty);
        }

        private static float sampleDenseDepth(DenseField d,float u,float v){
            float gx=u*(d.w-1),gy=v*(d.h-1);
            int x0=(int)Math.floor(gx),y0=(int)Math.floor(gy),x1=Math.min(d.w-1,x0+1),y1=Math.min(d.h-1,y0+1);
            x0=Math.max(0,x0);y0=Math.max(0,y0);float tx=gx-x0,ty=gy-y0;

            float d00=d.getDepth(x0,y0),d10=d.getDepth(x1,y0),d01=d.getDepth(x0,y1),d11=d.getDepth(x1,y1);
            boolean v00=d.isDepthValid(x0,y0),v10=d.isDepthValid(x1,y0),v01=d.isDepthValid(x0,y1),v11=d.isDepthValid(x1,y1);
            if(v00&&v10&&v01&&v11){
                float a=lerp(d00,d10,tx),b=lerp(d01,d11,tx);return lerp(a,b,ty);
            }

            // Kısmi/eksik derinlikte en yakın geçerli komşuyu kullan.
            float best=Float.NaN,bd=Float.MAX_VALUE;
            int cx=Math.round(gx),cy=Math.round(gy);
            for(int yy=Math.max(0,cy-2);yy<=Math.min(d.h-1,cy+2);yy++)
                for(int xx=Math.max(0,cx-2);xx<=Math.min(d.w-1,cx+2);xx++){
                    if(!d.isDepthValid(xx,yy))continue;
                    float dx=xx-gx,dy=yy-gy,dd=dx*dx+dy*dy;
                    if(dd<bd){bd=dd;best=d.getDepth(xx,yy);}
                }
            return best;
        }
        private static float lerp(float a,float b,float t){return a+(b-a)*t;}

        private static void polygoniseTet(P[] p,float[] v,int[] t,float iso,ArrayList<Float> out,ArrayList<Float> normals){int[] in=new int[4],ou=new int[4];int ni=0,no=0;for(int k:t){if(v[k]>=iso)in[ni++]=k;else ou[no++]=k;}if(ni==0||ni==4)return;if(ni==1||ni==3){boolean flip=ni==3;int center=flip?ou[0]:in[0];int[] other=flip?in:ou;P a=edge(p[center],p[other[0]],v[center],v[other[0]],iso),b=edge(p[center],p[other[1]],v[center],v[other[1]],iso),c=edge(p[center],p[other[2]],v[center],v[other[2]],iso);if(flip)addTri(out,normals,a,c,b);else addTri(out,normals,a,b,c);return;}int i0=in[0],i1=in[1],o0=ou[0],o1=ou[1];P a=edge(p[i0],p[o0],v[i0],v[o0],iso),b=edge(p[i0],p[o1],v[i0],v[o1],iso),c=edge(p[i1],p[o0],v[i1],v[o0],iso),d=edge(p[i1],p[o1],v[i1],v[o1],iso);addTri(out,normals,a,b,d);addTri(out,normals,a,d,c);}
        private static P edge(P a,P b,float va,float vb,float iso){float den=vb-va,t=Math.abs(den)<1e-6f?.5f:(iso-va)/den;t=Math.max(0,Math.min(1,t));return new P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}
        private static void addTri(ArrayList<Float> out,ArrayList<Float> normals,P a,P b,P c){float ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(len<1e-7f)return;nx/=len;ny/=len;nz/=len;add(out,a);add(out,b);add(out,c);for(int i=0;i<3;i++){normals.add(nx);normals.add(ny);normals.add(nz);}}
        private static void add(ArrayList<Float> a,P p){a.add(p.x);a.add(p.y);a.add(p.z);}

        /** Saydam dış doğal zemin bloğu: gerçek doku/jeoloji iddiası olmadan alan sınırı. */
        private Mesh buildGroundShell(float depthExtent){
            float z=-Math.max(.10f,depthExtent),a=1.15f,b=1.05f;
            P[] v={new P(-a,-b,z),new P(a,-b,z),new P(a,b,z),new P(-a,b,z),
                   new P(-a,-b,0),new P(a,-b,0),new P(a,b,0),new P(-a,b,0)};
            int[][] faces={{4,5,6,7},{0,3,2,1},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}};
            ArrayList<Float> pos=new ArrayList<>(),norm=new ArrayList<>(),col=new ArrayList<>();
            for(int i=0;i<faces.length;i++){
                int[] f=faces[i];ArrayList<Float> fp=new ArrayList<>(),fn=new ArrayList<>();
                addTri(fp,fn,v[f[0]],v[f[1]],v[f[2]]);
                addTri(fp,fn,v[f[0]],v[f[2]],v[f[3]]);
                float faceAlpha=groundOpacity*(i==0?.62f:(i==1?.40f:1f));
                for(int k=0;k<fp.size();k++)pos.add(fp.get(k));
                for(int k=0;k<fn.size();k++)norm.add(fn.get(k));
                for(int k=0;k<fp.size()/3;k++){
                    // Soluk kaya/toprak tonu; saydam duvarın içinde anomaliler baskın kalır.
                    col.add(.42f);col.add(.52f);col.add(.50f);col.add(faceAlpha);
                }
            }
            return new Mesh(buffer(pos),buffer(norm),buffer(col),pos.size()/3,groundOpacity);
        }
        private void buildBox(float depthExtent){float a=1.15f,z0=0f,zb=-Math.max(.10f,depthExtent);float[][]v={{-a,-1.05f,zb},{a,-1.05f,zb},{a,1.05f,zb},{-a,1.05f,zb},{-a,-1.05f,z0},{a,-1.05f,z0},{a,1.05f,z0},{-a,1.05f,z0}};int[][]e={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};ArrayList<Float>o=new ArrayList<>();for(int[] q:e)for(int k:q){o.add(v[k][0]);o.add(v[k][1]);o.add(v[k][2]);}box=buffer(o);boxCount=o.size()/3;
            ArrayList<Float> lines=new ArrayList<>();
            for(int i=1;i<=3;i++){
                float zz=zb*i/4f;
                float[][] ring={{-a,-1.05f,zz},{a,-1.05f,zz},{a,1.05f,zz},{-a,1.05f,zz}};
                for(int k=0;k<4;k++){
                    float[] q=ring[k],t=ring[(k+1)%4];
                    lines.add(q[0]);lines.add(q[1]);lines.add(q[2]);
                    lines.add(t[0]);lines.add(t[1]);lines.add(t[2]);
                }
                float[][] crosses={{-a,0,zz},{a,0,zz},{0,-1.05f,zz},{0,1.05f,zz}};
                for(int k=0;k<4;k+=2){for(int j=0;j<2;j++){
                    lines.add(crosses[k+j][0]);lines.add(crosses[k+j][1]);lines.add(crosses[k+j][2]);
                }}
            }
            innerGrid=buffer(lines);gridCount=lines.size()/3;
        }
        private static FloatBuffer buffer(ArrayList<Float>a){ByteBuffer b=ByteBuffer.allocateDirect(a.size()*4).order(ByteOrder.nativeOrder());FloatBuffer f=b.asFloatBuffer();for(Float v:a)f.put(v);f.position(0);return f;}
        private static int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}private static int createProgram(String vs,String fs){int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
        private static final String VS="attribute vec3 aPos;attribute vec3 aNorm;attribute vec4 aColor;uniform mat4 uMVP;uniform mat4 uModel;varying vec3 vN;varying vec4 vColor;void main(){vN=normalize(mat3(uModel)*aNorm);vColor=aColor;gl_Position=uMVP*vec4(aPos,1.0);}";
        private static final String FS="precision mediump float;varying vec3 vN;varying vec4 vColor;void main(){vec3 L=normalize(vec3(-0.35,0.55,0.78));float d=max(dot(normalize(vN),L),0.0);float edge=pow(1.0-abs(normalize(vN).z),2.0);float light=0.70+0.38*d+0.08*edge;gl_FragColor=vec4(min(vec3(1.0),vColor.rgb*light),vColor.a);}";
        private static final class P{final float x,y,z;P(float x,float y,float z){this.x=x;this.y=y;this.z=z;}}
        private static final class Mesh{final FloatBuffer pos,norm,col;final int count;final float opacity;Mesh(FloatBuffer p,FloatBuffer n,FloatBuffer c,int count,float opacity){pos=p;norm=n;col=c;this.count=count;this.opacity=opacity;}}
    }
}
