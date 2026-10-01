package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** 3.91: the 3.89 "Start" tab is gone; the timeline guide opens from Live-TV ("Programm") or the Guide key. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class)
public class StartTabRemovedTest {

    private static Context app() {
        return RuntimeEnvironment.getApplication();
    }

    private static void makeTv(Context context) {
        UiModeManager um = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (um != null) shadowOf(um).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
    }

    private static ActivityController<MainActivity> launch(boolean tv) {
        if (tv) makeTv(app());
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        if (tv) makeTv(c.get());
        c.setup();
        ShadowLooper.idleMainLooper();
        return c;
    }

    static int tabOf(MainActivity a) throws Exception {
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("tab");
        f.setAccessible(true);
        return f.getInt(a);
    }

    private static boolean hasText(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText()) && v.getVisibility() == View.VISIBLE) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasText(g.getChildAt(i), text)) return true;
        }
        return false;
    }

    @Test
    public void startScreenResourcesAndClassesAreGone() {
        String pkg = app().getPackageName();
        for (String id : new String[]{"tabHome", "homePane", "homeBody"}) {
            assertEquals(id, 0, app().getResources().getIdentifier(id, "id", pkg));
        }
        for (String cls : new String[]{"app.streamy2.HomeScreen", "app.streamy2.HomeRows"}) {
            try {
                Class.forName(cls);
                fail(cls + " still exists");
            } catch (ClassNotFoundException expected) {
                // removed in 3.91
            }
        }
    }

    @Test
    public void phoneLandsOnLiveTvWithoutStartTab() throws Exception {
        ActivityController<MainActivity> c = launch(false);
        MainActivity a = c.get();
        assertEquals("Live-TV is the first screen", 0, tabOf(a));
        View tabs = a.findViewById(R.id.tabs);
        assertFalse("no Start tab", hasText(tabs, "Start"));
        assertEquals(View.VISIBLE, a.findViewById(R.id.list).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void programmChipOpensTimelineGuideOnlyInLiveTv() throws Exception {
        ActivityController<MainActivity> c = launch(true);
        MainActivity a = c.get();
        assertEquals(0, tabOf(a));
        View chip = a.findViewById(R.id.chipGuide);
        assertNotNull(chip);
        assertEquals(View.VISIBLE, chip.getVisibility());
        assertTrue(chip.isFocusable());
        chip.performClick();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertNotNull("guide opened", next);
        assertEquals(GuideActivity.class.getName(), next.getComponent().getClassName());
        // Other sections (Filme) do not show the chip.
        a.findViewById(R.id.tabMovies).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(View.GONE, chip.getVisibility());
        a.findViewById(R.id.tabLive).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(View.VISIBLE, chip.getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void guideKeyOpensTimelineGuide() {
        ActivityController<MainActivity> c = launch(true);
        MainActivity a = c.get();
        assertTrue(a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_GUIDE)));
        Intent next = shadowOf(a).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(GuideActivity.class.getName(), next.getComponent().getClassName());
        c.pause().stop().destroy();
    }

    @Test
    public void guideHasNoFavouriteButton() {
        App.live = DemoCatalog.build().live;
        ActivityController<GuideActivity> c = Robolectric.buildActivity(GuideActivity.class).setup();
        ShadowLooper.idleMainLooper();
        View root = c.get().getWindow().getDecorView();
        assertTrue(hasText(root, "Jetzt ansehen") || hasText(root, "Live ansehen"));
        assertFalse(hasText(root, "Favorit"));
        assertFalse(hasText(root, "Favorit entfernen"));
        c.pause().stop().destroy();
    }
}
