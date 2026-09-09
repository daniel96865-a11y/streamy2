package app.streamy2;

import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes.dex */
public final class Updates {
    public static final String[] FEEDS = {"https://raw.githubusercontent.com/daniel96865-a11y/streamy2/main/docs/streamy2.json", "https://raw.githubusercontent.com/daniel96865-a11y/streamy2/main/docs/streamy2.txt"};

    public static class Info {
        public int versionCode;
        public String versionName = "";
        public String apkUrl = "";
        public String changelog = "";
    }

    private static boolean looksLikeHtml(HttpURLConnection httpURLConnection) {
        return false;
    }

    public static Info fetch() {
        Info info = null;
        for (String str : FEEDS) {
            try {
                Info parse = parse(get(str, 12000));
                if (parse != null && parse.versionCode > 0 && !parse.apkUrl.isEmpty() && (info == null || parse.versionCode > info.versionCode)) {
                    info = parse;
                }
            } catch (Exception unused) {
            }
        }
        return info;
    }

    public static void download(String str, File file) throws Exception {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        if (str != null && !str.isEmpty()) {
            linkedHashSet.add(str);
        }
        Iterator it = linkedHashSet.iterator();
        Exception e = null;
        while (it.hasNext()) {
            try {
                downloadOne((String) it.next(), file);
                return;
            } catch (Exception e2) {
                e = e2;
                String extractApk = extractApk(e.getMessage());
                if (extractApk != null) {
                    if (linkedHashSet.add(extractApk)) {
                        try {
                            downloadOne(extractApk, file);
                            return;
                        } catch (Exception e3) {
                            e = e3;
                        }
                    } else {
                        continue;
                    }
                }
            }
        }
        if (e == null) {
            throw new Exception("Download fehlgeschlagen");
        }
    }

    private static void downloadOne(String str, File file) throws Exception {
        HttpURLConnection open = open(str);
        int responseCode = open.getResponseCode();
        String lowerCase = open.getContentType() == null ? "" : open.getContentType().toLowerCase();
        if (responseCode >= 400) {
            open.disconnect();
            throw new Exception("HTTP " + responseCode);
        }
        if (lowerCase.contains("text/html") || (lowerCase.contains("text/plain") && looksLikeHtml(open))) {
            String readLimited = readLimited(open, 120000);
            open.disconnect();
            String extractTmpfiles = extractTmpfiles(readLimited);
            if (extractTmpfiles != null) {
                downloadOne(extractTmpfiles, file);
                return;
            }
            throw new Exception("keine APK (HTML) " + str);
        }
        InputStream inputStream = open.getInputStream();
        File file2 = new File(file.getParentFile(), file.getName() + ".part");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        byte[] bArr = new byte[16384];
        long j = 0;
        while (true) {
            int read = inputStream.read(bArr);
            if (read < 0) {
                break;
            }
            fileOutputStream.write(bArr, 0, read);
            j += read;
        }
        fileOutputStream.flush();
        fileOutputStream.close();
        inputStream.close();
        open.disconnect();
        if (j < 1000000) {
            file2.delete();
            throw new Exception("Datei zu klein");
        }
        RandomAccessFile randomAccessFile = new RandomAccessFile(file2, "r");
        byte[] bArr2 = new byte[2];
        randomAccessFile.readFully(bArr2);
        randomAccessFile.close();
        if (bArr2[0] != 80 || bArr2[1] != 75) {
            file2.delete();
            throw new Exception("keine APK");
        }
        if (file.exists()) {
            file.delete();
        }
        if (file2.renameTo(file)) {
            return;
        }
        Files.copy(file2.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        file2.delete();
    }

    public static String directApk(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
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
            String extractTmpfiles = extractTmpfiles(readLimited);
            if (extractTmpfiles != null) {
                return extractTmpfiles;
            }
            Matcher matcher = Pattern.compile("https://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?").matcher(readLimited);
            return matcher.find() ? matcher.group() : str;
        } catch (Exception unused) {
            return str;
        }
    }

    private static String extractTmpfiles(String str) {
        if (str == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("https://tmpfiles\\.org/dl/[0-9]+\\.[A-Za-z0-9]+/[^\"'\\s<>]+\\.apk").matcher(str);
        if (matcher.find()) {
            return matcher.group();
        }
        Matcher matcher2 = Pattern.compile("https://tmpfiles\\.org/dl/[^\"'\\s<>]+\\.apk").matcher(str);
        if (matcher2.find()) {
            return matcher2.group();
        }
        return null;
    }

    private static String extractApk(String str) {
        if (str == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("https://[^\\s]+\\.apk").matcher(str);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static HttpURLConnection open(String str) throws Exception {
        URL url = new URL(str);
        int i = 0;
        while (i < 8) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(10000);
            httpURLConnection.setReadTimeout(180000);
            httpURLConnection.setInstanceFollowRedirects(false);
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 Streamy2");
            httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode < 300 || responseCode >= 400) {
                return httpURLConnection;
            }
            String headerField = httpURLConnection.getHeaderField(HttpHeaders.LOCATION);
            httpURLConnection.disconnect();
            if (headerField == null) {
                throw new Exception("Redirect ohne Ziel");
            }
            i++;
            url = new URL(url, headerField);
        }
        throw new Exception("zu viele Redirects");
    }

    private static Info parse(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        String replace = str.replace("&amp;quot;", "\"").replace("&quot;", "\"").replace("&#34;", "\"").replace("&amp;", "&");
        Matcher matcher = Pattern.compile("versionCode\"?\\s*:\\s*(\\d+)").matcher(replace);
        if (!matcher.find()) {
            return null;
        }
        Info info = new Info();
        info.versionCode = Integer.parseInt(matcher.group(1));
        Matcher matcher2 = Pattern.compile("versionName\"\\s*:\\s*\"([^\"]+)\"").matcher(replace);
        if (matcher2.find()) {
            info.versionName = matcher2.group(1);
        }
        Matcher matcher3 = Pattern.compile("https://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?").matcher(replace);
        if (matcher3.find()) {
            info.apkUrl = matcher3.group();
        }
        Matcher matcher4 = Pattern.compile("changelog\"\\s*:\\s*\"([^\"]+)\"").matcher(replace);
        if (matcher4.find()) {
            info.changelog = matcher4.group(1);
        }
        if (info.apkUrl.isEmpty()) {
            return null;
        }
        return info;
    }

    private static String get(String str, int i) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(i);
        httpURLConnection.setReadTimeout(i);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 Streamy2");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "text/plain, application/json, text/html, */*");
        return readLimited(httpURLConnection, 192000);
    }

    private static String readLimited(HttpURLConnection httpURLConnection, int i) throws Exception {
        InputStream inputStream = httpURLConnection.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[2048];
        int i2 = 0;
        do {
            int read = inputStream.read(bArr);
            if (read < 0) {
                break;
            }
            byteArrayOutputStream.write(bArr, 0, read);
            i2 += read;
        } while (i2 <= i);
        inputStream.close();
        return byteArrayOutputStream.toString("UTF-8");
    }

    private Updates() {
    }
}
