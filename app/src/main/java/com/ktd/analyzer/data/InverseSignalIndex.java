package com.ktd.analyzer.data;

/**
 * Deneysel ters sinyal dönüşümü: C_indeks = 800 / Z.
 *
 * Bu bir fiziksel derinlik denklemi değildir; bilinen bir manyetik kaynak
 * gücü, sensör aktarım fonksiyonu ve doğrulanmış saha kalibrasyonu içermez.
 * Bu nedenle hesaplanan C değerleri cihazın bağımsız derinlik ölçümü olarak
 * kullanılmaz. CSV'de yalnızca X,Y,Z,C sütunları tutulur.
 */
public final class InverseSignalIndex {
    public static final float NUMERATOR=800f;
    private InverseSignalIndex(){}
    public static float fromRaw(float z){
        if(!Float.isFinite(z)||z<=0f)return Float.NaN;
        return NUMERATOR/z;
    }
    public static boolean matches(float z,float c,float numerator){
        if(!Float.isFinite(z)||z<=0f||!Float.isFinite(c)||c<=0f)return false;
        float expected=numerator/z;
        return Math.abs(c-expected)<=Math.max(0.00010f,expected*0.00005f);
    }
}
