package com.ktd.analyzer.data;

/**
 * Ham sensör için tek-sinyal ortam referansı.
 *
 * Nominal ADC aralığı 0..1023'tür; ancak cihazın kendi CSV çıktısında küçük
 * kalibrasyon/filtre taşmaları (ör. 1028.x) görülebildiği için işleme katmanı
 * gelen ham float değeri kırpmaz. Ekran gösterimi ayrıca yuvarlanabilir.
 */
public final class ReferenceModel {
    public static final float GROUND_BAND = ReferenceMeterModel.ANOMALY_THRESHOLD;
    public static final int SIGNAL_MIN = 0;
    public static final int SIGNAL_MAX = 1023;
    private float reference = Float.NaN;

    public void clear(){ reference = Float.NaN; }
    public void zero(float raw){ reference = normalize(raw); }
    public boolean hasReference(){ return !Float.isNaN(reference); }
    public float reference(){ return reference; }
    public float lower(){ return reference - GROUND_BAND; }
    public float upper(){ return reference + GROUND_BAND; }
    public float delta(float raw){ return hasReference() ? normalize(raw)-reference : normalize(raw); }
    public float visualDelta(float raw){
        float d = delta(raw);
        return Math.abs(d) <= GROUND_BAND ? 0f : d;
    }
    public String classify(float raw){
        if(!hasReference()) return "SIFIRLAMA BEKLENİYOR";
        float d = normalize(raw)-reference;
        if(d < -GROUND_BAND) return "NEGATİF ANOMALİ • BOŞLUK ADAYI";
        if(d >  GROUND_BAND) return "POZİTİF ANOMALİ • YAPI / METAL ADAYI";
        return "ZEMİN";
    }
    public static float normalize(float raw){
        if(Float.isNaN(raw)||Float.isInfinite(raw))return 0f;
        return Math.max(0f,raw);
    }
}
