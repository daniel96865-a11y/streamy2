package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveDelayTest {
    @Test public void settingValues() {
        assertEquals(0, LiveDelay.normalize(0));
        assertEquals(20, LiveDelay.normalize(20));
        assertEquals(0, LiveDelay.normalize(15));
        assertEquals(0, LiveDelay.normalize(-10));
        assertEquals("Aus", LiveDelay.label(0));
        assertEquals("30 s", LiveDelay.label(30));
    }

    @Test public void onlyLiveChannels() {
        assertEquals(20000L, LiveDelay.delayMs(20, true, false));
        assertEquals(0L, LiveDelay.delayMs(20, false, false)); // VOD
        assertEquals(0L, LiveDelay.delayMs(20, true, true));   // catch-up / archive
        assertEquals(0L, LiveDelay.delayMs(0, true, false));   // Aus
    }

    @Test public void offIsPreviousBehaviour() {
        assertEquals(LiveDelay.UNSET, LiveDelay.targetOffsetMs(0, 18000, 60000));
        int[] b = LiveDelay.buffers(0, 1800, 7000, 1000, 1600, false);
        assertArrayEquals(new int[]{1800, 7000, 1000, 1600}, b);
        assertEquals(0, LiveDelay.vlcLiveDelayMs(0));
        assertEquals(0, LiveDelay.vlcMaxBufferMs(0));
        assertEquals("LIVE", LiveDelay.badge(0, false));
        assertEquals("ARCHIV", LiveDelay.badge(0, true));
    }

    @Test public void streamDefaultOffset() {
        assertEquals(12000L, LiveDelay.streamDefaultOffsetMs(12000, 6000)); // HOLD-BACK wins
        assertEquals(18000L, LiveDelay.streamDefaultOffsetMs(0, 6000));     // 3 × target duration
        assertEquals(30000L, LiveDelay.streamDefaultOffsetMs(0, 10000));
        assertEquals(LiveDelay.ASSUMED_BASE_MS, LiveDelay.streamDefaultOffsetMs(0, 0));
    }

    @Test public void targetIsDefaultPlusDelayClampedToWindow() {
        // Long window: 18 s default + 20 s.
        assertEquals(38000L, LiveDelay.targetOffsetMs(20000, 18000, 120000));
        // Unknown window (before the playlist): not clamped.
        assertEquals(48000L, LiveDelay.targetOffsetMs(30000, 18000, 0));
        // 40 s window: at most 40 - 8 (safety) = 32 s, still more than the default.
        assertEquals(32000L, LiveDelay.targetOffsetMs(30000, 18000, 40000));
        // Short window (24 s): only up to the safety margin (24 - 4.8 s), never beyond the window.
        assertEquals(19200L, LiveDelay.targetOffsetMs(30000, 18000, 24000));
        // Window even shorter than the default: stays inside the window.
        assertEquals(12000L, LiveDelay.targetOffsetMs(10000, 18000, 12000));
        for (long w = 6000; w <= 200000; w += 1000) {
            long t = LiveDelay.targetOffsetMs(20000, 18000, w);
            assertTrue("window " + w, t <= w);
            assertTrue("window " + w, t >= Math.min(18000, w));
        }
    }

    @Test public void seekOnlyWhenNeeded() {
        assertFalse(LiveDelay.needsSeek(37000, 38000));
        assertTrue(LiveDelay.needsSeek(18000, 38000));
        assertFalse(LiveDelay.needsSeek(-1, 38000));
        assertFalse(LiveDelay.needsSeek(18000, LiveDelay.UNSET));
        // Window 120 s, at 102 s (18 s behind): seek to 82 s (38 s behind).
        assertEquals(82000L, LiveDelay.seekPositionMs(102000, 18000, 38000, 120000));
        // Never before the safety margin at the window start.
        assertEquals(8000L, LiveDelay.seekPositionMs(30000, 10000, 60000, 40000));
    }

    @Test public void bufferGrowsWithDelayButStartStaysFast() {
        int[] b = LiveDelay.buffers(20000, 1800, 7000, 1000, 1600, false);
        assertEquals(20000, b[0]);
        assertEquals(30000, b[1]);
        assertEquals(1000, b[2]); // bufferForPlayback unchanged: zapping as fast as before
        assertTrue(b[3] >= 2500 && b[3] <= b[0]);
        int[] low = LiveDelay.buffers(30000, 2000, 8000, 1500, 2000, true);
        assertTrue(low[1] <= 25000); // low-RAM devices capped
        assertTrue(low[0] < low[1]);
        assertEquals(1500, low[2]);
        int[] big = LiveDelay.buffers(10000, 8000, 30000, 2500, 5000, false);
        assertEquals(30000, big[1]); // already large buffers are kept
        assertEquals(10000, big[0]);
    }

    @Test public void vlcOptions() {
        assertEquals(35000, LiveDelay.vlcLiveDelayMs(20000));
        assertEquals(45000, LiveDelay.vlcLiveDelayMs(30000));
        assertEquals(40000, LiveDelay.vlcMaxBufferMs(20000));
        assertEquals(30000, LiveDelay.vlcMaxBufferMs(10000));
    }

    @Test public void badgeShowsDelay() {
        assertEquals("LIVE \u221220 s", LiveDelay.badge(20000, false));
        assertEquals("ARCHIV", LiveDelay.badge(20000, true));
    }
}
