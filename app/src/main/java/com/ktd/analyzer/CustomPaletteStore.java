package com.ktd.analyzer;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Kullanici renk paletleri; yalnizca goruntu ayaridir. */
public final class CustomPaletteStore {
    public static final int BASE=20, MAX=12;
    public static final class Item {
        public final String name; public final int[] colors;
        public Item(String name,int[] colors){this.name=name;this.colors=colors;}
    }
    private static final String KEY="user_color_palettes_v1";
    private CustomPaletteStore(){}
    public static ArrayList<Item> load(Context ctx){
        ArrayList<Item> result=new ArrayList<>();
        try{
            JSONArray list=new JSONArray(ctx.getSharedPreferences("ktd_v3",0).getString(KEY,"[]"));
            for(int i=0;i<list.length()&&result.size()<MAX;i++){
                JSONObject entry=list.getJSONObject(i);JSONArray a=entry.getJSONArray("colors");
                if(a.length()!=5 && a.length()!=10)continue;
                int[] colors=new int[10];
                if(a.length()==10){
                    for(int j=0;j<10;j++)colors[j]=Color.rgb(Color.red(a.getInt(j)),Color.green(a.getInt(j)),Color.blue(a.getInt(j)));
                } else {
                    // Eski 5 noktali kayitlari kaybetmeden 10 noktaya genislet.
                    int[] old=new int[5];for(int j=0;j<5;j++)old[j]=a.getInt(j);
                    for(int j=0;j<10;j++){
                        float pos=j*4f/9f;int k=Math.min(3,(int)pos);float f=pos-k;
                        colors[j]=Color.rgb(Math.round(Color.red(old[k])*(1-f)+Color.red(old[k+1])*f),
                            Math.round(Color.green(old[k])*(1-f)+Color.green(old[k+1])*f),
                            Math.round(Color.blue(old[k])*(1-f)+Color.blue(old[k+1])*f));
                    }
                }
                result.add(new Item(entry.optString("name","Özel renk "+(i+1)),colors));
            }
        }catch(Exception ignored){}
        return result;
    }
    public static boolean save(Context ctx,String name,int[] colors){
        String clean=name==null?"":name.trim();
        if(clean.isEmpty()||colors==null||colors.length!=10)return false;
        ArrayList<Item> items=load(ctx);
        if(items.size()>=MAX)return false;
        for(Item item:items)if(item.name.equalsIgnoreCase(clean))return false;
        items.add(new Item(clean.length()>32?clean.substring(0,32):clean,colors.clone()));
        return persist(ctx,items);
    }
    public static boolean delete(Context ctx,int palette){
        int idx=palette-BASE;ArrayList<Item> items=load(ctx);
        if(idx<0||idx>=items.size())return false;
        items.remove(idx);return persist(ctx,items);
    }
    private static boolean persist(Context ctx,ArrayList<Item> items){
        try{
            JSONArray list=new JSONArray();
            for(Item item:items){JSONObject o=new JSONObject();o.put("name",item.name);JSONArray c=new JSONArray();for(int color:item.colors)c.put(color);o.put("colors",c);list.put(o);}
            return ctx.getSharedPreferences("ktd_v3",0).edit().putString(KEY,list.toString()).commit();
        }catch(Exception e){return false;}
    }
}
