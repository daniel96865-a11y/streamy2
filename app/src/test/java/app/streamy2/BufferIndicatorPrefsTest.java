package app.streamy2;

import android.app.Application;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class BufferIndicatorPrefsTest {
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("streamy2", 0).edit().clear().commit();
    }

    @Test public void defaultsToWithControls() {
        assertEquals(BufferStats.MODE_HUD, new Prefs(context).bufferIndicator());
    }

    @Test public void persistsAndNormalizes() {
        Prefs prefs = new Prefs(context);
        prefs.setBufferIndicator(BufferStats.MODE_ALWAYS);
        assertEquals(BufferStats.MODE_ALWAYS, new Prefs(context).bufferIndicator());
        prefs.setBufferIndicator(BufferStats.MODE_OFF);
        assertEquals(BufferStats.MODE_OFF, new Prefs(context).bufferIndicator());
        prefs.setBufferIndicator("nonsense");
        assertEquals(BufferStats.MODE_HUD, new Prefs(context).bufferIndicator());
    }

    @Test public void engineDefaultsAreSafe() {
        LiveEngine engine = new LiveEngine() {
            public boolean isPlaying() { return false; }
            public void pause() {}
            public void play(String s, boolean z) {}
            public void resume() {}
            public void stop(boolean z) {}
            public void toggle() {}
            public long getPositionMs() { return 0L; }
            public long getDurationMs() { return 0L; }
            public void seekToMs(long j) {}
        };
        engine.refreshStats();
        assertEquals(-1f, engine.bufferingPercent(), 0f);
        assertEquals(0, engine.rebufferCount());
        assertEquals(0L, engine.inputBitrateBps());
        assertEquals(0L, engine.demuxBitrateBps());
        assertEquals(0, engine.lostPictures());
    }
}
