package app.streamy2;

import static org.junit.Assert.*;

import org.junit.Test;

public class CatchupSeekTest {
    private static final long MIN = 60_000L;
    private static final long START = 1_000_000_000_000L;      // programme start
    private static final long STOP = START + 60 * MIN;          // 60 min programme
    private static final long NOW = STOP + 120 * MIN;           // programme is in the past

    @Test public void shownTimeUsesStreamStartNotProgrammeStart() {
        long streamStart = START + 20 * MIN;                     // playback began mid-programme
        assertEquals(START + 25 * MIN, CatchupSeek.shownTime(streamStart, 5 * MIN));
        assertEquals(416, CatchupSeek.progress(CatchupSeek.shownTime(streamStart, 5 * MIN), START, STOP));
    }

    @Test public void rewindAndForwardMoveByStepsFromCurrentPosition() {
        long shown = START + 25 * MIN;
        long back = CatchupSeek.clamp(shown - CatchupSeek.STEP_MS, START, STOP, NOW);
        long fwd = CatchupSeek.clamp(shown + CatchupSeek.STEP_MS, START, STOP, NOW);
        assertEquals(shown - 30_000L, back);
        assertEquals(shown + 30_000L, fwd);
        // repeated presses accumulate
        long t = shown;
        for (int i = 0; i < 4; i++) t = CatchupSeek.clamp(t - CatchupSeek.STEP_MS, START, STOP, NOW);
        assertEquals(shown - 2 * MIN, t);
    }

    @Test public void clampStaysInsideProgrammeAndBeforeLiveEdge() {
        assertEquals(START, CatchupSeek.clamp(START - 10 * MIN, START, STOP, NOW));
        assertEquals(STOP - CatchupSeek.END_GUARD_MS, CatchupSeek.clamp(STOP + MIN, START, STOP, NOW));
        long airingNow = START + 30 * MIN;                       // programme still running
        assertEquals(airingNow - CatchupSeek.LIVE_GUARD_MS, CatchupSeek.clamp(STOP, START, STOP, airingNow));
    }

    @Test public void forwardNearEndNoLongerRestartsProgramme() {
        // regression: old code reset a target within 30 s of the end to programme START
        long start = CatchupSeek.streamStartFor(STOP - 10_000L, START, STOP);
        assertEquals(STOP - CatchupSeek.END_GUARD_MS, start);
        assertNotEquals(START, start);
        assertEquals(START + 10 * MIN, CatchupSeek.streamStartFor(START + 10 * MIN, START, STOP));
        assertEquals(START, CatchupSeek.streamStartFor(START - MIN, START, STOP));
    }

    @Test public void inStreamSeekOnlyWhenTargetInsideSeekableStream() {
        long streamStart = START + 10 * MIN;
        assertEquals(5 * MIN, CatchupSeek.inStreamOffset(START + 15 * MIN, streamStart, 50 * MIN, true));
        assertEquals(-1L, CatchupSeek.inStreamOffset(START + 5 * MIN, streamStart, 50 * MIN, true));   // before stream: rebuild URL
        assertEquals(-1L, CatchupSeek.inStreamOffset(START + 15 * MIN, streamStart, 50 * MIN, false)); // live-like stream
        assertEquals(-1L, CatchupSeek.inStreamOffset(START + 15 * MIN, streamStart, 0, true));        // unknown duration
    }

    @Test public void progressAndTimeRoundTrip() {
        for (int p : new int[]{0, 250, 500, 999, 1000}) {
            assertEquals(p, CatchupSeek.progress(CatchupSeek.timeForProgress(p, START, STOP), START, STOP));
        }
    }
}
