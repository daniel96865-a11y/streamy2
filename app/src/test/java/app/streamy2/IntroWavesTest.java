package app.streamy2;

import static org.junit.Assert.*;

import org.junit.Test;

/** "Farbwellen" geometry and colours (3.90). */
public class IntroWavesTest {
    private static float hueDiff(int a, int b) {
        float d = Math.abs(IntroWaves.rgbToHsv(a)[0] - IntroWaves.rgbToHsv(b)[0]);
        return Math.min(d, 360f - d);
    }

    @Test public void accentIsTheMainColourWithSubtleTints() {
        int[] blue = IntroWaves.palette(0xFF5B9DFF);
        assertEquals(0xFF5B9DFF, blue[0]);
        assertEquals(30f, hueDiff(blue[0], blue[1]), 1.5f);
        assertEquals(35f, hueDiff(blue[0], blue[2]), 1.5f);
        for (Theme.Accent a : Theme.ALL) {
            int[] p = IntroWaves.palette(a.color);
            assertEquals(a.id, 0xFF000000 | (a.color & 0xFFFFFF), p[0]);
            assertTrue(a.id, hueDiff(p[0], p[1]) < 45f && hueDiff(p[0], p[2]) < 45f);
        }
    }

    @Test public void hsvRoundTrip() {
        for (int c : new int[]{0xFF5B9DFF, 0xFFFF5577, 0xFF33CC99, 0xFF808080, 0xFF000000, 0xFFFFFFFF}) {
            float[] h = IntroWaves.rgbToHsv(c);
            int back = IntroWaves.hsvToRgb(h[0], h[1], h[2]);
            for (int s = 0; s <= 16; s += 8) assertTrue(Math.abs(((c >> s) & 255) - ((back >> s) & 255)) <= 1);
        }
    }

    @Test public void fewLargeShapesInsideTheScreenAndFlowing() {
        float[] a = new float[IntroWaves.COUNT * IntroWaves.STRIDE];
        float[] b = new float[a.length];
        IntroWaves.frame(0.5f, 1920, 1080, a);
        IntroWaves.frame(1.0f, 1920, 1080, b);
        assertTrue(IntroWaves.COUNT <= 20);
        boolean moved = false;
        for (int k = 0; k < a.length; k += IntroWaves.STRIDE) {
            assertTrue(a[k + 1] > 0 && a[k + 1] < 1080);   // centres vertically on screen
            assertTrue(a[k] > -400 && a[k] < 2320);
            assertTrue(a[k + 2] > 200 && a[k + 3] > 150);  // large and soft
            assertTrue(a[k + 4] >= 0f && a[k + 4] <= 1f);
            int ci = (int) a[k + 5];
            assertTrue(ci >= 0 && ci <= 2);
            if (Math.abs(a[k + 1] - b[k + 1]) > 1f) moved = true;
        }
        assertTrue("waves must move", moved);
    }
}
