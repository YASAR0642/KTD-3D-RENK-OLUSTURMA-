package com.ktd.analyzer.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import com.ktd.analyzer.Prefs;
import com.ktd.analyzer.view.ColorMaps;
import com.ktd.analyzer.view.DenseField;

import java.io.File;
import java.io.FileOutputStream;

/** Kayıt kartı için yoğun interpolasyonlu 3D PNG önizleme. */
public final class PreviewUtil {
    private PreviewUtil(){}

    public static void save3dPng(Context c, ScanData data, int palette, File out)throws Exception{
        final int size=900;
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.rgb(5,11,16));
        DenseField f=DenseField.from(data,64);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float max=Math.max(12f,f.robustAbs);float gain=Prefs.heightGain(c);
        double yaw=Math.toRadians(-34),pitch=Math.toRadians(52);float s=size*.34f;
        for(int sum=0;sum<f.w+f.h-2;sum++)for(int y=0;y<f.h-1;y++){int x=sum-y;if(x<0||x>=f.w-1)continue;int a=y*f.w+x,bb=a+1,d=(y+1)*f.w+x,e=d+1;if(!(f.valid[a]&&f.valid[bb]&&f.valid[d]&&f.valid[e]))continue;float[] pa=pr(x,y,z(f,a,max,gain),f.w,f.h,size,s,yaw,pitch),pb=pr(x+1,y,z(f,bb,max,gain),f.w,f.h,size,s,yaw,pitch),pd=pr(x,y+1,z(f,d,max,gain),f.w,f.h,size,s,yaw,pitch),pe=pr(x+1,y+1,z(f,e,max,gain),f.w,f.h,size,s,yaw,pitch);Path path=new Path();path.moveTo(pa[0],pa[1]);path.lineTo(pb[0],pb[1]);path.lineTo(pe[0],pe[1]);path.lineTo(pd[0],pd[1]);path.close();float v=(f.value[a]+f.value[bb]+f.value[d]+f.value[e])*.25f;p.setStyle(Paint.Style.FILL);int pc=ColorMaps.color(palette,v,max);p.setColor(Color.rgb(Color.red(pc),Color.green(pc),Color.blue(pc)));p.setAlpha(255);canvas.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.7f);p.setColor(Color.argb(80,235,245,250));canvas.drawPath(path,p);}
        p.setAlpha(255);FileOutputStream fos=new FileOutputStream(out);if(!b.compress(Bitmap.CompressFormat.PNG,95,fos))throw new Exception("PNG oluşturulamadı");fos.flush();fos.close();b.recycle();
    }
    private static float z(DenseField f,int i,float max,float gain){
        if(f.maxDepth>0.0001f&&f.depthValid[i])return -Math.max(0f,Math.min(1f,Math.abs(f.depth[i])/f.maxDepth))*.82f;
        return Math.max(-1.2f,Math.min(1.2f,f.value[i]/max))*.8f*gain;
    }
    private static float[] pr(int x,int y,float z,int w,int h,int size,float s,double yaw,double pitch){float nx=-1.15f+2.3f*x/Math.max(1f,w-1f),ny=-1f+2f*y/Math.max(1f,h-1f);float x1=(float)(nx*Math.cos(yaw)-ny*Math.sin(yaw)),y1=(float)(nx*Math.sin(yaw)+ny*Math.cos(yaw)),y2=(float)(y1*Math.cos(pitch)-z*Math.sin(pitch));return new float[]{size*.5f+x1*s,size*.54f+y2*s};}
}
