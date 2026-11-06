package com.ktd.analyzer;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** Sahada ekrana bakmadan hücre/sıra takibi için Türkçe sesli yönlendirme. */
public final class VoiceGuide implements TextToSpeech.OnInitListener {
    private final TextToSpeech tts;
    private boolean ready=false;
    private boolean enabled=true;

    public VoiceGuide(Context c){tts=new TextToSpeech(c.getApplicationContext(),this);}
    @Override public void onInit(int status){
        if(status==TextToSpeech.SUCCESS){
            int r=tts.setLanguage(new Locale("tr","TR"));
            ready=r!=TextToSpeech.LANG_MISSING_DATA&&r!=TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setSpeechRate(1.32f);tts.setPitch(1.0f);
        }
    }
    public void setEnabled(boolean e){enabled=e;if(!e)tts.stop();}
    public void say(String s){speak(s,TextToSpeech.QUEUE_FLUSH,"ktd_voice_now");}
    public void append(String s){speak(s,TextToSpeech.QUEUE_ADD,"ktd_voice_queue");}
    private void speak(String s,int q,String id){if(enabled&&ready&&s!=null&&!s.isEmpty())tts.speak(s,q,null,id+System.nanoTime());}
    /** Nokta sayımı önceki referans/sıra anonsunu kesmeden kuyruğa eklenir. */
    public void point(int n){append(String.valueOf(n));}
    public void startLine(int line){append(line+". sıra.");}
    public void lineFinished(int line,boolean hasNext){append(line+". sıra bitti."+(hasNext?" Yan sıraya geçiniz.":""));}
    public void shutdown(){tts.stop();tts.shutdown();}
}
