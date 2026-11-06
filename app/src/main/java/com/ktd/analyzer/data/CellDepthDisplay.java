package com.ktd.analyzer.data;

import java.util.Locale;

/**
 * 2D ızgaraya yazılan derinliğin KAYNAĞINI görünür tutar.
 * C/tekil saha noktası hücreye aittir. Dipol modeli tek hedefi tahmin eder;
 * sadece hedefin yakınındaki güçlü anomali kutularına ≈ işaretiyle yazılır.
 * 1024/Z, deneysel indeks olarak ≈ işaretiyle sunulur; gerçek metre değildir.
 */
public final class CellDepthDisplay {
    private CellDepthDisplay() {}
    public enum Source { VERIFIED, DEVICE_C, MODEL, INVERSE_INDEX, RULER, UNKNOWN }
    public static final class Value {
        public final Source source;
        public final float meters;
        private Value(Source s,float m){source=s;meters=m;}
        public boolean known(){return source!=Source.UNKNOWN;}
        public String label(){
            if(!known())return "—";
            String m=String.format(Locale.forLanguageTag("tr-TR"),"%.2f",meters);
            // 0,00 ortam yüzeyinin (anomali yok) işaretidir.
            return source==Source.INVERSE_INDEX?m+" m":
                   (source==Source.MODEL||source==Source.RULER?(meters==0f?"0,00":"≈"+m):source==Source.VERIFIED?"✓"+m:m);
        }
    }
    private static final Value UNKNOWN=new Value(Source.UNKNOWN,Float.NaN);
    public static Value at(ScanData d,MagneticDepthEstimator.Result fit,int row,int col){
        if(d==null||row<0||row>=d.rows||col<0||col>=d.cols||!d.filled[row][col])return UNKNOWN;
        float site=d.fieldVerifiedDepth[row][col];
        if(Float.isFinite(site)&&site>=0)return new Value(Source.VERIFIED,site);
        // Ortak KTD proje dönüşümü: 2D = 3D = 4D, (800 / |ham Z - referans|) / 10.
        // Ölçek, bilinen derinliklerde saha kalibrasyonu yapılana kadar proje değeridir.
        float calculated=ReferenceMeterModel.meters(d.raw[row][col],ReferenceInverseDepth.referenceOf(d));
        if(Float.isFinite(calculated))return new Value(Source.INVERSE_INDEX,calculated);
        return UNKNOWN;
    }
}
