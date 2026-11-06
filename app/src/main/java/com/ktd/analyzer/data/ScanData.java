package com.ktd.analyzer.data;

import java.util.Arrays;

/**
 * Tarama matrisi.
 *
 * KTD cihaz protokolü:
 *   X = saha X koordinatı
 *   Y = saha Y koordinatı
 *   Z = ham sensör değeri / genlik
 *   C = cihazın derinlik değeri (metre)
 *
 * C alanı cihazın ilettiği değer olarak saklanır. Uygulama Z'den fiziksel
 * derinlik türetmez; bu veri fiziksel olarak kalibre edilmiş olmayabilir.
 */
public final class ScanData {
    // Geriye dönük API uyumu için sabit; artık derinlik üst limiti uygulanmaz.
    public static final float MAX_DEPTH_M = Float.POSITIVE_INFINITY;

    public final int rows, cols;
    public final float[][] x,y,z,c,raw,delta,depth;
    /** Saha tarafından doğrulanmış noktalar, cihazın ölçtüğü C derinliğinden ayrı saklanır. */
    public final float[][] fieldVerifiedDepth;
    /** Bu yapı sadece karşılaştırma profilidir; tek başına metre üreten formül değildir. */
    public String soilProfile="Neutral";
    /** Visualizer3D.dat içindeki Neutral sayısı; anlamı çözülmeden hesapta KULLANILMAZ. */
    public float soilParameter=19.35f;
    public boolean importedFromV3d=false;
    /** Eski 0-25 m deneyinden kalan dosya/API uyum alanı; artık her zaman false. */
    public boolean experimentalDepthActive=false;
    /** En az bir saha referansıyla anomali ölçeği ankrajlanmıştır; fiziksel doğruluk garantisi değildir. */
    public boolean experimentalDepthCalibrated=false;
    /** true: C bağımsız derinlik değil, 800/Z veya 1024/Z türevi dönüsümdür. */
    public boolean cIsDerivedFromRaw=false;
    /** Z ekseni deneysel Ereğli referans cetvelinden geliyor; cihaz ölçümü değil. */
    public boolean rulerDerivedDepth=false;
    /** Türetilmiş C için bulunan ters dönüşüm payı. 0: türetilmiş değil. */
    public int derivedCDivisor=0;
    /** Bir tarama alanına ait kullanıcı tarafından doğrulanan gerçek saha derinlik aralığı.
     * Asla c[][] veya depth[][] alanlarını doldurmak için kullanılmaz. */
    public float verifiedSiteMinMeters=Float.NaN;
    public float verifiedSiteMaxMeters=Float.NaN;
    public String verifiedSiteLabel="";
    public void setVerifiedSiteRange(float min,float max,String label){
        if(!Float.isFinite(min)||!Float.isFinite(max)||min<=0||max<min||max>1000)
            throw new IllegalArgumentException("Gerçek derinlik aralığı geçersiz");
        verifiedSiteMinMeters=min;
        verifiedSiteMaxMeters=max;
        verifiedSiteLabel=label==null?"":label.trim();
    }
    public boolean hasVerifiedSiteRange(){
        return Float.isFinite(verifiedSiteMinMeters) && Float.isFinite(verifiedSiteMaxMeters);
    }
    /** Kayıtlarda açık C kanalıyla gelen örnek sayısı. */
    public int nativeCCount(){
        int n=0;
        for(int r=0;r<rows;r++)for(int co=0;co<cols;co++)
            if(filled[r][co]&&Float.isFinite(c[r][co])&&c[r][co]>=0f)n++;
        return n;
    }
    /**
     * Yalniz X,Y,Z,C bulunan CSV yeniden acilinca ters donusumun gercek
     * metre gibi yorumlanmamasi icin 800/Z ve 1024/Z eslesmesini arar.
     * Kaynak meta verisi bulunmadigindan istatistiksel tespit kullanilir.
     */
    public void reassessDepthSource(){
        int eligible=0,oldMatches=0,newMatches=0;
        for(int r=0;r<rows;r++)for(int co=0;co<cols;co++){
            if(!filled[r][co])continue;
            float z0=raw[r][co],c0=c[r][co];
            if(!Float.isFinite(z0)||z0<=0f||!Float.isFinite(c0)||c0<=0f)continue;
            eligible++;
            if(InverseSignalIndex.matches(z0,c0,800f))oldMatches++;
            if(InverseSignalIndex.matches(z0,c0,1024f))newMatches++;
        }
        // Tek noktadaki tesadufi benzerligi cihaz derinligi diye ya da
        // turetilmis diye siniflandirmamak icin en az 3 nokta gerekir.
        int threshold=Math.max(3,(int)Math.ceil(eligible*.95));
        boolean derived800=oldMatches>=threshold;
        boolean derived1024=newMatches>=threshold;
        cIsDerivedFromRaw=derived800||derived1024;
        derivedCDivisor=derived1024?1024:derived800?800:0;
        for(int r=0;r<rows;r++)for(int co=0;co<cols;co++)if(filled[r][co]){
            depth[r][co]=cIsDerivedFromRaw?Float.NaN:nativeDepthMeters(raw[r][co],c[r][co]);
        }
        experimentalDepthActive=false;
        experimentalDepthCalibrated=false;
    }

    public int suspectedFilledSamples=0;
    public final boolean[][] filled;
    public float reference=Float.NaN;
    /** Sadece gradyometre ters oranti ekrani, CSV ve gercek C dokunulmaz. */
    public float depthCalibrationFactor=1f;
    /** true when X,Y,Z,C lacked the exact REF and it was reconstructed statistically. */
    public boolean referenceEstimated=false;
    public int stepCm=30;
    /** Taramanın fiziksel başlangıç köşesi: 0=sol alt, 1=sağ alt. */
    public int startCorner=0;
    /** true=zikzak, false=paralel. Eksen başlangıcı için startCorner esas alınır. */
    public boolean serpentine=true;
    public int count=0;
    public String name="KTD_SCAN";

    public ScanData(int rows,int cols){
        this.rows=rows;this.cols=cols;
        x=new float[rows][cols];y=new float[rows][cols];z=new float[rows][cols];c=new float[rows][cols];
        raw=new float[rows][cols];delta=new float[rows][cols];depth=new float[rows][cols];filled=new boolean[rows][cols];
        fieldVerifiedDepth=new float[rows][cols];
        for(int r=0;r<rows;r++){
            Arrays.fill(x[r],Float.NaN);Arrays.fill(y[r],Float.NaN);Arrays.fill(z[r],Float.NaN);Arrays.fill(c[r],Float.NaN);
            Arrays.fill(raw[r],Float.NaN);Arrays.fill(delta[r],Float.NaN);Arrays.fill(depth[r],Float.NaN);
            Arrays.fill(fieldVerifiedDepth[r],Float.NaN);
        }
    }

    public void put(int r,int col,SensorFrame f,float d){
        if(r<0||col<0||r>=rows||col>=cols||f==null)return;
        // Uygulamanın kanonik saha koordinatı: X ileri, Y yan sıra.
        // Cihaz X/Y göndermiyorsa grid hücresinden otomatik üret.
        float xAuto=(rows-1-r)*stepCm/100f;
        float yAuto=(startCorner==1?(cols-1-col):col)*stepCm/100f;
        x[r][col]=Float.isFinite(f.x)?f.x:xAuto;
        y[r][col]=Float.isFinite(f.y)?f.y:yAuto;
        z[r][col]=f.z;c[r][col]=f.channelC();raw[r][col]=f.signal();delta[r][col]=d;

        // Yalnız cihazın açık C derinlik kanalı geçerlidir; C yoksa NaN saklanır.
        // Z'den derinlik üretmek ölçülmeyen bir derinliği gerçekmiş gibi gösterir.
        depth[r][col]=nativeDepthMeters(raw[r][col],f.channelC());
        // Eski deneysel gösterim değeri yeniden hesaplanmadan kalmasın.
        experimentalDepthActive=false;experimentalDepthCalibrated=false;

        if(!filled[r][col]){filled[r][col]=true;count++;}
    }

    /** İndeksler 1'den başlar: hat = yan sıra, nokta = o hat üzerinde ileri mesafe. */
    public void setFieldVerifiedDepth(int lineNumber,int pointNumber,float meters){
        if(lineNumber<1||lineNumber>cols||pointNumber<1||pointNumber>rows)
            throw new IllegalArgumentException("Hat/nokta ızgara dışında");
        if(!Float.isFinite(meters)||meters<0f)
            throw new IllegalArgumentException("Derinlik 0 veya pozitif olmalı");
        int row=rows-pointNumber,col=lineNumber-1;
        if(!filled[row][col])throw new IllegalArgumentException("Bu noktada ölçüm yok");
        fieldVerifiedDepth[row][col]=meters;
    }
    public int fieldVerifiedCount(){
        int n=0;
        for(int r=0;r<rows;r++)for(int c0=0;c0<cols;c0++)
            if(filled[r][c0]&&Float.isFinite(fieldVerifiedDepth[r][c0]))n++;
        return n;
    }

    public void putLegacy(int r,int col,float rv,float d){put(r,col,new SensorFrame(rv),d);}
    public boolean full(){return count>=rows*cols;}
    public float maxAbs(){
        float m=10f;
        for(int r=0;r<rows;r++)for(int c0=0;c0<cols;c0++)if(filled[r][c0])m=Math.max(m,Math.abs(delta[r][c0]));
        return m;
    }

    /**
     * Cihazın gönderdiği C derinlik kanalını metre olarak alır.
     * Bu metot fiziksel derinliği doğrulamaz veya Z değerinden derinlik hesaplamaz.
     * Eksik/geçersiz C verisi bilinmiyor (NaN) olarak saklanır.
     */
    public static float nativeDepthMeters(float rawZ,float channelC){
        if(!Float.isFinite(channelC) || channelC<0f)return Float.NaN;
        return channelC;
    }

    /** Geriye uyumluluk; C yokken derinlik tahmini oluşturulmaz. */
    public static float estimateDepthMeters(float raw,float reference){
        return Float.NaN;
    }

    public boolean hasDepthData(){
        for(int r=0;r<rows;r++)for(int c0=0;c0<cols;c0++)
            if(filled[r][c0]&&Float.isFinite(depth[r][c0]))return true;
        return false;
    }

    public float maxDepthMeters(){
        float m=0f;
        for(int r=0;r<rows;r++)for(int c0=0;c0<cols;c0++)if(filled[r][c0]){
            float v=depth[r][c0];
            if(Float.isFinite(v))m=Math.max(m,Math.abs(v));
        }
        return m;
    }
}
