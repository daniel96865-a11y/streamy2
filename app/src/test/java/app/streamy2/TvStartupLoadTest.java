package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * 3.92: right after the start, the catalog arrives in several steps (cache, network,
 * Live Extra, library). With ~5000 channels these refreshes must stay cheap and must not
 * pull the remote focus back to the top while the user is already navigating.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TvStartupLoadTest {
    private static final int CHANNELS = 5000;

    private static Context app() {
        return RuntimeEnvironment.getApplication();
    }

    private static ActivityController<MainActivity> launchTv() {
        UiModeManager um = (UiModeManager) app().getSystemService(Context.UI_MODE_SERVICE);
        shadowOf(um).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
        shadowOf(app().getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN, false);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        UiModeManager um2 = (UiModeManager) c.get().getSystemService(Context.UI_MODE_SERVICE);
        shadowOf(um2).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
        c.setup();
        idle(50);
        leaveTouchMode(c.get());
        return c;
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private static void leaveTouchMode(android.app.Activity a) {
        try {
            View decor = a.getWindow().getDecorView();
            Object root = decor.getClass().getMethod("getViewRootImpl").invoke(decor);
            java.lang.reflect.Method m = root.getClass().getDeclaredMethod("ensureTouchMode", boolean.class);
            m.setAccessible(true);
            m.invoke(root, false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static Models.Catalog bigCatalog(String tag) {
        Models.Catalog c = new Models.Catalog();
        c.liveCats.add(new Models.Category("a", "Alle A"));
        c.liveCats.add(new Models.Category("b", "Alle B"));
        for (int i = 0; i < CHANNELS; i++) {
            Models.Channel ch = new Models.Channel();
            ch.number = i + 1;
            ch.id = "ch" + i;
            ch.name = "Sender " + tag + " " + i;
            ch.categoryId = i % 2 == 0 ? "a" : "b";
            ch.categoryName = i % 2 == 0 ? "Alle A" : "Alle B";
            ch.logo = "mark:S" + (i % 10);
            ch.hlsUrl = "http://127.0.0.1/" + i;
            c.live.add(ch);
        }
        return c;
    }

    private static void setCatalog(MainActivity a, Models.Catalog c) throws Exception {
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("catalog");
        f.setAccessible(true);
        f.set(a, c);
        App.live = c.live;
    }

    private static int focusedPosition(MainActivity a) {
        RecyclerView list = a.findViewById(R.id.list);
        View focus = a.getWindow().getDecorView().findFocus();
        if (focus == null) return -2;
        View child = focus;
        while (child != null && child.getParent() != list) {
            child = child.getParent() instanceof View ? (View) child.getParent() : null;
        }
        return child == null ? -1 : list.getChildAdapterPosition(child);
    }

    @Test
    public void backgroundRefreshKeepsRemoteFocusAndSkipsUnchangedList() throws Exception {
        ActivityController<MainActivity> c = launchTv();
        MainActivity a = c.get();
        RecyclerView list = a.findViewById(R.id.list);

        // Cache: first fill, focus on the first channel (as in 3.88).
        setCatalog(a, bigCatalog("cache"));
        a.showFirstCatalog();
        idle(100);
        assertEquals(CHANNELS, list.getAdapter().getItemCount());
        assertEquals(0, focusedPosition(a));

        // The user is already moving down while the rest still loads (focus search like the D-pad).
        for (int k = 0; k < 4; k++) {
            View f = a.getWindow().getDecorView().findFocus();
            View next = f == null ? null : f.focusSearch(View.FOCUS_DOWN);
            if (next != null) next.requestFocus();
            idle(20);
        }
        int pos = focusedPosition(a);
        assertTrue("remote moved down: " + pos, pos > 0);

        // Network catalog arrives (new objects): focus stays on the same row, no jump to the top.
        setCatalog(a, bigCatalog("net"));
        a.showFirstCatalog();
        idle(100);
        assertEquals("focus kept after network refresh", pos, focusedPosition(a));

        // Unchanged data (e.g. library/Live Extra without new channels): no rebind at all.
        final int[] changes = {0};
        list.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { changes[0]++; }
        });
        a.refreshListData();
        a.refreshListData();
        idle(100);
        assertEquals(0, changes[0]);
        assertEquals(pos, focusedPosition(a));

        // A user action (tab Live-TV) still starts at the first row.
        a.findViewById(R.id.tabLive).performClick();
        idle(100);
        assertEquals(0, focusedPosition(a));
        c.pause().stop().destroy();
    }

    @Test
    public void keysDuringRepeatedRefreshesDoNotThrowAndStayInList() throws Exception {
        ActivityController<MainActivity> c = launchTv();
        MainActivity a = c.get();
        setCatalog(a, bigCatalog("0"));
        a.showFirstCatalog();
        idle(50);
        for (int round = 1; round <= 6; round++) {
            for (int k = 0; k < 5; k++) {
                // Keys go through the activity (number keys, chrome, ...) and focus moves like the D-pad.
                a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));
                View f = a.getWindow().getDecorView().findFocus();
                View next = f == null ? null : f.focusSearch(View.FOCUS_DOWN);
                if (next != null) next.requestFocus();
            }
            setCatalog(a, bigCatalog(String.valueOf(round)));
            a.showFirstCatalog();
            idle(30);
        }
        assertTrue("focus still on a channel row", focusedPosition(a) > 0);
        c.pause().stop().destroy();
    }

    @Test
    public void renderingFiveThousandChannelsIsCheap() throws Exception {
        ActivityController<MainActivity> c = launchTv();
        MainActivity a = c.get();
        setCatalog(a, bigCatalog("t"));
        a.showFirstCatalog();
        idle(50);
        for (int i = 0; i < 10; i++) {
            setCatalog(a, bigCatalog("r" + i));
            long s = System.nanoTime();
            a.refreshListData();
            long ms = (System.nanoTime() - s) / 1_000_000L;
            assertTrue("refresh of " + CHANNELS + " channels took " + ms + " ms", ms < 1500);
        }
        c.pause().stop().destroy();
    }

    @Test
    public void nameSortUsesOneKeyPerChannelAndMatchesOldOrder() {
        List<Models.Channel> l = new ArrayList<>();
        for (String n : new String[]{"zdf", "Arte", "ard", "Önce", "3sat", null}) {
            Models.Channel ch = new Models.Channel();
            ch.name = n;
            l.add(ch);
        }
        List<Models.Channel> asc = new ArrayList<>(l);
        MainActivity.sortByName(asc, false);
        List<Models.Channel> old = new ArrayList<>(l);
        old.sort(java.util.Comparator.comparing(x -> (x.name == null ? "" : x.name).toLowerCase(java.util.Locale.GERMAN)));
        assertEquals(old, asc);
        List<Models.Channel> desc = new ArrayList<>(l);
        MainActivity.sortByName(desc, true);
        assertEquals("zdf", desc.get(0).name);
        assertNull(desc.get(desc.size() - 1).name);
    }

    @Test
    public void startupEpgIsDeferred() {
        assertTrue(MainActivity.STARTUP_EPG_DELAY_MS >= 3000L);
    }
}
