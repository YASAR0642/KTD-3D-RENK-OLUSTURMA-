package com.ktd.analyzer;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.TextView;
import com.ktd.analyzer.data.ScanData;
import com.ktd.analyzer.data.ScanSessionStore;
import java.util.Locale;

/** Tarama sinyalinden deneysel anomali puanları ve (800/Z)/10 proje gosterimi. */
public final class AutoAnalysisActivity extends Activity {
 private void line(LinearLayout host,String text,int size){
  TextView t=Ui.text(this,text,size,Color.rgb(224,236,242));
  t.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,14));host.addView(t);
 }
 private static String fmt(double value){return String.format(Locale.forLanguageTag("tr-TR"),"%.2f",value);}
 private static int percent(double strength){
  // Mutlak olasılık değil, bu ölçekteki sapmanın normalize edilmiş görsel puanı.
  return (int)Math.round(100.0*Math.min(1.0,Math.max(0.0,strength)/120.0));
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
  root.setBackgroundColor(Color.rgb(7,16,24));
  Button back=Ui.button(this,"‹  TARANAN ALAN ANALİZİ",false);
  back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
  ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(1);
  ScanData data=ScanSessionStore.current;
  if(data==null||data.count==0){line(content,"Analiz için kayıtlı tarama ölçümü bulunmuyor.",17);}
  else {
   double width=data.cols*data.stepCm/100.0;
   double length=data.rows*data.stepCm/100.0;
   line(content,"ÇEKİM ALANI",20);
   line(content,data.cols+" × "+data.rows+" örnek  •  "+fmt(width*length)+" m²",18);
   double ref=com.ktd.analyzer.data.ReferenceInverseDepth.referenceOf(data);
   double positive=0,negative=0;
   double strongestPositive=0,strongestNegative=0;
   double positiveZ=Double.NaN,negativeZ=Double.NaN;
   for(int r=0;r<data.rows;r++)for(int c=0;c<data.cols;c++){
    if(!data.filled[r][c])continue;
    double z=data.raw[r][c];if(!Double.isFinite(z)||z<=0)continue;
    double difference=z-ref;
    // ±15 referans toleransı. Gruplar işaretlidir; değerli metal/boşluk teşhisi değildir.
    if(difference>15){positive+=difference-15;if(difference>strongestPositive){strongestPositive=difference;positiveZ=z;}}
    if(difference< -15){negative+=-difference-15;if(-difference>strongestNegative){strongestNegative=-difference;negativeZ=z;}}
   }
   // Bağımsız iki sinyal gücü; toplamları 100 olmak zorunda değildir.
   int metal=percent(positive);
   int cavity=percent(negative);
   line(content,"BOŞLUK OLASILIĞI",20);
   line(content,cavity+"%",28);
   double cavityDepth=com.ktd.analyzer.data.ReferenceMeterModel.meters((float)negativeZ,(float)ref);
   line(content,"BOŞLUK DERİNLİĞİ",20);
   line(content,Double.isFinite(cavityDepth)?fmt(cavityDepth)+" m":"—",23);
   line(content,"METAL OLASILIĞI",20);
   line(content,metal+"%",28);
   double metalDepth=com.ktd.analyzer.data.ReferenceMeterModel.meters((float)positiveZ,(float)ref);
   line(content,"METAL DERİNLİĞİ",20);
   line(content,Double.isFinite(metalDepth)?fmt(metalDepth)+" m":"—",23);
  }
  scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);Ui.applySafeInsets(this,root);
 }
}
