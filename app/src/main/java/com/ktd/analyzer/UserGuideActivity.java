package com.ktd.analyzer;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.TextView;

/** Sahada adım adım kullanılabilen, cihazın içinde çevrimdışı kılavuz. */
public final class UserGuideActivity extends Activity {
 private LinearLayout body;
 private void section(String heading,String explanation){
   Button title=Ui.button(this,heading,false); title.setAllCaps(false);
   TextView details=Ui.text(this,explanation,15,Color.rgb(215,230,239));
   details.setPadding(Ui.dp(this,15),Ui.dp(this,10),Ui.dp(this,15),Ui.dp(this,18));
   details.setVisibility(View.GONE); title.setOnClickListener(v->details.setVisibility(details.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,56));lp.topMargin=Ui.dp(this,8);body.addView(title,lp);body.addView(details);
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));root.setBackgroundColor(Color.rgb(7,16,24));
  Button back=Ui.button(this,"‹  KULLANIM KILAVUZU",false);back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
  ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);
  TextView introduction=Ui.text(this,"KTD 3D ANALYZER • Profesyonel saha talimatları\nBaşlığa dokunarak ilgili bölümü açın.",16,Color.rgb(48,226,215));introduction.setPadding(8,18,8,12);body.addView(introduction);
  section("01 • Referans ve başlangıç","Ölçüm alanı dışında, mümkün olduğunca sakin bir zeminde referans alın. 3D zemin taramasında ilk ÖRNEK AL yalnızca referansı belirler. Referans sırasında sensörü hareket ettirmeyin. Referans kaymasını kontrol etmek için ölçüm öncesi ve sonrasında aynı noktayı tekrar ölçün.");
  section("02 • Alanı ızgaraya bölme","Izgara menüsünden satır ve sütun sayısını seçin. Standart örnekleme adımı 30 cm’dir. Başlangıç için sol alt veya sağ alt köşeyi seçebilirsiniz. Paralel taramada her sıra aynı yönde; zikzak taramada bir sıra gidiş, sonraki sıra dönüş yönündedir. Konumları karıştırmamak için ölçüm noktalarını sahada işaretleyin.");
  section("03 • Adım adım ölçüm","İlk ÖRNEK AL ile referans belirlenir. Sonraki basışların her biri yalnızca seçilen hücrenin bir örneğini kaydeder. Tarama sırasında konum ve yönü mümkün olduğunca tutarlı tutun. Hızlı yön değiştirmeyin. Tüm hücreler tamamlanınca KAYDET ile CSV kaydını oluşturun. Aynı alanı ikinci kez ölçerek sonuçları karşılaştırın.");
  section("04 • 2D, 3D ve 4D ekranlarını okuma","2D harita, ölçüm noktalarının üstten görünümünü ve aralarındaki yazılımsal interpolasyonu gösterir. 3D yüzey, aynı değerlerin görsel yükseklik ve renk dönüşümüdür. 4D hacim, yüzey verisinden türetilen temsili izoyüzeyleri gösterir; gerçek cismin ölçülmüş dış sınırı değildir. Aynı koordinattaki pozitif/negatif sapmaları tüm görünümlerde karşılaştırın.");
  section("05 • Renkler ve sinyal yorumu","Yeşil: referansa yakın. Mavi/mor: negatif manyetik sapma. Sarı/turuncu/kırmızı: pozitif sapma ve şiddet katmanları. Siyah kontur: görsel eşik/sınır. Renkler tek başına boşluk, dolgu, duvar, demir ya da değerli metal teşhisi değildir. İşaret, şiddet, konum, hedefin çevresi ve tekrar ölçüm birlikte değerlendirilir.");
  section("06 • Canlı tarama ve GMR","NORMAL canlı modda BAŞLAT akışı başlatır; DURDUR akışı bitirir. GMR ekranında radargramdaki bozulmalar manyetik değişimlerden görselleştirilir. GMR görselleştirmesi klasik darbe radarının çok katmanlı derinlik ölçümü değildir. Kayıt ismi, zaman ve Bluetooth durumunu kontrol edin.");
  section("07 • Güvenli saha doğrulama","Önce hedef bulunmayan kontrol alanı; sonra yeri ve derinliği önceden bilinen güvenli test hedefleri ölçülmelidir. Aynı noktada en az iki tekrar yapın, farklı yönlerden tarayın. Referans kaydı, cihaz yüksekliği ve sensör yönelimi aynı olmalıdır. Sonuçlardan kazı güvenliği veya kesin nesne teşhisi kararı tek başına üretmeyin.");
  scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);Ui.applySafeInsets(this,root);
 }
}
