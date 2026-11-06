package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.content.ContentUris;
import android.provider.DocumentsContract;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.content.ClipData;
import android.database.Cursor;
import android.provider.OpenableColumns;
import com.ktd.analyzer.data.ScanData;
import com.ktd.analyzer.data.PhoneCsvCatalog;
import com.ktd.analyzer.data.MeasurementFileReader;
import com.ktd.analyzer.data.V3dImporter;
import java.io.InputStream;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.ktd.analyzer.data.CsvUtil;
import com.ktd.analyzer.data.ScanSessionStore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public final class RecordsActivity extends Activity {
    private static final int PICK_EXTERNAL=9190;
    private static final int PICK_FOLDER=9191;
    private static final int STORAGE_PERMISSION=9192;
    private ArrayList<PhoneCsvCatalog.Item> publicCsv=new ArrayList<>();
    private boolean publicScanComplete=false;
    private boolean publicScanBusy=false;
    private boolean storagePermissionRequested=false;
    private LinearLayout list;

    @Override protected void onCreate(Bundle b){super.onCreate(b);build();load();scanPhoneFiles();
        if(getIntent().getBooleanExtra("import_picker",false))
            findViewById(android.R.id.content).post(this::pickExternalCsv);
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,12));root.setBackgroundColor(Color.rgb(7,16,24));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.title(this,"KTD 3D ANALYZER"),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        Button back=Ui.button(this,"‹",false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,40)));root.addView(top);
        Button importButton=Ui.button(this,"TELEFONDAN / TABLETTEN CSV AÇ",true);
        Ui.fitText(importButton,12,16,1);
        importButton.setOnClickListener(v->pickExternalCsv());
        root.addView(importButton,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));
        LinearLayout tools=new LinearLayout(this);
        Button refresh=Ui.button(this,"CSV'LERİ TARA",false);refresh.setOnClickListener(v->scanPhoneFiles());
        Button tree=Ui.button(this,"KLASÖR EKLE",false);tree.setOnClickListener(v->chooseFolder());
        tools.addView(refresh,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        tools.addView(tree,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        root.addView(tools);
        TextView hint=Ui.text(this,"Telefon CSV listesi + İndirilenler / WhatsApp / Belgeler klasör izni. Korumalı klasörler otomatik taranamaz.",11,Color.rgb(154,190,200));
        root.addView(hint,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,48)));
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);Ui.applySafeInsets(this,root);
    }

    private void section(String value){
        TextView title=Ui.text(this,value,14,Color.rgb(41,222,186));
        title.setPadding(Ui.dp(this,9),Ui.dp(this,18),0,Ui.dp(this,8));
        list.addView(title);
    }

    private void load(){
        list.removeAllViews();
        File dir=new File(getFilesDir(),"scans");File[] fs=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".csv"));
        section("KTD KAYITLARIM");
        if(fs==null||fs.length==0){
            TextView t=Ui.text(this,"Uygulama içi kayıt bulunamadı.",13,Color.rgb(160,180,195));
            list.addView(t);
        }else{
            Arrays.sort(fs, Comparator.comparingLong(File::lastModified).reversed());
            for(File csv:fs)addCard(csv);
        }
        section("TELEFON / TABLET CSV DOSYALARI");
        if(!publicScanComplete){
            TextView info=Ui.text(this,"Telefon dosyaları taranıyor...",13,Color.rgb(160,180,195));list.addView(info);
        }else if(publicCsv.isEmpty()){
            TextView info=Ui.text(this,
                "İndekste erişilebilir CSV bulunamadı. Yukarıdan KLASÖR EKLE ile İndirilenler / Documents / WhatsApp klasörü seç veya TELEFONDAN CSV AÇ kullan.",
                13,Color.rgb(160,180,195));list.addView(info);
        }else{
            TextView info=Ui.text(this,publicCsv.size()+" erişilebilir CSV/V3D dosyası listelendi • "+PhoneCsvCatalog.treeCount(this)+" klasör izni",11,Color.rgb(150,185,196));
            list.addView(info);
            for(PhoneCsvCatalog.Item item:publicCsv)addPhoneCard(item);
        }
    }

    private void addPhoneCard(PhoneCsvCatalog.Item item) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));
        card.setBackground(Ui.bg(Color.rgb(16,29,39),12,this));
        TextView name=Ui.text(this,item.name,14,Color.WHITE);name.setTypeface(null,1);
        TextView path=Ui.text(this,item.location,11,Color.rgb(150,180,195));
        card.addView(name);card.addView(path);
        card.setOnClickListener(v->openPhoneFile(item));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,Ui.dp(this,4),0,Ui.dp(this,4));list.addView(card,cp);
    }

    private void openPhoneFile(PhoneCsvCatalog.Item item) {
        Toast.makeText(this,"CSV açılıyor: "+item.name,Toast.LENGTH_SHORT).show();
        new Thread(()->{
            ScanData d=null;String error=null;
            try {
                d=MeasurementFileReader.read(this,item.uri,Prefs.stepCm(this));
                if(d.count<1)throw new Exception("Ölçüm bulunamadı");
                d.name=CsvUtil.safeBaseName(base(item.name));
            }catch(Exception e){error=e.getMessage();}
            final ScanData opened=d; final String failure=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(opened!=null){ScanSessionStore.current=opened;startActivity(new Intent(this,AnalysisActivity.class));}
                else Toast.makeText(this,"CSV açılamadı: "+failure,Toast.LENGTH_LONG).show();
            });
        },"ktd-open-phone-csv").start();
    }

    private void scanPhoneFiles() {
        if(publicScanBusy)return;
        if(Build.VERSION.SDK_INT>=23&&Build.VERSION.SDK_INT<33 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED
            && !storagePermissionRequested){
            // Tek seferlik talep. Reddedildiğinde ısrar edilmez: SAF yine çalışır.
            storagePermissionRequested=true;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},STORAGE_PERMISSION);
            return;
        }
        publicScanBusy=true;publicScanComplete=false;load();
        new Thread(()->{
            ArrayList<PhoneCsvCatalog.Item> r=PhoneCsvCatalog.scan(this);
            runOnUiThread(()->{
                publicScanBusy=false;publicScanComplete=true;publicCsv=r;
                if(!isFinishing()&&!isDestroyed())load();
            });
        },"ktd-phone-csv-catalog").start();
    }

    private void chooseFolder() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,PICK_FOLDER);}
        catch(Exception e){Toast.makeText(this,"Klasör seçilemedi: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==STORAGE_PERMISSION)scanPhoneFiles();
    }

    private void addCard(File csv){
        String base=base(csv.getName());File png=new File(csv.getParentFile(),base+".png");
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(Ui.dp(this,10),Ui.dp(this,9),Ui.dp(this,10),Ui.dp(this,9));card.setBackground(Ui.bg(Color.rgb(16,29,39),12,this));

        ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackgroundColor(Color.rgb(5,11,16));
        if(png.exists()){Bitmap b=BitmapFactory.decodeFile(png.getAbsolutePath());if(b!=null)thumb.setImageBitmap(b);}
        card.addView(thumb,new LinearLayout.LayoutParams(Ui.dp(this,92),Ui.dp(this,92)));

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(Ui.dp(this,12),0,Ui.dp(this,6),0);info.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=Ui.text(this,base,15,Color.WHITE);name.setTypeface(null,1);Ui.fitText(name,12,16,1);
        String when=new SimpleDateFormat("dd.MM.yyyy  HH:mm",Locale.getDefault()).format(new Date(csv.lastModified()));
        TextView meta=Ui.text(this,when+"  •  CSV"+(png.exists()?" + PNG":""),11,Color.rgb(145,170,188));Ui.fitText(meta,9,12,1);
        TextView hint=Ui.text(this,"Aç • Sil • Gönder için dokunun",11,Color.rgb(35,214,176));Ui.fitText(hint,9,12,1);
        info.addView(name);info.addView(meta);info.addView(hint);card.addView(info,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1));
        card.setOnClickListener(v->showActions(csv,png));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,112));cp.setMargins(0,Ui.dp(this,5),0,Ui.dp(this,5));list.addView(card,cp);
    }

    private void showActions(File csv,File png){
        String[] actions=png.exists()
                ?new String[]{"Aç","Sil","Gönder CSV","Görseli gönder"}
                :new String[]{"Aç","Sil","Gönder CSV"};
        new AlertDialog.Builder(this).setTitle(base(csv.getName())).setItems(actions,(d,w)->{
            if(w==0)open(csv);
            else if(w==1)delete(csv,png);
            else if(w==2)share(csv,png);
            else sharePng(png);
        }).setNegativeButton("İptal",null).show();
    }

    private void sharePng(File png) {
        try {
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",png);
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM,u);
            i.setClipData(ClipData.newUri(getContentResolver(),png.getName(),u));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Görseli gönder"));
        }catch(Exception e){Toast.makeText(this,"Görsel paylaşımı başarısız: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void open(File csv){
        try{ScanSessionStore.current=CsvUtil.readFile(csv);startActivity(new Intent(this,AnalysisActivity.class));}
        catch(Exception e){Toast.makeText(this,"Dosya açılamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void delete(File csv,File png){
        new AlertDialog.Builder(this).setMessage(base(csv.getName())+" silinsin mi?")
                .setPositiveButton("Sil",(d,w)->{csv.delete();if(png.exists())png.delete();load();})
                .setNegativeButton("İptal",null).show();
    }

    private void share(File csv,File png){
        try{
            String auth=getPackageName()+".fileprovider";
            Uri csvUri=FileProvider.getUriForFile(this,auth,csv);
            // Separate CSV share, not CSV+PNG mixed MIME. Receiver can open it directly.
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("text/csv");
            i.putExtra(Intent.EXTRA_STREAM,csvUri);
            i.setClipData(ClipData.newUri(getContentResolver(),csv.getName(),csvUri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.putExtra(Intent.EXTRA_SUBJECT,base(csv.getName()));
            startActivity(Intent.createChooser(i,"CSV dosyasını gönder"));
        }catch(Exception e){Toast.makeText(this,"Gönderme hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    /** Android dosya seçici (SAF): yalnız kullanıcı izin verdiği CSV/V3D dosyalarına erişir. */
    private void pickExternalCsv(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*"); // MIME türü hatalı kaydedilen CSV'ler de görünür.
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(i,PICK_EXTERNAL);}
        catch(Exception ex){Toast.makeText(this,"Dosya seçici açılamadı: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent intent){
        super.onActivityResult(requestCode,resultCode,intent);
        if(requestCode==PICK_FOLDER && resultCode==RESULT_OK && intent!=null && intent.getData()!=null){
            Uri folder=intent.getData();
            try{
                getContentResolver().takePersistableUriPermission(folder,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PhoneCsvCatalog.addTree(this,folder);
                Toast.makeText(this,"Klasör eklendi. CSV dosyaları listeleniyor.",Toast.LENGTH_LONG).show();
                scanPhoneFiles();
            }catch(Exception e){Toast.makeText(this,"Klasör erişim izni alınamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            return;
        }
        if(requestCode!=PICK_EXTERNAL||resultCode!=RESULT_OK||intent==null)return;
        ArrayList<Uri> selected=new ArrayList<>();
        ClipData clips=intent.getClipData();
        if(clips!=null){for(int i=0;i<clips.getItemCount();i++){
            Uri u=clips.getItemAt(i).getUri();if(u!=null&&!selected.contains(u))selected.add(u);
        }}else if(intent.getData()!=null)selected.add(intent.getData());
        if(selected.isEmpty())return;
        Toast.makeText(this,selected.size()+" dosya inceleniyor...",Toast.LENGTH_SHORT).show();
        // Büyük farklı-cihaz CSV listelerinde arayüz donmasın.
        new Thread(()->{
            int ok=0;String firstFailure=null;ScanData single=null;
            for(Uri uri:selected){try{
                String name=fileName(uri);
                boolean v3d=MeasurementFileReader.isV3d(this,uri);
                ScanData d=MeasurementFileReader.read(this,uri,Prefs.stepCm(this));
                if(d.count<1)throw new IllegalArgumentException("Ölçüm bulunamadı");
                d.name=CsvUtil.safeBaseName(base(name));
                if(v3d) CsvUtil.saveNamed(this,d,d.name);
                else CsvUtil.copyOriginalCsv(this,uri,d.name);
                if(selected.size()==1)single=d;
                ok++;
            }catch(Exception e){if(firstFailure==null)firstFailure=e.getMessage();}}
            final int success=ok;final String error=firstFailure;final ScanData opened=single;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                load();
                if(success==1&&selected.size()==1&&opened!=null){
                    ScanSessionStore.current=opened;
                    startActivity(new Intent(this,AnalysisActivity.class));
                }else if(success>0){Toast.makeText(this,success+" dosya KTD Kayıtlarıma aktarıldı.",Toast.LENGTH_LONG).show();}
                if(error!=null)Toast.makeText(this,"Bazı dosyalar açılamadı: "+error,Toast.LENGTH_LONG).show();
            });
        },"ktd-external-csv-import").start();
    }
    private String fileName(Uri uri){
        try(Cursor cur=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cur!=null&&cur.moveToFirst()){
                String name=cur.getString(0);if(name!=null&&!name.trim().isEmpty())return name;
            }
        }catch(Exception ignored){}
        String seg=uri.getLastPathSegment();return seg==null?"ALINAN_DOSYA.csv":seg;
    }

    private static String base(String n){int k=n.lastIndexOf('.');return k>0?n.substring(0,k):n;}
}
