package com.ktd.analyzer.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;

import com.ktd.analyzer.data.SensorFrame;
import com.ktd.analyzer.data.SensorStreamDecoder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Uygulama genelinde TEK Classic Bluetooth SPP bağlantısı.
 * Activity değişince soket kapanmaz; yeni ekran sadece dinleyici olur.
 */
public final class BluetoothClient {
    public interface Listener { void onState(String s); void onFrame(SensorFrame f); }
    /** CANLI GPR için yalnız etkin modda dağıtılan çerçevelenmiş ASCII paketleri. */
    public interface GprLineListener { void onGprLine(String line); }
    /** ESP32 physical button notifies the active 3D scan screen. */
    public interface HardwareSampleListener { void onHardwareSample(); }
    private static final CopyOnWriteArraySet<HardwareSampleListener> HARDWARE_SAMPLE_LISTENERS=new CopyOnWriteArraySet<>();
    public static void addHardwareSampleListener(HardwareSampleListener l){if(l!=null)HARDWARE_SAMPLE_LISTENERS.add(l);}
    public static void removeHardwareSampleListener(HardwareSampleListener l){if(l!=null)HARDWARE_SAMPLE_LISTENERS.remove(l);}
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final CopyOnWriteArraySet<Listener> LISTENERS=new CopyOnWriteArraySet<>();
    private static final CopyOnWriteArraySet<GprLineListener> GPR_LISTENERS=new CopyOnWriteArraySet<>();
    private static volatile boolean gprLineMode=false;
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final Object LOCK=new Object();
    private static final ScheduledExecutorService RETRY=Executors.newSingleThreadScheduledExecutor();
    private static volatile Context appContext;
    private static volatile String savedAddress="";
    private static volatile boolean manualDisconnect=false;
    private static volatile boolean retryPending=false;
    private static volatile BluetoothDevice preferredDevice;
    private static volatile BluetoothSocket socket;
    private static volatile boolean reading=false,connecting=false;
    private static volatile String state="Bluetooth bağlı değil";
    private static volatile String deviceName="";
    private static volatile SensorFrame lastFrame;
    // Link mode is stored per Bluetooth MAC, NEVER inferred from a device name.
    // HC-05 can be renamed, and some Nano sketches emit X/Y/Z/C as well.
    public static final int PROTOCOL_AUTO=0, PROTOCOL_STREAM=1, PROTOCOL_COMMAND=2;
    private static volatile int configuredProtocol=PROTOCOL_AUTO;
    private static volatile boolean observedRawStream=false;
    private static volatile boolean observedReference=false;
    /** True when data arrives continuously and the device does not need SAMPLE/REF commands. */
    public static boolean isContinuousDataSource(){
        return configuredProtocol==PROTOCOL_STREAM ||
            (configuredProtocol==PROTOCOL_AUTO && observedRawStream && !observedReference);
    }
    /** Selected AUTO mode (not inferred from the HC-05 advertised name). */
    public static boolean isAutoProtocol(){ return configuredProtocol==PROTOCOL_AUTO; }
    /** Explicit REF packets distinguish a command-based ESP32 from a passive Nano stream. */
    public static boolean hasObservedReference(){ return observedReference; }
    public static String protocolDescription(){
        return isContinuousDataSource()?"Seri veri akışı":configuredProtocol==PROTOCOL_COMMAND?"Komutlu cihaz":"Otomatik algılama";
    }


    private final Listener listener;
    private boolean active=false;
    public BluetoothClient(Listener l){listener=l;}

    public void activate(){
        if(active)return; active=true; LISTENERS.add(listener);
        listener.onState(state);
        if(isConnected()&&lastFrame!=null)listener.onFrame(lastFrame);
    }
    public void release(){active=false;LISTENERS.remove(listener);}
    /** Eski çağrılar için: artık global bağlantıyı kapatmaz. */
    public void close(){release();}

    public static boolean isConnected(){BluetoothSocket s=socket;return s!=null&&s.isConnected()&&reading;}
    public static String connectedName(){return deviceName==null?"":deviceName;}
    public static SensorFrame lastFrame(){return lastFrame;}

    public static void startGprLines(GprLineListener listener){
        if(listener!=null)GPR_LISTENERS.add(listener);
        gprLineMode=true;
    }
    public static void stopGprLines(GprLineListener listener){
        if(listener!=null)GPR_LISTENERS.remove(listener);
        if(GPR_LISTENERS.isEmpty())gprLineMode=false;
    }

    public static boolean hasConnectPermission(Activity a){
        return Build.VERSION.SDK_INT<31 || a.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    public static void requestConnectPermission(Activity a,int code){
        if(Build.VERSION.SDK_INT>=31 && !hasConnectPermission(a))a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},code);
    }

    /** Call when the app starts; reconnect previously selected SPP device. */
    public void reconnectSaved(Activity a){
        appContext=a.getApplicationContext();
        if(!hasConnectPermission(a)) { requestConnectPermission(a,4001); return; }
        BluetoothAdapter ad=BluetoothAdapter.getDefaultAdapter();
        if(ad==null || !ad.isEnabled())return;
        savedAddress=appContext.getSharedPreferences("ktd_bt",Context.MODE_PRIVATE).getString("last_mac","");
        if(savedAddress.isEmpty() || isConnected() || connecting)return;
        try{
            BluetoothDevice d=ad.getRemoteDevice(savedAddress);
            if(d.getBondState()==BluetoothDevice.BOND_BONDED){
                preferredDevice=d;manualDisconnect=false;connect(d);
            }
        }catch(Exception ignored){}
    }
    public void pickPaired(Activity a){
        appContext=a.getApplicationContext();
        if(isConnected()){broadcastState("Bağlı: "+connectedName());return;}
        if(!hasConnectPermission(a)){requestConnectPermission(a,4001);broadcastState("Bluetooth izni gerekli");return;}
        BluetoothAdapter ad=BluetoothAdapter.getDefaultAdapter();
        if(ad==null){broadcastState("Bluetooth yok");return;}
        if(!ad.isEnabled()){broadcastState("Bluetooth kapalı");return;}
        Set<BluetoothDevice> set=ad.getBondedDevices();
        if(set==null||set.isEmpty()){broadcastState("Eşleşmiş cihaz yok");return;}
        ArrayList<BluetoothDevice> devs=new ArrayList<>(set);String[] names=new String[devs.size()];
        for(int i=0;i<devs.size();i++){
            BluetoothDevice d=devs.get(i);
            names[i]=safeName(d);
        }
        new AlertDialog.Builder(a).setTitle("Eşleşmiş Bluetooth cihazını seç")
            .setItems(names,(dialog,which)->chooseProtocolAndConnect(a,devs.get(which)))
            .setNegativeButton("İptal",null).show();
    }

    /** Cihaz seciminden sonra ek bir veri iletisim tipi penceresi gosterme.
     * Tüm baglantilar otomatik protokol algilama kullanir; ESP32 ve Nano
     * paketleri mevcut decoder tarafindan icerigine gore ayristirilir.
     */
    private void chooseProtocolAndConnect(Activity a,BluetoothDevice chosen){
        if(chosen==null)return;
        String mac=chosen.getAddress();
        Context ctx=appContext!=null?appContext:a.getApplicationContext();
        ctx.getSharedPreferences("ktd_bt",Context.MODE_PRIVATE).edit()
                .putString("last_mac",mac)
                .putInt("protocol_"+mac,PROTOCOL_AUTO).apply();
        savedAddress=mac;
        configuredProtocol=PROTOCOL_AUTO;
        preferredDevice=chosen;
        manualDisconnect=false;
        connect(chosen);
    }

    public void connect(BluetoothDevice d){
        if(d==null || isConnected())return;
        synchronized(LOCK){if(connecting)return;connecting=true;}
        preferredDevice=d;
        manualDisconnect=false;
        // Detect from content, not the module's advertising name (HC-05 can be renamed).
        Context ctx=appContext;
        // Daha once kayitli STREAM / COMMAND secimi de otomatik moda gecsin.
        configuredProtocol=PROTOCOL_AUTO;
        if(ctx!=null)ctx.getSharedPreferences("ktd_bt",Context.MODE_PRIVATE).edit()
                .putInt("protocol_"+d.getAddress(),PROTOCOL_AUTO).apply();
        observedRawStream=false;observedReference=false;
        disconnectInternal(false); broadcastState("Bağlanıyor: "+safeName(d));
        EXEC.submit(()->{
            try{
                BluetoothSocket s=null;
                try{ s=d.createRfcommSocketToServiceRecord(SPP);s.connect(); }
                catch(Exception first){ try{ if(s!=null)s.close(); }catch(Exception ignored){} s=d.createInsecureRfcommSocketToServiceRecord(SPP);s.connect(); }
                socket=s;deviceName=safeName(d);reading=true;connecting=false;
                retryPending=false;
                broadcastState("Bağlı: "+deviceName+" • "+protocolDescription());
                readLoop(s.getInputStream());
            }catch(Exception e){connecting=false;broadcastState("Bağlantı kurulamadı: "+shortMsg(e));}
            finally{
                disconnectInternal(true);
                scheduleReconnect();
            }
        });
    }

    public void disconnect(){
        manualDisconnect=true;
        disconnectInternal(false);
        broadcastState("Bluetooth bağlı değil");
    }
    private static void scheduleReconnect(){
        if(manualDisconnect || preferredDevice==null || retryPending || isConnected())return;
        retryPending=true;
        RETRY.schedule(()->{
            retryPending=false;
            if(manualDisconnect || isConnected() || connecting)return;
            Context c=appContext;
            if(c==null)return;
            try{
                BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
                if(a==null || !a.isEnabled())return;
                if(Build.VERSION.SDK_INT>=31 && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;
                new BluetoothClient(null).connect(preferredDevice);
            }catch(Exception ignored){}
        },3,TimeUnit.SECONDS);
    }

    /** Telefonda ÖRNEK AL gibi komutları bağlı SPP cihazına yollar. */
    public boolean send(String text){
        BluetoothSocket s=socket;
        if(s==null||!s.isConnected()||!reading||text==null)return false;
        try{
            OutputStream out=s.getOutputStream();
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        }catch(Exception e){broadcastState("Komut gönderilemedi: "+shortMsg(e));return false;}
    }

    private static void readLoop(InputStream in)throws Exception{
        byte[] buf=new byte[2048];SensorStreamDecoder decoder=new SensorStreamDecoder();
        StringBuilder lineBuffer=new StringBuilder();
        StringBuilder buttonLineBuffer=new StringBuilder();
        boolean lastWasGpr=false;
        while(reading){
            int n=in.read(buf);if(n<0)break;if(n==0)continue;
            boolean nowGpr=gprLineMode;
            if(nowGpr!=lastWasGpr){lineBuffer.setLength(0);decoder=new SensorStreamDecoder();lastWasGpr=nowGpr;}
            String chunk=new String(buf,0,n,StandardCharsets.UTF_8);
            // Read hardware trigger independently of the sensor decoder; do not fabricate a data frame.
            if(!nowGpr){
                for(int k=0;k<chunk.length();k++){
                    char ch=chunk.charAt(k);
                    if(ch=='\n'||ch=='\r'){
                        if(buttonLineBuffer.length()>0){
                            String line=buttonLineBuffer.toString().trim();buttonLineBuffer.setLength(0);
                            if(line.equals("BUTTON_SAMPLE"))for(HardwareSampleListener l:HARDWARE_SAMPLE_LISTENERS)try{l.onHardwareSample();}catch(Exception ignored){}
                        }
                    }else if(buttonLineBuffer.length()<256){buttonLineBuffer.append(ch);}
                    else buttonLineBuffer.setLength(0);
                }
            }else buttonLineBuffer.setLength(0);
            if(nowGpr){
                for(int k=0;k<chunk.length();k++){
                    char ch=chunk.charAt(k);
                    if(ch=='\n' || ch=='\r'){
                        if(lineBuffer.length()>0){
                            String line=lineBuffer.toString().trim();lineBuffer.setLength(0);
                            if(line.startsWith("KTDGPR,")||line.startsWith("KTD1,TRACE,")){
                                for(GprLineListener l:GPR_LISTENERS)try{l.onGprLine(line);}catch(Exception ignored){}
                            }
                        }
                    }else if(lineBuffer.length()<20000){lineBuffer.append(ch);}
                    else{lineBuffer.setLength(0);}
                }
            }else{
                for(SensorFrame f:decoder.feed(chunk))broadcastFrame(f);
            }
        }
    }

    private static void broadcastFrame(SensorFrame f){
        if(f==null)return;
        if(f.referencePacket)observedReference=true;
        else if(!Float.isFinite(f.x)||!Float.isFinite(f.y))observedRawStream=true;
        lastFrame=f;
        for(Listener l:LISTENERS)try{l.onFrame(f);}catch(Exception ignored){}
    }
    private static void broadcastState(String s){state=s;for(Listener l:LISTENERS)try{l.onState(s);}catch(Exception ignored){}}
    private static void disconnectInternal(boolean fromReader){
        reading=false;BluetoothSocket s=socket;socket=null;deviceName="";lastFrame=null;
        try{if(s!=null)s.close();}catch(Exception ignored){}
        if(fromReader)broadcastState("Bağlantı kesildi");
    }
    private static String safeName(BluetoothDevice d){try{String n=d.getName();return n==null||n.trim().isEmpty()?d.getAddress():n;}catch(Exception e){return "Cihaz";}}
    private static String shortMsg(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}
}
