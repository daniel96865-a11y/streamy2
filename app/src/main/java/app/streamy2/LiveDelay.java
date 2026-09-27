package app.streamy2;

/**
 * "Live-Verzögerung": live channels play a fixed number of seconds further behind the
 * live edge than the stream would by default, so the player can keep a larger buffer.
 * Aus (0) keeps the previous behaviour unchanged. Live channels only (no VOD, no catch-up).
 *
 * <p>ExoPlayer (HLS): the stream's own default offset (HOLD-BACK or 3 × target duration)
 * plus the delay, clamped to the live window. Plain TS streams have no live window: there
 * the delay cannot be applied. VLC (HLS): the adaptive module's live delay is raised by the
 * same amount; other stream types unchanged.
 */
final class LiveDelay {
    static final int[] VALUES = {0, 10, 20, 30};
    static final String[] LABELS = {"Aus", "10 s", "20 s", "30 s"};
    /** Unknown/unset offset (maps to C.TIME_UNSET in the player). */
    static final long UNSET = -1L;
    /** Typical HLS default offset (3 × 6 s segments) until the playlist is known. */
    static final long ASSUMED_BASE_MS = 18000L;
    /** VLC adaptive (HLS/DASH) default live delay and max buffer. */
    static final int VLC_DEFAULT_LIVE_DELAY_MS = 15000;
    static final int VLC_DEFAULT_MAX_BUFFER_MS = 30000;
    /** Re-seek only if the actual offset is this far from the target. */
    static final long SEEK_TOLERANCE_MS = 2500L;

    private LiveDelay() {
    }

    static int normalize(int seconds) {
        for (int v : VALUES) if (v == seconds) return v;
        return 0;
    }

    static String label(int seconds) {
        int v = normalize(seconds);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i] == v) return LABELS[i];
        return LABELS[0];
    }

    /** Delay in ms for this playback: only for live channels, never for catch-up/VOD. */
    static long delayMs(int seconds, boolean live, boolean catchup) {
        if (!live || catchup) return 0L;
        return normalize(seconds) * 1000L;
    }

    /** The stream's own default distance to the live edge (as ExoPlayer's HLS source). */
    static long streamDefaultOffsetMs(long holdBackMs, long targetDurationMs) {
        if (holdBackMs > 0) return holdBackMs;
        if (targetDurationMs > 0) return 3 * targetDurationMs;
        return ASSUMED_BASE_MS;
    }

    /** Safety distance to the start of the live window (segments fall out there). */
    static long windowSafetyMs(long windowDurationMs) {
        return Math.max(4000L, windowDurationMs / 5);
    }

    /**
     * Target offset from the live edge: stream default + delay, but never outside the live
     * window (keeps a safety margin at the window start) and never closer than the default.
     * {@link #UNSET} when the delay is off (previous behaviour).
     */
    static long targetOffsetMs(long delayMs, long baseMs, long windowDurationMs) {
        if (delayMs <= 0) return UNSET;
        long base = baseMs > 0 ? baseMs : ASSUMED_BASE_MS;
        long target = base + delayMs;
        if (windowDurationMs > 0) {
            long max = windowDurationMs - windowSafetyMs(windowDurationMs);
            target = Math.min(target, max);
        }
        return Math.max(target, Math.min(base, windowDurationMs > 0 ? windowDurationMs : base));
    }

    /** Should the player re-seek to reach the target offset? */
    static boolean needsSeek(long currentOffsetMs, long targetMs) {
        if (targetMs <= 0 || currentOffsetMs < 0) return false;
        return Math.abs(currentOffsetMs - targetMs) > SEEK_TOLERANCE_MS;
    }

    /** Position in the window that gives {@code targetMs} distance to the live edge. */
    static long seekPositionMs(long positionMs, long currentOffsetMs, long targetMs, long windowDurationMs) {
        long pos = positionMs + currentOffsetMs - targetMs;
        long min = windowDurationMs > 0 ? Math.min(windowSafetyMs(windowDurationMs), windowDurationMs / 2) : 0L;
        return Math.max(min, pos);
    }

    /**
     * ExoPlayer buffer (minBuffer, maxBuffer, bufferForPlayback, bufferAfterRebuffer) with the
     * delay: the buffer may hold about as much as the extra distance to the live edge. Start
     * and zapping stay as fast as before (bufferForPlayback unchanged).
     */
    static int[] buffers(long delayMs, int minBuf, int maxBuf, int playback, int afterRebuffer, boolean lowRam) {
        if (delayMs <= 0) return new int[]{minBuf, maxBuf, playback, afterRebuffer};
        int d = (int) delayMs;
        int max = Math.max(maxBuf, d + 10000);
        if (lowRam) max = Math.min(max, Math.max(maxBuf, 25000));
        int min = Math.min(Math.max(minBuf, d), max - 2000);
        min = Math.max(min, minBuf);
        int after = Math.min(Math.max(afterRebuffer, 2500), min);
        return new int[]{min, max, playback, Math.max(after, playback)};
    }

    /** VLC ":adaptive-livedelay" in ms, or 0 = option not set (previous behaviour). */
    static int vlcLiveDelayMs(long delayMs) {
        return delayMs <= 0 ? 0 : (int) (VLC_DEFAULT_LIVE_DELAY_MS + delayMs);
    }

    /** VLC ":adaptive-maxbuffer" in ms, or 0 = option not set. */
    static int vlcMaxBufferMs(long delayMs) {
        return delayMs <= 0 ? 0 : Math.max(VLC_DEFAULT_MAX_BUFFER_MS, vlcLiveDelayMs(delayMs) + 5000);
    }

    /** Badge in the player: "LIVE", with delay "LIVE −20 s"; catch-up "ARCHIV". */
    static String badge(long delayMs, boolean catchup) {
        if (catchup) return "ARCHIV";
        if (delayMs <= 0) return "LIVE";
        return "LIVE \u2212" + (delayMs / 1000) + " s";
    }
}
