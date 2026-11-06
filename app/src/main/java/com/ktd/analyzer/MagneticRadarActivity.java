package com.ktd.analyzer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import com.ktd.analyzer.bluetooth.BluetoothClient;
import com.ktd.analyzer.data.ReferenceModel;
import com.ktd.analyzer.data.ReferenceMeterModel;
import com.ktd.analyzer.data.SensorFrame;
import com.ktd.analyzer.gpr.GprProtocol;
import com.ktd.analyzer.view.MagneticRadargramView;
import com.ktd.analyzer.view.MagneticTracePanelsView;
import com.ktd.analyzer.view.ColorMaps;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Magnetic survey visualisation with CANLI GPR-like controls, used only in 3D live scan > GPR görünüm. */
public final class MagneticRadarActivity extends Activity implements BluetoothClient.Listener {
    private static final int SAVE=9914, OPEN=9915;
    private static final int MAGIC=0x4B4D4752; // legacy KMGR
    private static final int SECURE_MAGIC=0x4B544732; // KTG2 encrypted recording
    private final Handler playbackHandler=new Handler(Looper.getMainLooper());
    private final ArrayList<Float> playback=new ArrayList<>();
    private boolean fileMode=false,playingFile=false,previewShowing=false;
    private float playbackReference=Float.NaN;
    private int playPos=0;
    private long lastArrivalMs=0;
    private double averageArrivalNs=0;
    private int timingSamples=0;
    private final Runnable playTick=new Runnable(){@Override public void run(){if(!playingFile)return;int n=0;while(n<15&&playPos<playback.size()){appendDelta(playback.get(playPos++),false);n++;}if(playPos>=playback.size()){playingFile=false;radar.setBrowseEnabled(true);showStartButton(false);}else playbackHandler.postDelayed(this,32);}};
    private BluetoothClient bluetooth;
    private final PerfMonitor perfMonitor=new PerfMonitor();
    private TextView performanceText;
    private final ReferenceModel reference=new ReferenceModel();
    private MagneticRadargramView radar;
    private MagneticTracePanelsView panels;
    private TextView status, gainLabel, fileNameLabel;
    private Button colorButton, mineralButton;
    private Button startButton;
    private boolean running=false,zeroed=false,mineral=false;
    private float gain=1f, boost=2f;
    private static final int GMR_CLASSIC_PALETTE_COUNT=5;
    private int palette=0;
    
    private final ArrayList<Float> captured=new ArrayList<>();
    private static final double CALIBRATED_CM_PER_SAMPLE=180.0/24.0; // 180 cm measured for 24 values in cantimetre.kmgr; only valid at same scanning step
    private long sequence=0;
    private float baseline=0,slowBase=0,fastBase=0,lastDelta=0;
    private float smoothedAnomaly=0, featurePolarity=0, featureEnergy=0;
    private int featureKind=0, featureStep=0, featureSpan=0;
    private boolean anomalyLatched=false;
    private int quietSamples=0;
    private static final int REFERENCE_SAMPLES=8;
    private int referenceSamples=0, polaritySamples=0;
    private double referenceSum=0;
    private float scanBaseline=0;

    @Override public void onCreate(Bundle b){super.onCreate(b);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);bluetooth=new BluetoothClient(this);build();}
    private int dp(int n){return Ui.dp(this,n);}    
    private Button button(String name){Button b=Ui.button(this,name,false);b.setTextSize(11);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));b.setAllCaps(false);b.setSingleLine(false);b.setMaxLines(2);b.setGravity(Gravity.CENTER);b.setPadding(dp(2),0,dp(2),0);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    private TextView text(String s,int size){TextView t=Ui.text(this,s,size,Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private void add(LinearLayout p,Button b){p.addView(b,new LinearLayout.LayoutParams(-1,0,1));}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(5,15,23));root.setPadding(dp(5),dp(3),dp(5),dp(3));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(176,227,255),Color.rgb(47,128,197),Color.rgb(121,208,248)}));
        Button back=button("‹ GERİ");back.setLines(1);back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(27,84,132)));back.setTextColor(Color.WHITE);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(100),-1));
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        TextView title=text("KTD  GMR",25);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD));
        title.setTextColor(Color.rgb(115,205,255));
        title.setShadowLayer(dp(2),0,dp(1),Color.rgb(13,62,127));
        android.graphics.drawable.GradientDrawable metallicBlue=new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(176,229,255),Color.rgb(38,128,205),Color.rgb(127,216,255)});
        // Entire header has the metallic blue background, without raised steps.
        title.setTextColor(Color.rgb(4,39,80));
        title.setIncludeFontPadding(false);
        heading.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
        fileNameLabel=text("CANLI ÇEKİM",11);
        fileNameLabel.setSingleLine(true);
        fileNameLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        fileNameLabel.setTextColor(Color.rgb(175,208,223));
        heading.addView(fileNameLabel,new LinearLayout.LayoutParams(-1,dp(19)));
        head.addView(heading,new LinearLayout.LayoutParams(0,dp(52),1));
        status=text("○ BT YOK",12);status.setTextColor(Color.WHITE);
        status.setTypeface(null,android.graphics.Typeface.BOLD);
        head.addView(status,new LinearLayout.LayoutParams(dp(134),-1));
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);
        radar=new MagneticRadargramView(this);radar.setGain(gain);radar.setPalette(palette);radar.setEstimatedTime(true);radar.setTimeWindowNs(Double.NaN);
        radar.setPerfMonitor(perfMonitor);
        body.addView(radar,new LinearLayout.LayoutParams(0,-1,68));

        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setBackgroundColor(Color.rgb(6,16,23));
        LinearLayout gainRow=new LinearLayout(this);gainRow.setGravity(Gravity.CENTER_VERTICAL);
        gainLabel=text("KAZANÇ 1.0×",12);gainLabel.setSingleLine(true);
        gainLabel.setTextColor(Color.rgb(235,249,255));
        gainRow.setOrientation(LinearLayout.VERTICAL);
        gainRow.addView(gainLabel,new LinearLayout.LayoutParams(-1,dp(26)));
        SeekBar gainBar=new SeekBar(this);gainBar.setMax(100);gainBar.setProgress(10);
        gainBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){gain=p/10f;gainLabel.setText(String.format(Locale.US,"KAZANÇ %.1f×",gain));radar.setGain(gain);panels.invalidate();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        gainBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(45,216,229)));
        gainRow.addView(gainBar,new LinearLayout.LayoutParams(-1,dp(23)));
        center.addView(gainRow,new LinearLayout.LayoutParams(-1,dp(53)));
        panels=new MagneticTracePanelsView(this,radar);center.addView(panels,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(center,new LinearLayout.LayoutParams(0,-1,17));

        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        startButton=button("BAŞLAT");
        startButton.setOnClickListener(v->{
            if(fileMode){
                if(playingFile){
                    playingFile=false;
                    radar.setBrowseEnabled(true);
                    playbackHandler.removeCallbacks(playTick);
                    showStartButton(false);
                    return;
                }
                if(playback.isEmpty())return;
                if(previewShowing||playPos>=playback.size()){
                    radar.clear();radar.setSurveyPositionCm(0);
                    sequence=0;resetDetection();playPos=0;previewShowing=false;
                }
                radar.setWindowTraces(72);
                playingFile=true;
                radar.setBrowseEnabled(false);
                showStartButton(true);
                playbackHandler.post(playTick);
                return;
            }
            if(running){
                if(BluetoothClient.isConnected()&&!BluetoothClient.isContinuousDataSource())bluetooth.send("LIVE_STOP\n");
                running=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);radar.setBrowseEnabled(true);showStartButton(false);
                return;
            }
            if(!BluetoothClient.isConnected()){
                Toast.makeText(this,"Bluetooth bağlantısı gerekli",Toast.LENGTH_SHORT).show();return;
            }
            reference.clear();zeroed=false; // Each new live recording takes its own ground reference.
            if(!BluetoothClient.isContinuousDataSource()){
                bluetooth.send("RATE=100\n");
                if(!bluetooth.send("LIVE_START\n"))return;
            }
            // GMR CANLI GORSEL HIZ %50: 72 yerine 144 olcum sutunu ayni genislikte gosterilir.
            // Sensor akisi, olcum sayisi, kayit, derinlik ve dosya oynatma hizi degismez.
            radar.setWindowTraces(144);
            running=true;getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);radar.setBrowseEnabled(false);showStartButton(true);
        });
        add(controls,startButton);
        Button open=button("DOSYA AÇ");open.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff265d86));open.setOnClickListener(v->open());add(controls,open);
        Button save=button("KAYDET");save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff247c67));save.setOnClickListener(v->save());add(controls,save);
        Button color=button("RENK");colorButton=color;color.setOnClickListener(v->{
            com.ktd.analyzer.view.ColorMaps.configure(this);
            java.util.ArrayList<com.ktd.analyzer.CustomPaletteStore.Item> saved=com.ktd.analyzer.CustomPaletteStore.load(this);
            String[] names=new String[GMR_CLASSIC_PALETTE_COUNT+saved.size()];
            for(int i=0;i<GMR_CLASSIC_PALETTE_COUNT;i++)names[i]="GMR Klasik "+(i+1);
            for(int i=0;i<saved.size();i++)names[GMR_CLASSIC_PALETTE_COUNT+i]=saved.get(i).name;
            new android.app.AlertDialog.Builder(this).setTitle("GMR RENK PALETİ")
                .setItems(names,(d,which)->{
                    palette=which<GMR_CLASSIC_PALETTE_COUNT?which:com.ktd.analyzer.CustomPaletteStore.BASE+which-GMR_CLASSIC_PALETTE_COUNT;
                    radar.setPalette(palette);color.setText(which<GMR_CLASSIC_PALETTE_COUNT?"RENK\n"+(which+1):"ÖZEL\nRENK");panels.invalidate();updateButtonColors();
                }).setNegativeButton("İPTAL",null).show();
        });add(controls,color);
        Button filter=button("MİNERAL\nKAPALI");mineralButton=filter;filter.setOnClickListener(v->{mineral=!mineral;filter.setText(mineral?"MİNERAL\nAÇIK":"MİNERAL\nKAPALI");updateButtonColors();});add(controls,filter);
        Button reset=button("SIFIRLA");reset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff965344));reset.setOnClickListener(v->{setFileTitle("CANLI ÇEKİM");fileMode=false;playingFile=false;previewShowing=false;lastArrivalMs=0;averageArrivalNs=0;timingSamples=0;radar.setWindowTraces(72);playbackHandler.removeCallbacks(playTick);showStartButton(false);playback.clear();playPos=0;reference.clear();zeroed=false;sequence=0;resetDetection();radar.clear();radar.setSurveyPositionCm(0);radar.setBrowseEnabled(true);panels.invalidate();synchronized(captured){captured.clear();}});add(controls,reset);
        body.addView(controls,new LinearLayout.LayoutParams(0,-1,16));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);Ui.applySafeInsets(this,root);updateStatus();updateButtonColors();
    }

    private void showStartButton(boolean active){
        if(startButton==null)return;
        startButton.setText(active?"DURDUR":"BAŞLAT");
        startButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active?0xffb3343f:0xff204c75));
        startButton.setTextColor(Color.WHITE);
    }
    private void updateButtonColors(){
        if(colorButton!=null){
            int[] colors={0xff276eaa,0xff138da4,0xff777b82,0xffa98627,0xffa94743};
            colorButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[Math.floorMod(palette,colors.length)]));
            colorButton.setTextColor(Color.WHITE);
        }
        if(mineralButton!=null){
            mineralButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mineral?0xffb13f43:0xff2364af));
            mineralButton.setTextColor(Color.WHITE);
        }
    }
    private final java.util.ArrayDeque<Float> measuredHistory=new java.util.ArrayDeque<>();
    private void resetDetection(){
        measuredHistory.clear();
        baseline=slowBase=fastBase=lastDelta=smoothedAnomaly=featurePolarity=featureEnergy=0;
        featureKind=featureStep=featureSpan=quietSamples=polaritySamples=0;
        anomalyLatched=false;referenceSamples=0;referenceSum=0;scanBaseline=0;
    }
    private void updateStatus(){if(status==null)return;boolean ok=BluetoothClient.isConnected();status.setText(ok?"● BT BAĞLI":"○ BT YOK");status.setBackgroundColor(ok?Color.rgb(23,136,97):Color.rgb(147,56,70));}
    @Override public void onState(String s){runOnUiThread(()->{updateStatus();if(!BluetoothClient.isConnected()){running=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);showStartButton(false);}});}    
    @Override protected void onStart(){super.onStart();running=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);bluetooth.activate();if(BluetoothClient.isConnected()&&!BluetoothClient.isContinuousDataSource())bluetooth.send("LIVE_STOP\n");updateStatus();showStartButton(false);radar.setBrowseEnabled(true);}
    @Override protected void onStop(){perfMonitor.stop();playingFile=false;playbackHandler.removeCallbacks(playTick);if(BluetoothClient.isConnected()&&!BluetoothClient.isContinuousDataSource())bluetooth.send("LIVE_STOP\n");running=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);radar.setBrowseEnabled(true);showStartButton(false);bluetooth.release();super.onStop();}

    private static double ricker(double x,double mu,double sigma){ double t=(x-mu)/Math.max(1e-6,sigma); double t2=t*t; return (1.0-t2)*Math.exp(-0.5*t2); }

    @Override public void onFrame(SensorFrame f){
        perfMonitor.received();if(!running||fileMode||f==null)return;float v=f.signal();if(!Float.isFinite(v)||v<0||v>1023)return;
        // One received measurement produces one screen column.
        // Keep a FIXED trace window: changes to Bluetooth arrival speed must not
        // stretch previous anomalies or reverse the direction of the animation.
        final long queuedAtNs=System.nanoTime();
        runOnUiThread(()->{perfMonitor.queueWaitNs(System.nanoTime()-queuedAtNs);if(!running)return;float z=ReferenceModel.normalize(v);if(!zeroed){reference.zero(z);zeroed=true;return;}
            float delta=reference.delta(z);appendDelta(delta,true);
        });
    }
    // GMR anomaly stream: one received magnetic measurement per horizontal column.
    // The vertical position is the project's (800/Z)/10 visualization convention,
    // NOT independently measured GPR propagation depth or a material ID.
    // No simulated hyperbolas or added target geometries.
    // GMR pseudo-radargram: each column is generated from ONE measured magnetic
    // gradiometer sample. The wavelet is a visual encoding, not a measured GPR echo.
    // (800/Z)/10 is kept as the project's display axis. It is not time-of-flight depth.
    private void appendDelta(float delta, boolean store){
        long processToken=perfMonitor.processBegin();
        if(store){synchronized(captured){captured.add(delta);}}
        if(!Float.isFinite(delta)){perfMonitor.processEnd(processToken);return;}
        final int size=256;
        final short[] section=new short[size];
        final float signedRaw=delta;
        final double magnitude=Math.abs(signedRaw);
        final double threshold=15.0;
        if(magnitude>=threshold){
            final float activeReference=fileMode?playbackReference:reference.reference();
            final float calculated=ReferenceMeterModel.metersFromDelta(signedRaw,activeReference);
            // Values outside the visible 0-10 display are not falsely pinned at 10 m.
            final double projectDepth=calculated;
            if(!Double.isFinite(projectDepth)||projectDepth<0.25||projectDepth>10.0){
                radar.add(new GprProtocol.Trace(sequence++,section));
                radar.setSurveyPositionCm(0);panels.invalidate();
                perfMonitor.processEnd(processToken);return;
            }
            final double center=projectDepth*(size-1)/10.0;
            // Width represents visual anomaly intensity, not measured target height.
            // Multi-lobed Ricker response adds GPR-like alternating dark/light bands
            // without fabricating independent subsoil depth samples or parabolas.
            final double strength=Math.min(1.0,(magnitude-threshold)/95.0);
            final double sigma=3.8+6.2*strength;
            final double amplitude=Math.min(23500.0,9500.0+(magnitude-threshold)*125.0);
            final double polarity=signedRaw<0?-1.0:1.0;
            for(int y=0;y<size;y++){
                double u=(y-center)/sigma;
                if(Math.abs(u)>6.0)continue;
                double primary=(1.0-u*u)*Math.exp(-0.5*u*u);
                double secondary=0.32*(1.0-(u-2.65)*(u-2.65))
                        *Math.exp(-0.5*(u-2.65)*(u-2.65));
                double tertiary=0.19*(1.0-(u+2.7)*(u+2.7))
                        *Math.exp(-0.5*(u+2.7)*(u+2.7));
                double reflection=primary+secondary+tertiary;
                double sample=polarity*amplitude*reflection;
                section[y]=(short)Math.max(-30000,Math.min(30000,Math.round(sample)));
            }
        }
        radar.add(new GprProtocol.Trace(sequence++,section));
        radar.setSurveyPositionCm(0);
        panels.invalidate();
        perfMonitor.processEnd(processToken);
    }
    private void preview(){
        radar.setWindowTraces(180);radar.clear();radar.setSurveyPositionCm(0);sequence=0;resetDetection();
        // Reconstruct from EVERY sample in the exact same order as live/playback;
        // only the final window of 180 traces remains visible.
        int total=playback.size();if(total==0)return;
        for(int i=0;i<total;i++) appendDelta(playback.get(i),false);
        playPos=0;previewShowing=true;
    }
    private void open(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,OPEN);}

    private String displayFileName(Uri uri){
        if(uri==null)return "KAYIT";
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){
                int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(column>=0){String name=cursor.getString(column);if(name!=null&&!name.trim().isEmpty())return name;}
            }
        }catch(Exception ignored){}
        String value=uri.getLastPathSegment();
        return value==null?"KAYIT":value;
    }
    private void setFileTitle(String name){
        if(fileNameLabel!=null)fileNameLabel.setText(name);
    }

    private static byte[] derivedKey() throws Exception {
        // App-level encryption: protect against casual file inspection.
        // For true trade-secret protection the key must NOT be published with source/APK.
        byte[] seed = ("KTD-GMR-RECORDING-ACCESS-" + "2026-NONPUBLIC-V1")
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.getInstance("SHA-256").digest(seed);
    }
    private static byte[] encryptRecord(ArrayList<Float> values) throws Exception {
        ByteArrayOutputStream payload=new ByteArrayOutputStream();
        try(DataOutputStream data=new DataOutputStream(payload)){
            data.writeInt(values.size());
            for(Float value: values)data.writeFloat(value);
        }
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(derivedKey(),"AES"),new GCMParameterSpec(128,nonce));
        byte[] ciphertext=cipher.doFinal(payload.toByteArray());
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(result)){
            out.writeInt(SECURE_MAGIC);out.writeInt(2);
            out.write(nonce);out.writeInt(ciphertext.length);out.write(ciphertext);
        }
        return result.toByteArray();
    }
    private static ArrayList<Float> decryptRecord(DataInputStream in) throws Exception {
        int version=in.readInt();
        if(version!=2)throw new java.io.IOException("Desteklenmeyen korumalı kayıt sürümü");
        byte[] nonce=new byte[12];in.readFully(nonce);
        int size=in.readInt();
        if(size<16||size>40_000_032)throw new java.io.IOException("Bozuk kayıt uzunluğu");
        byte[] encrypted=new byte[size];in.readFully(encrypted);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(derivedKey(),"AES"),new GCMParameterSpec(128,nonce));
        byte[] decrypted=cipher.doFinal(encrypted);
        try(DataInputStream payload=new DataInputStream(new ByteArrayInputStream(decrypted))){
            int count=payload.readInt();
            if(count<0||count>10000000||decrypted.length!=4+4L*count)
                throw new java.io.IOException("Bozuk kayıt verisi");
            ArrayList<Float> values=new ArrayList<>(count);
            for(int j=0;j<count;j++){
                float v=payload.readFloat();
                if(!Float.isFinite(v))throw new java.io.IOException("Geçersiz ölçüm");
                values.add(v);
            }
            return values;
        }
    }
    private void save(){synchronized(captured){if(captured.isEmpty()){Toast.makeText(this,"Kaydedilecek manyetik ölçüm yok",Toast.LENGTH_SHORT).show();return;}}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,"KTD_MANYETIK_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".kmgr");startActivityForResult(i,SAVE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==SAVE){
            ArrayList<Float> copy; synchronized(captured){copy=new ArrayList<>(captured);}
            new Thread(()->{try(OutputStream stream=getContentResolver().openOutputStream(uri);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(stream))){
                out.writeInt(MAGIC);out.writeInt(2);
                out.writeFloat(reference.reference());out.writeInt(copy.size());
                for(Float value:copy)out.writeFloat(value);
                out.flush();
                runOnUiThread(()->{setFileTitle(displayFileName(uri));Toast.makeText(this,"Şifresiz KMGR kaydı tamamlandı",Toast.LENGTH_SHORT).show();});
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Kayıt hatası: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();
        }else if(request==OPEN){
            new Thread(()->{try(InputStream stream=getContentResolver().openInputStream(uri);DataInputStream in=new DataInputStream(new BufferedInputStream(stream))){
                int fileMagic=in.readInt();
                ArrayList<Float> values;
                if(fileMagic==SECURE_MAGIC){
                    values=decryptRecord(in);
                }else if(fileMagic==MAGIC){
                    // Backwards compatible reader for older, unencrypted recordings.
                    int formatVersion=in.readInt();
                    if(formatVersion!=1&&formatVersion!=2)throw new java.io.IOException("Desteklenmeyen kayıt sürümü");
                    playbackReference=formatVersion==2?in.readFloat():Float.NaN;
                    int count=in.readInt();
                    if(count<0||count>10000000)throw new java.io.IOException("Geçersiz örnek sayısı");
                    values=new ArrayList<>(count);
                    for(int j=0;j<count;j++){
                        float v=in.readFloat();
                        if(!Float.isFinite(v))throw new java.io.IOException("Geçersiz ölçüm");
                        values.add(v);
                    }
                }else throw new java.io.IOException("KTD GMR kayıt dosyası değil");
                runOnUiThread(()->{playingFile=false;playbackHandler.removeCallbacks(playTick);running=false;if(!BluetoothClient.isContinuousDataSource())bluetooth.send("LIVE_STOP\n");fileMode=true;setFileTitle(displayFileName(uri));playback.clear();playback.addAll(values);preview();radar.setBrowseEnabled(true);showStartButton(false);Toast.makeText(this,"Dosya açıldı: "+values.size()+" ölçüm",Toast.LENGTH_SHORT).show();});
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Dosya açılamadı: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();
        }
    }
}
