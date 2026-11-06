package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ktd.analyzer.bluetooth.BluetoothClient;
import com.ktd.analyzer.data.SensorFrame;
import com.ktd.analyzer.gpr.GprFileIo;
import com.ktd.analyzer.gpr.GprProtocol;
import com.ktd.analyzer.view.GprRadargramView;
import com.ktd.analyzer.view.GprTracePanelsView;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Landscape GPR console. Bluetooth GPR traces are never synthesized from gradiometer ADC. */
public final class LiveGprActivity extends Activity implements BluetoothClient.Listener,BluetoothClient.GprLineListener {
    private static final int OPEN_FILE=1103, SAVE_FILE=1104;
    private static final int NAVY=Color.rgb(11,27,39),TEAL=Color.rgb(31,204,182);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Object guard=new Object();
    private final ArrayDeque<GprProtocol.Message> queue=new ArrayDeque<>();
    private boolean flushScheduled=false, running=false, wasDemo=false, sound=true, hyperbola=true, auto=true;
    private boolean wanted=false;
    private BluetoothClient bluetooth;
    private GprRadargramView radar;
    private GprTracePanelsView panels;
    private TextView connection, gainLabel, footer;
    private Button startButton,pauseButton,restartButton,speedButton,filterButton,timeButton,antennaButton,colorButton,soundButton,hyperbolaButton,autoButton,voiceButton;
    private float gain=1f;
    private int antennaMhz=300,filter=0,timeIndex=0,soundTone=0;
    private long count=0,lastAudio=0;
    private final ArrayList<GprProtocol.Trace> loadedTraces=new ArrayList<>();
    private boolean fileMode=false,playingFile=false;
    private int replayPosition=0, replaySpeed=1;
    private double loadedDxMeters=Double.NaN;
    private int replayPreviewColumns=180;
    private int selectedPalette=1;
    private static final int REPLAY_FRAME_MS=29; // 25% faster playback: 40/1.25
    // Bluetooth input is drained in short batches; display memory stays bounded.
    private static final int MAX_PENDING_MESSAGES=4096;
    private long droppedGprMessages=0;
    private final Runnable replayTick=new Runnable(){@Override public void run(){
        if(!fileMode||!playingFile)return;
        int batch=replaySpeed;
        while(batch-->0&&replayPosition<loadedTraces.size()){
            GprProtocol.Trace trace=loadedTraces.get(replayPosition++);
            // Same horizontal sampling grid as full-record preview: no stretched hyperbolas.
            final int total=loadedTraces.size();
            final int columns=Math.min(180,total);
            final int playedIndex=replayPosition-1;
            // Preview chooses floor(k*total/columns); replay chooses the identical indices.
            final int column=(int)(((long)playedIndex*columns+total-1)/total);
            if(column<columns && (int)((long)column*total/columns)==playedIndex) radar.add(trace);
            count++;
            if(sound)alertSignal(trace);
        }
        panels.invalidate();updateFooter();
        if(replayPosition>=loadedTraces.size()){
            playingFile=false;refreshReplayButtons();
        }else ui.postDelayed(this,REPLAY_FRAME_MS);
    }};
    private double eps=9.0, timeOverrideNs=Double.NaN, lastDeviceNs=Double.NaN;
    private ToneGenerator tone;
    private final double[] timeOptions={Double.NaN,30,60,100,150,200,300,500};
    private final String[] filters={"FİLTRE YOK","DC DÜZELT","ZEMİN FİLTRE","KENAR VURGU"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().setStatusBarColor(Color.rgb(2,14,24));
        getWindow().setNavigationBarColor(Color.rgb(2,14,24));
        bluetooth=new BluetoothClient(this);
        try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,30);}catch(Exception ignored){}
        build();
        if(getIntent()!=null && getIntent().getData()!=null)loadUri(getIntent().getData());
    }
    @Override protected void onStart(){
        super.onStart();BluetoothClient.startGprLines(this);bluetooth.activate();refresh();
    }
    @Override protected void onStop(){
        wanted=false;
        if(running&&BluetoothClient.isConnected())bluetooth.send("GPR_STOP\n");
        running=false;BluetoothClient.stopGprLines(this);bluetooth.release();
        ui.removeCallbacks(flush);ui.removeCallbacks(replayTick);playingFile=false;synchronized(guard){queue.clear();flushScheduled=false;}
        super.onStop();
    }
    @Override protected void onDestroy(){if(tone!=null){tone.release();tone=null;}super.onDestroy();}
    private int dp(float v){return Ui.dp(this,v);}
    private TextView text(String value,int size){
        TextView tv=Ui.text(this,value,size,Color.WHITE);tv.setGravity(Gravity.CENTER);
        return tv;
    }
    private Button action(String label){
        Button b=Ui.button(this,label,false);b.setTextSize(10);
        b.setPadding(dp(3),0,dp(3),0);Ui.fitText(b,8,12,2);
        return b;
    }
    private void margin(View v,LinearLayout.LayoutParams params,int all){params.setMargins(dp(all),dp(all),dp(all),dp(all));v.setLayoutParams(params);}
    private LinearLayout.LayoutParams weighted(float value){return new LinearLayout.LayoutParams(0,-1,value);}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5,15,23));root.setPadding(dp(4),dp(3),dp(4),dp(3));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        Button back=action("‹");back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(38)));
        TextView title=text("KTD GPR ANALYZER",20);title.setGravity(Gravity.CENTER_VERTICAL);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        connection=text("COM",11);connection.setBackground(Ui.bg(NAVY,6,this));
        connection.setOnClickListener(v->bluetooth.pickPaired(this));
        head.addView(connection,new LinearLayout.LayoutParams(dp(110),dp(34)));
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(43)));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);
        radar=new GprRadargramView(this);
        radar.setPalette(GprRadargramView.PALETTE_DIVERGING);
        radar.setHyperbolaEnhance(hyperbola);
        radar.setEpsilon(eps);
        body.addView(radar,weighted(66));
        panels=new GprTracePanelsView(this,radar);
        LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);
        middle.setBackgroundColor(Color.rgb(7,16,25));
        LinearLayout lineGain=new LinearLayout(this);lineGain.setGravity(Gravity.CENTER_VERTICAL);
        gainLabel=text("KAZANÇ 1.0×",10);
        lineGain.addView(gainLabel,new LinearLayout.LayoutParams(dp(91),-1));
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(10);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int n,boolean fromUser){
                gain=n*.1f;radar.setGain(gain);gainLabel.setText(String.format(Locale.US,"KAZANÇ %.1f×",gain));
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        lineGain.addView(slider,new LinearLayout.LayoutParams(0,-1,1));
        middle.addView(lineGain,new LinearLayout.LayoutParams(-1,dp(43)));
        middle.addView(panels,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(middle,weighted(20));

        LinearLayout control=new LinearLayout(this);control.setPadding(dp(3),dp(3),dp(3),dp(3));
        control.setOrientation(LinearLayout.VERTICAL);
        Button com=action("COM");
        com.setOnClickListener(v->bluetooth.pickPaired(this));
        Button open=action("DOSYA AÇ");
        open.setOnClickListener(v->openFile());
        addRow(control,com,open);
        Button save=action("KAYDET");
        save.setOnClickListener(v->save());
        startButton=action("BAŞLAT");
        startButton.setOnClickListener(v->toggle());
        addRow(control,save,startButton);
        Button stop=action("DURDUR");
        stop.setOnClickListener(v->stopGprFromButton());
        timeButton=action("ZAMAN");
        timeButton.setOnClickListener(v->chooseTime());
        addRow(control,stop,timeButton);
        Button colors=action("RENK");
        colors.setOnClickListener(v->choosePalette());
        LinearLayout paletteRow=new LinearLayout(this);
        paletteRow.addView(colors,new LinearLayout.LayoutParams(-1,dp(36)));
        control.addView(paletteRow,new LinearLayout.LayoutParams(-1,dp(37)));
        antennaButton=action("ANTEN\n"+antennaMhz+" MHz");
        antennaButton.setOnClickListener(v->chooseAntenna());
        LinearLayout antennaRow=new LinearLayout(this);
        antennaRow.addView(antennaButton,new LinearLayout.LayoutParams(-1,dp(36)));
        control.addView(antennaRow,new LinearLayout.LayoutParams(-1,dp(37)));
        body.addView(control,weighted(17));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        footer=text("COM • 0 İZ",10);footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setTextColor(Color.rgb(132,191,215));
        // Bottom status details deliberately hidden; keep footer for internal updates.
        setContentView(root);Ui.applySafeInsets(this,root);
    }
    private void addRow(LinearLayout parent,Button one,Button two){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-1,1);p1.setMargins(dp(1),dp(1),dp(1),dp(1));
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,-1,1);p2.setMargins(dp(1),dp(1),dp(1),dp(1));
        row.addView(one,p1);row.addView(two,p2);
        parent.addView(row,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void refresh(){
        if(connection==null)return;
        boolean connected=BluetoothClient.isConnected();
        connection.setText(connected?"● COM":"○ COM");
        connection.setTextColor(connected?TEAL:Color.rgb(247,129,111));
        updateFooter();
    }
    private void updateFooter(){
        if(footer==null)return;
        String mode=fileMode?(playingFile?"DOSYA OYNATILIYOR":"DOSYA DURDU"):(radar.isDemo()?"DEMO":radar.hasTimeAxis()?"ns":"ÖRNEK");
        footer.setText(String.format(Locale.forLanguageTag("tr-TR"),"%s  •  %d İZ  •  %s  •  εr=%.0f",fileMode?(replayPosition+"/"+loadedTraces.size()):BluetoothClient.isConnected()?"COM BAĞLI":"COM KAPALI",count,mode,eps)+"  •  ANTEN "+antennaMhz+" MHz");
    }
    private void stopPlayback(){playingFile=false;ui.removeCallbacks(replayTick);}
    private void refreshReplayButtons(){
        if(startButton!=null)startButton.setText(fileMode?(playingFile?"DURAKLAT":"BAŞLAT"):(running?"ÇALIŞIYOR":"BAŞLAT"));
        if(pauseButton!=null)pauseButton.setText(playingFile?"Ⅱ\nDURAKLAT":"▶\nDEVAM");
    }
    private void startReplay(boolean fromBeginning){
        if(!fileMode||loadedTraces.isEmpty()){Toast.makeText(this,"Önce bir SGY/CSV dosyası açın",Toast.LENGTH_SHORT).show();return;}
        stopPlayback();
        if(fromBeginning||replayPosition==0||replayPosition>=loadedTraces.size()){
            replayPosition=0;count=0;radar.resetSession();panels.invalidate();
            radar.setImportedMeta(Double.isFinite(timeOverrideNs)?timeOverrideNs:lastDeviceNs,loadedDxMeters,false);
        }
        playingFile=true;refreshReplayButtons();ui.post(replayTick);updateFooter();
    }
    private void restartReplay(){
        if(!fileMode){Toast.makeText(this,"Yeniden oynatmak için dosya açın",Toast.LENGTH_SHORT).show();return;}
        startReplay(true);
    }
    private void pauseOrResume(){
        if(!fileMode){Toast.makeText(this,"Önce SGY veya CSV dosyası açın",Toast.LENGTH_SHORT).show();return;}
        if(playingFile){stopPlayback();refreshReplayButtons();updateFooter();}
        else startReplay(false);
    }
    private void showReplayInfo(){
        new AlertDialog.Builder(this).setTitle("GPR DOSYA / OYNATMA")
            .setMessage(fileMode?("İz: "+replayPosition+" / "+loadedTraces.size()+"\nOynatma: "+replaySpeed+"×\nOynatma gerçek zamanlı ölçüm değil, dosyadaki izlerin sırayla gösterimidir."):
              "SGY/SEGY veya CSV dosyası açıp tekrar oynatabilirsiniz. Canlı GPR için Bluetooth bağlantısı gerekir.")
            .setPositiveButton("TAMAM",null).show();
    }
    private void start(){
        if(!BluetoothClient.isConnected()){bluetooth.pickPaired(this);return;}
        stopPlayback();fileMode=false;loadedTraces.clear();
        radar.resetSession();count=0;panels.invalidate();
        wanted=true;
        if(bluetooth.send("GPR_START\n")){
            running=true;wasDemo=false;
            startButton.setText("ÇALIŞIYOR");
        }
        updateFooter();
    }
    /** Stops live acquisition or pauses a file replay and resets its cursor for restart. */
    private void stopGprFromButton(){
        if(fileMode){
            stopPlayback();
            replayPosition=0;
            count=0;
            showFullPreview();
            refreshReplayButtons();
            updateFooter();
            return;
        }
        if(running){bluetooth.send("GPR_STOP\n");}
        wanted=false;running=false;
        refreshReplayButtons();
        updateFooter();
    }
    private void toggle(){
        if(fileMode){if(playingFile){stopPlayback();refreshReplayButtons();updateFooter();}else startReplay(false);return;}
        if(running){wanted=false;running=false;bluetooth.send("GPR_STOP\n");startButton.setText("BAŞLAT");}
        else start();
    }
    private void extraMenu(){
        String[] items={"DEMO", "ZEMİN εr", "ZAMAN PENCERESİ", "NS/ÖRNEK KALİBRASYONU", "DOSYA AÇ"};
        new AlertDialog.Builder(this).setItems(items,(d,i)->{
            if(i==0)demo();else if(i==1)chooseSoil();else if(i==2)chooseTime();else if(i==3)calibrateSampleTime();else openFile();
        }).show();
    }
    private void demo(){
        if(!BluetoothClient.isConnected()){bluetooth.pickPaired(this);return;}
        radar.resetSession();count=0;
        if(bluetooth.send("GPR_DEMO_START\n")){
            wanted=true;running=true;wasDemo=true;startButton.setText("ÇALIŞIYOR");
        }
    }
    private void choosePalette(){
        String[] choices={"Siyah - Beyaz", "Renkli (Kırmızı / Mavi)", "Sepya"};
        new AlertDialog.Builder(this).setTitle("RADARGRAM RENKLERİ")
            .setSingleChoiceItems(choices,selectedPalette,(d,choice)->{
                selectedPalette=choice;radar.setPalette(choice);d.dismiss();
            }).setNegativeButton("İPTAL",null).show();
    }
    private void showFullPreview(){
        radar.resetSession();count=0;
        radar.setImportedMeta(Double.isFinite(timeOverrideNs)?timeOverrideNs:lastDeviceNs,loadedDxMeters,false);
        // Complete recording is represented in the fixed-width preview without dropping its timeline.
        int total=loadedTraces.size();
        final int columns=Math.min(180,total);
        for(int k=0;k<columns;k++){
            int idx=(int)((long)k*total/columns);
            radar.add(loadedTraces.get(idx));
        }
        panels.invalidate();updateFooter();
    }
    private void chooseTime(){
        String[] choices={"Tümü", "30 ns", "60 ns", "100 ns", "150 ns", "200 ns", "300 ns", "500 ns"};
        new AlertDialog.Builder(this).setTitle("ZAMAN").setSingleChoiceItems(choices,timeIndex,(d,selected)->{
            timeIndex=selected;radar.setTimeWindowNs(timeOptions[timeIndex]);
            timeButton.setText("ZAMAN\n"+(selected==0?"TÜMÜ":choices[selected]));
            panels.invalidate();d.dismiss();
        }).setNegativeButton("İPTAL",null).show();
    }
    private void calibrateSampleTime(){
        EditText field=new EditText(this);
        field.setHint("ns / örnek; boş = cihaz verisi");
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(Double.isFinite(timeOverrideNs))field.setText(Double.toString(timeOverrideNs));
        new AlertDialog.Builder(this).setTitle("NS/ÖRNEK KALİBRASYONU")
            .setView(field)
            .setPositiveButton("UYGULA",(d,which)->{
                String value=field.getText().toString().trim().replace(',','.');
                if(value.isEmpty()){
                    timeOverrideNs=Double.NaN;
                    radar.setImportedMeta(lastDeviceNs,radar.getStepMeters(),radar.isDemo());
                    updateFooter();return;
                }
                try{
                    double ns=Double.parseDouble(value);
                    if(!Double.isFinite(ns)||ns<=0||ns>10000)throw new NumberFormatException();
                    timeOverrideNs=ns;
                    radar.setImportedMeta(ns,radar.getStepMeters(),radar.isDemo());
                    updateFooter();
                }catch(NumberFormatException e){Toast.makeText(this,"Geçerli ns/örnek değeri gir",Toast.LENGTH_SHORT).show();}
            }).setNegativeButton("İPTAL",null).show();
    }
    private void chooseSoil(){
        double[] vals={1,4,6,9,12,16,25};
        String[] labels={"Hava  εr=1","Kuru zemin  εr=4","Zemin  εr=6","Zemin  εr=9","Nemli zemin  εr=12","Nemli zemin  εr=16","Yüksek dielektrik  εr=25"};
        new AlertDialog.Builder(this).setTitle("ZEMİN SEÇ").setItems(labels,(d,which)->{
            eps=vals[which];radar.setEpsilon(eps);updateFooter();
        }).show();
    }
    private void chooseAntenna(){
        String[] options={"100 MHz","200 MHz","300 MHz","400 MHz","500 MHz","800 MHz"};
        final int[] values={100,200,300,400,500,800};
        new AlertDialog.Builder(this).setTitle("ANTEN SEÇ").setItems(options,(d,n)->{
            antennaMhz=values[n];radar.setAntennaMhz(antennaMhz);if(antennaButton!=null)antennaButton.setText("ANTEN\n"+antennaMhz+" MHz");
            // It is a display preset; RF hardware cannot be retuned without a supported command.
        }).show();
    }
    private void openFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,OPEN_FILE);
    }
    private void loadUri(final Uri uri){
        new Thread(()->{
            try{
                String filename="";
                try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                    if(c!=null&&c.moveToFirst())filename=c.getString(0);
                }catch(Exception ignored){}
                boolean csv=filename!=null&&filename.toLowerCase(Locale.ROOT).endsWith(".csv");
                GprFileIo.Record rec;
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    if(input==null)throw new java.io.IOException("Dosya okunamadı");
                    rec=csv?GprFileIo.readCsv(input):GprFileIo.readSegy(input);
                }
                runOnUiThread(()->{
                    if(running)bluetooth.send("GPR_STOP\n");
                    running=false;wanted=false;startButton.setText("BAŞLAT");
                    stopPlayback();
                    loadedTraces.clear();loadedTraces.addAll(rec.traces);
                    fileMode=true;replayPosition=0;count=0;lastDeviceNs=rec.dtNs;loadedDxMeters=rec.dxMeters;
                    radar.resetSession();
                    radar.setImportedMeta(Double.isFinite(timeOverrideNs)?timeOverrideNs:rec.dtNs,rec.dxMeters,false);
                    // Opening a file must only preview the whole recording. PLAY begins acquisition-style replay.
                    showFullPreview();refreshReplayButtons();
                    Toast.makeText(this,"Dosya hazır: "+loadedTraces.size()+" iz. BAŞLAT ile oynat.",Toast.LENGTH_LONG).show();
                });
            }catch(Exception ex){runOnUiThread(()->Toast.makeText(this,"GPR dosyası açılamadı: "+ex.getMessage(),Toast.LENGTH_LONG).show());}
        },"ktd-gpr-import").start();
    }
    private void save(){
        if(radar.traceCount()==0 && (!fileMode||loadedTraces.isEmpty())){Toast.makeText(this,"İz bulunamadı",Toast.LENGTH_SHORT).show();return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/csv");
        String date=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE,"KTD_GPR_"+date+".csv");
        startActivityForResult(intent,SAVE_FILE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        if(request==OPEN_FILE){
            loadUri(uri);
        } else if(request==SAVE_FILE){
            final List<GprProtocol.Trace> current=fileMode?new ArrayList<>(loadedTraces):radar.snapshot();
            final double time=radar.getSampleIntervalNs(),dx=radar.getStepMeters();
            new Thread(()->{
                try(OutputStream o=getContentResolver().openOutputStream(uri)){
                    if(o==null)throw new java.io.IOException("Dosya açılamadı");
                    GprFileIo.writeCsv(o,current,time,dx);
                    runOnUiThread(()->Toast.makeText(this,"KAYDEDİLDİ",Toast.LENGTH_SHORT).show());
                }catch(Exception ex){runOnUiThread(()->Toast.makeText(this,"Kayıt hatası: "+ex.getMessage(),Toast.LENGTH_LONG).show());}
            },"ktd-gpr-save").start();
        }
    }
    @Override public void onState(String s){runOnUiThread(()->{
        refresh();if(!BluetoothClient.isConnected()){running=false;startButton.setText("BAŞLAT");}
    });}
    @Override public void onFrame(SensorFrame f){/* Magnetometer is never converted to radar traces. */}
    @Override public void onGprLine(String line){
        if(fileMode)return;
        GprProtocol.Message m=GprProtocol.parse(line);if(m==null)return;
        synchronized(guard){
            while(queue.size()>=MAX_PENDING_MESSAGES){
                queue.removeFirst();droppedGprMessages++;
            }
            queue.addLast(m);
            if(!flushScheduled){flushScheduled=true;ui.postDelayed(flush,30);}
        }
    }
    private final Runnable flush=new Runnable(){@Override public void run(){
        ArrayDeque<GprProtocol.Message> packet=new ArrayDeque<>();
        synchronized(guard){while(!queue.isEmpty())packet.addLast(queue.removeFirst());flushScheduled=false;}
        for(GprProtocol.Message m:packet){
            if(m.meta!=null){lastDeviceNs=m.meta.nsPerSample;radar.setMeta(m.meta);
                if(Double.isFinite(timeOverrideNs))radar.setImportedMeta(timeOverrideNs,m.meta.dxMeters,m.meta.isDemo());
                wasDemo=m.meta.isDemo();}
            if(m.trace!=null){radar.add(m.trace);count++;if(sound)alertSignal(m.trace);}
        }
        panels.invalidate();updateFooter();
    }};
    private void alertSignal(GprProtocol.Trace t){
        if(tone==null||t.samples.length<16)return;
        long now=SystemClock.elapsedRealtime();if(now-lastAudio<2400)return;
        long sum=0;int max=0;
        for(short v:t.samples){sum+=Math.abs((int)v);max=Math.max(max,Math.abs((int)v));}
        if(max>100&&max>3.8*(sum/(double)t.samples.length)){
            lastAudio=now;
            try{tone.startTone(soundTone==0?ToneGenerator.TONE_PROP_BEEP:soundTone==1?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,100);}catch(Exception ignored){}
        }
    }
}
