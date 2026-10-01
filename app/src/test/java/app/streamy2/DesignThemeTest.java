package app.streamy2;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Einstellungen → Darstellung → Design: Dunkel (default) / OLED-Schwarz (3.89). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DesignThemeTest {
    private Context themed(String design, String accent) {
        ContextThemeWrapper ctx = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Streamy);
        int d = Design.overlayFor(design);
        if (d != 0) ctx.getTheme().applyStyle(d, true);
        int a = AccentTheme.overlayFor(accent);
        if (a != 0) ctx.getTheme().applyStyle(a, true);
        return ctx;
    }

    @After public void reset() {
        Prefs p = new Prefs(RuntimeEnvironment.getApplication());
        p.setDesign(Design.DARK);
        p.setAccent("blue");
    }

    @Test public void defaultIsDunkel() {
        Prefs p = new Prefs(RuntimeEnvironment.getApplication());
        assertEquals(Design.DARK, p.design());
        assertEquals("Dunkel", Design.label(p.design()));
        p.setDesign(Design.OLED);
        assertEquals(Design.OLED, p.design());
        assertEquals("OLED-Schwarz", Design.label(p.design()));
        p.setDesign("unbekannt");
        assertEquals(Design.DARK, p.design());
    }

    @Test public void normalizeAndLabels() {
        assertEquals(Design.DARK, Design.normalize(null));
        assertEquals(Design.DARK, Design.normalize(""));
        assertEquals(Design.OLED, Design.normalize("oled"));
        assertTrue(Design.isOled("oled"));
        assertFalse(Design.isOled("dark"));
        assertEquals(0, Design.overlayFor(Design.DARK));
        assertNotEquals(0, Design.overlayFor(Design.OLED));
    }

    @Test public void dunkelUsesMockupColours() {
        Context ctx = themed(Design.DARK, "blue");
        assertEquals(Design.DARK_BG, AccentTheme.background(ctx));
        assertEquals(Design.DARK_CARD, AccentTheme.card(ctx));
        assertEquals(Design.DARK_ELEVATED, AccentTheme.elevated(ctx));
    }

    @Test public void oledIsPureBlackWithVeryDarkSurfaces() {
        Context ctx = themed(Design.OLED, "blue");
        assertEquals(0xFF000000, AccentTheme.background(ctx));
        assertEquals(Design.OLED_CARD, AccentTheme.card(ctx));
        assertEquals(Design.OLED_ELEVATED, AccentTheme.elevated(ctx));
        assertEquals(0xFF000000, AccentTheme.color(ctx, android.R.attr.colorBackground, 0));
        for (int c : new int[]{AccentTheme.card(ctx), AccentTheme.elevated(ctx), AccentTheme.color(ctx, R.attr.streamySurface, 0)}) {
            assertTrue(Integer.toHexString(c), ((c >> 16) & 255) < 0x20 && ((c >> 8) & 255) < 0x20 && (c & 255) < 0x20);
        }
    }

    @Test public void oledKeepsEveryAccentColour() {
        for (Theme.Accent accent : Theme.ALL) {
            Context ctx = themed(Design.OLED, accent.id);
            assertEquals(accent.id, accent.color, AccentTheme.accent(ctx));
            assertEquals(accent.id, accent.onColor, AccentTheme.color(ctx, R.attr.streamyOnAccent, 0));
            assertEquals(accent.id, 0xFF000000, AccentTheme.background(ctx));
        }
    }

    @Test public void activityAppliesSavedDesign() {
        new Prefs(RuntimeEnvironment.getApplication()).setDesign(Design.OLED);
        ActivityController<GuideActivity> c = Robolectric.buildActivity(GuideActivity.class).setup();
        assertEquals(0xFF000000, AccentTheme.background(c.get()));
        c.pause().stop().destroy();
        new Prefs(RuntimeEnvironment.getApplication()).setDesign(Design.DARK);
        ActivityController<GuideActivity> d = Robolectric.buildActivity(GuideActivity.class).setup();
        assertEquals(Design.DARK_BG, AccentTheme.background(d.get()));
        d.pause().stop().destroy();
    }
}
