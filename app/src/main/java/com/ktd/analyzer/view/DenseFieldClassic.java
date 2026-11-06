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
public final class DenseFieldClassic {
    public final int w, h;
    public final float[] value;
    public final boolean[] valid;
    public final float[] depth;
    public final boolean[] depthValid;
    public final float maxAbs;
    public final float robustAbs;
    public final float maxDepth;

    private DenseFieldClassic(int w, int h, float[] value, boolean[] valid,
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

    public static DenseFieldClassic from(ScanData d, int target) {return from(d,target,true);}

    /** Eski API: artık 2D/3D/4D aynı IDW power=2 gridleme motorunu kullanır.
     * Bağımsız, farklı yöntemle 2D yüzey oluşturulmasını önler.
     */
    public static DenseFieldClassic from(ScanData d, int target, boolean smoothing) {
        DenseField unified=DenseField.from(d,target,smoothing);
        return new DenseFieldClassic(unified.w,unified.h,unified.value,unified.valid,
                unified.depth,unified.depthValid,unified.maxAbs,unified.robustAbs,unified.maxDepth);
    }

    private static Sample sampleDelta(ScanData d, float gx, float gy) {
        return sample(d, gx, gy, false);
    }

    private static Sample sampleDepth(ScanData d, float gx, float gy) {
        return sample(d, gx, gy, true);
    }

    private static Sample sample(ScanData d, float gx, float gy, boolean depth) {
        int x0 = (int)Math.floor(gx), y0 = (int)Math.floor(gy);
        int x1 = Math.min(d.cols - 1, x0 + 1), y1 = Math.min(d.rows - 1, y0 + 1);
        x0 = clamp(x0, 0, d.cols - 1); y0 = clamp(y0, 0, d.rows - 1);
        float tx = gx - x0, ty = gy - y0;

        if (usable(d, y0, x0, depth) && usable(d, y0, x1, depth) &&
            usable(d, y1, x0, depth) && usable(d, y1, x1, depth)) {
            float a = lerp(source(d, y0, x0, depth), source(d, y0, x1, depth), tx);
            float b = lerp(source(d, y1, x0, depth), source(d, y1, x1, depth), tx);
            return new Sample(lerp(a, b, ty), true);
        }

        // Kısmi taramada yalnız yakın gerçek ölçümler arasında IDW.
        double sw = 0, sv = 0;
        int hits = 0;
        final float maxDist = 2.35f;
        int ry0 = Math.max(0, (int)Math.floor(gy - maxDist));
        int ry1 = Math.min(d.rows - 1, (int)Math.ceil(gy + maxDist));
        int rx0 = Math.max(0, (int)Math.floor(gx - maxDist));
        int rx1 = Math.min(d.cols - 1, (int)Math.ceil(gx + maxDist));
        for (int r = ry0; r <= ry1; r++) for (int c = rx0; c <= rx1; c++) {
            if (!usable(d, r, c, depth)) continue;
            float dx = c - gx, dy = r - gy;
            float dist2 = dx * dx + dy * dy;
            if (dist2 < 1e-6f) return new Sample(source(d, r, c, depth), true);
            if (dist2 > maxDist * maxDist) continue;
            double wt = 1.0 / Math.pow(dist2, 1.15);
            sw += wt; sv += wt * source(d, r, c, depth); hits++;
        }
        return hits == 0 ? new Sample(0f, false) : new Sample((float)(sv / sw), true);
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
