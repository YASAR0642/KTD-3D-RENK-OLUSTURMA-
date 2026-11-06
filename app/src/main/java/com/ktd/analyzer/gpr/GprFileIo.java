package com.ktd.analyzer.gpr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lossless KTD GPR CSV and basic SEG-Y reader (formats 1,2,3,5,8). */
public final class GprFileIo {
    private GprFileIo(){}
    public static final class Record {
        public final List<GprProtocol.Trace> traces;
        public final double dtNs,dxMeters;
        public Record(List<GprProtocol.Trace> t,double ns,double dx){traces=t;dtNs=ns;dxMeters=dx;}
    }
    public static void writeCsv(OutputStream out,List<GprProtocol.Trace> traces,double ns,double dx)throws IOException {
        BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
        writer.write("#KTD_GPR,NS_PER_SAMPLE,"+number(ns)+",DX_M,"+number(dx)+"\n");
        int n=traces.isEmpty()?0:traces.get(0).samples.length;
        writer.write("sequence");for(int k=0;k<n;k++)writer.write(",s"+k);
        writer.newLine();
        for(GprProtocol.Trace t:traces){
            if(t.samples.length!=n)continue;
            writer.write(Long.toString(t.sequence));
            for(short v:t.samples){writer.write(',');writer.write(Short.toString(v));}
            writer.newLine();
        }
        writer.flush();
    }
    private static String number(double d){return Double.isFinite(d)&&d>0?String.format(Locale.US,"%.9g",d):"NaN";}
    public static Record readCsv(InputStream in)throws IOException {
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        double ns=Double.NaN,dx=Double.NaN;
        List<GprProtocol.Trace> traces=new ArrayList<>();
        String line; int n=0;
        while((line=br.readLine())!=null){
            if(line.startsWith("#KTD_GPR,")){
                String[] tokens=line.split(",");
                for(int k=1;k+1<tokens.length;k+=2){
                    try{if("NS_PER_SAMPLE".equals(tokens[k]))ns=Double.parseDouble(tokens[k+1]);
                        else if("DX_M".equals(tokens[k]))dx=Double.parseDouble(tokens[k+1]);}
                    catch(NumberFormatException ignored){}
                }
                continue;
            }
            if(line.startsWith("sequence,"))continue;
            if(line.startsWith("#")||line.trim().isEmpty())continue;
            String[] parts=line.trim().split(",");
            if(parts.length<17||parts.length>GprProtocol.MAX_SAMPLES+1)continue;
            if(n==0)n=parts.length-1;
            if(parts.length!=n+1)continue;
            try{
                long seq=Long.parseLong(parts[0].trim());
                short[] vals=new short[n];
                for(int i=0;i<n;i++)vals[i]=Short.parseShort(parts[i+1].trim());
                traces.add(new GprProtocol.Trace(seq,vals));
            }catch(NumberFormatException bad){throw new IOException("Geçersiz radar izi veya örnek değeri",bad);}
            if(traces.size()>=100000)throw new IOException("Çok büyük CSV kaydı; kayıt bölünmeli");
        }
        if(traces.isEmpty())throw new IOException("Radar izi bulunamadı");
        return new Record(traces,ns,dx);
    }
    public static boolean looksLikeSegyHeader(byte[] h){
        if(h==null||h.length<3600)return false;
        // SEG-Y binary header: sample count at 3220, format at 3224.
        for(boolean le:new boolean[]{false,true}){
            int n=u16(h,3220,le),fmt=u16(h,3224,le);
            if(n>=1&&n<=32768&&(fmt==1||fmt==2||fmt==3||fmt==5||fmt==8))return true;
        }
        return false;
    }
    private static int u16(byte[] b,int index,boolean le){int x=b[index]&255,y=b[index+1]&255;return le?(x|(y<<8)):((x<<8)|y);}
    private static int i32(byte[] b,int idx,boolean le){
        int a=b[idx]&255,c=b[idx+1]&255,d=b[idx+2]&255,e=b[idx+3]&255;
        return le?(a|(c<<8)|(d<<16)|(e<<24)):((a<<24)|(c<<16)|(d<<8)|e);
    }
    private static void exactly(InputStream in,byte[] b)throws IOException { exactly(in,b,0); }
    private static void exactly(InputStream in,byte[] b,int off)throws IOException {
        int o=off;while(o<b.length){int r=in.read(b,o,b.length-o);if(r<0)throw new IOException("SEG-Y kaydı yarım kalmış");o+=r;}
    }
    private static short decoded(byte[] bytes,int offset,int format,boolean le){
        double v;
        if(format==3){int val=u16(bytes,offset,le);v=(short)val;}
        else if(format==8){v=bytes[offset];}
        else if(format==2){v=i32(bytes,offset,le);}
        else if(format==5){v=Float.intBitsToFloat(i32(bytes,offset,le));}
        else if(format==1){
            int v32=i32(bytes,offset,le);
            if(v32==0)return 0;
            int sign=(v32>>>31)!=0?-1:1;
            int exponent=((v32>>>24)&127)-64;
            long mantissa=v32&0xFFFFFFL;
            v=sign*(mantissa/(double)0x1000000)*Math.pow(16,exponent);
        }else return 0;
        if(!Double.isFinite(v))return 0;
        return (short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(v)));
    }
    public static Record readSegy(InputStream in)throws IOException {
        byte[] text=new byte[3200];exactly(in,text);
        String textualHeader=new String(text,java.nio.charset.StandardCharsets.ISO_8859_1).toUpperCase(Locale.US);
        // KTD PC GPR v8.5 exports SGY with a *placeholder* 1 us sample interval.
        // Never treat that placeholder as real propagation time or metre depth.
        // Prism's SEG-Y export is explicitly seismic-format-compatible, not calibrated GPR depth.
        boolean prismHeader=textualHeader.contains("PRISM FOR WINDOWS")
                || textualHeader.contains("INTENDED FOR SEISMIC (NOT GEORADAR)");
        boolean unverifiedTime=prismHeader || textualHeader.contains("REAL ACQUISITION DT UNVERIFIED")
                || textualHeader.contains("SAMPLE INTERVAL HEADER=1 US PLACEHOLDER");
        byte[] binary=new byte[400];exactly(in,binary);
        int interval=u16(binary,16,false),n=u16(binary,20,false),format=u16(binary,24,false);
        boolean le=false;
        if(n<16||n>GprProtocol.MAX_SAMPLES||!(format==1||format==2||format==3||format==5||format==8)){
            n=u16(binary,20,true);format=u16(binary,24,true);interval=u16(binary,16,true);le=true;
        }
        if(n<16||n>GprProtocol.MAX_SAMPLES)throw new IOException("Desteklenmeyen SEG-Y örnek sayısı");
        int bytesPerSample=(format==3?2:format==8?1:4);
        if(!(format==1||format==2||format==3||format==5||format==8))throw new IOException("Desteklenmeyen SEG-Y format kodu: "+format);
        List<GprProtocol.Trace> results=new ArrayList<>();
        for(int i=0;i<100000;i++){
            byte[] hdr=new byte[240];
            int first=in.read();if(first==-1)break;
            hdr[0]=(byte)first;
            exactly(in,hdr,1);
            int samples=u16(hdr,114,le);
            if(samples<16||samples>GprProtocol.MAX_SAMPLES)samples=n;
            byte[] data=new byte[samples*bytesPerSample];exactly(in,data);
            short[] values=new short[samples];
            for(int k=0;k<samples;k++)values[k]=decoded(data,k*bytesPerSample,format,le);
            // SEG-Y sample interval is microseconds. Convert to ns.
            results.add(new GprProtocol.Trace(i+1,values));
        }
        if(results.isEmpty())throw new IOException("SEG-Y iz verisi bulunamadı");
        double ns=!unverifiedTime && interval>0?interval*1000.0:Double.NaN;
        // SEG-Y is a general seismic format; no RF ns or distance is inferred when missing.
        return new Record(results,ns,Double.NaN);
    }
}
