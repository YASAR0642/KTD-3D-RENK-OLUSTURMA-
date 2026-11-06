package com.ktd.analyzer.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ereglı.v3d dosyasında doğrulanmış V3D.V3D.01 / 481 bayt başlık / 9 float kayıt biçimi.
 * Diğer OKM sürümlerinin V3D biçimleri farklı olabilir ve reddedilir.
 * Dosyada bağımsız metre bilgisi yok; tahmini derinlik ÜRETMEZ.
 */
public final class V3dImporter {
    private static final int HEADER=481,RECORD=36,MAX_BYTES=16*1024*1024;
    private V3dImporter(){}

    public static ScanData read(InputStream stream,int stepCm)throws IOException {
        if(stream==null)throw new IOException("Dosya okunamadı");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] chunk=new byte[4096];int n;
        while((n=stream.read(chunk))!=-1){
            if(out.size()+n>MAX_BYTES)throw new IOException("V3D çok büyük");
            out.write(chunk,0,n);
        }
        byte[] bytes=out.toByteArray();
        if(bytes.length<HEADER+RECORD||((bytes.length-HEADER)%RECORD)!=0)
            throw new IOException("Bu V3D kayıt boyutu desteklenmiyor");
        byte[] magic="V3D.V3D.01".getBytes("US-ASCII");
        // Örnek OKM dosyasında imzadan önce tek LF (0x0A) yer alır.
        int signOffset=bytes[0]=='\n'?1:0;
        for(int i=0;i<magic.length;i++)if(bytes[signOffset+i]!=magic[i])
            throw new IOException("V3D imzası desteklenmiyor");
        ByteBuffer buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int num=(bytes.length-HEADER)/RECORD;
        if(num>100000)throw new IOException("Çok fazla kayıt");
        ArrayList<Rec> records=new ArrayList<>();
        ArrayList<Float> signals=new ArrayList<>();
        Set<Long> positions=new HashSet<>();
        int maxLine=-1,maxPoint=-1;
        for(int i=0;i<num;i++){
            int off=HEADER+i*RECORD;
            float raw=buf.getFloat(off),lineF=buf.getFloat(off+4),pointF=buf.getFloat(off+8);
            if(!Float.isFinite(raw)||!Float.isFinite(lineF)||!Float.isFinite(pointF))
                throw new IOException("V3D içinde geçersiz ölçüm bulundu");
            if(raw<0||raw>1000000||lineF<0||pointF<0||
               lineF!=Math.round(lineF)||pointF!=Math.round(pointF))
                throw new IOException("V3D ölçüm koordinatları uyumsuz");
            int line=Math.round(lineF),point=Math.round(pointF);
            if(line>999||point>999)throw new IOException("V3D ızgara çok büyük");
            long pos=(((long)line)<<32)|(point&0xffffffffL);
            if(!positions.add(pos))throw new IOException("V3D içinde tekrarlanan koordinat");
            records.add(new Rec(line,point,raw));signals.add(raw);
            maxLine=Math.max(maxLine,line);maxPoint=Math.max(maxPoint,point);
        }
        int rows=maxPoint+1,cols=maxLine+1;
        if(rows<2||cols<2||((long)rows*cols)>100000)
            throw new IOException("V3D ızgara boyutları geçersiz");
        Collections.sort(signals);
        float median=signals.get(signals.size()/2);
        ScanData d=new ScanData(rows,cols);
        d.soilProfile="Neutral";
        d.importedFromV3d=true;
        d.stepCm=Math.max(1,stepCm);
        d.reference=median;
        d.serpentine=true; // Gerçek koordinat sırasıyla yerleşim; kaydedilmiş 'Parallel' etiketi esas alınmaz.
        for(Rec r:records){
            int row=rows-1-r.point;
            d.put(row,r.line,new SensorFrame(r.point*d.stepCm/100f,r.line*d.stepCm/100f,r.raw,Float.NaN),r.raw-median);
        }
        // Son hatta, diğerlerine oranla çok dar bir değere yapışan örnekleri işaretle.
        // Sadece şüphe sayacı; hiçbir ölçüm kaldırılmaz veya derinlik olarak yorumlanmaz.
        if(cols>1){
            ArrayList<Float> lastLine=new ArrayList<>();
            for(int x=0;x<rows;x++){
                int r=rows-1-x;
                if(d.filled[r][cols-1])lastLine.add(d.raw[r][cols-1]);
            }
            if(lastLine.size()>=5){
                Collections.sort(lastLine);
                float center=lastLine.get(lastLine.size()/2);
                int similar=0;
                for(float v:lastLine)if(Math.abs(v-center)<.001f)similar++;
                if(similar>=Math.ceil(lastLine.size()*.60f))d.suspectedFilledSamples=similar;
            }
        }
        NeutralExperimentalDepth.apply(d);
        return d;
    }
    private static final class Rec {
        final int line,point;final float raw;
        Rec(int line,int point,float raw){this.line=line;this.point=point;this.raw=raw;}
    }
}
