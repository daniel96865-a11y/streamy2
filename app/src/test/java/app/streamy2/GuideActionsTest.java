package app.streamy2;

import static org.junit.Assert.*;

import org.junit.Test;

/** Helpers of the programme guide (moved from the removed 3.89 start screen in 3.91). */
public class GuideActionsTest {
    private static final long NOW = 1_790_000_000_000L;

    private static Models.Epg epg(long start, long end) {
        Models.Epg e = new Models.Epg();
        e.start = start;
        e.end = end;
        e.title = "x";
        return e;
    }

    private static Models.Channel ch(String id, String name) {
        Models.Channel c = new Models.Channel();
        c.id = id;
        c.name = name;
        return c;
    }

    @Test public void progressAndRemainingTime() {
        Models.Epg e = epg(NOW - 30 * 60_000L, NOW + 90 * 60_000L);
        assertEquals(25, GuideActions.progress(e, NOW));
        assertEquals(0, GuideActions.progress(e, e.start - 1));
        assertEquals(100, GuideActions.progress(e, e.end + 1));
        assertEquals(0, GuideActions.progress(null, NOW));
        assertEquals("noch 1 Std. 30 Min.", GuideActions.remaining(e, NOW));
        assertEquals("noch 35 Min.", GuideActions.remaining(epg(NOW - 1, NOW + 35 * 60_000L), NOW));
        assertEquals("noch 2 Std.", GuideActions.remaining(epg(NOW - 1, NOW + 120 * 60_000L), NOW));
        assertEquals("", GuideActions.remaining(epg(NOW + 1, NOW + 5), NOW));
        assertTrue(GuideActions.covers(e, NOW));
        assertFalse(GuideActions.covers(null, NOW));
    }

    @Test public void keyUsesIdOrName() {
        assertEquals("7", GuideActions.key(ch("7", "A")));
        assertEquals("Arte", GuideActions.key(ch(null, "Arte")));
        assertEquals("Arte", GuideActions.key(ch("", "Arte")));
        assertNull(GuideActions.key(null));
    }
}
