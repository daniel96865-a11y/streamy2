package app.streamy2;

/* loaded from: classes.dex */
interface LiveEngine {
    boolean isPlaying();

    void pause();

    void play(String str, boolean z);

    void resume();

    void stop(boolean z);

    void toggle();

    /** Current position in ms, or 0 if unknown. */
    long getPositionMs();

    /** Media duration in ms, or 0 if unknown/live. */
    long getDurationMs();

    /** Seek to position in ms (VOD). */
    void seekToMs(long j);

    /** Last buffering percent (0..100), or -1 if unknown. */
    default float bufferingPercent() { return -1f; }

    /** Number of buffering episodes after playback had started. */
    default int rebufferCount() { return 0; }

    /** Input (download) bitrate in bit/s, or 0 if unknown. */
    default long inputBitrateBps() { return 0L; }

    /** Demux (stream) bitrate in bit/s, or 0 if unknown. */
    default long demuxBitrateBps() { return 0L; }

    /** Lost (dropped) video pictures, or 0 if unknown. */
    default int lostPictures() { return 0; }

    /** Refresh cached statistics (called ~1x per second from the UI thread). */
    default void refreshStats() {}
}
