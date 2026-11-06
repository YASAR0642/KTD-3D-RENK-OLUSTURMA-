package com.ktd.analyzer.data;

import java.util.Arrays;
/**
 * Ereğli V3D'deki 4. hat / 12. nokta Z=675 sayısı ile, kullanıcı tarafından
 * kazı için bildirilen 8.50..8.95 m aralığını ÖRNEK OLARAK bağlayan cetvel.
 * Bu noktada yapının bulunduğu bağımsız doğrulanmış değildir; başka hedeflerde
 * kesin fiziksel derinlik sağlamaz. Salt görüntüleme için deneysel tahmindir.
 */
public final class EregliDepthRuler {
    private EregliDepthRuler() {}
    public static final float ANCHOR_Z=675f;
    public static final float LOW_AT_ANCHOR=8.50f;
    public static final float HIGH_AT_ANCHOR=8.95f;
    public static final float MID_AT_ANCHOR=(LOW_AT_ANCHOR+HIGH_AT_ANCHOR)*.5f;
    /** 1024/Z indeksi, Z=675 noktasına normlanır; 1024 çarpanı sadeleşir. */
    public static float estimate(float rawZ){
        if(!Float.isFinite(rawZ)||rawZ<=0f)return Float.NaN;
        float result=MID_AT_ANCHOR*ANCHOR_Z/rawZ;
        return Float.isFinite(result)&&result>0f&&result<=30f?result:Float.NaN;
    }
    public static float low(float rawZ){return validScale(rawZ,LOW_AT_ANCHOR);}
    public static float high(float rawZ){return validScale(rawZ,HIGH_AT_ANCHOR);}
    private static float validScale(float z,float anchor){
        if(!Float.isFinite(z)||z<=0f)return Float.NaN;
        float result=anchor*ANCHOR_Z/z;
        return Float.isFinite(result)&&result>0f&&result<=30f?result:Float.NaN;
    }
    private static final class Profile {
        final float reference,threshold;final boolean enough;
        Profile(float r,float t,boolean e){reference=r;threshold=t;enough=e;}
    }
    private static Profile profile(ScanData d){
        if(d==null||d.count<3)return new Profile(0,0,false);
        float[] values=new float[d.count];int n=0;
        for(int i=0;i<d.rows;i++)for(int j=0;j<d.cols;j++)if(d.filled[i][j]){
            float v=d.raw[i][j];if(Float.isFinite(v)&&v>1f)values[n++]=v;
        }
        if(n<3)return new Profile(0,0,false);
        Arrays.sort(values,0,n);float med=values[n/2];
        float[] dev=new float[n];for(int k=0;k<n;k++)dev[k]=Math.abs(values[k]-med);
        Arrays.sort(dev);float mad=dev[n/2];
        float reference=Float.isFinite(d.reference)&&d.reference>0f?d.reference:med;
        return new Profile(reference,Math.max(8f,Math.max(3f*mad,reference*.012f)),true);
    }
    public static boolean eligible(ScanData d,int r,int c){return eligible(d,r,c,profile(d));}
    private static boolean eligible(ScanData d,int r,int c,Profile p){
        if(!p.enough||!d.filled[r][c])return false;
        float z=d.raw[r][c];if(!Float.isFinite(estimate(z)))return false;
        if(z<=1f || (z>=1022f && z<=1023.01f))return false;
        return Math.abs(z-p.reference)>=p.threshold;
    }
    /** Orijinal ScanData/C/CSV DOKUNULMAZ; görselleştirme için ayrı kopya. */
    public static ScanData createVisualDepth(ScanData original){
        if(original==null||original.hasDepthData())return original;
        ScanData out=new ScanData(original.rows,original.cols);
        out.reference=original.reference;out.stepCm=original.stepCm;out.startCorner=original.startCorner;
        out.serpentine=original.serpentine;out.soilProfile=original.soilProfile;
        out.name=original.name;out.count=original.count;
        out.cIsDerivedFromRaw=original.cIsDerivedFromRaw;
        out.derivedCDivisor=original.derivedCDivisor;
        out.verifiedSiteMinMeters=original.verifiedSiteMinMeters;
        out.verifiedSiteMaxMeters=original.verifiedSiteMaxMeters;
        out.verifiedSiteLabel=original.verifiedSiteLabel;
        out.rulerDerivedDepth=true;
        Profile prof=profile(original);
        for(int r=0;r<original.rows;r++)for(int c=0;c<original.cols;c++){
            out.filled[r][c]=original.filled[r][c];
            out.x[r][c]=original.x[r][c];out.y[r][c]=original.y[r][c];
            out.z[r][c]=original.z[r][c];out.raw[r][c]=original.raw[r][c];
            out.c[r][c]=original.c[r][c];out.delta[r][c]=original.delta[r][c];
            out.fieldVerifiedDepth[r][c]=original.fieldVerifiedDepth[r][c];
            out.depth[r][c]=eligible(original,r,c,prof)?estimate(original.raw[r][c]):Float.NaN;
        }
        return out;
    }
}
