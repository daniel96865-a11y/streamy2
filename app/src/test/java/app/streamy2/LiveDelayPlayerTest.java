package app.streamy2;

import android.app.Application;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Live-Verzögerung in the Exo live configuration, prefs and the VLC engine. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LiveDelayPlayerTest {
    private ActivityController<PlayerActivity> controller;
    private PlayerActivity activity;

    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("streamy2", 0).edit().clear().commit();
        controller = Robolectric.buildActivity(PlayerActivity.class);
        controller.create().start().resume();
        activity = controller.get();
    }

    @After public void cleanup() {
        controller.pause().stop().destroy();
    }

    private void set(String name, boolean value) throws Exception {
        Field f = PlayerActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setBoolean(activity, value);
    }

    @Test public void prefDefaultsToOff() {
        Prefs p = new Prefs(activity);
        assertEquals(0, p.liveDelay());
        p.setLiveDelay(20);
        assertEquals(20, p.liveDelay());
        p.setLiveDelay(17);
        assertEquals(0, p.liveDelay());
    }

    @Test public void offKeepsPreviousLiveConfiguration() throws Exception {
        set("liveMode", true);
        set("catchup", false);
        MediaItem.LiveConfiguration c = activity.liveConfiguration("http://x/live.m3u8");
        assertEquals(C.TIME_UNSET, c.targetOffsetMs);
        assertEquals(C.TIME_UNSET, c.minOffsetMs);
        assertEquals(0.96f, c.minPlaybackSpeed, 0.001f);
        assertEquals(1.04f, c.maxPlaybackSpeed, 0.001f);
    }

    @Test public void delaySetsTargetOffsetAndReusesExactTargetOnRestart() throws Exception {
        new Prefs(activity).setLiveDelay(20);
        set("liveMode", true);
        set("catchup", false);
        assertEquals(20000L, activity.currentLiveDelayMs());
        MediaItem.LiveConfiguration first = activity.liveConfiguration("http://x/live.m3u8");
        assertEquals(LiveDelay.ASSUMED_BASE_MS + 20000L, first.targetOffsetMs);
        assertEquals(C.TIME_UNSET, first.minOffsetMs); // window unknown yet
        // After the playlist was seen (e.g. window-clamped 32 s): "Live"/restart goes exactly there.
        activity.liveTargetByUrl.put("http://x/live.m3u8", 32000L);
        MediaItem.LiveConfiguration again = activity.liveConfiguration("http://x/live.m3u8");
        assertEquals(32000L, again.targetOffsetMs);
        assertEquals(20000L, again.minOffsetMs); // never closer than the delay
        // Other channel: own estimate.
        assertEquals(38000L, activity.liveConfiguration("http://y/other.m3u8").targetOffsetMs);
    }

    @Test public void noDelayForCatchupOrVod() throws Exception {
        new Prefs(activity).setLiveDelay(30);
        set("liveMode", true);
        set("catchup", true);
        assertEquals(0L, activity.currentLiveDelayMs());
        assertEquals(C.TIME_UNSET, activity.liveConfiguration("http://x/a.m3u8").targetOffsetMs);
        set("liveMode", false);
        set("catchup", false);
        assertEquals(0L, activity.currentLiveDelayMs());
    }

    @Test public void vlcEngineTakesDelay() {
        VlcEngine e = new VlcEngine(activity, null);
        assertEquals(0L, e.liveDelayMs());
        e.setLiveDelayMs(20000L);
        assertEquals(20000L, e.liveDelayMs());
        e.setLiveDelayMs(-5L);
        assertEquals(0L, e.liveDelayMs());
    }
}
