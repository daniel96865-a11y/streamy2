package app.streamy2;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Layout maths of the timeline programme guide (pure Java, unit tested): time axis in
 * half-hour steps, block positions sized by duration, the "Jetzt" line, visible-row range for
 * lazy drawing and DPAD focus navigation. All day boundaries in German time (Europe/Berlin).
 */
final class Timeline {
    static final long MIN = 60_000L;
    static final long HALF_HOUR = 30 * MIN;
    static final long DAY = 24 * 60 * MIN;
    /** Gap between two blocks in px. */
    static final int GAP = 4;
    /** Narrowest drawn block (very short programmes stay visible). */
    static final int MIN_BLOCK = 6;

    private Timeline() {
    }

    /** Start of the half hour containing {@code t}. */
    static long floorHalfHour(long t) {
        Calendar c = Calendar.getInstance(EpgTime.ZONE);
        c.setTimeInMillis(t);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.MINUTE, c.get(Calendar.MINUTE) < 30 ? 0 : 30);
        return c.getTimeInMillis();
    }

    /** 00:00 German time of the day containing {@code t}. */
    static long dayStart(long t) {
        Calendar c = Calendar.getInstance(EpgTime.ZONE);
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 00:00 of the day {@code offset} days after the day of {@code t} (DST-safe). */
    static long dayStart(long t, int offset) {
        Calendar c = Calendar.getInstance(EpgTime.ZONE);
        c.setTimeInMillis(dayStart(t));
        c.add(Calendar.DAY_OF_MONTH, offset);
        return c.getTimeInMillis();
    }

    /** x position of {@code time} relative to the axis origin. */
    static float x(long time, long origin, float pxPerMin) {
        return (time - origin) / (float) MIN * pxPerMin;
    }

    /** Time at x position (inverse of {@link #x}). */
    static long timeAt(float x, long origin, float pxPerMin) {
        return origin + Math.round(x / pxPerMin * MIN);
    }

    /** Block {left, width} in axis coordinates; width at least {@link #MIN_BLOCK}. */
    static float[] block(long start, long stop, long origin, float pxPerMin) {
        float l = x(start, origin, pxPerMin);
        float r = x(Math.max(stop, start), origin, pxPerMin);
        float w = Math.max(MIN_BLOCK, r - l - GAP);
        return new float[]{l, w};
    }

    /** Half-hour ticks covering [from, to]. */
    static List<Long> ticks(long from, long to) {
        ArrayList<Long> out = new ArrayList<>();
        for (long t = floorHalfHour(from); t <= to; t += HALF_HOUR) {
            if (t >= from - HALF_HOUR) out.add(t);
        }
        return out;
    }

    /** "Jetzt" line x on screen, or NaN if outside the visible area. */
    static float nowLine(long now, long origin, float pxPerMin, float scrollX, float viewWidth) {
        float sx = x(now, origin, pxPerMin) - scrollX;
        return sx < 0 || sx > viewWidth ? Float.NaN : sx;
    }

    /** First and last row index visible for vertical scroll {@code scrollY} (lazy drawing). */
    static int[] visibleRows(float scrollY, float viewHeight, float rowHeight, int rowCount) {
        if (rowCount <= 0 || rowHeight <= 0) return new int[]{0, -1};
        int first = Math.max(0, (int) Math.floor(scrollY / rowHeight));
        int last = Math.min(rowCount - 1, (int) Math.floor((scrollY + viewHeight - 1) / rowHeight));
        return new int[]{first, Math.max(first - 1, last)};
    }

    static float clamp(float v, float min, float max) {
        return max < min ? min : Math.max(min, Math.min(max, v));
    }

    /**
     * Horizontal scroll so that [left, left+width] is visible with a margin; long blocks
     * show their start (or keep the current position if their visible part is enough).
     */
    static float scrollToShow(float scrollX, float viewWidth, float left, float width, float margin) {
        float right = left + width;
        if (left >= scrollX + margin && right <= scrollX + viewWidth - margin) return scrollX;
        if (left < scrollX + margin) {
            // Block starts left of the view: only scroll if too little of it is visible.
            if (right > scrollX + Math.min(viewWidth * 0.4f, width) && left < scrollX) {
                return scrollX;
            }
            return left - margin;
        }
        if (width > viewWidth - 2 * margin) return left - margin;
        return right - viewWidth + margin;
    }

    /** Vertical scroll so that row {@code row} is fully visible. */
    static float scrollToRow(float scrollY, float viewHeight, float rowHeight, int row) {
        float top = row * rowHeight;
        if (top < scrollY) return top;
        if (top + rowHeight > scrollY + viewHeight) return top + rowHeight - viewHeight;
        return scrollY;
    }

    // --- focus navigation ---

    /** Index of the programme at {@code time} (or the nearest one), -1 if the row is empty. */
    static int indexAt(List<EpgGuide.Listing> row, long time) {
        if (row == null || row.isEmpty()) return -1;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < row.size(); i++) {
            EpgGuide.Listing l = row.get(i);
            if (l == null) continue;
            if (l.start <= time && time < l.stop) return i;
            long d = time < l.start ? l.start - time : time - l.stop + 1;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Time anchor kept while moving up/down (like TV guides): the later of the focused
     * programme's start and the left edge of the visible window, but not past its end.
     */
    static long anchor(EpgGuide.Listing focused, long viewStart) {
        if (focused == null) return viewStart;
        long a = Math.max(focused.start, viewStart);
        return Math.min(a, Math.max(focused.start, focused.stop - 1));
    }

    /** Next focus index in the same row (dx = -1 / +1), clamped. */
    static int horizontal(List<EpgGuide.Listing> row, int index, int dx) {
        if (row == null || row.isEmpty()) return -1;
        return Math.max(0, Math.min(row.size() - 1, index + dx));
    }

    /** Next focused row (dy = -1 / +1), clamped; -1 for no rows. */
    static int vertical(int rowCount, int row, int dy) {
        if (rowCount <= 0) return -1;
        return Math.max(0, Math.min(rowCount - 1, row + dy));
    }

    /** "Heute, Mo 28.09." / "Gestern" / "Morgen" / "Mi 30.09." for the day {@code offset} from today. */
    static String dayLabel(long dayStart, long now) {
        long today = dayStart(now);
        java.text.SimpleDateFormat f = EpgTime.format("EE dd.MM.");
        String date = f.format(new java.util.Date(dayStart + 12 * 60 * MIN)).replace(".,", "").replace("..", ".");
        if (dayStart == today) return "Heute, " + date;
        if (dayStart == dayStart(now, -1)) return "Gestern";
        if (dayStart == dayStart(now, 1)) return "Morgen";
        return date;
    }

    /** Day offsets (relative to today) that contain programme data between {@code min} and {@code max}. */
    static List<Integer> days(long min, long max, long now, int maxPast, int maxFuture) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int d = -maxPast; d <= maxFuture; d++) {
            long s = dayStart(now, d), e = dayStart(now, d + 1);
            if (d == 0 || (min < e && max > s)) out.add(d);
        }
        return out;
    }
}
