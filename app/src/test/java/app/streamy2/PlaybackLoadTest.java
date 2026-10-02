package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * 3.94: on TV sticks the app was killed a few minutes into a stream. While the player runs no
 * XMLTV download/parse and no list work may happen, an Error on the EPG thread must not kill
 * the process, and weak devices only keep programmes of playlist channels.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaybackLoadTest {
    private static final SimpleDateFormat XT = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    static { XT.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private Thread.UncaughtExceptionHandler savedHandler;
    private final List<Throwable> uncaught = Collections.synchronizedList(new ArrayList<>());

    private static Context app() { return RuntimeEnvironment.getApplication(); }

    private static void idle(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    private static String digest(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder r = new StringBuilder();
        for (byte b : hash) r.append(String.format(Locale.US, "%02x", b & 255));
        return r.toString();
    }

    private static String xmltv(int channels) {
        long now = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < channels; i++) {
            String id = "S" + i + ".de";
            body.append("<channel id=\"").append(id).append("\"><display-name>Testsender ").append(i).append("</display-name></channel>");
        }
        for (int i = 0; i < channels; i++) {
            body.append("<programme start=\"").append(XT.format(new Date(now - 20 * 60000L)))
                    .append("\" stop=\"").append(XT.format(new Date(now + 40 * 60000L)))
                    .append("\" channel=\"S").append(i).append(".de\"><title>Sendung ").append(i).append("</title></programme>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" + body + "</tv>";
    }

    private static File[] seedCache(long mtime) throws Exception {
        byte[] xml = xmltv(30).getBytes(StandardCharsets.UTF_8);
        File dir = new File(app().getFilesDir(), "epg");
        dir.mkdirs();
        File[] out = new File[EpgSources.WEB_URLS.length];
        for (int k = 0; k < out.length; k++) {
            out[k] = new File(dir, digest(EpgSources.WEB_URLS[k]) + ".xml");
            try (FileOutputStream o = new FileOutputStream(out[k])) { o.write(xml); }
            assertTrue(out[k].setLastModified(mtime));
        }
        return out;
    }

    private static List<Models.Channel> channels(int n) {
        List<Models.Channel> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Models.Channel ch = new Models.Channel();
            ch.number = i + 1;
            ch.id = "c" + i;
            ch.name = "Testsender " + i;
            ch.categoryId = "7";
            ch.categoryName = "Allgemein";
            ch.hlsUrl = "http://127.0.0.1/" + i;
            l.add(ch);
        }
        return l;
    }

    /** Runs one EpgRefresh request and waits (real IO thread) for its completion. */
    private static String refreshAndWait() throws Exception {
        final AtomicReference<String> done = new AtomicReference<>();
        final boolean[] called = {false};
        boolean started = EpgRefresh.request(app(), false, e -> { called[0] = true; done.set(e); });
        long end = System.currentTimeMillis() + 20000;
        while (!called[0] && System.currentTimeMillis() < end) {
            Thread.sleep(30);
            idle(10);
        }
        assertTrue("refresh finished (started=" + started + ")", called[0]);
        return done.get();
    }

    @Before
    public void setUp() {
        savedHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> uncaught.add(e));
        App.guide = new EpgGuide();
        App.live = null;
        App.playerOpen = false;
        EpgRefresh.memoryTight = false;
    }

    @After
    public void tearDown() {
        App.playerOpen = false;
        App.live = null;
        EpgRefresh.memoryTight = false;
        Thread.setDefaultUncaughtExceptionHandler(savedHandler);
    }

    @Test
    public void noXmltvDownloadOrParseWhilePlayerRuns() throws Exception {
        // Two days old: outside playback this would be re-downloaded and parsed.
        long old = System.currentTimeMillis() - 2 * 24 * 3600000L;
        File[] cache = seedCache(old);
        App.live = channels(30);
        App.playerOpen = true;
        assertNull(refreshAndWait());
        assertEquals("no XMLTV parse during playback", 0, App.guide.programmeCount);
        assertTrue("refresh is caught up later", EpgRefresh.deferredWhilePlaying);
        for (File f : cache) {
            assertEquals("no download during playback", old / 1000, f.lastModified() / 1000);
            assertFalse(new File(f.getPath() + ".part").exists());
        }
        // Player closed: fresh cache is parsed again.
        seedCache(System.currentTimeMillis());
        App.playerOpen = false;
        refreshAndWait();
        assertTrue("EPG loaded after playback", App.guide.programmeCount > 0);
        assertFalse(EpgRefresh.deferredWhilePlaying);
        assertTrue(EpgTime.isCurrent(App.live.get(3).epg, System.currentTimeMillis()));
        assertTrue(uncaught.toString(), uncaught.isEmpty());
    }

    /** A list whose copy throws like a heap exhausted in the middle of apply(). */
    private static final class OomList extends ArrayList<Models.Channel> {
        @Override public Object[] toArray() { throw new OutOfMemoryError("test"); }
        @Override public <T> T[] toArray(T[] a) { throw new OutOfMemoryError("test"); }
    }

    @Test
    public void outOfMemoryOnEpgThreadDoesNotKillTheApp() throws Exception {
        seedCache(System.currentTimeMillis());
        OomList live = new OomList();
        live.addAll(channels(5));
        App.live = live;
        String error = refreshAndWait();
        Thread.sleep(200);
        assertTrue("no uncaught Error on the EPG thread: " + uncaught, uncaught.isEmpty());
        assertNotNull(error);
        assertTrue(error, error.contains("Arbeitsspeicher"));
        assertTrue(EpgRefresh.memoryTight);
        assertFalse("EPG thread is free again", App.guide.loading);
        // Next refresh works normally.
        App.live = channels(5);
        assertNull(refreshAndWait());
    }

    @Test
    public void weakDevicesKeepOnlyPlaylistChannels() throws Exception {
        File f = new File(app().getCacheDir(), "big.xml");
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(xmltv(200).getBytes(StandardCharsets.UTF_8)); }
        List<Models.Channel> playlist = channels(5);
        Models.Channel byId = new Models.Channel();
        byId.id = "x";
        byId.name = "Ganz anderer Name";
        byId.epgChannelId = "S150.de";
        playlist.add(byId);

        EpgGuide all = new EpgGuide();
        all.loadFileMerge(f, true);
        EpgGuide filtered = new EpgGuide();
        filtered.setRelevance(playlist);
        filtered.loadFileMerge(f, true);

        assertEquals(200, all.channelCount);
        assertEquals("only playlist channels kept", 6, filtered.channelCount);
        assertTrue(filtered.programmeCount < all.programmeCount / 10);
        for (Models.Channel c : playlist) {
            Models.Epg a = all.forChannel(c, false);
            Models.Epg b = filtered.forChannel(c, false);
            assertNotNull(c.name, b);
            assertEquals(c.name, a.title, b.title);
        }
        // A file without any playlist channel is no error (other feeds may have them).
        EpgGuide none = new EpgGuide();
        List<Models.Channel> other = new ArrayList<>();
        Models.Channel o = new Models.Channel();
        o.id = "q";
        o.name = "Unbekannt";
        other.add(o);
        none.setRelevance(other);
        none.loadFileMerge(f, true);
        assertEquals(0, none.programmeCount);
    }

    @Test
    public void listAndEpgWorkWaitWhilePlayerIsInFront() throws Exception {
        UiModeManager um = (UiModeManager) app().getSystemService(Context.UI_MODE_SERVICE);
        shadowOf(um).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        UiModeManager um2 = (UiModeManager) c.get().getSystemService(Context.UI_MODE_SERVICE);
        shadowOf(um2).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
        c.setup();
        MainActivity a = c.get();
        Models.Catalog first = new Models.Catalog();
        first.liveCats.add(new Models.Category("7", "Allgemein"));
        first.live.addAll(channels(20));
        setCatalog(a, first);
        a.showFirstCatalog();
        idle(100);
        RecyclerView list = a.findViewById(R.id.list);
        assertEquals(20, list.getAdapter().getItemCount());
        final int[] changes = {0};
        list.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { changes[0]++; }
            @Override public void onItemRangeChanged(int s, int n, Object p) { changes[0]++; }
        });

        while (shadowOf(a).getNextStartedActivity() != null) { /* drop starts from onCreate */ }
        // A channel is opened: MainActivity goes to the background, the player runs.
        App.playerOpen = true;
        c.pause().stop();
        Models.Catalog net = new Models.Catalog();
        net.liveCats.add(new Models.Category("7", "Allgemein"));
        net.live.addAll(channels(25));
        setCatalog(a, net);
        a.showFirstCatalog();   // network catalog arrives behind the player
        a.onEpgArrived();       // EPG completion behind the player
        idle(65000);            // > one epgTick period
        assertEquals("no list work while the player runs", 0, changes[0]);
        assertEquals(20, list.getAdapter().getItemCount());
        assertFalse(a.isFinishing());
        assertNull("nothing starts an activity over the player", shadowOf(a).getNextStartedActivity());

        // Back from the player: the list catches up.
        App.playerOpen = false;
        c.restart().resume();
        idle(500);
        assertEquals(25, list.getAdapter().getItemCount());
        c.pause().stop().destroy();
        assertTrue(uncaught.toString(), uncaught.isEmpty());
    }

    private static void setCatalog(MainActivity a, Models.Catalog cat) throws Exception {
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("catalog");
        f.setAccessible(true);
        f.set(a, cat);
        App.live = cat.live;
    }
}
