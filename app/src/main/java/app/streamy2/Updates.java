package app.streamy2;

import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Updates {
    private static final String BASE = "https://raw.githubusercontent.com/daniel96865-a11y/streamy2/main/docs/";
    /**
     * Same feed file via the GitHub contents API. raw.githubusercontent.com is a CDN that caches
     * for 5 minutes and ignores query strings, so right after a release the raw feed can still
     * show the previous version. The API answer is only cached for 60 s.
     */
    private static final String API = "https://api.github.com/repos/daniel96865-a11y/streamy2/contents/docs/";
    private static final String API_ACCEPT = "application/vnd.github.raw+json";

    public static String[] feeds() {
        return feedsFor(BuildConfig.UPDATE_CHANNEL);
    }

    /** Feed URLs for an update channel: raw JSON, raw TXT and (new channels only) the API copy. */
    static String[] feedsFor(String channel) {
        String stem;
        if ("mobile".equals(channel)) {
            stem = "streamy2-mobile";
        } else if ("tv-new".equals(channel)) {
            stem = "streamy2-tv";
        } else {
            stem = "streamy2";
            // Legacy feed is frozen on purpose; no API fallback for it.
            return new String[]{BASE + stem + ".json", BASE + stem + ".txt"};
        }
        return new String[]{BASE + stem + ".json", BASE + stem + ".txt", API + stem + ".json?ref=main"};
    }

    public static class Info {
        public int versionCode;
        public String versionName = "";
        public String apkUrl = "";
        public String changelog = "";
    }

    public static Info fetch() {
        Info info = null;
        for (String str : feeds()) {
            try {
                info = newer(info, parse(get(str, 12000)));
            } catch (Exception unused) { Quiet.ignored("Updates", unused); }
        }
        return info;
    }

    /** Keeps the candidate with the higher versionCode (valid entries only). */
    static Info newer(Info current, Info candidate) {
        if (candidate == null || candidate.versionCode <= 0 || candidate.apkUrl == null || candidate.apkUrl.isEmpty()) {
            return current;
        }
        return current == null || candidate.versionCode > current.versionCode ? candidate : current;
    }

    public static void download(String url, File file) throws Exception {
        if (url == null || url.isEmpty()) throw new Exception("Keine Download-Adresse");
        downloadOne(url, file, 0);
    }

    private static void downloadOne(String url, File file, int hops) throws Exception {
        if (hops > 3) throw new Exception("Zu viele Download-Weiterleitungen");
        HttpURLConnection connection = open(url);
        File part = new File(file.getPath() + ".part");
        String linkedApk = null;
        try {
            int code = connection.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            String type = connection.getContentType();
            if (type != null && (type.contains("text/") || type.contains("json"))) {
                String body = readLimited(connection, 120000);
                linkedApk = extractTmpfiles(body);
                if (linkedApk == null) linkedApk = extractApk(body);
                if (linkedApk == null || linkedApk.equals(url)) throw new Exception("Keine APK im Download");
            } else {
                long total = 0;
                long expected = connection.getContentLengthLong();
                long deadline = android.os.SystemClock.elapsedRealtime() + 300000;
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        if (total > 300L * 1024 * 1024 || Thread.currentThread().isInterrupted()
                                || android.os.SystemClock.elapsedRealtime() > deadline) {
                            throw new Exception("Download-Limit erreicht");
                        }
                        out.write(buffer, 0, count);
                    }
                }
                if (expected >= 0 && expected != total) throw new Exception("Download unvollständig");
                try (java.util.zip.ZipFile apk = new java.util.zip.ZipFile(part)) {
                    if (apk.getEntry("AndroidManifest.xml") == null || apk.getEntry("classes.dex") == null) {
                        throw new Exception("Keine gültige APK");
                    }
                }
                if (!part.renameTo(file)) throw new Exception("APK konnte nicht gespeichert werden");
            }
        } finally {
            connection.disconnect();
            if (part.exists()) part.delete();
        }
        if (linkedApk != null) downloadOne(linkedApk, file, hops + 1);
    }

    public static String directApk(String str) {
        if (str == null || str.isEmpty()) return str;
        try {
            HttpURLConnection open = open(str);
            String lowerCase = open.getContentType() == null ? "" : open.getContentType().toLowerCase();
            if (open.getResponseCode() >= 400) {
                open.disconnect();
                return str;
            }
            if (!lowerCase.contains("html") && !lowerCase.contains("text/plain") && !lowerCase.contains("json")) {
                open.disconnect();
                return str;
            }
            String readLimited = readLimited(open, 120000);
            open.disconnect();
            String tmp = extractTmpfiles(readLimited);
            if (tmp != null) return tmp;
            Matcher matcher = Pattern.compile("https://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?").matcher(readLimited);
            return matcher.find() ? matcher.group() : str;
        } catch (Exception unused) {
            return str;
        }
    }

    private static String extractTmpfiles(String str) {
        if (str == null) return null;
        Matcher matcher = Pattern.compile("https://tmpfiles\\.org/dl/[0-9]+\\.[A-Za-z0-9]+/[^\"'\\s<>]+\\.apk").matcher(str);
        if (matcher.find()) return matcher.group();
        Matcher matcher2 = Pattern.compile("https://tmpfiles\\.org/dl/[^\"'\\s<>]+\\.apk").matcher(str);
        return matcher2.find() ? matcher2.group() : null;
    }

    private static String extractApk(String str) {
        if (str == null) return null;
        Matcher matcher = Pattern.compile("https://[^\\s]+\\.apk").matcher(str);
        return matcher.find() ? matcher.group() : null;
    }

    private static HttpURLConnection open(String str) throws Exception {
        URL url = new URL(str);
        int i = 0;
        while (i < 8) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(25000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 Streamy2");
            connection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
            int responseCode = connection.getResponseCode();
            if (responseCode < 300 || responseCode >= 400) return connection;
            String location = connection.getHeaderField(HttpHeaders.LOCATION);
            connection.disconnect();
            if (location == null) throw new Exception("Redirect ohne Ziel");
            i++;
            url = new URL(url, location);
        }
        throw new Exception("zu viele Redirects");
    }

    static Info parse(String str) {
        if (str == null || str.isEmpty()) return null;
        String replace = str.replace("&amp;quot;", "\"").replace("&quot;", "\"")
                .replace("&#34;", "\"").replace("&amp;", "&");
        Matcher matcher = Pattern.compile("versionCode\"?\\s*:\\s*(\\d+)").matcher(replace);
        if (!matcher.find()) return null;
        Info info = new Info();
        info.versionCode = Integer.parseInt(matcher.group(1));
        Matcher matcher2 = Pattern.compile("versionName\"\\s*:\\s*\"([^\"]+)\"").matcher(replace);
        if (matcher2.find()) info.versionName = matcher2.group(1);
        Matcher matcher3 = Pattern.compile("https://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?").matcher(replace);
        if (matcher3.find()) info.apkUrl = matcher3.group();
        Matcher matcher4 = Pattern.compile("changelog\"\\s*:\\s*\"([^\"]+)\"").matcher(replace);
        if (matcher4.find()) info.changelog = matcher4.group(1);
        return info.apkUrl.isEmpty() ? null : info;
    }

    private static String get(String str, int timeout) throws Exception {
        String sep = str.contains("?") ? "&" : "?";
        String fresh = str + sep + "_=" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(fresh).openConnection();
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 Streamy2/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty(HttpHeaders.ACCEPT, str.startsWith(API) ? API_ACCEPT
                : "text/plain, application/json, text/html, */*");
        try {
            return readLimited(connection, 192000);
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(HttpURLConnection connection, int limit) throws Exception {
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        do {
            int read = inputStream.read(buffer);
            if (read < 0) break;
            output.write(buffer, 0, read);
            total += read;
        } while (total <= limit);
        inputStream.close();
        return output.toString("UTF-8");
    }

    private Updates() { }
}
