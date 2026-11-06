package com.ktd.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.ktd.analyzer.data.CsvUtil;
import com.ktd.analyzer.data.MeasurementFileReader;
import com.ktd.analyzer.data.PreviewUtil;
import com.ktd.analyzer.data.ScanData;
import com.ktd.analyzer.data.MagneticDepthEstimator;
import com.ktd.analyzer.data.ScanSessionStore;
import com.ktd.analyzer.data.NeutralExperimentalDepth;
import com.ktd.analyzer.data.ReferenceInverseDepth;
import com.ktd.analyzer.data.V3dImporter;
import com.ktd.analyzer.view.AxisOverlayView;
import com.ktd.analyzer.view.ColorMaps;
import com.ktd.analyzer.view.ColorScaleView;
import com.ktd.analyzer.view.PaletteStripView;
import com.ktd.analyzer.view.ProSurfaceGLView;
import com.ktd.analyzer.view.Volume4DView;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;

public final class AnalysisActivity extends Activity {
    private static final int OPEN=8101,SAVE=8102;
    private ScanData data;
    private ScanData visualData;
    private float anomalyGain=1.0f;
    private float volumeSpread=1.0f;
    private int volumeLayers=2;
    private TextView gainLabel;
    private SeekBar gainSeek;
    private FrameLayout host;
    private ProSurfaceGLView surface;
    private Volume4DView volume4d;
    private ColorScaleView scale;
    private LinearLayout levelControls,isoControls;
    private TextView levelLabel,spreadLabel,layerLabel,thresholdLabel,neutralInfo,depthHeadline,depthDetail;
    private SeekBar levelSeek;
    private Button mode3Button,mode4Button;
    private MagneticDepthEstimator.Result lastDepthResult;
    private boolean depthCalculating=false;
    private int depthAnalysisSequence=0;
    private int palette;
    private float depthSliceMeters=0f;
    // Sadece sahne cetveli: dosyadaki X/Y/Z/C ve derinlik hesaplamasi degismez.
    private float displayDepthMeters=5f;
    private TextView autoDepthRangeLabel;
    private float volumeLevel=.35f;
    private boolean showing4D=false;
    private Button autoReport;
    private AxisOverlayView currentAxes;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        data=ScanSessionStore.current;
        if(data==null){
            data=new ScanData(Prefs.rows(this),Prefs.cols(this));
            data.stepCm=Prefs.stepCm(this);
            data.startCorner=Prefs.startCorner(this);
            data.serpentine=Prefs.serpentine(this);
        }
        palette=Prefs.palette(this);
        anomalyGain=3.0f;
        volumeSpread=Prefs.volumeSpread(this);
        volumeLayers=Prefs.volumeLayers(this);
        volumeLevel=Prefs.volumeThreshold(this);
        data.depthCalibrationFactor=1f;
        NeutralExperimentalDepth.apply(data);
        build();showSurface();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(7,16,24));
        FrameLayout top=new FrameLayout(this);top.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);top.setBackgroundColor(Color.rgb(9,21,29));
        TextView title=Ui.title(this,"KTD 3D ANALYZER");title.setGravity(Gravity.CENTER);
        title.setOnLongClickListener(v->{showVerificationMenu();return true;});
        FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,56));tlp.setMargins(Ui.dp(this,58),0,Ui.dp(this,58),0);top.addView(title,tlp);
        Button back=Ui.button(this,"‹",false);back.setOnClickListener(v->finish());FrameLayout.LayoutParams blp=new FrameLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,42),Gravity.END|Gravity.CENTER_VERTICAL);top.addView(back,blp);root.addView(top,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,56)));

        scale=new ColorScaleView(this);scale.setPalette(palette);root.addView(scale,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,18)));
        // Önceden gösterilen uzun analiz açıklaması kaldırıldı.
        neutralInfo=null;

        LinearLayout modes=new LinearLayout(this);modes.setGravity(Gravity.CENTER);
        mode3Button=Ui.button(this,"3D",true);mode4Button=Ui.button(this,"4D",false);
        mode3Button.setOnClickListener(v->showSurface());mode4Button.setOnClickListener(v->show4D());
        Button settingsButton=Ui.button(this,"GÖRÜNÜM",false);Ui.fitText(settingsButton,10,13,1);
        settingsButton.setOnClickListener(v->showSceneSettings());
        // KAMERA button removed: camera rotation remains available by touch.
        modes.addView(mode3Button,Ui.weight(1f));modes.addView(mode4Button,Ui.weight(1f));
        modes.addView(settingsButton,Ui.weight(.95f));
        Button smartColors=Ui.button(this,"AKILLI RENK",true);
        Ui.fitText(smartColors,9,12,2);
        smartColors.setOnClickListener(v->SmartColorDialog.show(this,()->{
            scale.setPalette(palette);
            if(surface!=null)surface.setPalette(palette);
            if(volume4d!=null)volume4d.setPalette(palette);
        }));
        modes.addView(smartColors,Ui.weight(1.25f));
        autoReport=Ui.button(this,"ANALİZ ET",true);autoReport.setOnClickListener(v->{ScanSessionStore.current=data;startActivity(new Intent(this,AutoAnalysisActivity.class));});
        root.addView(modes,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));

        root.addView(autoReport,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));
        autoReport.setVisibility(View.GONE);
        depthHeadline=null;depthDetail=null;

        host=new FrameLayout(this);root.addView(host,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));

        root.addView(buildDepthRangeControls(),new LinearLayout.LayoutParams(-1,Ui.dp(this,49)));

        // Ortak derinlik katmanı sürgüsü.
        // Kullanıcı seçilen metre seviyesinde 3D/4D görseli aşağı-yukarı gezdirir.
        levelControls=buildLevelControls();
        root.addView(levelControls,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,56)));
        // Kazanç en yüksek görünümde sabit; ayrı satır kaldırıldı.
        setGain(3.0f);

        isoControls=buildIsoControls();isoControls.setVisibility(View.GONE);
        root.addView(isoControls,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,116)));

        HorizontalScrollView ps=new HorizontalScrollView(this);ps.setHorizontalScrollBarEnabled(false);PaletteStripView palettes=new PaletteStripView(this);
        palettes.setListener(p->{palette=p;Prefs.setPalette(this,p);scale.setPalette(p);if(surface!=null)surface.setPalette(p);if(volume4d!=null)volume4d.setPalette(p);});
        ps.addView(palettes,new HorizontalScrollView.LayoutParams(Ui.dp(this,PaletteStripView.entryCount()*112),Ui.dp(this,52)));
        // 3D ve 4D ortak renk ayar butonu ust mod seciminde sabit durur.
        root.addView(ps,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        // CSV/V3D açma ve CSV kayıt butonları görüntüleme ekranından kaldırıldı.addBottom(actions,"PAYLAŞ",v->shareDialog());
        root.addView(actions,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,62)));

        setContentView(root);Ui.applySafeInsets(this,root);updateDepthLabel();refreshNeutralInfo();refreshDepthDisplay();
    }

    private void addBottom(LinearLayout actions,String text,View.OnClickListener l){
        Button b=Ui.button(this,text,text.equals("PAYLAŞ"));b.setOnClickListener(l);Ui.fitText(b,10,14,1);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);bp.setMargins(Ui.dp(this,3),Ui.dp(this,4),Ui.dp(this,3),Ui.dp(this,6));actions.addView(b,bp);
    }

    /** 3D ve 4D icin ortak otomatik cetvel (en fazla 20 m).
     * Ham veriye ve ReferenceMeterModel / MagneticDepthEstimator formullerine dokunmaz.
     * Tarama yoksa 0-5 m baslangic cetveli gosterilir.
     */
    private LinearLayout buildDepthRangeControls(){
        LinearLayout bar=new LinearLayout(this);
        bar.setPadding(Ui.dp(this,10),Ui.dp(this,3),Ui.dp(this,10),Ui.dp(this,3));
        bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(Color.rgb(10,24,32));
        TextView label=Ui.text(this,"OTOMATİK DERİNLİK",12,Color.WHITE);
        Ui.fitText(label,10,12,1);
        bar.addView(label,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1));
        autoDepthRangeLabel=Ui.text(this,"0–5 m",14,Color.rgb(97,211,247));
        autoDepthRangeLabel.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        bar.addView(autoDepthRangeLabel,new LinearLayout.LayoutParams(Ui.dp(this,110),Ui.dp(this,38)));
        return bar;
    }

    private float automaticDepthRange(){
        float detected=0f;
        // Orijinal cihaz derinligi mevcutsa goz ardi edilmez.
        if(data!=null&&data.hasDepthData())detected=Math.max(detected,data.maxDepthMeters());
        // Halihazirda kullanilan 3D/4D hesaplamasi: sadece okunur.
        if(visualData!=null&&visualData.hasDepthData())detected=Math.max(detected,visualData.maxDepthMeters());
        // Mevcut hesaplamanin urettiği tahmin varsa buna da bakilir.
        if(lastDepthResult!=null&&lastDepthResult.hasEstimate())
            detected=Math.max(detected,(float)lastDepthResult.depthMeters);
        if(!Float.isFinite(detected)||detected<=0f)return 5f;
        // Cetvel maksimum 20 m; goruntuleme araligi tam metreye yukari yuvarlanir.
        return Math.max(1f,Math.min(20f,(float)Math.ceil(detected)));
    }

    private void applyDisplayRange(){
        float next=automaticDepthRange();
        boolean changed=Math.abs(next-displayDepthMeters)>.001f;
        displayDepthMeters=next;
        if(autoDepthRangeLabel!=null)
            autoDepthRangeLabel.setText(String.format(Locale.forLanguageTag("tr-TR"),"0–%.0f m",displayDepthMeters));
        if(surface!=null)surface.setDisplayDepthRange(displayDepthMeters);
        if(volume4d!=null)volume4d.setDisplayDepthRange(displayDepthMeters);
        if(currentAxes!=null)currentAxes.setDisplayDepthRange(displayDepthMeters);
        // Sahne yalniz aralik degisince yeniden sigdirilir; kullanicinin zoom'u korunur.
        if(changed){
            if(showing4D&&volume4d!=null)volume4d.resetCamera();
            else if(!showing4D&&surface!=null)surface.resetCamera();
        }
    }

    private LinearLayout buildLevelControls(){
        LinearLayout all=new LinearLayout(this);all.setGravity(Gravity.CENTER_VERTICAL);
        all.setPadding(Ui.dp(this,10),Ui.dp(this,2),Ui.dp(this,10),Ui.dp(this,2));
        all.setBackgroundColor(Color.rgb(10,24,32));
        levelLabel=Ui.text(this,"Derinlik: Tümü",12,Color.WHITE);
        Ui.fitText(levelLabel,10,13,1);
        levelSeek=new SeekBar(this);levelSeek.setMax(100);levelSeek.setProgress(0);
        levelSeek.setOnSeekBarChangeListener(simple(p->{
            boolean available=visualData!=null&&visualData.hasDepthData();
            float maxDepth=available?visualData.maxDepthMeters():hasModelDepth()?modelMaxDepth():0f;
            // Surgu, ters sinyalden turetilen C indeksinde fiziksel metre degildir.
            depthSliceMeters=(!available||p==0)?0f:Math.min(maxDepth,Math.max(.5f,Math.round((maxDepth*p/100f)*2f)/2f));
            if(showing4D){
                if(volume4d!=null)volume4d.setDepthFocusMeters(depthSliceMeters);
            }else{
                if(surface!=null)surface.setDepthSliceMeters(depthSliceMeters);
            }
            updateDepthLabel();
        }));
        all.addView(levelLabel,new LinearLayout.LayoutParams(Ui.dp(this,182),Ui.dp(this,38)));
        all.addView(levelSeek,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1));
        return all;
    }

    private void updateDepthLabel(){
        boolean available=visualData!=null&&visualData.hasDepthData();
        boolean derived=visualData!=null&&visualData.rulerDerivedDepth;
        boolean modelReady=hasModelDepth();
        if(levelSeek!=null)levelSeek.setEnabled(available);
        if(levelLabel==null)return;
        if(!available&&!modelReady){levelLabel.setText("Z: goreli hacim");return;}
        if(depthSliceMeters<=0f){levelLabel.setText("Derinlik: Tümü");return;}
        levelLabel.setText(String.format(Locale.forLanguageTag("tr-TR"),"Derinlik: %.1f m",depthSliceMeters));
    }

    private boolean hasModelDepth(){
        return data!=null&&!data.hasDepthData() && (visualData==null||!visualData.hasDepthData()) &&lastDepthResult!=null&&lastDepthResult.hasEstimate();
    }
    private float modelMaxDepth(){
        return hasModelDepth()?Math.max(.5f,(float)(lastDepthResult.highMeters*1.10)):Float.NaN;
    }
    /** Model sonucunu yalnız ekrana uygula. Orijinal C/depth ve CSV değerleri değişmez. */
    private void applyModelToScene(){
        float m=hasModelDepth()?(float)lastDepthResult.depthMeters:Float.NaN;
        float mx=modelMaxDepth();
        if(surface!=null)surface.setModelDepthMeters(m,mx);
        if(volume4d!=null)volume4d.setModelDepthMeters(Float.NaN,Float.NaN);
        if(currentAxes!=null)currentAxes.setModelDepthMeters(Float.NaN,Float.NaN);
        updateDepthLabel();
    }

    /** Göreli anomali = yalnız gösterim kazancı; ham Z, CSV ve tahmini metre değişmez. */
    private LinearLayout buildGainControls(){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);
        line.setBackgroundColor(Color.rgb(10,24,32));
        gainLabel=Ui.text(this,"Kazanç: 1.0×",12,Color.WHITE);Ui.fitText(gainLabel,10,13,1);
        gainSeek=new SeekBar(this);gainSeek.setMax(28);
        gainSeek.setProgress(Math.round((anomalyGain-.2f)*10f));
        gainSeek.setOnSeekBarChangeListener(simple(progress->{
            setGain(.2f+.1f*progress);
        }));
        line.addView(gainLabel,new LinearLayout.LayoutParams(Ui.dp(this,182),Ui.dp(this,40)));
        line.addView(gainSeek,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));
        setGain(anomalyGain);
        return line;
    }
    private void setGain(float g){
        anomalyGain=Math.max(.2f,Math.min(3f,g));
        if(gainLabel!=null)gainLabel.setText(String.format(Locale.forLanguageTag("tr-TR"),"Kazanç: %.1f×",anomalyGain));
        Prefs.setAnomalyGain(this,anomalyGain);
        if(surface!=null)surface.setDisplayGain(anomalyGain);
        if(volume4d!=null)volume4d.setThreshold(Math.max(.03f,Math.min(.92f,volumeLevel/anomalyGain)));
    }
    /** Ayarlar içindeki Görünüm, 3D ve 4D'nin içinden de değiştirilebilir. */
    private void showSceneSettings(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,4));
        CheckBox smooth=new CheckBox(this);smooth.setText("3D ve 4D yumuşatma");smooth.setTextColor(Color.WHITE);smooth.setChecked(Prefs.smoothing(this));
        CheckBox grid=new CheckBox(this);grid.setText("3D ızgara / 4D çerçeve");grid.setTextColor(Color.WHITE);grid.setChecked(Prefs.grid(this));
        panel.addView(smooth);panel.addView(grid);

        TextView opacityTitle=Ui.text(this,String.format(Locale.US,"Doğal zemin görünürlüğü: %d",Math.round(Prefs.groundOpacity(this)*100)),13,Color.WHITE);
        SeekBar opacitySeek=new SeekBar(this);opacitySeek.setMax(33);opacitySeek.setProgress(Math.round((Prefs.groundOpacity(this)-.02f)*100f));
        opacitySeek.setOnSeekBarChangeListener(simple(v->opacityTitle.setText("Doğal zemin görünürlüğü: %"+(v+2))));
        panel.addView(opacityTitle);panel.addView(opacitySeek);
        TextView sharpTitle=Ui.text(this,String.format(Locale.US,"Anomali keskinliği: %.1fx",Prefs.anomalySharpness(this)),13,Color.WHITE);
        SeekBar sharpSeek=new SeekBar(this);sharpSeek.setMax(18);sharpSeek.setProgress(Math.round((Prefs.anomalySharpness(this)-.70f)*10f));
        sharpSeek.setOnSeekBarChangeListener(simple(v->sharpTitle.setText(String.format(Locale.US,"Anomali keskinliği: %.1fx",.70f+v*.1f))));
        panel.addView(sharpTitle);panel.addView(sharpSeek);
        TextView volTitle=Ui.text(this,"4D katman: "+volumeLayers,13,Color.WHITE);
        SeekBar layers=new SeekBar(this);layers.setMax(5);layers.setProgress(volumeLayers-1);
        layers.setOnSeekBarChangeListener(simple(v->volTitle.setText("4D katman: "+(v+1))));
        panel.addView(volTitle);panel.addView(layers);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.setFillViewport(false);scroll.addView(panel);
        new AlertDialog.Builder(this).setTitle("3D / 4D Görünüm")
            .setView(scroll).setPositiveButton("Uygula",(d,w)->{
                Prefs.setView(this,Prefs.heightGain(this),smooth.isChecked(),grid.isChecked());
                Prefs.setVolumeVisuals(this,.02f+opacitySeek.getProgress()/100f,.70f+sharpSeek.getProgress()/10f);
                setGain(3.0f);
                volumeLayers=layers.getProgress()+1;
                Prefs.setVolume(this,volumeLayers,volumeSpread,volumeLevel);
                if(showing4D)show4D();else showSurface();
            }).setNegativeButton("İptal",null).show();
    }

    private LinearLayout buildIsoControls(){
        LinearLayout all=new LinearLayout(this);all.setOrientation(LinearLayout.VERTICAL);
        all.setPadding(Ui.dp(this,10),Ui.dp(this,2),Ui.dp(this,10),Ui.dp(this,2));
        all.setBackgroundColor(Color.rgb(10,24,32));
        spreadLabel=Ui.text(this,"Hacim yayılımı",12,Color.WHITE);Ui.fitText(spreadLabel,10,13,1);
        SeekBar spread=new SeekBar(this);spread.setMax(100);spread.setProgress(Math.round((volumeSpread-.60f)*100));
        spread.setOnSeekBarChangeListener(simple(p->{float sp=.60f+p/100f;volumeSpread=sp;Prefs.setVolume(this,volumeLayers,volumeSpread,volumeLevel);if(volume4d!=null)volume4d.setSpread(sp);}));
        layerLabel=Ui.text(this,"Katman: "+volumeLayers,12,Color.WHITE);Ui.fitText(layerLabel,10,13,1);
        SeekBar layers=new SeekBar(this);layers.setMax(5);layers.setProgress(volumeLayers-1);
        layers.setOnSeekBarChangeListener(simple(p->{int n=p+1;volumeLayers=n;Prefs.setVolume(this,volumeLayers,volumeSpread,volumeLevel);layerLabel.setText("Katman: "+n);if(volume4d!=null)volume4d.setLayers(n);}));
        thresholdLabel=Ui.text(this,"Anomali eşiği: %"+Math.round(volumeLevel*100),12,Color.WHITE);Ui.fitText(thresholdLabel,10,13,1);
        SeekBar thresholdSeek=new SeekBar(this);thresholdSeek.setMax(80);thresholdSeek.setProgress(Math.round((volumeLevel-.05f)*100));
        thresholdSeek.setOnSeekBarChangeListener(simple(p->{
            volumeLevel=.05f+(p/100f);
            Prefs.setVolume(this,volumeLayers,volumeSpread,volumeLevel);
            thresholdLabel.setText("Anomali eşiği: %"+Math.round(volumeLevel*100));
            if(volume4d!=null)volume4d.setThreshold(Math.max(.06f,Math.min(.90f,volumeLevel/anomalyGain)));
        }));
        all.addView(row(thresholdLabel,thresholdSeek));
        all.addView(row(spreadLabel,spread));all.addView(row(layerLabel,layers));return all;
    }
    private LinearLayout row(TextView t,SeekBar s){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(t,new LinearLayout.LayoutParams(Ui.dp(this,145),Ui.dp(this,38)));
        r.addView(s,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1));return r;
    }
    private SeekBar.OnSeekBarChangeListener simple(final Progress c){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){c.on(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}
    private interface Progress{void on(int p);}

    private void styleModeButtons(boolean is4d){
        if(mode3Button!=null){mode3Button.setTextColor(is4d?Color.WHITE:Color.BLACK);mode3Button.setBackground(Ui.bg(is4d?Color.rgb(23,42,54):Color.rgb(35,214,176),10,this));}
        if(mode4Button!=null){mode4Button.setTextColor(is4d?Color.BLACK:Color.WHITE);mode4Button.setBackground(Ui.bg(is4d?Color.rgb(35,214,176):Color.rgb(23,42,54),10,this));}
    }

    private void showSurface(){
        if(surface!=null)surface.onPause();if(volume4d!=null)volume4d.onPause();
        showing4D=false;
        if(autoReport!=null)autoReport.setVisibility(View.GONE);
        visualData=ReferenceInverseDepth.createRelativeIndexVolume(data);
        host.removeAllViews();surface=new ProSurfaceGLView(this);surface.setData(visualData);surface.setPalette(palette);surface.setSmoothing(Prefs.smoothing(this));surface.setWireframe(Prefs.grid(this));surface.setDisplayGain(anomalyGain);
        surface.setMode(ProSurfaceGLView.ADVANCED);surface.setDepthSliceMeters(depthSliceMeters);
        host.addView(surface,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        AxisOverlayView ax=new AxisOverlayView(this);ax.setData(visualData);ax.setVolumeMode(false);surface.setAxisOverlay(ax);
        currentAxes=ax;applyModelToScene();
        applyDisplayRange();
        host.addView(ax,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        volume4d=null;isoControls.setVisibility(View.GONE);styleModeButtons(false);
        if(levelSeek!=null){float md=visualData==null?0f:visualData.maxDepthMeters();levelSeek.setProgress(md<=0f?0:Math.round((depthSliceMeters/md)*100f));updateDepthLabel();}
    }
    private void show4D(){
        if(data==null||data.rows<2||data.cols<2||data.count<4){
            Toast.makeText(this,"4D yüzey için en az 2×2 konumsal ölçüm gerekiyor. 2D/CSV görüntüleme serbesttir.",Toast.LENGTH_LONG).show();
            return;
        }
        if(surface!=null)surface.onPause();if(volume4d!=null)volume4d.onPause();
        showing4D=true;
        if(autoReport!=null)autoReport.setVisibility(View.VISIBLE);
        visualData=ReferenceInverseDepth.createRelativeIndexVolume(data);
        host.removeAllViews();volume4d=new Volume4DView(this);volume4d.setData(visualData);volume4d.setPalette(palette);
        volume4d.setSmoothing(Prefs.smoothing(this));volume4d.setWireframe(Prefs.grid(this));
        volume4d.setThreshold(Math.max(.035f,Math.min(.20f,volumeLevel/anomalyGain)));volume4d.setDepthFocusMeters(depthSliceMeters);
        volume4d.setSpread(volumeSpread);volume4d.setLayers(volumeLayers);
        volume4d.setOpacity(Prefs.groundOpacity(this));volume4d.setSharpness(Prefs.anomalySharpness(this));
        host.addView(volume4d,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        AxisOverlayView ax=new AxisOverlayView(this);ax.setData(visualData);ax.setVolumeMode(true);volume4d.setAxisOverlay(ax);
        currentAxes=ax;applyModelToScene();
        applyDisplayRange();
        host.addView(ax,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        surface=null;isoControls.setVisibility(View.VISIBLE);styleModeButtons(true);
        if(levelSeek!=null){float md=visualData==null?0f:visualData.maxDepthMeters();
            levelSeek.setProgress(md<=0f?0:Math.round((depthSliceMeters/md)*100f));updateDepthLabel();}
    }

    /** Tarama yüklenir yüklenmez otomatik çalışır; sonuç ekranda sabit kalır. */
    private void runDepthAnalysis(){
        if(data==null||data.count==0){
            depthAnalysisSequence++;
            lastDepthResult=null;depthCalculating=false;refreshDepthDisplay();return;
        }
        final ScanData input=data;
        final int seq=++depthAnalysisSequence;
        depthCalculating=true;
        refreshDepthDisplay();
        new Thread(()->{
            final MagneticDepthEstimator.Result result=MagneticDepthEstimator.analyze(input);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||data!=input||seq!=depthAnalysisSequence)return;
                depthCalculating=false;
                lastDepthResult=result;
                refreshDepthDisplay();
                refreshNeutralInfo();
                applyModelToScene();
                applyDisplayRange();
            });
        },"ktd-derinlik-tahmini").start();
    }

    /** Kazı kaydı, açık C kanalı ve model tahmini birbirine karıştırılmaz. */
    private void refreshDepthDisplay(){
        if(depthHeadline==null||depthDetail==null)return;
        if(data==null||data.count==0){
            depthHeadline.setText("DERİNLİK: TARAMA BEKLENİYOR");
            depthDetail.setText("Önce tarama yap veya CSV / V3D aç.");
            return;
        }
        final Locale tr=Locale.forLanguageTag("tr-TR");
        if(data.hasVerifiedSiteRange()){
            depthHeadline.setText(String.format(tr,"KAZI: %.2f–%.2f m",data.verifiedSiteMinMeters,data.verifiedSiteMaxMeters));
            if(lastDepthResult!=null&&lastDepthResult.hasEstimate())
                depthDetail.setText(String.format(tr,"Kazı kaydı • model tahmini %.2f m (ayrı hesap)",lastDepthResult.depthMeters));
            else
                depthDetail.setText("Kazı kaydı • otomatik derinlik: "+(depthCalculating?"hesaplanıyor":"doğrulanamadı"));
            return;
        }
        if(data.fieldVerifiedCount()>0){
            float a=Float.POSITIVE_INFINITY,b=0f;
            for(int r=0;r<data.rows;r++)for(int c=0;c<data.cols;c++){
                float v=data.fieldVerifiedDepth[r][c];if(Float.isFinite(v)){a=Math.min(a,v);b=Math.max(b,v);}
            }
            if(a==b)depthHeadline.setText(String.format(tr,"SAHA NOKTASI: %.2f m",a));
            else depthHeadline.setText(String.format(tr,"KAZI NOKTALARI: %.2f–%.2f m",a,b));
            depthDetail.setText(data.fieldVerifiedCount()+" kazı notu • diğer noktalara genellenmez");
            return;
        }
        if(data.hasDepthData()){
            float min=Float.POSITIVE_INFINITY,max=0f;
            for(int r=0;r<data.rows;r++)for(int c=0;c<data.cols;c++){
                if(!data.filled[r][c])continue;
                float v=data.depth[r][c];if(Float.isFinite(v)){min=Math.min(min,v);max=Math.max(max,v);}
            }
            depthHeadline.setText(min==max?String.format(tr,"C KANALI: %.2f m",min):
                String.format(tr,"C KANALI: %.2f–%.2f m",min,max));
            depthDetail.setText("Cihazın gönderdiği değer • fiziksel doğruluk teyit edilmeli");
            return;
        }
        if(lastDepthResult!=null&&lastDepthResult.hasEstimate()){
            depthHeadline.setText(String.format(tr,data.count<64?"ÖN MODEL: ~%.2f m":"MODEL: ~%.2f m",lastDepthResult.depthMeters));
            depthDetail.setText(String.format(tr,"Koşullu model aralığı %.2f–%.2f m • saha doğrulaması yok",lastDepthResult.lowMeters,lastDepthResult.highMeters));
            return;
        }
        if(depthCalculating){
            depthHeadline.setText("DERİNLİK: HESAPLANIYOR…");
            depthDetail.setText("Yeterli ve geniş alana yayılmış veri olduğunda manyetik model değerlendirilir.");
        }else{
            depthHeadline.setText("DERİNLİK: BELİRLENEMEDİ");
            depthDetail.setText(lastDepthResult==null?"Açık C veya saha bilgisi bulunamadı":lastDepthResult.reason);
        }
    }

    private void showVerificationMenu(){
        new AlertDialog.Builder(this)
            .setTitle("Saha verisi ve kontrol örnekleri")
            .setItems(new String[]{"Gerçek kazı derinliğini kaydet", "Nokta bazında saha notu", "Ereğli örneğini aç (300 nokta CSV)", "Ereğli örneğini aç (160 nokta V3D)"},
                (dlg,which)->{
                    if(which==0)showVerifiedRangeDialog();
                    else if(which==1)showFieldVerificationDialog();
                    else loadEregliSample(which==3);
                }).show();
    }
    private void loadEregliSample(boolean v3d){
        try(java.io.InputStream in=getAssets().open(v3d?"samples/eregli_original.v3d":"samples/eregli_original.csv")){
            ScanData sample=v3d?V3dImporter.read(in,30):CsvUtil.readStream(in);
            sample.name=v3d?"EREGLI_SAHA_V3D":"EREGLI_SAHA_CSV";
            sample.setVerifiedSiteRange(8.50f,8.95f,"Kullanıcının kazı ile bildirdiği 8,50–8,95 m saha aralığı");
            NeutralExperimentalDepth.apply(sample);
            data=sample;lastDepthResult=null;depthSliceMeters=0;
            ScanSessionStore.current=data;
            if(levelSeek!=null)levelSeek.setProgress(0);
            if(showing4D)show4D();else showSurface();
            refreshNeutralInfo();
            runDepthAnalysis();
            Toast.makeText(this,"Ereğli saha ölçümü açıldı; kazı derinliği üstte gösteriliyor.",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Toast.makeText(this,"Ereğli dosyası açılamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void showVerifiedRangeDialog(){
        if(data==null||data.count==0){Toast.makeText(this,"Önce tarama aç.",Toast.LENGTH_SHORT).show();return;}
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,8));
        EditText low=new EditText(this),high=new EditText(this),site=new EditText(this);
        low.setHint("Kazıda doğrulanan en az derinlik (m)");
        high.setHint("Kazıda doğrulanan en çok derinlik (m)");
        site.setHint("Saha notu (ör. Ereğli)");
        low.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        high.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(data.hasVerifiedSiteRange()){
            low.setText(String.valueOf(data.verifiedSiteMinMeters));
            high.setText(String.valueOf(data.verifiedSiteMaxMeters));
            site.setText(data.verifiedSiteLabel);
        }
        panel.addView(low);panel.addView(high);panel.addView(site);
        AlertDialog dlg=new AlertDialog.Builder(this)
            .setTitle("Gerçek saha doğrulaması")
            .setMessage("Bu değerler yalnızca bu tarama alanına ait kazıyla doğrulanmış derinliktir. Başka hedeflere otomatik uygulanmaz.")
            .setView(panel).setPositiveButton("Kaydet",null).setNegativeButton("Vazgeç",null).create();
        dlg.setOnShowListener(ignored->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                float a=Float.parseFloat(low.getText().toString().trim().replace(',','.'));
                float b=Float.parseFloat(high.getText().toString().trim().replace(',','.'));
                data.setVerifiedSiteRange(a,b,site.getText().toString());
                refreshNeutralInfo();refreshDepthDisplay();
                Toast.makeText(this,"Saha referansı kaydedildi. CSV ile dışa aktarabilirsin.",Toast.LENGTH_LONG).show();
                dlg.dismiss();
            }catch(Exception e){Toast.makeText(this,"Geçersiz derinlik aralığı",Toast.LENGTH_LONG).show();}
        }));
        dlg.show();
    }

    /** Kullanıcı tarafından kazı/saha ile doğrulanmış metre, sensörün C kanalı gibi işlenmez. */
    private void showFieldVerificationDialog(){
        if(data==null||data.count==0){Toast.makeText(this,"Önce bir tarama açın",Toast.LENGTH_SHORT).show();return;}
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,8));
        EditText line=new EditText(this),point=new EditText(this),meters=new EditText(this);
        line.setHint("Hat numarası (1 - "+data.cols+")");
        point.setHint("İleri nokta (1 - "+data.rows+")");
        meters.setHint("Sahada doğrulanan derinlik (m)");
        line.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        point.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        meters.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(data.importedFromV3d&&data.cols>=4&&data.rows>=12){line.setText("4");point.setText("12");}
        panel.addView(line);panel.addView(point);panel.addView(meters);
        AlertDialog dlg=new AlertDialog.Builder(this)
            .setTitle("Neutral • saha derinliği doğrulaması")
            .setMessage("Bu değer, yalnızca belirtilen noktadaki kazı/saha doğrulama notudur. Diğer noktalara otomatik derinlik veya oda tespiti atamaz.")
            .setView(panel)
            .setPositiveButton("Kaydet",null)
            .setNegativeButton("Vazgeç",null).create();
        dlg.setOnShowListener(ignored->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int li=Integer.parseInt(line.getText().toString().trim());
                int po=Integer.parseInt(point.getText().toString().trim());
                float dep=Float.parseFloat(meters.getText().toString().trim().replace(',','.'));
                data.setFieldVerifiedDepth(li,po,dep);
                NeutralExperimentalDepth.apply(data);
                if(showing4D)show4D();else showSurface();
                refreshNeutralInfo();refreshDepthDisplay();
                Toast.makeText(this,String.format(Locale.US,"Hat %d / nokta %d: %.2f m saha kaydı",li,po,dep),Toast.LENGTH_LONG).show();
                dlg.dismiss();
            }catch(Exception ex){Toast.makeText(this,"Hat, nokta veya metre geçersiz: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dlg.show();
    }

    private void refreshNeutralInfo(){
        if(neutralInfo==null||data==null)return;
        String text;
        if(data.cIsDerivedFromRaw)text="Z derinlik ekseni • 3D anomali";
        else if(data.hasDepthData())text="C kanalı mevcut • fiziksel doğruluk teyit edilmeli";
        else text="HAM ANOMALİ • model metreleri doğrulanmamış";
        int verified=data.fieldVerifiedCount();
        if(verified>0) text+=" • "+verified+" saha doğrulaması";
        if(data.hasVerifiedSiteRange())
            text+=String.format(Locale.forLanguageTag("tr-TR")," • SAHA %.2f–%.2f m",data.verifiedSiteMinMeters,data.verifiedSiteMaxMeters);
        if(lastDepthResult!=null){
            text+=lastDepthResult.hasEstimate()?String.format(Locale.forLanguageTag("tr-TR"),
                " • ÖN MODEL ~%.2f m",lastDepthResult.depthMeters):" • model sonucu belirsiz";
        }
        if(data.suspectedFilledSamples>0)text+=" • "+data.suspectedFilledSamples+" şüpheli kayıt";
        neutralInfo.setText(text+" • Saha / örnek için dokun");
    }

    /** Yalnız daha önce kazıyla doğrulanan birebir aynı Ereğli dosyasına saha etiketi koy. */
    private boolean matchesKnownEregliFile(Uri uri,boolean v3d){
        final String expected=v3d?
            "a08fb5e1943aecfddeb023538426add3194150419310b9797fb0fa6e25253e90":
            "4f34b5539a80f4791a888107801a9b0bab8c74c6215c925d79cf39c142c99d8f";
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)return false;
            java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");
            byte[] block=new byte[8192];int n;
            while((n=in.read(block))!=-1){if(n>0)sha.update(block,0,n);}
            StringBuilder hex=new StringBuilder();
            for(byte b:sha.digest())hex.append(String.format(Locale.US,"%02x",b&0xff));
            return expected.equals(hex.toString());
        }catch(Exception ignored){return false;}
    }

    /** KTD kayıtları uygulamanın özel dizinindedir; Android'in sistem dosya
     * seçicisinde görünmez. İki açma yolu da aynı CsvUtil'i kullanır. */
    private void openCsv(){
        new AlertDialog.Builder(this).setTitle("Tarama dosyası aç")
            .setItems(new String[]{"KTD kayıtlarım (uygulama içi)","Telefonda / tabletteki CSV veya V3D'yi seç", "Telefondaki CSV listesi (tüm erişilebilir kayıtlar)"},
                (dlg,choice)->{if(choice==0)openInternalScans();else if(choice==1)openExternalFile();else{
                Intent i=new Intent(this,RecordsActivity.class);i.putExtra("import_picker",true);startActivity(i);
            }})
            .show();
    }
    private void openExternalFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
        // Depolama izni gerekmez: kullanıcı Files / İndirilenler / Belgeler / Drive konumundan seçer.
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,OPEN);
    }
    private void openInternalScans(){
        File folder=new File(getFilesDir(),"scans");
        File[] scans=folder.listFiles((dir,name)->name.toLowerCase(Locale.ROOT).endsWith(".csv"));
        if(scans==null||scans.length==0){
            new AlertDialog.Builder(this).setTitle("KTD kayıtları")
                .setMessage("Henüz kayıt yok. Tarama ekranındaki Kaydet düğmesi buraya kaydeder.")
                .setPositiveButton("Telefondan seç",(d,w)->openExternalFile())
                .setNegativeButton("Kapat",null).show();return;
        }
        Arrays.sort(scans,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        String[] names=new String[scans.length];
        for(int j=0;j<scans.length;j++)names[j]=scans[j].getName();
        new AlertDialog.Builder(this).setTitle("KTD kayıtlarım ("+scans.length+")")
            .setItems(names,(dlg,index)->{
                try{loadScan(CsvUtil.readFile(scans[index]),"KTD kaydı açıldı");}
                catch(Exception e){Toast.makeText(this,"CSV okunamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            }).setNegativeButton("İptal",null).show();
    }
    private void loadScan(ScanData scan,String status){
        data=scan;depthSliceMeters=0f;lastDepthResult=null;
        NeutralExperimentalDepth.apply(data);
        if(levelSeek!=null)levelSeek.setProgress(0);
        ScanSessionStore.current=data;
        if(showing4D && data.rows>=2&&data.cols>=2&&data.count>=4)show4D();else showSurface();
        refreshNeutralInfo();runDepthAnalysis();
        Toast.makeText(this,status+" • "+data.count+" örnek",Toast.LENGTH_LONG).show();
    }
    private void saveCsv(){
        if(data==null||data.count==0){Toast.makeText(this,"Kayıt yok",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("CSV Kaydet")
            .setItems(new String[]{"KTD kayıtlarıma kaydet","Telefon klasörüne kaydet"},(d,k)->{
                if(k==0){try{File f=CsvUtil.saveNamed(this,data,CsvUtil.safeBaseName(data.name));
                    Toast.makeText(this,"KTD kayıtlarına kaydedildi: "+f.getName(),Toast.LENGTH_LONG).show();}
                    catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}
                else{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/csv");
                    String n=data.name!=null&&!data.name.trim().isEmpty()?data.name:"KTD_TARAMA";
                    i.putExtra(Intent.EXTRA_TITLE,n+".csv");startActivityForResult(i,SAVE);}
            }).show();
    }

    private void shareDialog(){
        if(data==null||data.count==0){Toast.makeText(this,"Paylaşılacak tarama verisi yok",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Paylaş").setItems(new String[]{"PNG Görüntü","CSV Veri","PNG + CSV"},(d,which)->prepareAndShare(which)).show();
    }
    private void prepareAndShare(int which){
        try{
            String base=data.name==null||data.name.trim().isEmpty()?"KTD_ANALIZ_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()):CsvUtil.safeBaseName(data.name);
            File csv=CsvUtil.saveNamed(this,data,base);File png=new File(csv.getParentFile(),csv.getName().replaceFirst("\\.csv$",".png"));
            // Paylaşılan PNG, saydam isosurface ekran görüntüsü değil; normal, opak renk skalalı analiz önizlemesidir.
            PreviewUtil.save3dPng(this,data,palette,png);
            if(which==0)shareOne(png,"image/png");else if(which==1)shareOne(csv,"text/csv");else shareBoth(png,csv);
        }catch(Exception e){Toast.makeText(this,"Paylaşım hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private Uri uriFor(File f){return FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);}
    private void shareOne(File f,String type){
        Intent i=new Intent(Intent.ACTION_SEND);i.setType(type);i.putExtra(Intent.EXTRA_STREAM,uriFor(f));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Paylaş"));
    }
    private void shareBoth(File png,File csv){
        ArrayList<Uri> uris=new ArrayList<>();uris.add(uriFor(png));uris.add(uriFor(csv));Intent i=new Intent(Intent.ACTION_SEND_MULTIPLE);i.setType("*/*");i.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"PNG + CSV paylaş"));
    }

    @Override protected void onActivityResult(int req,int res,Intent i){
        super.onActivityResult(req,res,i);if(res!=RESULT_OK||i==null||i.getData()==null)return;Uri u=i.getData();
        try{
            if(req==OPEN){
                // Dosya turu isimden DEGIL, veri imzasindan belirlenir.
                boolean v3d=MeasurementFileReader.isV3d(this,u);
                data=MeasurementFileReader.read(this,u,Prefs.stepCm(this));
                // Yalnız SHA-256 eşleşmesi halinde, orijinal Ereğli saha kaydını tanı.
                // Başka bir kullanıcının yeni çekimine bu derinlik asla taşınmaz.
                if(matchesKnownEregliFile(u,v3d))
                    data.setVerifiedSiteRange(8.50f,8.95f,"Ereğli • kullanıcı kazısı ile doğrulanan aralık");
                loadScan(data,v3d?"V3D açıldı":"CSV açıldı");
            }else if(req==SAVE){CsvUtil.write(this,u,data);Toast.makeText(this,"CSV kaydedildi",Toast.LENGTH_SHORT).show();}
        }catch(Exception e){Toast.makeText(this,"Dosya hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onResume(){super.onResume();if(surface!=null)surface.onResume();if(volume4d!=null)volume4d.onResume();
        if(surface!=null){surface.setSmoothing(Prefs.smoothing(this));surface.setWireframe(Prefs.grid(this));}
        if(volume4d!=null){volume4d.setSmoothing(Prefs.smoothing(this));volume4d.setWireframe(Prefs.grid(this));
            volume4d.setOpacity(Prefs.groundOpacity(this));volume4d.setSharpness(Prefs.anomalySharpness(this));}
    }
    @Override protected void onPause(){if(surface!=null)surface.onPause();if(volume4d!=null)volume4d.onPause();super.onPause();}
}
