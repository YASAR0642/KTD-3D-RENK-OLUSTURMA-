package com.ktd.analyzer.data;

/**
 * Two independently transmitted 10-bit ATmega328P ADC channels.
 * Lower EMS OUT -> Nano A1, upper EMS OUT -> Nano A2; vertical distance 0.30 m.
 * Device's 2.5 V REF is NOT equivalent to Nano ADC AVcc reference.
 * Difference is relative to a scan baseline, never absolute target depth.
 * The centered 0..1023 display signal preserves both signs for existing views.
 */
public final class NanoGradiometerSignal {
    public static final float SPACING_METERS=0.30f;
    public static final float ADC_MIDPOINT=511.5f;
    private NanoGradiometerSignal(){}
    public static boolean validAdc(float v){return Float.isFinite(v)&&v>=0f&&v<=1023f;}
    public static float gradientAdcPerMeter(float a1,float a2){
        return validAdc(a1)&&validAdc(a2)?(a1-a2)/SPACING_METERS:Float.NaN;
    }
    public static float combinedAdc(float a1,float a2){
        if(!validAdc(a1)||!validAdc(a2))return Float.NaN;
        // (A1-A2) can be -1023..1023. Scale by 1/2 to avoid clipping.
        return ADC_MIDPOINT+(a1-a2)*0.5f;
    }
    public static float baselineCorrectedGradient(float a1,float a2,float refA1,float refA2){
        float now=gradientAdcPerMeter(a1,a2),base=gradientAdcPerMeter(refA1,refA2);
        return Float.isFinite(now)&&Float.isFinite(base)?now-base:Float.NaN;
    }
}
