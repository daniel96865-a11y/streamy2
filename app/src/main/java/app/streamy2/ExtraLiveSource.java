package app.streamy2;

import android.os.Build;
import androidx.media3.common.PlaybackException;
import app.streamy2.Models;
import com.google.common.net.HttpHeaders;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class ExtraLiveSource {
    static final String CAT_ID = "extra_live";
    static final String CAT_ID_PL = "extra_live_pl";
    private static final String CAT_NAME_DE = "Live Extra Deutschland";
    private static final String CAT_NAME_PL = "Live Extra Polen";
    private static volatile String sig;
    private static volatile long sigAt;
    private static final String[] HOSTS = {"https://kool.to", "https://vavoo.to", "https://www.vavoo.to"};
    private static final String[] PINGS = {"https://www.vavoo.tv/api/app/ping"};
    private static final String CLIENT_ID = "s2-" + UUID.randomUUID();
    private static final long CLIENT_STARTED_AT = System.currentTimeMillis();
    static volatile String lastError = "";
    static volatile String diagnosticStage = "Leerlauf";
    static volatile String diagnosticResolveHost = "";
    static volatile String diagnosticAuthHost = "";
    static final String EPG_URL = "https://epg.pw/xmltv/epg_DE.xml.gz";
    /** Network-first DE XMLTV sources (reachable). Cache is only for offline reuse after a successful pull. */
    static final String[] EPG_URLS = {
            "https://epg.pw/xmltv/epg_DE.xml.gz",
            "https://epg.lat/files/de.xml.gz",
            "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz"
    };
    private static volatile String activeHost = "https://kool.to";



    ExtraLiveSource() {
    }

    static void merge(Models.Catalog catalog) {
        merge(catalog, null);
    }

    static void merge(Models.Catalog catalog, File file) {
        if (catalog == null) {
            return;
        }
        try {
            // Show a previously successful Live-Extra catalogue immediately.
            // Old cache files contain only Germany and are migrated transparently.
            diagnosticStage = "Katalog: Cache prüfen";
            List<Models.Channel> extraChannels = readCache(file);
            if (extraChannels == null || extraChannels.isEmpty()) {
                diagnosticStage = "Katalog: Online-Erstabfrage";
                extraChannels = new ArrayList<>();

                List<Models.Channel> germany = fetchGermanyFast();
                if (germany != null && !germany.isEmpty()) {
                    extraChannels.addAll(germany);
                }

                // Poland uses the same catalogue source and the host that just worked
                // for Germany. One fast page keeps first launch responsive.
                List<Models.Channel> poland = fetchPolandFast();
                if (poland != null && !poland.isEmpty()) {
                    extraChannels.addAll(poland);
                }

                if (!extraChannels.isEmpty()) {
                    writeCache(file, extraChannels);
                }
            } else {
                lastError = "";
                diagnosticStage = "Katalog: Cache bereit";
            }

            if (extraChannels == null || extraChannels.isEmpty()) {
                return;
            }

            boolean hasGermany = false;
            boolean hasPoland = false;
            for (Models.Channel channel : extraChannels) {
                if (channel == null) continue;
                if (CAT_ID_PL.equals(channel.categoryId)) hasPoland = true;
                else if (CAT_ID.equals(channel.categoryId)) hasGermany = true;
            }

            if (hasGermany) ensureCategory(catalog, CAT_ID, CAT_NAME_DE, 0);
            if (hasPoland) ensureCategory(catalog, CAT_ID_PL, CAT_NAME_PL, hasGermany ? 1 : 0);

            HashSet hashSet = new HashSet();
            int size = 0;
            for (Models.Channel channel : catalog.live) {
                if (channel != null && channel.id != null) {
                    hashSet.add(channel.id);
                }
                if (channel != null && !channel.header && channel.number > size) {
                    size = channel.number;
                }
            }
            for (Models.Channel channel : extraChannels) {
                if (channel != null && channel.id != null && !hashSet.contains(channel.id)) {
                    hashSet.add(channel.id);
                    size++;
                    channel.number = size;
                    catalog.live.add(channel);
                }
            }
        } catch (Exception unused) {
        }
    }

    private static void ensureCategory(Models.Catalog catalog, String id, String name, int preferredIndex) {
        for (Models.Category category : catalog.liveCats) {
            if (category != null && id.equals(category.id)) {
                return;
            }
        }
        int index = Math.max(0, Math.min(preferredIndex, catalog.liveCats.size()));
        catalog.liveCats.add(index, new Models.Category(id, name));
    }

    static boolean refreshCache(File file) {
        try {
            diagnosticStage = "Katalog: Hintergrund-Aktualisierung";
            List<Models.Channel> cached = readCache(file);
            List<Models.Channel> germany = fetchGermany();
            List<Models.Channel> poland = fetchPoland();

            List<Models.Channel> combined = new ArrayList<>();
            if (germany != null && !germany.isEmpty()) {
                combined.addAll(germany);
            } else {
                addCachedCountry(combined, cached, CAT_ID);
            }
            if (poland != null && !poland.isEmpty()) {
                combined.addAll(poland);
            } else {
                addCachedCountry(combined, cached, CAT_ID_PL);
            }

            if (!combined.isEmpty()) {
                writeCache(file, combined);
                diagnosticStage = "Katalog: Hintergrund aktuell";
                return (germany != null && !germany.isEmpty()) || (poland != null && !poland.isEmpty());
            }
        } catch (Throwable unused) {
        }
        return false;
    }

    private static void addCachedCountry(List<Models.Channel> target, List<Models.Channel> cached, String categoryId) {
        if (target == null || cached == null) return;
        for (Models.Channel channel : cached) {
            if (channel != null && categoryId.equals(channel.categoryId)) {
                target.add(channel);
            }
        }
    }

    static String toPlay(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        int lastIndexOf = str.lastIndexOf("/play/");
        if (lastIndexOf >= 0) {
            String substring = str.substring(lastIndexOf + 6);
            int indexOf = substring.indexOf(63);
            if (indexOf >= 0) {
                substring = substring.substring(0, indexOf);
            }
            int indexOf2 = substring.indexOf(47);
            if (indexOf2 >= 0) {
                substring = substring.substring(0, indexOf2);
            }
            if (!substring.isEmpty()) {
                return "https://vavoo.to/vavoo-iptv/play/" + substring;
            }
        }
        return str.replace("https://kool.to", "https://vavoo.to").replace("kool-iptv", "vavoo-iptv");
    }

    static String resolve(String str) {
        diagnosticStage = "Resolve: Start";
        diagnosticResolveHost = "";
        if (str == null || str.isEmpty()) return null;
        if (!isPlayUrl(str) && str.startsWith("http")) {
            return playable(str);
        }

        // Keep the original channel URL unchanged. Only the resolve endpoint
        // is switched between mirrors.
        final String channelUrl = str;
        final String[] resolveHosts = str.startsWith("https://kool.to/")
                ? new String[]{"https://kool.to", "https://vavoo.to"}
                : new String[]{"https://vavoo.to", "https://kool.to"};

        String failure = "Resolve ohne Stream-URL";
        for (String host : resolveHosts) {
            try {
                String sigNow = signature();
                diagnosticAuthHost = host;
                diagnosticStage = (sigNow == null || sigNow.isEmpty())
                        ? "Resolve: keine Signatur"
                        : "Resolve: Anfrage";

                if (sigNow == null || sigNow.isEmpty()) {
                    lastError = "Live-Extra-Anmeldung fehlgeschlagen (Ping/Signatur).";
                    return null;
                }

                JSONObject payload = new JSONObject();
                Models.Channel currentChannel = App.playing;
                boolean polish = currentChannel != null && CAT_ID_PL.equals(currentChannel.categoryId);
                payload.put("language", polish ? "pl" : "de");
                payload.put("region", polish ? "PL" : "DE");
                payload.put("url", channelUrl);
                payload.put("clientVersion", "3.0.2");

                OkPlay.ResolveResponse response = OkPlay.postResolve(
                        host + "/mediahubmx-resolve.json", payload.toString(), sigNow);
                // A cached signature can be rejected before its five-minute TTL expires.
                // Refresh it only for authentication errors, then try this mirror once more.
                if (response.status == 401 || response.status == 403) {
                    invalidateSig();
                    String freshSig = signature();
                    if (freshSig != null && !freshSig.isEmpty()) {
                        response = OkPlay.postResolve(host + "/mediahubmx-resolve.json",
                                payload.toString(), freshSig);
                    }
                }
                String resolved = parseResolve(response.body);
                if (resolved != null && !resolved.isEmpty()) {
                    activeHost = host;
                    diagnosticStage = "Resolve: URL erhalten";
                    lastError = "";
                    try {
                        diagnosticResolveHost = new URL(resolved).getHost();
                    } catch (Throwable ignored) {
                        diagnosticResolveHost = "";
                    }
                    return resolved;
                }

                diagnosticStage = "Resolve: fehlgeschlagen";
                failure = response.failure.isEmpty() ? "Resolve ohne Stream-URL" : response.failure;
                lastError = "Live-Extra-Stream konnte nicht aufgelöst werden (" + failure + ")";
            } catch (Throwable ignored) {
                diagnosticStage = "Resolve: Ausnahme";
                lastError = "Live-Extra-Stream konnte nicht aufgelöst werden (" + failure + ")";
            }
        }
        return null;
    }

    private static String parseResolve(String str) {
        String playable;
        if (str != null && !str.isEmpty()) {
            String trim = str.trim();
            try {
                int i = 0;
                if (trim.startsWith("[")) {
                    JSONArray jSONArray = new JSONArray(trim);
                    while (i < jSONArray.length()) {
                        JSONObject optJSONObject = jSONArray.optJSONObject(i);
                        if (optJSONObject != null && (playable = playable(firstUrl(optJSONObject))) != null) {
                            return playable;
                        }
                        i++;
                    }
                } else if (trim.startsWith("{")) {
                    JSONObject jSONObject = new JSONObject(trim);
                    String playable2 = playable(firstUrl(jSONObject));
                    if (playable2 != null) {
                        return playable2;
                    }
                    JSONArray optJSONArray = jSONObject.optJSONArray("streams");
                    if (optJSONArray == null) {
                        optJSONArray = jSONObject.optJSONArray("urls");
                    }
                    if (optJSONArray != null) {
                        while (i < optJSONArray.length()) {
                            JSONObject optJSONObject2 = optJSONArray.optJSONObject(i);
                            String playable3 = playable(optJSONObject2 != null ? firstUrl(optJSONObject2) : optJSONArray.optString(i, ""));
                            if (playable3 != null) {
                                return playable3;
                            }
                            i++;
                        }
                    }
                } else if (trim.startsWith("http")) {
                    return playable(trim.split("\\s")[0]);
                }
            } catch (Exception unused) {
            }
        }
        return null;
    }

    private static String firstUrl(JSONObject jSONObject) {
        if (jSONObject == null) {
            return "";
        }
        String optString = jSONObject.optString("url", "");
        if (optString.isEmpty()) {
            optString = jSONObject.optString("stream", "");
        }
        return optString.isEmpty() ? jSONObject.optString("src", "") : optString;
    }

    static boolean isPlayUrl(String str) {
        return str != null && (str.contains("vavoo-iptv") || str.contains("kool-iptv"));
    }

    static boolean needsAuth(String str) {
        if (str == null) {
            return false;
        }
        if (isPlayUrl(str) || isCdn(str)) {
            return true;
        }
        Models.Channel channel = App.playing;
        return (channel == null || channel.extraLiveUrl == null || channel.extraLiveUrl.isEmpty()) ? false : true;
    }

    static boolean isCdn(String str) {
        if (str == null) {
            return false;
        }
        return str.contains("vavoo.to") || str.contains("kool.to") || str.contains("ngolpdky") || str.contains("/sunshine/") || str.contains("vavoo-iptv");
    }

    static String playable(String str) {
        if (str == null || !str.startsWith("http")) {
            return null;
        }
        return str;
    }

    private static List<Models.Channel> fetchGermany() {
        return fetchCountry("Germany", "de", "DE", CAT_ID, CAT_NAME_DE);
    }

    private static List<Models.Channel> fetchPoland() {
        return fetchCountry("Poland", "pl", "PL", CAT_ID_PL, CAT_NAME_PL);
    }

    private static List<Models.Channel> fetchCountry(
            String country, String language, String region, String categoryId, String categoryName) {
        String[][] hosts = {
                new String[]{"https://kool.to", "/mediahubmx-catalog.json", "iptv"},
                new String[]{"https://vavoo.to", "/mediahubmx-catalog.json", "iptv"},
                new String[]{"https://kool.to", "/vto-cluster/mediahubmx-catalog.json", "vto-iptv"},
                new String[]{"https://vavoo.to", "/vto-cluster/mediahubmx-catalog.json", "vto-iptv"},
                new String[]{"https://www.vavoo.to", "/mediahubmx-catalog.json", "iptv"}
        };
        for (String[] host : hosts) {
            List<Models.Channel> channels = fetchPage(
                    host[0], host[1], host[2], country, 20,
                    language, region, categoryId, categoryName);
            if (!channels.isEmpty()) {
                activeHost = host[0];
                lastError = "";
                return channels;
            }
        }
        if (lastError == null || lastError.isEmpty()) {
            lastError = "Live Extra momentan nicht erreichbar.";
        }
        return new ArrayList();
    }

    private static List<Models.Channel> fetchGermanyFast() {
        String[][] fastHosts = {
                new String[]{"https://kool.to", "/mediahubmx-catalog.json", "iptv"},
                new String[]{"https://vavoo.to", "/mediahubmx-catalog.json", "iptv"}
        };
        long deadline = System.currentTimeMillis() + 9000L;
        for (String[] host : fastHosts) {
            if (System.currentTimeMillis() >= deadline) break;
            List<Models.Channel> firstPage = fetchPage(
                    host[0], host[1], host[2], "Germany", 1,
                    "de", "DE", CAT_ID, CAT_NAME_DE);
            if (!firstPage.isEmpty()) {
                activeHost = host[0];
                lastError = "";
                return firstPage;
            }
        }
        lastError = "Live Extra momentan nicht erreichbar. Gespeicherte Sender werden verwendet, sobald vorhanden.";
        return new ArrayList();
    }

    private static List<Models.Channel> fetchPolandFast() {
        // Do not double the startup timeout. Reuse the mirror that worked for Germany
        // and fetch only the first Polish page; the background refresh fills the rest.
        String host = activeHost == null || activeHost.isEmpty() ? "https://kool.to" : activeHost;
        List<Models.Channel> firstPage = fetchPage(
                host, "/mediahubmx-catalog.json", "iptv", "Poland", 1,
                "pl", "PL", CAT_ID_PL, CAT_NAME_PL);
        if (!firstPage.isEmpty()) {
            lastError = "";
            return firstPage;
        }
        return new ArrayList();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r10v11, types: [org.json.JSONArray] */
    /* JADX WARN: Type inference failed for: r10v13 */
    /* JADX WARN: Type inference failed for: r10v14 */
    /* JADX WARN: Type inference failed for: r15v3 */
    /* JADX WARN: Type inference failed for: r15v4, types: [int] */
    /* JADX WARN: Type inference failed for: r15v6 */
    private static List<Models.Channel> fetchPage(
            String str, String str2, String str3, String str4, int maxPages,
            String language, String region, String categoryId, String categoryName) {
        JSONObject jSONObject;
        JSONArray optJSONArray;
        int optInt;
        String str5;
        String str6;
        Set set;
        JSONArray obj;
        String str7 = str3;
        String str8 = "name";
        String str9 = "id";
        ArrayList arrayList = new ArrayList();
        Set hashSet = new HashSet();
        boolean z = false;
        int i = 0;
        int i2 = 0;
        while (i < Math.max(1, maxPages)) {
            try {
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.put("group", str4);
                JSONObject jSONObject3 = new JSONObject();
                jSONObject3.put("language", language);
                jSONObject3.put("region", region);
                jSONObject3.put("catalogId", str7);
                jSONObject3.put(str9, str7);
                jSONObject3.put("adult", z);
                jSONObject3.put("search", "");
                jSONObject3.put("sort", str8);
                jSONObject3.put("filter", jSONObject2);
                jSONObject3.put("cursor", i2);
                jSONObject3.put("clientVersion", "3.0.2");
                String post = post(str + str2, jSONObject3.toString(), true);
                if (post == null || post.isEmpty() || !post.trim().startsWith("{") || (optJSONArray = (jSONObject = new JSONObject(post)).optJSONArray("items")) == null || optJSONArray.length() == 0) {
                    break;
                }
                JSONArray r10 = optJSONArray;
                for (int r15 = 0; r15 < r10.length(); r15++) {
                    JSONObject optJSONObject = r10.optJSONObject(r15);
                    if (optJSONObject != null) {
                        String optString = optJSONObject.optString("url", "");
                        String optString2 = optJSONObject.optString(str8, "");
                        if (!optString.isEmpty() && !optString2.isEmpty() && !hashSet.contains(optString)) {
                            hashSet.add(optString);
                            str5 = str8;
                            JSONObject optJSONObject2 = optJSONObject.optJSONObject("ids");
                            String optString3 = optJSONObject2 != null ? optJSONObject2.optString(str9, optString) : optString;
                            str6 = str9;
                            Models.Channel channel = new Models.Channel();
                            set = hashSet;
                            obj = r10;
                            channel.id = categoryId + ":" + optString3;
                            channel.name = Text.clean(optString2);
                            channel.categoryId = categoryId;
                            channel.categoryName = categoryName;
                            channel.logo = optJSONObject.optString("logo", "");
                            channel.extraLiveUrl = optString;
                            channel.hlsUrl = optString;
                            arrayList.add(channel);
                            str8 = str5;
                            str9 = str6;
                            hashSet = set;
                            r10 = obj;
                        }
                    }
                    str5 = str8;
                    str6 = str9;
                    set = hashSet;
                    obj = r10;
                    str8 = str5;
                    str9 = str6;
                    hashSet = set;
                    r10 = obj;
                }
                String str10 = str8;
                String str11 = str9;
                Set set2 = hashSet;
                if (jSONObject.isNull("nextCursor") || (optInt = jSONObject.optInt("nextCursor", -1)) < 0 || optInt == i2) {
                    break;
                }
                i++;
                i2 = optInt;
                str8 = str10;
                str9 = str11;
                hashSet = set2;
                z = false;
                str7 = str3;
            } catch (Exception unused) {
            }
        }
        return arrayList;
    }

    static String sigNow() {
        return signature();
    }

    static void invalidateSig() {
        sig = null;
        sigAt = 0L;
    }

    static boolean isAvailable() {
        String s = signature();
        return s != null && !s.isEmpty();
    }

    private static String signature() {
        String empty = "";
        diagnosticStage = "Auth: Signatur prüfen";
        if (sig != null && System.currentTimeMillis() - sigAt < 480000L) {
            diagnosticStage = "Auth: Cache-Signatur";
            return sig;
        }
        String pingBody = pingBody();
        for (String endpoint : PINGS) {
            try {
                diagnosticAuthHost = endpoint;
                diagnosticStage = "Auth: Ping";
                String response = post(endpoint, pingBody, false);
                if (response != null && !response.isEmpty()) {
                    JSONObject obj = new JSONObject(response);
                    String candidate = obj.optString("addonSig", empty);
                    if (candidate.isEmpty()) candidate = obj.optString("signed", empty);
                    if (!candidate.isEmpty()) {
                        // The ping endpoint already returns the valid signed token.
                        // Never rewrite its embedded data; doing so invalidates the signature.
                        sig = candidate;
                        sigAt = System.currentTimeMillis();
                        lastError = "";
                        diagnosticStage = "Auth: OK";
                        return sig;
                    }
                }
            } catch (Exception unused) {
            }
        }
        diagnosticStage = "Auth: fehlgeschlagen";
        lastError = "Live-Extra-Anmeldung fehlgeschlagen (Ping/Signatur).";
        return sig == null ? "" : sig;
    }

    static String diagnosticSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(diagnosticStage == null || diagnosticStage.isEmpty() ? "—" : diagnosticStage);
        if (diagnosticAuthHost != null && !diagnosticAuthHost.isEmpty()) {
            try {
                String host = new URL(diagnosticAuthHost).getHost();
                if (host != null && !host.isEmpty()) sb.append(" · Auth ").append(host);
            } catch (Throwable ignored) {
            }
        }
        if (diagnosticResolveHost != null && !diagnosticResolveHost.isEmpty()) {
            sb.append(" · Ziel ").append(diagnosticResolveHost);
        }
        if (lastError != null && !lastError.isEmpty()) {
            sb.append(" · ").append(lastError);
        }
        return sb.toString();
    }

    private static String pingBody() {
        try {
            JSONObject device = new JSONObject();
            device.put("type", "Handset");
            device.put("brand", Build.BRAND == null ? "android" : Build.BRAND);
            device.put("model", Build.MODEL == null ? "Android" : Build.MODEL);
            device.put("name", "streamy2");
            device.put("uniqueId", CLIENT_ID);

            JSONObject os = new JSONObject();
            os.put("name", "android");
            os.put("version", Build.VERSION.RELEASE);
            JSONArray abis = new JSONArray();
            if (Build.SUPPORTED_ABIS != null) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (abi != null && !abi.isEmpty()) abis.put(abi);
                }
            }
            os.put("abis", abis);
            os.put("host", "android");

            JSONObject app = new JSONObject();
            app.put("platform", "android");
            app.put("version", "3.1.21");
            app.put("buildId", "289515000");
            app.put("engine", "hbc85");
            app.put("installer", "com.android.vending");
            app.put("signatures", new JSONArray()
                    .put("6e8a975e3cbf07d5de823a760d4c2547f86c1403105020adee5de67ac510999e"));

            JSONObject version = new JSONObject();
            version.put("package", "tv.vavoo.app");
            version.put("binary", "3.1.21");
            version.put("js", "3.1.21");

            JSONObject metadata = new JSONObject();
            metadata.put("device", device);
            metadata.put("os", os);
            metadata.put("app", app);
            metadata.put("version", version);

            JSONObject proxy = new JSONObject();
            proxy.put("supported", new JSONArray().put("ss"));
            proxy.put("engine", "Mu");
            proxy.put("enabled", false);
            proxy.put("autoServer", true);

            JSONObject iap = new JSONObject();
            iap.put("supported", false);

            long now = System.currentTimeMillis();
            JSONObject body = new JSONObject();
            body.put("token", "");
            body.put("reason", "app-focus");
            body.put("locale", "de");
            body.put("theme", "dark");
            body.put("metadata", metadata);
            body.put("appFocusTime", 0);
            body.put("playerActive", false);
            body.put("playDuration", 0);
            body.put("devMode", false);
            body.put("hasAddon", true);
            body.put("castConnected", false);
            body.put("package", "tv.vavoo.app");
            body.put("version", "3.1.21");
            body.put("process", "app");
            body.put("firstAppStart", CLIENT_STARTED_AT);
            body.put("lastAppStart", now);
            body.put("ipLocation", JSONObject.NULL);
            body.put("adblockEnabled", true);
            body.put("proxy", proxy);
            body.put("iap", iap);
            return body.toString();
        } catch (Exception unused) {
            return "{}";
        }
    }

    private static String postMozilla(String str, String str2) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setConnectTimeout(8000);
        httpURLConnection.setReadTimeout(12000);
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
        httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        httpURLConnection.setRequestProperty(HttpHeaders.ORIGIN, "https://vavoo.to");
        httpURLConnection.setRequestProperty(HttpHeaders.REFERER, "https://vavoo.to/");
        byte[] bytes = str2.getBytes(StandardCharsets.UTF_8);
        httpURLConnection.setFixedLengthStreamingMode(bytes.length);
        try {
            OutputStream outputStream = httpURLConnection.getOutputStream();
            try {
                outputStream.write(bytes);
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
            if (httpURLConnection.getResponseCode() >= 400) {
                return null;
            }
            InputStream inputStream = httpURLConnection.getInputStream();
            if (inputStream == null) {
                return null;
            }
            String contentEncoding = httpURLConnection.getContentEncoding();
            if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            try {
                StringBuilder sb = new StringBuilder();
                while (true) {
                    String readLine = bufferedReader.readLine();
                    if (readLine == null) {
                        return sb.toString();
                    }
                    sb.append(readLine);
                }
            } finally {
                bufferedReader.close();
            }
        } finally {
            httpURLConnection.disconnect();
        }
    }

    private static String post(String str, String str2, boolean z) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setConnectTimeout(6001);
        httpURLConnection.setReadTimeout(10000);
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "gzip");
        if (z) {
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "MediaHubMX/2");
            httpURLConnection.setRequestProperty(HttpHeaders.ORIGIN, "https://vavoo.to");
            httpURLConnection.setRequestProperty(HttpHeaders.REFERER, "https://vavoo.to/");
            String signature = signature();
            if (signature != null && !signature.isEmpty()) {
                httpURLConnection.setRequestProperty("mediahubmx-signature", signature);
            }
        } else {
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "okhttp/4.11.0");
        }
        byte[] bytes = str2.getBytes(StandardCharsets.UTF_8);
        httpURLConnection.setFixedLengthStreamingMode(bytes.length);
        try {
            OutputStream outputStream = httpURLConnection.getOutputStream();
            try {
                outputStream.write(bytes);
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
            if (httpURLConnection.getResponseCode() >= 400) {
                return null;
            }
            InputStream inputStream = httpURLConnection.getInputStream();
            if (inputStream == null) {
                return null;
            }
            String contentEncoding = httpURLConnection.getContentEncoding();
            if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            try {
                StringBuilder sb = new StringBuilder();
                while (true) {
                    String readLine = bufferedReader.readLine();
                    if (readLine == null) {
                        return sb.toString();
                    }
                    sb.append(readLine);
                }
            } finally {
                bufferedReader.close();
            }
        } finally {
            httpURLConnection.disconnect();
        }
    }

    private static void writeCache(File file, List<Models.Channel> list) {
        if (file == null || list == null || list.isEmpty()) {
            return;
        }
        try {
            JSONArray jSONArray = new JSONArray();
            for (Models.Channel channel : list) {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("id", channel.id);
                jSONObject.put("name", channel.name);
                jSONObject.put("url", channel.extraLiveUrl != null ? channel.extraLiveUrl : channel.hlsUrl);
                jSONObject.put("categoryId", CAT_ID_PL.equals(channel.categoryId) ? CAT_ID_PL : CAT_ID);
                jSONObject.put("categoryName", CAT_ID_PL.equals(channel.categoryId) ? CAT_NAME_PL : CAT_NAME_DE);
                jSONArray.put(jSONObject);
            }
            byte[] bytes = jSONArray.toString().getBytes(StandardCharsets.UTF_8);
            File file2 = new File(file.getPath() + ".tmp");
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(bytes);
            fileOutputStream.close();
            if (file.exists()) {
                file.delete();
            }
            file2.renameTo(file);
        } catch (Exception unused) {
        }
    }

    private static List<Models.Channel> readCache(File file) {
        ArrayList arrayList = new ArrayList();
        if (file != null && file.exists() && file.length() >= 8) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                int length = (int) file.length();
                byte[] bArr = new byte[length];
                int i = 0;
                while (i < length) {
                    int read = fileInputStream.read(bArr, i, length - i);
                    if (read < 0) {
                        break;
                    }
                    i += read;
                }
                fileInputStream.close();
                JSONArray jSONArray = new JSONArray(new String(bArr, 0, i, StandardCharsets.UTF_8));
                HashSet hashSet = new HashSet();
                for (int i2 = 0; i2 < jSONArray.length(); i2++) {
                    JSONObject optJSONObject = jSONArray.optJSONObject(i2);
                    if (optJSONObject != null) {
                        String optString = optJSONObject.optString("url", "");
                        String optString2 = optJSONObject.optString("name", "");
                        if (!optString.isEmpty() && !optString2.isEmpty() && !hashSet.contains(optString)) {
                            hashSet.add(optString);
                            String categoryId = optJSONObject.optString("categoryId", CAT_ID);
                            if (!CAT_ID_PL.equals(categoryId)) categoryId = CAT_ID;
                            String categoryName = CAT_ID_PL.equals(categoryId) ? CAT_NAME_PL : CAT_NAME_DE;

                            Models.Channel channel = new Models.Channel();
                            channel.id = optJSONObject.optString("id", categoryId + ":" + optString);
                            channel.name = optString2;
                            channel.categoryId = categoryId;
                            channel.categoryName = categoryName;
                            channel.extraLiveUrl = optString;
                            channel.hlsUrl = optString;
                            arrayList.add(channel);
                        }
                    }
                }
            } catch (Exception unused) {
            }
        }
        return arrayList;
    }
}
