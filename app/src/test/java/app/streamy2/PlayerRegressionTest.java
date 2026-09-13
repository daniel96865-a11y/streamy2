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
        FakeEngine engine=new FakeEngine(); field("vlc",engine); field("useVlc",true); field("foreground",true); field("userPaused",true); field("vavooKeep","test");
        Runnable watchdog=(Runnable)field("watchdog");
        for(int i=0;i<25;i++) watchdog.run();
        assertEquals(0,field("freezeTicks")); assertEquals(0,engine.plays); assertEquals(0,engine.stops); assertTrue((Boolean)field("useVlc"));
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

    private static class FakeEngine implements LiveEngine {
        int plays,stops,pauses,resumes;
        public boolean isPlaying(){return false;} public void pause(){pauses++;} public void play(String url,boolean hw){plays++;}
        public void resume(){resumes++;} public void stop(boolean release){stops++;} public void toggle(){}
        public long getPositionMs(){return 42000;} public long getDurationMs(){return 60000;} public void seekToMs(long value){}
    }
}
