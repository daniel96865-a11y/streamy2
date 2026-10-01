package app.streamy2;

/**
 * Timing/state of the "Farbwellen" start animation (pure Java, unit tested).
 * Fade in fast, hold until the first screen is ready (but at least {@link #MIN_HOLD_MS}),
 * then cross-fade out. While loading takes longer the waves keep flowing; a subtle loading
 * hint appears after {@link #HINT_AFTER_MS}. {@link #MAX_MS} is a safety cap.
 * With system animations off ("reduced"), a static gradient is shown without fades.
 */
final class IntroTiming {
    static final long FADE_IN_MS = 500L;
    /** Earliest start of the fade-out: total visible time = MIN_HOLD_MS + FADE_OUT_MS ≈ 1.9 s. */
    static final long MIN_HOLD_MS = 1400L;
    static final long FADE_OUT_MS = 500L;
    static final long HINT_AFTER_MS = 3000L;
    static final long HINT_FADE_MS = 400L;
    /** Hard cap: never cover the app longer than this, even if loading is still running. */
    static final long MAX_MS = 10_000L;
    /** Reduced motion: shortest time the static gradient is shown (avoids a flash). */
    static final long REDUCED_MIN_MS = 600L;

    enum State { FADING_IN, SHOWING, FADING_OUT, DONE }

    final long start;
    final boolean reduced;
    private long readyAt = -1L;
    private long fadeOutAt = -1L;

    IntroTiming(long start, boolean reduced) {
        this.start = start;
        this.reduced = reduced;
    }

    /** The first screen has its content. */
    void markReady(long now) {
        if (readyAt < 0) readyAt = Math.max(start, now);
    }

    boolean isReady() {
        return readyAt >= 0;
    }

    /** Key press / tap: end now (with the normal fade-out). */
    void skip(long now) {
        if (fadeOutAt < 0) fadeOutAt = Math.max(start, now);
    }

    long minHold() {
        return reduced ? REDUCED_MIN_MS : MIN_HOLD_MS;
    }

    long fadeOutDuration() {
        return reduced ? 0L : FADE_OUT_MS;
    }

    /** Advances the state; returns the fade-out start or -1 while still holding. */
    long update(long now) {
        if (fadeOutAt < 0) {
            long elapsed = now - start;
            if (readyAt >= 0 && elapsed >= minHold()) {
                fadeOutAt = Math.max(start + minHold(), readyAt);
            } else if (elapsed >= MAX_MS) {
                fadeOutAt = start + MAX_MS;
            }
        }
        return fadeOutAt;
    }

    State state(long now) {
        update(now);
        if (fadeOutAt >= 0 && now >= fadeOutAt) {
            return now - fadeOutAt >= fadeOutDuration() ? State.DONE : State.FADING_OUT;
        }
        if (!reduced && now - start < FADE_IN_MS) return State.FADING_IN;
        return State.SHOWING;
    }

    boolean done(long now) {
        return state(now) == State.DONE;
    }

    static float smooth(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x * x * (3f - 2f * x);
    }

    /** Opacity of the background (covers the app until the cross-fade). */
    float backgroundAlpha(long now) {
        update(now);
        if (fadeOutAt < 0 || now < fadeOutAt) return 1f;
        long d = fadeOutDuration();
        if (d <= 0) return 0f;
        return 1f - smooth((now - fadeOutAt) / (float) d);
    }

    /** Opacity of the waves: fade in, then fade out together with the background. */
    float wavesAlpha(long now) {
        float in = reduced ? 1f : smooth((now - start) / (float) FADE_IN_MS);
        return in * backgroundAlpha(now);
    }

    /** Loading hint: only if still not ready after {@link #HINT_AFTER_MS}. */
    float hintAlpha(long now) {
        update(now);
        long elapsed = now - start;
        if (elapsed < HINT_AFTER_MS) return 0f;
        if (readyAt >= 0 && readyAt < start + HINT_AFTER_MS) return 0f;
        float in = reduced ? 1f : smooth((elapsed - HINT_AFTER_MS) / (float) HINT_FADE_MS);
        return in * backgroundAlpha(now);
    }

    /** Animation time in seconds (fixed for the static reduced-motion frame). */
    float seconds(long now) {
        return reduced ? STATIC_T : Math.max(0, now - start) / 1000f;
    }

    static final float STATIC_T = 1.2f;
}
