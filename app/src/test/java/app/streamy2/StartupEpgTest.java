package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * 3.93: after 3.92 the web EPG did not show up on the phone. After the start the EPG must be
 * loaded and the visible rows must show "Jetzt: …" on mobile and TV, without moving the focus.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class StartupEpgTest {
    private static final SimpleDateFormat XT = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    static { XT.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static Context app() { return RuntimeEnvironment.getApplication(); }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private static String digest(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder r = new StringBuilder();
        for (byte b : hash) r.append(String.format(Locale.US, "%02x", b & 255));
        return r.toString();
    }

    /** Fresh cached web XMLTV files: the refresh hydrates them without any download. */
    @Before
    public void seedWebEpgCache() throws Exception {
        App.guide = null;
        App.live = null;
        long now = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            String id = "S" + i + ".de";
            body.append("<channel id=\"").append(id).append("\"><display-name>Testsender ").append(i).append("</display-name></channel>");
            body.append("<programme start=\"").append(XT.format(new Date(now - 20 * 60000L)))
                    .append("\" stop=\"").append(XT.format(new Date(now + 40 * 60000L)))
                    .append("\" channel=\"").append(id).append("\"><title>Sendung ").append(i).append("</title></programme>");
        }
        byte[] xml = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" + body + "</tv>").getBytes(StandardCharsets.UTF_8);
        File dir = new File(app().getFilesDir(), "epg");
        dir.mkdirs();
        for (String url : EpgSources.WEB_URLS) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, digest(url) + ".xml"))) {
                out.write(xml);
            }
        }
    }

    private static Models.Catalog catalog() {
        Models.Catalog c = new Models.Catalog();
        c.liveCats.add(new Models.Category("7", "Allgemein"));
        for (int i = 0; i < 30; i++) {
            Models.Channel ch = new Models.Channel();
            ch.number = i + 1;
            ch.id = "c" + i;
            ch.name = "Testsender " + i;
            ch.categoryId = "7";
            ch.categoryName = "Allgemein";
            ch.logo = "mark:T";
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

    private static ActivityController<MainActivity> launch(boolean tv) throws Exception {
        UiModeManager um = (UiModeManager) app().getSystemService(Context.UI_MODE_SERVICE);
        shadowOf(um).setCurrentModeType(tv ? Configuration.UI_MODE_TYPE_TELEVISION : Configuration.UI_MODE_TYPE_NORMAL);
        if (tv) {
            shadowOf(app().getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN, false);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        }
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        UiModeManager um2 = (UiModeManager) c.get().getSystemService(Context.UI_MODE_SERVICE);
        shadowOf(um2).setCurrentModeType(tv ? Configuration.UI_MODE_TYPE_TELEVISION : Configuration.UI_MODE_TYPE_NORMAL);
        c.setup();
        // The catalog is there before the startup EPG runs (cache / first paint).
        setCatalog(c.get(), catalog());
        c.get().showFirstCatalog();
        idle(50);
        if (tv) {
            View decor = c.get().getWindow().getDecorView();
            Object root = decor.getClass().getMethod("getViewRootImpl").invoke(decor);
            java.lang.reflect.Method m = root.getClass().getDeclaredMethod("ensureTouchMode", boolean.class);
            m.setAccessible(true);
            m.invoke(root, false);
            c.get().releaseSearchFocus();
            idle(50);
        }
        return c;
    }

    private static String firstRowSub(MainActivity a) {
        RecyclerView list = a.findViewById(R.id.list);
        if (list.getChildCount() == 0) return "<no rows>";
        TextView sub = list.getChildAt(0).findViewById(R.id.sub);
        return sub == null ? "<no sub>" : String.valueOf(sub.getText());
    }

    /** Lets the real IO thread parse the cache while the main looper keeps running. */
    private static String waitForEpgLine(MainActivity a, long maxMs) throws Exception {
        long end = System.currentTimeMillis() + maxMs;
        String sub = firstRowSub(a);
        while (System.currentTimeMillis() < end) {
            idle(250);
            sub = firstRowSub(a);
            if (sub.startsWith("Jetzt:")) return sub;
            Thread.sleep(40);
        }
        return sub;
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
    public void mobileShowsWebEpgAfterStart() throws Exception {
        ActivityController<MainActivity> c = launch(false);
        MainActivity a = c.get();
        assertFalse(Tv.isTv(a));
        assertTrue(a.startupEpgDelayMs() < 1000L);
        String sub = waitForEpgLine(a, 20000);
        assertTrue("phone row shows web EPG: '" + sub + "' guide=" + (App.guide == null ? -1 : App.guide.programmeCount), sub.startsWith("Jetzt: Sendung 0"));
        c.pause().stop().destroy();
    }

    @Test
    public void mobileShowsWebEpgWhenPausedDuringStart() throws Exception {
        ActivityController<MainActivity> c = launch(false);
        MainActivity a = c.get();
        // e.g. the notification permission dialog right after the start
        c.pause();
        idle(100);
        c.resume();
        String sub = waitForEpgLine(a, 20000);
        assertTrue("phone row shows web EPG after pause/resume: '" + sub + "'", sub.startsWith("Jetzt: Sendung 0"));
        c.pause().stop().destroy();
    }

    @Test
    public void tvShowsWebEpgAfterStartAndKeepsFocus() throws Exception {
        ActivityController<MainActivity> c = launch(true);
        MainActivity a = c.get();
        assertTrue(Tv.isTv(a));
        assertTrue("TV keeps the 3.92 startup delay", a.startupEpgDelayMs() >= 3000L);
        // The user moves down before the EPG arrives.
        for (int k = 0; k < 2; k++) {
            View f = a.getWindow().getDecorView().findFocus();
            View next = f == null ? null : f.focusSearch(View.FOCUS_DOWN);
            if (next != null) next.requestFocus();
            idle(20);
        }
        int pos = focusedPosition(a);
        String sub = waitForEpgLine(a, 25000);
        assertTrue("TV row shows web EPG: '" + sub + "'", sub.startsWith("Jetzt: Sendung 0"));
        assertEquals("EPG rebind does not move the focus", pos, focusedPosition(a));
        c.pause().stop().destroy();
    }

    @Test
    public void epgArrivingForUnchangedListRebindsVisibleRowsOnly() throws Exception {
        ActivityController<MainActivity> c = launch(false);
        MainActivity a = c.get();
        idle(50);
        RecyclerView list = a.findViewById(R.id.list);
        final int[] full = {0};
        final int[] ranged = {0};
        list.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { full[0]++; }
            @Override public void onItemRangeChanged(int start, int count, Object payload) { ranged[0]++; }
        });
        // EPG lands on the channel objects outside the activity's own request (e.g. Live Extra).
        Models.Epg e = new Models.Epg();
        e.title = "Direkt";
        e.start = System.currentTimeMillis() - 60000L;
        e.end = System.currentTimeMillis() + 3600000L;
        App.live.get(0).epg = e;
        a.refreshListData();
        idle(300);
        assertEquals("unchanged list: no full rebind", 0, full[0]);
        assertTrue("visible rows rebound with payload", ranged[0] > 0);
        assertTrue(firstRowSub(a), firstRowSub(a).startsWith("Jetzt: Direkt"));
        c.pause().stop().destroy();
    }
}
