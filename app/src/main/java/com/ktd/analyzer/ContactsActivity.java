package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** User-selected contacts only. No READ_CONTACTS, no bulk address-book reading. */
public final class ContactsActivity extends Activity {
    private static final int PICK_PHONE = 9273;
    private static final String PREF = "ktd_selected_contact_store";
    private static final String KEY = "contacts_json";
    private final ArrayList<Entry> entries = new ArrayList<>();
    private LinearLayout list;

    private static final class Entry {
        final String name;
        final String number;
        Entry(String n, String p) { name=n; number=p; }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        read(); build(); showEntries();
    }

    private void read() {
        entries.clear();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY, "[]"));
            for(int i=0;i<arr.length();i++) {
                JSONObject o=arr.optJSONObject(i);
                if(o==null)continue;
                String p=o.optString("number", "");
                if(!p.trim().isEmpty())entries.add(new Entry(o.optString("name", "Kişi"),p));
            }
        }catch(Exception ignored) { }
    }

    private void save() {
        JSONArray arr=new JSONArray();
        for(Entry e:entries) {
            JSONObject o=new JSONObject();
            try { o.put("name",e.name);o.put("number",e.number); arr.put(o); }
            catch(Exception ignored) { }
        }
        // Stored on this device only, never uploaded/transmitted by KTD.
        getSharedPreferences(PREF,MODE_PRIVATE).edit().putString(KEY,arr.toString()).apply();
    }

    private void build() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,16,24));
        root.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));
        TextView title=Ui.title(this,"KTD KİŞİLERİ");
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
        TextView hint=Ui.text(this,
                "Yalnız senin seçtiğin kişileri kaydederiz. WhatsApp araması WhatsApp içinde başlatılır.",
                13,Color.rgb(173,194,202));
        root.addView(hint,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));

        Button pick=Ui.button(this,"+ TELEFON REHBERİNDEN KİŞİ EKLE",true);
        pick.setOnClickListener(v->pickContact());
        root.addView(pick,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));

        Button group=Ui.button(this,"WHATSAPP GRUP GÖRÜŞMESİNE GİT",false);
        group.setOnClickListener(v->showGroupCallInstructions());
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));
        gp.setMargins(0,Ui.dp(this,8),0,Ui.dp(this,8));
        root.addView(group,gp);

        ScrollView scroll=new ScrollView(this);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button back=Ui.button(this,"GERİ",false);
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        setContentView(root);
        Ui.applySafeInsets(this,root);
    }

    private void showEntries() {
        list.removeAllViews();
        if(entries.isEmpty()) {
            TextView t=Ui.text(this,"Henüz kişi seçilmedi. Rehberden kişi ekleyerek başlayabilirsin.",14,Color.rgb(164,185,195));
            t.setPadding(Ui.dp(this,8),Ui.dp(this,20),0,0);
            list.addView(t);return;
        }
        TextView count=Ui.text(this,entries.size()+" kişi bu cihazda kayıtlı",13,Color.rgb(36,213,176));
        list.addView(count);
        for(Entry e:new ArrayList<>(entries)) {
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this,12),Ui.dp(this,13),Ui.dp(this,12),Ui.dp(this,12));
            card.setBackground(Ui.bg(Color.rgb(18,36,48),12,this));
            TextView title=Ui.text(this,e.name,17,Color.WHITE);
            TextView phone=Ui.text(this,e.number,13,Color.rgb(166,191,205));
            card.addView(title);card.addView(phone);
            LinearLayout actions=new LinearLayout(this);
            Button chat=Ui.button(this,"WHATSAPP",true);
            chat.setOnClickListener(v->openChat(e));
            Button del=Ui.button(this,"SİL",false);
            del.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Kayıtlı kişiyi sil")
                    .setMessage(e.name+" KTD listesinden silinsin mi?")
                    .setPositiveButton("Sil",(dialog,which)->{entries.remove(e);save();showEntries();})
                    .setNegativeButton("Vazgeç",null).show());
            actions.addView(chat,new LinearLayout.LayoutParams(0,Ui.dp(this,44),2));
            actions.addView(del,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
            card.addView(actions);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,Ui.dp(this,6),0,Ui.dp(this,6));list.addView(card,lp);
        }
    }

    private void pickContact() {
        Intent pick=new Intent(Intent.ACTION_PICK);
        pick.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        try { startActivityForResult(pick,PICK_PHONE); }
        catch(Exception ex) { Toast.makeText(this,"Rehber açılamadı: "+ex.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_PHONE||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        String name=null,number=null;
        String[] projection={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        try(Cursor c=getContentResolver().query(uri,projection,null,null,null)) {
            if(c!=null&&c.moveToFirst()) { name=c.getString(0);number=c.getString(1); }
        }catch(Exception ex) {
            Toast.makeText(this,"Kişi okunamadı: "+ex.getMessage(),Toast.LENGTH_LONG).show();return;
        }
        if(number==null||number.trim().isEmpty()) {
            Toast.makeText(this,"Seçilen kişinin telefon numarası bulunamadı",Toast.LENGTH_LONG).show();return;
        }
        String normalized=number.trim();
        for(Entry e:entries)if(e.number.equals(normalized)) {
            Toast.makeText(this,"Bu kişi zaten kayıtlı",Toast.LENGTH_SHORT).show();return;
        }
        entries.add(new Entry(name==null||name.trim().isEmpty()?"Kişi":name,normalized));
        save();showEntries();
    }

    private static String whatsappNumber(String raw) {
        if(raw==null)return "";
        String digits=raw.replaceAll("[^0-9]", "");
        // Turkish local number forms. Other country codes must already be present.
        if(digits.startsWith("00"))digits=digits.substring(2);
        else if(digits.length()==11&&digits.startsWith("0"))digits="90"+digits.substring(1);
        else if(digits.length()==10&&digits.startsWith("5"))digits="90"+digits;
        return digits;
    }

    private void openChat(Entry e) {
        String phone=whatsappNumber(e.number);
        if(phone.length()<9) {
            Toast.makeText(this,"Numarayı ülke koduyla kaydedin (örn. +90)",Toast.LENGTH_LONG).show();return;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+phone));
        try { intent.setPackage("com.whatsapp");startActivity(intent); }
        catch(Exception ex) {
            try { intent.setPackage(null);startActivity(intent); }
            catch(Exception error) { Toast.makeText(this,"WhatsApp veya tarayıcı açılamadı",Toast.LENGTH_LONG).show(); }
        }
    }

    private void showGroupCallInstructions() {
        new AlertDialog.Builder(this)
            .setTitle("WhatsApp konferans görüşmesi")
            .setMessage("WhatsApp açıldıktan sonra ARAMALAR bölümüne girin; yeni grup araması oluşturun veya bir WhatsApp grubundan sesli/görüntülü arama başlatın.\n\n"+
                    "KTD'de kaydettiğiniz kişiler WhatsApp görüşmesine otomatik eklenemez. Katılımcıları WhatsApp içinde siz seçersiniz.")
            .setPositiveButton("WHATSAPP'I AÇ",(d,w)->openWhatsApp())
            .setNegativeButton("İptal",null).show();
    }

    private void openWhatsApp() {
        Intent launch=getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if(launch==null)launch=getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
        try {
            if(launch!=null)startActivity(launch);
            else {
                Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.whatsapp.com/calling"));
                startActivity(i);
            }
        }catch(Exception ex){Toast.makeText(this,"WhatsApp açılamadı",Toast.LENGTH_LONG).show();}
    }
}
