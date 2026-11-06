package com.ktd.analyzer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SplashActivity extends Activity {
    protected void onCreate(Bundle b){super.onCreate(b);
        if(!LicenseManager.isActivated(this)){
            startActivity(new Intent(this,ActivationActivity.class));
            finish();
            return;
        }
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(Ui.dp(this,26),Ui.dp(this,26),Ui.dp(this,26),Ui.dp(this,26));root.setBackgroundColor(Color.rgb(3,8,12));
        ImageView logo=new ImageView(this);logo.setImageResource(com.ktd.analyzer.R.drawable.ktd_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);root.addView(logo,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        TextView t=Ui.text(this,"3D Zemin Tarama • 3D Canlı Tarama • 4D Analiz",15,Color.rgb(160,185,202));t.setGravity(Gravity.CENTER);root.addView(t,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,52)));setContentView(root);Ui.applySafeInsets(this,root);
        logo.setAlpha(0f);logo.setScaleX(.72f);logo.setScaleY(.72f);logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).withEndAction(()->root.postDelayed(()->{startActivity(new Intent(this,MainActivity.class));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);finish();},650)).start();
    }
}
