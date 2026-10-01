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

    /** Stable key of a channel (id, else name), e.g. for logo slots in the timeline. */
    static String key(Models.Channel c) {
        if (c == null) return null;
        if (c.id != null && !c.id.isEmpty()) return c.id;
        return c.name;
    }

    static boolean covers(Models.Epg e, long now) {
        return e != null && e.start > 0 && e.end > e.start && e.start <= now && now < e.end;
    }

    /** Progress 0..100 of the programme at {@code now}. */
    static int progress(Models.Epg e, long now) {
        if (e == null || e.end <= e.start) return 0;
        if (now <= e.start) return 0;
        if (now >= e.end) return 100;
        return (int) ((now - e.start) * 100L / (e.end - e.start));
    }

    /** "noch 35 Min." / "noch 1 Std. 5 Min." */
    static String remaining(Models.Epg e, long now) {
        if (!covers(e, now)) return "";
        long min = Math.max(1, (e.end - now + 59999) / 60000);
        if (min < 60) return "noch " + min + " Min.";
        long h = min / 60, m = min % 60;
        return "noch " + h + " Std." + (m > 0 ? " " + m + " Min." : "");
    }
}
