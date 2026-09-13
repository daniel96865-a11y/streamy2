package app.streamy2;

/** Shared time checks for the guide, list and player. */
final class EpgTime {
    static boolean isCurrent(Models.Epg epg, long now) {
        return epg != null && epg.title != null && !epg.title.isEmpty()
                && epg.start > 0 && epg.start <= now && epg.end > now;
    }
    static EpgGuide.Listing current(java.util.List<EpgGuide.Listing> items, long now) {
        EpgGuide.Listing current = null;
        for (EpgGuide.Listing item : items) {
            if (item != null && item.start > 0 && item.start <= now && now < item.stop
                    && (current == null || item.start > current.start)) current = item;
        }
        return current;
    }
    private EpgTime() {}
}
