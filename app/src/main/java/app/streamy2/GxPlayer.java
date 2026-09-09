package app.streamy2;

import com.google.common.net.HttpHeaders;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class GxPlayer {
    private static final Pattern VIDEO = Pattern.compile("var\\s+video\\s*=\\s*(\\{.*?\\})\\s*;", 32);

    GxPlayer() {
    }

    static boolean isGx(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.contains("gxplayer") || lowerCase.contains("watch.gx");
    }

    static String extract(String str) {
        if (str != null && !str.isEmpty()) {
            if (str.startsWith("//")) {
                str = "https:" + str;
            }
            try {
                String str2 = get(str);
                if (str2 != null && !str2.isEmpty()) {
                    String grab = grab(str2, "\"id\"\\s*:\\s*\"([^\"]+)\"");
                    String grab2 = grab(str2, "\"uid\"\\s*:\\s*\"([^\"]+)\"");
                    String grab3 = grab(str2, "\"md5\"\\s*:\\s*\"([^\"]+)\"");
                    String grab4 = grab(str2, "\"status\"\\s*:\\s*\"([^\"]+)\"");
                    if (grab2.isEmpty() || grab3.isEmpty()) {
                        Matcher matcher = VIDEO.matcher(str2);
                        if (matcher.find()) {
                            JSONObject jSONObject = new JSONObject(matcher.group(1));
                            grab2 = jSONObject.optString("uid", grab2);
                            grab3 = jSONObject.optString("md5", grab3);
                            grab = jSONObject.optString("id", grab);
                            grab4 = jSONObject.optString("status", grab4);
                        }
                    }
                    if (!grab2.isEmpty() && !grab3.isEmpty()) {
                        String origin = origin(str);
                        if (!grab.isEmpty()) {
                            return origin + "/m3u8/" + grab2 + "/" + grab3 + "/master.txt?s=1&id=" + grab + "&cache=" + grab4;
                        }
                        return origin + "/alternative_stream/" + grab2 + "/" + grab3 + "/master.m3u8";
                    }
                }
            } catch (Exception unused) {
            }
        }
        return null;
    }

    private static String grab(String str, String str2) {
        Matcher matcher = Pattern.compile(str2).matcher(str);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String origin(String str) {
        try {
            URL url = new URL(str);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception unused) {
            return "https://watch.gxplayer.xyz";
        }
    }

    private static String get(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setConnectTimeout(12000);
        httpURLConnection.setReadTimeout(15000);
        httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
        httpURLConnection.setRequestProperty(HttpHeaders.REFERER, "https://watch.gxplayer.xyz/");
        InputStream errorStream = httpURLConnection.getResponseCode() >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
        if (errorStream == null) {
            return null;
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                bufferedReader.close();
                httpURLConnection.disconnect();
                return sb.toString();
            }
            sb.append(readLine).append('\n');
        }
    }
}
