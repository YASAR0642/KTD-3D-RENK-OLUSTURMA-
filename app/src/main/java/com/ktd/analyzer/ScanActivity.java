package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;
import android.view.View;

import com.ktd.analyzer.bluetooth.BluetoothClient;
import com.ktd.analyzer.data.CsvUtil;
import com.ktd.analyzer.data.PreviewUtil;
import com.ktd.analyzer.data.ReferenceModel;
import com.ktd.analyzer.data.ScanData;
import com.ktd.analyzer.data.ScanSessionStore;
import com.ktd.analyzer.data.NeutralExperimentalDepth;
import com.ktd.analyzer.data.MagneticDepthEstimator;
import com.ktd.analyzer.data.SensorFrame;
import com.ktd.analyzer.view.ColorMaps;
import com.ktd.analyzer.view.PaletteStripView;
import com.ktd.analyzer.view.ScanGridContourView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v3.4.2 çekim akışı:
 * - Çekim ekranına girildiğinde tarama otomatik hazırdır.
 * - İlk YENİ X/Y/Z/C paketi yalnız ortam referansıdır ve gride yazılmaz.
 * - İkinci yeni paket gridin 1. örneğidir.
 * - DEĞER ham sensörü gösterir; DERİNLİK ancak açık, türetilmemiş C kanalı varsa çalışır.
 */
public final class ScanActivity extends Activity implements BluetoothClient.Listener {
    private BluetoothClient bt;
    // Same operation as the screen's ORNEK AL button, coordinated on the Android side.
    private final BluetoothClient.HardwareSampleListener hardwareSampleListener=()->runOnUiThread(()->requestSample());
    private final ReferenceModel ref=new ReferenceModel();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private VoiceGuide voice;
    private ScanData data;
    private ScanGridContourView gridView;

    private boolean running=true,awaitZero=true,manualSampleRequested=false,groundModeArmed=false;
    private boolean importedPreview=false;
    private boolean showValue=false,showDepth=false;
    private SensorFrame lastFrame,replayToIgnore;
    private long lastCapture=0,linePauseUntil=0,pendingRequestedAtMs=0;
    private float pendingXcm=Float.NaN,pendingYcm=Float.NaN;
    private int index=0,palette;
    private Button connectionBtn,valueBtn,depthBtn;
    private TextView depthHint;
    private TextView sensProHint;
    private MagneticDepthEstimator.Result gridDepthResult;
    private int analysisGeneration=0;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        bt=new BluetoothClient(this);
        voice=new VoiceGuide(this);
        voice.setEnabled(Prefs.voiceGuide(this));
        palette=Prefs.palette(this);
        if(getIntent().getBooleanExtra("open_imported_2d",false) && ScanSessionStore.current!=null){
            data=ScanSessionStore.current;
            importedPreview=true;
            running=false;
            index=data.rows*data.cols;
        }else{
            newData();
        }
        build();
        if(importedPreview){
            gridView.setActiveCell(-1,-1);
            Toast.makeText(this,"CSV 2D görünümde açıldı · 3D / 4D ile aynı veriyi inceleyin",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onStart(){
        super.onStart();
        // Global Bluetooth istemcisi önceki ekranın son paketini tekrar yayınlayabilir.
        // O paket yeni taramanın referansı sayılmaz; ilk GERÇEK yeni paket beklenir.
        replayToIgnore=BluetoothClient.lastFrame();
        bt.activate();
        if(!importedPreview)BluetoothClient.addHardwareSampleListener(hardwareSampleListener);
        updateConnectionButton();
        if(!importedPreview)ensureGroundMode();
    }
    @Override protected void onResume(){
        super.onResume();
        if(data!=null)data.depthCalibrationFactor=1f;
        if(gridView!=null)gridView.invalidate();
    }
    @Override protected void onStop(){BluetoothClient.removeHardwareSampleListener(hardwareSampleListener);bt.release();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(voice!=null)voice.shutdown();super.onDestroy();}

    private void newData(){
        data=new ScanData(Prefs.rows(this),Prefs.cols(this));
        data.depthCalibrationFactor=1f;
        data.stepCm=Prefs.stepCm(this);
        data.startCorner=Prefs.startCorner(this);
        data.serpentine=Prefs.serpentine(this);
        index=0;running=true;ref.clear();lastFrame=null;awaitZero=true;manualSampleRequested=false;groundModeArmed=false;linePauseUntil=0;lastCapture=0;pendingRequestedAtMs=0;pendingXcm=Float.NaN;pendingYcm=Float.NaN;
        analysisGeneration++;gridDepthResult=null;
        ScanSessionStore.current=data;
    }

    private boolean smoothMap=true;

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(7,16,24));root.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,8));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackgroundColor(Color.rgb(9,21,29));
        top.addView(Ui.title(this,"KTD 3D ANALYZER"),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        Button back=Ui.button(this,"‹",false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,40)));root.addView(top);

        // Üst bölüm sade: bağlantı + iki görüntü anahtarı. Ham değer/ref/anomali yazıları kaldırıldı.
        LinearLayout toggles=new LinearLayout(this);toggles.setGravity(Gravity.CENTER);toggles.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,5));
        connectionBtn=Ui.button(this,"BLUETOOTH",false);connectionBtn.setOnClickListener(v->{if(BluetoothClient.isConnected())Toast.makeText(this,"Bağlı: "+BluetoothClient.connectedName(),Toast.LENGTH_SHORT).show();else bt.pickPaired(this);});
        valueBtn=Ui.button(this,"DEĞER",false);valueBtn.setOnClickListener(v->{showValue=!showValue;if(showValue)showDepth=false;applyOverlayMode();});
        depthBtn=Ui.button(this,"DERİNLİK",false);
        depthBtn.setOnClickListener(v->{
            showDepth=!showDepth;
            if(showDepth){showValue=false;}
            applyOverlayMode();
        });
        toggles.addView(connectionBtn,toggleLp());toggles.addView(valueBtn,toggleLp());toggles.addView(depthBtn,toggleLp());root.addView(toggles,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,48)));

        // Kullanıcı isteği: 2D kareleri dışında açıklama satırı yok.
        depthHint=null;
        sensProHint=null; // Nano ADC10 test paneli kullanıcı arayüzünden kaldırıldı.

        LinearLayout topActions=new LinearLayout(this);topActions.setGravity(Gravity.CENTER_VERTICAL);topActions.setPadding(Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,5));
        addTopAction(topActions,"Izgara",false,v->gridDialog());
        addTopAction(topActions,"Yeni Tarama",false,v->resetScan());
        addTopAction(topActions,"3D / 4D",false,v->{ScanSessionStore.current=data;startActivity(new Intent(this,AnalysisActivity.class));});
        addTopAction(topActions,"AKILLI RENK",true,v->SmartColorDialog.show(this,()->{gridView.setPalette(palette);}));
        root.addView(topActions,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,58)));
        // Her ekranda görünür: 10 noktalı kullanıcı paleti editörü.

        // 2D yalnız yumuşak harita: kare görünüm düğmesi ve 2D ANALİZ ET kaldırıldı.
        smoothMap=true;

        FrameLayout host=new FrameLayout(this);
        gridView=new ScanGridContourView(this);gridView.setSmoothMap(true);gridView.setData(data);gridView.setPalette(palette);gridView.setTraversal(Prefs.startCorner(this),Prefs.serpentine(this));setActiveCell();
        host.addView(gridView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(host,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));

        HorizontalScrollView ps=new HorizontalScrollView(this);ps.setHorizontalScrollBarEnabled(false);PaletteStripView palettes=new PaletteStripView(this);
        palettes.setListener(p->{palette=p;Prefs.setPalette(this,p);gridView.setPalette(p);});
        ps.addView(palettes,new HorizontalScrollView.LayoutParams(Ui.dp(this,PaletteStripView.entryCount()*112),Ui.dp(this,52)));
        // Akilli renk tusu ust satirda daima gorunur; palet tum genisligi kullanir.
        root.addView(ps,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));

        LinearLayout bottomActions=new LinearLayout(this);bottomActions.setGravity(Gravity.CENTER_VERTICAL);bottomActions.setPadding(Ui.dp(this,2),Ui.dp(this,4),Ui.dp(this,2),Ui.dp(this,5));
        addBottomAction(bottomActions,"Kaydet",false,v->saveNamed());
        addBottomAction(bottomActions,"ÖRNEK AL",true,v->requestSample());
        addBottomAction(bottomActions,"SIFIRLA",false,v->zeroNow());
        root.addView(bottomActions,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,58)));

        setContentView(root);Ui.applySafeInsets(this,root);applyOverlayMode();updateConnectionButton();
    }

    private LinearLayout.LayoutParams toggleLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,42),1);p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);return p;}
    private void addTopAction(LinearLayout bar,String s,boolean primary,android.view.View.OnClickListener l){Button b=Ui.button(this,s,primary);Ui.fitText(b,9,12,1);b.setOnClickListener(l);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f);bp.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);bar.addView(b,bp);}
    private void addBottomAction(LinearLayout bar,String s,boolean primary,android.view.View.OnClickListener l){Button b=Ui.button(this,s,primary);Ui.fitText(b,10,13,1);b.setOnClickListener(l);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f);bp.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);bar.addView(b,bp);}

    private void updateConnectionButton(){
        if(connectionBtn==null)return;
        boolean connected=BluetoothClient.isConnected();
        connectionBtn.setText(connected?"BAĞLI":"BLUETOOTH");
        connectionBtn.setTextColor(connected?Color.BLACK:Color.WHITE);
        connectionBtn.setBackground(Ui.bg(connected?Color.rgb(64,220,105):Color.rgb(23,42,54),10,this));
    }
    private void styleToggle(Button b,boolean on){if(b==null)return;b.setTextColor(on?Color.BLACK:Color.WHITE);b.setBackground(Ui.bg(on?Color.rgb(35,214,176):Color.rgb(23,42,54),10,this));}
    private void applyOverlayMode(){
        if(depthBtn!=null)depthBtn.setText("DERİNLİK");
        styleToggle(valueBtn,showValue);styleToggle(depthBtn,showDepth);
        if(gridView!=null){
            gridView.setDepthModel(showDepth?gridDepthResult:null);
            gridView.setOverlayMode(showValue?ScanGridContourView.OVERLAY_VALUE:showDepth?ScanGridContourView.OVERLAY_DEPTH:ScanGridContourView.OVERLAY_NONE);
        }
        if(depthHint!=null){
            depthHint.setVisibility(showDepth?View.VISIBLE:View.GONE);
            if(showDepth)refreshDepthHint();
        }
    }

    private void refreshDepthHint(){
        if(depthHint==null||data==null)return;
        if(data.fieldVerifiedCount()>0 || data.hasDepthData()){
            depthHint.setText("✓ Saha doğrulaması • C: cihazın bildirdiği değer • —: bilinmiyor");
            return;
        }
        if(gridDepthResult!=null&&gridDepthResult.hasEstimate()){
            depthHint.setText(String.format(Locale.forLanguageTag("tr-TR"),
                "≈ %.2f m koşullu model • sadece hedef yakınında • kesin değil",gridDepthResult.depthMeters));
        }else if(gridDepthResult!=null){
            depthHint.setText("Derinlik belirlenemedi: "+gridDepthResult.reason);
        }else if(data.count<25){
            depthHint.setText("Karelere ait ters sinyal ölçeği gösteriliyor • —: veri yetersiz");
        }else{
            depthHint.setText("2D ters sinyal cetveli açık; konumsal model kontrol ediliyor…");
        }
    }

    private void requestGridDepthAnalysis(){
        if(data==null)return;
        NeutralExperimentalDepth.apply(data);
        analysisGeneration++;
        if(data.hasDepthData()){
            gridDepthResult=null;applyOverlayMode();return;
        }
        if(data.rows<3||data.cols<3||data.count<9){
            gridDepthResult=null;applyOverlayMode();return;
        }
        final ScanData snapshot=copyForDepthModel(data);
        final ScanData expected=data;
        final int generation=analysisGeneration;
        gridDepthResult=null;applyOverlayMode();
        new Thread(()->{
            final MagneticDepthEstimator.Result result=MagneticDepthEstimator.analyze(snapshot);
            runOnUiThread(()->{
                if(isFinishing()||data!=expected||analysisGeneration!=generation||!showDepth)return;
                gridDepthResult=result;
                applyOverlayMode();
            });
        },"KTD-2D-depth-model").start();
    }

    /** Ölçüm akışı devam ederken arka plan çözümleyicisine değişmeyen kopya verir. */
    private static ScanData copyForDepthModel(ScanData src){
        ScanData out=new ScanData(src.rows,src.cols);
        out.stepCm=src.stepCm;out.reference=src.reference;
        for(int r=0;r<src.rows;r++)for(int c=0;c<src.cols;c++)if(src.filled[r][c]){
            out.filled[r][c]=true;out.count++;
            out.raw[r][c]=src.raw[r][c];out.delta[r][c]=src.delta[r][c];
            out.x[r][c]=src.x[r][c];out.y[r][c]=src.y[r][c];
        }
        return out;
    }

    private int[] cellForIndex(int idx){
        if(idx<0||idx>=data.rows*data.cols)return new int[]{-1,-1};
        int line=idx/data.rows,point=idx%data.rows;
        int col=Prefs.startCorner(this)==1?data.cols-1-line:line;
        boolean bottomUp=!Prefs.serpentine(this)||(line&1)==0;
        int row=bottomUp?data.rows-1-point:point;
        return new int[]{row,col};
    }
    private void setActiveCell(){if(gridView==null)return;int[] q=cellForIndex(index);gridView.setActiveCell(q[0],q[1]);}

    private boolean archiveOldScan(){
        if(data==null||data.count<=0)return true;
        try{
            CsvUtil.quickSave(this,data,"KTD_ONCEKI_TARAMA");
            return true;
        }catch(Exception e){
            Toast.makeText(this,"Eski tarama kaydedilemedi; silinmedi: "+e.getMessage(),Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Her yeni çekim tüm ham örnekleri ve eski REF'i ayırır. */
    private boolean beginFreshScan(){
        // Arşivleme hatasında ölçümler kullanıcının elinden kaybolmamalı.
        if(!archiveOldScan())return false;
        SensorFrame before=BluetoothClient.lastFrame();
        newData();
        if(importedPreview){
            importedPreview=false;
            BluetoothClient.addHardwareSampleListener(hardwareSampleListener);
        }
        replayToIgnore=before;
        if(gridView!=null){
            gridView.setData(data);
            gridView.setTraversal(Prefs.startCorner(this),Prefs.serpentine(this));
            setActiveCell();applyOverlayMode();
        }
        voice.setEnabled(Prefs.voiceGuide(this));
        return true;
    }

    private void resetScan(){
        if(!beginFreshScan())return;
        ensureGroundMode();
        Toast.makeText(this,"YENİ çekim • yeni ortam referansı bekleniyor",Toast.LENGTH_SHORT).show();
    }

    /**
     * ESP32'yi manuel zemin tarama moduna alır. Bu komut canlı akışı kapatır ve
     * cihazdan yalnızca BİR referans paketi ister. Sonraki paketler sadece ÖRNEK AL
     * komutuyla gelir.
     */
    private void ensureGroundMode(){
        if(groundModeArmed || !BluetoothClient.isConnected())return;
        if(BluetoothClient.isContinuousDataSource()){
            groundModeArmed=true; // No serial commands are required by a Nano/HC-05 stream.
            return;
        }
        if(bt.send("GROUND_START\n"))groundModeArmed=true;
    }

    /** SIFIRLA yeni bir çekimdir: eski referans ve hücreler KESİNLİKLE karışmaz. */
    private void zeroNow(){
        if(!beginFreshScan())return;
        if(BluetoothClient.isConnected()&&!BluetoothClient.isContinuousDataSource())bt.send("REF\n");
        Toast.makeText(this,"Yeni çekim • yeni ortam referansı bekleniyor",Toast.LENGTH_SHORT).show();
    }

    /**
     * Şimdiden telefon -> cihaz komut kanalını hazırlar. Sürekli akışlı cihazda sıradaki paket zorla örneklenir;
     * ileride cihaz SAMPLE komutuna tek paketle cevap verdiğinde aynı buton değişmeden çalışır.
     */
    private void requestSample(){
        if(importedPreview){
            Toast.makeText(this,"Bu ekran içe aktarılan CSV incelemesidir. Yeni ölçüm için YENİ TARAMA seçin.",Toast.LENGTH_LONG).show();
            return;
        }
        if(!BluetoothClient.isConnected()){Toast.makeText(this,"Bluetooth bağlantısı yok",Toast.LENGTH_SHORT).show();bt.pickPaired(this);return;}
        if(data.full()){Toast.makeText(this,"Tarama tamamlandı. Yeni Tarama seçin.",Toast.LENGTH_SHORT).show();return;}
        if(BluetoothClient.isContinuousDataSource() && (awaitZero||!ref.hasReference())){
            Toast.makeText(this,"Yeni çekimin ilk sensör referansı bekleniyor",Toast.LENGTH_SHORT).show();
            return;
        }
        if(manualSampleRequested){
            if(System.currentTimeMillis()-pendingRequestedAtMs<6000L){
                Toast.makeText(this,"Önceki örnek yanıtı bekleniyor",Toast.LENGTH_SHORT).show();return;
            }
            manualSampleRequested=false; // timed out: allow one new request
        }
        int[] q=cellForIndex(index);
        if(q[0]<0||q[1]<0)return;
        // Uygulamanın kanonik koordinatı: X daima ileri yön, Y yan sıra yönüdür.
        float xCoord=(data.rows-1-q[0])*data.stepCm/100f;
        float yCoord=(data.startCorner==1?(data.cols-1-q[1]):q[1])*data.stepCm/100f;
        manualSampleRequested=true;
        pendingRequestedAtMs=System.currentTimeMillis();pendingXcm=xCoord*100f;pendingYcm=yCoord*100f;
        String cmd=String.format(Locale.US,"SAMPLE X=%.0f Y=%.0f\n",xCoord*100f,yCoord*100f);
        if(BluetoothClient.isContinuousDataSource()){
            // HC-05 only transmits; capture the NEXT new sensor frame at this grid cell.
            // Nothing is transmitted to Arduino, regardless of the Bluetooth device name.
            return;
        }
        boolean sent=bt.send(cmd);
        if(!sent){manualSampleRequested=false;pendingRequestedAtMs=0;}
        if(!sent)Toast.makeText(this,"Örnek komutu gönderilemedi",Toast.LENGTH_SHORT).show();
    }

    @Override public void onFrame(SensorFrame f){runOnUiThread(()->consume(f));}
    private void consume(SensorFrame f){
        if(f==null)return;
        // onStart'taki global son-paket tekrarı referans veya örnek değildir.
        if(replayToIgnore!=null&&f==replayToIgnore){replayToIgnore=null;return;}
        replayToIgnore=null;

        if(f.referencePacket){
            float newRef=coerceRawSignal(f.signal());
            if(!Float.isFinite(newRef)||newRef<=1f||newRef>=1022f){
                Toast.makeText(this,"REF geçersiz: 1–1022 aralığında normal zemin gerekli",Toast.LENGTH_SHORT).show();
                return;
            }
            // REF:654 gibi açık paket her zaman YENİ tarama demektir.
            // Eski tarama ayrı kaydedilir; ilk REF hiçbir hücreye yazılmaz.
            if(!beginFreshScan())return;
            groundModeArmed=true;
            acceptReference(newRef);
            return;
        }

        // KTD protokolü: X/Y = konum, Z = 0..1023 ham sensör. C alanı ham kayıt olarak korunur.
        // C yoksa ham değerlerden yalnız DENEYSEL görsel ölçek oluşturulur. Ortam referansı ham sensörden alınır.
        float signal=f.signal();
        float rawSignal=coerceRawSignal(signal);
        if(Float.isNaN(rawSignal))return;
        SensorFrame frame=f.hasXYZC?new SensorFrame(f.x/100f,f.y/100f,rawSignal,f.c):new SensorFrame(rawSignal);
        lastFrame=frame;

        // İlk yeni sinyal ve her manuel SIFIRLA sonrası ilk yeni sinyal yalnız referanstır; gride yazılmaz.
        // Reference is only taken by the FIRST explicit SAMPLE button response (REF:512).
        if(awaitZero||!ref.hasReference()){
            if(BluetoothClient.isContinuousDataSource()){
                // Every NEW grid scan takes the first real arriving Nano frame as baseline.
                // Never reuse a packet replayed from the previous activity.
                acceptReference(rawSignal);
            }
            return;
        }

        if(!running||data.full())return;
        long now=System.currentTimeMillis();
        // Nano / HC-05: in AUTO mode a physical button may send only a single ADC frame,
        // without an ESP32 BUTTON_SAMPLE event or a SAMPLE response.  The first frame
        // was consumed as the baseline above.  Every subsequent *new* scalar frame
        // can fill the next grid cell without pressing the Android button.
        // Explicit STREAM mode keeps its original phone-button capture contract.
        final boolean nanoButtonFrame = BluetoothClient.isAutoProtocol()
                && !BluetoothClient.hasObservedReference()
                && !f.referencePacket;
        if(!manualSampleRequested && nanoButtonFrame){
            // Suppress duplicates/bounced frame bursts produced by one physical press.
            if(now-lastCapture<350L)return;
            manualSampleRequested=true;
            pendingRequestedAtMs=now;
            int[] next=cellForIndex(index);
            pendingXcm=(data.rows-1-next[0])*data.stepCm;
            pendingYcm=(data.startCorner==1?(data.cols-1-next[1]):next[1])*data.stepCm;
        }
        if(!manualSampleRequested)return;
        // A delayed/out-of-order sample must NEVER occupy the next grid cell.
        // Position is in cm in the device protocol; parsed SensorFrame coordinates are metres.
        if(now-pendingRequestedAtMs>6000L){manualSampleRequested=false;return;}
        boolean serialStream=BluetoothClient.isContinuousDataSource();
        if(!serialStream && (!f.hasXYZC || !Float.isFinite(f.x) || !Float.isFinite(f.y))){
            // Legacy scalar frames can be from NORMAL/GMR streaming, not a specific SAMPLE request.
            // Manual survey requires a coordinate-stamped response to prevent wrong-cell writes.
            Toast.makeText(this,"Koordinatsız veri reddedildi; ESP32 SAMPLE paketini kontrol edin",Toast.LENGTH_SHORT).show();
            manualSampleRequested=false;return;
        }
        if(!serialStream && (Math.abs(f.x-pendingXcm)>1.5f || Math.abs(f.y-pendingYcm)>1.5f)){
            // Keep the pending request: its matching reply may still be in transit.
            return;
        }
        if(data.filled[cellForIndex(index)[0]][cellForIndex(index)[1]])return;
        // Manual mode: button is the only trigger; do not skip user-requested samples
        // on automatic line/voice delays.
        // if(now<linePauseUntil)return;
        int interval=Prefs.voiceGuide(this)?Math.max(650,Prefs.intervalMs(this)):Math.max(250,Prefs.intervalMs(this));
        boolean force=manualSampleRequested;
        boolean firstGridSample=index==0;
        if(!force&&!firstGridSample&&now-lastCapture<interval)return;

        manualSampleRequested=false;pendingRequestedAtMs=0;lastCapture=now;
        int[] q=cellForIndex(index);int line=index/data.rows,point=index%data.rows;
        // For a data-only Nano, coordinates come from the selected grid cell,
        // not from optional/missing serial X/Y fields.
        SensorFrame stored=serialStream?
            new SensorFrame(Float.NaN,Float.NaN,rawSignal,f.channelC()):frame;
        data.put(q[0],q[1],stored,ref.visualDelta(rawSignal));
        // Explicit A1/A2 frames use the 30 cm gradient as a baseline-centered
        // display signal. Legacy Nano scalar frames remain unchanged.
        if(Float.isFinite(rawSignal)&&rawSignal>0f){data.c[q[0]][q[1]]=com.ktd.analyzer.data.InverseSignalIndex.fromRaw(rawSignal);data.depth[q[0]][q[1]]=Float.NaN;data.cIsDerivedFromRaw=true;data.derivedCDivisor=800;}
        NeutralExperimentalDepth.apply(data);
        index++;
        analysisGeneration++;gridDepthResult=null;
        applyOverlayMode();gridView.invalidate();voice.point(point+1);

        boolean lineEnd=point==data.rows-1;
        boolean finished=data.full();
        if(finished){
            running=false;gridView.setActiveCell(-1,-1);ScanSessionStore.current=data;
            if(showDepth)requestGridDepthAnalysis();
            voice.append("Çekim tamamlandı.");
            Toast.makeText(this,"Tarama tamamlandı • 3D / 4D Analiz hazır",Toast.LENGTH_LONG).show();
        }else{
            setActiveCell();
            if(lineEnd){
                long pause=Prefs.voiceGuide(this)?1850:1250;linePauseUntil=now+pause;
                voice.lineFinished(line+1,true);
                final int nextLine=line+2;
                handler.postDelayed(()->{if(running&&index<data.rows*data.cols)voice.startLine(nextLine);},Math.max(250,pause-150));
            }
        }
    }

    /** Bir taramanın REF'i yalnız ilk ölçümde alınır; her yeni çekimde resetlenir. */
    private void acceptReference(float raw){
        if(!Float.isFinite(raw)||raw<=1f||raw>=1022f){
            Toast.makeText(this,"Referans doyumda; normal zeminden yeniden ölçün",Toast.LENGTH_LONG).show();
            return;
        }
        ref.zero(raw);data.reference=raw;awaitZero=false;
        voice.setEnabled(Prefs.voiceGuide(this));voice.say("Yeni çekim ortam referansı alındı.");
        // 3D zemin tarama NEVER calls LIVE_START: first manual SAMPLE is only REF.
        // Subsequent SAMPLE commands receive exactly one new measurement.
        manualSampleRequested=false;pendingRequestedAtMs=0;
        lastCapture=0;
        applyOverlayMode();
    }

    private float coerceRawSignal(float signal){
        if(Float.isNaN(signal)||Float.isInfinite(signal)||signal<0f)return Float.NaN;
        // Eşik üzerinden otomatik 10/12-bit dönüşümü HATALIDIR:
        // 1023->1023, 1024->256 gibi keskin sıçrama sahte anomali üretir.
        // Gelen sensör sayısını koru; gerçek ADC çözünürlüğü firmware/protokolden belirlenmelidir.
        if(signal<=65535f)return signal;
        return Float.NaN;
    }

    @Override public void onState(String s){
        runOnUiThread(()->{
            updateConnectionButton();
            if(BluetoothClient.isConnected())ensureGroundMode();
            else groundModeArmed=false;
            if(s!=null&&s.toLowerCase(Locale.ROOT).contains("kesildi"))voice.say("Bluetooth bağlantısı kesildi.");
        });
    }

    private void saveNamed(){
        if(data.count==0){Toast.makeText(this,"Kaydedilecek tarama verisi yok",Toast.LENGTH_SHORT).show();return;}
        EditText name=new EditText(this);name.setSingleLine(true);name.setTextColor(Color.WHITE);name.setHintTextColor(Color.GRAY);name.setHint("Bölge / kayıt adı");name.setText("Tarama_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date()));int pad=Ui.dp(this,18);name.setPadding(pad,Ui.dp(this,8),pad,Ui.dp(this,8));
        new AlertDialog.Builder(this).setTitle("Kaydı adlandır").setView(name).setPositiveButton("Kaydet",(d,w)->{try{String base=CsvUtil.safeBaseName(name.getText().toString());data.name=base;File csv=CsvUtil.saveNamed(this,data,base);File png=new File(csv.getParentFile(),csv.getName().replaceFirst("\\.csv$",".png"));PreviewUtil.save3dPng(this,data,palette,png);Toast.makeText(this,"Kaydedildi: "+base,Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Kayıt hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();}}).setNegativeButton("İptal",null).show();
    }

    private void gridDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),0);
        // v3.5.2: fiziksel hücre boyutu sabit 30 x 30 cm. Kullanıcı yalnız X/Y
        // hücre sayısını ve tarama yönünü seçer.
        EditText ex=field("X ileri nokta sayısı",Prefs.rows(this)),ey=field("Y yan sıra sayısı",Prefs.cols(this));l.addView(ex);l.addView(ey);
        Spinner start=new Spinner(this);String[] starts={"Sol alt köşeden başla","Sağ alt köşeden başla"};ArrayAdapter<String> sa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,starts);start.setAdapter(sa);start.setSelection(Prefs.startCorner(this));l.addView(start);
        Spinner mode=new Spinner(this);String[] modes={"Zikzak tarama","Paralel tarama"};ArrayAdapter<String> ma=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes);mode.setAdapter(ma);mode.setSelection(Prefs.serpentine(this)?0:1);l.addView(mode);
        new AlertDialog.Builder(this).setTitle("Izgara").setView(l).setPositiveButton("Uygula",(d,w)->{try{int x=Integer.parseInt(ex.getText().toString()),y=Integer.parseInt(ey.getText().toString());Prefs.setGrid(this,x,y,30,start.getSelectedItemPosition(),mode.getSelectedItemPosition()==0);resetScan();}catch(Exception e){Toast.makeText(this,"Değerleri kontrol edin",Toast.LENGTH_SHORT).show();}}).setNegativeButton("İptal",null).show();
    }
    private EditText field(String hint,int v){EditText e=new EditText(this);e.setHint(hint);e.setText(String.valueOf(v));e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
}
