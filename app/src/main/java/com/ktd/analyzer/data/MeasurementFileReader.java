package com.ktd.analyzer.data;

import android.content.Context;
import android.net.Uri;
import java.io.InputStream;
import java.io.IOException;

/**
 * Harici/WhatsApp olcumlerinin turunu dosya adindan DEGIL icerik imzasindan bulur.
 * Dosyanin adi Tarama, Ali, koy, a1, veya uzantisiz olsa da ayni bicimde okunur.
 */
public final class MeasurementFileReader {
    private MeasurementFileReader() { }

    public static boolean isV3d(Context context, Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Dosyaya erisilemiyor");
            byte[] prefix = new byte[20];
            int size = 0, n;
            while (size < prefix.length && (n = in.read(prefix,size,prefix.length-size)) > 0) size += n;
            int start = size>0 && prefix[0] == '\n' ? 1 : 0;
            byte[] magic = "V3D.V3D.01".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            boolean match = size-start >= magic.length;
            for (int i=0; match && i<magic.length; i++) if (prefix[start+i] != magic[i]) match = false;
            if (match) return true;
            // PNG/JPEG/PDF/ZIP ya da cok dusuk duzeyli ikili dosyalari CSV sanma.
            if (size>=4 && prefix[0]==(byte)0x89 && prefix[1]=='P' && prefix[2]=='N' && prefix[3]=='G')
                throw new IOException("PNG resim dosyasi CSV degildir");
            if (size>=3 && prefix[0]==(byte)0xff && prefix[1]==(byte)0xd8 && prefix[2]==(byte)0xff)
                throw new IOException("JPG resim dosyasi CSV degildir");
            if (size>=4 && prefix[0]=='%' && prefix[1]=='P' && prefix[2]=='D' && prefix[3]=='F')
                throw new IOException("PDF belgesi CSV degildir");
            if (size>=4 && prefix[0]=='P' && prefix[1]=='K' && prefix[2]==3 && prefix[3]==4)
                throw new IOException("ZIP dosyasi CSV degildir");
            for (int i=0;i<size;i++) if(prefix[i]==0)throw new IOException("Dosya olcum CSV metni degil");
            return false;
        }
    }

    public static ScanData read(Context c, Uri uri, int stepCm) throws Exception {
        if (isV3d(c,uri)) {
            try (InputStream in=c.getContentResolver().openInputStream(uri)) {
                return V3dImporter.read(in,stepCm);
            }
        }
        return CsvUtil.read(c,uri);
    }
}
