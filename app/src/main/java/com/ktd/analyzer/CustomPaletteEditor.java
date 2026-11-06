package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;

/** Ayarlar > Renk Olustur: on durakli isimli renk paleti. */
public final class CustomPaletteEditor {
    private CustomPaletteEditor(){}
    public static void show(Activity ctx){show(ctx,null);}
    public static void show(Activity ctx,Runnable onSaved){
        final int[] stops={
            Color.rgb(0,28,125), Color.rgb(0,60,220), Color.rgb(0,135,255),
            Color.rgb(0,210,240), Color.rgb(48,171,145), Color.rgb(80,150,85),
            Color.rgb(210,196,45), Color.rgb(255,215,0), Color.rgb(255,119,5),
            Color.rgb(245,24,28)
        };
        LinearLayout body=new LinearLayout(ctx);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(22,12,22,12);
        TextView nameTitle=new TextView(ctx);nameTitle.setText("PALET ADI");nameTitle.setTextColor(Color.WHITE);nameTitle.setTextSize(16);body.addView(nameTitle);
        EditText name=new EditText(ctx);name.setSingleLine(true);name.setHint("Palet ismi (örn. KTD Özel 1)");name.setTextColor(Color.WHITE);name.setHintTextColor(Color.LTGRAY);body.addView(name);
        TextView info=new TextView(ctx);info.setText("10 kategori için görünüm renklerini seç. Bunlar ölçülen sinyal düzeylerine karşılık gelen palet etiketleridir; cihazın metalleri veya boşlukları kesin tanıdığı anlamına gelmez.");info.setTextColor(Color.WHITE);info.setTextSize(14);body.addView(info);
        LinearLayout bar=new LinearLayout(ctx);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setMinimumHeight(dp(ctx,34));body.addView(bar);
        LinearLayout list=new LinearLayout(ctx);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        // Siralama, 10 durakli sinyal paletinin soldan saga renk sirasidir.
        // Bunlar yalnizca kullanici etiketleridir; otomatik malzeme teshisi yapilmaz.
        final String[] labels={"1 · Metal", "2 · Değerli metal", "3 · Değersiz metal",
            "4 · Boşluk", "5 · Mineral", "6 · Yapı", "7 · Doğal zemin",
            "8 · Sert zemin", "9 · Küp", "10 · Toprak"};
        final Button[] buttons=new Button[10];
        Runnable update=()->{
            bar.removeAllViews();
            for(int color:stops){TextView t=new TextView(ctx);t.setBackgroundColor(color);bar.addView(t,new LinearLayout.LayoutParams(0,dp(ctx,34),1));}
            for(int i=0;i<10;i++)if(buttons[i]!=null){buttons[i].setText(labels[i]+"   #"+String.format(java.util.Locale.US,"%06X",stops[i]&0xffffff));buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(stops[i]));buttons[i].setTextColor((Color.red(stops[i])*299+Color.green(stops[i])*587+Color.blue(stops[i])*114)/1000>155?Color.BLACK:Color.WHITE);}
        };
        for(int i=0;i<10;i++){
            final int k=i;Button b=new Button(ctx);buttons[i]=b;list.addView(b,new LinearLayout.LayoutParams(-1,dp(ctx,49)));
            b.setOnClickListener(v->chooseColor(ctx,labels[k],stops[k],color->{stops[k]=color;update.run();}));
        }
        update.run();
        TextView existing=new TextView(ctx);existing.setTextColor(Color.WHITE);existing.setPadding(0,dp(ctx,12),0,0);
        body.addView(existing);
        Runnable refresh=()->{ArrayList<CustomPaletteStore.Item> entries=CustomPaletteStore.load(ctx);StringBuilder sb=new StringBuilder("Kayıtlı paletler ("+entries.size()+"/"+CustomPaletteStore.MAX+"):");for(CustomPaletteStore.Item item:entries)sb.append("\n• ").append(item.name);existing.setText(sb.toString());};refresh.run();
        ScrollView scroll=new ScrollView(ctx);scroll.addView(body);
        AlertDialog dlg=new AlertDialog.Builder(ctx).setTitle("10 KATEGORİ RENK OLUŞTUR").setView(scroll)
            .setPositiveButton("KAYDET",null).setNeutralButton("SİL",null).setNegativeButton("KAPAT",null).create();
        dlg.setOnShowListener(d->{
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String n=name.getText().toString().trim();
                if(n.isEmpty()){name.setError("Renk paletine isim verin");return;}
                if(!CustomPaletteStore.save(ctx,n,stops)){Toast.makeText(ctx,"Aynı isim zaten var veya 12 palet sınırına ulaşıldı",Toast.LENGTH_LONG).show();return;}
                com.ktd.analyzer.view.ColorMaps.configure(ctx);
                int selected=CustomPaletteStore.BASE+CustomPaletteStore.load(ctx).size()-1;
                Prefs.setPalette(ctx,selected);
                if(onSaved!=null)onSaved.run();
                Toast.makeText(ctx,"'"+n+"' kaydedildi. 2D / 3D / 4D / GMR paletlerinde seçilebilir.",Toast.LENGTH_LONG).show();dlg.dismiss();
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->manage(ctx,refresh));
        });dlg.show();
    }
    private interface ColorCallback{void onColor(int color);}
    private static void chooseColor(Activity ctx,String title,int initial,ColorCallback cb){
        LinearLayout root=new LinearLayout(ctx);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(ctx,18),dp(ctx,12),dp(ctx,18),dp(ctx,12));
        TextView preview=new TextView(ctx);preview.setGravity(Gravity.CENTER);preview.setTextSize(17);preview.setMinimumHeight(dp(ctx,48));root.addView(preview);
        final int[] vals={Color.red(initial),Color.green(initial),Color.blue(initial)};
        Runnable update=()->{int col=Color.rgb(vals[0],vals[1],vals[2]);preview.setBackgroundColor(col);preview.setText(String.format(java.util.Locale.US,"#%06X",col&0xffffff));preview.setTextColor((vals[0]*299+vals[1]*587+vals[2]*114)/1000>155?Color.BLACK:Color.WHITE);};
        String[] channels={"Kırmızı (R)","Yeşil (G)","Mavi (B)"};
        for(int i=0;i<3;i++){
            final int channel=i;TextView label=new TextView(ctx);label.setText(channels[i]+": "+vals[i]);root.addView(label);
            SeekBar seek=new SeekBar(ctx);seek.setMax(255);seek.setProgress(vals[i]);root.addView(seek);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean fromUser){vals[channel]=progress;label.setText(channels[channel]+": "+progress);update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        }update.run();
        new AlertDialog.Builder(ctx).setTitle(title).setView(root).setPositiveButton("BU RENGİ SEÇ",(d,w)->cb.onColor(Color.rgb(vals[0],vals[1],vals[2]))).setNegativeButton("İPTAL",null).show();
    }
    private static void manage(Activity ctx,Runnable refresh){
        ArrayList<CustomPaletteStore.Item> items=CustomPaletteStore.load(ctx);
        if(items.isEmpty()){Toast.makeText(ctx,"Henüz kayıtlı palet yok",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[items.size()];for(int i=0;i<items.size();i++)names[i]=items.get(i).name;
        new AlertDialog.Builder(ctx).setTitle("Silinecek paleti seç").setItems(names,(d,idx)->new AlertDialog.Builder(ctx).setMessage("'"+names[idx]+"' silinsin mi?")
            .setPositiveButton("SİL",(dd,w)->{
                int selected=Prefs.palette(ctx),deleted=CustomPaletteStore.BASE+idx;
                if(CustomPaletteStore.delete(ctx,deleted)){
                    if(selected==deleted)Prefs.setPalette(ctx,0);
                    else if(selected>deleted)Prefs.setPalette(ctx,selected-1);
                    com.ktd.analyzer.view.ColorMaps.configure(ctx);refresh.run();
                    Toast.makeText(ctx,"Palet silindi",Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("VAZGEÇ",null).show()).setNegativeButton("KAPAT",null).show();
    }
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
}
