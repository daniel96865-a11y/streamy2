package app.streamy2;

import android.util.Xml;
import app.streamy2.EpgGuide;
import app.streamy2.Models;
import com.google.common.net.HttpHeaders;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.xmlpull.v1.XmlPullParser;

/* loaded from: classes.dex */
public class EpgGuide {
    public volatile int channelCount;
    public volatile String error;
    public volatile boolean loading;
    public volatile int programmeCount;
    private final ConcurrentHashMap<String, List<Listing>> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nameToId = new ConcurrentHashMap<>();

    /** Extra Vavoo display-name → preferred XMLTV lookup keys (after normName). */
    private static final Map<String, String[]> DE_TOP = new HashMap<>();
    static {
        DE_TOP.put("das erste", new String[]{"das erste", "ard", "ard das erste"});
        DE_TOP.put("ard", new String[]{"das erste", "ard"});
        DE_TOP.put("zdf", new String[]{"zdf"});
        DE_TOP.put("rtl", new String[]{"rtl"});
        DE_TOP.put("sat1", new String[]{"sat1", "sat 1"});
        DE_TOP.put("prosieben", new String[]{"prosieben", "pro sieben", "pro7"});
        DE_TOP.put("vox", new String[]{"vox"});
        DE_TOP.put("kabel eins", new String[]{"kabel eins", "kabeleins", "kabel1"});
        DE_TOP.put("rtlzwei", new String[]{"rtlzwei", "rtl 2", "rtl2"});
        DE_TOP.put("nitro", new String[]{"nitro", "rtl nitro"});
        DE_TOP.put("ntv", new String[]{"ntv", "n-tv"});
        DE_TOP.put("welt", new String[]{"welt", "n24"});
        DE_TOP.put("phoenix", new String[]{"phoenix"});
        DE_TOP.put("tagesschau24", new String[]{"tagesschau24", "tagesschau 24"});
        DE_TOP.put("zdfinfo", new String[]{"zdfinfo", "zdf info"});
        DE_TOP.put("zdfneo", new String[]{"zdfneo", "zdf neo"});
        DE_TOP.put("3sat", new String[]{"3sat"});
        DE_TOP.put("arte", new String[]{"arte"});
        DE_TOP.put("one", new String[]{"one", "ard one"});
        DE_TOP.put("sport1", new String[]{"sport1", "sport 1"});
        DE_TOP.put("super rtl", new String[]{"super rtl", "superrtl"});
        DE_TOP.put("superrtl", new String[]{"super rtl", "superrtl"});
        DE_TOP.put("dmax", new String[]{"dmax"});
        DE_TOP.put("tele 5", new String[]{"tele 5", "tele5"});
        DE_TOP.put("tele5", new String[]{"tele 5", "tele5"});
        DE_TOP.put("sixx", new String[]{"sixx"});
        DE_TOP.put("prosieben maxx", new String[]{"prosieben maxx", "pro7 maxx"});
        DE_TOP.put("sat1 gold", new String[]{"sat1 gold", "sat 1 gold"});
        DE_TOP.put("rtlup", new String[]{"rtlup", "rtl up"});
        DE_TOP.put("voxup", new String[]{"voxup", "vox up"});
        DE_TOP.put("comedy central", new String[]{"comedy central"});
        DE_TOP.put("nickelodeon", new String[]{"nickelodeon", "nick"});
        DE_TOP.put("nick", new String[]{"nick", "nickelodeon"});
        DE_TOP.put("disney channel", new String[]{"disney channel", "disney"});
        DE_TOP.put("kika", new String[]{"kika"});
        DE_TOP.put("wdr", new String[]{"wdr", "wdr koeln"});
        DE_TOP.put("ndr", new String[]{"ndr", "ndr fs hh"});
        DE_TOP.put("mdr", new String[]{"mdr", "mdr sachsen"});
        DE_TOP.put("br", new String[]{"br", "br fernsehen"});
        DE_TOP.put("hr", new String[]{"hr", "hr fernsehen"});
        DE_TOP.put("rbb", new String[]{"rbb", "rbb berlin"});
        DE_TOP.put("swr", new String[]{"swr", "swr sr"});
        DE_TOP.put("servus tv", new String[]{"servus tv", "servustv"});
        DE_TOP.put("servustv", new String[]{"servus tv", "servustv"});
        DE_TOP.put("orf 1", new String[]{"orf 1", "orf1"});
        DE_TOP.put("orf1", new String[]{"orf 1", "orf1"});
        DE_TOP.put("orf 2", new String[]{"orf 2", "orf2"});
        DE_TOP.put("orf2", new String[]{"orf 2", "orf2"});
    }

    public static class Listing {
        public long start;
        public long stop;
        public String title = "";
        public String desc = "";
    }

    public void clear() {
        this.byId.clear();
        this.nameToId.clear();
        this.programmeCount = 0;
        this.channelCount = 0;
        this.error = null;
    }

    /**
     * Shrink in-memory EPG under memory pressure.
     * @param aggressive drop most programme maps (keep only a thin current-window slice)
     */
    public void trim(boolean aggressive) {
        try {
            long now = System.currentTimeMillis();
            long keepPast = aggressive ? 30L * 60L * 1000L : 60L * 60L * 1000L;
            long keepFuture = aggressive ? 2L * 60L * 60L * 1000L : 4L * 60L * 60L * 1000L;
            int maxPer = aggressive ? 4 : 12;
            int keptProg = 0;
            for (Map.Entry<String, List<Listing>> e : this.byId.entrySet()) {
                List<Listing> src = e.getValue();
                if (src == null || src.isEmpty()) {
                    continue;
                }
                ArrayList<Listing> kept = new ArrayList<>();
                for (Listing listing : src) {
                    if (listing == null) continue;
                    if (listing.stop < now - keepPast) continue;
                    if (listing.start > now + keepFuture) continue;
                    kept.add(listing);
                    if (kept.size() >= maxPer) break;
                }
                if (aggressive && kept.isEmpty() && !src.isEmpty()) {
                    // Keep at most one near-now listing so channel keys survive lightly
                    Listing best = null;
                    for (Listing listing : src) {
                        if (listing == null) continue;
                        if (listing.start <= now && listing.stop >= now) {
                            best = listing;
                            break;
                        }
                        if (best == null) best = listing;
                    }
                    if (best != null) kept.add(best);
                }
                e.setValue(kept);
                keptProg += kept.size();
            }
            if (aggressive && this.byId.size() > 400) {
                // Drop empty channel buckets to free map entries
                ArrayList<String> drop = new ArrayList<>();
                for (Map.Entry<String, List<Listing>> e : this.byId.entrySet()) {
                    List<Listing> v = e.getValue();
                    if (v == null || v.isEmpty()) drop.add(e.getKey());
                }
                for (String k : drop) this.byId.remove(k);
            }
            this.programmeCount = keptProg;
            this.channelCount = this.byId.size();
        } catch (Throwable ignored) {
        }
    }

    /** Prefer a single successful feed on low-RAM (skip merging extras). */
    public boolean preferSingleFeed() {
        return App.isLowRam();
    }

    public int maxProgrammesPerChannel() {
        return App.isLowRam() ? 24 : 96;
    }

    public long parseWindowPastMs() {
        return App.isLowRam() ? 3600000L : 7200000L; // 1h / 2h
    }

    public long parseWindowFutureMs() {
        return App.isLowRam() ? 14400000L : 28800000L; // 4h / 8h
    }

    /**
     * Prefer name-based XMLTV so HD/FHD/UHD/name variants share one guide.
     * Playlist epgChannelId is only used when no normalized-name match exists —
     * otherwise divergent Xtream epgIds (e.g. 13TH STREET HD vs FHD) split the EPG.
     */
    public Models.Epg forChannel(Models.Channel channel) {
        return forChannel(channel, false);
    }

    /**
     * @param allowFuzzy O(nameToId) contains-scan. Never true on the UI thread or bulk apply().
     */
    public Models.Epg forChannel(Models.Channel channel, boolean allowFuzzy) {
        Models.Epg lookup;
        Models.Epg lookup2;
        if (channel == null || channel.header) {
            return null;
        }
        // Exact/alias name first (no fuzzy O(n) scan) so HD/FHD share a guide without
        // stalling the UI when apply() walks thousands of live rows.
        String exactNameId = findNameId(normName(channel.name), false);
        if (exactNameId != null) {
            Models.Epg byName = current(exactNameId);
            if (byName != null) {
                channel.epgChannelId = exactNameId;
                return byName;
            }
        }
        Models.Epg lookup3 = lookup(channel.epgChannelId);
        if (lookup3 != null) {
            return lookup3;
        }
        if (channel.epgChannelId != null && channel.epgChannelId.contains("@") && (lookup2 = lookup(channel.epgChannelId.substring(0, channel.epgChannelId.indexOf(64)))) != null) {
            return lookup2;
        }
        Models.Epg lookup4 = lookup(channel.id);
        if (lookup4 != null) {
            return lookup4;
        }
        if (channel.id != null && channel.id.startsWith("iptv:") && (lookup = lookup(channel.id.substring(5))) != null) {
            return lookup;
        }
        // Fuzzy name only as last resort off the UI thread (player seed).
        if (allowFuzzy && exactNameId == null) {
            String fuzzyId = findNameId(normName(channel.name), true);
            if (fuzzyId != null) {
                Models.Epg byFuzzy = current(fuzzyId);
                if (byFuzzy != null) {
                    channel.epgChannelId = fuzzyId;
                    return byFuzzy;
                }
            }
        }
        return null;
    }

    /** True when XMLTV indexes the normalized display name (skip Xtream shortEpg). */
    public boolean hasNameMatch(Models.Channel channel) {
        if (channel == null) {
            return false;
        }
        // Exact/alias only — fuzzy scan is too expensive for askEpg on the UI thread.
        return findNameId(normName(channel.name), false) != null;
    }

    public List<Listing> listingsFor(Models.Channel channel) {
        List<Listing> list;
        ArrayList arrayList = new ArrayList();
        if (channel == null) {
            return arrayList;
        }
        String keyOf = keyOf(channel);
        if (keyOf != null && (list = this.byId.get(keyOf)) != null) {
            arrayList.addAll(list);
        }
        arrayList.sort(new Comparator() { // from class: app.streamy2.EpgGuide$$ExternalSyntheticLambda1
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Long.compare(((EpgGuide.Listing) obj).start, ((EpgGuide.Listing) obj2).start);
                return compare;
            }
        });
        return arrayList;
    }

    public void putListings(Models.Channel channel, List<Listing> list) {
        if (channel == null || list == null || list.isEmpty()) {
            return;
        }
        String norm = norm((channel.epgChannelId == null || channel.epgChannelId.isEmpty()) ? channel.id : channel.epgChannelId);
        if (norm.isEmpty()) {
            norm = norm(channel.id);
        }
        List<Listing> list2 = this.byId.get(norm);
        HashMap hashMap = new HashMap();
        if (list2 != null) {
            for (Listing listing : list2) {
                hashMap.put(listing.start + "|" + listing.stop, listing);
            }
        }
        for (Listing listing2 : list) {
            hashMap.put(listing2.start + "|" + listing2.stop, listing2);
        }
        ArrayList arrayList = new ArrayList(hashMap.values());
        arrayList.sort(new Comparator() { // from class: app.streamy2.EpgGuide$$ExternalSyntheticLambda0
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Long.compare(((EpgGuide.Listing) obj).start, ((EpgGuide.Listing) obj2).start);
                return compare;
            }
        });
        this.byId.put(norm, arrayList);
        if (channel.name != null) {
            this.nameToId.put(normName(channel.name), norm);
        }
        this.channelCount = this.byId.size();
        Iterator<List<Listing>> it = this.byId.values().iterator();
        int i = 0;
        while (it.hasNext()) {
            i += it.next().size();
        }
        this.programmeCount = i;
    }

    /** Union listings for the same XMLTV id so a weaker extra feed cannot wipe a richer first feed. */
    @SuppressWarnings("unchecked")
    private void mergeProgrammes(Map incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        int max = maxProgrammesPerChannel();
        for (Object raw : incoming.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) raw;
            if (!(e.getKey() instanceof String) || !(e.getValue() instanceof List)) {
                continue;
            }
            String key = (String) e.getKey();
            List add = (List) e.getValue();
            if (add == null || add.isEmpty()) {
                continue;
            }
            List<Listing> existing = this.byId.get(key);
            if (existing == null || existing.isEmpty()) {
                this.byId.put(key, new ArrayList<Listing>(add));
                continue;
            }
            HashMap<String, Listing> uniq = new HashMap<>();
            for (Listing listing : existing) {
                if (listing != null) {
                    uniq.put(listing.start + "|" + listing.stop, listing);
                }
            }
            for (Object item : add) {
                if (!(item instanceof Listing)) {
                    continue;
                }
                Listing listing = (Listing) item;
                uniq.putIfAbsent(listing.start + "|" + listing.stop, listing);
            }
            ArrayList<Listing> merged = new ArrayList<>(uniq.values());
            merged.sort(new Comparator<Listing>() {
                @Override
                public int compare(Listing a, Listing b) {
                    return Long.compare(a.start, b.start);
                }
            });
            if (merged.size() > max) {
                long nowMs = System.currentTimeMillis();
                int pivot = 0;
                for (int i = 0; i < merged.size(); i++) {
                    Listing L = merged.get(i);
                    if (L == null) continue;
                    if (L.start <= nowMs && L.stop > nowMs) {
                        pivot = i;
                        break;
                    }
                    if (L.start > nowMs) {
                        pivot = Math.max(0, i - 1);
                        break;
                    }
                    pivot = i;
                }
                int from = Math.max(0, pivot - Math.max(1, max / 4));
                int to = Math.min(merged.size(), from + max);
                from = Math.max(0, to - max);
                merged = new ArrayList<>(merged.subList(from, to));
            }
            this.byId.put(key, merged);
        }
    }

    private String keyOf(Models.Channel channel) {
        String nameId = findNameId(normName(channel.name), false);
        if (nameId != null && this.byId.containsKey(nameId)) {
            return nameId;
        }
        if (channel.epgChannelId != null && !channel.epgChannelId.isEmpty()) {
            String norm = norm(channel.epgChannelId);
            if (this.byId.containsKey(norm)) {
                return norm;
            }
            int indexOf = norm.indexOf(64);
            if (indexOf > 0 && this.byId.containsKey(norm.substring(0, indexOf))) {
                return norm.substring(0, indexOf);
            }
        }
        return (channel.id == null || !this.byId.containsKey(norm(channel.id))) ? nameId : norm(channel.id);
    }

    private String findNameId(String str) {
        return findNameId(str, true);
    }

    /**
     * Resolve XMLTV id from a normalized display name.
     * @param allowFuzzy when false, skip the O(nameToId) contains scan — required for
     *                   bulk apply()/askEpg on the UI thread after large internet XMLTV loads.
     */
    private String findNameId(String str, boolean allowFuzzy) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        String hit = lookupNameKey(str);
        if (hit != null) {
            return hit;
        }
        String[] top = DE_TOP.get(str);
        if (top != null) {
            for (String alias : top) {
                hit = lookupNameKey(alias);
                if (hit != null) {
                    return hit;
                }
                hit = lookupNameKey(alias.replace(" ", ""));
                if (hit != null) {
                    return hit;
                }
            }
        }
        String compact = str.replace(" ", "");
        hit = lookupNameKey(compact);
        if (hit != null) {
            return hit;
        }
        top = DE_TOP.get(compact);
        if (top != null) {
            for (String alias : top) {
                hit = lookupNameKey(alias);
                if (hit != null) {
                    return hit;
                }
            }
        }
        for (String alias : aliases(str)) {
            hit = lookupNameKey(alias);
            if (hit != null) {
                return hit;
            }
            hit = lookupNameKey(alias.replace(" ", ""));
            if (hit != null) {
                return hit;
            }
        }
        // Drop common IPTV prefixes/suffixes and retry (e.g. "ard das erste", "ndr fs hh", "rtl deutschland")
        String stripped = str.replaceAll("\\b(ard|das|fs|fernsehen|deutschland|austria|osterr?eich|sat|backup|koeln|koln|hh|hamburg|sachsen|bw|baden|wuerttemberg|berlin|brandenburg|vip)\\b", " ")
                .trim().replaceAll("\\s+", " ");
        if (!stripped.isEmpty() && !stripped.equals(str)) {
            hit = findNameId(stripped, allowFuzzy);
            if (hit != null) {
                return hit;
            }
        }
        String[] split = str.split(" ");
        if (split.length >= 2) {
            hit = lookupNameKey(split[0] + " " + split[1]);
            if (hit != null) {
                return hit;
            }
            // Only known regional bases: "mdr sachsen" → mdr. Never "rtl crime" → rtl.
            if (isRegionalBase(split[0])) {
                hit = lookupNameKey(split[0]);
                if (hit != null) {
                    return hit;
                }
            }
        }
        if (!allowFuzzy) {
            return null;
        }
        // Fuzzy contains: longest known key contained in query (min length 4), or query contained in key
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String> e : this.nameToId.entrySet()) {
            String key = e.getKey();
            if (key == null || key.length() < 4) {
                continue;
            }
            if (str.contains(key) || compact.contains(key.replace(" ", "")) || (str.length() >= 4 && key.contains(str))) {
                if (key.length() > bestLen) {
                    bestLen = key.length();
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    private static boolean isRegionalBase(String token) {
        if (token == null || token.length() < 2) {
            return false;
        }
        return "mdr".equals(token) || "ndr".equals(token) || "wdr".equals(token)
                || "br".equals(token) || "hr".equals(token) || "rbb".equals(token)
                || "swr".equals(token) || "orf".equals(token) || "sr".equals(token);
    }

    private String lookupNameKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return this.nameToId.get(key);
    }

    private static void indexName(Map<String, String> map, String str, String str2) {
        String normName = normName(str);
        if (normName.isEmpty()) {
            return;
        }
        putFree(map, normName, str2);
        putFree(map, normName.replace(" ", ""), str2);
        for (String str3 : aliases(normName)) {
            putFree(map, str3, str2);
        }
        String[] split = normName.split(" ");
        if (split.length >= 3) {
            putFree(map, split[0] + " " + split[1], str2);
        }
    }

    private static void putFree(Map<String, String> map, String str, String str2) {
        if (str == null || str.length() < 2 || str2 == null || map.containsKey(str)) {
            return;
        }
        map.put(str, str2);
    }

    public int apply(List<Models.Channel> list) {
        int i = 0;
        if (list == null) {
            return 0;
        }
        try {
            for (Models.Channel channel : new ArrayList<Models.Channel>(list)) {
                try {
                    if (channel == null || channel.header) {
                        continue;
                    }
                    Models.Epg forChannel = forChannel(channel, false);
                    if (forChannel != null) {
                        channel.epg = forChannel;
                        i++;
                    }
                } catch (Exception unused) {
                }
            }
            int unified = applyUnified(list);
            if (unified > i) {
                i = unified;
            }
        } catch (Exception unused2) {
        }
        return i;
    }

    /**
     * Propagate one Models.Epg to every live row that shares the same normName
     * (HD / FHD / UHD / DE: / .c variants). Prefer a guide hit from name match;
     * otherwise the richest non-empty title already on a sibling in the group.
     */
    public int applyUnified(List<Models.Channel> list) {
        int matched = 0;
        if (list == null || list.isEmpty()) {
            return 0;
        }
        try {
            // Snapshot — catalog.live may be mutated by Vavoo.merge on another thread.
            List<Models.Channel> snapshot = new ArrayList<>(list);
            HashMap<String, ArrayList<Models.Channel>> groups = new HashMap<>();
            for (Models.Channel channel : snapshot) {
                if (channel == null || channel.header || channel.name == null) {
                    continue;
                }
                String key = normName(channel.name);
                if (key.isEmpty()) {
                    continue;
                }
                ArrayList<Models.Channel> g = groups.get(key);
                if (g == null) {
                    g = new ArrayList<>();
                    groups.put(key, g);
                }
                g.add(channel);
            }
            for (Map.Entry<String, ArrayList<Models.Channel>> e : groups.entrySet()) {
                ArrayList<Models.Channel> g = e.getValue();
                Models.Epg best = null;
                String sharedId = findNameId(e.getKey(), false);
                if (sharedId != null) {
                    best = current(sharedId);
                }
                if (best == null) {
                    for (Models.Channel c : g) {
                        if (c.epg != null && c.epg.title != null && !c.epg.title.isEmpty()) {
                            best = c.epg;
                            break;
                        }
                    }
                }
                if (best == null) {
                    continue;
                }
                for (Models.Channel c : g) {
                    c.epg = best;
                    if (sharedId != null && !sharedId.isEmpty()) {
                        c.epgChannelId = sharedId;
                    }
                    matched++;
                }
            }
        } catch (Exception unused) {
        }
        return matched;
    }

    /** Cheap unify: copy EPG only to siblings that share seed's normName (askEpg path). */
    public int applyUnifiedSiblings(Models.Channel seed, List<Models.Channel> list) {
        if (seed == null || seed.name == null || list == null || list.isEmpty()) {
            return 0;
        }
        int matched = 0;
        try {
            String key = normName(seed.name);
            if (key.isEmpty()) {
                return 0;
            }
            Models.Epg best = seed.epg;
            String sharedId = findNameId(key, false);
            if (sharedId != null) {
                Models.Epg byName = current(sharedId);
                if (byName != null) {
                    best = byName;
                }
            }
            if (best == null || best.title == null || best.title.isEmpty()) {
                return 0;
            }
            for (Models.Channel c : new ArrayList<>(list)) {
                if (c == null || c.header || c.name == null) {
                    continue;
                }
                if (!key.equals(normName(c.name))) {
                    continue;
                }
                c.epg = best;
                if (sharedId != null && !sharedId.isEmpty()) {
                    c.epgChannelId = sharedId;
                }
                matched++;
            }
        } catch (Exception unused) {
        }
        return matched;
    }

    public void loadUrl(String str, File file, boolean z) throws Exception {
        loadUrl(str, file, z, false);
    }

    public void loadUrlMerge(String str, File file, boolean z) throws Exception {
        loadUrl(str, file, z, true);
    }

    private void loadUrl(String str, File file, boolean z, boolean z2) throws Exception {
        if (str == null || str.trim().isEmpty()) {
            throw new Exception("Keine EPG-URL");
        }
        String trim = str.trim();
        if (file != null && file.exists() && file.length() > 100663296) {
            file.delete();
        }
        if (!z && file != null && file.exists() && file.length() > 200) {
            int beforeProgrammes = this.programmeCount;
            try {
                parseFile(file, z2);
            } catch (Exception unused) {
                try {
                    file.delete();
                } catch (Exception unused2) {
                }
            }
            if (!z2 && this.channelCount > 0 && this.programmeCount > 0) {
                return;
            }
            // Merge mode: only reuse cache if THIS file actually contributed programmes.
            // Otherwise channelCount may already be >0 from a prior feed and we'd keep a bad/empty cache.
            if (z2 && this.programmeCount > beforeProgrammes) {
                return;
            }
        }
        download(trim, file);
        parseFile(file, z2);
        if (this.channelCount == 0 || this.programmeCount == 0) {
            throw new Exception("XMLTV ohne Programme");
        }
    }

    public void loadFile(File file) throws Exception {
        parseFile(file, false);
    }

    private static final long EPG_MIN_BYTES = 100 * 1024L;
    private static final long EPG_MAX_BYTES = 90 * 1024 * 1024L;
    private static final long EPG_MAX_BYTES_LOW = 32 * 1024 * 1024L;

    private long epgMaxBytes() {
        return App.isLowRam() ? EPG_MAX_BYTES_LOW : EPG_MAX_BYTES;
    }
    private static final int EPG_DOWNLOAD_ATTEMPTS = 3;

    private void download(String str, File file) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= EPG_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadOnce(str, file);
                return;
            } catch (Exception e) {
                last = e;
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
                boolean retryable = e instanceof java.io.IOException
                        || e instanceof java.net.SocketException
                        || msg.contains("closed")
                        || msg.contains("connection")
                        || msg.contains("reset")
                        || msg.contains("timeout");
                if (!retryable || attempt >= EPG_DOWNLOAD_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(400L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        if (last != null) {
            throw last;
        }
        throw new Exception("EPG-Download fehlgeschlagen");
    }

    private void downloadOnce(String str, File file) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        File part = null;
        try {
            conn = (HttpURLConnection) new URL(str).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty(HttpHeaders.ACCEPT, "application/xml,text/xml,application/gzip,*/*");
            conn.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "identity");
            int code = conn.getResponseCode();
            in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new Exception("HTTP " + code);
            }
            // Keep compressed body as-is when URL/encoding is gzip; parseFile handles gzip magic.
            // Only unwrap transport-level gzip when the URL is not already a .gz file.
            String encoding = conn.getContentEncoding();
            boolean transportGzip = encoding != null && encoding.toLowerCase(Locale.US).contains("gzip");
            boolean urlGz = str.toLowerCase(Locale.US).contains(".gz");
            if (transportGzip && !urlGz) {
                try {
                    in = new GZIPInputStream(in);
                } catch (Exception unused) {
                }
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            part = new File(file.getPath() + ".part");
            out = new FileOutputStream(part);
            byte[] buf = new byte[16384];
            long total = 0;
            while (true) {
                int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                out.write(buf, 0, n);
                total += n;
                if (total > epgMaxBytes()) {
                    throw new Exception("EPG-Datei zu groß");
                }
            }
            out.flush();
            try {
                out.close();
            } catch (Exception unused) {
            }
            out = null;
            if (code >= 400 || total < 40) {
                throw new Exception("HTTP " + code);
            }
            if (total < EPG_MIN_BYTES) {
                throw new Exception("EPG zu klein (" + total + " Bytes)");
            }
            // Validate gzip magic or XML/text start so we do not cache HTML error pages.
            // Body stays gzip only for .gz URLs; transport gzip was already unwrapped above.
            validateEpgPart(part, urlGz);
            if (file.exists()) {
                file.delete();
            }
            if (!part.renameTo(file)) {
                throw new Exception("EPG-Cache fehlgeschlagen");
            }
            part = null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception unused) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception unused) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception unused) {
                }
            }
            if (part != null && part.exists()) {
                try {
                    part.delete();
                } catch (Exception unused) {
                }
            }
        }
    }

    private static void validateEpgPart(File part, boolean expectGzip) throws Exception {
        FileInputStream fis = new FileInputStream(part);
        try {
            int b0 = fis.read();
            int b1 = fis.read();
            if (b0 < 0 || b1 < 0) {
                throw new Exception("EPG leer");
            }
            boolean gzipMagic = b0 == 0x1f && b1 == 0x8b;
            if (gzipMagic) {
                return;
            }
            if (expectGzip) {
                throw new Exception("EPG kein gültiges GZIP");
            }
            // Plain XMLTV should start with whitespace/'<' / BOM
            if (b0 == 0xef && b1 == 0xbb) {
                int b2 = fis.read();
                if (b2 == 0xbf) {
                    b0 = fis.read();
                    b1 = fis.read();
                }
            }
            while (b0 == ' ' || b0 == '\t' || b0 == '\n' || b0 == '\r') {
                b0 = b1;
                b1 = fis.read();
            }
            if (b0 != '<') {
                throw new Exception("EPG kein XML/GZIP");
            }
        } finally {
            try {
                fis.close();
            } catch (Exception unused) {
            }
        }
    }

    private void parseFile(File file, boolean z) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("Kein EPG-Cache");
        }
        InputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file), 16384);
        try {
            bufferedInputStream.mark(4);
            int read = bufferedInputStream.read();
            int read2 = bufferedInputStream.read();
            bufferedInputStream.reset();
            if (read == 31 && read2 == 139) {
                bufferedInputStream = new GZIPInputStream(bufferedInputStream);
            }
            parse(bufferedInputStream, z);
        } finally {
            try {
                bufferedInputStream.close();
            } catch (Exception unused) {
            }
        }
    }

    private void parse(InputStream inputStream, boolean z) throws Exception {
        HashMap hashMap;
        String str;
        XmlPullParser newPullParser = Xml.newPullParser();
        int i = 0;
        newPullParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false);
        newPullParser.setInput(inputStream, "UTF-8");
        HashMap hashMap2 = new HashMap();
        HashMap hashMap3 = new HashMap();
        long currentTimeMillis = System.currentTimeMillis();
        long j = currentTimeMillis - parseWindowPastMs();
        long j2 = currentTimeMillis + parseWindowFutureMs();
        final int maxPerChannel = maxProgrammesPerChannel();
        int eventType = newPullParser.getEventType();
        String str2 = null;
        String str3 = null;
        while (eventType != 1) {
            int i2 = 2;
            hashMap = hashMap3;
            str = str3;
            if (eventType != 2) {
                if (eventType == 3 && "channel".equals(newPullParser.getName())) {
                    str3 = null;
                }
            } else {
                String name = newPullParser.getName();
                if ("channel".equals(name)) {
                    hashMap = hashMap3;
                    str3 = newPullParser.getAttributeValue(str2, "id");
                } else {
                    if ("display-name".equals(name) && str3 != null) {
                        indexName(hashMap3, text(newPullParser), norm(str3));
                        indexName(hashMap3, str3, norm(str3));
                        int indexOf = str3.indexOf(64);
                        if (indexOf > 0) {
                            indexName(hashMap3, str3.substring(i, indexOf), norm(str3));
                        }
                    } else if ("programme".equals(name)) {
                        String attributeValue = newPullParser.getAttributeValue(str2, "channel");
                        hashMap = hashMap3;
                        long parseXmltvTime = parseXmltvTime(newPullParser.getAttributeValue(str2, "start"));
                        str = str3;
                        long parseXmltvTime2 = parseXmltvTime(newPullParser.getAttributeValue(str2, "stop"));
                        boolean z2 = attributeValue != null && parseXmltvTime > 0 && parseXmltvTime2 > parseXmltvTime && parseXmltvTime2 >= j && parseXmltvTime <= j2;
                        String str4 = "";
                        int i3 = 1;
                        while (i3 > 0) {
                            int next = newPullParser.next();
                            if (next == i2) {
                                if (z2 && str4.isEmpty() && "title".equals(newPullParser.getName())) {
                                    str4 = text(newPullParser);
                                } else {
                                    i3++;
                                }
                            } else if (next == 3) {
                                i3--;
                            } else if (next == 1) {
                                break;
                            } else {
                                i2 = 2;
                            }
                            i2 = 2;
                        }
                        if (z2 && str4 != null && !str4.isEmpty()) {
                            Listing listing = new Listing();
                            listing.title = str4.trim();
                            listing.start = parseXmltvTime;
                            listing.stop = parseXmltvTime2;
                            String norm = norm(attributeValue);
                            List list = (List) hashMap2.get(norm);
                            if (list == null) {
                                list = new ArrayList();
                                hashMap2.put(norm, list);
                            }
                            if (list.size() < maxPerChannel) {
                                list.add(listing);
                            }
                        }
                        str3 = str;
                    }
                    hashMap = hashMap3;
                    str = str3;
                    str3 = str;
                }
            }
            eventType = newPullParser.next();
            hashMap3 = hashMap;
            i = 0;
            str2 = null;
        }
        HashMap hashMap4 = hashMap3;
        if (!z) {
            this.byId.clear();
            this.nameToId.clear();
            this.byId.putAll(hashMap2);
            this.nameToId.putAll(hashMap4);
        } else {
            mergeProgrammes(hashMap2);
            for (Object raw : hashMap4.entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) raw;
                if (e.getKey() instanceof String && e.getValue() instanceof String) {
                    this.nameToId.putIfAbsent((String) e.getKey(), (String) e.getValue());
                }
            }
        }
        Iterator<List<Listing>> it = this.byId.values().iterator();
        int i4 = 0;
        while (it.hasNext()) {
            i4 += it.next().size();
        }
        this.programmeCount = i4;
        this.channelCount = this.byId.size();
        this.error = null;
        if (App.isLowRam()) {
            trim(false);
        }
    }

    private Models.Epg lookup(String str) {
        if (str == null || str.isEmpty() || "null".equalsIgnoreCase(str)) {
            return null;
        }
        return current(norm(str));
    }

    private Models.Epg current(String str) {
        List<Listing> list = this.byId.get(str);
        if (list == null || list.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Listing airing = null;
        Listing upcoming = null;
        for (Listing listing : list) {
            if (listing == null || listing.start <= 0 || listing.stop <= listing.start) {
                continue;
            }
            // Strict: only the slot that actually covers now is "Jetzt"
            if (listing.start <= now && listing.stop > now) {
                airing = listing;
            } else if (listing.start > now && (upcoming == null || listing.start < upcoming.start)) {
                upcoming = listing;
            }
        }
        // Do NOT fall back to a future (or arbitrary past) programme as current —
        // that produced afternoon "Jetzt:" rows with a bogus full progress bar.
        if (airing == null) {
            return null;
        }
        Models.Epg epg = new Models.Epg();
        epg.title = airing.title;
        epg.start = airing.start;
        epg.end = airing.stop;
        if (upcoming == null) {
            for (Listing listing : list) {
                if (listing != null && listing.start >= airing.stop
                        && (upcoming == null || listing.start < upcoming.start)) {
                    upcoming = listing;
                }
            }
        }
        if (upcoming != null) {
            epg.nextTitle = upcoming.title;
        }
        if (epg.title == null || epg.title.isEmpty()) {
            return null;
        }
        return epg;
    }

    private static String text(XmlPullParser xmlPullParser) throws Exception {
        String nextText = xmlPullParser.nextText();
        return nextText == null ? "" : nextText.trim();
    }

    static long parseXmltvTime(String str) {
        if (str == null) {
            return 0L;
        }
        String trim = str.trim();
        if (trim.length() < 14) {
            return 0L;
        }
        try {
            String digits = trim.substring(0, 14);
            // Offset may be " +0200", "+0200", " +02:00".
            String rest = trim.length() > 14 ? trim.substring(14).trim() : "";
            int sign = 0;
            int offMin = 0;
            boolean hasOffset = false;
            if (!rest.isEmpty() && (rest.charAt(0) == '+' || rest.charAt(0) == '-')) {
                hasOffset = true;
                sign = rest.charAt(0) == '+' ? 1 : -1;
                String num = rest.substring(1).replace(":", "");
                if (num.length() >= 4) {
                    offMin = Integer.parseInt(num.substring(0, 2)) * 60 + Integer.parseInt(num.substring(2, 4));
                } else if (num.length() >= 2) {
                    offMin = Integer.parseInt(num.substring(0, 2)) * 60;
                }
            } else if (trim.length() >= 19 && (trim.charAt(14) == '+' || trim.charAt(14) == '-')) {
                hasOffset = true;
                sign = trim.charAt(14) == '+' ? 1 : -1;
                String num = trim.substring(15).replace(":", "");
                if (num.length() >= 4) {
                    offMin = Integer.parseInt(num.substring(0, 2)) * 60 + Integer.parseInt(num.substring(2, 4));
                }
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            if (!hasOffset) {
                // German IPTV XMLTV often omits TZ; wall-clock is Europe/Berlin, not UTC.
                // Bare-as-UTC shifted slots by +1/+2h and left gaps at "now", so current()
                // fell through to a future afternoon programme as "Jetzt".
                sdf.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
                return sdf.parse(digits).getTime();
            }
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            long asUtc = sdf.parse(digits).getTime();
            return asUtc - ((long) sign) * offMin * 60000L;
        } catch (Exception unused) {
            return 0L;
        }
    }

    static String norm(String str) {
        return str == null ? "" : str.trim().toLowerCase(Locale.US);
    }

    static String normName(String str) {
        if (str == null) {
            return "";
        }
        String s = str.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("sat.1", "sat1").replace("sat 1", "sat1")
                .replace("3sat", "3sat").replace("3 sat", "3sat")
                .replace("proSieben", "prosieben").replace("pro sieben", "prosieben")
                .replace("pro7", "prosieben").replace("pro 7", "prosieben")
                .replace("kabel1", "kabel eins").replace("kabel 1", "kabel eins")
                .replace("rtl ii", "rtlzwei").replace("rtl 2", "rtlzwei").replace("rtl2", "rtlzwei")
                .replace("rtl nitro", "nitro")
                .replace("n-tv", "ntv").replace("n tv", "ntv")
                .replaceAll("\\[.*?\\]", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\s*\\.[bcsf]\\b", " ")
                .replaceFirst("^de:\\s*", "")
                .replaceFirst("^\\[+\\s*", "")
                .replaceFirst("^vip\\s+", "")
                .replaceAll("\\b(full\\s*hd|fullhd|ultra\\s*hd|ultrahd|fhd|uhd|hd\\+|hdtv|sd|4k|8k|hevc|h265|h264|raw|hq|backup|germany|deutschland|deutsch|german|austria|osterr?eich|universal|fernsehen|vip)\\b", " ")
                .replaceAll("(?<!\\w)hd(?!\\w)", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+[bcsf]$", "")
                .replaceAll("\\s+", " ");
        // After punctuation wipe, re-apply compact brand maps (Vavoo often ships Kabel1/RTL2/n-tv)
        s = s.replace("kabel1", "kabel eins")
                .replace("kabeleins", "kabel eins")
                .replace("rtl2", "rtlzwei")
                .replace("rtl zwei", "rtlzwei")
                .replace("n tv", "ntv")
                .replace("zdf neo", "zdfneo")
                .replace("zdf info", "zdfinfo")
                .replace("sat 1", "sat1");
        return s;
    }

    private static String[] aliases(String str) {
        if (str == null) {
            return new String[0];
        }
        if ("rtl 2".equals(str) || "rtl ii".equals(str) || "rtlzwei".equals(str) || "rtl2".equals(str)) {
            return new String[]{"rtl 2", "rtlzwei", "rtl2", "rtl ii"};
        }
        if ("kabel eins".equals(str) || "kabeleins".equals(str) || "kabel 1".equals(str) || "kabel1".equals(str)) {
            return new String[]{"kabel eins", "kabeleins", "kabel 1", "kabel1"};
        }
        if ("sat1".equals(str) || "sat 1".equals(str)) {
            return new String[]{"sat1", "sat 1"};
        }
        if ("3sat".equals(str) || "3 sat".equals(str)) {
            return new String[]{"3sat", "3 sat"};
        }
        if ("prosieben".equals(str) || "pro sieben".equals(str) || "pro7".equals(str) || "pro 7".equals(str)) {
            return new String[]{"prosieben", "pro sieben", "pro7", "pro 7"};
        }
        if ("prosieben maxx".equals(str) || "pro7 maxx".equals(str) || "pro 7 maxx".equals(str)) {
            return new String[]{"prosieben maxx", "pro7 maxx", "pro 7 maxx"};
        }
        if ("das erste".equals(str) || "ard".equals(str) || "ard das erste".equals(str)) {
            return new String[]{"das erste", "ard", "ard das erste"};
        }
        if ("13th street".equals(str) || "13th street universal".equals(str)) {
            return new String[]{"13th street", "13th street universal"};
        }
        if ("123 tv".equals(str) || "1 2 3 tv".equals(str) || "123tv".equals(str)) {
            return new String[]{"123 tv", "1 2 3 tv", "123tv"};
        }
        if ("nick".equals(str) || "nickelodeon".equals(str)) {
            return new String[]{"nick", "nickelodeon"};
        }
        if ("disney channel".equals(str) || "disney".equals(str)) {
            return new String[]{"disney channel", "disney"};
        }
        if ("rtl up".equals(str) || "rtlup".equals(str)) {
            return new String[]{"rtlup", "rtl up"};
        }
        if ("vox up".equals(str) || "voxup".equals(str)) {
            return new String[]{"voxup", "vox up"};
        }
        if ("nitro".equals(str) || "rtl nitro".equals(str)) {
            return new String[]{"nitro", "rtl nitro"};
        }
        if ("zdf neo".equals(str) || "zdfneo".equals(str)) {
            return new String[]{"zdf neo", "zdfneo"};
        }
        if ("zdf info".equals(str) || "zdfinfo".equals(str)) {
            return new String[]{"zdf info", "zdfinfo"};
        }
        if ("swr".equals(str) || "swr sr".equals(str) || "sr".equals(str)) {
            return new String[]{"swr", "swr sr", "swr/sr"};
        }
        if ("wdr".equals(str) || "wdr koeln".equals(str) || "wdr koln".equals(str) || "wdr fernsehen".equals(str)) {
            return new String[]{"wdr", "wdr koeln", "wdr koln"};
        }
        if ("ndr".equals(str) || str.startsWith("ndr ")) {
            return new String[]{"ndr", "ndr fs hh", "ndr fernsehen"};
        }
        if ("mdr".equals(str) || str.startsWith("mdr ")) {
            return new String[]{"mdr", "mdr sachsen", "mdr fernsehen"};
        }
        if ("br".equals(str) || "br fernsehen".equals(str) || str.startsWith("br ")) {
            return new String[]{"br", "br fernsehen"};
        }
        if ("hr".equals(str) || "hr fernsehen".equals(str) || "hessischer rundfunk".equals(str)) {
            return new String[]{"hr", "hr fernsehen"};
        }
        if ("rbb".equals(str) || str.startsWith("rbb ")) {
            return new String[]{"rbb", "rbb berlin", "rbb brandenburg"};
        }
        if ("ntv".equals(str) || "n tv".equals(str)) {
            return new String[]{"ntv", "n tv", "n-tv"};
        }
        if ("sport1".equals(str) || "sport 1".equals(str)) {
            return new String[]{"sport1", "sport 1"};
        }
        if ("welt".equals(str) || "n24".equals(str)) {
            return new String[]{"welt", "n24"};
        }
        return new String[0];
    }
}
