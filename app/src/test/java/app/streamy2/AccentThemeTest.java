package app.streamy2;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AccentThemeTest {
    private Context themed(String accentId) {
        ContextThemeWrapper ctx = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Streamy);
        int overlay = AccentTheme.overlayFor(accentId);
        if (overlay != 0) ctx.getTheme().applyStyle(overlay, true);
        return ctx;
    }

    @Test public void defaultAccentKeepsOriginalBlue() {
        assertEquals(0, AccentTheme.overlayFor("blue"));
        assertEquals(0, AccentTheme.overlayFor(null));
        assertEquals(0, AccentTheme.overlayFor("unknown"));
        Context ctx = themed("blue");
        assertEquals(0xFF5B9DFF, AccentTheme.accent(ctx));
        assertEquals(0xFF26354D, AccentTheme.color(ctx, R.attr.streamyAccentSurface, 0));
        assertEquals(0xFF202A3A, AccentTheme.color(ctx, R.attr.streamyAccentSurfaceDim, 0));
        assertEquals(0x665B9DFF, AccentTheme.color(ctx, R.attr.streamyAccentSoft, 0));
        assertEquals(0x335B9DFF, AccentTheme.color(ctx, R.attr.streamyAccentTrack, 0));
        assertEquals(RuntimeEnvironment.getApplication().getColor(R.color.accent), AccentTheme.accent(ctx));
    }

    @Test public void everyAccentOverlayMatchesThemePalette() {
        for (Theme.Accent accent : Theme.ALL) {
            Context ctx = themed(accent.id);
            assertEquals(accent.id, accent.color, AccentTheme.accent(ctx));
            assertEquals(accent.id, accent.onColor, AccentTheme.color(ctx, R.attr.streamyOnAccent, 0));
            assertEquals(accent.id, AccentTheme.withAlpha(accent.color, 0x66), AccentTheme.color(ctx, R.attr.streamyAccentSoft, 0));
            assertEquals(accent.id, accent.color, AccentTheme.color(ctx, androidx.appcompat.R.attr.colorPrimary, 0));
            assertEquals(accent.id, accent.color, AccentTheme.color(ctx, androidx.appcompat.R.attr.colorControlActivated, 0));
            if (!"blue".equals(accent.id)) {
                int want = AccentTheme.blend(accent.color, 0xFF1C1C1E, 0.19f);
                int got = AccentTheme.color(ctx, R.attr.streamyAccentSurface, 0);
                for (int shift = 0; shift <= 16; shift += 8) {
                    assertTrue(accent.id, Math.abs(((want >> shift) & 255) - ((got >> shift) & 255)) <= 1);
                }
            }
        }
    }

    @Test public void colourHelpers() {
        assertEquals(0x805B9DFF, AccentTheme.withAlpha(0xFF5B9DFF, 0x80));
        assertEquals(0x005B9DFF, AccentTheme.withAlpha(0xFF5B9DFF, -4));
        assertEquals(0xFF5B9DFF, AccentTheme.blend(0xFF5B9DFF, 0xFF000000, 1f));
        assertEquals(0xFF000000, AccentTheme.blend(0xFF5B9DFF, 0xFF000000, 0f));
        assertEquals(0xFF808080, AccentTheme.blend(0xFFFFFFFF, 0xFF000000, 0.5f));
        assertEquals(0xFFAAAAAA, AccentTheme.color(null, 0, 0xFFAAAAAA));
        assertEquals(0xFF5B9DFF, AccentTheme.accent(null));
    }
}
