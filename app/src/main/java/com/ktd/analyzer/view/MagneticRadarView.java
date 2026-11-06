package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.ArrayList;

/** Magnetic field waterfall styled like a live radargram panel. */
public final class MagneticRadarView extends View {
    private final ArrayList<Float> samples = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float boost = 2f, gain = 1f;
    private boolean mineral = false;
    private int palette = 0;
    private static final int MAX = 700;
    private static final int WINDOW = 240;
    public MagneticRadarView(Context c) { super(c); setBackgroundColor(Color.rgb(3,10,17)); }
    public void append(float d) { if (!Float.isFinite(d)) return; samples.add(d); if (samples.size() > MAX) samples.remove(0); postInvalidateOnAnimation(); }
    public void clear() { samples.clear(); invalidate(); }
    public void setBoost(float f) { boost = Math.max(0f, Math.min(10f, f)); invalidate(); }
    public void setMineral(boolean value) { mineral = value; invalidate(); }
    public void setGain(float f) { gain = Math.max(0f, Math.min(10f, f)); invalidate(); }
    public void setPalette(int value) { palette = value; invalidate(); }


    // Yalnızca son WINDOW ölçümü, ekranın sabit genişlikli kayan penceresinde tutulur.
    // Ölçüm sayısı az olduğunda tüm kayıt ekrana yayılarak anomali gerilmez.
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w < 16 || h < 16) return;
        c.drawColor(Color.rgb(3,10,17));
        int left = 12, top = 18, right = w - 8, bottom = h - 8;
        int width = right-left, height = bottom-top;
        if (width <= 0 || height <= 0) return;
        int cols = Math.min(WINDOW, Math.max(1, width));
        int rows = Math.min(260, Math.max(1, height));
        Bitmap bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.RGB_565);
        bitmap.eraseColor(Color.rgb(5,15,23));

        // İlk veri solda, en yeni veri sağda. Tampon dolunca eski iz soldan kaybolur.
        // Her örnek tam bir sütunda çizilir; yeni veri geldikçe sütunlar sola ilerler.
        int count = Math.min(WINDOW, samples.size());
        int first = samples.size()-count;
        int offset = cols - Math.min(cols, count);
        for (int x=0; x<cols; x++) {
            int local = x-offset;
            if (local<0 || local>=count) continue;
            int index=first+local;
            float value=samples.get(index);
            float trend=0f, broad=0f;
            int nt=0, nb=0;
            for(int j=Math.max(0,index-30);j<=index;j++){trend+=samples.get(j);nt++;}
            for(int j=Math.max(0,index-8);j<=index;j++){broad+=samples.get(j);nb++;}
            trend/=Math.max(1,nt);broad/=Math.max(1,nb);
            float source=mineral? value-.8f*trend:value;
            float center=mineral? broad-.8f*trend:broad;
            float boosted=source+(boost-1f)*Math.signum(center)*Math.max(0f,Math.abs(center)-1f);
            // Bu dikey şekil derinlik kestirimi DEĞİLDİR: manyetik genliğin
            // temsili renk kesiti. Tek zaman serisinden 4D metre derinliği çıkarılmaz.
            for(int y=0;y<rows;y++){
                float yy=(float)y/Math.max(1,rows-1);
                float window=(float)Math.exp(-Math.pow((yy-.5f)/.30f,2));
                float v=boosted*window*gain;
                int color=(Math.abs(v)<.55f)?Color.rgb(5,15,23):ColorMaps.color(palette,v,32f);
                bitmap.setPixel(x,y,color);
            }
        }
        paint.setFilterBitmap(true);
        c.drawBitmap(bitmap,null,new Rect(left,top,right,bottom),paint);
        bitmap.recycle();
    }
}
