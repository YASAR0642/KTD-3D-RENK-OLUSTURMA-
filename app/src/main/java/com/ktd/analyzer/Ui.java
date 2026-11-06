package com.ktd.analyzer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static GradientDrawable bg(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Yazıyı ekran genişliğine göre otomatik küçültür; üst üste binmeyi engeller. */
    public static void fitText(TextView v, int minSp, int maxSp, int maxLines) {
        v.setMaxLines(Math.max(1, maxLines));
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                v, minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        if (maxLines == 1) {
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    public static TextView title(Context c, String s) {
        TextView v = text(c, s, 19, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        fitText(v, 14, 21, 1);
        return v;
    }

    public static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(13);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        b.setBackground(bg(primary ? Color.rgb(35,214,176) : Color.rgb(23,42,54), 10, c));
        fitText(b, 10, 15, 1);
        return b;
    }

    /** Android 5+ sistem çubukları ve ekran çentiği için güvenli alan uygular. */
    @SuppressWarnings("deprecation")
    public static void applySafeInsets(Activity a, View root){
        if(Build.VERSION.SDK_INT < 21) return;
        final int l0=root.getPaddingLeft(), t0=root.getPaddingTop(), r0=root.getPaddingRight(), b0=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left=insets.getSystemWindowInsetLeft();
            int top=insets.getSystemWindowInsetTop();
            int right=insets.getSystemWindowInsetRight();
            int bottom=insets.getSystemWindowInsetBottom();
            v.setPadding(l0+left,t0+top,r0+right,b0+bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams weight(float weight) { return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight); }

    public static void setMargins(View v, int l, int t, int r, int b, Context c) {
        if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams)v.getLayoutParams();
            p.setMargins(dp(c,l), dp(c,t), dp(c,r), dp(c,b));
            v.setLayoutParams(p);
        }
    }
}
