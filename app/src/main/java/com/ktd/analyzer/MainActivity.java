package com.ktd.analyzer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ktd.analyzer.bluetooth.BluetoothClient;
import com.ktd.analyzer.data.SensorFrame;

public final class MainActivity extends Activity implements BluetoothClient.Listener {
    private BluetoothClient bt;
    private TextView btState;
    private TextView connectBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!LicenseManager.isActivated(this)) {
            startActivity(new Intent(this, ActivationActivity.class));
            finish();
            return;
        }
        bt = new BluetoothClient(this);
        build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bt.activate();
        bt.reconnectSaved(this);
        refreshBluetooth();
    }

    @Override
    protected void onStop() {
        bt.release();
        super.onStop();
    }

    private GradientDrawable cardBg(int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(13, 27, 37));
        d.setCornerRadius(Ui.dp(this, 18));
        d.setStroke(Ui.dp(this, 1.3f), strokeColor);
        return d;
    }

    private TextView pill(String text, boolean connected) {
        TextView v = Ui.text(this, text, 13, connected ? Color.rgb(4, 20, 18) : Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setSingleLine(true);
        v.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        v.setBackground(Ui.bg(connected ? Color.rgb(44, 220, 117) : Color.rgb(23, 42, 54), 14, this));
        return v;
    }

    private void addMenuCard(GridLayout grid, int iconRes, String title, String desc,
                             int accent, Class<?> target) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        // v3.5.2: kart içeriği dikeyde de ortalanır; ikon/yazı aralıkları tüm
        // telefon boyutlarında dengeli görünür.
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        card.setBackground(cardBg(accent));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> startActivity(new Intent(this, target)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAdjustViewBounds(true);
        card.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 124)));

        TextView t = Ui.text(this, title, 17, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setLineSpacing(Ui.dp(this, 1), 1.02f);
        Ui.fitText(t, 13, 18, 2);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        tp.setMargins(0, Ui.dp(this, 6), 0, 0);
        card.addView(t, tp);

        TextView d = Ui.text(this, desc, 12, Color.rgb(162, 187, 201));
        d.setGravity(Gravity.CENTER);
        d.setSingleLine(false);
        d.setMaxLines(3);
        d.setLineSpacing(Ui.dp(this, 1), 1.03f);
        Ui.fitText(d, 10, 13, 3);
        card.addView(d, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        TextView open = Ui.text(this, "AÇ  ›", 12, accent);
        open.setGravity(Gravity.CENTER);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Ui.fitText(open, 11, 14, 1);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 30));
        op.setMargins(0, Ui.dp(this, 2), 0, 0);
        card.addView(open, op);

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = 0;
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        gp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        gp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        grid.addView(card, gp);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 14));
        root.setBackgroundColor(Color.rgb(7, 16, 24));

        FrameLayout head = new FrameLayout(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ktd_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams logoLp=new FrameLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,62),Gravity.START|Gravity.CENTER_VERTICAL);
        head.addView(logo,logoLp);
        TextView title = Ui.title(this, "KTD 3D ANALYZER");
        title.setGravity(Gravity.CENTER);
        Ui.fitText(title,15,22,1);
        FrameLayout.LayoutParams titleLp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,62),Gravity.CENTER);
        titleLp.setMargins(Ui.dp(this,68),0,Ui.dp(this,10),0);
        head.addView(title,titleLp);
        root.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 70)));

        LinearLayout conn = new LinearLayout(this);
        conn.setGravity(Gravity.CENTER_VERTICAL);
        conn.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        conn.setBackground(Ui.bg(Color.rgb(13, 30, 39), 14, this));
        btState = Ui.text(this, "", 13, Color.rgb(170, 195, 208));
        btState.setSingleLine(true);
        conn.addView(btState, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        connectBtn = pill("Bluetooth", false);
        connectBtn.setOnClickListener(v -> {
            if (BluetoothClient.isConnected()) {
                Toast.makeText(this, "Bağlı: " + BluetoothClient.connectedName(), Toast.LENGTH_SHORT).show();
            } else {
                bt.pickPaired(this);
            }
        });
        conn.addView(connectBtn, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 42)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 58));
        cp.setMargins(0, 0, 0, Ui.dp(this, 8));
        root.addView(conn, cp);

        GridLayout grid = new GridLayout(this);
        int sw = getResources().getConfiguration().screenWidthDp;
        int cols = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE || sw >= 600)
                ? 2 : (sw < 340 ? 1 : 2);
        grid.setColumnCount(cols);
        grid.setRowCount(cols == 1 ? 4 : 2);

        addMenuCard(grid, R.drawable.menu_ground,
                "3D ZEMİN TARAMA", "",
                Color.rgb(25, 222, 235), ScanActivity.class);
        addMenuCard(grid, R.drawable.menu_live,
                "3D CANLI TARAMA", "",
                Color.rgb(38, 133, 255), LiveScanActivity.class);
        addMenuCard(grid, R.drawable.menu_records,
                "KAYITLAR", "",
                Color.rgb(255, 190, 38), RecordsActivity.class);
        addMenuCard(grid, R.drawable.menu_settings,
                "AYARLAR", "",
                Color.rgb(92, 218, 255), SettingsActivity.class);

        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        Ui.applySafeInsets(this, root);
        refreshBluetooth();
    }

    private void refreshBluetooth() {
        if (btState == null || connectBtn == null) return;
        boolean connected = BluetoothClient.isConnected();
        btState.setText(connected ? "Bağlı: " + BluetoothClient.connectedName() : "Bluetooth bağlı değil");
        connectBtn.setText(connected ? "BAĞLI" : "Bluetooth");
        connectBtn.setTextColor(connected ? Color.rgb(4, 20, 18) : Color.WHITE);
        connectBtn.setBackground(Ui.bg(connected ? Color.rgb(44, 220, 117) : Color.rgb(23, 42, 54), 14, this));
    }

    @Override
    public void onState(String s) {
        runOnUiThread(this::refreshBluetooth);
    }

    @Override
    public void onFrame(SensorFrame f) {
    }
}
