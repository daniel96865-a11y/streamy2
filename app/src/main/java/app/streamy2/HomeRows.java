package app.streamy2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Start screen rows (pure logic, no Android): "Läuft gerade" hero, Zuletzt geschaut,
 * Favoriten, Läuft gerade and category chips. Empty rows are left out.
 */
final class HomeRows {
    static final int MAX_RECENT = 15;
    static final int MAX_ROW = 20;
    /** Channels scanned for the "Läuft gerade" row (keeps big playlists fast). */
    static final int NOW_SCAN_LIMIT = 400;

    static final String ROW_RECENT = "Zuletzt geschaut";
    static final String ROW_FAVORITES = "Favoriten";
    static final String ROW_NOW = "Läuft gerade";

    /** Current programme of a channel (EPG), or null. */
    interface EpgLookup {
        Models.Epg current(Models.Channel channel, long now);
    }

    static final class Row {
        final String title;
        final List<Models.Channel> channels;

        Row(String title, List<Models.Channel> channels) {
            this.title = title;
            this.channels = channels;
        }
    }

    static final class Chip {
        final String id;
        final String name;
        final int count;

        Chip(String id, String name, int count) {
            this.id = id;
            this.name = name;
            this.count = count;
        }
    }

    static final class Home {
        Models.Channel hero;
        Models.Epg heroEpg;
        final List<Row> rows = new ArrayList<>();
        final List<Chip> chips = new ArrayList<>();

        boolean isEmpty() {
            return hero == null && rows.isEmpty() && chips.isEmpty();
        }
    }

    private HomeRows() {
    }

    // --- id lists (recents / favourites) ---

    static List<String> splitIds(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split("\n")) {
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static String joinIds(List<String> ids) {
        StringBuilder b = new StringBuilder();
        if (ids != null) {
            for (String s : ids) {
                if (s == null || s.isEmpty() || s.indexOf('\n') >= 0) continue;
                if (b.length() > 0) b.append('\n');
                b.append(s);
            }
        }
        return b.toString();
    }

    static List<String> pushRecent(List<String> ids, String id, int max) {
        ArrayList<String> out = new ArrayList<>();
        if (id != null && !id.isEmpty()) out.add(id);
        if (ids != null) for (String s : ids) if (s != null && !s.equals(id) && out.size() < max) out.add(s);
        while (out.size() > max) out.remove(out.size() - 1);
        return out;
    }

    /** Adds/removes {@code id}; returns true if it is in the list afterwards. */
    static boolean toggle(List<String> ids, String id) {
        if (ids == null || id == null || id.isEmpty()) return false;
        if (ids.remove(id)) return false;
        ids.add(id);
        return true;
    }

    // --- channels ---

    static String key(Models.Channel c) {
        if (c == null) return null;
        if (c.id != null && !c.id.isEmpty()) return c.id;
        return c.name;
    }

    /** Channels for the ids, in id order; unknown ids and section headers are skipped. */
    static List<Models.Channel> resolve(List<String> ids, List<Models.Channel> channels, int max) {
        ArrayList<Models.Channel> out = new ArrayList<>();
        if (ids == null || ids.isEmpty() || channels == null) return out;
        Map<String, Models.Channel> byId = new HashMap<>();
        for (Models.Channel c : channels) {
            String k = key(c);
            if (c != null && !c.header && k != null && !byId.containsKey(k)) byId.put(k, c);
        }
        for (String id : ids) {
            Models.Channel c = byId.get(id);
            if (c != null && !out.contains(c)) out.add(c);
            if (out.size() >= max) break;
        }
        return out;
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

    static List<Models.Channel> nowPlaying(List<Models.Channel> channels, EpgLookup epg, long now, int max, int scanLimit) {
        ArrayList<Models.Channel> out = new ArrayList<>();
        if (channels == null || epg == null) return out;
        int scanned = 0;
        for (Models.Channel c : channels) {
            if (c == null || c.header) continue;
            if (++scanned > scanLimit || out.size() >= max) break;
            if (covers(epg.current(c, now), now)) out.add(c);
        }
        return out;
    }

    static List<Chip> chips(List<Models.Category> cats, List<Models.Channel> channels) {
        ArrayList<Chip> out = new ArrayList<>();
        if (channels == null) return out;
        Map<String, Integer> counts = new HashMap<>();
        int all = 0;
        for (Models.Channel c : channels) {
            if (c == null || c.header) continue;
            all++;
            if (c.categoryId != null) {
                Integer n = counts.get(c.categoryId);
                counts.put(c.categoryId, n == null ? 1 : n + 1);
            }
        }
        if (all == 0) return out;
        out.add(new Chip("all", "Alle Sender", all));
        if (cats != null) {
            for (Models.Category cat : cats) {
                if (cat == null || cat.id == null) continue;
                Integer n = counts.get(cat.id);
                if (n != null && n > 0) out.add(new Chip(cat.id, cat.name == null ? "" : cat.name, n));
            }
        }
        return out;
    }

    /**
     * Builds the start screen. Hero: first channel with a current programme from recents,
     * then favourites, then "Läuft gerade"; without EPG the last watched channel.
     */
    static Home build(List<Models.Channel> channels, List<Models.Category> cats, List<String> recentIds,
                      List<String> favoriteIds, EpgLookup epg, long now) {
        Home home = new Home();
        List<Models.Channel> recent = resolve(recentIds, channels, MAX_ROW);
        List<Models.Channel> fav = resolve(favoriteIds, channels, MAX_ROW);
        List<Models.Channel> live = nowPlaying(channels, epg, now, MAX_ROW, NOW_SCAN_LIMIT);
        if (!recent.isEmpty()) home.rows.add(new Row(ROW_RECENT, recent));
        if (!fav.isEmpty()) home.rows.add(new Row(ROW_FAVORITES, fav));
        if (!live.isEmpty()) home.rows.add(new Row(ROW_NOW, live));
        home.chips.addAll(chips(cats, channels));
        List<List<Models.Channel>> order = new ArrayList<>();
        order.add(recent);
        order.add(fav);
        order.add(live);
        for (List<Models.Channel> l : order) {
            for (Models.Channel c : l) {
                Models.Epg e = epg == null ? null : epg.current(c, now);
                if (covers(e, now)) {
                    home.hero = c;
                    home.heroEpg = e;
                    return home;
                }
            }
        }
        if (!recent.isEmpty()) home.hero = recent.get(0);
        return home;
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
