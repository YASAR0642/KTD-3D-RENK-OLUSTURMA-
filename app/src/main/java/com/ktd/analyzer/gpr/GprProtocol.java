package com.ktd.analyzer.gpr;

/** KTD PC GPR v8.5 uyumlu Bluetooth ASCII iz protokolü. Android bağımsızdır. */
public final class GprProtocol {
    private GprProtocol() { }
    public static final int MAX_SAMPLES = 2048;

    public static final class Trace {
        public final long sequence;
        public final short[] samples;
        public Trace(long sequence, short[] samples) {
            this.sequence = sequence;
            this.samples = samples;
        }
    }
    public static final class Meta {
        public final double nsPerSample;
        public final double dxMeters;
        public final String source;
        Meta(double dt, double dx, String source) {
            this.nsPerSample = dt;
            this.dxMeters = dx;
            this.source = source;
        }
        public boolean isDemo() { return "DEMO".equalsIgnoreCase(source); }
        public boolean hasPhysicalTime() { return Double.isFinite(nsPerSample) && nsPerSample > 0.0 && !isDemo(); }
    }
    public static final class Message {
        public final Trace trace;
        public final Meta meta;
        public final String status;
        private Message(Trace t, Meta m, String s) { trace=t; meta=m; status=s; }
        public static Message trace(Trace t){return new Message(t,null,null);}
        public static Message meta(Meta m){return new Message(null,m,null);}
        public static Message status(String s){return new Message(null,null,s);}
    }

    public static Message parse(String line) {
        if(line==null || line.length()>20000) return null;
        String s=line.trim();
        if(!(s.startsWith("KTDGPR,") || s.startsWith("KTD1,TRACE,"))) return null;
        String[] a=s.split(",",-1);
        if(a.length<3)return null;
        if("TRACE".equalsIgnoreCase(a[1])) {
            if(a.length<5)return null;
            try {
                long seq=Long.parseLong(a[2].trim());
                int n=Integer.parseInt(a[3].trim());
                if(seq<0||n<16||n>MAX_SAMPLES||a.length!=n+4)return null;
                short[] data=new short[n];
                for(int i=0;i<n;i++) {
                    int v=Integer.parseInt(a[i+4].trim());
                    if(v<Short.MIN_VALUE||v>Short.MAX_VALUE)return null;
                    data[i]=(short)v;
                }
                return Message.trace(new Trace(seq,data));
            }catch(NumberFormatException bad){return null;}
        }
        if("META".equalsIgnoreCase(a[1])) {
            // KTDGPR,META,NS_PER_SAMPLE,0.5,DX_M,0.05,SOURCE,FRONTEND
            if(a.length<8 || ((a.length-2)%2)!=0)return null;
            double dt=Double.NaN, dx=Double.NaN; String source="UNKNOWN";
            try {
                for(int i=2;i+1<a.length;i+=2){
                    String key=a[i].trim().toUpperCase(java.util.Locale.US);
                    if("NS_PER_SAMPLE".equals(key))dt=Double.parseDouble(a[i+1].trim());
                    if("DX_M".equals(key))dx=Double.parseDouble(a[i+1].trim());
                    if("SOURCE".equals(key))source=a[i+1].trim();
                }
            }catch(NumberFormatException bad){return null;}
            if(!(dt>0 && dt<=100000) || !Double.isFinite(dt))dt=Double.NaN;
            if(!(dx>0 && dx<=100) || !Double.isFinite(dx))dx=Double.NaN;
            return Message.meta(new Meta(dt,dx,source));
        }
        if("STATUS".equalsIgnoreCase(a[1]) || "ADC".equalsIgnoreCase(a[1])) {
            return Message.status(s.substring(s.indexOf(',',s.indexOf(',')+1)+1));
        }
        return null;
    }
    /** Radarın çift yönlü uçuş süresinden metre hesabı; ns örnek zamanı cihazdan gelmelidir. */
    public static double depthMeters(int sampleIndex, double nsPerSample, double dielectric, double timeZeroNs) {
        if(sampleIndex<0 || !Double.isFinite(nsPerSample) || nsPerSample<=0 ||
                !Double.isFinite(dielectric) || dielectric<1 || !Double.isFinite(timeZeroNs))return Double.NaN;
        double t=Math.max(0, sampleIndex*nsPerSample-timeZeroNs);
        return 0.299792458*t/(2.0*Math.sqrt(dielectric));
    }
}
