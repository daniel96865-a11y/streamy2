package app.streamy2;

/**
 * Pure time math for catch-up (archive / timeshift) playback.
 *
 * A timeshift stream starts at a wall-clock time (the "stream start") inside an EPG
 * programme. The player only knows its position inside that stream, so the time on
 * screen is streamStart + position. Seeking either moves inside the current stream
 * (when the engine reports a seekable duration) or rebuilds the timeshift URL with
 * a new start time.
 */
final class CatchupSeek {
    /** D-pad left/right step. */
    static final long STEP_MS = 30_000L;
    /** Media rewind / fast-forward key step. */
    static final long MEDIA_STEP_MS = 60_000L;
    /** Archive cannot reach the live edge; keep this distance. */
    static final long LIVE_GUARD_MS = 15_000L;
    /** Rebuilt timeshift URLs never start closer than this to the programme end. */
    static final long END_GUARD_MS = 30_000L;

    private CatchupSeek() {
    }

    static long shownTime(long streamStartMs, long enginePositionMs) {
        return streamStartMs + Math.max(0L, enginePositionMs);
    }

    /** Clamps a seek target into the programme and before the live edge. */
    static long clamp(long target, long programmeStart, long programmeStop, long nowMs) {
        long max = Math.min(programmeStop - END_GUARD_MS, nowMs - LIVE_GUARD_MS);
        if (max < programmeStart) max = programmeStart;
        return Math.max(programmeStart, Math.min(max, target));
    }

    static int progress(long timeMs, long programmeStart, long programmeStop) {
        long len = Math.max(1L, programmeStop - programmeStart);
        return (int) Math.max(0L, Math.min(1000L, ((timeMs - programmeStart) * 1000L) / len));
    }

    static long timeForProgress(int progress, long programmeStart, long programmeStop) {
        long len = Math.max(1L, programmeStop - programmeStart);
        return programmeStart + (len * Math.max(0, Math.min(1000, progress))) / 1000L;
    }

    /**
     * Offset inside the current stream for {@code target}, or -1 if the target is
     * outside it (or the stream is not seekable) and the URL must be rebuilt.
     */
    static long inStreamOffset(long target, long streamStartMs, long streamDurationMs, boolean seekable) {
        if (!seekable || streamDurationMs <= 0) return -1L;
        long offset = target - streamStartMs;
        if (offset < 0 || offset > streamDurationMs - 2000L) return -1L;
        return offset;
    }

    /** Start time for a rebuilt timeshift URL. */
    static long streamStartFor(long target, long programmeStart, long programmeStop) {
        long start = Math.max(programmeStart, target);
        long latest = Math.max(programmeStart, programmeStop - END_GUARD_MS);
        return Math.min(start, latest);
    }
}
