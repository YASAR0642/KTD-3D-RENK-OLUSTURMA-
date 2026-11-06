package com.ktd.analyzer.view;
import android.content.Context;import android.graphics.*;import android.view.View;
public final class ColorScaleView extends View{private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private int palette=0;public ColorScaleView(Context c){super(c);}public void setPalette(int v){palette=v;invalidate();}protected void onDraw(Canvas c){super.onDraw(c);for(int i=0;i<getWidth();i++){float t=i/(float)Math.max(1,getWidth()-1);p.setColor(ColorMaps.color(palette,(t*2-1)*100,100));c.drawRect(i,0,i+1,getHeight(),p);}}}
