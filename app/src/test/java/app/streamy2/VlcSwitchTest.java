package app.streamy2;

import android.app.Application;
import android.os.Looper;
import android.widget.FrameLayout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},application=Application.class)
public class VlcSwitchTest {
    private FakeBackend backend;
    private VlcEngine engine;
    private FrameLayout host;
    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("streamy2",0).edit().clear().commit();
        host=new FrameLayout(RuntimeEnvironment.getApplication());backend=new FakeBackend();
        engine=new VlcEngine(host.getContext(),host,()->backend);engine.prepare();
    }
    @After public void cleanup() throws Exception {
        backend.unblock.countDown();engine.stop(true);
        awaitRelease();
    }
    private void awaitRelease() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(backend.released.getCount()!=0 && System.nanoTime()<deadline) {
            ShadowLooper.idleMainLooper();
            backend.released.await(10,TimeUnit.MILLISECONDS);
        }
        assertEquals("native resources released",0,backend.released.getCount());
    }
    private static void await(CountDownLatch latch) throws Exception { assertTrue("worker completed",latch.await(4,TimeUnit.SECONDS)); }
    private void play(String url) throws Exception { engine.play(url,true);assertEquals(url,backend.played.poll(4,TimeUnit.SECONDS)); }
    @Test public void blockedStopDoesNotBlockUiAndOnlyLatestChannelStarts() throws Exception {
        backend.block=true;engine.play("first",true);await(backend.entered);
        engine.play("second",true);engine.play("last",true);
        AtomicInteger ui=new AtomicInteger();new android.os.Handler(Looper.getMainLooper()).post(ui::incrementAndGet);
        ShadowLooper.idleMainLooper();assertEquals(1,ui.get());assertFalse(backend.stopOnMain);
        backend.unblock.countDown();assertEquals("last",backend.played.poll(4,TimeUnit.SECONDS));
        assertTrue(backend.played.isEmpty());assertEquals(1,backend.attaches);assertEquals(1,host.getChildCount());
    }
    @Test public void lateEventsFromOldChannelCannotCorruptNewPlayback() throws Exception {
        AtomicInteger errors=new AtomicInteger();engine.setPlaybackListener(listener(errors));
        play("old");VlcEngine.Events old=backend.events;play("new");
        old.event(MediaPlayer.Event.EncounteredError,0);old.event(MediaPlayer.Event.TimeChanged,999999);
        backend.events.event(MediaPlayer.Event.TimeChanged,1234);backend.events.event(MediaPlayer.Event.Playing,0);
        ShadowLooper.idleMainLooper();assertEquals(0,errors.get());assertEquals(1234,engine.getPositionMs());assertTrue(engine.isPlaying());
    }
    @Test public void resumedPlaybackUsesReboundListenerAndPreservesSeek() throws Exception {
        AtomicInteger old=new AtomicInteger(),fresh=new AtomicInteger();
        engine.setPlaybackListener(listener(old));play("vod");
        backend.events.event(MediaPlayer.Event.LengthChanged,90000);backend.events.event(MediaPlayer.Event.TimeChanged,42000);
        ShadowLooper.idleMainLooper();assertEquals(90000,engine.getDurationMs());assertEquals(42000,engine.getPositionMs());
        engine.pause();engine.setPlaybackListener(listener(fresh));engine.resume();
        engine.seekToMs(60000);assertEquals(Long.valueOf(60000),backend.seeks.poll(4,TimeUnit.SECONDS));
        backend.events.event(MediaPlayer.Event.EncounteredError,0);ShadowLooper.idleMainLooper();
        assertEquals(0,old.get());assertEquals(1,fresh.get());
    }
    @Test public void endOfFilmDoesNotReportAPlaybackFailure() throws Exception {
        AtomicInteger errors=new AtomicInteger();engine.setPlaybackListener(listener(errors));play("vod");
        backend.events.event(MediaPlayer.Event.Playing,0);ShadowLooper.idleMainLooper();assertTrue(engine.isPlaying());
        backend.events.event(MediaPlayer.Event.EndReached,0);ShadowLooper.idleMainLooper();
        assertFalse(engine.isPlaying());assertEquals(0,errors.get());
    }
    @Test public void vlcAppliesUserBufferChoiceOnNextChannel() throws Exception {
        Prefs prefs=new Prefs(host.getContext());prefs.setBuffer("low");play("low");int low=backend.cache;
        prefs.setBuffer("max");play("max");assertTrue(backend.cache>low);assertEquals(5000,backend.cache);
    }
    @Test public void releasingEngineCancelsQueuedStartAndDoesNotHideNewOwner() throws Exception {
        backend.block=true;engine.play("obsolete",true);await(backend.entered);
        engine.stop(true);FrameLayout replacement=new FrameLayout(host.getContext());host.addView(replacement);
        backend.unblock.countDown();awaitRelease();
        assertTrue(backend.played.isEmpty());assertEquals(1,host.getChildCount());assertSame(replacement,host.getChildAt(0));assertEquals(0,host.getVisibility());
    }
    private static VlcEngine.PlaybackListener listener(AtomicInteger errors) {
        return new VlcEngine.PlaybackListener(){public void onPlaying(){}public void onPaused(){}public void onError(){errors.incrementAndGet();}};
    }
    private static final class FakeBackend implements VlcEngine.Backend {
        volatile VlcEngine.Events events;volatile boolean block,stopOnMain;volatile int attaches,cache;
        final CountDownLatch entered=new CountDownLatch(1),unblock=new CountDownLatch(1),cleanupStop=new CountDownLatch(1),released=new CountDownLatch(1);
        final BlockingQueue<String> played=new LinkedBlockingQueue<>();final BlockingQueue<Long> seeks=new LinkedBlockingQueue<>();
        public void events(VlcEngine.Events e){events=e;}
        public void attach(VLCVideoLayout l){assertEquals(Looper.getMainLooper(),Looper.myLooper());attaches++;}
        public void detach(){assertEquals(Looper.getMainLooper(),Looper.myLooper());}
        public void stop(){
            stopOnMain|=Looper.myLooper()==Looper.getMainLooper();entered.countDown();
            if(block)try{assertTrue(unblock.await(4,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();}
            cleanupStop.countDown();
        }
        public void play(String url,boolean hw,int cacheMs){cache=cacheMs;played.add(url);}
        public void pause(){}public void resume(){}public void seek(long value){seeks.add(value);}
        public void release(){released.countDown();}
    }
}
