package app.streamy2;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.junit.Test;

/** Layout maths of the timeline programme guide (3.89). */
public class TimelineTest {
    private static final long MIN = Timeline.MIN;

    private static long berlin(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(EpgTime.ZONE);
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    private static EpgGuide.Listing listing(long start, long stop, String title) {
        EpgGuide.Listing l = new EpgGuide.Listing();
        l.start = start;
        l.stop = stop;
        l.title = title;
        return l;
    }

    /** 20:00 news (15 min), 20:15 film (105 min), 22:00 talk (60 min). */
    private static List<EpgGuide.Listing> evening() {
        long t = berlin(2026, 9, 28, 20, 0);
        List<EpgGuide.Listing> row = new ArrayList<>();
        row.add(listing(t, t + 15 * MIN, "Nachrichten"));
        row.add(listing(t + 15 * MIN, t + 120 * MIN, "Film"));
        row.add(listing(t + 120 * MIN, t + 180 * MIN, "Talk"));
        return row;
    }

    @Test public void blockWidthIsProportionalToDuration() {
        long origin = berlin(2026, 9, 28, 20, 0);
        float ppm = 8f; // 8 px per minute = 240 px per half hour
        float[] news = Timeline.block(origin, origin + 15 * MIN, origin, ppm);
        float[] film = Timeline.block(origin + 15 * MIN, origin + 120 * MIN, origin, ppm);
        assertEquals(0f, news[0], 0.001f);
        assertEquals(15 * 8 - Timeline.GAP, news[1], 0.001f);
        assertEquals(120f, film[0], 0.001f);
        assertEquals(105 * 8 - Timeline.GAP, film[1], 0.001f);
        // Next block starts exactly one gap after the previous block ends.
        assertEquals(film[0], news[0] + news[1] + Timeline.GAP, 0.001f);
        // Twice as long → twice as wide (plus the shared gap).
        float[] half = Timeline.block(origin, origin + 30 * MIN, origin, ppm);
        float[] hour = Timeline.block(origin, origin + 60 * MIN, origin, ppm);
        assertEquals(2 * (half[1] + Timeline.GAP), hour[1] + Timeline.GAP, 0.001f);
    }

    @Test public void veryShortOrBrokenProgrammesKeepMinimumWidth() {
        long o = berlin(2026, 9, 28, 20, 0);
        assertEquals(Timeline.MIN_BLOCK, Timeline.block(o, o + 30_000L, o, 4f)[1], 0.001f);
        assertEquals(Timeline.MIN_BLOCK, Timeline.block(o, o - 10 * MIN, o, 4f)[1], 0.001f);
    }

    @Test public void xAndTimeAtAreInverse() {
        long o = berlin(2026, 9, 28, 18, 0);
        for (int m = 0; m <= 600; m += 7) {
            long t = o + m * MIN;
            assertEquals(t, Timeline.timeAt(Timeline.x(t, o, 5.5f), o, 5.5f));
        }
        assertEquals(-60f, Timeline.x(o - 10 * MIN, o, 6f), 0.001f);
    }

    @Test public void nowLinePositionAndVisibility() {
        long o = berlin(2026, 9, 28, 20, 0);
        long now = berlin(2026, 9, 28, 21, 15);
        // 75 min * 4 px = 300 px from origin, scrolled by 100 → 200 on screen.
        assertEquals(200f, Timeline.nowLine(now, o, 4f, 100f, 1000f), 0.001f);
        assertEquals(0f, Timeline.nowLine(now, o, 4f, 300f, 1000f), 0.001f);
        assertTrue(Float.isNaN(Timeline.nowLine(now, o, 4f, 301f, 1000f)));
        assertTrue(Float.isNaN(Timeline.nowLine(now, o, 4f, 0f, 299f)));
    }

    @Test public void halfHourTicks() {
        long from = berlin(2026, 9, 28, 20, 10);
        long to = berlin(2026, 9, 28, 22, 0);
        List<Long> ticks = Timeline.ticks(from, to);
        assertEquals(Long.valueOf(berlin(2026, 9, 28, 20, 0)), ticks.get(0));
        assertEquals(Long.valueOf(to), ticks.get(ticks.size() - 1));
        for (int i = 1; i < ticks.size(); i++) assertEquals(Timeline.HALF_HOUR, ticks.get(i) - ticks.get(i - 1));
        assertEquals(berlin(2026, 9, 28, 20, 30), Timeline.floorHalfHour(berlin(2026, 9, 28, 20, 59)));
        assertEquals(berlin(2026, 9, 28, 20, 0), Timeline.floorHalfHour(berlin(2026, 9, 28, 20, 29)));
    }

    @Test public void dayBoundariesFollowBerlinTimeAcrossDst() {
        // DST ends 25 Oct 2026: that day has 25 hours in Germany.
        long midday = berlin(2026, 10, 25, 12, 0);
        long start = Timeline.dayStart(midday);
        assertEquals(berlin(2026, 10, 25, 0, 0), start);
        assertEquals(25 * 60 * MIN, Timeline.dayStart(midday, 1) - start);
        assertEquals(berlin(2026, 10, 24, 0, 0), Timeline.dayStart(midday, -1));
        // 23:30 UTC on 27 Sep is already 28 Sep in Berlin.
        long utcLate = berlin(2026, 9, 28, 1, 30);
        assertEquals(berlin(2026, 9, 28, 0, 0), Timeline.dayStart(utcLate));
    }

    @Test public void visibleRowsForLazyDrawing() {
        assertArrayEquals(new int[]{0, 9}, Timeline.visibleRows(0, 460, 46, 1000));
        assertArrayEquals(new int[]{2, 12}, Timeline.visibleRows(100, 460, 46, 1000));
        assertArrayEquals(new int[]{995, 999}, Timeline.visibleRows(995 * 46, 460, 46, 1000));
        assertArrayEquals(new int[]{0, -1}, Timeline.visibleRows(0, 460, 46, 0));
        int[] r = Timeline.visibleRows(0, 460, 46, 3);
        assertEquals(0, r[0]);
        assertEquals(2, r[1]);
    }

    @Test public void scrollKeepsFocusedBlockVisible() {
        // Already visible: no scroll.
        assertEquals(100f, Timeline.scrollToShow(100, 1000, 300, 200, 20), 0.001f);
        // Right of the view: scroll so its end is visible with margin.
        assertEquals(1220f - 1000f + 20f, Timeline.scrollToShow(0, 1000, 1000, 220, 20), 0.001f);
        // Left of the view: show its start.
        assertEquals(180f, Timeline.scrollToShow(500, 1000, 200, 100, 20), 0.001f);
        // Long programme that is mostly visible already: keep the position.
        assertEquals(500f, Timeline.scrollToShow(500, 1000, 400, 900, 20), 0.001f);
        // Longer than the view, starting to the right: show its start.
        assertEquals(1180f, Timeline.scrollToShow(0, 1000, 1200, 3000, 20), 0.001f);
        // Rows.
        assertEquals(0f, Timeline.scrollToRow(0, 460, 46, 3), 0.001f);
        assertEquals(46f * 11 - 460f, Timeline.scrollToRow(0, 460, 46, 10), 0.001f);
        assertEquals(46f * 2, Timeline.scrollToRow(400, 460, 46, 2), 0.001f);
    }

    @Test public void focusNavigationLeftRight() {
        List<EpgGuide.Listing> row = evening();
        long t = row.get(0).start;
        assertEquals(0, Timeline.indexAt(row, t + 5 * MIN));
        assertEquals(1, Timeline.indexAt(row, t + 15 * MIN));
        assertEquals(2, Timeline.indexAt(row, t + 179 * MIN));
        // Outside the data → nearest programme.
        assertEquals(0, Timeline.indexAt(row, t - 60 * MIN));
        assertEquals(2, Timeline.indexAt(row, t + 400 * MIN));
        assertEquals(-1, Timeline.indexAt(new ArrayList<EpgGuide.Listing>(), t));
        assertEquals(1, Timeline.horizontal(row, 0, 1));
        assertEquals(0, Timeline.horizontal(row, 1, -1));
        assertEquals(0, Timeline.horizontal(row, 0, -1));
        assertEquals(2, Timeline.horizontal(row, 2, 1));
        assertEquals(-1, Timeline.horizontal(null, 0, 1));
    }

    @Test public void focusNavigationUpDownKeepsTimeAnchor() {
        List<EpgGuide.Listing> row = evening();
        EpgGuide.Listing film = row.get(1);
        long viewStart = film.start - 60 * MIN;
        assertEquals(film.start, Timeline.anchor(film, viewStart));
        // Programme started before the visible window: anchor at the window start.
        long later = film.start + 30 * MIN;
        assertEquals(later, Timeline.anchor(film, later));
        // Never past the programme's end.
        assertEquals(film.stop - 1, Timeline.anchor(film, film.stop + 90 * MIN));
        assertEquals(viewStart, Timeline.anchor(null, viewStart));
        // Next channel: the block under the anchor is focused.
        long base = row.get(0).start;
        List<EpgGuide.Listing> other = new ArrayList<>();
        other.add(listing(base - 30 * MIN, base + 50 * MIN, "Serie"));
        other.add(listing(base + 50 * MIN, base + 140 * MIN, "Doku"));
        assertEquals(0, Timeline.indexAt(other, Timeline.anchor(film, viewStart)));
        assertEquals(1, Timeline.indexAt(other, Timeline.anchor(film, base + 60 * MIN)));
        assertEquals(1, Timeline.vertical(10, 0, 1));
        assertEquals(0, Timeline.vertical(10, 0, -1));
        assertEquals(9, Timeline.vertical(10, 9, 1));
        assertEquals(-1, Timeline.vertical(0, 0, 1));
    }

    @Test public void daySwitcherOnlyWhereDataExists() {
        long now = berlin(2026, 9, 28, 20, 0);
        long min = berlin(2026, 9, 27, 6, 0);
        long max = berlin(2026, 9, 30, 6, 0);
        List<Integer> days = Timeline.days(min, max, now, 7, 7);
        assertEquals(java.util.Arrays.asList(-1, 0, 1, 2), days);
        // No data at all: "Heute" only.
        assertEquals(java.util.Arrays.asList(0), Timeline.days(Long.MAX_VALUE, Long.MIN_VALUE, now, 7, 7));
        assertEquals("Gestern", Timeline.dayLabel(Timeline.dayStart(now, -1), now));
        assertEquals("Morgen", Timeline.dayLabel(Timeline.dayStart(now, 1), now));
        String today = Timeline.dayLabel(Timeline.dayStart(now), now);
        assertTrue(today, today.startsWith("Heute, "));
        assertTrue(today, today.contains("28.09."));
        assertTrue(Timeline.dayLabel(Timeline.dayStart(now, 2), now).contains("30.09."));
    }

    @Test public void okOnBlockPlaysLiveCatchupOrShowsInfo() {
        List<EpgGuide.Listing> row = evening();
        long now = row.get(1).start + 10 * MIN;
        assertEquals(GuideActions.PLAY_LIVE, GuideActions.onOpen(row.get(1), now, true));
        assertEquals(GuideActions.PLAY_CATCHUP, GuideActions.onOpen(row.get(0), now, true));
        assertEquals(GuideActions.SHOW_INFO, GuideActions.onOpen(row.get(0), now, false));
        assertEquals(GuideActions.SHOW_INFO, GuideActions.onOpen(row.get(2), now, true));
        assertEquals(GuideActions.PLAY_LIVE, GuideActions.onOpen(null, now, false));
        assertEquals("Läuft gerade", GuideActions.state(row.get(1), now));
        assertEquals("Vorbei", GuideActions.state(row.get(0), now));
        assertEquals("Später", GuideActions.state(row.get(2), now));
    }
}
