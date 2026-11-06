package com.ktd.analyzer.view;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import com.ktd.analyzer.Prefs;
import com.ktd.analyzer.Ui;

public final class PaletteStripView extends View {
    public interface Listener {void onPalette(int palette);}
    private static final int[] PRESET={0,1,7,11,12,13};
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int slot;
    private int selected;
    private Listener listener;
    public PaletteStripView(Context context){super(context);ColorMaps.configure(context);selected=Prefs.palette(context);slot=Ui.dp(context,112);setBackgroundColor(Color.rgb(7,16,24));setMinimumWidth(slot*entryCount());}
    public static int entryCount(){return PRESET.length+Math.max(0,ColorMaps.count()-ColorMaps.COUNT);}
    public void setListener(Listener l){listener=l;}
    public int selected(){return selected;}
    private static int indexAt(int i){return i<PRESET.length?PRESET[i]:ColorMaps.COUNT+i-PRESET.length;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int count=entryCount();float density=getResources().getDisplayMetrics().density;
        pen.setTextSize(10*density);pen.setTypeface(Typeface.DEFAULT_BOLD);pen.setTextAlign(Paint.Align.CENTER);
        for(int k=0;k<count;k++){
            int id=indexAt(k);float left=k*slot+4*density,right=(k+1)*slot-4*density;
            float top=3*density,bottom=getHeight()-17*density;
            for(int i=0;i<44;i++){float t=i/43f;pen.setStyle(Paint.Style.FILL);pen.setColor(ColorMaps.color(id,(t*2-1)*100f,100));float x1=left+(right-left)*i/44f,x2=left+(right-left)*(i+1)/44f+1;c.drawRect(x1,top,x2,bottom,pen);}
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth((id==selected?3:1)*density);pen.setColor(id==selected?Color.WHITE:Color.GRAY);c.drawRect(left,top,right,bottom,pen);
            pen.setStyle(Paint.Style.FILL);pen.setColor(Color.WHITE);
            String label=ColorMaps.name(id);if(label.length()>15)label=label.substring(0,14)+"…";
            c.drawText(label,(left+right)/2,getHeight()-3*density,pen);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){int index=(int)(e.getX()/slot);if(index>=0&&index<entryCount()){selected=indexAt(index);Prefs.setPalette(getContext(),selected);invalidate();if(listener!=null)listener.onPalette(selected);}return true;}return true;}
}
