package com.ktd.analyzer.data;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Ortam referansina gore iki yonlu normalize edilen proje derinlik indeksi.
 * R = taramanin ilk referansi, delta = hamZ-R.
 * Z_ref=|delta|, goruntuleme olcegi=(800/Z_ref)/10 (|delta| >= 15).
 * Bu fiziksel derinlik degildir. CSV ham Z/C kayitlari degistirilmez.
 */
public final class ReferenceInverseDepth {
    private ReferenceInverseDepth(){}
    public static final float ADC_MAX=1023f;
    public static final float GROUND_BAND=ReferenceMeterModel.ANOMALY_THRESHOLD;
    public static final float SATURATION_ASSUMED_METERS=.05f;
    public static final class Reading {
        public final float meters,relativeStrength;
        public final boolean anomaly,positive,saturated,approxReference;
        Reading(float m,float s,boolean a,boolean p,boolean sat,boolean approx){
            meters=m;relativeStrength=s;anomaly=a;positive=p;saturated=sat;approxReference=approx;
        }
    }
    public static float referenceOf(ScanData scan){
        if(scan==null)return Float.NaN;
        if(Float.isFinite(scan.reference)&&scan.reference>0f&&scan.reference<ADC_MAX)
            return scan.reference;
        // Dört sütunlu CSV, ilk REF değerini içermez. Sadece ön izleme için
        // sağlam bir medyan tahmini; gerçek REFERANS diye sunulmaz.
        ArrayList<Float> vals=new ArrayList<>();
        for(int r=0;r<scan.rows;r++)for(int c=0;c<scan.cols;c++)if(scan.filled[r][c]){
            float z=scan.raw[r][c];
            if(Float.isFinite(z)&&z>1f&&z<1022f)vals.add(z);
        }
        if(vals.size()<3)return Float.NaN;
        Collections.sort(vals);
        return vals.get(vals.size()/2);
    }
    public static Reading measure(float raw,float ref){
        return measure(raw,ref,1f);
    }
    public static Reading measure(float raw,float ref,float calibrationFactor){
        float z=ReferenceMeterModel.normalizedSignal(raw,ref);
        if(!Float.isFinite(z))return new Reading(Float.NaN,Float.NaN,false,
                Float.isFinite(raw)&&Float.isFinite(ref)&&raw>=ref,false,false);
        float meters=ReferenceMeterModel.NUMERATOR/z;
        boolean positive=raw>=ref;
        boolean saturated=raw<=1f||raw>=ADC_MAX-1f;
        return new Reading(meters,Math.min(1f,z/ADC_MAX),true,positive,saturated,false);
    }
    public static Reading at(ScanData d,int row,int col){
        if(d==null||row<0||col<0||row>=d.rows||col>=d.cols||!d.filled[row][col])
            return new Reading(Float.NaN,Float.NaN,false,false,false,false);
        float ref=referenceOf(d);
        Reading base=measure(d.raw[row][col],ref,d.depthCalibrationFactor);
        if(!d.referenceEstimated)return base;
        return new Reading(base.meters,base.relativeStrength,base.anomaly,base.positive,base.saturated,true);
    }
    /** Orijinal X/Y/Z/C ve CSV saklanır, 3D/4D yalnız kopyayı kullanır. */
    public static ScanData createVisualDepth(ScanData original){
        if(original==null)return null;
        ScanData out=new ScanData(original.rows,original.cols);
        out.reference=referenceOf(original);
        out.depthCalibrationFactor=original.depthCalibrationFactor;
        out.referenceEstimated=original.referenceEstimated;
        out.stepCm=original.stepCm;out.startCorner=original.startCorner;
        out.serpentine=original.serpentine;out.soilProfile=original.soilProfile;
        out.name=original.name;out.count=original.count;
        out.cIsDerivedFromRaw=original.cIsDerivedFromRaw;
        out.derivedCDivisor=original.derivedCDivisor;
        out.verifiedSiteMinMeters=original.verifiedSiteMinMeters;
        out.verifiedSiteMaxMeters=original.verifiedSiteMaxMeters;
        out.verifiedSiteLabel=original.verifiedSiteLabel;
        out.rulerDerivedDepth=true;
        for(int r=0;r<original.rows;r++)for(int c=0;c<original.cols;c++){
            out.filled[r][c]=original.filled[r][c];
            out.x[r][c]=original.x[r][c];out.y[r][c]=original.y[r][c];
            out.z[r][c]=original.z[r][c];out.raw[r][c]=original.raw[r][c];
            out.c[r][c]=original.c[r][c];out.delta[r][c]=original.delta[r][c];
            out.fieldVerifiedDepth[r][c]=original.fieldVerifiedDepth[r][c];
            if(!original.filled[r][c])continue;
            Reading reading=measure(original.raw[r][c],out.reference,out.depthCalibrationFactor);
            // Orijinal C'de bağımsız cihaz derinliği varsa ayrı saklanır.
            // Z temelli deneysel görünüm her iki tarafta aynı cetveldir.
            out.depth[r][c]=Float.isFinite(reading.meters)?reading.meters:Float.NaN;
        }
        return out;
    }

    /**
     * Voxler-like display axis: (800 / Z) / 10 is used in its numeric
     * coordinate, without per-scan min/max normalization. Thus two scans
     * with identical Z values produce identical C positions.
     * C is a derived magnetic SIGNAL INDEX, NOT a measured depth in metres.
     */
    public static ScanData createRelativeIndexVolume(ScanData original){
        if(original==null)return null;
        ScanData out=createVisualDepth(original);
        // Referans farkinin tersine dayanan ortak (800/|Z-R|)/10 konumu.
        // 2D, 3D, 4D ve analiz ayni referans temelli formulu kullanir.
        for(int r=0;r<out.rows;r++)for(int c=0;c<out.cols;c++){
            float estimate=ReferenceMeterModel.meters(out.raw[r][c],out.reference);
            out.depth[r][c]=out.filled[r][c] ? estimate : Float.NaN;
        }
        out.cIsDerivedFromRaw=true;
        out.derivedCDivisor=800;
        out.rulerDerivedDepth=true;
        return out;
    }

}
