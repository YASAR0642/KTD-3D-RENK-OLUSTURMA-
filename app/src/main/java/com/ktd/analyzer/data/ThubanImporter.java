package com.ktd.analyzer.data;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

/** Thuban / Lodestar JSON surfacePoints dosyalarini KTD nokta izgarasina aktarir.
 * data_0 ham imzali bayttir; fiziksel alan/degerli metal/derinlik birimi DEGILDIR.
 * point3D.z/f/e bu ornekte sabit 0 oldugu icin olcum olarak kullanilmaz.
 */
public final class ThubanImporter {
    private ThubanImporter() {}
    public static ScanData read(Context c, Uri uri) throws Exception {
        try(InputStream in=c.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new Exception("Thuban dosyasina erisilemiyor");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){
                if(out.size()+n>16*1024*1024)throw new Exception("Thuban dosyasi 16 MB sinirini asiyor");
                out.write(buf,0,n);
            }
            JSONObject root=new JSONObject(out.toString("UTF-8"));
            JSONObject surface=root.optJSONObject("surfacePoints");
            if(surface==null)throw new Exception("Thuban surfacePoints bulunamadi");
            JSONArray lines=surface.optJSONArray("datas");
            if(lines==null)throw new Exception("Thuban olcum listesi bulunamadi");
            int maxX=-1,maxY=-1;
            ArrayList<int[]> coords=new ArrayList<>();ArrayList<Float> values=new ArrayList<>();
            for(int r=0;r<lines.length();r++){
                JSONArray row=lines.optJSONArray(r);if(row==null)continue;
                for(int k=0;k<row.length();k++){
                    JSONObject cell=row.optJSONObject(k);if(cell==null)continue;
                    JSONObject point=cell.optJSONObject("point3D");
                    JSONObject message=cell.optJSONObject("contentValues");
                    if(point==null||message==null||!message.has("data_0"))continue;
                    double xx=point.optDouble("x",Double.NaN), yy=point.optDouble("y",Double.NaN);
                    if(!Double.isFinite(xx)||!Double.isFinite(yy)||xx<0||yy<0||xx>200||yy>200
                      ||Math.abs(xx-Math.rint(xx))>1e-4||Math.abs(yy-Math.rint(yy))>1e-4)
                        throw new Exception("Thuban nokta koordinatlari tam sayi izgarasinda degil");
                    int x=(int)Math.round(xx),y=(int)Math.round(yy);
                    int raw=message.getInt("data_0");
                    if(raw< -128||raw>255)throw new Exception("Thuban data_0 bayt araligi disinda");
                    // Java JSON negatif bayt degerlerini korur; 128..255 ayni imzali bayta eslenir.
                    if(raw>127)raw-=256;
                    coords.add(new int[]{x,y});values.add((float)raw);
                    maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
                }
            }
            if(coords.isEmpty())throw new Exception("Thuban ham olcum noktalari bulunamadi");
            if((long)(maxX+1)*(maxY+1)>50000)throw new Exception("Thuban izgara cok buyuk");
            ScanData d=new ScanData(maxX+1,maxY+1);
            ArrayList<Float> sorted=new ArrayList<>(values);Collections.sort(sorted);
            float reference=sorted.get(sorted.size()/2);
            d.reference=reference;d.referenceEstimated=true;
            d.name="THUBAN_OLCUM";
            // Dosya serpantin yonunu nokta koordinatlarinda tanimliyor: y'ye gore hucreye koy.
            for(int i=0;i<coords.size();i++){
                int[] xy=coords.get(i);int r=maxX-xy[0],col=xy[1];float v=values.get(i);
                if(d.filled[r][col])throw new Exception("Thuban dosyasinda tekrar eden izgarali nokta");
                d.put(r,col,new SensorFrame(Float.NaN,Float.NaN,v,Float.NaN),v-reference);
            }
            // Thuban koordinatlari nokta indeksleridir, metre/derinlik degildir.
            // KTD 2D ve 3D/4D gostergesi ortak ScanData'yi kullanir.
            return d;
        }
    }
}
