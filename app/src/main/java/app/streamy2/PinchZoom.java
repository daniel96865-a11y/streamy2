package app.streamy2;

/** Phone player pinch gesture: spread = Ausfüllen, pinch = Einpassen. */
final class PinchZoom {
    static final float OUT = 1.10f;
    static final float IN = 0.90f;

    private PinchZoom() {
    }

    /** New resize mode after a pinch with the accumulated scale factor. */
    static String decide(String current, float factor) {
        if (factor >= OUT) return "zoom";
        if (factor <= IN) return "fit";
        return current == null ? "fit" : current;
    }

    static String label(String mode) {
        if ("zoom".equals(mode)) return "Ausfüllen";
        if ("stretch".equals(mode)) return "Strecken";
        return "Einpassen";
    }
}
