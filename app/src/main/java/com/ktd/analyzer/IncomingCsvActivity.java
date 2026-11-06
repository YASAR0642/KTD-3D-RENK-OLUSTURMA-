package com.ktd.analyzer;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.view.Gravity;

import com.ktd.analyzer.data.CsvUtil;
import com.ktd.analyzer.data.MeasurementFileReader;
import com.ktd.analyzer.Prefs;
import com.ktd.analyzer.data.ScanData;
import com.ktd.analyzer.data.ScanSessionStore;

import java.util.ArrayList;
import java.io.InputStream;
import com.ktd.analyzer.gpr.GprFileIo;

/**
 * Android ACTION_VIEW / ACTION_SEND / ACTION_SEND_MULTIPLE ile WhatsApp, Dosyalar,
 * Gmail ve diğer uygulamaların paylaştığı CSV/V3D dosyalarını kabul eder.
 * URI izni yalnız gelen dosyayı okumak için kullanılır; geniş depolama izni istemez.
 */
public final class IncomingCsvActivity extends Activity {
    private volatile boolean working;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        TextView t = Ui.text(this, "KTD 3D ANALYZER\nCSV dosyası açılıyor...", 19, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.rgb(7,16,24));
        setContentView(t);
        process(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        process(intent);
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Uri> incomingUris(Intent i) {
        ArrayList<Uri> out = new ArrayList<>();
        if (i == null) return out;
        String action = i.getAction();
        if (Intent.ACTION_VIEW.equals(action)) add(out, i.getData());
        if (Intent.ACTION_SEND.equals(action)) {
            Uri single=i.getParcelableExtra(Intent.EXTRA_STREAM);
            add(out,single);
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Uri u : streams) add(out, u);
        }
        ClipData clip = i.getClipData();
        if (clip != null) for (int k=0; k<clip.getItemCount(); k++) add(out, clip.getItemAt(k).getUri());
        if (out.isEmpty()) add(out, i.getData());
        return out;
    }

    private static void add(ArrayList<Uri> out, Uri uri) {
        if (uri != null && ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))
                && !out.contains(uri)) out.add(uri);
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)) {
            if (c != null && c.moveToFirst()) {
                int sizeColumn = c.getColumnIndex(OpenableColumns.SIZE);
                if (sizeColumn >= 0 && !c.isNull(sizeColumn) && c.getLong(sizeColumn) > 32L*1024L*1024L)
                    throw new IllegalArgumentException("32 MB üzerindeki dosya kabul edilmiyor");
                String n = c.getString(0);
                if (n != null && !n.trim().isEmpty()) return n;
            }
        } catch (IllegalArgumentException ex) { throw ex; }
          catch (Exception ignored) { }
        String tail = uri.getLastPathSegment();
        return tail == null ? "ALINAN_DOSYA.csv" : tail;
    }

    private static String base(String n) {
        int p=n.lastIndexOf('.'); return p>0 ? n.substring(0,p) : n;
    }

    private void process(Intent intent) {
        if (working) return;
        final ArrayList<Uri> incoming=incomingUris(intent);
        if (incoming.isEmpty()) { fail("Dosya bağlantısı bulunamadı"); return; }
        if (!LicenseManager.isActivated(this)) {
            Toast.makeText(this,"Önce KTD uygulamasını etkinleştirin",Toast.LENGTH_LONG).show();
            startActivity(new Intent(this,ActivationActivity.class)); finish(); return;
        }
        working=true;
        new Thread(() -> {
            int success=0; ScanData first=null; String firstError=null;
            for (Uri uri : incoming) {
                try {
                    String filename=displayName(uri);
                    // Adin Tarama/Ali/Olcum ya da baska bir sey olmasi HIC onemli degil.
                    // Ilk once dosya icerigini kontrol et, CSV/V3D olarak oku.
                    boolean segy=filename.toLowerCase(java.util.Locale.ROOT).endsWith(".sgy") || filename.toLowerCase(java.util.Locale.ROOT).endsWith(".segy");
                    if(!segy) {
                        try(InputStream in=getContentResolver().openInputStream(uri)) {
                            if(in!=null){
                                byte[] h=new byte[3600];int total=0;
                                while(total<h.length){int r=in.read(h,total,h.length-total);if(r<0)break;total+=r;}
                                segy=total==3600 && GprFileIo.looksLikeSegyHeader(h);
                            }
                        }
                    }
                    if(segy){
                        // SGY doğrudan GPR okuyucusuna gider. URI tek seferlik okunur.
                        try(InputStream in=getContentResolver().openInputStream(uri)){
                            if(in==null)throw new Exception("SGY dosyası okunamadı");
                            GprFileIo.Record checked=GprFileIo.readSegy(in);
                            if(checked.traces.isEmpty())throw new Exception("SGY izleri boş");
                        }
                        final Uri openUri=uri;
                        runOnUiThread(()->{
                            Intent open=new Intent(this,LiveGprActivity.class);
                            open.setData(openUri);open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(open);
                            finish();
                        });
                        return;
                    }
                    boolean v3d=MeasurementFileReader.isV3d(this,uri);
                    ScanData d=MeasurementFileReader.read(this,uri,Prefs.stepCm(this));
                    if (d == null || d.count < 1) throw new Exception("Ölçüm verisi bulunamadı");
                    d.name=CsvUtil.safeBaseName(base(filename));
                    // Gelen CSV'nin orijinal baytlarini koru. V3D ise KTD CSV'sine cevir.
                    if(v3d)CsvUtil.saveNamed(this,d,d.name);
                    else CsvUtil.copyOriginalCsv(this,uri,d.name);
                    if (first==null) first=d;
                    success++;
                } catch (Exception ex) {
                    if (firstError==null) firstError=ex.getMessage();
                }
            }
            final ScanData opened=first;
            final int count=success;
            final String error=firstError;
            runOnUiThread(() -> {
                working=false;
                if (isFinishing()||isDestroyed()) return;
                if (count>0) {
                    if (count==1 && opened!=null) {
                        ScanSessionStore.current=opened;
                        startActivity(new Intent(this,AnalysisActivity.class));
                    } else {
                        startActivity(new Intent(this,RecordsActivity.class));
                    }
                    Toast.makeText(this,count+" CSV/V3D KTD kayıtlarına alındı",Toast.LENGTH_LONG).show();
                    finish();
                } else fail("CSV/V3D açılamadı: "+(error==null?"Desteklenmeyen dosya":error));
            });
        },"ktd-incoming-csv").start();
    }

    private void fail(String msg) {
        new android.app.AlertDialog.Builder(this).setTitle("KTD dosya aç")
            .setMessage(msg)
            .setPositiveButton("Dosya seç",(d,w)->{
                Intent i=new Intent(this,RecordsActivity.class);
                i.putExtra("import_picker",true);startActivity(i);finish();
            })
            .setNegativeButton("Kapat",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
}
