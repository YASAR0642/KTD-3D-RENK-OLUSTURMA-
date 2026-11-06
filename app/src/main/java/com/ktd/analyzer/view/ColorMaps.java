package com.ktd.analyzer.view;

import android.graphics.Color;
import android.content.Context;
import com.ktd.analyzer.Prefs;

/**
 * KTD renk paletleri.
 * 0-7: uygulamanın yerleşik paletleri.
 * 8-13: kullanıcı tarafından sağlanan CLR paletlerinin uygulamaya aktarılmış karşılıkları.
 */
public final class ColorMaps {
    public static final int COUNT=20;
    public static final String[] NAMES={
        "KTD Spectrum", "Siyah / Beyaz", "Neon", "Termal", "Buz",
        "Bakır", "Viridis", "Boşluk İçi Metal", "KTD VOLENDER", "ODA", "Malzeme", "Visualizer Klasik", "GER / KTD Anomali", "KTD 6 Renk Deney", "Küp / Turuncu", "Boşluk / Mavi", "Dolgu / Buz Mavisi", "Demir / Siyah", "Metal / Kırmızı", "KTD Polarite (+/-)"
    };
    private ColorMaps(){}
    private static volatile java.util.ArrayList<com.ktd.analyzer.CustomPaletteStore.Item> custom=new java.util.ArrayList<>();
    public static int count(){return COUNT+custom.size();}
    public static String name(int index){return index<COUNT?NAMES[Math.max(0,Math.min(COUNT-1,index))]:index-COUNT<custom.size()?custom.get(index-COUNT).name:"Özel";}
    private static volatile int sensitivity=1, transition=1;
    private static volatile float blueThreshold=.20f, mineralThreshold=.28f, redThreshold=.48f;
    public static void configure(Context context){
        custom=com.ktd.analyzer.CustomPaletteStore.load(context);
        sensitivity=Prefs.smartSensitivity(context);
        transition=Prefs.smartTransition(context);
        blueThreshold=Prefs.smartBlueThreshold(context)/100f;
        mineralThreshold=Prefs.smartMineralThreshold(context)/100f;
        redThreshold=Prefs.smartRedThreshold(context)/100f;
    }


    private static final PaletteData P8=new PaletteData(
        new float[]{0.00000000f,0.37800688f,0.40893471f,0.42268041f,0.55326461f,0.59106530f,0.66323024f,0.67697595f,0.75601375f,0.79111842f,0.81907895f,0.85855263f,0.88160056f,0.89690722f,0.92096220f,0.93658338f,0.94736842f,0.94799930f,0.97532895f,1.00000000f},
        new int[]{Color.argb(255,0,0,0),Color.argb(255,255,0,0),Color.argb(255,25,25,25),Color.argb(255,255,255,102),Color.argb(255,255,255,0),Color.argb(255,0,0,0),Color.argb(255,255,255,0),Color.argb(255,0,255,0),Color.argb(255,0,0,255),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,0,0,0),Color.argb(255,0,0,255),Color.argb(255,255,0,0),Color.argb(255,0,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0)},
        new float[]{0.00000000f,0.37800688f,0.40893471f,0.94166230f,0.94799930f,1.00000000f},
        new int[]{61,61,235,235,89,89}
    );
    private static final PaletteData P9=new PaletteData(
        new float[]{0.00000000f,0.37800688f,0.40893471f,0.42268041f,0.44901316f,0.47697368f,0.49013158f,0.50164474f,0.51151316f,0.53289474f,0.55756579f,0.57401316f,0.59106530f,0.62828947f,0.66323024f,0.67697595f,0.69736842f,0.71381579f,0.72861842f,0.75601375f,0.80098684f,0.85827946f,0.87500000f,0.88486842f,0.89638158f,0.92096220f,0.93658338f,0.94166230f,0.94572368f,0.94799930f,0.94901316f,0.95723684f,0.97203947f,0.97697368f,0.98848684f,1.00000000f},
        new int[]{Color.argb(255,230,230,230),Color.argb(255,255,0,0),Color.argb(255,25,25,25),Color.argb(255,0,0,255),Color.argb(255,50,50,205),Color.argb(255,103,103,152),Color.argb(255,137,137,118),Color.argb(255,0,255,0),Color.argb(255,168,168,87),Color.argb(255,208,208,47),Color.argb(255,255,255,0),Color.argb(255,0,255,255),Color.argb(255,0,0,0),Color.argb(255,0,0,255),Color.argb(255,255,0,0),Color.argb(255,0,0,0),Color.argb(255,255,255,255),Color.argb(255,64,180,165),Color.argb(255,102,51,51),Color.argb(255,0,0,255),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,255,0,0),Color.argb(255,204,102,51),Color.argb(255,153,51,102),Color.argb(255,186,89,77),Color.argb(255,204,110,63),Color.argb(255,195,116,60),Color.argb(255,126,166,39),Color.argb(255,0,255,0),Color.argb(255,204,162,142),Color.argb(255,204,183,173),Color.argb(255,204,204,204)},
        new float[]{0.00000000f,0.37800688f,0.40893471f,0.94166230f,0.94799930f,1.00000000f},
        new int[]{61,255,235,235,79,89}
    );
    private static final PaletteData P10=new PaletteData(
        new float[]{0.00000000f,0.00000000f,0.00000000f,0.00000000f,0.00000000f,0.00000000f,0.00764818f,0.03059273f,0.03059273f,0.03059273f,0.27061856f,0.32130584f,0.43403442f,0.43403442f,0.43403442f,0.43403442f,0.43785851f,0.54684512f,0.54684512f,0.62130584f,0.62130584f,0.62130584f,0.84512428f,0.84703633f,0.84703633f,0.87954111f,0.87954111f,0.89866157f,0.89866157f,0.97131931f,0.98470363f,1.00000000f},
        new int[]{Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,204,51,0),Color.argb(255,255,255,255),Color.argb(255,255,255,0),Color.argb(255,255,255,255),Color.argb(255,0,51,51),Color.argb(255,102,51,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,229,229,229),Color.argb(255,0,0,0),Color.argb(255,0,255,0),Color.argb(255,0,0,0),Color.argb(255,0,0,0),Color.argb(255,0,0,0),Color.argb(255,221,221,221),Color.argb(255,255,255,255),Color.argb(255,30,255,30),Color.argb(255,0,255,0),Color.argb(255,147,147,96),Color.argb(255,0,255,0),Color.argb(255,255,0,0),Color.argb(255,166,0,0),Color.argb(255,153,0,0),Color.argb(255,255,0,0)},
        new float[]{0.00000000f,0.24812030f,0.31766917f,0.75563910f,1.00000000f},
        new int[]{20,20,13,228,228}
    );
    private static final PaletteData P11=new PaletteData(
        new float[]{0.00000000f,1.00000000f},
        new int[]{Color.argb(255,0,0,0),Color.argb(255,255,255,255)},
        new float[]{0.00000000f,1.00000000f},
        new int[]{255,255}
    );
    private static final PaletteData P12=new PaletteData(
        new float[]{0.00000000f,0.00000000f,0.00000000f,0.00000000f,0.00000000f,0.00000000f,0.00764818f,0.03059273f,0.03059273f,0.03059273f,0.03059273f,0.06309751f,0.07648184f,0.10325048f,0.12810707f,0.14340344f,0.17590822f,0.20650096f,0.54684512f,0.62130584f,0.62130584f,0.62130584f,0.84512428f,0.84703633f,0.84703633f,0.87954111f,0.87954111f,0.89866157f,0.89866157f,0.97131931f,0.98470363f,1.00000000f},
        new int[]{Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,204,51,0),Color.argb(255,255,255,255),Color.argb(255,255,255,0),Color.argb(255,255,255,255),Color.argb(255,0,51,51),Color.argb(255,102,51,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,255,255,255),Color.argb(255,229,229,229),Color.argb(255,0,0,0),Color.argb(255,0,255,0),Color.argb(255,0,0,0),Color.argb(255,0,0,0),Color.argb(255,0,0,0),Color.argb(255,221,221,221),Color.argb(255,255,255,255),Color.argb(255,30,255,30),Color.argb(255,0,255,0),Color.argb(255,147,147,96),Color.argb(255,0,255,0),Color.argb(255,255,0,0),Color.argb(255,166,0,0),Color.argb(255,153,0,0),Color.argb(255,255,0,0)},
        new float[]{0.00000000f,0.20300752f,0.33082707f,0.75563910f,1.00000000f},
        new int[]{255,255,255,228,228}
    );
    private static final PaletteData P13=new PaletteData(
        new float[]{0.00000000f,0.37800688f,0.40893471f,0.42268041f,0.55326461f,0.59106530f,0.66323024f,0.67697595f,0.75601375f,0.85827946f,0.88160056f,0.89690722f,0.92096220f,0.93658338f,0.94166230f,0.94799930f,1.00000000f},
        new int[]{Color.argb(61,0,0,0),Color.argb(255,255,0,0),Color.argb(235,25,25,25),Color.argb(235,255,255,102),Color.argb(235,255,255,0),Color.argb(235,0,0,0),Color.argb(235,255,255,0),Color.argb(235,0,255,0),Color.argb(235,0,0,255),Color.argb(235,204,204,51),Color.argb(235,0,0,0),Color.argb(235,153,102,51),Color.argb(235,0,0,0),Color.argb(235,204,102,51),Color.argb(235,153,51,102),Color.argb(79,204,110,63),Color.argb(89,204,204,204)},
        null,
        null
    );

    public static int color(int palette,float value,float maxAbs){
        if(maxAbs<1f)maxAbs=1f;
        int base=baseColor(palette,value,maxAbs);
        return smartOverlay(palette,value,maxAbs,base);
    }

    private static int baseColor(int palette,float value,float maxAbs){
        if(palette>=COUNT){
            int index=palette-COUNT;
            if(index>=0 && index<custom.size()){
                int[] stops=custom.get(index).colors;
                float u=Math.max(0f,Math.min(1f,(value/Math.max(1f,maxAbs)+1f)*.5f))*(stops.length-1);
                int section=Math.min(stops.length-2,(int)u);
                return lerp(stops[section],stops[section+1],u-section);
            }
        }
        if(maxAbs<1f)maxAbs=1f;
        float t=Math.max(-1f,Math.min(1f,value/maxAbs));
        float u=(t+1f)*.5f;
        switch(palette){
            case 1: return opaque(gray(t));
            case 2: return opaque(diverging(t,Color.rgb(90,0,160),Color.rgb(0,235,255),Color.rgb(40,255,95),Color.rgb(255,245,0),Color.rgb(255,0,145)));
            case 3: return opaque(diverging(t,Color.rgb(10,0,40),Color.rgb(80,0,140),Color.rgb(225,35,20),Color.rgb(255,145,0),Color.rgb(255,250,210)));
            case 4: return opaque(diverging(t,Color.rgb(0,20,90),Color.rgb(0,80,220),Color.rgb(0,220,255),Color.rgb(170,250,255),Color.WHITE));
            case 5: return opaque(diverging(t,Color.rgb(45,15,5),Color.rgb(120,50,15),Color.rgb(205,105,25),Color.rgb(250,185,70),Color.rgb(255,242,195)));
            case 6: return opaque(diverging(t,Color.rgb(68,1,84),Color.rgb(59,82,139),Color.rgb(33,145,140),Color.rgb(94,201,98),Color.rgb(253,231,37)));
            case 7: return opaque(P8.color(u));
            case 8: return opaque(P9.color(u));
            case 9: return opaque(P10.color(u));
            case 10:return opaque(P12.color(u));
            case 12:return gerKtdAnomaly(value);
            case 13:return experimentalSix(value);
            case 14:return targetContour(value, 0);
            case 15:return targetContour(value, 1);
            case 16:return targetContour(value, 2);
            case 17:return targetContour(value, 3);
            case 18:return targetContour(value, 4);
            case 19:return polarContour(value);
            case 11:return opaque(diverging(t,Color.rgb(6,25,165),Color.rgb(0,190,240),Color.rgb(0,150,10),Color.rgb(255,230,0),Color.rgb(255,100,0)));
            default:return opaque(diverging(t,Color.rgb(20,55,170),Color.rgb(10,145,210),Color.rgb(75,215,55),Color.rgb(245,225,35),Color.rgb(255,45,20)));
        }
    }

    /**
     * Tum paletlerde ortak akilli anomali vurgusu:
     * - zemin secili palette kalir
     * - negatif guclu sinyal = net mavi (bosluk benzeri)
     * - pozitif orta sinyal = daginik ochre/ambre (mineral benzeri)
     * - pozitif guclu sinyal = net kirmizi (metal benzeri)
     * Bu bir teshis DEGILDIR; gorsel yorum yardimidir.
     */
    private static int smartOverlay(int palette,float value,float maxAbs,int base){
        if(!Float.isFinite(value))return base;
        // 0 Dusuk, 1 Orta, 2 Yuksek: yalniz gorsel esikleri kaydirir.
        final float sensitivityFactor=sensitivity==0?1.25f:(sensitivity==2?.75f:1f);
        float rel=Math.abs(value)/Math.max(1f,maxAbs);
        float noise=.09f*sensitivityFactor;
        if(Math.abs(value)<6f || rel<noise)return base;
        final int cyan=Color.rgb(0,224,255),blue=Color.rgb(0,82,255),deepBlue=Color.rgb(0,48,221);
        final int mineralA=Color.rgb(182,146,62),mineralB=Color.rgb(131,103,41),mineralC=Color.rgb(97,79,34);
        final int red=Color.rgb(255,34,26),deepRed=Color.rgb(198,0,0);
        // Sert gecis keskin renk siniri verir; yumusak gecis araligi genisletir.
        float fade=transition==0?.14f:(transition==2?.015f:.065f);
        if(value<0f){
            float start=Math.max(noise,blueThreshold*sensitivityFactor);
            if(rel<start-fade)return base;
            if(rel<start)return lerp(base,cyan,clamp01((rel-(start-fade))/Math.max(.001f,fade)));
            if(rel<start+fade)return lerp(cyan,blue,clamp01((rel-start)/Math.max(.001f,fade)));
            return lerp(blue,deepBlue,clamp01((rel-start-fade)/.38f));
        }
        float mineralStart=Math.max(noise,mineralThreshold*sensitivityFactor);
        float metalStart=Math.max(mineralStart+.04f,redThreshold*sensitivityFactor);
        int mineral=chooseMineralColor(value,mineralA,mineralB,mineralC);
        if(rel<mineralStart-fade)return base;
        if(rel<mineralStart)return lerp(base,mineral,clamp01((rel-mineralStart+fade)/Math.max(.001f,fade)));
        if(rel<metalStart-fade)return mineral;
        if(rel<metalStart)return lerp(mineral,red,clamp01((rel-metalStart+fade)/Math.max(.001f,fade)));
        return lerp(red,deepRed,clamp01((rel-metalStart)/.40f));
    }

    private static int chooseMineralColor(float value,int a,int b,int c){
        int bucket=Math.abs(Math.round(value*10f))%3;
        return bucket==0?a:(bucket==1?b:c);
    }

    private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}

    /**
     * GER/KTD yorumsal renklendirme. Sifir = yesil, negatif = mavi,
     * pozitif = sari/turuncu/kirmizi. Bu metot yalniz sinyal buyuklugunu
     * kodlar: mineral, demir, mezar, oda veya kup teshisi YAPMAZ.
     * Tum 2D/3D/4D yuzeylerinde ayni sabit Z delta esikleri kullanilir.
     */
    private static int gerKtdAnomaly(float delta){
        if(!Float.isFinite(delta))return Color.rgb(0,150,18);
        float d=Math.max(-120f,Math.min(120f,delta));
        final int green=Color.rgb(0,153,17);
        if(d < -10f){
            float t=Math.min(1f,(-d-10f)/70f);
            return lerp(green,Color.rgb(0,43,215),Math.min(1f,t*2.2f));
        }
        if(d <= 10f)return green;
        if(d < 38f)return lerp(green,Color.rgb(255,238,0),(d-10f)/28f);
        if(d < 66f)return lerp(Color.rgb(255,238,0),Color.rgb(255,123,0),(d-38f)/28f);
        if(d < 92f)return lerp(Color.rgb(255,123,0),Color.rgb(240,15,12),(d-66f)/26f);
        return lerp(Color.rgb(240,15,12),Color.rgb(150,0,0),(d-92f)/28f);
    }

    /**
     * Deneysel GORSEL yorum paleti; malzeme / bosluk tespiti DEGILDIR.
     * Tum modlarda sabit, isaretli referans farki (Z delta) birimleri:
     * d<=-35 mavi, -10..10 yesil, 10..30 sari, 30..55 turuncu,
     * 55..75 koyu kontur, >=75 kirmizi. Koyu kontur demir kaniti DEGILDIR.
     */
    private static int experimentalSix(float d){
        if(!Float.isFinite(d))return Color.rgb(0,150,17);
        final int blue=Color.rgb(0,58,205);
        final int green=Color.rgb(0,165,35);
        final int yellow=Color.rgb(255,225,0);
        final int orange=Color.rgb(244,119,0);
        final int black=Color.rgb(15,13,16);
        final int red=Color.rgb(239,19,18);
        if(d<=-35f)return blue;
        if(d<-10f)return lerp(blue,green,(d+35f)/25f);
        if(d<=10f)return green;
        if(d<30f)return lerp(green,yellow,(d-10f)/20f);
        if(d<55f)return lerp(yellow,orange,(d-30f)/25f);
        if(d<75f)return lerp(orange,black,(d-55f)/20f);
        if(d<90f)return lerp(black,red,(d-75f)/15f);
        return red;
    }


    /**
     * Hedef yorum paletleri: delta, referansa gore isaretli sensör farkidir.
     * Bunlar madde tespiti DEGIL, sadece secilen sinyalin gorunus bicimidir.
     * Mod 0: turuncu kup / siyah ince kontur
     * Mod 1: mavi negatif bolge / siyah ince kontur
     * Mod 2: acik buz mavisi negatif bolge / siyah ince kontur
     * Mod 3: siyah pozitif bolge / turuncu ince kontur
     * Mod 4: kirmizi merkez / sari / turuncu / siyah ince kontur
     * Ekranlar ayni ColorMaps.color() fonksiyonunu kullandiginda tutarlidir.
     */
    private static int targetContour(float delta,int mode){
        final int ground=Color.rgb(0,164,34);
        if(!Float.isFinite(delta)) return ground;
        // Deneysel sinyal esikleri; olculen madde turunun kaniti degildir.
        float a=(mode==1 || mode==2) ? -delta : delta;
        int black=Color.rgb(12,10,13);
        int orange=Color.rgb(249,122,5);
        int blue=Color.rgb(0,66,221);
        int ice=Color.rgb(137,229,251);
        int yellow=Color.rgb(255,228,5);
        int red=Color.rgb(244,22,18);
        // 0..10 doğal zemin, 10..23 gecis, 23..27 ince dis kontur,
        // 27..32 gecis, 32+ hedef cekirdegi.
        if(a<=10f) return ground;
        if(a<20f) return lerp(ground,Color.rgb(146,199,33),(a-10f)/10f);
        if(a<23f) return lerp(Color.rgb(146,199,33),black,(a-20f)/3f);
        if(a<27f) return mode==3?orange:black;
        if(mode==4){
            if(a<32f) return lerp(black,orange,(a-27f)/5f);
            if(a<42f) return orange;
            if(a<52f) return lerp(orange,yellow,(a-42f)/10f);
            if(a<62f) return lerp(yellow,red,(a-52f)/10f);
            return red;
        }
        if(mode==3){
            if(a<33f) return lerp(orange,black,(a-27f)/6f);
            return black;
        }
        int core=mode==0?orange:(mode==1?blue:ice);
        if(a<33f) return lerp(black,core,(a-27f)/6f);
        return core;
    }

    /**
     * Yeni palet 19: tek ham isaretli delta ile iki polarite gorunumu.
     * Bir renk bir malzeme teshisi DEGILDIR. -35..-60 mavi sinyal,
     * -60 altinda negatif cekirdek mor + lacivert + turkuaz kontur;
     * pozitif tarafta turuncu-sari-kirmizi cekirdek gorunumu.
     * Esikler KTD 512 merkezli GRADYAN delta birimindedir;
     * Nano ADC10 180/200..260/366 esikleri BAMBASKA olcektir.
     */
    private static int polarContour(float delta){
        final int green=Color.rgb(25,185,50);
        final int black=Color.rgb(10,13,17);
        if(!Float.isFinite(delta))return green;
        if(delta<0f){
            float a=-delta;
            if(a<=8f)return green;
            if(a<13f)return lerp(green,Color.rgb(10,145,245),(a-8f)/5f);
            if(a<30f)return lerp(Color.rgb(10,145,245),Color.rgb(0,63,218),(a-13f)/17f);
            if(a<44f)return Color.rgb(0,63,218); // mavi negatif bolge
            if(a<48f)return lerp(Color.rgb(0,63,218),black,(a-44f)/4f);
            if(a<52f)return black; // ince ayirma konturu
            if(a<61f)return lerp(Color.rgb(0,222,230),Color.rgb(7,34,130),(a-52f)/9f);
            if(a<76f)return lerp(Color.rgb(7,34,130),Color.rgb(145,30,220),(a-61f)/15f);
            return Color.rgb(166,35,235); // guclu NEGATIF cekirdek
        }
        float a=delta;
        if(a<=8f)return green;
        if(a<14f)return lerp(green,Color.rgb(255,166,0),(a-8f)/6f);
        if(a<22f)return lerp(Color.rgb(255,166,0),black,(a-14f)/8f);
        if(a<25f)return black; // ince dis kontur
        if(a<39f)return Color.rgb(246,116,0);
        if(a<54f)return lerp(Color.rgb(246,116,0),Color.rgb(255,231,0),(a-39f)/15f);
        if(a<69f)return lerp(Color.rgb(255,231,0),Color.rgb(245,19,18),(a-54f)/15f);
        return Color.rgb(245,19,18); // guclu POZITIF cekirdek
    }

    public static int recommendedAlpha(int palette){ return 255; }

    private static int gray(float t){int v=(int)(255f*(t+1f)*.5f);v=Math.max(0,Math.min(255,v));return Color.rgb(v,v,v);}
    private static int opaque(int c){return Color.rgb(Color.red(c),Color.green(c),Color.blue(c));}
    private static int withAlpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}
    private static int diverging(float t,int c0,int c1,int c2,int c3,int c4){float u=(t+1f)*2f;int i=Math.min(3,(int)Math.floor(u));float f=u-i;int a=i==0?c0:i==1?c1:i==2?c2:c3;int b=i==0?c1:i==1?c2:i==2?c3:c4;return lerp(a,b,f);}
    private static int lerp(int a,int b,float t){t=Math.max(0f,Math.min(1f,t));int r=(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),g=(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),bl=(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t),al=(int)(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t);return Color.argb(al,r,g,bl);}

    private static final class PaletteData {
        final float[] x; final int[] c; final float[] ax; final int[] av;
        PaletteData(float[] x,int[] c,float[] ax,int[] av){this.x=x;this.c=c;this.ax=ax;this.av=av;}
        int color(float u){
            int col=sampleColor(x,c,u);
            int alpha=Color.alpha(col);
            if(ax!=null&&av!=null&&ax.length>0&&av.length>0) alpha=(alpha*sampleAlpha(ax,av,u))/255;
            return Color.argb(alpha,Color.red(col),Color.green(col),Color.blue(col));
        }
    }
    private static int sampleColor(float[] x,int[] c,float u){
        if(x==null||c==null||x.length==0||c.length==0) return Color.WHITE;
        if(u<=x[0]) return c[0];
        int n=Math.min(x.length,c.length);
        for(int i=1;i<n;i++){if(u<=x[i]){float d=x[i]-x[i-1];if(Math.abs(d)<1e-7f)return c[i];return lerp(c[i-1],c[i],(u-x[i-1])/d);}}
        return c[n-1];
    }
    private static int sampleAlpha(float[] x,int[] a,float u){
        if(x==null||a==null||x.length==0||a.length==0)return 255;
        if(u<=x[0])return a[0];
        int n=Math.min(x.length,a.length);
        for(int i=1;i<n;i++){if(u<=x[i]){float d=x[i]-x[i-1];if(Math.abs(d)<1e-7f)return a[i];float f=(u-x[i-1])/d;return Math.max(0,Math.min(255,(int)(a[i-1]+(a[i]-a[i-1])*f)));}}
        return a[n-1];
    }
}
