package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,14));root.setBackgroundColor(Color.rgb(7,16,24));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(Ui.title(this,"KTD 3D ANALYZER"),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));Button back=Ui.button(this,"‹",false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,40)));root.addView(top);
        Button createColors=Ui.button(this,"+ RENK OLUŞTUR · 10 NOKTA",true);
        createColors.setTextColor(Color.WHITE);
        createColors.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0,126,112)));
        createColors.setOnClickListener(v->CustomPaletteEditor.show(this));
        root.addView(createColors,new LinearLayout.LayoutParams(-1,Ui.dp(this,58)));
        Button smartSettings=Ui.button(this,"AKILLI RENK · HASSASİYET / EŞİK",false);
        smartSettings.setOnClickListener(v->SmartColorDialog.show(this,null));
        root.addView(smartSettings,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        String[] n={"GÖRÜNÜM","AKILLI ANOMALİ RENKLERİ","RENK OLUŞTUR","IZGARA","SESLİ YÖNLENDİRME","PROFESYONEL KULLANIM KILAVUZU"};
        String[] d={"3D/4D • saydam doğal zemin • keskin anomaliler","Hassasiyet • mavi/mineral/kırmızı eşikleri • renk sertliği","İsim ver • 10 renk seç • kaydet • 2D/3D/4D/GMR","X/Y hücre • adım • başlangıç yönü","Türkçe sıra ve hücre uyarıları","Cihaz hazırlığı, tarama, verileri okuma ve raporlama"};
        for(int i=0;i<n.length;i++){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,18),Ui.dp(this,15),Ui.dp(this,18),Ui.dp(this,15));card.setBackground(Ui.bg(Color.rgb(16,29,39),14,this));
            TextView a=Ui.text(this,n[i],19,Color.WHITE);a.setTypeface(null,1);Ui.fitText(a,15,20,1);
            TextView s=Ui.text(this,d[i],13,Color.rgb(150,176,193));s.setSingleLine(false);Ui.fitText(s,11,14,2);
            card.addView(a,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,34)));
            card.addView(s,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
            final int k=i;card.setOnClickListener(v->{if(k==0)viewDialog();else if(k==1)SmartColorDialog.show(this,null);else if(k==2)CustomPaletteEditor.show(this);else if(k==3)gridDialog();else if(k==4)voiceDialog();else if(k==5)startActivity(new android.content.Intent(this,UserGuideActivity.class));});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,104));cp.setMargins(0,Ui.dp(this,6),0,Ui.dp(this,6));body.addView(card,cp);
        }
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));setContentView(root);Ui.applySafeInsets(this,root);
    }

    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);return e;}
    private void viewDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);EditText gain=field("3D yükseklik kazancı",String.valueOf(Prefs.heightGain(this)));gain.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox smooth=new CheckBox(this);smooth.setText("Yumuşatma");smooth.setTextColor(Color.WHITE);smooth.setChecked(Prefs.smoothing(this));CheckBox grid=new CheckBox(this);grid.setText("3D wireframe / ızgara");grid.setTextColor(Color.WHITE);grid.setChecked(Prefs.grid(this));l.addView(gain);l.addView(smooth);l.addView(grid);
        TextView opacityLabel=Ui.text(this,"4D doğal zemin görünürlüğü: %"+Math.round(Prefs.groundOpacity(this)*100),13,Color.WHITE);
        SeekBar opacitySeek=new SeekBar(this);opacitySeek.setMax(33);opacitySeek.setProgress(Math.round((Prefs.groundOpacity(this)-.02f)*100f));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean from){opacityLabel.setText("4D doğal zemin görünürlüğü: %"+(v+2));}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });l.addView(opacityLabel);l.addView(opacitySeek);
        TextView sharpLabel=Ui.text(this,"4D anomali keskinliği: "+Prefs.anomalySharpness(this)+"x",13,Color.WHITE);
        SeekBar sharpSeek=new SeekBar(this);sharpSeek.setMax(18);sharpSeek.setProgress(Math.round((Prefs.anomalySharpness(this)-.70f)*10f));
        sharpSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean from){sharpLabel.setText(String.format(java.util.Locale.US,"4D anomali keskinliği: %.1fx",.70f+v*.1f));}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });l.addView(sharpLabel);l.addView(sharpSeek);
        ScrollView viewScroll=new ScrollView(this);viewScroll.addView(l);
        new AlertDialog.Builder(this).setTitle("Görünüm").setView(viewScroll).setPositiveButton("Uygula",(d,w)->{try{Prefs.setView(this,Float.parseFloat(gain.getText().toString().replace(',','.')),smooth.isChecked(),grid.isChecked());
            Prefs.setVolumeVisuals(this,.02f+opacitySeek.getProgress()/100f,.70f+sharpSeek.getProgress()/10f);}catch(Exception e){Toast.makeText(this,"Değeri kontrol edin",Toast.LENGTH_SHORT).show();}}).setNegativeButton("İptal",null).show();
    }
    private void gridDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        EditText x=field("X sütun sayısı",String.valueOf(Prefs.cols(this))),y=field("Y satır sayısı",String.valueOf(Prefs.rows(this))),s=field("Adım cm",String.valueOf(Prefs.stepCm(this)));for(EditText e:new EditText[]{x,y,s})e.setInputType(InputType.TYPE_CLASS_NUMBER);l.addView(x);l.addView(y);l.addView(s);
        Spinner start=new Spinner(this);start.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Sol alt köşeden başla","Sağ alt köşeden başla"}));start.setSelection(Prefs.startCorner(this));l.addView(start);
        Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Zikzak tarama","Paralel tarama"}));mode.setSelection(Prefs.serpentine(this)?0:1);l.addView(mode);
        new AlertDialog.Builder(this).setTitle("Izgara").setView(l).setPositiveButton("Uygula",(d,w)->{try{Prefs.setGrid(this,Integer.parseInt(y.getText().toString()),Integer.parseInt(x.getText().toString()),Integer.parseInt(s.getText().toString()),start.getSelectedItemPosition(),mode.getSelectedItemPosition()==0);}catch(Exception e){Toast.makeText(this,"Değerleri kontrol edin",Toast.LENGTH_SHORT).show();}}).setNegativeButton("İptal",null).show();
    }
    private void voiceDialog(){
        CheckBox on=new CheckBox(this);on.setText("Türkçe sesli yönlendirme açık");on.setTextColor(Color.WHITE);on.setChecked(Prefs.voiceGuide(this));on.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,10));
        new AlertDialog.Builder(this).setTitle("Sesli yönlendirme").setView(on).setMessage("Çekim başında sıra numarası, her ölçümde nokta numarası, sıra sonunda 'sıra bitti, yan sıraya geçiniz' uyarısı verir.").setPositiveButton("Uygula",(d,w)->Prefs.setVoiceGuide(this,on.isChecked())).setNegativeButton("İptal",null).show();
    }
}
