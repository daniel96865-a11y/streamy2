package app.streamy2;

/* loaded from: classes.dex */
interface LiveEngine {
    boolean isPlaying();

    void pause();

    void play(String str, boolean z);

    void resume();

    void stop(boolean z);

    void toggle();
}
