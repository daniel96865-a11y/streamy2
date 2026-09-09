package app.streamy2;

import android.util.Base64;
import app.streamy2.EpgGuide;
import app.streamy2.Models;
import com.google.common.net.HttpHeaders;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class XtreamApi {
    public final String base;
    public final String format;
    public final String pass;
    public final String user;

    public XtreamApi(String str, String str2, String str3, String str4) {
        this.base = normalize(str);
        this.user = str2 == null ? "" : str2.trim();
        this.pass = str3 != null ? str3.trim() : "";
        this.format = "ts".equals(str4) ? "ts" : "hls";
    }

    public static String normalize(String str) {
        String trim = str == null ? "" : str.trim();
        if (trim.isEmpty()) {
            return "";
        }
        if (!trim.matches("(?i)^https?://.*")) {
            trim = "https://" + trim;
        }
        while (trim.endsWith("/")) {
            trim = trim.substring(0, trim.length() - 1);
        }
        return trim;
    }

    public boolean loginOk() throws Exception {
        JSONObject optJSONObject = asObject(getRaw(null, null)).optJSONObject("user_info");
        if (optJSONObject == null) {
            return false;
        }
        return "1".equals(optJSONObject.optString("auth", "")) || "Active".equalsIgnoreCase(optJSONObject.optString("status", ""));
    }

    public Models.Catalog loadLive() throws Exception {
        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(2);
        try {
            Future submit = newFixedThreadPool.submit(new Callable() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda5
                @Override // java.util.concurrent.Callable
                public final Object call() throws Exception {
                    Object lambda$loadLive$0;
                    lambda$loadLive$0 = XtreamApi.this.lambda$loadLive$0();
                    return lambda$loadLive$0;
                }
            });
            Future submit2 = newFixedThreadPool.submit(new Callable() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda6
                @Override // java.util.concurrent.Callable
                public final Object call() throws Exception {
                    Object lambda$loadLive$1;
                    lambda$loadLive$1 = XtreamApi.this.lambda$loadLive$1();
                    return lambda$loadLive$1;
                }
            });
            Models.Catalog catalog = new Models.Catalog();
            catalog.liveCats = mapCats(asArray(submit.get()));
            fillLive(catalog, asArray(submit2.get()));
            return catalog;
        } finally {
            newFixedThreadPool.shutdownNow();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Object lambda$loadLive$0() throws Exception {
        return getRaw("get_live_categories", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Object lambda$loadLive$1() throws Exception {
        return getRaw("get_live_streams", null);
    }

    public void loadLibrary(Models.Catalog catalog) {
        if (catalog == null) {
            return;
        }
        try {
            ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(4);
            Future submit = newFixedThreadPool.submit(new Callable() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda0
                @Override // java.util.concurrent.Callable
                public final Object call() throws Exception {
                    Object lambda$loadLibrary$2;
                    lambda$loadLibrary$2 = XtreamApi.this.lambda$loadLibrary$2();
                    return lambda$loadLibrary$2;
                }
            });
            Future submit2 = newFixedThreadPool.submit(new Callable() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda1
                @Override // java.util.concurrent.Callable
                public final Object call() throws Exception {
                    Object lambda$loadLibrary$3;
                    lambda$loadLibrary$3 = XtreamApi.this.lambda$loadLibrary$3();
                    return lambda$loadLibrary$3;
                }
            });
            Future submit3 = newFixedThreadPool.submit(new Callable() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda2
                @Override // java.util.concurrent.Callable
                public final Object call() throws Exception {
                    Object lambda$loadLibrary$4;
                    lambda$loadLibrary$4 = XtreamApi.this.lambda$loadLibrary$4();
                    return lambda$loadLibrary$4;
                }
            });
            Future submit4 = newFixedThreadPool.submit(new Callable() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda3
                @Override // java.util.concurrent.Callable
                public final Object call() throws Exception {
                    Object lambda$loadLibrary$5;
                    lambda$loadLibrary$5 = XtreamApi.this.lambda$loadLibrary$5();
                    return lambda$loadLibrary$5;
                }
            });
            catalog.vodCats = mapCats(asArray(submit.get()));
            catalog.seriesCats = mapCats(asArray(submit2.get()));
            fillVod(catalog, asArray(submit3.get()));
            fillSeries(catalog, asArray(submit4.get()));
            newFixedThreadPool.shutdownNow();
        } catch (Exception unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Object lambda$loadLibrary$2() throws Exception {
        return getRaw("get_vod_categories", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Object lambda$loadLibrary$3() throws Exception {
        return getRaw("get_series_categories", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Object lambda$loadLibrary$4() throws Exception {
        return getRaw("get_vod_streams", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Object lambda$loadLibrary$5() throws Exception {
        return getRaw("get_series", null);
    }

    public Models.Catalog loadCatalog() throws Exception {
        Models.Catalog loadLive = loadLive();
        loadLibrary(loadLive);
        return loadLive;
    }

    private void fillLive(Models.Catalog catalog, JSONArray jSONArray) {
        String enc = enc(this.user);
        String enc2 = enc(this.pass);
        if (jSONArray == null) {
            return;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null) {
                Models.Channel channel = new Models.Channel();
                channel.id = optJSONObject.optString("stream_id", String.valueOf(i));
                channel.name = Text.clean(optJSONObject.optString("name", "Sender"));
                channel.categoryId = optJSONObject.optString("category_id", "");
                channel.logo = optJSONObject.optString("stream_icon", "");
                channel.epgChannelId = optJSONObject.isNull("epg_channel_id") ? "" : optJSONObject.optString("epg_channel_id", "");
                if ("null".equalsIgnoreCase(channel.epgChannelId)) {
                    channel.epgChannelId = "";
                }
                if (channel.epgChannelId.isEmpty() && !optJSONObject.isNull("xmltv_id")) {
                    channel.epgChannelId = optJSONObject.optString("xmltv_id", "");
                }
                channel.number = optJSONObject.optInt("num", i + 1);
                boolean z = true;
                channel.header = channel.name.contains("#####") || channel.name.startsWith("---");
                channel.hlsUrl = this.base + "/live/" + enc + "/" + enc2 + "/" + channel.id + ".m3u8";
                channel.tsUrl = this.base + "/live/" + enc + "/" + enc2 + "/" + channel.id + ".ts";
                channel.archiveDays = optJSONObject.optInt("tv_archive_duration", 0);
                if (optJSONObject.optInt("tv_archive", 0) != 1 && channel.archiveDays <= 0) {
                    z = false;
                }
                channel.archive = z;
                if (channel.archive && channel.archiveDays <= 0) {
                    channel.archiveDays = 7;
                }
                if ("ts".equals(this.format)) {
                    String str = channel.hlsUrl;
                    channel.hlsUrl = channel.tsUrl;
                    channel.tsUrl = str;
                }
                catalog.live.add(channel);
            }
        }
        HashMap hashMap = new HashMap();
        for (Models.Category category : catalog.liveCats) {
            hashMap.put(category.id, category.name);
        }
        for (Models.Channel channel2 : catalog.live) {
            String str2 = (String) hashMap.get(channel2.categoryId);
            if (str2 == null) {
                str2 = "";
            }
            channel2.categoryName = str2;
        }
    }

    private void fillVod(Models.Catalog catalog, JSONArray jSONArray) {
        String enc = enc(this.user);
        String enc2 = enc(this.pass);
        if (jSONArray == null) {
            return;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null) {
                Models.Media media = new Models.Media();
                media.id = optJSONObject.optString("stream_id", String.valueOf(i));
                media.name = Text.clean(optJSONObject.optString("name", "Film"));
                media.categoryId = optJSONObject.optString("category_id", "");
                media.poster = firstUrl(optJSONObject.optString("stream_icon"), optJSONObject.optString("cover"), optJSONObject.optString("movie_image"));
                media.year = optJSONObject.optString("year", "");
                media.genre = optJSONObject.optString("genre", "");
                media.plot = firstNonEmpty(optJSONObject.optString("plot"), optJSONObject.optString("description"));
                media.rating = optJSONObject.optString("rating", "");
                media.duration = optJSONObject.optString("duration", "");
                media.director = optJSONObject.optString("director", "");
                media.cast = optJSONObject.optString("cast", "");
                media.streamUrl = this.base + "/movie/" + enc + "/" + enc2 + "/" + media.id + "." + optJSONObject.optString("container_extension", "mp4");
                catalog.vod.add(media);
            }
        }
    }

    private void fillSeries(Models.Catalog catalog, JSONArray jSONArray) {
        if (jSONArray == null) {
            return;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null) {
                Models.Media media = new Models.Media();
                media.id = optJSONObject.optString("series_id", String.valueOf(i));
                media.name = Text.clean(optJSONObject.optString("name", "Serie"));
                media.categoryId = optJSONObject.optString("category_id", "");
                media.poster = firstUrl(optJSONObject.optString("cover"), optJSONObject.optString("stream_icon"), optJSONObject.optString("movie_image"));
                media.year = optJSONObject.optString("year", "");
                media.genre = optJSONObject.optString("genre", "");
                media.plot = firstNonEmpty(optJSONObject.optString("plot"), optJSONObject.optString("description"));
                media.rating = optJSONObject.optString("rating", "");
                media.duration = optJSONObject.optString("episode_run_time", optJSONObject.optString("duration", ""));
                media.director = optJSONObject.optString("director", "");
                media.cast = optJSONObject.optString("cast", "");
                media.series = true;
                catalog.series.add(media);
            }
        }
    }

    public String xmltvUrl() {
        return this.base + "/xmltv.php?username=" + enc(this.user) + "&password=" + enc(this.pass);
    }

    public void enrich(Models.Media media) throws Exception {
        if (media == null || media.id == null) {
            return;
        }
        if (media.series) {
            JSONObject asObject = asObject(getRaw("get_series_info", "series_id=" + enc(media.id)));
            applyInfo(media, asObject.optJSONObject("info"));
            if (media.episodes.isEmpty()) {
                media.episodes.addAll(parseEpisodes(asObject));
                return;
            }
            return;
        }
        JSONObject asObject2 = asObject(getRaw("get_vod_info", "vod_id=" + enc(media.id)));
        applyInfo(media, asObject2.optJSONObject("info"));
        JSONObject optJSONObject = asObject2.optJSONObject("movie_data");
        if (optJSONObject != null) {
            String optString = optJSONObject.optString("container_extension", "mp4");
            media.streamUrl = this.base + "/movie/" + enc(this.user) + "/" + enc(this.pass) + "/" + media.id + "." + (optString.isEmpty() ? "mp4" : optString);
        }
    }

    public List<Models.Episode> seriesEpisodes(String str) throws Exception {
        return parseEpisodes(asObject(getRaw("get_series_info", "series_id=" + enc(str))));
    }

    private List<Models.Episode> parseEpisodes(JSONObject jSONObject) {
        int i;
        JSONObject optJSONObject = jSONObject == null ? null : jSONObject.optJSONObject("episodes");
        ArrayList arrayList = new ArrayList();
        if (optJSONObject == null) {
            return arrayList;
        }
        String enc = enc(this.user);
        String enc2 = enc(this.pass);
        Iterator<String> keys = optJSONObject.keys();
        while (keys.hasNext()) {
            String next = keys.next();
            JSONArray optJSONArray = optJSONObject.optJSONArray(next);
            if (optJSONArray != null) {
                try {
                    i = Integer.parseInt(next);
                } catch (Exception unused) {
                    i = 1;
                }
                for (int i2 = 0; i2 < optJSONArray.length(); i2++) {
                    JSONObject optJSONObject2 = optJSONArray.optJSONObject(i2);
                    if (optJSONObject2 != null) {
                        Models.Episode episode = new Models.Episode();
                        episode.id = optJSONObject2.optString("id", next + "-" + i2);
                        int i3 = i2 + 1;
                        episode.title = optJSONObject2.optString("title", "Folge " + i3);
                        episode.season = i;
                        episode.episode = optJSONObject2.optInt("episode_num", i3);
                        episode.streamUrl = this.base + "/series/" + enc + "/" + enc2 + "/" + episode.id + "." + optJSONObject2.optString("container_extension", "mp4");
                        arrayList.add(episode);
                    }
                }
            }
        }
        arrayList.sort(new Comparator() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda7
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return XtreamApi.lambda$parseEpisodes$6((Models.Episode) obj, (Models.Episode) obj2);
            }
        });
        return arrayList;
    }

    static /* synthetic */ int lambda$parseEpisodes$6(Models.Episode episode, Models.Episode episode2) {
        int i;
        int i2;
        if (episode.season != episode2.season) {
            i = episode.season;
            i2 = episode2.season;
        } else {
            i = episode.episode;
            i2 = episode2.episode;
        }
        return Integer.compare(i, i2);
    }

    private static void applyInfo(Models.Media media, JSONObject jSONObject) {
        if (media == null || jSONObject == null) {
            return;
        }
        fill(media, jSONObject);
    }

    private static void fill(Models.Media media, JSONObject jSONObject) {
        String optString = jSONObject.optString("plot", jSONObject.optString("description", ""));
        if (!optString.isEmpty() && (media.plot == null || media.plot.isEmpty() || optString.length() > media.plot.length())) {
            media.plot = Text.clean(optString);
        }
        String optString2 = jSONObject.optString("genre", "");
        if (!optString2.isEmpty() && (media.genre == null || media.genre.isEmpty())) {
            media.genre = optString2;
        }
        String optString3 = jSONObject.optString("releasedate", jSONObject.optString("releaseDate", jSONObject.optString("year", "")));
        if (!optString3.isEmpty() && (media.year == null || media.year.isEmpty())) {
            if (optString3.length() >= 4) {
                optString3 = optString3.substring(0, 4);
            }
            media.year = optString3;
        }
        String optString4 = jSONObject.optString("rating", jSONObject.optString("rating_5based", ""));
        if (!optString4.isEmpty() && (media.rating == null || media.rating.isEmpty())) {
            media.rating = optString4;
        }
        String optString5 = jSONObject.optString("duration", jSONObject.optString("episode_run_time", ""));
        if (!optString5.isEmpty() && (media.duration == null || media.duration.isEmpty())) {
            media.duration = optString5;
        }
        String optString6 = jSONObject.optString("director", "");
        if (!optString6.isEmpty()) {
            media.director = optString6;
        }
        String optString7 = jSONObject.optString("cast", jSONObject.optString("actors", ""));
        if (!optString7.isEmpty()) {
            media.cast = optString7;
        }
        String optString8 = jSONObject.optString("movie_image", jSONObject.optString("cover", ""));
        if (optString8.isEmpty()) {
            Object opt = jSONObject.opt("backdrop_path");
            if (opt instanceof JSONArray) {
                JSONArray jSONArray = (JSONArray) opt;
                if (jSONArray.length() > 0) {
                    optString8 = jSONArray.optString(0, "");
                }
            }
        }
        if (optString8.isEmpty()) {
            return;
        }
        media.poster = optString8;
    }

    public Models.Epg shortEpg(String str) {
        Models.Channel channel = new Models.Channel();
        channel.id = str;
        return shortEpg(channel);
    }

    public Models.Epg shortEpg(Models.Channel channel) {
        if (channel == null) {
            return null;
        }
        String str = channel.id;
        Models.Epg parseEpgList = parseEpgList(getEpgArray("get_short_epg", "stream_id=" + enc(str) + "&limit=4"));
        if (parseEpgList != null) {
            return parseEpgList;
        }
        Models.Epg parseEpgList2 = parseEpgList(getEpgArray("get_short_epg", "stream_id=" + enc(str)));
        if (parseEpgList2 != null) {
            return parseEpgList2;
        }
        Models.Epg parseEpgList3 = parseEpgList(getEpgArray("get_simple_data_table", "stream_id=" + enc(str)));
        if (parseEpgList3 != null) {
            return parseEpgList3;
        }
        if (channel.epgChannelId != null && !channel.epgChannelId.isEmpty()) {
            Models.Epg parseEpgList4 = parseEpgList(getEpgArray("get_short_epg", "epg_channel_id=" + enc(channel.epgChannelId) + "&limit=4"));
            if (parseEpgList4 != null) {
                return parseEpgList4;
            }
            Models.Epg parseEpgList5 = parseEpgList(getEpgArray("get_simple_data_table", "epg_channel_id=" + enc(channel.epgChannelId)));
            if (parseEpgList5 != null) {
                return parseEpgList5;
            }
        }
        return null;
    }

    public List<EpgGuide.Listing> programmes(Models.Channel channel) {
        ArrayList arrayList = new ArrayList();
        if (channel != null && channel.id != null) {
            JSONArray epgArray = getEpgArray("get_simple_data_table", "stream_id=" + enc(channel.id));
            if (epgArray == null) {
                epgArray = getEpgArray("get_short_epg", "stream_id=" + enc(channel.id) + "&limit=40");
            }
            if (epgArray == null && channel.epgChannelId != null && !channel.epgChannelId.isEmpty()) {
                epgArray = getEpgArray("get_simple_data_table", "epg_channel_id=" + enc(channel.epgChannelId));
            }
            if (epgArray == null) {
                return arrayList;
            }
            for (int i = 0; i < epgArray.length(); i++) {
                JSONObject optJSONObject = epgArray.optJSONObject(i);
                if (optJSONObject != null) {
                    EpgGuide.Listing listing = new EpgGuide.Listing();
                    listing.title = decodeMaybe(optJSONObject.optString("title", optJSONObject.optString("name", "")));
                    listing.desc = decodeMaybe(optJSONObject.optString("description", optJSONObject.optString("desc", "")));
                    listing.start = ts(optJSONObject, "start_timestamp");
                    if (listing.start == 0) {
                        listing.start = parseDate(optJSONObject.optString("start", ""));
                    }
                    listing.stop = ts(optJSONObject, "stop_timestamp");
                    if (listing.stop == 0) {
                        listing.stop = parseDate(optJSONObject.optString("end", optJSONObject.optString("stop", "")));
                    }
                    if (!listing.title.isEmpty() && listing.stop > listing.start) {
                        arrayList.add(listing);
                    }
                }
            }
            arrayList.sort(new Comparator() { // from class: app.streamy2.XtreamApi$$ExternalSyntheticLambda4
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    int compare;
                    compare = Long.compare(((EpgGuide.Listing) obj).start, ((EpgGuide.Listing) obj2).start);
                    return compare;
                }
            });
        }
        return arrayList;
    }

    public List<String> timeshiftUrls(Models.Channel channel, long j, long j2) {
        ArrayList arrayList = new ArrayList();
        if (channel != null && channel.id != null) {
            int max = (int) Math.max(1.0d, Math.ceil((j2 - j) / 60000.0d));
            String enc = enc(this.user);
            String enc2 = enc(this.pass);
            String fmtTime = fmtTime(j, "yyyy-MM-dd:HH-mm");
            String fmtTime2 = fmtTime(j, "yyyy-MM-dd:HH-mm-ss");
            arrayList.add(this.base + "/timeshift/" + enc + "/" + enc2 + "/" + max + "/" + fmtTime + "/" + channel.id + ".m3u8");
            arrayList.add(this.base + "/timeshift/" + enc + "/" + enc2 + "/" + max + "/" + fmtTime + "/" + channel.id + ".ts");
            arrayList.add(this.base + "/timeshift/" + enc + "/" + enc2 + "/" + max + "/" + fmtTime2 + "/" + channel.id + ".m3u8");
            arrayList.add(this.base + "/timeshift/" + enc + "/" + enc2 + "/" + max + "/" + fmtTime2 + "/" + channel.id + ".ts");
            arrayList.add(this.base + "/streaming/timeshift.php?username=" + enc + "&password=" + enc2 + "&stream=" + enc(channel.id) + "&start=" + fmtTime + "&duration=" + max);
        }
        return arrayList;
    }

    private static String fmtTime(long j, String str) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(str, Locale.US);
        simpleDateFormat.setTimeZone(TimeZone.getDefault());
        return simpleDateFormat.format(new Date(j));
    }

    private JSONArray getEpgArray(String str, String str2) {
        try {
            JSONArray listingsOf = listingsOf(getRaw(str, str2));
            if (listingsOf == null) {
                return null;
            }
            if (listingsOf.length() > 0) {
                return listingsOf;
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    private JSONArray listingsOf(Object obj) {
        JSONArray optJSONArray;
        if (obj instanceof JSONArray) {
            return (JSONArray) obj;
        }
        if (!(obj instanceof JSONObject)) {
            return null;
        }
        JSONObject jSONObject = (JSONObject) obj;
        String[] strArr = {"epg_listings", "listings", "data", "programmes", "epg", "results"};
        for (int i = 0; i < 6; i++) {
            String str = strArr[i];
            JSONArray optJSONArray2 = jSONObject.optJSONArray(str);
            if (optJSONArray2 != null && optJSONArray2.length() > 0) {
                return optJSONArray2;
            }
            Object opt = jSONObject.opt(str);
            if ((opt instanceof JSONObject) && (optJSONArray = ((JSONObject) opt).optJSONArray("epg_listings")) != null && optJSONArray.length() > 0) {
                return optJSONArray;
            }
        }
        return null;
    }

    private Models.Epg parseEpgList(JSONArray jSONArray) {
        JSONObject jSONObject;
        if (jSONArray != null && jSONArray.length() != 0) {
            long currentTimeMillis = System.currentTimeMillis();
            JSONObject jSONObject2 = null;
            int i = 0;
            while (true) {
                if (i >= jSONArray.length()) {
                    jSONObject = null;
                    break;
                }
                jSONObject = jSONArray.optJSONObject(i);
                if (jSONObject != null) {
                    long ts = ts(jSONObject, "stop_timestamp");
                    if (ts == 0) {
                        ts = parseDate(jSONObject.optString("end", jSONObject.optString("stop", "")));
                    }
                    if (jSONObject2 == null && (ts == 0 || ts > currentTimeMillis)) {
                        jSONObject2 = jSONObject;
                    } else if (jSONObject2 != null) {
                        break;
                    }
                }
                i++;
            }
            if (jSONObject2 == null) {
                jSONObject2 = jSONArray.optJSONObject(0);
            }
            if (jSONObject2 == null) {
                return null;
            }
            Models.Epg epg = new Models.Epg();
            epg.title = decodeMaybe(jSONObject2.optString("title", jSONObject2.optString("name", "")));
            epg.start = ts(jSONObject2, "start_timestamp");
            if (epg.start == 0) {
                epg.start = parseDate(jSONObject2.optString("start", ""));
            }
            epg.end = ts(jSONObject2, "stop_timestamp");
            if (epg.end == 0) {
                epg.end = parseDate(jSONObject2.optString("end", jSONObject2.optString("stop", "")));
            }
            if (jSONObject != null) {
                epg.nextTitle = decodeMaybe(jSONObject.optString("title", ""));
            }
            if (epg.title != null && !epg.title.isEmpty()) {
                return epg;
            }
        }
        return null;
    }

    private long parseDate(String str) {
        long j = 0;
        if (str != null && !str.isEmpty()) {
            String[] strArr = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "dd-MM-yyyy HH:mm"};
            for (int i = 0; i < 3; i++) {
                try {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(strArr[i], Locale.US);
                    simpleDateFormat.setTimeZone(TimeZone.getDefault());
                    return simpleDateFormat.parse(str).getTime();
                } catch (Exception unused) {
                }
            }
        }
        return j;
    }

    private long ts(JSONObject jSONObject, String str) {
        try {
            String optString = jSONObject.optString(str, "0");
            if (optString.isEmpty()) {
                return 0L;
            }
            long parseLong = Long.parseLong(optString);
            return parseLong < 1000000000000L ? parseLong * 1000 : parseLong;
        } catch (Exception unused) {
            return 0L;
        }
    }

    private String decodeMaybe(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        try {
            String trim = new String(Base64.decode(str, 0), StandardCharsets.UTF_8).trim();
            if (!trim.isEmpty() && looksText(trim)) {
                return Text.clean(trim);
            }
        } catch (Exception unused) {
        }
        return Text.clean(str);
    }

    private static boolean looksText(String str) {
        int i = 0;
        for (int i2 = 0; i2 < str.length(); i2++) {
            char charAt = str.charAt(i2);
            if (charAt >= ' ' || charAt == '\n' || charAt == '\r' || charAt == '\t') {
                i++;
            }
        }
        return ((float) i) > ((float) str.length()) * 0.85f;
    }

    private List<Models.Category> mapCats(JSONArray jSONArray) {
        ArrayList arrayList = new ArrayList();
        if (jSONArray == null) {
            return arrayList;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null) {
                arrayList.add(new Models.Category(optJSONObject.optString("category_id", String.valueOf(i)), Text.clean(optJSONObject.optString("category_name", "Ohne Kategorie"))));
            }
        }
        return arrayList;
    }

    private Object getRaw(String str, String str2) throws Exception {
        StringBuilder sb = new StringBuilder(this.base);
        sb.append("/player_api.php?username=").append(enc(this.user)).append("&password=").append(enc(this.pass));
        if (str != null) {
            sb.append("&action=").append(str);
        }
        if (str2 != null && !str2.isEmpty()) {
            sb.append("&").append(str2);
        }
        String trim = http(sb.toString()).trim();
        return trim.startsWith("[") ? new JSONArray(trim) : new JSONObject(trim);
    }

    private static String firstNonEmpty(String... strArr) {
        if (strArr == null) {
            return "";
        }
        for (String str : strArr) {
            if (str != null && !str.trim().isEmpty()) {
                return str.trim();
            }
        }
        return "";
    }

    private static String firstUrl(String... strArr) {
        if (strArr == null) {
            return "";
        }
        for (String str : strArr) {
            if (str != null && str.startsWith("http")) {
                return str;
            }
        }
        return "";
    }

    private JSONObject asObject(Object obj) {
        if (obj instanceof JSONObject) {
            return (JSONObject) obj;
        }
        JSONObject jSONObject = new JSONObject();
        try {
            jSONObject.put("epg_listings", obj);
        } catch (Exception unused) {
        }
        return jSONObject;
    }

    private JSONArray asArray(Object obj) {
        return obj instanceof JSONArray ? (JSONArray) obj : new JSONArray();
    }

    private static String enc(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8").replace("+", "%20");
        } catch (Exception unused) {
            return str;
        }
    }

    private static String http(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setReadTimeout(25000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "application/json,*/*");
        int responseCode = httpURLConnection.getResponseCode();
        InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
        if (errorStream == null) {
            throw new Exception("HTTP " + responseCode);
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                break;
            }
            sb.append(readLine);
        }
        bufferedReader.close();
        if (responseCode >= 400) {
            throw new Exception("HTTP " + responseCode);
        }
        return sb.toString();
    }
}
