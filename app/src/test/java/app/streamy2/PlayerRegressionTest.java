package app.streamy2;

import android.app.Application;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, application=Application.class)
public class PlayerRegressionTest {
    private PlayerActivity activity;
    private org.robolectric.android.controller.ActivityController<PlayerActivity> controller;
    private boolean created;
    @Before public void setup() { controller=Robolectric.buildActivity(PlayerActivity.class); activity=controller.get(); }
    private void startActivity() { controller.create().start().resume(); created=true; }
    @After public void cleanup() { if(created) controller.pause().stop().destroy(); }
    private Object field(String name) throws Exception { Field f=PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void field(String name,Object value) throws Exception { Field f=PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity,value); }
    private Object call(String name,Class<?>[] types,Object... args) throws Exception { Method m=PlayerActivity.class.getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(activity,args); }

    @Test public void intentionalVlcPauseDoesNotTriggerRecovery() throws Exception {
        FakeEngine engine=new FakeEngine(); field("vlc",engine); field("useVlc",true); field("foreground",true); field("userPaused",true); field("extraLiveKeep","test");
        Runnable watchdog=(Runnable)field("watchdog");
        for(int i=0;i<25;i++) watchdog.run();
        assertEquals(0,field("freezeTicks")); assertEquals(0,engine.plays); assertEquals(0,engine.stops); assertTrue((Boolean)field("useVlc"));
    }

    @Test @Config(sdk={28,34}) public void playingLiveStreamWithoutClockIsNotRestarted() throws Exception {
        FakeEngine engine=new FakeEngine() {
            @Override public boolean isPlaying(){return true;}
            @Override public long getPositionMs(){return 0;}
        };
        field("vlc",engine); field("useVlc",true); field("foreground",true); field("liveMode",true);
        Runnable watchdog=(Runnable)field("watchdog");
        try {
            watchdog.run();
            org.robolectric.shadows.ShadowLooper.idleMainLooper(60,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0,engine.plays); assertEquals(0,engine.stops);
            assertEquals(0,field("recoverTries")); assertEquals(0,field("freezeTicks"));
            assertTrue((Boolean)field("useVlc"));
        } finally {
            ((android.os.Handler)field("UI")).removeCallbacks(watchdog);
            field("foreground",false);
        }
    }

    @Test public void exoResumesWithoutStoppingOrLosingItsPosition() throws Exception {
        startActivity();
        ExoPlayer original=(ExoPlayer)field("player");
        original.seekTo(42000L);
        original.setPlayWhenReady(true);
        AtomicBoolean stopped=new AtomicBoolean();
        ExoPlayer exo=(ExoPlayer)Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),new Class[]{ExoPlayer.class},(proxy,method,args)->{
            if(method.getName().equals("stop")) stopped.set(true);
            return method.invoke(original,args);
        });
        field("player",exo);
        androidx.media3.ui.PlayerView view=(androidx.media3.ui.PlayerView)field("playerView");
        view.setPlayer(exo);
        activity.onPause();
        assertFalse(exo.getPlayWhenReady()); assertFalse(stopped.get()); assertEquals(42000L,exo.getCurrentPosition());
        activity.onResume();
        assertTrue(exo.getPlayWhenReady()); assertFalse(stopped.get()); assertEquals(42000L,exo.getCurrentPosition());
        assertSame(exo,view.getPlayer());
    }

    @Test public void vlcPausesInBackgroundAndRespectsManualPauseOnReturn() throws Exception {
        startActivity();
        FakeEngine engine=new FakeEngine(); field("vlc",engine); field("useVlc",true); field("foreground",true);
        activity.onPause(); assertEquals(1,engine.pauses);
        activity.onResume(); assertEquals(1,engine.resumes);
        field("userPaused",true); activity.onPause(); activity.onResume(); assertEquals(1,engine.resumes);
    }

    @Test public void switchingChannelClearsOldEpgAndRejectsOldCallbacks() throws Exception {
        Models.Channel previous=new Models.Channel(); previous.id="old";
        Models.Channel next=new Models.Channel(); next.id="new"; next.name="New"; next.hlsUrl="https://example.com/new.m3u8";
        EpgGuide.Listing old=new EpgGuide.Listing(); old.title="Old programme"; old.start=1; old.stop=Long.MAX_VALUE;
        field("channel",previous); field("current",old); ((List<EpgGuide.Listing>)field("programmes")).add(old);
        field("playerTitle",new TextView(RuntimeEnvironment.getApplication())); field("playerSub",new TextView(RuntimeEnvironment.getApplication()));
        long generation=(Long)field("playbackGeneration");
        call("playChannel",new Class[]{Models.Channel.class},next);
        assertNull(field("current")); assertTrue(((List<?>)field("programmes")).isEmpty()); assertNull(next.epg);
        field("foreground",true);
        assertEquals(false,call("acceptPlayback",new Class[]{long.class},generation));
        assertEquals(true,call("acceptPlayback",new Class[]{long.class},(Long)field("playbackGeneration")));
    }


    @Test public void buildQueueUsesOnlyProviderUrls() throws Exception {
        call("buildQueue", new Class[]{String.class,String.class},
                "https://provider.example/live/123.m3u8",
                "https://provider.example/live/123.ts");
        List<?> queue=(List<?>)field("queue");
        assertEquals(2,queue.size());
        assertEquals("https://provider.example/live/123.m3u8",queue.get(0));
        assertEquals("https://provider.example/live/123.ts",queue.get(1));

        call("buildQueue", new Class[]{String.class,String.class},
                "https://provider.example/live/456", null);
        queue=(List<?>)field("queue");
        assertEquals(1,queue.size());
        assertEquals("https://provider.example/live/456",queue.get(0));
    }

    @Test public void uiHiddenIsNotTreatedAsCriticalMemoryPressure() {
        assertFalse(App.isAggressiveTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN));
        assertTrue(App.isAggressiveTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        assertTrue(App.isAggressiveTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE));
    }

    @Test public void playbackStateLabelsAreReadable() throws Exception {
        assertEquals("Leerlauf", call("playbackStateLabel", new Class[]{int.class}, androidx.media3.common.Player.STATE_IDLE));
        assertEquals("Puffert", call("playbackStateLabel", new Class[]{int.class}, androidx.media3.common.Player.STATE_BUFFERING));
        assertEquals("Bereit", call("playbackStateLabel", new Class[]{int.class}, androidx.media3.common.Player.STATE_READY));
        assertEquals("Beendet", call("playbackStateLabel", new Class[]{int.class}, androidx.media3.common.Player.STATE_ENDED));
    }

    @Test public void slowExtraResolveDoesNotCancelPendingPlayback() throws Exception {
        field("resolving", true);
        TextView error = new TextView(RuntimeEnvironment.getApplication());
        field("errorView", error);
        call("lambda$playCurrent$17", new Class[]{});
        assertTrue((Boolean) field("resolving"));
        assertTrue(error.getText().toString().contains("weiter geprüft"));
    }

    @Test public void vlcBufferChoicesKeepNormalAtTwoAndHalfSeconds() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        prefs.setBuffer("low"); assertEquals(1500, prefs.bufferMs());
        prefs.setBuffer("normal"); assertEquals(2500, prefs.bufferMs());
        prefs.setBuffer("high"); assertEquals(4000, prefs.bufferMs());
        prefs.setBuffer("max"); assertEquals(8000, prefs.bufferMs());
    }

    @Test public void applyResizeSupportsAllThreeModes() throws Exception {
        startActivity();
        new Prefs(RuntimeEnvironment.getApplication()).setResize("zoom");
        call("applyResize", new Class[]{});
        androidx.media3.ui.PlayerView view = (androidx.media3.ui.PlayerView) field("playerView");
        assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, view.getResizeMode());
        TextView btn = (TextView) field("btnResize");
        assertEquals("Füllen", btn.getText().toString());
        new Prefs(RuntimeEnvironment.getApplication()).setResize("fit");
        call("applyResize", new Class[]{});
        assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, view.getResizeMode());
        assertEquals("Anpassen", btn.getText().toString());
        new Prefs(RuntimeEnvironment.getApplication()).setResize("stretch");
        call("applyResize", new Class[]{});
        assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL, view.getResizeMode());
        assertEquals("Strecken", btn.getText().toString());
    }

    @Test public void version346UsesFitAsMobileDefaultOnlyOnce() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("streamy2", 0).edit().clear().commit();
        Prefs prefs = new Prefs(context);
        prefs.setResize("zoom");
        prefs.ensureMobilePlayerDefaults346();
        assertEquals("mobile".equals(BuildConfig.FLAVOR) ? "fit" : "zoom", prefs.resize());
        prefs.setResize("stretch");
        prefs.ensureMobilePlayerDefaults346();
        assertEquals("stretch", prefs.resize());
    }

    private static class FakeEngine implements LiveEngine {
        int plays,stops,pauses,resumes;
        public boolean isPlaying(){return false;} public void pause(){pauses++;} public void play(String url,boolean hw){plays++;}
        public void resume(){resumes++;} public void stop(boolean release){stops++;} public void toggle(){}
        public long getPositionMs(){return 42000;} public long getDurationMs(){return 60000;} public void seekToMs(long value){}
    }
}
