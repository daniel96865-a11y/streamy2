package app.streamy2;

/**
 * Geometry and colours of the "Farbwellen" start animation (pure Java, unit tested).
 * Two flowing aurora bands made of a few large soft ellipses (radial gradients) plus a
 * wide haze; no per-pixel work. Colours: the accent colour plus two subtle tints derived
 * from it (hue shifted towards violet and towards teal).
 */
final class IntroWaves {
    /** Ellipses per band. */
    static final int PER_BAND = 8;
    static final int COUNT = 2 * PER_BAND + 1;
    /** Floats per ellipse: x, y, rx, ry, alpha (0..1), colour index (0 main, 1/2 tints). */
    static final int STRIDE = 6;

    private IntroWaves() {
    }

    /** {main, tint towards violet, tint towards teal} as ARGB. */
    static int[] palette(int accent) {
        float[] hsv = rgbToHsv(accent);
        int t1 = hsvToRgb((hsv[0] + 30f) % 360f, Math.min(1f, hsv[1] * 0.8f), hsv[2]);
        int t2 = hsvToRgb((hsv[0] + 325f) % 360f, Math.min(1f, hsv[1] * 0.9f), Math.min(1f, hsv[2] * 0.95f));
        return new int[]{0xFF000000 | (accent & 0xFFFFFF), t1, t2};
    }

    /** Fills {@code out} (length ≥ COUNT*STRIDE) with the ellipses at time {@code t} seconds. */
    static void frame(float t, float w, float h, float[] out) {
        int k = 0;
        float step = 1.2f / (PER_BAND - 1);
        // Band thickness follows a 16:9-ish reference height, so portrait phones get slim
        // aurora bands instead of huge blobs.
        float th = Math.min(h, w * 0.9f);
        for (int band = 0; band < 2; band++) {
            for (int i = 0; i < PER_BAND; i++) {
                float u = -0.1f + i * step + 0.04f * (float) Math.sin(t * 0.7f + i * 1.7f + band);
                float cy, inten, ry, alpha;
                if (band == 0) {
                    cy = 0.55f + 0.15f * (float) Math.sin(u * 4f + t * 1.3f);
                    float a = (float) Math.sin(u * 3f + t * 1.4f + Math.sin(cy * 2f + t) * 1.5f);
                    inten = 0.35f + 0.65f * (a * 0.5f + 0.5f);
                    ry = 0.18f * 1.9f;
                    alpha = 0.80f * inten;
                } else {
                    cy = 0.40f + 0.12f * (float) Math.cos(u * 3f - t);
                    float a = (float) Math.sin(cy * 4f - t * 1.1f + Math.cos(u * 2.5f - t * 0.8f) * 1.8f);
                    inten = 0.30f + 0.70f * (a * 0.5f + 0.5f);
                    ry = 0.14f * 1.9f;
                    alpha = 0.45f * inten;
                }
                out[k] = u * w;
                out[k + 1] = cy * h;
                out[k + 2] = w * step * 1.9f;
                out[k + 3] = th * ry;
                out[k + 4] = alpha;
                out[k + 5] = band == 0 ? 0 : (i < 2 ? 2 : 1);
                k += STRIDE;
            }
        }
        // Wide, faint haze in the accent colour.
        out[k] = w * 0.5f;
        out[k + 1] = h * 0.52f;
        out[k + 2] = w * 0.75f;
        out[k + 3] = Math.min(h * 0.62f, th * 0.9f);
        out[k + 4] = 0.10f;
        out[k + 5] = 0;
    }

    static float[] rgbToHsv(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float hue;
        if (d == 0) hue = 0;
        else if (max == r) hue = 60f * (((g - b) / d) % 6f);
        else if (max == g) hue = 60f * ((b - r) / d + 2f);
        else hue = 60f * ((r - g) / d + 4f);
        if (hue < 0) hue += 360f;
        return new float[]{hue, max == 0 ? 0 : d / max, max};
    }

    static int hsvToRgb(float hue, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((hue / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (hue < 60) { r = c; g = x; b = 0; }
        else if (hue < 120) { r = x; g = c; b = 0; }
        else if (hue < 180) { r = 0; g = c; b = x; }
        else if (hue < 240) { r = 0; g = x; b = c; }
        else if (hue < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int R = Math.round((r + m) * 255), G = Math.round((g + m) * 255), B = Math.round((b + m) * 255);
        return 0xFF000000 | (R << 16) | (G << 8) | B;
    }
}
