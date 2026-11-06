package com.ktd.analyzer.data;

public final class SensorFrame {
    public final float x,y,z,c;
    /** Optional 10-bit Nano-compatible voltage ADC, not gradiometer signal or depth. */
    public int legacyAdc10=-1;
    /** Explicit two-channel Nano frame. A1=lower, A2=upper, spacing=0.30 m. */
    public final boolean hasDualSensors;
    public final float lowerAdc,upperAdc;
    /** ADC counts per metre: informational, not nT/m. */
    public final float gradientAdcPerMeter;
    public final float value;
    public final boolean hasXYZC;
    /** Explicit REF:NNN packet starts a NEW scan, never a grid measurement. */
    public final boolean referencePacket;

    /** Eski tek kanal veri için geriye uyumluluk. */
    public SensorFrame(float value){ this(Float.NaN,Float.NaN,Float.NaN,value,false); }
    /** Used ONLY for frames explicitly carrying both A1 and A2. */
    public static SensorFrame dualAdc(float a1,float a2){
        if(!NanoGradiometerSignal.validAdc(a1)||!NanoGradiometerSignal.validAdc(a2))
            throw new IllegalArgumentException("ADC 0..1023 gerekli");
        return new SensorFrame(a1,a2);
    }
    private SensorFrame(float a1,float a2){
        // The rest of the Android code consumes a baseline-centered 10-bit scalar.
        this.x=Float.NaN;this.y=Float.NaN;this.z=Float.NaN;
        this.c=NanoGradiometerSignal.combinedAdc(a1,a2);
        this.value=this.c;this.hasXYZC=false;this.referencePacket=false;
        this.hasDualSensors=true;this.lowerAdc=a1;this.upperAdc=a2;
        this.gradientAdcPerMeter=NanoGradiometerSignal.gradientAdcPerMeter(a1,a2);
    }
    public SensorFrame(float x,float y,float z,float c){ this(x,y,z,c,true); }
    private SensorFrame(float x,float y,float z,float c,boolean has){
        this(x,y,z,c,has,false);
    }
    private SensorFrame(float x,float y,float z,float c,boolean has,boolean ref){
        this.x=x; this.y=y; this.z=z; this.c=c; this.hasXYZC=has;
        this.referencePacket=ref;
        this.hasDualSensors=false;this.lowerAdc=Float.NaN;this.upperAdc=Float.NaN;
        this.gradientAdcPerMeter=Float.NaN;
        // KTD native protokolü: X/Y saha koordinatı, Z ham sensör, C derinlik (m).
        this.value = has && Float.isFinite(z) ? z : c;
    }

    public static SensorFrame reference(float newReference){
        return new SensorFrame(Float.NaN,Float.NaN,Float.NaN,newReference,false,true);
    }
    /** Ham sensör/genlik değeri. */
    public float signal(){ return hasXYZC && Float.isFinite(z) ? z : c; }

    /** Cihazın gönderdiği C derinlik alanı (metre). */
    public float channelC(){ return hasXYZC && Float.isFinite(c) ? c : Float.NaN; }
}
