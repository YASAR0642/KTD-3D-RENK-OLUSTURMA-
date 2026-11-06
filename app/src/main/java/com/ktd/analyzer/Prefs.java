package com.ktd.analyzer;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String N = "ktd_v3";
    private Prefs() {}
    private static SharedPreferences p(Context c){ return c.getSharedPreferences(N, Context.MODE_PRIVATE); }

    /** Yalniz goruntuleme tercihi; ham sensor, referans, kayit ve derinlik etkilenmez. */
    public static int smartSensitivity(Context c){return clamp(p(c).getInt("smart_sensitivity",1),0,2);}
    public static int smartTransition(Context c){return clamp(p(c).getInt("smart_transition",1),0,2);}
    public static int smartBlueThreshold(Context c){return clamp(p(c).getInt("smart_blue",20),5,85);}
    public static int smartMineralThreshold(Context c){return clamp(p(c).getInt("smart_mineral",28),5,85);}
    public static int smartRedThreshold(Context c){return clamp(p(c).getInt("smart_red",48),15,95);}
    public static void setSmartColors(Context c,int sensitivity,int transition,int blue,int mineral,int red){
        p(c).edit().putInt("smart_sensitivity",clamp(sensitivity,0,2))
            .putInt("smart_transition",clamp(transition,0,2))
            .putInt("smart_blue",clamp(blue,5,85))
            .putInt("smart_mineral",clamp(mineral,5,85))
            .putInt("smart_red",clamp(red,15,95)).apply();
    }

    public static int rows(Context c){ return p(c).getInt("rows", 6); }
    public static int cols(Context c){ return p(c).getInt("cols", 10); }
    /** v3.5.2: saha örnekleme hücresi sabit 30 x 30 cm'dir. */
    public static int stepCm(Context c){ return 30; }
    public static int intervalMs(Context c){ return p(c).getInt("interval", 600); }
    public static int startCorner(Context c){ return p(c).getInt("start_corner", 0); } // 0 sol alt, 1 sağ alt
    public static boolean serpentine(Context c){ return p(c).getBoolean("serpentine", true); }
    public static boolean voiceGuide(Context c){ return p(c).getBoolean("voice_guide", true); }
    public static float heightGain(Context c){ return p(c).getFloat("gain", 1.0f); }
    /** Eski kalibrasyon katsayısı artık kullanılmaz; önceki tercihler etkisizdir. */
    public static float gradiometerDepthFactor(Context c){return 1f;}

    public static boolean smoothing(Context c){ return p(c).getBoolean("smoothing", true); }
    public static boolean grid(Context c){ return p(c).getBoolean("grid", true); }
    public static float anomalyGain(Context c){return p(c).getFloat("anomaly_gain",1.0f);}
    public static void setAnomalyGain(Context c,float gain){p(c).edit().putFloat("anomaly_gain",Math.max(.2f,Math.min(3f,gain))).apply();}
    /** Yalnız görsel tercihler: fiziki derinlik, C/Z ve referans hesaplarına dokunulmaz. */
    public static float groundOpacity(Context c){return p(c).getFloat("ground_opacity",.10f);}
    public static float anomalySharpness(Context c){return p(c).getFloat("anomaly_sharpness",1.50f);}
    public static void setVolumeVisuals(Context c,float opacity,float sharpness){p(c).edit()
        .putFloat("ground_opacity",Math.max(.02f,Math.min(.35f,opacity)))
        .putFloat("anomaly_sharpness",Math.max(.70f,Math.min(2.50f,sharpness))).apply();}
    public static int volumeLayers(Context c){return p(c).getInt("vol_layers",2);}
    public static float volumeSpread(Context c){return p(c).getFloat("vol_spread",1.0f);}
    public static float volumeThreshold(Context c){return p(c).getFloat("vol_threshold",.35f);}
    public static void setVolume(Context c,int layers,float spread,float threshold){p(c).edit()
        .putInt("vol_layers",Math.max(1,Math.min(6,layers)))
        .putFloat("vol_spread",Math.max(.6f,Math.min(1.6f,spread)))
        .putFloat("vol_threshold",Math.max(.03f,Math.min(.92f,threshold))).apply();}
    public static String prefix(Context c){ return p(c).getString("prefix", "KTD_SCAN"); }
    public static int palette(Context c){
        com.ktd.analyzer.view.ColorMaps.configure(c);
        int v=p(c).getInt("palette",0);
        return (v>=0 && v<com.ktd.analyzer.view.ColorMaps.count())?v:0;
    }

    /** Kullanıcı yalnız X/Y/adım/yön seçer; alan boyutu uygulama içinde otomatik hesaplanır. */
    public static void setGrid(Context c,int rows,int cols,int step,int startCorner,boolean serpentine){
        p(c).edit().putInt("rows", clamp(rows,2,60)).putInt("cols",clamp(cols,2,60))
                .putInt("step",30).putInt("start_corner",startCorner==1?1:0)
                .putBoolean("serpentine",serpentine).apply();
    }
    /** Eski sürümlerle uyumlu çağrı. */
    public static void setGrid(Context c,int r,int co,int step,int interval){
        p(c).edit().putInt("rows", clamp(r,2,60)).putInt("cols",clamp(co,2,60))
                .putInt("step",30).putInt("interval",clamp(interval,120,3000)).apply();
    }
    public static void setView(Context c,float gain,boolean smooth,boolean grid){
        p(c).edit().putFloat("gain",Math.max(.1f,Math.min(8f,gain))).putBoolean("smoothing",smooth).putBoolean("grid",grid).apply();
    }
    public static void setVoiceGuide(Context c,boolean on){p(c).edit().putBoolean("voice_guide",on).apply();}
    public static void setPrefix(Context c,String s){ p(c).edit().putString("prefix", s==null||s.trim().isEmpty()?"KTD_SCAN":s.trim()).apply(); }
    public static void setPalette(Context c,int v){ p(c).edit().putInt("palette",Math.max(0,Math.min(com.ktd.analyzer.view.ColorMaps.count()-1,v))).apply(); }
    private static int clamp(int v,int a,int b){ return Math.max(a,Math.min(b,v)); }
}
