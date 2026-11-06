package com.ktd.analyzer.data;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.util.*;

public final class CsvUtil {
    private CsvUtil(){}

    public static void write(Context c,Uri uri,ScanData d)throws Exception{
        OutputStream os=c.getContentResolver().openOutputStream(uri);if(os==null)throw new Exception("Dosya açılamadı");
        writeToStream(os,d);os.close();
    }
    public static File quickSave(Context c,ScanData d,String prefix)throws Exception{
        return saveNamed(c,d,safeBaseName(prefix+"_"+System.currentTimeMillis()));
    }
    public static File saveNamed(Context c,ScanData d,String baseName)throws Exception{
        File dir=new File(c.getFilesDir(),"scans");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Kayıt klasörü oluşturulamadı");
        String base=safeBaseName(baseName);File f=new File(dir,base+".csv");
        for(int i=2;f.exists()&&i<=9999;i++)f=new File(dir,base+"_"+i+".csv");
        if(f.exists())throw new IOException("Aynı adla çok fazla dosya var");
        try(FileOutputStream out=new FileOutputStream(f)){writeToStream(out,d);}
        return f;
    }
    /** Disaridan gelen CSV'nin ORIJINAL satir ve sutunlarini aynen saklar.
     * Dosya adi yalnizca kullanici etiketi; icerik turu buna gore saptanmaz.
     * Ayni isimli dosyalar uzerine yazilmaz, 2/3/... soneki eklenir. */
    public static File copyOriginalCsv(Context c,Uri uri,String baseName)throws Exception {
        File dir=new File(c.getFilesDir(),"scans");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Kayıt klasörü oluşturulamadı");
        String base=safeBaseName(baseName);
        File f=new File(dir,base+".csv");
        for(int i=2;f.exists()&&i<=9999;i++)f=new File(dir,base+"_"+i+".csv");
        if(f.exists())throw new IOException("Aynı isimle çok fazla kayıt var");
        boolean complete=false;
        try(InputStream in=c.getContentResolver().openInputStream(uri);
            FileOutputStream out=new FileOutputStream(f)) {
            if(in==null)throw new IOException("Dış CSV kaynağına erişilemedi");
            byte[] block=new byte[8192];long bytes=0;int n;
            while((n=in.read(block))!=-1){
                bytes+=n;
                if(bytes>32L*1024L*1024L)throw new IOException("CSV dosyası 32 MB sınırını aşıyor");
                out.write(block,0,n);
            }
            complete=true;
        }finally{if(!complete)f.delete();}
        return f;
    }

    public static String safeBaseName(String s){
        if(s==null)s="";s=s.trim();if(s.isEmpty())s="KTD_TARAMA";
        s=s.replaceAll("[\\\\/:*?\"<>|]","_").replaceAll("\\s+"," ").trim();
        while(s.endsWith("."))s=s.substring(0,s.length()-1);
        if(s.length()>80)s=s.substring(0,80);
        return s.isEmpty()?"KTD_TARAMA":s;
    }

    /**
     * KTD disari aktarim bicimi KESIN olarak yalniz dort sutun icerir:
     * X,Y,Z,C. Z cihazdan alinan ham degerdir. C gonderilmiyorsa yalniz
     * deneysel 800/Z sayisi olarak olusturulur; metre oldugu kanitlanmamistir.
     * Z=0/eksik ise C bos birakilir. Cihaz C gonderdiyse asla ustune yazilmaz.
     */
    private static void writeToStream(OutputStream os,ScanData d){
        PrintWriter pw=new PrintWriter(new OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
        pw.println("X,Y,Z,C");
        for(int r=0;r<d.rows;r++)for(int co=0;co<d.cols;co++)if(d.filled[r][co]){
            float z=Float.isFinite(d.z[r][co])?d.z[r][co]:d.raw[r][co];
            float receivedC=d.c[r][co];
            float c=(Float.isFinite(receivedC)&&receivedC>0f && !InverseSignalIndex.matches(z,receivedC,1024f))?receivedC:InverseSignalIndex.fromRaw(z);
            pw.printf(Locale.US,"%s,%s,%s,%s%n",n(d.x[r][co]*100f),n(d.y[r][co]*100f),n(z),n(c));
        }
        pw.flush();
        if(pw.checkError())throw new java.io.UncheckedIOException(new IOException("CSV yazma hatasi"));
    }
    private static String n(float v){return Float.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}

    public static ScanData read(Context c,Uri uri)throws Exception{
        InputStream in=c.getContentResolver().openInputStream(uri);if(in==null)throw new Exception("Dosya açılamadı");
        ScanData d=readReader(new BufferedReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)));in.close();return d;
    }
    public static ScanData readFile(File f)throws Exception{
        return readReader(new BufferedReader(new InputStreamReader(new FileInputStream(f),java.nio.charset.StandardCharsets.UTF_8)));
    }
    /** Uygulamanın paketine eklenmiş orijinal saha örnekleri için. */
    public static ScanData readStream(InputStream in)throws Exception{
        if(in==null)throw new Exception("Veri akışı açılamadı");
        return readReader(new BufferedReader(new InputStreamReader(in,"UTF-8")));
    }

    /** Yaygın saha cihazı CSV'leri: BOM, UTF-8, ; / , / TAB, TR ondalık virgül,
     * başlık eşanlamlıları, 3 veya 4 sütun XYZ(C), satır/sütun veya ham matris.
     * Koordinat veya sensör kanalı olmayan biçimleri sessizce uydurmaz. */
    private static ScanData readReader(BufferedReader br)throws Exception{
        ArrayList<String> physical=new ArrayList<>();
        String line;
        while((line=br.readLine())!=null){
            line=line.replace("\uFEFF","").trim();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("//"))continue;
            physical.add(line);
            if(physical.size()>50001)throw new Exception("Çok büyük CSV (en fazla 50000 satır)");
        }
        br.close();
        if(physical.isEmpty())throw new Exception("CSV boş");
        char sep=detectSeparator(physical.get(0));
        String[] h=split(physical.get(0),sep);
        boolean header=!mostlyNumeric(h);
        Map<String,Integer> map=new HashMap<>();
        if(header){
            for(int j=0;j<h.length;j++){
                String key=canonicalHeader(h[j]);
                if(!key.isEmpty()&&!map.containsKey(key))map.put(key,j);
            }
        } else {
            if(h.length==4){map.put("x",0);map.put("y",1);map.put("z",2);map.put("c",3);}
            else if(h.length==3){map.put("x",0);map.put("y",1);map.put("z",2);}
        }
        ArrayList<String[]> rows=new ArrayList<>();
        for(int i=header?1:0;i<physical.size();i++){
            String[] vals=split(physical.get(i),sep);
            if(vals.length>0)rows.add(vals);
        }
        if(rows.isEmpty())throw new Exception("CSV veri satırı içermiyor");
        // Başlıksız 3/4 sütun hem XYZ(C) koordinat tablosu hem de ham sensör
        // matrisi olabilir. Tamamı aynı ADC büyüklüğündeki tabloları (605;620;625)
        // uydurma XY koordinatları diye yorumlama.
        if(!header&&looksLikeSensorMatrix(rows,h.length))return readNumericMatrix(rows);
        if(map.containsKey("row")&&map.containsKey("col"))return readIndexed(rows,map);
        if(map.containsKey("x")&&map.containsKey("y")&&map.containsKey("z"))return readNativeXYZC(rows,map);
        if(map.containsKey("z")&&map.size()==1)return readSignalSeries(rows,map.get("z"));
        if(!header)return readNumericMatrix(rows);
        throw new Exception("Sütunlar tanınamadı. X,Y,Z[,C] ya da satır/sütun başlığı gerekir.");
    }
    private static boolean looksLikeSensorMatrix(ArrayList<String[]> rows,int width){
        if(width<3||width>4||rows.size()<2)return false;
        double min=Double.POSITIVE_INFINITY,max=0;
        int samples=Math.min(12,rows.size());
        for(int j=0;j<3;j++){
            ArrayList<Float> v=new ArrayList<>();
            for(int i=0;i<samples;i++)if(j<rows.get(i).length){
                float x=f0(rows.get(i)[j]);if(Float.isFinite(x))v.add(Math.abs(x));
            }
            if(v.size()<2)return false;
            Collections.sort(v);double med=v.get(v.size()/2);
            if(med<40)return false; // XYZ koordinatları sıklıkla 0-1.5 m'dir.
            min=Math.min(min,med);max=Math.max(max,med);
        }
        // Yakın genlikli ilk üç sütun: yüksek olasılıkla ADC matrisi.
        // Coğrafi/çok büyük XY koordinatları olan belirsiz dosyalara başlık eklenmelidir.
        return max/min<2.5;
    }

    private static char detectSeparator(String s){
        int semi=countSep(s,';'),tab=countSep(s,'\t'),comma=countSep(s,',');
        if(semi>0&&semi>=comma&&semi>=tab)return ';';
        if(comma>0)return ',';
        if(tab>0)return '\t';
        return ',';
    }
    private static int countSep(String s,char ch){int n=0;boolean quoted=false;
        for(int i=0;i<s.length();i++){char x=s.charAt(i);if(x=='"')quoted=!quoted;else if(x==ch&&!quoted)n++;}
        return n;
    }
    private static boolean mostlyNumeric(String[] a){
        int numeric=0;for(String v:a)if(Float.isFinite(f0(v)))numeric++;
        return a.length>0&&numeric>=Math.max(1,a.length-1);
    }
    private static String canonicalHeader(String input){
        String s=input.replace("\uFEFF","").trim().toLowerCase(Locale.ROOT)
            .replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c');
        s=s.replaceAll("[\\s_\\-]+","");
        s=s.replaceAll("\\([^)]*\\)","").replaceAll("\\[[^]]*\\]","");
        if(s.equals("x")||s.equals("xcoord")||s.equals("coordx")||s.equals("easting")||s.equals("xcoordinate"))return "x";
        if(s.equals("y")||s.equals("ycoord")||s.equals("coordy")||s.equals("northing")||s.equals("ycoordinate"))return "y";
        if(s.equals("z")||s.equals("zraw")||s.equals("raw")||s.equals("rawz")||s.equals("value")||s.equals("signal")||s.equals("sensor")||s.equals("adc")||s.equals("sensorvalue")||s.equals("reading")||s.equals("magnetic")||s.equals("magnetik")||s.equals("hamdeger"))return "z";
        if(s.equals("c")||s.equals("depth")||s.equals("depthm")||s.equals("depthmeters")||s.equals("derinlik")||s.equals("derinlikm"))return "c";
        if(s.equals("row")||s.equals("satir")||s.equals("line")||s.equals("satirno"))return "row";
        if(s.equals("col")||s.equals("column")||s.equals("sutun")||s.equals("colno"))return "col";
        // Eski KTD metadata sütun adlarını (start_corner vb.) koru.
        return input.trim().toLowerCase(Locale.ROOT);
    }
    /** Tek kanal CSV'de koordinat bilgisi bulunmadığı için yalnız bir hat olarak açılır. */
    private static ScanData readSignalSeries(ArrayList<String[]> lines,int idx)throws Exception{
        ArrayList<Float> z=new ArrayList<>();for(String[] a:lines){if(idx<a.length){float v=f0(a[idx]);if(Float.isFinite(v))z.add(v);}}
        if(z.isEmpty())throw new Exception("Sayısal sinyal bulunamadı");
        ScanData d=new ScanData(z.size(),1);d.reference=median(z);d.referenceEstimated=true;
        for(int i=0;i<z.size();i++)d.put(z.size()-1-i,0,new SensorFrame(i*.30f,0f,z.get(i),Float.NaN),z.get(i)-d.reference);
        NeutralExperimentalDepth.apply(d);return d;
    }
    private static ScanData readNumericMatrix(ArrayList<String[]> lines)throws Exception{
        int width=lines.get(0).length;if(width<2)throw new Exception("En az iki sütun gerekir");
        int height=lines.size();if((long)width*height>50000)throw new Exception("Matris çok büyük");
        ScanData d=new ScanData(height,width);ArrayList<Float> vals=new ArrayList<>();
        for(String[] a:lines)for(String t:a){float v=f0(t);if(Float.isFinite(v))vals.add(v);}
        d.reference=median(vals);d.referenceEstimated=true;
        for(int i=0;i<height;i++){
            String[] a=lines.get(i);
            for(int j=0;j<Math.min(width,a.length);j++){
                float z=f0(a[j]);if(Float.isFinite(z))d.put(i,j,new SensorFrame(z),z-d.reference);
            }
        }
        NeutralExperimentalDepth.apply(d);return d;
    }
    private static float median(ArrayList<Float> vals){
        if(vals.isEmpty())return Float.NaN;
        Collections.sort(vals);return vals.get(vals.size()/2);
    }

    /** KTD uygulamasının row/col içeren kayıt biçimi. */
    private static ScanData readIndexed(ArrayList<String[]> lines,Map<String,Integer> map)throws Exception{
        int mr=-1,mc=-1;
        ArrayList<String[]> good=new ArrayList<>();
        for(String[] a:lines){
            try{
                int r=iv(get(a,map,"row",-1)),co=iv(get(a,map,"col",-1));
                mr=Math.max(mr,r);mc=Math.max(mc,co);good.add(a);
            }catch(Exception ignored){}
        }
        if(mr<0||mc<0)throw new Exception("CSV grid bilgisi okunamadı");
        if((long)(mr+1)*(mc+1)>50000)throw new Exception("Izgara çok büyük");
        ScanData d=new ScanData(mr+1,mc+1);
        if(!good.isEmpty()){
            String[] first=good.get(0);
            float sc=f0(get(first,map,"start_corner",-1));
            if(Float.isFinite(sc))d.startCorner=Math.round(sc)==1?1:0;
            String sm=get(first,map,"scan_mode",-1).trim().toUpperCase(Locale.ROOT);
            if(!sm.isEmpty())d.serpentine=!sm.startsWith("PAR");
        }

        // Adım bilgisi dosyada açık değilse X/Y koordinatlarından tahmin et.
        ArrayList<Float> xs=new ArrayList<>(),ys=new ArrayList<>();
        for(String[] a:good){
            float xv=f0(get(a,map,"x",-1)),yv=f0(get(a,map,"y",-1));
            if(Float.isFinite(xv))xs.add(xv);
            if(Float.isFinite(yv))ys.add(yv);
        }
        int inferred=inferStepCm(xs,ys,false);
        if(inferred>0)d.stepCm=inferred;

        for(String[] a:good){
            int r=iv(get(a,map,"row",-1)),co=iv(get(a,map,"col",-1));
            float x=f0(get(a,map,"x",-1)),y=f0(get(a,map,"y",-1)),
                  z=f0(get(a,map,"z",-1)),cv=f0(get(a,map,"c",-1));
            float ref=f0(get(a,map,"reference",-1));
            float de=f0(get(a,map,"delta",-1));
            if(!Float.isFinite(ref))ref=Float.NaN;
            if(!Float.isFinite(de)&&Float.isFinite(z)&&Float.isFinite(ref))de=z-ref;
            if(!Float.isFinite(de))de=0f;
            // v3.6.2 tek kanallı CSV'lerinde Z boş, fakat reference+delta mevcuttu.
            // Sayısal ham ölçümü kaybetmeden geriye dönük olarak kurtar.
            if(!Float.isFinite(z)&&Float.isFinite(ref)&&Float.isFinite(de))z=ref+de;
            if(Float.isFinite(ref))d.reference=ref;
            float smin=f0(get(a,map,"site_verified_min_m",-1));
            float smax=f0(get(a,map,"site_verified_max_m",-1));
            if(Float.isFinite(smin)&&Float.isFinite(smax)&&smin>0&&smax>=smin){
                d.setVerifiedSiteRange(smin,smax,get(a,map,"site_verified_label",-1));
            }
            String soil=get(a,map,"soil_profile",-1);
            if(!soil.trim().isEmpty())d.soilProfile=soil.trim();
            float soilParam=f0(get(a,map,"soil_parameter",-1));
            if(Float.isFinite(soilParam))d.soilParameter=soilParam;
            String source=get(a,map,"source_format",-1);
            if(source.trim().equalsIgnoreCase("V3D"))d.importedFromV3d=true;
            d.put(r,co,new SensorFrame(x,y,z,cv),de);
            float verified=f0(get(a,map,"field_verified_depth_m",-1));
            if(Float.isFinite(verified)&&verified>=0f)d.fieldVerifiedDepth[r][co]=verified;
            // Eski uygulamalar depth_m sütununu 800/Z hesabıyla doldurabiliyordu.
            // Açık cihaz C alanı olmadıkça onu "ölçülen derinlik" sayma.
        }
        if(!Float.isFinite(d.reference)){
            d.reference=inferReferenceFromData(d);
            d.referenceEstimated=true;
        }
        NeutralExperimentalDepth.apply(d);
        return d;
    }

    /**
     * Cihazın doğal dosyası:
     *   X,Y,Z,C
     *
     * X/Y koordinattır, Z ham sensör değeridir. C bağımsız cihaz kanalı
     * veya 1024/Z türevi olabilir; kaynak bu dosyadan otomatik incelenir.
     */
    private static ScanData readNativeXYZC(ArrayList<String[]> lines,Map<String,Integer> map)throws Exception{
        ArrayList<NativeRec> recs=new ArrayList<>();
        ArrayList<Float> xVals=new ArrayList<>(),yVals=new ArrayList<>();
        for(String[] a:lines){
            float x=f0(get(a,map,"x",-1)),y=f0(get(a,map,"y",-1)),
                  z=f0(get(a,map,"z",-1)),c=f0(get(a,map,"c",-1));
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))continue;
            recs.add(new NativeRec(x,y,z,c));
            addUnique(xVals,x);addUnique(yVals,y);
        }
        if(recs.isEmpty()||xVals.isEmpty()||yVals.isEmpty())throw new Exception("X/Y/Z verisi okunamadı");
        if((long)xVals.size()*yVals.size()>50000)throw new Exception("Izgara çok büyük");
        Collections.sort(xVals);Collections.sort(yVals);

        float rawStep=inferRawStep(xVals,yVals);
        boolean coordinatesAreCm=rawStep>=5f; // 30,60,... -> cm; 0.30,0.60,... -> metre
        float coordScale=coordinatesAreCm?.01f:1f;
        int stepCm=rawStep>0f?Math.max(1,Math.round(rawStep*coordScale*100f)):30;

        // FINAL koordinat standardı: X ileri tarama hattıdır, Y yan sıradır.
        // Bu nedenle matris satırları X, sütunları Y eksenini temsil eder.
        ScanData d=new ScanData(xVals.size(),yVals.size());
        d.stepCm=stepCm;
        d.name="KTD_NATIVE_XYZC";
        // Başlangıç köşesi yan sıra (Y) ekseninin hangi kenarından başladığıdır.
        if(!recs.isEmpty()){
            float firstY=recs.get(0).y;
            float minY=yVals.get(0),maxY=yVals.get(yVals.size()-1);
            d.startCorner=Math.abs(firstY-maxY)<Math.abs(firstY-minY)?1:0;
        }

        // Native dosyada ortam referansı alanı yok. 0 yüzeysel anomali olduğundan
        // referansa katılmaz; pozitif Z değerlerinin medyanı sağlam zemin tahmini olur.
        float ref=inferNativeReference(recs);
        d.reference=ref;
        d.referenceEstimated=true; // X,Y,Z,C'ye orijinal REF yazılmadığından yaklaşık.

        for(NativeRec q:recs){
            int xi=nearestIndex(xVals,q.x);
            int yi=nearestIndex(yVals,q.y);
            int r=(xVals.size()-1)-xi; // X=0 başlangıçta/altta, ileri X ekranda yukarı.
            int co=yi;                // Y yan sıra fiziksel soldan sağa.
            float xM=q.x*coordScale,yM=q.y*coordScale;
            float de=Float.isFinite(ref)?q.z-ref:0f;
            d.put(r,co,new SensorFrame(xM,yM,q.z,q.c),de);
        }
        NeutralExperimentalDepth.apply(d);
        return d;
    }

    private static float inferNativeReference(ArrayList<NativeRec> recs){
        ArrayList<Float> vals=new ArrayList<>();
        for(NativeRec r:recs)if(Float.isFinite(r.z)&&r.z>0f)vals.add(r.z);
        if(vals.isEmpty())return Float.NaN;
        Collections.sort(vals);
        return vals.get(vals.size()/2);
    }

    private static float inferReferenceFromData(ScanData d){
        ArrayList<Float> vals=new ArrayList<>();
        for(int r=0;r<d.rows;r++)for(int c=0;c<d.cols;c++)if(d.filled[r][c]&&Float.isFinite(d.raw[r][c])&&d.raw[r][c]>0f)vals.add(d.raw[r][c]);
        if(vals.isEmpty())return Float.NaN;
        Collections.sort(vals);return vals.get(vals.size()/2);
    }

    private static int inferStepCm(ArrayList<Float> xs,ArrayList<Float> ys,boolean rawCm){
        float s=inferRawStep(uniqueSorted(xs),uniqueSorted(ys));
        if(s<=0f)return 0;
        float scale=rawCm?.01f:(s>=5f?.01f:1f);
        return Math.max(1,Math.round(s*scale*100f));
    }
    private static float inferRawStep(ArrayList<Float> xs,ArrayList<Float> ys){
        ArrayList<Float> diffs=new ArrayList<>();
        collectDiffs(xs,diffs);collectDiffs(ys,diffs);
        if(diffs.isEmpty())return 0f;
        Collections.sort(diffs);return diffs.get(diffs.size()/2);
    }
    private static void collectDiffs(ArrayList<Float> a,ArrayList<Float> out){
        for(int i=1;i<a.size();i++){float d=Math.abs(a.get(i)-a.get(i-1));if(d>1e-4f)out.add(d);}
    }
    private static ArrayList<Float> uniqueSorted(ArrayList<Float> src){
        ArrayList<Float> out=new ArrayList<>();
        for(Float v:src)if(v!=null&&Float.isFinite(v))addUnique(out,v);
        Collections.sort(out);return out;
    }
    private static void addUnique(ArrayList<Float> a,float v){
        for(float q:a)if(Math.abs(q-v)<1e-4f)return;
        a.add(v);
    }
    private static int nearestIndex(ArrayList<Float> a,float v){
        int best=0;float bd=Float.MAX_VALUE;
        for(int i=0;i<a.size();i++){float d=Math.abs(a.get(i)-v);if(d<bd){bd=d;best=i;}}
        return best;
    }

    private static final class NativeRec{
        final float x,y,z,c;
        NativeRec(float x,float y,float z,float c){this.x=x;this.y=y;this.z=z;this.c=c;}
    }

    private static String[] split(String s,char sep){
        ArrayList<String> out=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean q=false;
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            if(ch=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){cell.append('"');i++;}else q=!q;}
            else if(ch==sep&&!q){out.add(cell.toString().trim());cell.setLength(0);}
            else cell.append(ch);
        }
        out.add(cell.toString().trim());return out.toArray(new String[0]);
    }
    private static String get(String[] a,Map<String,Integer> map,String key,int fallback){
        Integer i=map.get(key);int idx=i!=null?i:fallback;return idx>=0&&idx<a.length?a[idx]:"";
    }
    private static int iv(String s){return Integer.parseInt(s.trim());}
    private static float f0(String s){
        if(s==null||s.trim().isEmpty())return Float.NaN;
        try{return Float.parseFloat(s.trim().replace(',','.'));}catch(Exception e){return Float.NaN;}
    }
}
