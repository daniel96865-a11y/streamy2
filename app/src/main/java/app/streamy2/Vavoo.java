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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class Vavoo {
    static final String CAT_ID = "vavoo";
    private static volatile String sig;
    private static volatile long sigAt;
    private static final String[] HOSTS = {"https://kool.to", "https://vavoo.to", "https://www.vavoo.to"};
    private static final String[] PINGS = {"https://www.vavoo.tv/api/app/ping", "https://www.vavoo.tv/api/box/ping2"};
    static volatile String lastError = "";
    static final String EPG_URL = "https://epg.lat/files/de.xml.gz";
    static final String[] EPG_URLS = {EPG_URL, "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz"};
    private static volatile String activeHost = "https://kool.to";

    static /* synthetic */ boolean lambda$trustSsl$0(String str, SSLSession sSLSession) {
        return true;
    }

    Vavoo() {
    }

    static void merge(Models.Catalog catalog) {
        merge(catalog, null);
    }

    static void merge(Models.Catalog catalog, File file) {
        if (catalog == null) {
            return;
        }
        try {
            List<Models.Channel> fetchGermany = fetchGermany();
            if (fetchGermany.isEmpty()) {
                fetchGermany = readCache(file);
            } else {
                writeCache(file, fetchGermany);
            }
            if (fetchGermany != null && !fetchGermany.isEmpty()) {
                Iterator<Models.Category> it = catalog.liveCats.iterator();
                while (true) {
                    if (it.hasNext()) {
                        if (CAT_ID.equals(it.next().id)) {
                            break;
                        }
                    } else {
                        catalog.liveCats.add(0, new Models.Category(CAT_ID, "Vavoo Deutschland"));
                        break;
                    }
                }
                HashSet hashSet = new HashSet();
                for (Models.Channel channel : catalog.live) {
                    if (channel != null && channel.id != null) {
                        hashSet.add(channel.id);
                    }
                }
                int size = catalog.live.size();
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
                    String sig = signature();
                    parseResolve = parseResolve(OkPlay.postJson(strArr2[0] + "/mediahubmx-resolve.json", jSONObject.toString(), sig));
                    if (parseResolve == null) {
                        lastError = "Vavoo-Stream konnte nicht aufgelöst werden (Resolve)";
                    }
                } catch (Exception unused) {
                }
                if (parseResolve != null) {
                    activeHost = strArr2[0];
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
        return (channel == null || channel.vavooUrl == null || channel.vavooUrl.isEmpty()) ? false : true;
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
            List<Models.Channel> fetchPage = fetchPage(strArr2[0], strArr2[1], strArr2[2], strArr2[3]);
            if (!fetchPage.isEmpty()) {
                activeHost = strArr2[0];
                lastError = "";
                return fetchPage;
            }
        }
        if (lastError == null || lastError.isEmpty()) {
            lastError = "Vavoo-Katalog leer — Host/Signatur prüfen.";
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
    private static List<Models.Channel> fetchPage(String str, String str2, String str3, String str4) {
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
        while (i < 20) {
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
                jSONObject3.put("clientVersion", "3.1.21");
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
                            channel.id = "vavoo:" + optString3;
                            channel.name = Text.clean(optString2);
                            channel.categoryId = CAT_ID;
                            channel.categoryName = "Vavoo Deutschland";
                            channel.logo = optJSONObject.optString("logo", "");
                            channel.vavooUrl = optString;
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
        String str = "";
        if (sig != null && System.currentTimeMillis() - sigAt < 480000) {
            return sig;
        }
        String pingBody = pingBody();
        for (String str2 : PINGS) {
            try {
                String post = post(str2, pingBody, false);
                if (post != null && !post.isEmpty()) {
                    JSONObject jSONObject = new JSONObject(post);
                    String optString = jSONObject.optString("addonSig", str);
                    if (optString.isEmpty()) {
                        optString = jSONObject.optString("signed", str);
                    }
                    if (!optString.isEmpty()) {
                        sig = optString;
                        sigAt = System.currentTimeMillis();
                        return sig;
                    }
                    continue;
                }
            } catch (Exception unused) {
            }
        }
        if (sig == null || sig.isEmpty()) {
            lastError = "Vavoo-Anmeldung fehlgeschlagen (Ping/Signatur). Netzwerk prüfen oder später erneut.";
        }
        return sig == null ? "" : sig;
    }

    static void trustSsl() {
        try {
            TrustManager[] trustManagerArr = {new X509TrustManager() { // from class: app.streamy2.Vavoo.1
                @Override // javax.net.ssl.X509TrustManager
                public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) {
                }

                @Override // javax.net.ssl.X509TrustManager
                public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) {
                }

                @Override // javax.net.ssl.X509TrustManager
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext sSLContext = SSLContext.getInstance("TLS");
            sSLContext.init(null, trustManagerArr, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sSLContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() { // from class: app.streamy2.Vavoo$$ExternalSyntheticLambda0
                @Override // javax.net.ssl.HostnameVerifier
                public final boolean verify(String str, SSLSession sSLSession) {
                    return Vavoo.lambda$trustSsl$0(str, sSLSession);
                }
            });
        } catch (Throwable unused) {
        }
    }

    private static String pingBody() {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("type", "Handset");
            jSONObject.put("brand", "google");
            jSONObject.put("model", "Pixel");
            jSONObject.put("name", "streamy2");
            jSONObject.put("uniqueId", "s2" + Build.ID);
            JSONObject jSONObject2 = new JSONObject();
            jSONObject2.put("name", "android");
            jSONObject2.put("version", Build.VERSION.RELEASE);
            JSONObject jSONObject3 = new JSONObject();
            jSONObject3.put("platform", "android");
            jSONObject3.put("version", "3.1.21");
            jSONObject3.put("buildId", "289515000");
            jSONObject3.put("engine", "hbc85");
            JSONObject jSONObject4 = new JSONObject();
            jSONObject4.put("package", "tv.vavoo.app");
            jSONObject4.put("binary", "3.1.21");
            jSONObject4.put("js", "3.1.21");
            JSONObject jSONObject5 = new JSONObject();
            jSONObject5.put("device", jSONObject);
            jSONObject5.put("os", jSONObject2);
            jSONObject5.put("app", jSONObject3);
            jSONObject5.put("version", jSONObject4);
            JSONObject jSONObject6 = new JSONObject();
            jSONObject6.put("supported", new JSONArray().put("ss").put("openvpn"));
            jSONObject6.put("engine", "ss");
            jSONObject6.put("ssVersion", 1);
            jSONObject6.put("enabled", true);
            jSONObject6.put("autoServer", true);
            jSONObject6.put("id", "de-fra");
            JSONObject jSONObject7 = new JSONObject();
            jSONObject7.put("token", "");
            jSONObject7.put("reason", "app-blur");
            jSONObject7.put("locale", "de");
            jSONObject7.put("theme", "dark");
            jSONObject7.put("metadata", jSONObject5);
            jSONObject7.put("hasAddon", true);
            jSONObject7.put("castConnected", false);
            jSONObject7.put("package", "tv.vavoo.app");
            jSONObject7.put("version", "3.1.21");
            jSONObject7.put("process", "app");
            jSONObject7.put("firstAppStart", 1743962904623L);
            jSONObject7.put("lastAppStart", System.currentTimeMillis());
            jSONObject7.put("adblockEnabled", true);
            jSONObject7.put("proxy", jSONObject6);
            JSONArray jSONArray = new JSONArray();
            jSONArray.put("6e8a975e3cbf07d5de823a760d4c2547f86c1403105020adee5de67ac510999e");
            jSONObject3.put("signatures", jSONArray);
            jSONObject3.put("installer", "com.android.vending");
            return jSONObject7.toString();
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
        OutputStream outputStream = httpURLConnection.getOutputStream();
        try {
            outputStream.write(bytes);
            if (outputStream != null) {
                outputStream.close();
            }
            if (httpURLConnection.getResponseCode() >= 400) {
                httpURLConnection.disconnect();
                return null;
            }
            InputStream inputStream = httpURLConnection.getInputStream();
            if (inputStream == null) {
                httpURLConnection.disconnect();
                return null;
            }
            String contentEncoding = httpURLConnection.getContentEncoding();
            if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    bufferedReader.close();
                    httpURLConnection.disconnect();
                    return sb.toString();
                }
                sb.append(readLine);
            }
        } catch (Throwable th) {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
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
        OutputStream outputStream = httpURLConnection.getOutputStream();
        try {
            outputStream.write(bytes);
            if (outputStream != null) {
                outputStream.close();
            }
            if (httpURLConnection.getResponseCode() >= 400) {
                httpURLConnection.disconnect();
                return null;
            }
            InputStream inputStream = httpURLConnection.getInputStream();
            if (inputStream == null) {
                httpURLConnection.disconnect();
                return null;
            }
            String contentEncoding = httpURLConnection.getContentEncoding();
            if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    bufferedReader.close();
                    httpURLConnection.disconnect();
                    return sb.toString();
                }
                sb.append(readLine);
            }
        } catch (Throwable th) {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
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
                jSONObject.put("url", channel.vavooUrl != null ? channel.vavooUrl : channel.hlsUrl);
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
                            channel.id = optJSONObject.optString("id", "vavoo:" + optString);
                            channel.name = optString2;
                            channel.categoryId = CAT_ID;
                            channel.categoryName = "Vavoo Deutschland";
                            channel.vavooUrl = optString;
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
