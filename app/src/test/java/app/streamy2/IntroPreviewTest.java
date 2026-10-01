package app.streamy2;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

/**
 * Renders the real "Farbwellen" drawing code with Robolectric's native graphics (3.90):
 * checks the pixels (OLED pure black, accent-coloured waves) and writes preview frames to
 * build/intro-preview/.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, application = Application.class)
public class IntroPreviewTest {
    /** Set to true only to print preview frames into the test log. */
    static final boolean PRINT = false;

    @After public void reset() {
        Prefs p = new Prefs(RuntimeEnvironment.getApplication());
        p.setDesign(Design.DARK);
        p.setAccent("blue");
    }

    private static Bitmap waves(IntroOverlay o, int w, int h, long now) {
        int sw = Math.max(1, (w + IntroOverlay.SCALE - 1) / IntroOverlay.SCALE);
        int sh = Math.max(1, (h + IntroOverlay.SCALE - 1) / IntroOverlay.SCALE);
        o.waves.measure(View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(sh, View.MeasureSpec.EXACTLY));
        o.waves.layout(0, 0, sw, sh);
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        o.waves.drawFrame(new Canvas(small), now);
        // Same as the GPU: bilinear upscale of the small layer.
        Bitmap big = Bitmap.createScaledBitmap(small, w, h, true);
        small.recycle();
        return big;
    }

    private static int channelMax(int c) {
        return Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
    }

    @Test public void oledCornersArePureBlackAndWavesUseAccent() {
        IntroOverlay o = new IntroOverlay(RuntimeEnvironment.getApplication(), 0xFF000000, 0xFF5B9DFF, false, 0L);
        Bitmap b = waves(o, 480, 270, 1200);
        assertTrue(Integer.toHexString(b.getPixel(0, 0)), channelMax(b.getPixel(0, 0)) <= 2);
        assertTrue(channelMax(b.getPixel(479, 0)) <= 2);
        // Somewhere on the bands the accent blue dominates.
        int best = 0;
        for (int x = 0; x < 480; x += 8) for (int y = 0; y < 270; y += 6) {
            int c = b.getPixel(x, y);
            if ((c & 255) > (best & 255)) best = c;
        }
        assertTrue(Integer.toHexString(best), (best & 255) > 120 && (best & 255) > ((best >> 16) & 255));
        b.recycle();
    }

    @Test public void dunkelBackgroundAndFadeIn() {
        IntroOverlay o = new IntroOverlay(RuntimeEnvironment.getApplication(), Design.DARK_BG, 0xFF5B9DFF, false, 0L);
        Bitmap first = waves(o, 192, 108, 0);
        // First frame: only the dark background (waves fade in).
        for (int x = 0; x < 192; x += 16) for (int y = 0; y < 108; y += 12) {
            assertEquals(Design.DARK_BG, first.getPixel(x, y));
        }
        first.recycle();
    }

    private static void makeTv(android.content.Context context) {
        android.app.UiModeManager um = (android.app.UiModeManager) context.getSystemService(android.content.Context.UI_MODE_SERVICE);
        if (um != null) org.robolectric.Shadows.shadowOf(um).setCurrentModeType(android.content.res.Configuration.UI_MODE_TYPE_TELEVISION);
    }

    @Config(qualifiers = "w960dp-h540dp-land-television-mdpi")
    @Test public void writePreviewFrames() throws Exception {
        File dir = new File("build/intro-preview");
        dir.mkdirs();
        // Real first screen behind the waves (TV layout, demo data), for the cross-fade.
        Bitmap screen = null;
        try {
            makeTv(RuntimeEnvironment.getApplication());
            ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class, new Intent());
            makeTv(c.get());
            c.setup();
            ShadowLooper.idleMainLooper();
            View root = c.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 960, 540);
            screen = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(screen));
            c.pause().stop().destroy();
        } catch (Throwable t) {
            screen = null;
        }
        String[][] variants = {{"dunkel", "blue"}, {"oled", "blue"}, {"dunkel", "rose"}};
        for (String[] v : variants) {
            int bg = Design.background(v[0]);
            int accent = Theme.get(v[1]).color;
            IntroOverlay o = new IntroOverlay(RuntimeEnvironment.getApplication(), bg, accent, false, 0L);
            o.timing.markReady(0);
            for (int ms = 0; ms <= 2000; ms += 100) {
                if (!"dunkel".equals(v[0]) || !"blue".equals(v[1])) {
                    if (ms % 500 != 0) continue;
                }
                Bitmap w = waves(o, 960, 540, ms);
                Bitmap out = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                if (screen != null) c.drawBitmap(screen, 0, 0, null);
                else c.drawColor(bg);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                p.setAlpha(Math.round(255 * o.timing.backgroundAlpha(ms)));
                c.drawBitmap(w, null, new Rect(0, 0, 960, 540), p);
                String name = String.format(java.util.Locale.ROOT, "tv-%s-%s-%04dms.jpg", v[0], v[1], ms);
                save(out, new File(dir, name));
                w.recycle();
                out.recycle();
            }
        }
        // Phone portrait still frames.
        for (String d : new String[]{"dunkel", "oled"}) {
            IntroOverlay o = new IntroOverlay(RuntimeEnvironment.getApplication(), Design.background(d), 0xFF5B9DFF, false, 0L);
            for (int ms : new int[]{600, 1200}) {
                Bitmap w = waves(o, 360, 780, ms);
                save(w, new File(dir, "phone-" + d + "-" + ms + "ms.jpg"));
                w.recycle();
            }
        }
        if (screen != null) screen.recycle();
        File[] files = dir.listFiles();
        assertNotNull(files);
        assertTrue(files.length >= 20);
    }

    private static void save(Bitmap b, File f) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        }
        if (PRINT) {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            String enc = java.util.Base64.getEncoder().encodeToString(data);
            System.out.println("PREVIEW-BEGIN " + f.getName());
            for (int i = 0; i < enc.length(); i += 4000) {
                System.out.println("PREVIEW " + enc.substring(i, Math.min(enc.length(), i + 4000)));
            }
            System.out.println("PREVIEW-END " + f.getName());
        }
    }
}
