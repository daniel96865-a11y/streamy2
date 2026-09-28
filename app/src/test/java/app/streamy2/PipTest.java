package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class PipTest {
    @Test public void onlyMobileAppWithSupportAndSetting() {
        assertTrue(Pip.isMobileFlavor("mobile"));
        assertFalse(Pip.isMobileFlavor("tv"));
        assertTrue(Pip.allowed(true, 26, true, true));
        assertFalse(Pip.allowed(false, 34, true, true)); // TV app: never
        assertFalse(Pip.allowed(true, 25, true, true));  // below Android 8
        assertFalse(Pip.allowed(true, 34, false, true)); // device without PiP
        assertFalse(Pip.allowed(true, 34, true, false)); // setting off
    }

    @Test public void autoEnterOnlyWhilePlaying() {
        assertTrue(Pip.autoEnter(true, true, true, false));
        assertFalse(Pip.autoEnter(true, false, true, false)); // auto toggle off
        assertFalse(Pip.autoEnter(true, true, false, false)); // paused
        assertFalse(Pip.autoEnter(true, true, true, true));   // finishing
        assertFalse(Pip.autoEnter(false, true, true, false)); // PiP off
        assertTrue(Pip.useLeaveHint(26));
        assertTrue(Pip.useLeaveHint(30));
        assertFalse(Pip.useLeaveHint(31)); // Android 12+: setAutoEnterEnabled
    }

    @Test public void aspectRatioFromVideoClamped() {
        assertArrayEquals(new int[]{16, 9}, Pip.aspect(1920, 1080, 1f));
        assertArrayEquals(new int[]{16, 9}, Pip.aspect(0, 0, 1f)); // unknown
        assertArrayEquals(new int[]{4, 3}, Pip.aspect(640, 480, 1f));
        // Anamorphic SD (720x576, 16:9 pixel ratio 64/45) -> 1024x576 = 16:9
        assertArrayEquals(new int[]{16, 9}, Pip.aspect(720, 576, 64f / 45f));
        assertArrayEquals(new int[]{239, 100}, Pip.aspect(3000, 1000, 1f)); // too wide
        assertArrayEquals(new int[]{100, 239}, Pip.aspect(500, 2000, 1f));  // too tall
        int[] r = Pip.aspect(1920, 800, 1f); // 2.4:1 just outside
        assertArrayEquals(new int[]{239, 100}, r);
        int[] ok = Pip.aspect(1920, 1080, Float.NaN);
        assertArrayEquals(new int[]{16, 9}, ok);
    }

    @Test public void closeVersusExpand() {
        assertTrue(Pip.closedByUser(true, false, false));   // window closed: activity stopped
        assertFalse(Pip.closedByUser(true, false, true));   // tapped: back to full player
        assertFalse(Pip.closedByUser(false, false, false)); // was not in PiP
        assertFalse(Pip.closedByUser(true, true, false));
    }
}
