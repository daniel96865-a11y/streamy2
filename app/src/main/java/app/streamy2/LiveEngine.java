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
}
