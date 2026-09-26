package app.streamy2;

import java.util.Locale;

/**
 * Pure helpers for the player buffer indicator (formatting + small calculations).
 * No Android dependencies so everything here is unit-testable.
 */
public final class BufferStats {
    public static final String MODE_OFF = "off";
    public static final String MODE_HUD = "hud";
    public static final String MODE_ALWAYS = "always";

    public static final int LEVEL_OK = 0;
    public static final int LEVEL_WARN = 1;
    public static final int LEVEL_BAD = 2;

    /** Buffer ahead below this is shown as warning. */
    public static final long LOW_BUFFER_MS = 3000L;
    /**
     * ExoPlayer stops downloading while its buffer is full, so a low download rate is only
     * meaningful while the buffer ahead is small.
     */
    public static final long SLOW_HINT_MAX_AHEAD_MS = 15000L;

    private BufferStats() {}

    public static String normMode(String mode) {
        if (MODE_OFF.equals(mode) || MODE_ALWAYS.equals(mode)) return mode;
        return MODE_HUD;
    }

    public static String modeLabel(String mode) {
        String m = normMode(mode);
        if (MODE_OFF.equals(m)) return "Aus";
        if (MODE_ALWAYS.equals(m)) return "Immer";
        return "Mit Bedienleiste";
    }

    public static String nextMode(String mode) {
        String m = normMode(mode);
        if (MODE_HUD.equals(m)) return MODE_ALWAYS;
        if (MODE_ALWAYS.equals(m)) return MODE_OFF;
        return MODE_HUD;
    }

    /** Buffered milliseconds ahead of the playhead, never negative. */
    public static long aheadMs(long bufferedPositionMs, long currentPositionMs) {
        if (bufferedPositionMs < 0 || currentPositionMs < 0) return 0L;
        long d = bufferedPositionMs - currentPositionMs;
        return d > 0 ? d : 0L;
    }

    /** Throughput in bit/s from a byte delta over a time window. */
    public static long rateBps(long bytes, long elapsedMs) {
        if (bytes <= 0 || elapsedMs <= 0) return 0L;
        return (bytes * 8000L) / elapsedMs;
    }

    /** Exponential smoothing so the display does not jump every second. */
    public static long smooth(long previous, long sample) {
        if (sample < 0) return previous;
        if (previous <= 0) return sample;
        return (previous * 6L + sample * 4L) / 10L;
    }

    /**
     * libVLC reports input/demux bitrate as bytes per microsecond-ish float
     * (VLC itself shows kbit/s as value * 8000). Converted to bit/s.
     */
    public static long vlcBitrateToBps(float vlcRate) {
        if (Float.isNaN(vlcRate) || Float.isInfinite(vlcRate) || vlcRate <= 0f) return 0L;
        return Math.round((double) vlcRate * 8000.0d * 1000.0d);
    }

    /** SeekBar secondary progress (0..max) for the buffered position. */
    public static int secondaryProgress(long bufferedPositionMs, long startMs, long durationMs, int max) {
        if (durationMs <= 0 || max <= 0 || bufferedPositionMs <= startMs) return 0;
        double f = (double) (bufferedPositionMs - startMs) / (double) durationMs;
        if (f >= 1d) return max;
        int v = (int) Math.round(f * max);
        return Math.max(0, Math.min(max, v));
    }

    /** "12 s", "4,5 s", "1:05 min". */
    public static String formatSeconds(long ms) {
        if (ms < 0) ms = 0;
        if (ms < 10000L) {
            return String.format(Locale.GERMANY, "%.1f s", ms / 1000.0d);
        }
        long s = ms / 1000L;
        if (s < 60L) return s + " s";
        return String.format(Locale.GERMANY, "%d:%02d min", s / 60L, s % 60L);
    }

    /** "850 kbit/s", "4,8 Mbit/s", "12 Mbit/s", "–" for unknown. */
    public static String formatBitrate(long bps) {
        if (bps <= 0) return "–";
        if (bps < 1_000_000L) {
            long k = Math.max(1L, Math.round(bps / 1000.0d));
            if (k >= 1000L) return "1,0 Mbit/s";
            return k + " kbit/s";
        }
        double m = bps / 1_000_000.0d;
        if (m < 10d) return String.format(Locale.GERMANY, "%.1f Mbit/s", m);
        return Math.round(m) + " Mbit/s";
    }

    public static int exoLevel(boolean buffering, long aheadMs, long throughputBps, long streamBps) {
        if (buffering) return LEVEL_BAD;
        if (aheadMs < LOW_BUFFER_MS) return LEVEL_WARN;
        if (aheadMs < SLOW_HINT_MAX_AHEAD_MS && tooSlow(throughputBps, streamBps)) return LEVEL_WARN;
        return LEVEL_OK;
    }

    /** Download slower than the stream needs (with 5 % tolerance). */
    public static boolean tooSlow(long throughputBps, long streamBps) {
        return throughputBps > 0 && streamBps > 0 && throughputBps * 100L < streamBps * 95L;
    }

    public static String rebufferLabel(int rebuffers) {
        return Math.max(0, rebuffers) + "× nachgeladen";
    }

    /** One-line indicator for Media3/ExoPlayer. */
    public static String exoLine(boolean buffering, long aheadMs, long throughputBps, long streamBps, int rebuffers) {
        return exoLine(buffering, false, aheadMs, throughputBps, streamBps, rebuffers);
    }

    public static String status(boolean buffering, boolean paused) {
        if (buffering) return "Puffert…";
        return paused ? "pausiert" : "läuft";
    }

    public static String exoLine(boolean buffering, boolean paused, long aheadMs, long throughputBps, long streamBps, int rebuffers) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("Puffer ").append(formatSeconds(aheadMs));
        sb.append(" · ↓ ").append(formatBitrate(throughputBps));
        if (streamBps > 0) sb.append(" · Stream ").append(formatBitrate(streamBps));
        sb.append(" · ").append(status(buffering, paused));
        sb.append(" · ").append(rebufferLabel(rebuffers));
        if (!buffering && aheadMs < SLOW_HINT_MAX_AHEAD_MS && tooSlow(throughputBps, streamBps)) sb.append(" · Download zu langsam");
        return sb.toString();
    }

    public static int vlcLevel(float bufferingPercent, int lostPictures, int lostPicturesBefore) {
        if (bufferingPercent >= 0f && bufferingPercent < 100f) return LEVEL_BAD;
        if (lostPictures > lostPicturesBefore) return LEVEL_WARN;
        return LEVEL_OK;
    }

    /** One-line indicator for libVLC (no buffer-ahead value available there). */
    public static String vlcLine(float bufferingPercent, long inputBps, long demuxBps, int lostPictures, int rebuffers) {
        return vlcLine(bufferingPercent, false, inputBps, demuxBps, lostPictures, rebuffers);
    }

    public static String vlcLine(float bufferingPercent, boolean paused, long inputBps, long demuxBps, int lostPictures, int rebuffers) {
        StringBuilder sb = new StringBuilder(96);
        boolean buffering = bufferingPercent >= 0f && bufferingPercent < 100f;
        if (buffering) {
            sb.append("Puffert… ").append(Math.max(0, Math.round(bufferingPercent))).append(" %");
        } else {
            sb.append(status(false, paused));
        }
        sb.append(" · ↓ ").append(formatBitrate(inputBps));
        if (demuxBps > 0) sb.append(" · Stream ").append(formatBitrate(demuxBps));
        if (lostPictures > 0) sb.append(" · ").append(lostPictures).append(" Bilder verloren");
        sb.append(" · ").append(rebufferLabel(rebuffers));
        return sb.toString();
    }
}
