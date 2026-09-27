package app.streamy2;

/**
 * Picture size math for the phone player. Given the real window size and the video
 * size (with pixel aspect ratio), returns the displayed picture size for a mode:
 * "fit" = Einpassen (whole picture, largest size that fits), "zoom" = Ausfüllen
 * (fills the window, crops), "stretch" = Strecken (fills, distorts).
 */
final class VideoFit {
    private VideoFit() {
    }

    /** Displayed {width, height} in px. Unknown video size falls back to the window size. */
    static int[] size(int viewW, int viewH, int videoW, int videoH, float pixelRatio, String mode) {
        if (viewW <= 0 || viewH <= 0) return new int[]{0, 0};
        if (videoW <= 0 || videoH <= 0 || "stretch".equals(mode)) return new int[]{viewW, viewH};
        float par = pixelRatio > 0f ? pixelRatio : 1f;
        double videoAspect = (videoW * (double) par) / videoH;
        double viewAspect = viewW / (double) viewH;
        boolean zoom = "zoom".equals(mode);
        boolean widthLimited = videoAspect > viewAspect; // video is wider than the window
        if (widthLimited != zoom) {
            // full width
            return new int[]{viewW, (int) Math.round(viewW / videoAspect)};
        }
        // full height
        return new int[]{(int) Math.round(viewH * videoAspect), viewH};
    }

    /** Share of the picture area that is cut off in "zoom", 0..100. */
    static int cropPercent(int viewW, int viewH, int videoW, int videoH, float pixelRatio) {
        int[] s = size(viewW, viewH, videoW, videoH, pixelRatio, "zoom");
        if (s[0] <= 0 || s[1] <= 0) return 0;
        double visible = (Math.min(s[0], viewW) * (double) Math.min(s[1], viewH)) / (s[0] * (double) s[1]);
        return (int) Math.round((1.0 - visible) * 100.0);
    }

    /** Share of the window covered by the picture, 0..100. */
    static int coveragePercent(int viewW, int viewH, int videoW, int videoH, float pixelRatio, String mode) {
        int[] s = size(viewW, viewH, videoW, videoH, pixelRatio, mode);
        if (viewW <= 0 || viewH <= 0) return 0;
        double covered = Math.min(s[0], viewW) * (double) Math.min(s[1], viewH);
        return (int) Math.round(covered * 100.0 / (viewW * (double) viewH));
    }
}
