package com.ktd.analyzer.data;

/**
 * Shared reference-relative PROJECT depth index (2D, 3D, 4D, NORMAL, GMR,
 * analysis). Not physical distance without target-specific field calibration.
 *
 * R: baseline measured at the start of the SAME scan (not nominal 512).
 * delta=raw-R. Positive and negative headroom are normalized separately:
 * Zrelative = abs(delta) (minimum 15 ADC counts).
 * depthIndex = (800 / Zrelative) / 10.
 *
 * 15 raw counts is the GMR display threshold; below it there is no anomaly.
 * A null reading is NaN, NEVER a fabricated 0 m depth.
 */
public final class ReferenceMeterModel {
    private ReferenceMeterModel(){}
    // An experimental dimensionless inverse-signal index, NEVER physical metres.
    public static final float NUMERATOR=80f; // (800 / |olcum - referans|) / 10
    public static final float ADC_RANGE=1023f;
    public static final float ANOMALY_THRESHOLD=15f;
    public static float normalizedSignal(float raw,float reference){
        if(!Float.isFinite(raw)||!Float.isFinite(reference)||reference<=0f||reference>=ADC_RANGE)return Float.NaN;
        float delta=raw-reference;
        if(Math.abs(delta)<ANOMALY_THRESHOLD)return Float.NaN;
        return Math.abs(delta);
    }
    public static float meters(float raw,float reference){
        float z=normalizedSignal(raw,reference);
        return Float.isFinite(z)&&z>0f?NUMERATOR/z:Float.NaN;
    }
    public static float metersFromDelta(float delta,float reference){
        return Float.isFinite(delta)?meters(reference+delta,reference):Float.NaN;
    }
}
