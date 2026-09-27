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
    /** Name index per origin (3.84): provider XMLTV vs. EPG aus dem Netz. */
    private final ConcurrentHashMap<String, String> providerNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> webNames = new ConcurrentHashMap<>();
    private final java.util.Set<String> webIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> providerIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** EpgSources mode: auto / provider / web. */
    public volatile String sourceMode = EpgSources.AUTO;
    /** Origin of the file currently being parsed (EpgRefresh runs on one IO thread). */
    private volatile boolean parsingWeb;

    /** Renamed / differently named channels: normalized name → EPG name. */
    private static final Map<String, String> RENAMES = new HashMap<>();
    static {
        RENAMES.put("tnt comedy", "warner tv comedy");
        RENAMES.put("tnt film", "warner tv film");
        RENAMES.put("tnt serie", "warner tv serie");
        RENAMES.put("tnt series", "warner tv serie");
        RENAMES.put("warner comedy", "warner tv comedy");
        RENAMES.put("warner film", "warner tv film");
        RENAMES.put("warner serie", "warner tv serie");
        RENAMES.put("sky 1", "sky one");
        RENAMES.put("sky family", "sky cinema family");
        RENAMES.put("sky thriller", "sky cinema thriller");
        RENAMES.put("sky special", "sky cinema special");
        RENAMES.put("sky best of", "sky cinema best of");
        RENAMES.put("sky action", "sky cinema action");
        RENAMES.put("sky premieren 24", "sky cinema premieren 24");
        RENAMES.put("sky premieren", "sky cinema premieren");
        RENAMES.put("sky formula 1", "sky sport f1");
        RENAMES.put("sky formel 1", "sky sport f1");
        RENAMES.put("sony axn", "axn");
        RENAMES.put("n24 docu", "n24 doku");
        RENAMES.put("qvc zwei", "qvc2");
        RENAMES.put("qvc 2", "qvc2");
        RENAMES.put("hse 24", "hse");
        RENAMES.put("hse24", "hse");
        RENAMES.put("hse 24 tv", "hse");
        RENAMES.put("hse 24 extra", "hse extra");
        RENAMES.put("hse24 extra", "hse extra");
        RENAMES.put("hse 24 trend", "hse trend");
        RENAMES.put("sport digital", "sportdigital fussball");
        RENAMES.put("sportdigital", "sportdigital fussball");
        RENAMES.put("sportdigital fusball", "sportdigital fussball");
        RENAMES.put("rtl plus", "rtlup");
        RENAMES.put("atv 1", "atv");
        RENAMES.put("atv 2", "atv2");
        RENAMES.put("wetter com tv", "wetter tv");
        RENAMES.put("geo", "geo television");
        RENAMES.put("sr", "sr fernsehen");
        RENAMES.put("history", "the history channel");
        RENAMES.put("history channel", "the history channel");
        RENAMES.put("kinowelt tv", "kinowelt");
        RENAMES.put("puls 8", "puls acht");
        RENAMES.put("orf iii", "orf 3");
        RENAMES.put("orf3", "orf 3");
        RENAMES.put("srf 2", "srf zwei");
        RENAMES.put("srf2", "srf zwei");
        RENAMES.put("srf1", "srf 1");
    }

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
        this.providerNames.clear();
        this.webNames.clear();
        this.webIds.clear();
        this.providerIds.clear();
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
            Quiet.ignored("EpgGuide", ignored);
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
        final long shift = timeshiftMs(channel.name);
        String exactNameId = nameIdFor(channel, false);
        if (exactNameId != null) {
            Models.Epg byName = current(exactNameId, shift);
            if (byName != null) {
                if (shift == 0) channel.epgChannelId = exactNameId;
                return byName;
            }
        }
        if (shift != 0) return null;
        Models.Epg lookup3 = allowedLookup(channel, channel.epgChannelId);
        if (lookup3 != null) {
            return lookup3;
        }
        if (channel.epgChannelId != null && channel.epgChannelId.contains("@") && (lookup2 = allowedLookup(channel, channel.epgChannelId.substring(0, channel.epgChannelId.indexOf(64)))) != null) {
            return lookup2;
        }
        Models.Epg lookup4 = allowedLookup(channel, channel.id);
        if (lookup4 != null) {
            return lookup4;
        }
        if (channel.id != null && channel.id.startsWith("iptv:") && (lookup = allowedLookup(channel, channel.id.substring(5))) != null) {
            return lookup;
        }
        // Fuzzy name only as last resort off the UI thread (player seed).
        if (allowFuzzy && exactNameId == null) {
            String fuzzyId = findNameIdIn(namesFor(channel), normName(channel.name), true);
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
        return nameIdFor(channel, false) != null;
    }

    /** Name index that may feed this channel (built-in section: always web EPG). */
    private Map<String, String> namesFor(Models.Channel channel) {
        boolean builtin = EpgSources.isBuiltin(channel);
        String mode = EpgSources.normalize(this.sourceMode);
        if (builtin || EpgSources.WEB.equals(mode)) return this.webNames;
        if (EpgSources.PROVIDER.equals(mode)) return this.providerNames;
        return this.nameToId;
    }

    private String nameIdFor(Models.Channel channel, boolean allowFuzzy) {
        if (channel == null) return null;
        return findNameIdIn(namesFor(channel), normName(channel.name), allowFuzzy);
    }

    /** Is guide data under this XMLTV id allowed for the channel by the EPG source setting? */
    boolean idAllowed(Models.Channel channel, String id) {
        if (id == null) return false;
        boolean builtin = EpgSources.isBuiltin(channel);
        boolean web = this.webIds.contains(id);
        boolean provider = !web || this.providerIds.contains(id);
        return (web && EpgSources.allowWeb(this.sourceMode, builtin))
                || (provider && EpgSources.allowProvider(this.sourceMode, builtin));
    }

    private Models.Epg allowedLookup(Models.Channel channel, String id) {
        if (id == null || id.isEmpty() || "null".equalsIgnoreCase(id)) return null;
        String key = norm(id);
        if (!idAllowed(channel, key)) return null;
        return current(key, 0L);
    }

    /** "RTL +1" style time-shift channels: same programme one (or two) hours later. */
    static long timeshiftMs(String name) {
        if (name == null) return 0L;
        java.util.regex.Matcher m = TIMESHIFT.matcher(name);
        if (!m.find()) return 0L;
        return Integer.parseInt(m.group(1)) * 3600000L;
    }

    private static final java.util.regex.Pattern TIMESHIFT =
            java.util.regex.Pattern.compile("(?:^|\\s)\\+\\s?([12])(?![0-9])");

    public List<Listing> listingsFor(Models.Channel channel) {
        List<Listing> list;
        ArrayList arrayList = new ArrayList();
        if (channel == null) {
            return arrayList;
        }
        String keyOf = keyOf(channel);
        long shift = timeshiftMs(channel.name);
        if (keyOf != null && (list = this.byId.get(keyOf)) != null) {
            if (shift == 0) {
                arrayList.addAll(list);
            } else {
                for (Listing l : list) {
                    if (l == null) continue;
                    Listing c = new Listing();
                    c.title = l.title;
                    c.desc = l.desc;
                    c.start = l.start + shift;
                    c.stop = l.stop + shift;
                    arrayList.add(c);
                }
            }
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
        this.providerIds.add(norm);
        if (channel.name != null && !EpgSources.isBuiltin(channel)) {
            this.nameToId.put(normName(channel.name), norm);
            this.providerNames.put(normName(channel.name), norm);
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
        String nameId = nameIdFor(channel, false);
        if (nameId != null && this.byId.containsKey(nameId)) {
            return nameId;
        }
        if (channel.epgChannelId != null && !channel.epgChannelId.isEmpty()) {
            String norm = norm(channel.epgChannelId);
            if (this.byId.containsKey(norm) && idAllowed(channel, norm)) {
                return norm;
            }
            int indexOf = norm.indexOf(64);
            if (indexOf > 0 && this.byId.containsKey(norm.substring(0, indexOf)) && idAllowed(channel, norm.substring(0, indexOf))) {
                return norm.substring(0, indexOf);
            }
        }
        return (channel.id == null || !this.byId.containsKey(norm(channel.id)) || !idAllowed(channel, norm(channel.id))) ? nameId : norm(channel.id);
    }

    private String findNameId(String str) {
        return findNameIdIn(this.nameToId, str, true);
    }

    private String findNameId(String str, boolean allowFuzzy) {
        return findNameIdIn(this.nameToId, str, allowFuzzy);
    }

    /**
     * Resolve XMLTV id from a normalized display name.
     * @param allowFuzzy when false, skip the O(nameToId) contains scan — required for
     *                   bulk apply()/askEpg on the UI thread after large internet XMLTV loads.
     */
    private String findNameIdIn(Map<String, String> names, String str, boolean allowFuzzy) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        String hit = lookupNameKey(names, str);
        if (hit != null) {
            return hit;
        }
        String renamed = RENAMES.get(str);
        if (renamed != null) {
            hit = lookupNameKey(names, renamed);
            if (hit == null) hit = lookupNameKey(names, renamed.replace(" ", ""));
            if (hit != null) {
                return hit;
            }
        }
        String[] top = DE_TOP.get(str);
        if (top != null) {
            for (String alias : top) {
                hit = lookupNameKey(names, alias);
                if (hit != null) {
                    return hit;
                }
                hit = lookupNameKey(names, alias.replace(" ", ""));
                if (hit != null) {
                    return hit;
                }
            }
        }
        String compact = str.replace(" ", "");
        hit = lookupNameKey(names, compact);
        if (hit != null) {
            return hit;
        }
        top = DE_TOP.get(compact);
        if (top != null) {
            for (String alias : top) {
                hit = lookupNameKey(names, alias);
                if (hit != null) {
                    return hit;
                }
            }
        }
        for (String alias : aliases(str)) {
            hit = lookupNameKey(names, alias);
            if (hit != null) {
                return hit;
            }
            hit = lookupNameKey(names, alias.replace(" ", ""));
            if (hit != null) {
                return hit;
            }
        }
        // Drop common IPTV prefixes/suffixes and retry (e.g. "ard das erste", "ndr fs hh", "rtl deutschland")
        String stripped = str.replaceAll("\\b(ard|das|fs|fernsehen|deutschland|austria|osterr?eich|sat|backup|koeln|koln|hh|hamburg|sachsen|bw|baden|wuerttemberg|berlin|brandenburg|vip)\\b", " ")
                .trim().replaceAll("\\s+", " ");
        if (!stripped.isEmpty() && !stripped.equals(str)) {
            hit = findNameIdIn(names, stripped, allowFuzzy);
            if (hit != null) {
                return hit;
            }
        }
        String[] split = str.split(" ");
        if (split.length >= 2) {
            hit = lookupNameKey(names, split[0] + " " + split[1]);
            if (hit != null) {
                return hit;
            }
            // Only known regional bases: "mdr sachsen" → mdr. Never "rtl crime" → rtl.
            if (isRegionalBase(split[0])) {
                hit = lookupNameKey(names, split[0]);
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
        for (Map.Entry<String, String> e : names.entrySet()) {
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

    private String lookupNameKey(Map<String, String> names, String key) {
        if (key == null || key.isEmpty() || names == null) {
            return null;
        }
        return names.get(key);
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
                    if (forChannel != null || !EpgTime.isCurrent(channel.epg, System.currentTimeMillis())) channel.epg = forChannel;
                    if (channel.epg != null) i++;
                } catch (Exception unused) {
                    Quiet.ignored("EpgGuide", unused);
                }
            }
            int unified = applyUnified(list);
            if (unified > i) {
                i = unified;
            }
        } catch (Exception unused2) {
            Quiet.ignored("EpgGuide", unused2);
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
                String base = normName(channel.name);
                if (base.isEmpty()) {
                    continue;
                }
                // Built-in section and time-shift channels never share a guide with others.
                String key = (EpgSources.isBuiltin(channel) ? "b" : "u") + timeshiftMs(channel.name) + "|" + base;
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
                Models.Channel rep = g.get(0);
                long shift = timeshiftMs(rep.name);
                String sharedId = nameIdFor(rep, false);
                if (sharedId != null) {
                    best = current(sharedId, shift);
                }
                if (best == null) {
                    for (Models.Channel c : g) {
                        if (EpgTime.isCurrent(c.epg, System.currentTimeMillis())) {
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
                    if (sharedId != null && !sharedId.isEmpty() && shift == 0) {
                        c.epgChannelId = sharedId;
                    }
                    matched++;
                }
            }
        } catch (Exception unused) {
            Quiet.ignored("EpgGuide", unused);
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
            final boolean seedBuiltin = EpgSources.isBuiltin(seed);
            final long seedShift = timeshiftMs(seed.name);
            String sharedId = nameIdFor(seed, false);
            if (sharedId != null) {
                Models.Epg byName = current(sharedId, seedShift);
                if (byName != null) {
                    best = byName;
                }
            }
            if (!EpgTime.isCurrent(best, System.currentTimeMillis())) {
                return 0;
            }
            for (Models.Channel c : new ArrayList<>(list)) {
                if (c == null || c.header || c.name == null) {
                    continue;
                }
                if (!key.equals(normName(c.name)) || EpgSources.isBuiltin(c) != seedBuiltin
                        || timeshiftMs(c.name) != seedShift) {
                    continue;
                }
                c.epg = best;
                if (sharedId != null && !sharedId.isEmpty() && seedShift == 0) {
                    c.epgChannelId = sharedId;
                }
                matched++;
            }
        } catch (Exception unused) {
            Quiet.ignored("EpgGuide", unused);
        }
        return matched;
    }

    public void loadUrl(String str, File file, boolean z) throws Exception {
        loadUrl(str, file, z, false);
    }

    public void loadUrlMerge(String str, File file, boolean z) throws Exception {
        loadUrl(str, file, z, true);
    }

    /** Merge with origin: web = EPG aus dem Netz, otherwise provider EPG. */
    public void loadUrlMerge(String str, File file, boolean z, boolean web) throws Exception {
        this.parsingWeb = web;
        try {
            loadUrl(str, file, z, true);
        } finally {
            this.parsingWeb = false;
        }
    }

    public void loadFileMerge(File file, boolean web) throws Exception {
        this.parsingWeb = web;
        try {
            parseFile(file, true);
        } finally {
            this.parsingWeb = false;
        }
    }

    private void loadUrl(String str, File file, boolean z, boolean z2) throws Exception {
        if (str == null || str.trim().isEmpty()) {
            throw new Exception("Keine EPG-URL");
        }
        String trim = str.trim();
        if (file != null && file.exists() && file.length() > 100663296) {
            file.delete();
        }
        if (!z && file != null && file.exists() && file.length() > 0) {
            boolean parsed = false;
            try {
                parseFile(file, z2);
                parsed = true;
            } catch (Exception unused) {
                try {
                    file.delete();
                } catch (Exception unused2) {
                    Quiet.ignored("EpgGuide", unused2);
                }
            }
            if (!z2 && this.channelCount > 0 && this.programmeCount > 0) {
                return;
            }
            // Successful parsing also counts when all programmes were already present.
            if (z2 && parsed) {
                return;
            }
        }
        download(trim, file);
        if (!z2) parseFile(file, false);
        if (this.channelCount == 0 || this.programmeCount == 0) {
            throw new Exception("XMLTV ohne Programme");
        }
    }

    public void loadFile(File file) throws Exception {
        parseFile(file, false);
    }

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
            conn.setReadTimeout(25000);
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
                    Quiet.ignored("EpgGuide", unused);
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
            long deadline = android.os.SystemClock.elapsedRealtime() + 120000;
            while (true) {
                if (Thread.currentThread().isInterrupted() || android.os.SystemClock.elapsedRealtime() > deadline) throw new java.io.IOException("EPG-Zeitlimit");
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
                Quiet.ignored("EpgGuide", unused);
            }
            out = null;
            if (code >= 400 || total < 40) {
                throw new Exception("HTTP " + code);
            }
            // Validate gzip magic or XML/text start so we do not cache HTML error pages.
            // Body stays gzip only for .gz URLs; transport gzip was already unwrapped above.
            validateEpgPart(part, urlGz);
            parseFile(part, true);
            if (!part.renameTo(file)) {
                throw new Exception("EPG-Cache fehlgeschlagen");
            }
            part = null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception unused) {
                    Quiet.ignored("EpgGuide", unused);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception unused) {
                    Quiet.ignored("EpgGuide", unused);
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception unused) {
                    Quiet.ignored("EpgGuide", unused);
                }
            }
            if (part != null && part.exists()) {
                try {
                    part.delete();
                } catch (Exception unused) {
                    Quiet.ignored("EpgGuide", unused);
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
                Quiet.ignored("EpgGuide", unused);
            }
        }
    }

    public void loadFileMerge(File file) throws Exception { parseFile(file, true); }

    static InputStream skipBom(InputStream in) throws java.io.IOException {
        InputStream b = in.markSupported() ? in : new BufferedInputStream(in, 16384);
        b.mark(3);
        if (b.read() == 0xEF && b.read() == 0xBB && b.read() == 0xBF) return b;
        b.reset();
        return b;
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
            // Several web XMLTV files start with a UTF-8 BOM; some pull parsers reject
            // "<?xml" after it ("PI must not start with xml") and the whole file was lost.
            bufferedInputStream = skipBom(bufferedInputStream);
            parse(bufferedInputStream, z);
        } finally {
            try {
                bufferedInputStream.close();
            } catch (Exception unused) {
                Quiet.ignored("EpgGuide", unused);
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
        if (hashMap2.isEmpty()) throw new Exception("XMLTV ohne Programme im Zeitfenster");
        boolean web = this.parsingWeb;
        java.util.Set<String> ids = web ? this.webIds : this.providerIds;
        Map<String, String> originNames = web ? this.webNames : this.providerNames;
        if (!z) {
            this.providerNames.clear();
            this.webNames.clear();
            this.webIds.clear();
            this.providerIds.clear();
        }
        for (Object k : hashMap2.keySet()) {
            if (k instanceof String) ids.add((String) k);
        }
        for (Object raw : hashMap4.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) raw;
            if (e.getKey() instanceof String && e.getValue() instanceof String) {
                originNames.putIfAbsent((String) e.getKey(), (String) e.getValue());
            }
        }
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
        return current(norm(str), 0L);
    }

    private Models.Epg current(String str) {
        return current(str, 0L);
    }

    /**
     * Programme airing now (+ next) for an XMLTV id. {@code shift} moves all slots
     * (time-shift channels). With overlapping slots from merged feeds the latest-starting
     * airing slot wins, and "next" is the first slot starting at/after it ends.
     */
    private Models.Epg current(String str, long shift) {
        List<Listing> list = str == null ? null : this.byId.get(str);
        if (list == null || list.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis() - shift;
        Listing airing = null;
        for (Listing listing : list) {
            if (listing == null || listing.start <= 0 || listing.stop <= listing.start) {
                continue;
            }
            // Strict: only a slot that actually covers now is "Jetzt"
            if (listing.start <= now && listing.stop > now
                    && (airing == null || listing.start > airing.start)) {
                airing = listing;
            }
        }
        // Do NOT fall back to a future (or arbitrary past) programme as current —
        // that produced afternoon "Jetzt:" rows with a bogus full progress bar.
        if (airing == null || airing.title == null || airing.title.isEmpty()) {
            return null;
        }
        Listing upcoming = null;
        for (Listing listing : list) {
            if (listing == null || listing == airing || listing.stop <= listing.start) continue;
            if (listing.start >= airing.stop - 60000L && listing.start > now
                    && (upcoming == null || listing.start < upcoming.start)) {
                upcoming = listing;
            }
        }
        Models.Epg epg = new Models.Epg();
        epg.title = airing.title;
        epg.start = airing.start + shift;
        epg.end = airing.stop + shift;
        if (upcoming != null) {
            epg.nextTitle = upcoming.title;
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
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        // Other accents (é, è, ô …) → base letter.
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s
                // Country prefixes of IPTV lists: "DE: ", "DE | ", "|DE| ".
                .replaceFirst("^\\|?\\s*(de|at|ch|ger|deu)\\s*[|:]\\s*", "")
                // Time-shift marker (see timeshiftMs), "sport1+" / "hd+" stay untouched.
                .replaceAll("(?:^|\\s)\\+\\s?[12](?![0-9])", " ")
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
                .replaceAll("\\b(full\\s*hd|fullhd|ultra\\s*hd|ultrahd|fhd|uhd|hd\\+|hdtv|sd|4k|8k|hevc|h265|h264|raw|hq|backup|germany|deutschland|deutsch|german|austria|osterr?eich|oesterreich|schweiz|universal|fernsehen|vip)\\b", " ")
                .replaceAll("(?<!\\w)hd(?!\\w)", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+[bcsf]$", "")
                .replaceAll("\\s+(de|ger|deu)$", "")
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
