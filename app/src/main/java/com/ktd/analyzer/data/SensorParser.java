package com.ktd.analyzer.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** KTD X,Y,Z,C akışını okur. Derinlik ham sensör + ortam referansından uygulamada göreli hesaplanır. */
public final class SensorParser {
    private static final String N="([-+]?\\d+(?:[\\.,]\\d+)?)";
    private static final Pattern LABELED = Pattern.compile(
            "(?i).*?X\\s*[:=]\\s*"+N+".*?Y\\s*[:=]\\s*"+N+".*?Z\\s*[:=]\\s*"+N+".*?C\\s*[:=]\\s*"+N+".*");
    private static final Pattern ZC_LABELED = Pattern.compile(
            "(?i).*?Z\\s*[:=]\\s*"+N+".*?C\\s*[:=]\\s*"+N+".*");
    private static final Pattern A1_FIELD=Pattern.compile("(?i)(?:^|[^A-Z0-9])A1\\s*[:=]\\s*"+N);
    private static final Pattern A2_FIELD=Pattern.compile("(?i)(?:^|[^A-Z0-9])A2\\s*[:=]\\s*"+N);
    private static final Pattern ADC10_FIELD = Pattern.compile("(?i)\\bADC10\\s*[:=]\\s*(\\d{1,4})");
    private static final Pattern REF_VALUE = Pattern.compile(
            "(?i)^(?:REF|REFERENCE|REFERANS|ZERO|SIFIR)\\s*[:=]\\s*"+N+"\\s*$");
    private static final Pattern NUM = Pattern.compile("[-+]?\\d+(?:[\\.,]\\d+)?");
    // Name-agnostic Nano serial frames, e.g. "A0:512", "ADC=654", "SENSOR:623".
    private static final Pattern NANO_SCALAR=Pattern.compile(
        "(?i)^(?:A0|ADC|ADC10|RAW|DATA|VALUE|VAL|SENSOR|SIGNAL|Z|C|DEGER|OLCUM|NANO)\\s*[:=]\\s*"+N+"\\s*;?$");
    private static final Pattern NANO_CSV=Pattern.compile(
        "(?i)^(?:ADC|RAW|DATA|VALUE|NANO)\\s*[,;]\\s*"+N+"\\s*$");
    private SensorParser(){}

    public static SensorFrame parse(String line){
        if(line==null)return null;String s=line.trim();if(s.isEmpty())return null;
        // A1/A2 must be explicit: do NOT infer two channels from legacy scalar or X/Y/Z/C.
        Matcher ma1=A1_FIELD.matcher(s),ma2=A2_FIELD.matcher(s);
        if(ma1.find()&&ma2.find()){
            try{return SensorFrame.dualAdc(f(ma1.group(1)),f(ma2.group(1)));}
            catch(Exception ignored){return null;}
        }
        Matcher rm=REF_VALUE.matcher(s);
        if(rm.matches()){
            try{return SensorFrame.reference(f(rm.group(1)));}catch(Exception ignored){}
        }
        Matcher nano=NANO_SCALAR.matcher(s);
        if(nano.matches()){
            try{return new SensorFrame(f(nano.group(1)));}catch(Exception ignored){}
        }
        nano=NANO_CSV.matcher(s);
        if(nano.matches()){
            try{return new SensorFrame(f(nano.group(1)));}catch(Exception ignored){}
        }
        Matcher lm=LABELED.matcher(s);if(lm.matches()){try{SensorFrame sf=new SensorFrame(f(lm.group(1)),f(lm.group(2)),f(lm.group(3)),f(lm.group(4)));setLegacy(sf,s);return sf;}catch(Exception ignored){}}
        // Cihaz X/Y göndermiyorsa yalnız Z(sensor),C(yardımcı kanal) paketi de kabul edilir.
        Matcher zc=ZC_LABELED.matcher(s);if(zc.matches()){try{SensorFrame sf=new SensorFrame(Float.NaN,Float.NaN,f(zc.group(1)),f(zc.group(2)));setLegacy(sf,s);return sf;}catch(Exception ignored){}}

        // Standart cihaz akışı: X,Y,Z,C -> virgülü alan ayırıcı kabul et.
        if(count(s,',')>=3 && s.indexOf(';')<0 && s.indexOf('\t')<0){
            String[] a=s.split(",");if(a.length>=4){try{return new SensorFrame(f(a[0]),f(a[1]),f(a[2]),f(a[3]));}catch(Exception ignored){}}
        }
        // Sadece Z,C gönderen kısa paket: ör. 574,1.20. Sensör kanalı tam sayı olduğundan
        // tek virgüllü bu biçimi decimal-comma yerine Z/C çifti olarak güvenle yorumlarız.
        if(count(s,',')==1 && s.indexOf(';')<0 && s.indexOf('\t')<0){
            String[] a=s.split(",",-1);if(a.length==2){try{float z=f(a[0]),c=f(a[1]);if(z>=0f&&z<=1023f)return new SensorFrame(Float.NaN,Float.NaN,z,c);}catch(Exception ignored){}}
        }
        // Avrupa ondalık virgülü için tavsiye edilen format: X;Y;Z;C
        if(s.indexOf(';')>=0||s.indexOf('\t')>=0){
            String[] a=s.split("[;\\t]");if(a.length>=4){try{return new SensorFrame(f(a[0]),f(a[1]),f(a[2]),f(a[3]));}catch(Exception ignored){}}
        }

        Matcher m=NUM.matcher(s);List<Float>a=new ArrayList<>();while(m.find()){try{a.add(f(m.group()));}catch(Exception ignored){}}
        if(a.size()>=4)return new SensorFrame(a.get(0),a.get(1),a.get(2),a.get(3));
        // KTD cihazı yalnız Z(sensor),C(yardımcı) yolladığında X/Y uygulamanın gridinden üretilir.
        if(a.size()==2)return new SensorFrame(Float.NaN,Float.NaN,a.get(0),a.get(1));
        if(a.size()==3){float x=a.get(0),y=a.get(1),z=a.get(2);return new SensorFrame(x,y,z,Float.NaN);}
        if(a.size()==1)return new SensorFrame(a.get(0));return null;
    }
    private static void setLegacy(SensorFrame f,String s){
        Matcher m=ADC10_FIELD.matcher(s);
        if(m.find()){try{int v=Integer.parseInt(m.group(1));if(v>=0&&v<=1023)f.legacyAdc10=v;}catch(Exception ignored){}}
    }
    public static String format(SensorFrame f){if(f==null)return"--";if(f.hasXYZC)return String.format(Locale.US,"X %.2f   Y %.2f   Z %.0f   C %.2f",f.x,f.y,f.z,f.c);return String.format(Locale.US,"C %.2f",f.c);}
    private static int count(String s,char c){int n=0;for(int i=0;i<s.length();i++)if(s.charAt(i)==c)n++;return n;}
    private static float f(String s){return Float.parseFloat(s.trim().replace(',','.'));}
}
