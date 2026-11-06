package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.ktd.analyzer.view.ColorMaps;

/** Tek ortak goruntuleme ayari: 2D, 3D, 4D ve GMR canli. */
public final class SmartColorDialog {
    private SmartColorDialog(){}
    public static void show(Activity a,Runnable onApply){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        int pad=Ui.dp(a,16);body.setPadding(pad,pad,pad,pad);
        TextView note=Ui.text(a,"Sinyal tabanlı görsel vurgudur; kesin metal/boşluk/mineral teşhisi değildir. Ham ölçüm ve derinlik değişmez.",13,Color.rgb(185,207,216));
        body.addView(note);
        TextView sen=Ui.text(a,"Hassasiyet",14,Color.WHITE);body.addView(sen);
        Spinner sens=new Spinner(a);sens.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"Düşük","Orta","Yüksek"}));sens.setSelection(Prefs.smartSensitivity(a));body.addView(sens);
        TextView tr=Ui.text(a,"Renk geçişi",14,Color.WHITE);body.addView(tr);
        Spinner transition=new Spinner(a);transition.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"Yumuşak","Normal","Sert"}));transition.setSelection(Prefs.smartTransition(a));body.addView(transition);
        SeekBar blue=slider(a,body,"Mavi (negatif) eşik",Prefs.smartBlueThreshold(a));
        SeekBar mineral=slider(a,body,"Mineral benzeri eşik",Prefs.smartMineralThreshold(a));
        SeekBar red=slider(a,body,"Kırmızı (pozitif) eşik",Prefs.smartRedThreshold(a));
        ScrollView scroll=new ScrollView(a);scroll.addView(body);
        new AlertDialog.Builder(a).setTitle("AKILLI ANOMALİ RENK AYARLARI")
            .setView(scroll)
            .setPositiveButton("UYGULA",(d,w)->{
                if(red.getProgress()<mineral.getProgress()+5){
                    Toast.makeText(a,"Kırmızı eşik mineral eşiğinin en az %5 üstünde olmalı",Toast.LENGTH_LONG).show();
                    return;
                }
                Prefs.setSmartColors(a,sens.getSelectedItemPosition(),transition.getSelectedItemPosition(),
                    blue.getProgress(),mineral.getProgress(),red.getProgress());
                ColorMaps.configure(a);
                if(onApply!=null)onApply.run();
                Toast.makeText(a,"Akıllı renk ayarları kaydedildi",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("İPTAL",null).show();
    }
    private static SeekBar slider(Activity a,LinearLayout body,String title,int current){
        TextView value=Ui.text(a,title+": %"+current,13,Color.WHITE);body.addView(value);
        SeekBar bar=new SeekBar(a);bar.setMax(95);bar.setProgress(current);body.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){value.setText(title+": %"+p);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });return bar;
    }
}
