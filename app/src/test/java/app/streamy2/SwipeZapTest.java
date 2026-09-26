package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class SwipeZapTest {
    private static final float D = 2.75f;      // typical phone density
    private static final int H = 1080;         // landscape height px

    @Test public void upIsNextDownIsPrevious() {
        assertEquals(SwipeZap.NEXT, SwipeZap.decide(700, 10, -300, 250, H, D));
        assertEquals(SwipeZap.PREVIOUS, SwipeZap.decide(300, -15, 300, 250, H, D));
    }

    @Test public void tapsAndShortMovesDoNothing() {
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 0, 0, 80, H, D));
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 5, -150, 200, H, D)); // < 72dp (198px)
    }

    @Test public void horizontalOrDiagonalDoNothing() {
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 400, -50, 200, H, D));
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 250, -300, 200, H, D)); // 300 < 1.8*250
        assertEquals(SwipeZap.NEXT, SwipeZap.decide(500, 150, -300, 200, H, D));
    }

    @Test public void slowDragsAndEdgeStartsDoNothing() {
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 0, -400, 1500, H, D));
        assertEquals(SwipeZap.NONE, SwipeZap.decide(20, 0, 400, 200, H, D));       // status bar edge
        assertEquals(SwipeZap.NONE, SwipeZap.decide(H - 30, 0, -400, 200, H, D));  // nav gesture edge
    }

    @Test public void minimumScalesWithViewHeight() {
        int tall = 2400;  // portrait: 12 % = 288px > 72dp
        assertEquals(SwipeZap.NONE, SwipeZap.decide(1200, 0, -250, 200, tall, D));
        assertEquals(SwipeZap.NEXT, SwipeZap.decide(1200, 0, -300, 200, tall, D));
    }

    @Test public void invalidInputIsSafe() {
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 0, -400, 200, 0, D));
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 0, -400, 200, H, 0f));
        assertEquals(SwipeZap.NONE, SwipeZap.decide(500, 0, -400, -1, H, D));
    }

    @Test public void onlyMobileLiveTv() {
        assertTrue(SwipeZap.allowed(true, false, true, false, false));
        assertFalse(SwipeZap.allowed(false, false, true, false, false)); // setting off
        assertFalse(SwipeZap.allowed(true, true, true, false, false));   // TV
        assertFalse(SwipeZap.allowed(true, false, false, false, false)); // VOD
        assertFalse(SwipeZap.allowed(true, false, true, true, false));   // catch-up
        assertFalse(SwipeZap.allowed(true, false, true, false, true));   // EPG sheet open
    }

    @Test public void debounce() {
        assertTrue(SwipeZap.intervalOk(1000, 0));
        assertFalse(SwipeZap.intervalOk(1300, 1000));
        assertTrue(SwipeZap.intervalOk(1400, 1000));
    }

    @Test public void overlayText() {
        assertEquals("▲  12  ·  Das Erste", SwipeZap.overlayText(1, 12, " Das Erste "));
        assertEquals("▼  Arte", SwipeZap.overlayText(-1, 0, "Arte"));
        assertEquals("▲  7", SwipeZap.overlayText(1, 7, null));
    }
}
