package app.streamy2;

import android.os.Build;
import android.util.Base64;
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
    private static volatile String sig;
    private static volatile long sigAt;
    private static final String[] HOSTS = {"https://kool.to", "https://vavoo.to", "https://www.vavoo.to"};
    private static final String[] PINGS = {"https://www.vavoo.tv/api/app/ping", "https://www.vypn.net/api/app/ping"};
    private static final String VYPN_PACKAGE = "net.vypn.app";
    private static final String VYPN_VERSION = "1.4.1";
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
            // Network refreshes must never block the whole tab for tens of seconds.
            diagnosticStage = "Katalog: Cache prüfen";
            List<Models.Channel> fetchGermany = readCache(file);
            if (fetchGermany == null || fetchGermany.isEmpty()) {
                diagnosticStage = "Katalog: Online-Erstabfrage";
                fetchGermany = fetchGermanyFast();
                if (fetchGermany != null && !fetchGermany.isEmpty()) {
                    writeCache(file, fetchGermany);
                }
            } else {
                lastError = "";
                diagnosticStage = "Katalog: Cache bereit";
            }
            if (fetchGermany != null && !fetchGermany.isEmpty()) {
                Iterator<Models.Category> it = catalog.liveCats.iterator();
                while (true) {
                    if (it.hasNext()) {
                        if (CAT_ID.equals(it.next().id)) {
                            break;
                        }
                    } else {
                        catalog.liveCats.add(0, new Models.Category(CAT_ID, "Live Extra Deutschland"));
                        break;
                    }
                }
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
                for (Models.Channel channel2 : fetchGermany) {
                    if (channel2.id != null && !hashSet.contains(channel2.id)) {
                        hashSet.add(channel2.id);
                        size++;
                        channel2.number = size;
                        catalog.live.add(channel2);
                    }
                }
            }
        } catch (Exception unused) {
        }
    }

    static boolean refreshCache(File file) {
        try {
            diagnosticStage = "Katalog: Hintergrund-Aktualisierung";
            List<Models.Channel> fresh = fetchGermany();
            if (fresh != null && !fresh.isEmpty()) {
                writeCache(file, fresh);
                diagnosticStage = "Katalog: Hintergrund aktuell";
                return true;
            }
        } catch (Throwable unused) {
        }
        return false;
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
        String parseResolve = null;
        diagnosticStage = "Resolve: Start";
        diagnosticResolveHost = "";
        if (str != null && !str.isEmpty()) {
            if (!isPlayUrl(str) && str.startsWith("http")) {
                return playable(str);
            }
            String replace = str.replace("https://vavoo.to", "https://kool.to").replace("vavoo-iptv", "kool-iptv");
            String replace2 = str.replace("https://kool.to", "https://vavoo.to").replace("kool-iptv", "vavoo-iptv");
            String[][] strArr = {new String[]{"https://kool.to", replace}, new String[]{"https://kool.to", replace2}, new String[]{"https://vavoo.to", replace2}};
            for (int i = 0; i < 3; i++) {
                String[] strArr2 = strArr[i];
                try {
                    JSONObject jSONObject = new JSONObject();
                    jSONObject.put("language", "de");
                    jSONObject.put("region", "DE");
                    jSONObject.put("url", strArr2[1]);
                    jSONObject.put("clientVersion", "3.0.2");
                    String sig = signature();
                    diagnosticAuthHost = strArr2[0];
                    diagnosticStage = (sig == null || sig.isEmpty()) ? "Resolve: keine Signatur" : "Resolve: Anfrage";
                    parseResolve = parseResolve(OkPlay.postJson(strArr2[0] + "/mediahubmx-resolve.json", jSONObject.toString(), sig));
                    if (parseResolve == null) {
                        diagnosticStage = "Resolve: fehlgeschlagen";
                        lastError = "Live-Extra-Stream konnte nicht aufgelöst werden (Resolve)";
                    }
                } catch (Exception unused) {
                }
                if (parseResolve != null) {
                    activeHost = strArr2[0];
                    diagnosticStage = "Resolve: URL erhalten";
                    try {
                        diagnosticResolveHost = new URL(parseResolve).getHost();
                    } catch (Throwable ignored) {
                        diagnosticResolveHost = "";
                    }
                    return parseResolve;
                }
                continue;
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
        String[][] strArr = {new String[]{"https://kool.to", "/mediahubmx-catalog.json", "iptv", "Germany"}, new String[]{"https://vavoo.to", "/mediahubmx-catalog.json", "iptv", "Germany"}, new String[]{"https://kool.to", "/vto-cluster/mediahubmx-catalog.json", "vto-iptv", "Germany"}, new String[]{"https://vavoo.to", "/vto-cluster/mediahubmx-catalog.json", "vto-iptv", "Germany"}, new String[]{"https://www.vavoo.to", "/mediahubmx-catalog.json", "iptv", "Germany"}};
        for (int i = 0; i < 5; i++) {
            String[] strArr2 = strArr[i];
            List<Models.Channel> fetchPage = fetchPage(strArr2[0], strArr2[1], strArr2[2], strArr2[3], 20);
            if (!fetchPage.isEmpty()) {
                activeHost = strArr2[0];
                lastError = "";
                return fetchPage;
            }
        }
        if (lastError == null || lastError.isEmpty()) {
            lastError = "Live Extra momentan nicht erreichbar.";
        }
        return new ArrayList();
    }

    private static List<Models.Channel> fetchGermanyFast() {
        String[][] fastHosts = {
                new String[]{"https://kool.to", "/mediahubmx-catalog.json", "iptv", "Germany"},
                new String[]{"https://vavoo.to", "/mediahubmx-catalog.json", "iptv", "Germany"}
        };
        long deadline = System.currentTimeMillis() + 9000L;
        for (String[] host : fastHosts) {
            if (System.currentTimeMillis() >= deadline) break;
            List<Models.Channel> firstPage = fetchPage(host[0], host[1], host[2], host[3], 1);
            if (!firstPage.isEmpty()) {
                activeHost = host[0];
                lastError = "";
                return firstPage;
            }
        }
        lastError = "Live Extra momentan nicht erreichbar. Gespeicherte Sender werden verwendet, sobald vorhanden.";
        return new ArrayList();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r10v11, types: [org.json.JSONArray] */
    /* JADX WARN: Type inference failed for: r10v13 */
    /* JADX WARN: Type inference failed for: r10v14 */
    /* JADX WARN: Type inference failed for: r15v3 */
    /* JADX WARN: Type inference failed for: r15v4, types: [int] */
    /* JADX WARN: Type inference failed for: r15v6 */
    private static List<Models.Channel> fetchPage(String str, String str2, String str3, String str4, int maxPages) {
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
                jSONObject3.put("language", "de");
                jSONObject3.put("region", "DE");
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
                            channel.id = "extra_live:" + optString3;
                            channel.name = Text.clean(optString2);
                            channel.categoryId = CAT_ID;
                            channel.categoryName = "Live Extra Deutschland";
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
        if (sig != null && System.currentTimeMillis() - sigAt < 300000L) {
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
                    if (candidate.isEmpty()) candidate = obj.optString("mhub", empty);
                    if (candidate.isEmpty()) candidate = obj.optString("signed", empty);
                    if (!candidate.isEmpty()) {
                        String publicIp = externalIp();
                        if (publicIp != null && !publicIp.isEmpty()) {
                            candidate = rewriteAddonSigIp(candidate, publicIp);
                        }
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
        if (sig == null || sig.isEmpty()) {
            diagnosticStage = "Auth: fehlgeschlagen";
            lastError = "Live-Extra-Anmeldung fehlgeschlagen (Ping/Signatur).";
        }
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
            long now = System.currentTimeMillis();

            JSONObject device = new JSONObject();
            device.put("type", "phone");
            device.put("uniqueId", UUID.randomUUID().toString());

            JSONObject os = new JSONObject();
            os.put("name", "android");
            os.put("version", "14");
            os.put("abis", new JSONArray().put("arm64-v8a"));
            os.put("host", "android");

            JSONObject app = new JSONObject();
            app.put("platform", "android");

            JSONObject version = new JSONObject();
            version.put("package", VYPN_PACKAGE);
            version.put("binary", VYPN_VERSION);
            version.put("js", VYPN_VERSION);

            JSONObject metadata = new JSONObject();
            metadata.put("device", device);
            metadata.put("os", os);
            metadata.put("app", app);
            metadata.put("version", version);

            JSONObject proxy = new JSONObject();
            proxy.put("supported", new JSONArray().put("ss"));
            proxy.put("engine", "Mu");
            proxy.put("ssVersion", "2022");
            proxy.put("enabled", false);
            proxy.put("autoServer", true);
            proxy.put("id", "");

            JSONObject iap = new JSONObject();
            iap.put("supported", false);
            iap.put("error", "");

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
            body.put("package", VYPN_PACKAGE);
            body.put("version", VYPN_VERSION);
            body.put("process", "app");
            body.put("firstAppStart", now - 86400000L);
            body.put("lastAppStart", now);
            body.put("ipLocation", JSONObject.NULL);
            body.put("adblockEnabled", true);
            body.put("migrationApplied", false);
            body.put("migrationTargetInstalled", false);
            body.put("proxy", proxy);
            body.put("iap", iap);
            return body.toString();
        } catch (Exception unused) {
            return "{}";
        }
    }

    private static String externalIp() {
        String[] urls = {
                "https://api.ipify.org",
                "https://v4.ident.me",
                "https://checkip.amazonaws.com"
        };
        for (String url : urls) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setRequestProperty(HttpHeaders.USER_AGENT, "okhttp/4.11.0");
                InputStream in = connection.getResponseCode() >= 400
                        ? connection.getErrorStream() : connection.getInputStream();
                if (in == null) continue;
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String value = reader.readLine();
                reader.close();
                if (value != null) {
                    value = value.trim();
                    if (!value.isEmpty() && value.length() <= 64) return value;
                }
            } catch (Exception unused) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return "";
    }

    private static String rewriteAddonSigIp(String signature, String publicIp) {
        if (signature == null || signature.isEmpty() || publicIp == null || publicIp.isEmpty()) {
            return signature;
        }
        try {
            String padded = signature;
            int mod = padded.length() % 4;
            if (mod != 0) {
                StringBuilder sb = new StringBuilder(padded);
                for (int i = mod; i < 4; i++) sb.append('=');
                padded = sb.toString();
            }
            byte[] decoded = Base64.decode(padded, Base64.DEFAULT);
            JSONObject root = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            if (!root.has("data")) return signature;

            String dataRaw = root.optString("data", "");
            if (dataRaw.isEmpty()) return signature;
            JSONObject data = new JSONObject(dataRaw);

            JSONArray ips = data.optJSONArray("ips");
            JSONArray merged = new JSONArray();
            merged.put(publicIp);
            if (ips != null) {
                for (int i = 0; i < ips.length(); i++) {
                    String ip = ips.optString(i, "");
                    if (!ip.isEmpty() && !publicIp.equals(ip)) merged.put(ip);
                }
            }
            data.put("ips", merged);
            if (data.has("ip")) data.put("ip", publicIp);
            root.put("data", data.toString());

            return Base64.encodeToString(
                    root.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
        } catch (Exception unused) {
            return signature;
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
                            Models.Channel channel = new Models.Channel();
                            channel.id = optJSONObject.optString("id", "extra_live:" + optString);
                            channel.name = optString2;
                            channel.categoryId = CAT_ID;
                            channel.categoryName = "Live Extra Deutschland";
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
