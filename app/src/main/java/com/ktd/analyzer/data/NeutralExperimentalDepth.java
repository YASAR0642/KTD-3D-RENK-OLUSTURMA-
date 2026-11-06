package com.ktd.analyzer.data;

/**
 * V3.7: Önceki 0..25 m deneyi kullanıcıyı yanlış yönlendiriyordu.
 * 0..1023 genlik veya tek kazı derinliği, fiziksel metreyi belirlemez.
 * Eski çağrı noktaları için API korunur; derinlik tahmini YAPILMAZ.
 */
public final class NeutralExperimentalDepth {
    public static final float DISPLAY_RANGE_M=25f; // Yalnız eski dosya/API uyumluluğu.
    private NeutralExperimentalDepth(){}
    public static void apply(ScanData data){
        if(data!=null)data.reassessDepthSource();
    }
    public static float sampleEstimate(float raw,float reference,float p90Contrast){
        return Float.NaN;
    }
}
