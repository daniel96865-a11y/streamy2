package app.streamy2;

import static org.junit.Assert.*;

import org.junit.Test;

/** "Farbwellen" start animation timing (3.90). */
public class IntroTimingTest {
    private static final long T0 = 10_000L;

    @Test public void readyEarlyStillShowsMinimumAndEndsGracefully() {
        IntroTiming t = new IntroTiming(T0, false);
        t.markReady(T0 + 100);
        assertEquals(IntroTiming.State.FADING_IN, t.state(T0 + 200));
        assertTrue(t.wavesAlpha(T0 + 250) > 0f && t.wavesAlpha(T0 + 250) < 1f);
        assertEquals(1f, t.backgroundAlpha(T0), 0.0001f);
        assertEquals(IntroTiming.State.SHOWING, t.state(T0 + 1000));
        assertEquals(1f, t.wavesAlpha(T0 + 1000), 0.0001f);
        // Fade-out begins at the minimum hold time, not at "ready".
        assertEquals(T0 + IntroTiming.MIN_HOLD_MS, t.update(T0 + 1500));
        assertEquals(IntroTiming.State.FADING_OUT, t.state(T0 + 1600));
        float mid = t.backgroundAlpha(T0 + 1650);
        assertTrue(mid > 0f && mid < 1f);
        assertTrue(t.done(T0 + IntroTiming.MIN_HOLD_MS + IntroTiming.FADE_OUT_MS));
        // Visible time ~1.9 s: within the 1.5–2.5 s target.
        long total = IntroTiming.MIN_HOLD_MS + IntroTiming.FADE_OUT_MS;
        assertTrue(total >= 1500 && total <= 2500);
        assertEquals(0f, t.hintAlpha(T0 + 1700), 0f);
    }

    @Test public void fadeInIsFast() {
        IntroTiming t = new IntroTiming(T0, false);
        assertEquals(0f, t.wavesAlpha(T0), 0.0001f);
        assertEquals(1f, t.wavesAlpha(T0 + IntroTiming.FADE_IN_MS), 0.0001f);
        assertTrue(IntroTiming.FADE_IN_MS <= 600);
        // Background covers the app from the first frame (no flash of the half-built screen).
        assertEquals(1f, t.backgroundAlpha(T0), 0.0001f);
    }

    @Test public void slowLoadingKeepsFlowingWithHintAfterThreeSeconds() {
        IntroTiming t = new IntroTiming(T0, false);
        assertEquals(IntroTiming.State.SHOWING, t.state(T0 + 2500));
        assertEquals(0f, t.hintAlpha(T0 + 2900), 0f);
        assertTrue(t.hintAlpha(T0 + 3500) > 0.5f);
        assertEquals(IntroTiming.State.SHOWING, t.state(T0 + 6000));
        // Waves are not frozen: animation time keeps running.
        assertTrue(t.seconds(T0 + 6000) > t.seconds(T0 + 5000));
        t.markReady(T0 + 6200);
        assertEquals(T0 + 6200, t.update(T0 + 6300));
        assertTrue(t.done(T0 + 6200 + IntroTiming.FADE_OUT_MS));
    }

    @Test public void hardCapEndsEvenIfNeverReady() {
        IntroTiming t = new IntroTiming(T0, false);
        assertFalse(t.done(T0 + IntroTiming.MAX_MS - 1));
        assertEquals(T0 + IntroTiming.MAX_MS, t.update(T0 + IntroTiming.MAX_MS));
        assertTrue(t.done(T0 + IntroTiming.MAX_MS + IntroTiming.FADE_OUT_MS));
    }

    @Test public void keyPressSkips() {
        IntroTiming t = new IntroTiming(T0, false);
        t.skip(T0 + 300);
        assertEquals(IntroTiming.State.FADING_OUT, t.state(T0 + 400));
        assertTrue(t.done(T0 + 300 + IntroTiming.FADE_OUT_MS));
        t.markReady(T0 + 900); // later "ready" does not restart anything
        assertTrue(t.done(T0 + 1000));
    }

    @Test public void reducedMotionShowsStaticGradientWithoutFades() {
        IntroTiming t = new IntroTiming(T0, true);
        assertEquals(1f, t.wavesAlpha(T0), 0f); // no fade-in
        assertEquals(IntroTiming.STATIC_T, t.seconds(T0 + 100), 0f);
        assertEquals(IntroTiming.STATIC_T, t.seconds(T0 + 5000), 0f); // static frame
        t.markReady(T0 + 50);
        assertFalse(t.done(T0 + IntroTiming.REDUCED_MIN_MS - 1));
        assertTrue(t.done(T0 + IntroTiming.REDUCED_MIN_MS)); // no fade-out either
        assertEquals(0f, t.backgroundAlpha(T0 + IntroTiming.REDUCED_MIN_MS), 0f);
    }

    @Test public void readyAfterHintKeepsHintUntilFadeOut() {
        IntroTiming t = new IntroTiming(T0, false);
        t.markReady(T0 + 4000);
        assertTrue(t.hintAlpha(T0 + 4000) > 0f);
        assertEquals(0f, t.hintAlpha(T0 + 4000 + IntroTiming.FADE_OUT_MS), 0f);
    }
}
