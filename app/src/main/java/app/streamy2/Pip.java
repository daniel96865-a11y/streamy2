package app.streamy2;

/**
 * Bild-in-Bild (Picture-in-Picture) rules, only for the mobile app. Pure logic so it can be
 * tested without a device; PlayerActivity does the Android calls.
 */
final class Pip {
    /** Android's allowed PiP aspect ratio range is about 1:2.39 .. 2.39:1. */
    static final float MAX_RATIO = 2.39f;
    static final String ACTION_CONTROL = "app.streamy2.PIP_CONTROL";
    static final String EXTRA_CONTROL = "control";
    static final int CONTROL_PLAY_PAUSE = 1;

    private Pip() {
    }

    static boolean isMobileFlavor(String flavor) {
        return "mobile".equals(flavor);
    }

    /** PiP possible: mobile app, Android 8+, device supports it, setting on. */
    static boolean allowed(boolean mobile, int sdk, boolean deviceSupports, boolean prefEnabled) {
        return mobile && sdk >= 26 && deviceSupports && prefEnabled;
    }

    /** Enter PiP automatically when leaving the app (Home / app switch)? Only with video playing. */
    static boolean autoEnter(boolean allowed, boolean autoPref, boolean playing, boolean finishing) {
        return allowed && autoPref && playing && !finishing;
    }

    /** onUserLeaveHint fallback is only needed below Android 12 (there setAutoEnterEnabled works). */
    static boolean useLeaveHint(int sdk) {
        return sdk < 31;
    }

    /**
     * Aspect ratio {numerator, denominator} from the video size and pixel aspect ratio,
     * clamped to the allowed range; 16:9 when the size is unknown.
     */
    static int[] aspect(int width, int height, float pixelRatio) {
        if (width <= 0 || height <= 0) return new int[]{16, 9};
        float par = pixelRatio > 0f && !Float.isNaN(pixelRatio) && !Float.isInfinite(pixelRatio) ? pixelRatio : 1f;
        float ratio = width * par / height;
        if (ratio > MAX_RATIO) return new int[]{239, 100};
        if (ratio < 1f / MAX_RATIO) return new int[]{100, 239};
        int w = Math.round(width * par);
        int h = height;
        int g = gcd(w, h);
        return new int[]{w / g, h / g};
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    /**
     * What to do when the PiP window goes away: {@code true} = the user closed it (stop and
     * release playback), {@code false} = it was expanded back to the full player.
     * Closing leaves the activity stopped (not started) without PiP.
     */
    static boolean closedByUser(boolean wasInPip, boolean nowInPip, boolean started) {
        return wasInPip && !nowInPip && !started;
    }
}
