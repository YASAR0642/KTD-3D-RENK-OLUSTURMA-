package com.ktd.analyzer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
/** Optional performance counters; does not change sensor data or visualization. */
public final class PerfMonitor {
    private final AtomicLong arrivals=new AtomicLong(), processed=new AtomicLong(), draws=new AtomicLong(), drawNs=new AtomicLong(), processNs=new AtomicLong();
    private final AtomicLong queued=new AtomicLong(), queueNs=new AtomicLong(), dropped=new AtomicLong();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView label;
    private volatile boolean active=false;
    private long lastAt,lastArrivals,lastProcessed,lastDraws,lastDrawNs,lastProcessNs,lastQueued,lastQueueNs,lastDropped;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!active||label==null)return;
        long now=SystemClock.elapsedRealtime(), a=arrivals.get(), b=processed.get(), d=draws.get(), dr=drawNs.get(), pr=processNs.get();
        long q=queued.get(), qns=queueNs.get(), lost=dropped.get();
        double sec=Math.max(.001,(now-lastAt)/1000.0);
        double input=(a-lastArrivals)/sec,output=(b-lastProcessed)/sec,fps=(d-lastDraws)/sec;
        double drawMs=(d==lastDraws)?0:(dr-lastDrawNs)/1e6/(d-lastDraws);
        double processMs=(b==lastProcessed)?0:(pr-lastProcessNs)/1e6/(b-lastProcessed);
        double queueMs=(q==lastQueued)?0:(qns-lastQueueNs)/1e6/(q-lastQueued);
        Runtime rt=Runtime.getRuntime();long mem=(rt.totalMemory()-rt.freeMemory())/(1024*1024);
        label.setText(String.format(Locale.US,"TEST  GELEN %.1f/sn  İŞLENEN %.1f/sn  ÇİZİM %.1f/sn  KARE %.2f ms  İŞLEM %.2f ms/ölçüm  KUYRUK %.1f ms  DÜŞEN %d  RAM %d MB",input,output,fps,drawMs,processMs,queueMs,lost-lastDropped,mem));
        lastAt=now;lastArrivals=a;lastProcessed=b;lastDraws=d;lastDrawNs=dr;lastProcessNs=pr;lastQueued=q;lastQueueNs=qns;lastDropped=lost;
        handler.postDelayed(this,1000);
    }};
    public void received(){if(active)arrivals.incrementAndGet();}
    public void queueWaitNs(long ns){if(active&&ns>=0){queued.incrementAndGet();queueNs.addAndGet(ns);}}
    public void dropped(){if(active)dropped.incrementAndGet();}
    public void recordBatch(long elapsedNs,int count){if(active&&count>0){processed.addAndGet(count);processNs.addAndGet(elapsedNs);}}
    public long processBegin(){return active?System.nanoTime():0;}
    public void processEnd(long start){if(active&&start!=0){processed.incrementAndGet();processNs.addAndGet(System.nanoTime()-start);}}
    public long drawBegin(){return active?System.nanoTime():0;}
    public void drawEnd(long start){if(active&&start!=0){draws.incrementAndGet();drawNs.addAndGet(System.nanoTime()-start);}}
    public boolean isActive(){return active;}
    public void toggle(TextView text){if(active){stop();return;}label=text;active=true;arrivals.set(0);processed.set(0);draws.set(0);drawNs.set(0);processNs.set(0);queued.set(0);queueNs.set(0);dropped.set(0);
        lastAt=SystemClock.elapsedRealtime();lastArrivals=lastProcessed=lastDraws=lastDrawNs=lastProcessNs=lastQueued=lastQueueNs=lastDropped=0;
        label.setVisibility(View.VISIBLE);label.setText("TEST: Ölçüm başlıyor...");handler.postDelayed(tick,1000);
    }
    public void stop(){active=false;handler.removeCallbacks(tick);if(label!=null)label.setVisibility(View.GONE);}
}
