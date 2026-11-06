package com.ktd.analyzer.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/** Android'in izin verdiği CSV kaynaklarını listeler. Gizli korumalı klasörlere erişmez. */
public final class PhoneCsvCatalog {
    private PhoneCsvCatalog() { }
    private static final String PREFS="ktd_external_csv_trees";
    private static final String TREES="granted_folders";
    public static final int MAX_RESULTS=1600;

    public static final class Item {
        public final String name,location;
        public final Uri uri;
        public final long modified;
        public Item(String name,String location,Uri uri,long modified) {
            this.name=name;this.location=location;this.uri=uri;this.modified=modified;
        }
    }

    public static void addTree(Context context, Uri tree) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        Set<String> old=p.getStringSet(TREES,Collections.emptySet());
        Set<String> next=new HashSet<>(old);
        next.add(tree.toString());
        p.edit().putStringSet(TREES,next).apply();
    }

    public static int treeCount(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getStringSet(TREES,Collections.emptySet()).size();
    }

    /**
     * MediaStore Files tüm özel CSV dosyalarını göstermeyi garanti etmez.
     * Android 11+ özel depolama ve WhatsApp korumalı alanları için SAF klasör izni gerekir.
     */
    public static ArrayList<Item> scan(Context ctx) {
        LinkedHashMap<String,Item> results=new LinkedHashMap<>();
        fromMediaStore(ctx,results);
        SharedPreferences prefs=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        Set<String> paths=prefs.getStringSet(TREES,Collections.emptySet());
        for(String str: paths) {
            try { fromTree(ctx,Uri.parse(str),results); }
            catch(Exception ignored) { /* İzin iptal edilmiş / bulut sağlayıcı çevrimdışı */ }
            if(results.size()>=MAX_RESULTS)break;
        }
        ArrayList<Item> sorted=new ArrayList<>(results.values());
        sorted.sort((a,b)->Long.compare(b.modified,a.modified));
        return sorted;
    }

    private static boolean csvOrV3d(String name) {
        if(name==null)return false;
        String low=name.toLowerCase(Locale.ROOT);
        return low.endsWith(".csv") || low.endsWith(".v3d");
    }

    private static void put(Map<String,Item> result,Item item) {
        if(result.size()<MAX_RESULTS)result.put(item.uri.toString(),item);
    }

    private static void fromMediaStore(Context ctx,Map<String,Item> out) {
        ContentResolver cr=ctx.getContentResolver();
        Uri uri=MediaStore.Files.getContentUri("external");
        String[] projection = Build.VERSION.SDK_INT>=29
                ? new String[]{MediaStore.Files.FileColumns._ID,MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED,MediaStore.Files.FileColumns.RELATIVE_PATH}
                : new String[]{MediaStore.Files.FileColumns._ID,MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED};
        String where=MediaStore.Files.FileColumns.DISPLAY_NAME+" LIKE ? OR "+
                MediaStore.Files.FileColumns.DISPLAY_NAME+" LIKE ?";
        try(Cursor cursor=cr.query(uri,projection,where,new String[]{"%.csv","%.v3d"},
                MediaStore.Files.FileColumns.DATE_MODIFIED+" DESC")) {
            if(cursor==null)return;
            int id=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int title=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
            int changed=cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED);
            int path=Build.VERSION.SDK_INT>=29?cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH):-1;
            while(cursor.moveToNext() && out.size()<MAX_RESULTS) {
                String name=cursor.getString(title);
                if(!csvOrV3d(name))continue;
                Uri file=ContentUris.withAppendedId(uri,cursor.getLong(id));
                String folder=path<0?"Telefon hafızası":cursor.getString(path);
                long modified=changed<0?0:cursor.getLong(changed)*1000L;
                put(out,new Item(name,folder==null?"Telefon hafızası":folder,file,modified));
            }
        } catch(Exception ignored) { /* İzin/yeni Android kısıtı: klasör seçicisi kullanılabilir. */ }
    }

    private static final class TreeNode {
        final String docId,folder;
        final int depth;
        TreeNode(String d,String f,int level){docId=d;folder=f;depth=level;}
    }

    private static void fromTree(Context ctx, Uri root, Map<String,Item> out) {
        ContentResolver cr=ctx.getContentResolver();
        String doc=DocumentsContract.getTreeDocumentId(root);
        if(doc==null)return;
        ArrayDeque<TreeNode> todo=new ArrayDeque<>();
        todo.add(new TreeNode(doc,"Seçilen klasör",0));
        int inspected=0;
        while(!todo.isEmpty() && inspected<12000 && out.size()<MAX_RESULTS) {
            TreeNode next=todo.removeFirst();
            if(next.depth>18)continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(root,next.docId);
            String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED};
            try(Cursor cursor=cr.query(children,projection,null,null,null)) {
                if(cursor==null)continue;
                while(cursor.moveToNext() && ++inspected<12000 && out.size()<MAX_RESULTS) {
                    String childId=cursor.getString(0);
                    String name=cursor.getString(1);
                    String mime=cursor.getString(2);
                    long changed=cursor.isNull(3)?0:cursor.getLong(3);
                    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        if(todo.size()<1500)todo.addLast(new TreeNode(childId,next.folder+" / "+name,next.depth+1));
                    } else if(csvOrV3d(name)) {
                        Uri file=DocumentsContract.buildDocumentUriUsingTree(root,childId);
                        put(out,new Item(name,next.folder,file,changed));
                    }
                }
            } catch(Exception ignored) { /* bu alt klasör yetkisiz olabilir */ }
        }
    }
}
