package com.ktd.analyzer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ktd.analyzer.bluetooth.BluetoothClient;
import com.ktd.analyzer.data.ReferenceModel;
import com.ktd.analyzer.data.ReferenceInverseDepth;
import com.ktd.analyzer.data.SensorFrame;
import com.ktd.analyzer.view.ColorMaps;
import com.ktd.analyzer.view.PaletteStripView;
import com.ktd.analyzer.view.UltrasoundView;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * KTD 3D CANLI TARAMA
 * - ESP32: LIVE_START / LIVE_STOP komutlari.
 * - Nano + HC-05: hic komut gerektirmeyen surekli 10-bit veri akisi.
 * - Ekrana girildiğinde bağlantı varsa canlı akış otomatik başlar.
 * - İlk gerçek paket gizli ortam referansıdır; görüntü ikinci paketten başlar.
 */
public final class LiveScanActivity extends Activity implements BluetoothClient.Listener {
    private BluetoothClient bt;
    private final PerfMonitor perfMonitor=new PerfMonitor();
    private TextView performanceText;
    private final ReferenceModel ref = new ReferenceModel();
    private SensorFrame last, replayToIgnore;
    private TextView connectionBox, cls;
    private UltrasoundView ultrasound;
    private Button liveToggle;
    private boolean gprStyle=false;
    private Button normalMode,gprMode;
    private TextView depthInfo,gainLabel;
    private float liveGain=1.0f;
    private long referenceBeginMs=0L;
    private double referenceAccum=0;
    private int referenceCount=0;
    // These controls apply ONLY to the magnetic GPR-style visualization.
    private LinearLayout magneticFilters;
    private float broadAnomalyBoost=2f;
    private boolean mineralFilterEnabled=false;

    private boolean autoZeroDone = false;
    private boolean liveRunning = false;
    private boolean wantLive = false;

    /*
     * Bluetooth'tan gelen paketler kaybolmadan kuyruğa alınır.
     * UI yaklaşık her 8 ms'de boşaltılır; gerçek örnek hızı ESP32'nin RATE değeriyle
     * belirlenir (firmware varsayılanı 20 ms = 50 paket/saniye).
     */
    private static final long UI_FRAME_MS = 8L;
    private static final int MAX_PENDING = 4096;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object queueLock = new Object();
    private final ArrayDeque<SensorFrame> pending = new ArrayDeque<>();
    private final ArrayDeque<Long> pendingTimes = new ArrayDeque<>();
    private boolean flushScheduled = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        bt = new BluetoothClient(this);
        liveGain=1.0f; // NORMAL: fixed conservative display gain
        build();
    }

    @Override protected void onStart() {
        super.onStart();
        wantLive = false;
        liveRunning = false;
        autoZeroDone = false;
        ref.clear();
        replayToIgnore = BluetoothClient.lastFrame();
        bt.activate();
        refreshConnection();
        // Reconnecting BT must NEVER start NORMAL without user START.
        if(BluetoothClient.isConnected()&&!BluetoothClient.isContinuousDataSource()) bt.send("LIVE_STOP\n");
        updateLiveButton();
    }

    @Override protected void onStop() {
        perfMonitor.stop();
        if(BluetoothClient.isConnected()&&!BluetoothClient.isContinuousDataSource()) bt.send("LIVE_STOP\n");
        liveRunning = false;
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateLiveButton();
        bt.release();
        ui.removeCallbacks(flushRunnable);
        synchronized(queueLock){ pending.clear();pendingTimes.clear(); flushScheduled=false; }
        super.onStop();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,10));
        root.setBackgroundColor(Color.rgb(7,16,24));

        FrameLayout top = new FrameLayout(this);
        TextView title = Ui.title(this,"KTD 3D ANALYZER");
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,54));
        tlp.setMargins(Ui.dp(this,58),0,Ui.dp(this,58),0);
        top.addView(title,tlp);
        Button back = Ui.button(this,"‹",false); back.setOnClickListener(v->finish());
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,42),Gravity.END|Gravity.CENTER_VERTICAL);
        top.addView(back,blp);
        root.addView(top,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,56)));
        performanceText=Ui.text(this,"",10,Color.rgb(178,243,255));
        performanceText.setVisibility(android.view.View.GONE);

        connectionBox = Ui.text(this,"Bluetooth bağlı değil",13,Color.WHITE);
        connectionBox.setGravity(Gravity.CENTER);
        connectionBox.setTypeface(null,1);
        connectionBox.setSingleLine(true);
        connectionBox.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        connectionBox.setOnClickListener(v->{
            if(BluetoothClient.isConnected()) Toast.makeText(this,"Bağlı: "+BluetoothClient.connectedName(),Toast.LENGTH_SHORT).show();
            else bt.pickPaired(this);
        });
        root.addView(connectionBox,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,44)));

        cls = Ui.text(this,"VERİ BEKLENİYOR",14,Color.rgb(35,214,176));
        cls.setTypeface(null,1); cls.setGravity(Gravity.CENTER); Ui.fitText(cls,11,15,1);
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,38));
        clp.setMargins(0,Ui.dp(this,3),0,Ui.dp(this,3));
        root.addView(cls,clp);

        depthInfo=Ui.text(this,"— m",13,Color.rgb(225,192,109));
        depthInfo.setGravity(Gravity.CENTER);Ui.fitText(depthInfo,11,14,1);
        root.addView(depthInfo,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,38)));

        LinearLayout modes=new LinearLayout(this);
        normalMode=Ui.button(this,"NORMAL",true);
        gprMode=Ui.button(this,"GPR GÖRÜNÜM",false);
        normalMode.setOnClickListener(v->switchMode(false));
        gprMode.setOnClickListener(v->startActivity(new android.content.Intent(this,MagneticRadarActivity.class)));
        modes.addView(normalMode,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        modes.addView(gprMode,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        root.addView(modes);
        magneticFilters=new LinearLayout(this);
        magneticFilters.setOrientation(LinearLayout.VERTICAL);
        magneticFilters.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,3));
        LinearLayout boostRow=new LinearLayout(this);
        boostRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView boostLabel=Ui.text(this,"GENİŞ ANOMALİ 2.0×",11,Color.WHITE);
        SeekBar boostSlider=new SeekBar(this);
        boostSlider.setMax(8);
        boostSlider.setProgress(2); // 1.0 + 2 * 0.5 = 2.0x
        boostSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar v,int progress,boolean fromUser){
                broadAnomalyBoost=1f+progress*.5f;
                boostLabel.setText(String.format(java.util.Locale.US,"GENİŞ ANOMALİ %.1f×",broadAnomalyBoost));
                if(ultrasound!=null)ultrasound.setBroadAnomalyBoost(broadAnomalyBoost);
            }
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        });
        boostRow.addView(boostLabel,new LinearLayout.LayoutParams(Ui.dp(this,150),Ui.dp(this,33)));
        boostRow.addView(boostSlider,new LinearLayout.LayoutParams(0,Ui.dp(this,33),1));
        magneticFilters.addView(boostRow);
        Button mineralToggle=Ui.button(this,"MİNERAL FİLTRESİ: KAPALI",false);
        mineralToggle.setOnClickListener(v->{
            mineralFilterEnabled=!mineralFilterEnabled;
            mineralToggle.setText(mineralFilterEnabled?"MİNERAL FİLTRESİ: AÇIK":"MİNERAL FİLTRESİ: KAPALI");
            if(ultrasound!=null)ultrasound.setMineralFilterEnabled(mineralFilterEnabled);
        });
        magneticFilters.addView(mineralToggle,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,36)));
        magneticFilters.setVisibility(android.view.View.GONE);
        root.addView(magneticFilters);
        ultrasound = new UltrasoundView(this); ultrasound.setPalette(Prefs.palette(this));ultrasound.setGain(liveGain);
        ultrasound.setPerfMonitor(perfMonitor);
        root.addView(ultrasound,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));

        HorizontalScrollView ps = new HorizontalScrollView(this); ps.setHorizontalScrollBarEnabled(false);
        PaletteStripView palettes = new PaletteStripView(this); palettes.setListener(p->{Prefs.setPalette(this,p);ultrasound.setPalette(p);});
        ps.addView(palettes,new HorizontalScrollView.LayoutParams(Ui.dp(this, PaletteStripView.entryCount()*112),Ui.dp(this,52)));
        LinearLayout smartRow=new LinearLayout(this);smartRow.setGravity(Gravity.CENTER_VERTICAL);
        Button smartColors=Ui.button(this,"AKILLI\nRENK",false);Ui.fitText(smartColors,9,12,2);
        smartColors.setOnClickListener(v->SmartColorDialog.show(this,()->{ultrasound.setPalette(Prefs.palette(this));palettes.invalidate();}));
        smartRow.addView(smartColors,new LinearLayout.LayoutParams(Ui.dp(this,99),Ui.dp(this,52)));
        smartRow.addView(ps,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
        root.addView(smartRow,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));

        LinearLayout gainRow=new LinearLayout(this);gainRow.setGravity(Gravity.CENTER_VERTICAL);
        gainLabel=Ui.text(this,String.format(java.util.Locale.US,"Kazanç %.1fx",liveGain),12,Color.WHITE);Ui.fitText(gainLabel,10,12,1);
        SeekBar gainSeek=new SeekBar(this);gainSeek.setMax(28);gainSeek.setProgress(Math.round((liveGain-.2f)*10));
        gainSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar v,int progress,boolean user){
                liveGain=.2f+progress*.1f;ultrasound.setGain(liveGain);Prefs.setAnomalyGain(LiveScanActivity.this,liveGain);
                gainLabel.setText(String.format(java.util.Locale.US,"Kazanç %.1fx",liveGain));
            }
            public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}
        });
        gainRow.addView(gainLabel,new LinearLayout.LayoutParams(Ui.dp(this,110),Ui.dp(this,40)));
        gainRow.addView(gainSeek,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));
        // NORMAL mode gain control hidden and locked at 1.0x; no persisted user gain.
        gainRow.setVisibility(android.view.View.GONE);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER);
        liveToggle = Ui.button(this,"DURDUR",true);
        Button zero = Ui.button(this,"SIFIRLA",false);
        Button clear = Ui.button(this,"TEMİZLE",false);
        liveToggle.setOnClickListener(v->toggleLive());
        zero.setOnClickListener(v->manualZero());
        clear.setOnClickListener(v->ultrasound.clear());
        bar.addView(liveToggle,Ui.weight(1)); bar.addView(zero,Ui.weight(1)); bar.addView(clear,Ui.weight(1));
        root.addView(bar,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,56)));

        setContentView(root); Ui.applySafeInsets(this,root); refreshConnection(); updateLiveButton();
    }

    private void switchMode(boolean gpr){
        gprStyle=gpr; ultrasound.setGprStyle(gpr);
        if(magneticFilters!=null)magneticFilters.setVisibility(gpr?android.view.View.VISIBLE:android.view.View.GONE);
        normalMode.setText(gpr?"NORMAL":"● NORMAL");
        gprMode.setText(gpr?"● GPR GÖRÜNÜM":"GPR GÖRÜNÜM");
    }

    private void refreshConnection(){
        if(connectionBox==null)return;
        boolean ok=BluetoothClient.isConnected();
        connectionBox.setText(ok?"BAĞLI • "+BluetoothClient.connectedName():"BLUETOOTH BAĞLI DEĞİL");
        connectionBox.setTextColor(ok?Color.rgb(4,20,18):Color.WHITE);
        connectionBox.setBackground(Ui.bg(ok?Color.rgb(55,224,110):Color.rgb(185,48,48),12,this));
    }

    private void updateLiveButton(){
        if(liveToggle==null)return;
        liveToggle.setText(liveRunning?"DURDUR":"BAŞLAT");
        liveToggle.setTextColor(liveRunning?Color.BLACK:Color.WHITE);
        liveToggle.setBackground(Ui.bg(liveRunning?Color.rgb(35,214,176):Color.rgb(23,42,54),10,this));
    }

    private void startLiveIfPossible(){
        if(!wantLive || liveRunning || !BluetoothClient.isConnected())return;
        // Passive Arduino Nano / HC-05 already sends frames continuously.
        // The screen's START/STOP controls LOCAL recording for these devices.
        boolean started;
        if(BluetoothClient.isContinuousDataSource())started=true;
        else{
            bt.send("RATE=20\n");
            started=bt.send("LIVE_START\n");
        }
        if(started){
            liveRunning=true;
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateLiveButton();
        }
    }

    private void toggleLive(){
        if(!BluetoothClient.isConnected()){
            Toast.makeText(this,"Bluetooth bağlantısı yok",Toast.LENGTH_SHORT).show();
            bt.pickPaired(this); return;
        }
        if(liveRunning){
            wantLive=false;
            if(BluetoothClient.isContinuousDataSource()||bt.send("LIVE_STOP\n")){
                liveRunning=false;
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                updateLiveButton();
                Toast.makeText(this,"Canlı tarama durduruldu",Toast.LENGTH_SHORT).show();
            }
        }else{
            wantLive=true;
            autoZeroDone=false; ref.clear();
            referenceBeginMs=android.os.SystemClock.elapsedRealtime();
            referenceAccum=0; referenceCount=0;
            ultrasound.clear();
            cls.setText("ORTAM REFERANSI • 2 sn");
            startLiveIfPossible();
            Toast.makeText(this,"Canlı tarama başlatıldı",Toast.LENGTH_SHORT).show();
        }
    }

    /** SIFIRLA'dan sonra ilk YENİ paket ortam referansı olur; eski paketi kullanmaz. */
    private void manualZero() {
        autoZeroDone=false; ref.clear();
        referenceBeginMs=android.os.SystemClock.elapsedRealtime(); referenceAccum=0; referenceCount=0;
        if(depthInfo!=null)depthInfo.setText("— m");
        Toast.makeText(this,"Sıradaki yeni sinyal ortam referansı alınacak",Toast.LENGTH_SHORT).show();
    }

    @Override public void onFrame(SensorFrame f){
        perfMonitor.received();
        if(f==null || !liveRunning)return;
        if(replayToIgnore!=null && f==replayToIgnore){ replayToIgnore=null; return; }
        replayToIgnore=null;
        float signal=f.signal();
        if(Float.isNaN(signal)||Float.isInfinite(signal)||signal<0f||signal>1023f)return;
        float z=ReferenceModel.normalize(signal);
        SensorFrame nf=f.hasXYZC?new SensorFrame(f.x,f.y,z,f.c):new SensorFrame(z);

        synchronized(queueLock){
            while(pending.size()>=MAX_PENDING){pending.removeFirst();pendingTimes.removeFirst();perfMonitor.dropped();}
            pending.addLast(nf);pendingTimes.addLast(System.nanoTime());
            if(!flushScheduled){
                flushScheduled=true;
                ui.postDelayed(flushRunnable,UI_FRAME_MS);
            }
        }
    }

    private final Runnable flushRunnable=new Runnable(){
        @Override public void run(){
            long pStart=perfMonitor.processBegin();
            ArrayList<SensorFrame> batch=new ArrayList<>();
            synchronized(queueLock){
                while(!pending.isEmpty()){batch.add(pending.removeFirst());if(!pendingTimes.isEmpty())perfMonitor.queueWaitNs(System.nanoTime()-pendingTimes.removeFirst());}
                flushScheduled=false;
            }
            if(batch.isEmpty())return;

            ArrayList<SensorFrame> visualFrames=new ArrayList<>(batch.size());
            ArrayList<Float> visualDeltas=new ArrayList<>(batch.size());
            SensorFrame newest=null;

            for(SensorFrame nf:batch){
                if(nf==null)continue;
                float z=nf.signal();
                last=nf; newest=nf;
                if(!autoZeroDone){
                    referenceAccum+=z;referenceCount++;
                    if(android.os.SystemClock.elapsedRealtime()-referenceBeginMs<2000L)continue;
                    ref.zero((float)(referenceAccum/Math.max(1,referenceCount)));
                    autoZeroDone=true;
                    cls.setText("REFERANS TAMAM • CANLI");
                    if(depthInfo!=null)depthInfo.setText("— m");
                    continue; // last reference sample must not appear on chart
                }
                visualFrames.add(nf);
                visualDeltas.add(ref.visualDelta(z));
            }

            if(!visualFrames.isEmpty()){ultrasound.addBatch(visualFrames,visualDeltas);}

            if(newest!=null){
                String k=ref.classify(newest.signal()); cls.setText(k);
                ReferenceInverseDepth.Reading m=ReferenceInverseDepth.measure(newest.signal(),ref.reference(),Prefs.gradiometerDepthFactor(LiveScanActivity.this));
                if(depthInfo!=null){
                    String measured=m.anomaly&&Float.isFinite(m.meters)
                      ? String.format(java.util.Locale.US,"≈ %.2f m",m.meters)
                      : String.format(java.util.Locale.US,"— m");
                    depthInfo.setText(measured);
                }
                cls.setTextColor(k.startsWith("NEGATİF")?Color.rgb(72,155,255):k.startsWith("POZİTİF")?Color.rgb(255,82,48):Color.rgb(65,225,112));
            }

            synchronized(queueLock){
                if(!pending.isEmpty()&&!flushScheduled){
                    flushScheduled=true;
                    ui.postDelayed(this,UI_FRAME_MS);
                }
            }
            perfMonitor.recordBatch(System.nanoTime()-pStart,batch.size());
        }
    };

    @Override public void onState(String s){
        runOnUiThread(()->{
            refreshConnection();
            if(!BluetoothClient.isConnected()){
                liveRunning=false;
                wantLive=false;
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                updateLiveButton();
            } // Bluetooth reconnect never auto-starts a scan.
        });
    }
}
