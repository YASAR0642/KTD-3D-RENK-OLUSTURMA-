package com.ktd.analyzer.view;

import com.ktd.analyzer.data.ReferenceModel;
import com.ktd.analyzer.data.ScanData;

import java.util.Arrays;

/**
 * Görselleştirme için yoğunlaştırılmış alan.
 * Ham ölçümleri değiştirmez. Anomali (delta) ve ScanData içinde saklanan
 * açık cihaz C verisi varsa onu ayrı enterpole eder; ham anomali fiziksel
 * derinlikmiş gibi kullanılmaz.
 */
public final class DenseField {
    public final int w, h;
    public final float[] value;
    public final boolean[] valid;
    public final float[] depth;
    public final boolean[] depthValid;
    public final float maxAbs;
    public final float robustAbs;
    public final float maxDepth;

    private DenseField(int w, int h, float[] value, boolean[] valid,
                       float[] depth, boolean[] depthValid,
                       float maxAbs, float robustAbs, float maxDepth) {
        this.w = w; this.h = h; this.value = value; this.valid = valid;
        this.depth = depth; this.depthValid = depthValid;
        this.maxAbs = maxAbs; this.robustAbs = robustAbs; this.maxDepth = maxDepth;
    }

    public float get(int x, int y) { return value[y * w + x]; }
    public boolean isValid(int x, int y) { return valid[y * w + x]; }
    public float getDepth(int x, int y) { return depth[y * w + x]; }
    public boolean isDepthValid(int x, int y) { return depthValid[y * w + x]; }

    public static DenseField from(ScanData d, int target) {return from(d,target,false);}

    public static DenseField from(ScanData d, int target, boolean smoothing) {
        int w = Math.max(18, Math.min(96, target));
        int h = w;
        float[] out = new float[w * h];
        boolean[] ok = new boolean[w * h];
        float[] dep = new float[w * h];
        boolean[] depOk = new boolean[w * h];
        Arrays.fill(out, 0f);
        Arrays.fill(dep, 0f);

        if (d == null || d.count == 0)
            return new DenseField(w, h, out, ok, dep, depOk, 10f, 10f, 0f);

        for (int yy = 0; yy < h; yy++) {
            float gy = h == 1 ? 0f : yy * (d.rows - 1f) / (h - 1f);
            for (int xx = 0; xx < w; xx++) {
                float gx = w == 1 ? 0f : xx * (d.cols - 1f) / (w - 1f);
                Sample s = sampleDelta(d, gx, gy);
                Sample z = sampleDepth(d, gx, gy);
                int i = yy * w + xx;
                ok[i] = s.valid;
                if (s.valid) {
                    float v = Math.abs(s.v) <= ReferenceModel.GROUND_BAND ? 0f : s.v;
                    out[i] = v;
                }
                depOk[i] = z.valid;
                if (z.valid) dep[i] = Math.abs(z.v);
            }
        }

        // v3.5.0: ham ölçümü değiştirmeden, sadece görselleştirme katmanında
        // tek sensör için kararlı işleme. Önce 3x3 medyan darbe/parazitleri kırar,
        // ardından hafif Gauss-benzeri smoothing grid geçişlerini yumuşatır.
        if(smoothing){median3x3(out, ok, w, h);smooth(out, ok, w, h, 1);}
        // Derinlik yalnız açık (ve 800/Z ile türetilmemiş) C kanalından gelebilir.
        // derinlik alanına ayrıca smoothing uygulanmaz; yalnız enterpolasyon kullanılır.

        // Tek bir aşırı örnek bütün 3D/4D ölçeğini bozmasın: %96 sağlam genlik
        // referansı çıkarılır ve yalnız görsel alanda bunun 1.35 katına winsorize edilir.
        int n = 0;
        float[] abs = new float[out.length];
        for (int i = 0; i < out.length; i++) if (ok[i]) abs[n++] = Math.abs(out[i]);
        float robust = Math.max(ReferenceModel.GROUND_BAND * 1.6f, percentile(abs, n, .96f));
        float clip = Math.max(ReferenceModel.GROUND_BAND * 2f, robust * 1.35f);
        float max = 10f, maxDepth = 0f;
        for (int i = 0; i < out.length; i++) {
            if (ok[i]) {
                out[i] = Math.max(-clip, Math.min(clip, out[i]));
                max = Math.max(max, Math.abs(out[i]));
            }
            if (depOk[i] && Float.isFinite(dep[i])) maxDepth = Math.max(maxDepth, Math.abs(dep[i]));
        }
        // Kırpma sonrası tek hafif geçiş; veri işleme yalnız görüntü içindir.
        if(smoothing)smooth(out, ok, w, h, 1);
        return new DenseField(w, h, out, ok, dep, depOk, max, Math.min(max, robust), maxDepth);
    }

    private static Sample sampleDelta(ScanData d, float gx, float gy) {
        return sample(d, gx, gy, false);
    }

    private static Sample sampleDepth(ScanData d, float gx, float gy) {
        return sample(d, gx, gy, true);
    }

    private static Sample sample(ScanData d, float gx, float gy, boolean depth) {
        // Voxler Gridder benzeri IDW power=2 (w=1/d^2).
        // Sadece gerçekten alınmış örnekler kullanılır. Ham CSV değiştirilmez.
        // Eksik taramada uzaktaki boş alanlar doldurulmaz.
        double sw=0d, sv=0d;
        int hits=0;
        final boolean complete=d.count>=d.rows*d.cols;
        final float radius=complete ? Float.POSITIVE_INFINITY : 2.35f;
        for(int r=0;r<d.rows;r++) for(int c=0;c<d.cols;c++) {
            if(!usable(d,r,c,depth)) continue;
            float dx=(c-gx), dy=(r-gy);
            float dist2=dx*dx+dy*dy;
            if(dist2<1e-7f) return new Sample(source(d,r,c,depth),true);
            if(dist2>radius*radius)continue;
            double weight=1.0d/dist2; // IDW power 2
            sw+=weight;
            sv+=weight*source(d,r,c,depth);
            hits++;
        }
        return hits==0 ? new Sample(0f,false) : new Sample((float)(sv/sw),true);
    }

    private static boolean usable(ScanData d, int r, int c, boolean depth) {
        if (!d.filled[r][c]) return false;
        float v = source(d, r, c, depth);
        return Float.isFinite(v);
    }

    private static float source(ScanData d, int r, int c, boolean depth) {
        return depth ? d.depth[r][c] : d.delta[r][c];
    }

    private static void median3x3(float[] a, boolean[] ok, int w, int h) {
        float[] src = Arrays.copyOf(a, a.length);
        float[] vals = new float[9];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!ok[i]) continue;
            int n = 0;
            for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++)
                for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
                    int j = yy * w + xx;
                    if (ok[j]) vals[n++] = src[j];
                }
            if (n >= 3) {
                Arrays.sort(vals, 0, n);
                a[i] = vals[n / 2];
            }
        }
    }

    private static void smooth(float[] a, boolean[] ok, int w, int h, int passes) {
        float[] tmp = new float[a.length];
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = y * w + x; if (!ok[i]) { tmp[i] = 0; continue; }
                float sum = 0, ws = 0;
                for (int k = -1; k <= 1; k++) {
                    int xx = x + k; if (xx < 0 || xx >= w) continue;
                    int j = y * w + xx; if (!ok[j]) continue;
                    float wt = k == 0 ? 2f : 1f; sum += a[j] * wt; ws += wt;
                }
                tmp[i] = ws > 0 ? sum / ws : a[i];
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = y * w + x; if (!ok[i]) continue;
                float sum = 0, ws = 0;
                for (int k = -1; k <= 1; k++) {
                    int yy = y + k; if (yy < 0 || yy >= h) continue;
                    int j = yy * w + x; if (!ok[j]) continue;
                    float wt = k == 0 ? 2f : 1f; sum += tmp[j] * wt; ws += wt;
                }
                if (ws > 0) a[i] = sum / ws;
            }
        }
    }

    private static float percentile(float[] a, int n, float p) {
        if (n <= 0) return 10f;
        float[] b = Arrays.copyOf(a, n); Arrays.sort(b);
        int idx = Math.max(0, Math.min(n - 1, Math.round((n - 1) * p)));
        return b[idx];
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
    private static final class Sample { final float v; final boolean valid; Sample(float v, boolean valid){this.v=v;this.valid=valid;} }
}
