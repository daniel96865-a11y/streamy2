package app.streamy2;

import android.util.Base64;
import com.google.common.net.HttpHeaders;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class Voe {
    private static final Pattern JSON_SCRIPT = Pattern.compile("<script\\s+type=\"application/json\">(.*?)</script>", 34);
    private static final Pattern HLS = Pattern.compile("\"(?:hls|source|mp4|file)\"\\s*:\\s*\"(https?:[^\"\\\\]+)\"");
    private static final Pattern M3U8 = Pattern.compile("https?:[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*");
    private static final Pattern JS_REDIRECT = Pattern.compile(
            "(?:window\\.)?location(?:\\.href)?\\s*=\\s*['\"](https?://[^'\"]+)['\"]");
    private static final String[] HINTS = {
            "voe.sx", "voe.", "voe-network", "johnfullwonder", "johnbeyondnation",
            "jilliandescribecompany", "mikaylaarealike", "christopheruntilpoint",
            "walterprettytheir", "crystaltreatmenteast", "lauradaydo", "lancewhosedifficult",
            "dianaavoidthey", "jefferycontrolmodel", "charlestoughrace", "richardquestionbuilding",
            "jessicayeahcatch", "juliewomanwish", "rebeccapracticeloss", "stevenfamilyedge",
            "nathanfromsubject", "donaldlineargroup", "tracylocalschool", "eugenemakedraw",
            "cloudwindow-route"
    };
    /** Live mirrors first — voe.sx /e/ is DDoS-Guard 403; player sits on rotating CDN hosts. */
    private static final String[] ALIASES = {
            "https://johnfullwonder.com",
            "https://johnbeyondnation.com",
            "https://tracylocalschool.com",
            "https://eugenemakedraw.com",
            "https://jilliandescribecompany.com",
            "https://voe.sx"
    };

    Voe() {
    }

    static boolean isVoe(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        for (String str2 : HINTS) {
            if (lowerCase.contains(str2)) {
                return true;
            }
        }
        return false;
    }

    static String extract(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        if (str.startsWith("//")) {
            str = "https:" + str;
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String pathOf = pathOf(str);
        if (pathOf != null && !pathOf.isEmpty()) {
            for (String alias : ALIASES) {
                candidates.add(alias + pathOf);
            }
        }
        candidates.add(str);
        for (String cand : candidates) {
            String hls = extractOne(cand);
            if (hls != null) {
                return hls;
            }
        }
        return null;
    }

    private static String extractOne(String str) {
        JSONObject decrypt;
        try {
            String str2 = getFollow(str, 0);
            if (str2 != null && !str2.isEmpty() && !str2.contains("DDoS-Guard") && str2.length() >= 500) {
                String findHls = findHls(str2);
                if (findHls != null) {
                    return findHls;
                }
                Matcher matcher = JSON_SCRIPT.matcher(str2);
                String trim = matcher.find() ? matcher.group(1).trim() : null;
                if (trim == null || trim.isEmpty() || (decrypt = decrypt(trim)) == null) {
                    return null;
                }
                String optString = decrypt.optString("source", "");
                if (optString.startsWith("http")) {
                    return optString.replace("\\/", "/");
                }
                String optString2 = decrypt.optString("hls", "");
                if (optString2.startsWith("http")) {
                    return optString2.replace("\\/", "/");
                }
                String optString3 = decrypt.optString("file", "");
                if (optString3.startsWith("http")) {
                    return optString3.replace("\\/", "/");
                }
                return null;
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    /** Follow HTML/JS location redirects used by rotating VOE CDN hosts. */
    private static String getFollow(String url, int depth) throws Exception {
        if (url == null || depth > 6) {
            return null;
        }
        String html = get(url);
        if (html == null || html.isEmpty() || html.contains("DDoS-Guard")) {
            return html;
        }
        if (html.contains("application/json") && html.length() > 2000) {
            return html;
        }
        Matcher matcher = JS_REDIRECT.matcher(html);
        if (matcher.find()) {
            String next = matcher.group(1);
            if (next != null && !next.equals(url)) {
                return getFollow(next, depth + 1);
            }
        }
        return html;
    }

    private static String findHls(String str) {
        Matcher matcher = HLS.matcher(str);
        if (matcher.find()) {
            return matcher.group(1).replace("\\/", "/");
        }
        Matcher matcher2 = M3U8.matcher(str);
        if (matcher2.find()) {
            return matcher2.group().replace("\\/", "/");
        }
        return null;
    }

    private static JSONObject decrypt(String str) {
        try {
            String rot13 = rot13(str);
            String[] strArr = {"@$", "^^", "~@", "%?", "*~", "!!", "#&"};
            for (int i = 0; i < 7; i++) {
                rot13 = rot13.replace(strArr[i], "_");
            }
            String str2 = new String(Base64.decode(rot13.replace("_", ""), 2), StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder(str2.length());
            for (int i2 = 0; i2 < str2.length(); i2++) {
                sb.append((char) (str2.charAt(i2) - 3));
            }
            return new JSONObject(new String(Base64.decode(sb.reverse().toString(), 2), StandardCharsets.UTF_8));
        } catch (Exception unused) {
            return null;
        }
    }

    private static String rot13(String str) {
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt >= 'A' && charAt <= 'Z') {
                sb.append((char) (((charAt - '4') % 26) + 65));
            } else if (charAt >= 'a' && charAt <= 'z') {
                sb.append((char) (((charAt - 'T') % 26) + 97));
            } else {
                sb.append(charAt);
            }
        }
        return sb.toString();
    }

    private static String pathOf(String str) {
        try {
            URL url = new URL(str);
            String path = url.getPath();
            return url.getQuery() != null ? path + "?" + url.getQuery() : path;
        } catch (Exception unused) {
            return null;
        }
    }

    private static String get(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setConnectTimeout(8000);
        httpURLConnection.setReadTimeout(10000);
        httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
        httpURLConnection.setRequestProperty(HttpHeaders.REFERER, "https://megakino19.com/");
        try {
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == 403 || responseCode == 429) {
                return "";
            }
            InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            if (errorStream == null) {
                return null;
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
            try {
                StringBuilder sb = new StringBuilder();
                while (true) {
                    String readLine = bufferedReader.readLine();
                    if (readLine == null) {
                        return sb.toString();
                    }
                    sb.append(readLine).append('\n');
                }
            } finally {
                bufferedReader.close();
            }
        } finally {
            httpURLConnection.disconnect();
        }
    }
}
