package com.ktd.analyzer.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bluetooth byte/chunk akışını X/Y/Z/C paketlerine dönüştüren durumlu çözücü. */
public final class SensorStreamDecoder {
    private static final Pattern NUMBER=Pattern.compile("[-+]?\\d+(?:[\\.,]\\d+)?");
    private final StringBuilder pending=new StringBuilder();

    public List<SensorFrame> feed(String chunk){
        ArrayList<SensorFrame> out=new ArrayList<>();
        if(chunk==null||chunk.isEmpty())return out;
        pending.append(chunk.replace('\0',' '));
        drainDelimited(out);drainLabeled(out);drainNumeric(out);
        if(pending.length()>2048){SensorFrame f=SensorParser.parse(pending.toString());if(f!=null)out.add(f);pending.setLength(0);}
        return out;
    }
    private void drainDelimited(List<SensorFrame> out){
        while(true){int cut=-1;for(int i=0;i<pending.length();i++){char ch=pending.charAt(i);if(ch=='\n'||ch=='\r'||ch=='|'){cut=i;break;}}if(cut<0)return;
            String one=pending.substring(0,cut).trim();int rm=cut+1;while(rm<pending.length()&&(pending.charAt(rm)=='\n'||pending.charAt(rm)=='\r'||pending.charAt(rm)=='|'))rm++;pending.delete(0,rm);add(out,one);}
    }
    private void drainLabeled(List<SensorFrame> out){
        while(true){
            String u=pending.toString().toUpperCase();
            int firstX=indexLabel(u,'X',0);
            int firstZ=indexLabel(u,'Z',0);
            // Tam X/Y/Z/C paketinde çerçeve X ile başlar. X yoksa Z/C çift paketini kabul et.
            char marker; int first;
            if(firstX>=0){marker='X';first=firstX;}
            else if(firstZ>=0){marker='Z';first=firstZ;}
            else return;
            int second=indexLabel(u,marker,first+1);
            if(second<0){if(first>0)pending.delete(0,first);return;}
            String one=pending.substring(first,second);pending.delete(0,second);add(out,one);
        }
    }
    private void drainNumeric(List<SensorFrame> out){
        String up=pending.toString().toUpperCase();
        // An unfinished A1/A2 packet must wait for newline, not be split into
        // accidental numeric frames (e.g. "1,512,2,510").
        if(up.contains("A1=")||up.contains("A2=")||up.contains("A1:")||up.contains("A2:"))return;
        if(up.indexOf('X')>=0||up.indexOf('Y')>=0||up.indexOf('Z')>=0||up.indexOf('C')>=0)return;
        if(pending.indexOf(";")>=0){while(extract(out,';')){}return;}
        if(pending.indexOf(",")>=0){while(extract(out,',')){}return;}
        while(true){Matcher m=NUMBER.matcher(pending);int n=0,cut=-1;while(m.find()){n++;if(n==5){cut=m.start();break;}}if(cut<0)return;String one=pending.substring(0,cut).trim();pending.delete(0,cut);add(out,one);}
    }
    private boolean extract(List<SensorFrame> out,char delim){int seen=0,cut=-1;for(int i=0;i<pending.length();i++)if(pending.charAt(i)==delim){seen++;if(seen==4){cut=i;break;}}if(cut<0)return false;String one=pending.substring(0,cut).trim();pending.delete(0,cut+1);add(out,one);return true;}
    private static void add(List<SensorFrame> out,String s){if(s==null||s.isEmpty())return;SensorFrame f=SensorParser.parse(s);if(f!=null)out.add(f);}
    private static int indexLabel(String s,char label,int from){for(int i=Math.max(0,from);i<s.length()-1;i++)if(s.charAt(i)==label){int j=i+1;while(j<s.length()&&Character.isWhitespace(s.charAt(j)))j++;if(j<s.length()&&(s.charAt(j)==':'||s.charAt(j)=='='))return i;}return -1;}
}
