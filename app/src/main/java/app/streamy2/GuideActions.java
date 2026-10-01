package app.streamy2;

/** What OK/tap does on a programme block, and its state label (pure, unit tested). */
final class GuideActions {
    static final int PLAY_LIVE = 1;
    static final int PLAY_CATCHUP = 2;
    static final int SHOW_INFO = 3;

    private GuideActions() {
    }

    static int onOpen(EpgGuide.Listing l, long now, boolean catchupPossible) {
        if (l == null || (l.start <= now && now < l.stop)) return PLAY_LIVE;
        if (l.stop <= now && catchupPossible) return PLAY_CATCHUP;
        return SHOW_INFO;
    }

    static String state(EpgGuide.Listing l, long now) {
        if (l == null) return "";
        if (l.start <= now && now < l.stop) return "Läuft gerade";
        if (l.stop <= now) return "Vorbei";
        return "Später";
    }
}
