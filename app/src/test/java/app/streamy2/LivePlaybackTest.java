package app.streamy2;

import android.app.Application;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34}, application=Application.class)
public class LivePlaybackTest {
    private PlayerActivity activity;
    private final List<String> played = new ArrayList<>();
    public static class RecordingActivity extends PlayerActivity {
        int plays;
        @Override public void playCurrent() { plays++; }
    }
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("streamy2",0).edit().clear().commit();
        App.api = null; App.guide = null; App.playing = null;
        activity = Robolectric.buildActivity(PlayerActivity.class).get();
        field("foreground",true); field("liveMode",true);
        field("playerTitle",new TextView(activity)); field("playerSub",new TextView(activity));
        field("errorView",new TextView(activity)); field("http",new DefaultHttpDataSource.Factory());
        field("player",Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),new Class[]{ExoPlayer.class},(o,m,args)->{
            if (m.getName().equals("setMediaSource")) played.add(((MediaSource)args[0]).getMediaItem().localConfiguration.uri.toString());
            if (m.getReturnType()==boolean.class) return false;
            if (m.getReturnType()==int.class) return 1;
            if (m.getReturnType()==long.class) return 0L;
            if (m.getReturnType()==float.class) return 1f;
            return null;
        }));
    }
    @After public void cleanup() throws Exception { call("invalidatePlayback"); field("foreground",false); }
    private Object field(String name) throws Exception { Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity); }
    private void field(String name,Object value) throws Exception { Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value); }
    private Object call(String name) throws Exception { return call(name,new Class[0]); }
    private Object call(String name,Class<?>[] types,Object...args) throws Exception { Method m=PlayerActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args); }
    private Models.Channel channel(String id,boolean vavoo) {
        Models.Channel c=new Models.Channel();c.id=id;c.name=id;
        c.hlsUrl="https://example.com/"+id+".m3u8";c.tsUrl="https://example.com/"+id+".ts";
        // A resolved test URL exercises Vavoo dispatch without making provider requests.
        if(vavoo)c.vavooUrl="https://vavoo.to/test/"+id+".m3u8";
        return c;
    }
    private void zap(Models.Channel c) throws Exception { call("playChannel",new Class[]{Models.Channel.class},c); }
    private List<String> queue() throws Exception { return (List<String>)field("queue"); }

    @Test public void zappingVlcReusesEngineAndHonorsVavooPreference() throws Exception {
        Prefs prefs=new Prefs(activity);prefs.setPlayerLive("vlc");prefs.setPlayerVavoo("vlc");
        FakeEngine engine=new FakeEngine();field("vlc",engine);
        zap(channel("first",false));zap(channel("second",true));zap(channel("third",true));
        assertSame(engine,field("vlc"));assertEquals(0,engine.releases);
        assertEquals(3,engine.plays.size());assertTrue(engine.plays.get(2).contains("third"));
        assertEquals("vlc",field("forceEngine"));assertTrue((Boolean)field("useVlc"));
    }
    @Test public void sourceSpecificExoChoiceReleasesVlcAndUsesExo() throws Exception {
        Prefs prefs=new Prefs(activity);prefs.setPlayerLive("vlc");prefs.setPlayerVavoo("exo");
        FakeEngine engine=new FakeEngine();field("vlc",engine);
        zap(channel("first",false));zap(channel("second",true));
        assertEquals(1,engine.releases);assertFalse((Boolean)field("useVlc"));
        assertEquals("https://vavoo.to/test/second.m3u8",played.get(0));
    }
    @Test public void stalledLiveTriesAlternateUrlAndStopsAfterBoundedAttempts() throws Exception {
        new Prefs(activity).setPlayerLive("exo");
        zap(channel("live",false));call("recoverLivePlayback");
        assertEquals(Arrays.asList("https://example.com/live.m3u8","https://example.com/live.ts"),played);
        call("recoverLivePlayback");call("recoverLivePlayback");call("recoverLivePlayback");
        assertTrue((Boolean)field("recoveryExhausted"));
        int count=played.size();call("recoverLivePlayback");assertEquals(count,played.size());
        assertEquals("exo",field("forceEngine"));
        zap(channel("next",false));assertFalse((Boolean)field("recoveryExhausted"));assertEquals(0,field("recoverTries"));
    }
    @Test public void lateRecoveryCannotRestartPreviousChannel() throws Exception {
        new Prefs(activity).setPlayerLive("exo");zap(channel("old",false));
        call("scheduleLiveRecovery");zap(channel("new",false));
        ShadowLooper.idleMainLooper(300,TimeUnit.MILLISECONDS);
        assertEquals(2,played.size());assertTrue(played.get(1).contains("new"));assertEquals(0,field("recoverTries"));
    }
    @Test public void explicitPauseNeverSchedulesRecovery() throws Exception {
        zap(channel("live",false));field("userPaused",true);
        call("scheduleLiveRecovery");ShadowLooper.idleMainLooper(300,TimeUnit.MILLISECONDS);
        assertEquals(1,played.size());assertFalse((Boolean)field("recoveryScheduled"));
    }
    @Test public void vavooPrefetchRestartsAfterEachZap() throws Exception {
        new Prefs(activity).setPlayerVavoo("exo");
        for(String id:Arrays.asList("one","two")) {
            zap(channel(id,true));
            ShadowLooper.idleMainLooper(12,TimeUnit.SECONDS);
            Future<?> job=(Future<?>)field("prefetchJob");
            if(job!=null)job.get(3,TimeUnit.SECONDS); // the completion may already have reached UI

            ShadowLooper.idleMainLooper();
            assertEquals("https://vavoo.to/test/"+id+".m3u8",field("vavooHot"));
        }
    }
    private RecordingActivity recordRecovery() throws Exception {
        activity=Robolectric.buildActivity(RecordingActivity.class).get();
        field("foreground",true);field("liveMode",true);field("vavooKeep","https://vavoo.to/vavoo-iptv/play/test");
        queue().add("https://example.com/current.m3u8");field("index",0);
        return (RecordingActivity)activity;
    }
    @Test public void vavooRecoveryRejectsSameOrExpiredReplacement() throws Exception {
        RecordingActivity recorder=recordRecovery();
        field("vavooHot","https://example.com/current.m3u8");field("vavooHotAt",SystemClock.elapsedRealtime());
        call("swapVavoo",new Class[]{boolean.class},true);
        assertEquals(field("vavooKeep"),queue().get(0));assertNull(field("vavooHot"));assertEquals(1,recorder.plays);
        queue().set(0,"https://example.com/current.m3u8");field("vavooHot","https://example.com/expired.m3u8");
        field("vavooHotAt",SystemClock.elapsedRealtime()-31000);
        call("swapVavoo",new Class[]{boolean.class},true);assertEquals(field("vavooKeep"),queue().get(0));
    }
    @Test public void vavooRecoveryUsesFreshDifferentReplacementOnlyOnce() throws Exception {
        recordRecovery();field("vavooHot","https://example.com/new.m3u8");field("vavooHotAt",SystemClock.elapsedRealtime());
        call("swapVavoo",new Class[]{boolean.class},true);assertEquals("https://example.com/new.m3u8",queue().get(0));
        call("swapVavoo",new Class[]{boolean.class},true);assertEquals(field("vavooKeep"),queue().get(0));
    }
    @Test public void canceledVavooResolveDoesNotStartAnotherRequest() {
        Thread.currentThread().interrupt();
        try { assertNull(Vavoo.resolve("https://vavoo.to/vavoo-iptv/play/canceled")); }
        finally { Thread.interrupted(); }
    }
    @Test public void progressDetectsStuckPlayingFlagAndAllowsSlowStartup() {
        LivePlaybackHealth health=new LivePlaybackHealth();health.reset(0);
        assertFalse(health.stalled(11000,0,false));assertTrue(health.stalled(12000,0,false));
        health.reset(20000);health.stalled(21000,1000,true);health.stalled(22000,2000,true);
        assertFalse(health.stalled(29000,2000,true));assertTrue(health.stalled(30000,2000,true));
    }
    @Test public void progressingLiveClockAndTimelineResetDoNotTriggerRecovery() {
        LivePlaybackHealth health=new LivePlaybackHealth();health.reset(0);
        for(int i=1;i<35;i++)assertFalse(health.stalled(i*1000L,(i%25)*1000L,true));
        assertTrue(health.stable(34000));
    }
    private static class FakeEngine implements LiveEngine {
        final List<String> plays=new ArrayList<>();int releases;
        public boolean isPlaying(){return false;}public void pause(){}public void resume(){}public void toggle(){}
        public void play(String url,boolean hw){plays.add(url);}public void stop(boolean release){if(release)releases++;}
        public long getPositionMs(){return 0;}public long getDurationMs(){return 0;}public void seekToMs(long p){}
    }
}
