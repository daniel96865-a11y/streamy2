package app.streamy2;

import android.text.Html;
import app.streamy2.Models;
import com.google.common.net.HttpHeaders;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.text.Typography;

/* loaded from: classes.dex */
final class ExtraMediaSource {
    static final String CAT = "extra_media";
    private static String base;
    static final List<Models.Media> films;
    static volatile boolean loaded;
    static volatile boolean loading;
    static volatile String lastError = "";
    private static volatile long lastLoadedAt;
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
    static final List<Models.Media> serials;
    private static long tokenAt;
    private static String[] BASES = {"https://megakino21.com", "https://megakino20.com", "https://megakino19.com", "https://megakino18.com", "https://megakino15.com", "https://megakino14.com", "https://megakino12.com", "https://megakino5.org", "https://megakino4.com", "https://megakino2.com", "https://megakino1.com"};
    private static final Object HOST = new Object();
    private static List<Models.Media> groupedSerials;
    private static final Pattern IFRAME = Pattern.compile("<iframe[^>]+(?:data-src|src)=\"([^\"]+)\"", 2);
    private static final Pattern OPTION = Pattern.compile("<option[^>]+value=\"([^\"]+)\"[^>]*>([^<]*)", 2);
    private static final Pattern SELECT_ID = Pattern.compile("<select[^>]*id=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</select>", 2);
    private static final Pattern SE_SELECT = Pattern.compile("<select[^>]*class=\"[^\"]*se-select[^\"]*\"[^>]*>([\\s\\S]*?)</select>", 2);
    private static final Pattern DESC = Pattern.compile("itemprop=\"description\"[^>]*>([\\s\\S]*?)</div>", 2);
    private static final Pattern PAGE_TEXT = Pattern.compile("class=\"[^\"]*page__text[^\"]*\"[^>]*>([\\s\\S]*?)</div>", 2);
    private static final Pattern YEAR = Pattern.compile("itemprop=\"dateCreated\"[^>]*>([^<]+)", 2);
    private static final Pattern TITLE = Pattern.compile("<h1[^>]*itemprop=\"name\"[^>]*>([^<]+)", 2);
    private static final Pattern GENRE = Pattern.compile("itemprop=\"genre\"[^>]*>([^<]+)", 2);
    private static final Pattern STAFFEL = Pattern.compile("(?:Staffel\\s*(\\d+)|-\\s*(\\d+)\\s*Staffel)", 2);
    private static final Pattern GENERIC_EP = Pattern.compile("(?i)^\\s*(?:folge|episode|ep\\.?|e)\\s*(\\d+)\\s*$");
    private static final Pattern SEASON_SUFFIX = Pattern.compile("(?i)staffel\\s*\\d+|\\d+\\s*staffel");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"", 2);
    private static final Pattern DATA_SRC = Pattern.compile("data-src=\"([^\"]+)\"", 2);
    private static final Pattern POSTER_TITLE = Pattern.compile("poster__title[^>]*>\\s*([^<]+)", 2);
    private static final Pattern POSTER_TEXT = Pattern.compile("poster__text[^>]*>\\s*([^<]+)", 2);
    private static final Pattern POSTER_SUB = Pattern.compile("<li>([^<]+)</li>", 2);
    private static final Map<String, String> cookies = new LinkedHashMap();

    ExtraMediaSource() {
    }

    static boolean isRestrictedUrl(String url) {
        return url != null && url.toLowerCase(Locale.US).contains("megakino");
    }

    static {
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cookieManager);
        } catch (Exception unused) {
            Quiet.ignored("ExtraMediaSource", unused);
        }
        films = new ArrayList();
        serials = new ArrayList();
    }

    static boolean owns(Models.Media media) {
        return (media == null || media.id == null || !media.id.startsWith("mk:")) ? false : true;
    }


    static void refreshHosts() {
        String[] seeds = new String[]{"https://megakino21.com", "https://megakino20.com", "https://megakino19.com", "https://megakino18.com", "https://megakino15.com", "https://megakino14.com", "https://megakino12.com", "https://megakino5.org", "https://megakino4.com", "https://megakino2.com"};
        java.util.LinkedHashSet<String> live = new java.util.LinkedHashSet<>();
        for (String seed : seeds) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(seed + "/").openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0");
                int code = c.getResponseCode();
                String loc = c.getHeaderField(HttpHeaders.LOCATION);
                absorbCookies(c);
                c.disconnect();
                if (loc != null && !loc.isEmpty()) {
                    live.add(origin(loc.startsWith("http") ? loc : (seed + loc)));
                } else if (code > 0 && code < 500) {
                    live.add(origin(seed));
                }
            } catch (Exception unused) {
                Quiet.ignored("ExtraMediaSource", unused);
            }
        }
        if (!live.isEmpty()) {
            String preferred = live.iterator().next();
            for (String b : BASES) {
                live.add(b);
            }
            BASES = live.toArray(new String[0]);
            // Preferred live Media Extra host for scraping only (do not touch browser HOME)
            base = preferred;
        }
    }

    static String base() {
        String cached = base;
        if (cached != null) {
            return cached;
        }
        synchronized (HOST) {
            if (base != null) {
                return base;
            }
            try { refreshHosts(); } catch (Throwable ignored) { Quiet.ignored("ExtraMediaSource", ignored); }
            String[] strArr = BASES;
            int length = strArr.length;
            for (int i = 0; i < length; i++) {
                String str2 = strArr[i];
                try {
                    req(str2 + "/index.php?yg=token", null);
                    String req = req(str2 + "/", null);
                    if (req != null && req.contains("poster grid-item")) {
                        tokenAt = System.currentTimeMillis();
                        lastError = "";
                        if (base == null) {
                            base = origin(str2);
                        }
                        return base;
                    }
                } catch (Exception e) {
                    lastError = "Media Extra-Host fehlgeschlagen: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
            lastError = "Kein funktionierender Media Extra-Host gefunden (Token/Seite)";
            String str3 = BASES[0];
            base = str3;
            return str3;
        }
    }

    static void ensureToken() {
        if (System.currentTimeMillis() - tokenAt >= 480000 || base == null) {
            String host = base();
            String tokenResp = req(host + "/index.php?yg=token", null);
            // token endpoint often returns 204; cookies in jar are what matter
            if (cookies.isEmpty() && tokenResp == null) {
                lastError = "Media Extra-Token fehlgeschlagen — Cookies fehlen. Später erneut versuchen.";
            } else {
                tokenAt = System.currentTimeMillis();
                if (lastError != null && lastError.startsWith("Media Extra-Token")) {
                    lastError = "";
                }
            }
        }
    }

    static boolean shouldRefresh() {
        if (!loaded || isCatalogEmpty()) return true;
        long loadedAt = lastLoadedAt;
        return loadedAt <= 0L || System.currentTimeMillis() - loadedAt >= CACHE_TTL_MS;
    }

    static void clearCatalogCache() {
        synchronized (ExtraMediaSource.class) {
            films.clear();
            serials.clear();
            groupedSerials = null;
            loaded = false;
            lastLoadedAt = 0L;
        }
    }

    static void invalidateRefreshSession() {
        synchronized (HOST) {
            base = null;
            tokenAt = 0L;
            cookies.clear();
        }
        synchronized (ExtraMediaSource.class) {
            lastLoadedAt = 0L;
        }
        lastError = "";
    }

    static void load() {
        loading = true;
        final boolean hadExisting = !isCatalogEmpty();
        try {
            ensureToken();
            String base2 = base();
            List<Models.Media> parseList = parseList(req(base2 + "/films/", null), false);
            for (int i = 2; i <= 5; i++) {
                for (Models.Media media : parseList(req(base2 + "/films/page/" + i + "/", null), false)) {
                    if (!contains(parseList, media.id)) {
                        parseList.add(media);
                    }
                }
            }
            for (Models.Media media2 : parseList(req(base2 + "/kinofilme/", null), false)) {
                if (!contains(parseList, media2.id)) {
                    parseList.add(media2);
                }
            }
            for (Models.Media media3 : parseList(req(base2 + "/", null), false)) {
                if (!contains(parseList, media3.id) && !media3.series) {
                    parseList.add(media3);
                }
            }
            // Newest uploads first. Previously /kinofilme/ entries were prepended one by one
            // (reversing them), so the same old cinema titles always sat on top and new
            // releases from /films/ page 1 were pushed below them.
            sortNewestFirst(parseList);
            List<Models.Media> parseList2 = parseList(req(base2 + "/serials/", null), true);
            for (int i2 = 2; i2 <= 8; i2++) {
                for (Models.Media media4 : parseList(req(base2 + "/serials/page/" + i2 + "/", null), true)) {
                    if (!contains(parseList2, media4.id)) {
                        parseList2.add(media4);
                    }
                }
            }
            parseList2.sort(new Comparator<Models.Media>() {
                @Override
                public int compare(Models.Media a, Models.Media b) {
                    int byShow = showTitle(a == null ? null : a.name)
                            .toLowerCase(Locale.GERMAN)
                            .compareTo(showTitle(b == null ? null : b.name).toLowerCase(Locale.GERMAN));
                    if (byShow != 0) {
                        return byShow;
                    }
                    return Integer.compare(seasonOf(a == null ? null : a.name), seasonOf(b == null ? null : b.name));
                }
            });
            boolean freshEmpty = parseList.isEmpty() && parseList2.isEmpty();
            boolean keepExisting = freshEmpty && hadExisting;
            synchronized (ExtraMediaSource.class) {
                if (!keepExisting) {
                    List<Models.Media> list = films;
                    list.clear();
                    list.addAll(parseList);
                    List<Models.Media> list2 = serials;
                    list2.clear();
                    list2.addAll(parseList2);
                    groupedSerials = null;
                    loaded = true;
                }
                lastLoadedAt = keepExisting ? 0L : System.currentTimeMillis();
                if (keepExisting) {
                    lastError = "Aktualisierung lieferte keine frischen Daten — vorhandener Katalog bleibt sichtbar und wird erneut geprüft.";
                } else if (films.isEmpty() && serials.isEmpty()) {
                    if (lastError == null || lastError.isEmpty()) {
                        lastError = "Media Extra-Katalog leer. Später erneut versuchen.";
                    }
                } else {
                    lastError = "";
                }
            }
            if (!keepExisting) {
                serialsGrouped();
            }
        } catch (Throwable th) {
            lastLoadedAt = 0L;
            lastError = "Media Extra-Aktualisierung fehlgeschlagen: " + (th.getMessage() != null ? th.getMessage() : th.getClass().getSimpleName());
        } finally {
            loading = false;
        }
    }

    private static final Pattern POST_ID = Pattern.compile("/(\\d+)-[^/]*\\.html");

    static long postId(Models.Media media) {
        String url = media == null ? null : (media.streamUrl != null ? media.streamUrl : media.id);
        if (url == null) {
            return -1L;
        }
        Matcher m = POST_ID.matcher(url);
        if (!m.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    static void sortNewestFirst(List<Models.Media> list) {
        // Stable sort: entries without a numeric post id keep their relative (source) order at the end.
        list.sort(new Comparator<Models.Media>() {
            @Override
            public int compare(Models.Media a, Models.Media b) {
                return Long.compare(postId(b), postId(a));
            }
        });
    }

    /** Local title filter: trimmed, case-insensitive "contains"; empty query matches everything. */
    static boolean matchesQuery(Models.Media media, String query) {
        if (media == null) {
            return false;
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.GERMAN);
        if (q.isEmpty()) {
            return true;
        }
        String name = media.name == null ? "" : media.name.toLowerCase(Locale.GERMAN);
        return name.contains(q);
    }

    static List<Models.Media> all() {
        ArrayList<Models.Media> arrayList = new ArrayList<>();
        List<Models.Media> grouped;
        synchronized (ExtraMediaSource.class) {
            arrayList.addAll(films);
            grouped = groupedSerials;
        }
        if (grouped == null) {
            grouped = serialsGrouped();
        }
        arrayList.addAll(grouped);
        return arrayList;
    }

    static boolean isCatalogEmpty() {
        synchronized (ExtraMediaSource.class) {
            return films.isEmpty() && serials.isEmpty();
        }
    }

    static List<Models.Media> search(String str) {
        if (str == null || str.trim().isEmpty()) {
            return all();
        }
        ensureToken();
        try {
            List<Models.Media> parseList = parseList(req(base() + "/index.php?do=search", "do=search&subaction=search&search_start=1&full_search=0&result_from=1&story=" + URLEncoder.encode(str.trim(), "UTF-8")), false);
            if (!parseList.isEmpty()) {
                return parseList;
            }
        } catch (Exception unused) {
            Quiet.ignored("ExtraMediaSource", unused);
        }
        return localSearch(str);
    }

    static List<Models.Media> localSearch(String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.GERMAN);
        ArrayList arrayList = new ArrayList();
        for (Models.Media media : all()) {
            if (media.name != null && media.name.toLowerCase(Locale.GERMAN).contains(lowerCase)) {
                arrayList.add(media);
            }
        }
        return arrayList;
    }

    static void enrich(Models.Media media) {
        if (media == null || media.streamUrl == null) {
            return;
        }
        ensureToken();
        String req = req(abs(media.streamUrl), null);
        if (req == null) {
            return;
        }
        Matcher matcher = TITLE.matcher(req);
        if (matcher.find()) {
            media.name = Text.clean(matcher.group(1));
        } else {
            Matcher matcher2 = Pattern.compile("<h1[^>]*>([^<]+)", 2).matcher(req);
            if (matcher2.find()) {
                media.name = Text.clean(matcher2.group(1));
            }
        }
        Matcher matcher3 = DESC.matcher(req);
        String htmlText = matcher3.find() ? htmlText(matcher3.group(1)) : "";
        if (htmlText.isEmpty()) {
            Matcher matcher4 = PAGE_TEXT.matcher(req);
            if (matcher4.find()) {
                htmlText = htmlText(matcher4.group(1));
            }
        }
        if (!htmlText.isEmpty()) {
            media.plot = htmlText;
        }
        Matcher matcher5 = YEAR.matcher(req);
        if (matcher5.find()) {
            String clean = Text.clean(matcher5.group(1));
            Matcher matcher6 = Pattern.compile("(19|20)\\d{2}").matcher(clean);
            if (matcher6.find()) {
                clean = matcher6.group();
            }
            media.year = clean;
        }
        Matcher matcher7 = GENRE.matcher(req);
        if (matcher7.find()) {
            String replace = Text.clean(matcher7.group(1)).replace('/', Typography.middleDot);
            if (!replace.isEmpty()) {
                media.genre = replace;
            }
        }
        Matcher matcher8 = Pattern.compile("itemprop=\"duration\"[^>]*content=\"([^\"]+)\"", 2).matcher(req);
        if (matcher8.find()) {
            media.duration = isoDur(matcher8.group(1));
        }
        int seasonOf = seasonOf(media.name);
        media.episodes.clear();
        Matcher matcher9 = SE_SELECT.matcher(req);
        if (matcher9.find()) {
            Matcher matcher10 = OPTION.matcher(matcher9.group(1));
            int i = 0;
            while (matcher10.find()) {
                String group = matcher10.group(1);
                String clean2 = Text.clean(matcher10.group(2));
                if (group != null && !group.isEmpty() && !group.startsWith("#") && !group.startsWith("http")) {
                    i++;
                    int n = epNum(group, 0);
                    if (n <= 0) {
                        n = epNum(clean2, 0);
                    }
                    if (n <= 0) {
                        n = i;
                    }
                    Models.Episode episode = new Models.Episode();
                    episode.id = "mkep:" + abs(media.streamUrl) + "|" + group;
                    episode.season = seasonOf;
                    episode.episode = n;
                    episode.title = isGenericEpisodeTitle(clean2) ? ("Folge " + n) : clean2;
                    episode.streamUrl = firstHttpOption(req, group);
                    if (episode.streamUrl == null) {
                        episode.streamUrl = group;
                    }
                    media.episodes.add(episode);
                }
            }
        }
        if (media.episodes.isEmpty()) {
            return;
        }
        media.episodes.sort(new Comparator<Models.Episode>() {
            @Override
            public int compare(Models.Episode a, Models.Episode b) {
                int se = Integer.compare(a.season, b.season);
                return se != 0 ? se : Integer.compare(a.episode, b.episode);
            }
        });
        media.series = true;
    }

    static String playUrl(Models.Media media) {
        if (media == null) {
            return null;
        }
        if (looksStream(media.streamUrl)) {
            return media.streamUrl;
        }
        String str = media.streamUrl;
        if (str == null && media.id != null && media.id.startsWith("mk:")) {
            str = media.id.substring(3);
        }
        if (str == null) {
            return null;
        }
        ensureToken();
        return firstStream(req(abs(str), null));
    }

    static String playEpisode(Models.Episode episode) {
        String substring;
        int indexOf;
        if (episode == null) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        if (episode.streamUrl != null && episode.streamUrl.startsWith("http")) {
            arrayList.add(episode.streamUrl);
        }
        if (episode.id != null && episode.id.startsWith("mkep:") && (indexOf = (substring = episode.id.substring(5)).indexOf(124)) > 0) {
            ensureToken();
            for (String str : httpOptions(req(abs(substring.substring(0, indexOf)), null), substring.substring(indexOf + 1))) {
                if (!arrayList.contains(str)) {
                    arrayList.add(str);
                }
            }
        }
        arrayList.sort(new Comparator() { // from class: app.streamy2.ExtraMediaSource$$ExternalSyntheticLambda1
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(ExtraMediaSource.rank((String) obj), ExtraMediaSource.rank((String) obj2));
                return compare;
            }
        });
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            String resolve = Hosts.resolve((String) it.next());
            if (resolve != null) {
                return resolve;
            }
        }
        return null;
    }

    static String firstStream(String str) {
        if (str == null) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        Matcher matcher = IFRAME.matcher(str);
        while (matcher.find()) {
            addEmbed(arrayList, matcher.group(1));
        }
        Matcher matcher2 = OPTION.matcher(str);
        while (matcher2.find()) {
            addEmbed(arrayList, matcher2.group(1));
        }
        arrayList.sort(new Comparator() { // from class: app.streamy2.ExtraMediaSource$$ExternalSyntheticLambda2
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(ExtraMediaSource.rank((String) obj), ExtraMediaSource.rank((String) obj2));
                return compare;
            }
        });
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            String resolve = Hosts.resolve((String) it.next());
            if (resolve != null) {
                return resolve;
            }
        }
        return null;
    }

    private static int rank(String str) {
        String lowerCase = str.toLowerCase(Locale.US);
        if (lowerCase.contains("gxplayer") || lowerCase.contains("watch.gx")) {
            return 0;
        }
        return Voe.isVoe(lowerCase) ? 1 : 2;
    }

    private static void addEmbed(List<String> list, String str) {
        if (str == null || str.isEmpty() || str.startsWith("about:")) {
            return;
        }
        if (str.startsWith("//")) {
            str = "https:" + str;
        } else if (str.startsWith("/")) {
            str = abs(str);
        }
        if (str.startsWith("http")) {
            String lowerCase = str.toLowerCase(Locale.US);
            if (lowerCase.contains("youtube") || lowerCase.contains("youtu.be") || list.contains(str)) {
                return;
            }
            list.add(str);
        }
    }

    private static boolean looksStream(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.contains(".m3u8") || lowerCase.contains(".mp4") || lowerCase.contains("/hls/") || lowerCase.contains("/alternative_stream/");
    }

    private static List<Models.Media> parseList(String str, boolean z) {
        ArrayList arrayList = new ArrayList();
        if (str == null) {
            return arrayList;
        }
        int i = 0;
        while (true) {
            int indexOf = str.indexOf("poster grid-item", i);
            if (indexOf < 0) {
                return arrayList;
            }
            int lastIndexOf = str.lastIndexOf("<a", indexOf);
            int indexOf2 = str.indexOf("</a>", indexOf);
            if (lastIndexOf < 0 || indexOf2 < 0 || lastIndexOf < i - 80) {
                i = indexOf + 16;
            } else {
                i = indexOf2 + 4;
                String substring = str.substring(lastIndexOf, i);
                String first = first(HREF, substring);
                String first2 = first(DATA_SRC, substring);
                String clean = Text.clean(first(POSTER_TITLE, substring));
                if (first != null && !clean.isEmpty() && !first.contains("/cast/") && !first.contains("/genre/")) {
                    boolean z2 = z || first.contains("/serials/");
                    Models.Media media = new Models.Media();
                    media.id = "mk:" + abs(first);
                    media.name = clean;
                    media.poster = abs(first2);
                    media.streamUrl = abs(first);
                    media.series = z2;
                    media.genre = z2 ? "Serie · Media Extra" : "Film · Media Extra";
                    media.categoryId = CAT;
                    media.plot = Text.clean(first(POSTER_TEXT, substring));
                    Matcher matcher = POSTER_SUB.matcher(substring);
                    if (matcher.find()) {
                        Matcher matcher2 = Pattern.compile("(19|20)\\d{2}").matcher(Text.clean(matcher.group(1)));
                        if (matcher2.find()) {
                            media.year = matcher2.group();
                        }
                    }
                    if (matcher.find()) {
                        String trim = Text.clean(matcher.group(1)).replace("Filme /", "").replace("Serien /", "").trim();
                        if (!trim.isEmpty()) {
                            media.genre = trim + (z2 ? " · Serie" : " · Film");
                        }
                    }
                    if (!contains(arrayList, media.id)) {
                        arrayList.add(media);
                    }
                }
            }
        }
    }

    private static String firstHttpOption(String str, String str2) {
        List<String> httpOptions = httpOptions(str, str2);
        if (httpOptions.isEmpty()) {
            return null;
        }
        return httpOptions.get(0);
    }

    private static List<String> httpOptions(String str, String str2) {
        ArrayList arrayList = new ArrayList();
        if (str != null && str2 != null) {
            Matcher matcher = SELECT_ID.matcher(str);
            while (matcher.find()) {
                if (str2.equalsIgnoreCase(matcher.group(1))) {
                    Matcher matcher2 = OPTION.matcher(matcher.group(2));
                    while (matcher2.find()) {
                        String group = matcher2.group(1);
                        if (group != null && group.startsWith("http") && !arrayList.contains(group)) {
                            arrayList.add(group);
                        }
                    }
                }
            }
        }
        return arrayList;
    }

    static int seasonOf(String str) {
        if (str == null) {
            return 1;
        }
        Matcher matcher = Pattern.compile("(?i)staffel\\s*(\\d+)").matcher(str);
        if (matcher.find()) {
            return parseInt(matcher.group(1));
        }
        matcher = Pattern.compile("(?i)(?:^|\\s|-)\\s*(\\d+)\\s*staffel").matcher(str);
        if (matcher.find()) {
            return parseInt(matcher.group(1));
        }
        matcher = Pattern.compile("(?i)\\bs(?:eason)?\\s*(\\d+)\\b").matcher(str);
        if (matcher.find()) {
            int n = parseInt(matcher.group(1));
            return n > 0 ? n : 1;
        }
        return 1;
    }

    static String showTitle(String str) {
        if (str == null) {
            return "";
        }
        String t = str.replaceAll("(?i)\\s*[-–:]\\s*(?:staffel\\s*)?\\d+(?:\\s*staffel)?\\s*$", "").trim();
        t = t.replaceAll("(?i)\\s*[-–:]\\s*s(?:eason)?\\s*\\d+\\s*$", "").trim();
        return t.isEmpty() ? str.trim() : t;
    }

    static String showKey(String str) {
        return showTitle(str).toLowerCase(Locale.GERMAN);
    }

    /** One card per series (latest season), A–Z — seasons remain in {@link #serials} for the picker. */
    static List<Models.Media> serialsGrouped() {
        List<Models.Media> snap;
        synchronized (ExtraMediaSource.class) {
            if (groupedSerials != null) {
                return groupedSerials;
            }
            snap = new ArrayList<>(serials);
        }
        LinkedHashMap<String, ArrayList<Models.Media>> groups = new LinkedHashMap<>();
        for (Models.Media media2 : snap) {
            if (media2 == null || media2.name == null) {
                continue;
            }
            String key = showKey(media2.name);
            if (key.isEmpty()) {
                key = media2.name.toLowerCase(Locale.GERMAN);
            }
            ArrayList<Models.Media> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(key, g);
            }
            g.add(media2);
        }
        ArrayList<String> keys = new ArrayList<>(groups.keySet());
        final java.text.Collator collator = java.text.Collator.getInstance(Locale.GERMAN);
        collator.setStrength(java.text.Collator.PRIMARY);
        keys.sort(collator);
        ArrayList<Models.Media> out = new ArrayList<>();
        for (String key : keys) {
            ArrayList<Models.Media> seasons = groups.get(key);
            seasons.sort(new Comparator<Models.Media>() {
                @Override
                public int compare(Models.Media a, Models.Media b) {
                    return Integer.compare(seasonOf(a.name), seasonOf(b.name));
                }
            });
            Models.Media latest = seasons.get(seasons.size() - 1);
            Models.Media card = copyCard(latest);
            card.name = showTitle(latest.name);
            card.series = true;
            int n = seasons.size();
            if (n > 1) {
                card.genre = n + " Staffeln · Serie";
            } else {
                int se = seasonOf(latest.name);
                card.genre = (se > 1 ? ("Staffel " + se + " · ") : "") + "Serie";
            }
            out.add(card);
        }
        synchronized (ExtraMediaSource.class) {
            if (groupedSerials == null) {
                groupedSerials = out;
            }
            return groupedSerials;
        }
    }

    private static Models.Media copyCard(Models.Media src) {
        Models.Media m = new Models.Media();
        m.id = src.id;
        m.name = src.name;
        m.poster = src.poster;
        m.streamUrl = src.streamUrl;
        m.series = true;
        m.genre = src.genre;
        m.year = src.year;
        m.plot = src.plot;
        m.categoryId = src.categoryId;
        return m;
    }

    static List<Models.Media> seasonsOf(Models.Media media) {
        ArrayList arrayList = new ArrayList();
        String showKey = showKey(media == null ? null : media.name);
        if (showKey.isEmpty()) {
            return arrayList;
        }
        synchronized (ExtraMediaSource.class) {
            for (Models.Media media2 : serials) {
                if (media2 != null && showKey.equals(showKey(media2.name))) {
                    arrayList.add(media2);
                }
            }
        }
        arrayList.sort(new Comparator() { // from class: app.streamy2.ExtraMediaSource$$ExternalSyntheticLambda0
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(ExtraMediaSource.seasonOf(((Models.Media) obj).name), ExtraMediaSource.seasonOf(((Models.Media) obj2).name));
                return compare;
            }
        });
        return arrayList;
    }

    private static String htmlText(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        String replaceAll = str.replaceAll("(?i)<br\\s*/?>", "\n");
        try {
            replaceAll = Html.fromHtml(replaceAll, 0).toString();
        } catch (Exception unused) {
            Quiet.ignored("ExtraMediaSource", unused);
        }
        return replaceAll.replace(Typography.nbsp, ' ').replaceAll("[ \\t]+", " ").trim();
    }

    private static String isoDur(String str) {
        if (str == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?", 2).matcher(str.trim());
        if (!matcher.matches()) {
            return "";
        }
        int parseInt = matcher.group(1) == null ? 0 : parseInt(matcher.group(1));
        int parseInt2 = matcher.group(2) == null ? 0 : parseInt(matcher.group(2));
        int parseInt3 = matcher.group(3) != null ? parseInt(matcher.group(3)) : 0;
        if (parseInt == 0 && parseInt2 == 0 && parseInt3 > 0) {
            parseInt2 = Math.round(parseInt3 / 60.0f);
        }
        int i = parseInt2 + (parseInt * 60);
        if (i <= 0) {
            return "";
        }
        return String.valueOf(i);
    }

    private static int parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Exception unused) {
            return 0;
        }
    }

    static boolean isGenericEpisodeTitle(String str) {
        return str == null || str.trim().isEmpty() || GENERIC_EP.matcher(str.trim()).matches();
    }

    static boolean hasSeasonSuffix(String str) {
        return str != null && SEASON_SUFFIX.matcher(str).find();
    }

    /** "Folge 1" — skips redundant "Episode 1" from Media Extra option labels. */
    static String formatEpisodeRow(Models.Episode episode) {
        if (episode == null) {
            return "";
        }
        int n = episode.episode > 0 ? episode.episode : 1;
        String t = episode.title == null ? "" : episode.title.trim();
        if (isGenericEpisodeTitle(t)) {
            return "Folge " + n;
        }
        return "Folge " + n + "  ·  " + t;
    }

    static String formatEpisodeSub(Models.Episode episode) {
        if (episode == null) {
            return "";
        }
        int se = episode.season > 0 ? episode.season : 1;
        return "Staffel " + se + " · " + formatEpisodeRow(episode);
    }

    private static int epNum(String str, int i) {
        if (str == null) {
            return i;
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(str);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception unused) {
                Quiet.ignored("ExtraMediaSource", unused);
            }
        }
        return i;
    }

    private static String first(Pattern pattern, String str) {
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static boolean contains(List<Models.Media> list, String str) {
        for (Models.Media media : list) {
            if (str != null && str.equals(media.id)) {
                return true;
            }
        }
        return false;
    }

    static String abs(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (str.startsWith("http")) {
            return str;
        }
        if (str.startsWith("//")) {
            return "https:" + str;
        }
        String str2 = base;
        if (str2 == null) {
            str2 = BASES[0];
        }
        if (!str.startsWith("/")) {
            str = "/" + str;
        }
        return str2 + str;
    }

    private static String origin(String str) {
        try {
            URL url = new URL(str);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception unused) {
            return str;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r13v1 */
    /* JADX WARN: Type inference failed for: r13v2, types: [int] */
    /* JADX WARN: Type inference failed for: r13v4 */
    private static String req(String str, String str2) {
        HttpURLConnection httpURLConnection;
        boolean z = false;
        String str3 = str;
        int i = 0;
        while (i < 6) {
            try {
                httpURLConnection = (HttpURLConnection) new URL(str3).openConnection();
                try {
                    httpURLConnection.setInstanceFollowRedirects(z);
                    httpURLConnection.setConnectTimeout(12000);
                    httpURLConnection.setReadTimeout(18000);
                    httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0");
                    httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9,en;q=0.8");
                    String str4 = base;
                    if (str4 == null) {
                        str4 = origin(str3);
                    }
                    httpURLConnection.setRequestProperty(HttpHeaders.REFERER, str4 + "/");
                    String cookieHeader = cookieHeader();
                    if (!cookieHeader.isEmpty()) {
                        httpURLConnection.setRequestProperty(HttpHeaders.COOKIE, cookieHeader);
                    }
                    if (str2 != null) {
                        httpURLConnection.setRequestMethod("POST");
                        httpURLConnection.setDoOutput(true);
                        httpURLConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
                        byte[] bytes = str2.getBytes(StandardCharsets.UTF_8);
                        httpURLConnection.setFixedLengthStreamingMode(bytes.length);
                        OutputStream outputStream = httpURLConnection.getOutputStream();
                        try {
                            outputStream.write(bytes);
                            if (outputStream != null) {
                                outputStream.close();
                            }
                        } finally {
                        }
                    }
                    int responseCode = httpURLConnection.getResponseCode();
                    absorbCookies(httpURLConnection);
                    String headerField = httpURLConnection.getHeaderField(HttpHeaders.LOCATION);
                    if (headerField != null && headerField.startsWith("/")) {
                        headerField = origin(str3) + headerField;
                    }
                    if (responseCode >= 300 && responseCode < 400 && headerField != null && headerField.startsWith("http")) {
                        if (headerField.startsWith("http://")) {
                            headerField = "https://" + headerField.substring(7);
                        }
                        String origin = origin(headerField);
                        if (isRestrictedUrl(origin)) {
                            base = origin;
                        }
                        httpURLConnection.disconnect();
                        str3 = headerField;
                    } else {
                        if (responseCode != 204 && responseCode != 205) {
                            InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
                            if (errorStream == null) {
                                httpURLConnection.disconnect();
                                return null;
                            }
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            int r13 = 0;
                            while (true) {
                                String readLine = bufferedReader.readLine();
                                if (readLine == null) {
                                    break;
                                }
                                sb.append(readLine).append('\n');
                                int length = r13 + readLine.length();
                                if (length > 1500000) {
                                    break;
                                }
                                r13 = length;
                            }
                            bufferedReader.close();
                            if (responseCode < 400) {
                                String origin2 = origin(httpURLConnection.getURL().toString());
                                if (isRestrictedUrl(origin2)) {
                                    base = origin2;
                                }
                            }
                            httpURLConnection.disconnect();
                            if (sb.indexOf("yg=token") >= 0 && sb.length() < 800 && !str3.contains("yg=token")) {
                                req(origin(str3) + "/index.php?yg=token", null);
                            } else {
                                return sb.toString();
                            }
                        }
                        httpURLConnection.disconnect();
                        return "";
                    }
                    i++;
                    z = false;
                } catch (Exception unused) {
                    if (httpURLConnection != null) {
                        try {
                            httpURLConnection.disconnect();
                        } catch (Exception unused2) {
                            Quiet.ignored("ExtraMediaSource", unused2);
                        }
                    }
                    return null;
                }
            } catch (Exception unused3) {
                httpURLConnection = null;
            }
        }
        return null;
    }

    private static synchronized void absorbCookies(HttpURLConnection httpURLConnection) {
        synchronized (ExtraMediaSource.class) {
            int i = 0;
            while (true) {
                try {
                    String headerFieldKey = httpURLConnection.getHeaderFieldKey(i);
                    String headerField = httpURLConnection.getHeaderField(i);
                    if (headerFieldKey == null && headerField == null) {
                        break;
                    }
                    if (headerFieldKey != null && headerField != null && "set-cookie".equalsIgnoreCase(headerFieldKey)) {
                        int indexOf = headerField.indexOf(59);
                        if (indexOf > 0) {
                            headerField = headerField.substring(0, indexOf);
                        }
                        int indexOf2 = headerField.indexOf(61);
                        if (indexOf2 > 0) {
                            cookies.put(headerField.substring(0, indexOf2).trim(), headerField.substring(indexOf2 + 1).trim());
                        }
                    }
                    i++;
                } catch (Exception unused) {
                    Quiet.ignored("ExtraMediaSource", unused);
                }
            }
        }
    }

    private static synchronized String cookieHeader() {
        synchronized (ExtraMediaSource.class) {
            Map<String, String> map = cookies;
            if (map.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return sb.toString();
        }
    }
}
