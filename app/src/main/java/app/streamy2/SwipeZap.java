package app.streamy2;

/**
 * Pure decision logic for "Sender per Wischen wechseln" (mobile live TV).
 * Swipe up = next channel (+1), swipe down = previous channel (-1), same order as
 * the channel +/- keys / zap buttons. Everything else (taps, diagonal or short moves,
 * slow drags, swipes from the screen edges) returns {@link #NONE} so existing
 * gestures keep working.
 */
public final class SwipeZap {
    public static final int NONE = 0;
    public static final int NEXT = 1;
    public static final int PREVIOUS = -1;

    /** Minimum vertical travel in dp (also at least {@link #MIN_HEIGHT_FRACTION} of the view). */
    public static final float MIN_DISTANCE_DP = 72f;
    public static final float MIN_HEIGHT_FRACTION = 0.12f;
    /** Vertical movement must dominate horizontal movement by this factor. */
    public static final float DOMINANCE = 1.8f;
    /** A deliberate flick/swipe, not a slow drag. */
    public static final long MAX_DURATION_MS = 900L;
    /** Ignore swipes that start in the system gesture / status bar edges. */
    public static final float EDGE_DP = 40f;
    /** Minimum time between two swipe zaps. */
    public static final long MIN_INTERVAL_MS = 400L;

    private SwipeZap() {}

    /** Whether swipe zapping is active at all for the current player state. */
    public static boolean allowed(boolean enabledSetting, boolean tv, boolean liveMode, boolean catchup, boolean epgSheetOpen) {
        return enabledSetting && !tv && liveMode && !catchup && !epgSheetOpen;
    }

    /**
     * @param downY     y of ACTION_DOWN in view coordinates (px)
     * @param dx        x(up) - x(down) in px
     * @param dy        y(up) - y(down) in px (negative = upwards)
     * @param durationMs time between down and up
     * @param viewHeightPx height of the touch layer
     * @param density   display density (px per dp)
     */
    public static int decide(float downY, float dx, float dy, long durationMs, int viewHeightPx, float density) {
        if (viewHeightPx <= 0 || density <= 0f) return NONE;
        if (durationMs < 0 || durationMs > MAX_DURATION_MS) return NONE;
        float edge = EDGE_DP * density;
        if (downY < edge || downY > viewHeightPx - edge) return NONE;
        float ady = Math.abs(dy);
        float adx = Math.abs(dx);
        float min = Math.max(MIN_DISTANCE_DP * density, viewHeightPx * MIN_HEIGHT_FRACTION);
        if (ady < min) return NONE;
        if (ady < adx * DOMINANCE) return NONE;
        return dy < 0 ? NEXT : PREVIOUS;
    }

    /** Debounce helper: true if enough time passed since the last swipe zap. */
    public static boolean intervalOk(long nowMs, long lastZapMs) {
        return lastZapMs <= 0 || nowMs - lastZapMs >= MIN_INTERVAL_MS;
    }

    /** Short overlay text, e.g. "▲  12  ·  Das Erste". */
    public static String overlayText(int direction, int number, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(direction >= 0 ? "▲" : "▼");
        if (number > 0) sb.append("  ").append(number);
        String n = name == null ? "" : name.trim();
        if (!n.isEmpty()) sb.append(number > 0 ? "  ·  " : "  ").append(n);
        return sb.toString();
    }
}
