package app.streamy2;

import com.google.common.net.HttpHeaders;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* loaded from: classes.dex */
final class LocalHls {
    private static volatile int port;
    private static ServerSocket server;
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Map<String, Held> HELD = new ConcurrentHashMap();



    LocalHls() {
    }

    private static final class Held {
        long at;
        String cdn;

        private Held() {
        }
    }

    private static final Object READY = new Object();
    private static volatile boolean ready;

    static void start() {
        if (ready && server != null && port > 0) {
            return;
        }
        synchronized (READY) {
            if (ready && server != null && port > 0) {
                return;
            }
            Exception last = null;
            // Prefer ephemeral, then a small fixed range to avoid races
            int[] candidates = new int[33];
            candidates[0] = 0;
            for (int i = 0; i < 32; i++) {
                candidates[i + 1] = 8787 + i;
            }
            for (int p : candidates) {
                try {
                    ServerSocket serverSocket = new ServerSocket(p, 32, InetAddress.getByName("127.0.0.1"));
                    server = serverSocket;
                    port = serverSocket.getLocalPort();
                    ready = true;
                    READY.notifyAll();
                    POOL.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while (server != null && !server.isClosed()) {
                                    final Socket accept = server.accept();
                                    POOL.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            LocalHls.serve(accept);
                                        }
                                    });
                                }
                            } catch (Exception unused) {
                            }
                        }
                    });
                    return;
                } catch (Exception e) {
                    last = e;
                }
            }
            ready = false;
            port = 0;
        }
    }

    static /* synthetic */ void lambda$start$1() {
        start();
    }

    static boolean isReady() {
        return ready && server != null && port > 0 && !server.isClosed();
    }

    static int getPort() {
        return port;
    }

    static void forget(String str) {
        if (str != null) {
            HELD.remove(str);
        }
    }

    static String wrap(String str) {
        start();
        synchronized (READY) {
            long deadline = System.currentTimeMillis() + 2000L;
            while ((!ready || port == 0) && System.currentTimeMillis() < deadline) {
                try {
                    READY.wait(50L);
                } catch (InterruptedException unused) {
                    break;
                }
                if (!ready) {
                    start();
                }
            }
        }
        if (str == null || str.isEmpty() || str.contains("127.0.0.1")) {
            return str;
        }
        if (!isReady()) {
            start();
            synchronized (READY) {
                long deadline = System.currentTimeMillis() + 1500L;
                while (!isReady() && System.currentTimeMillis() < deadline) {
                    try {
                        READY.wait(50L);
                    } catch (InterruptedException unused) {
                        break;
                    }
                }
            }
        }
        return !isReady() ? str : "http://127.0.0.1:" + port + "/p?u=" + enc(str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void serve(Socket socket) {
        try {
            socket.setSoTimeout(60000);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                return;
            }
            String readLine2;
            do {
                readLine2 = bufferedReader.readLine();
                if (readLine2 == null) {
                    break;
                }
            } while (!readLine2.isEmpty());
            int indexOf = readLine.indexOf(32);
            int i = indexOf + 1;
            int indexOf2 = readLine.indexOf(32, i);
            if (indexOf >= 0 && indexOf2 >= 0) {
                String substring = readLine.substring(i, indexOf2);
                String q = query(substring, "u");
                OutputStream outputStream = socket.getOutputStream();
                if (q != null && !q.isEmpty()) {
                    if (substring.startsWith("/p")) {
                        playlist(outputStream, q);
                    } else {
                        segment(outputStream, q);
                    }
                } else {
                    write(outputStream, 400, "text/plain", "bad".getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception unused) {
        } finally {
            try {
                socket.close();
            } catch (Exception unused2) {
            }
        }
    }

    private static void playlist(OutputStream outputStream, String str) throws Exception {
        int i;
        int indexOf;
        String upstream = upstream(str);
        if (upstream == null) {
            write(outputStream, 502, "text/plain", "resolve".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] fetch = fetch(upstream, 10000);
        if (fetch == null || fetch.length < 8) {
            HELD.remove(str);
            upstream = upstream(str);
            fetch = upstream == null ? null : fetch(upstream, 10000);
        }
        if (fetch == null) {
            write(outputStream, 502, "text/plain", "empty".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String str2 = new String(fetch, StandardCharsets.UTF_8);
        if (!str2.trim().startsWith("#EXTM3U")) {
            HELD.remove(str);
            write(outputStream, 502, "text/plain", "not-hls".getBytes(StandardCharsets.UTF_8));
            return;
        }
        int indexOf2 = upstream.indexOf(63);
        if (indexOf2 > 0) {
            upstream = upstream.substring(0, indexOf2);
        }
        int lastIndexOf = upstream.lastIndexOf(47);
        if (lastIndexOf > 0) {
            upstream = upstream.substring(0, lastIndexOf + 1);
        }
        StringBuilder sb = new StringBuilder(str2.length() + 512);
        boolean z = false;
        for (String str3 : str2.split("\n")) {
            String trim = str3.trim();
            if (trim.isEmpty()) {
                sb.append(str3).append('\n');
            } else if (trim.startsWith("#")) {
                z = trim.startsWith("#EXT-X-STREAM-INF");
                int indexOf3 = trim.indexOf("URI=\"");
                if (indexOf3 > 0 && (indexOf = trim.indexOf(34, (i = indexOf3 + 5))) > indexOf3) {
                    trim = trim.substring(0, i) + proxied(abs(upstream, trim.substring(i, indexOf)), true) + trim.substring(indexOf);
                }
                sb.append(trim).append('\n');
            } else {
                sb.append(proxied(abs(upstream, trim), z)).append('\n');
                z = false;
            }
        }
        write(outputStream, 200, "application/vnd.apple.mpegurl", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void segment(OutputStream outputStream, String str) throws Exception {
        String playable = ExtraLiveSource.playable(str);
        if (playable != null) {
            str = playable;
        }
        HttpURLConnection open = open(str, 10000);
        if (open == null) {
            write(outputStream, 502, "text/plain", "seg".getBytes(StandardCharsets.UTF_8));
            return;
        }
        int responseCode = open.getResponseCode();
        InputStream errorStream = responseCode >= 400 ? open.getErrorStream() : open.getInputStream();
        if (errorStream == null || responseCode >= 400) {
            open.disconnect();
            write(outputStream, 502, "text/plain", "seg".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String contentType = open.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = "video/MP2T";
        }
        outputStream.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        byte[] bArr = new byte[16384];
        while (true) {
            int read = errorStream.read(bArr);
            if (read <= 0) {
                outputStream.flush();
                errorStream.close();
                open.disconnect();
                return;
            }
            outputStream.write(bArr, 0, read);
        }
    }

    private static String upstream(String str) {
        if (!ExtraLiveSource.isPlayUrl(str)) {
            return str.startsWith("http") ? str : ExtraLiveSource.playable(str);
        }
        Map<String, Held> map = HELD;
        Held held = map.get(str);
        long currentTimeMillis = System.currentTimeMillis();
        if (held != null && held.cdn != null && currentTimeMillis - held.at < 12000) {
            return held.cdn;
        }
        String resolve = ExtraLiveSource.resolve(str);
        if (resolve != null) {
            Held held2 = new Held();
            held2.cdn = resolve;
            held2.at = currentTimeMillis;
            map.put(str, held2);
        }
        return resolve;
    }

    private static String proxied(String str, boolean z) {
        if (str == null) {
            return "";
        }
        if (str.contains("127.0.0.1")) {
            return str;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return "http://127.0.0.1:" + port + ((z || lowerCase.contains(".m3u8") || lowerCase.contains("m3u8?") || lowerCase.contains("master.txt") || lowerCase.contains("/m3u8/")) ? "/p?u=" : "/s?u=") + enc(str);
    }

    private static String abs(String str, String str2) {
        if (str2.startsWith("http://") || str2.startsWith("https://")) {
            return str2;
        }
        if (str2.startsWith("/")) {
            try {
                URL url = new URL(str);
                return url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 ? ":" + url.getPort() : "") + str2;
            } catch (Exception unused) {
                return str + str2;
            }
        }
        return str + str2;
    }

    private static byte[] fetch(String str, int i) {
        HttpURLConnection open = null;
        try {
            open = open(str, i);
            if (open == null) {
                return null;
            }
            int responseCode = open.getResponseCode();
            InputStream errorStream = responseCode >= 400 ? open.getErrorStream() : open.getInputStream();
            if (errorStream != null && responseCode < 400) {
                BufferedInputStream bufferedInputStream = new BufferedInputStream(errorStream);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[8192];
                while (true) {
                    int read = bufferedInputStream.read(bArr);
                    if (read <= 0) {
                        bufferedInputStream.close();
                        return byteArrayOutputStream.toByteArray();
                    }
                    byteArrayOutputStream.write(bArr, 0, read);
                }
            }
            return null;
        } catch (Exception unused) {
            return null;
        } finally {
            if (open != null) {
                try { open.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private static HttpURLConnection open(String str, int i) {
        try {
            String playable = ExtraLiveSource.playable(str);
            if (playable == null) {
                return null;
            }
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(playable).openConnection();
            httpURLConnection.setConnectTimeout(i);
            httpURLConnection.setReadTimeout(Math.max(i, 15000));
            httpURLConnection.setInstanceFollowRedirects(true);

            if (!playable.contains("gxplayer") && !playable.contains("master.txt")) {
                if (!ExtraLiveSource.isCdn(playable) && !ExtraLiveSource.isPlayUrl(playable) && !playable.contains("/sunshine/") && !playable.contains("ngolpdky")) {
                    httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "libmpv");
                    httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
                    httpURLConnection.setRequestProperty("Icy-MetaData", "1");
                    httpURLConnection.setRequestProperty(HttpHeaders.CONNECTION, "keep-alive");
                    return httpURLConnection;
                }
                httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "okhttp/4.11.0");
                httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
                httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "identity");
                httpURLConnection.setRequestProperty(HttpHeaders.CONNECTION, "keep-alive");
                return httpURLConnection;
            }
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            httpURLConnection.setRequestProperty(HttpHeaders.REFERER, "https://watch.gxplayer.xyz/");
            httpURLConnection.setRequestProperty(HttpHeaders.ORIGIN, "https://watch.gxplayer.xyz");
            httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
            httpURLConnection.setRequestProperty(HttpHeaders.CONNECTION, "keep-alive");
            return httpURLConnection;
        } catch (Exception unused) {
            return null;
        }
    }



    private static void write(OutputStream outputStream, int i, String str, byte[] bArr) throws Exception {
        outputStream.write(("HTTP/1.1 " + i + " " + (i == 200 ? "OK" : "ERR") + "\r\nContent-Type: " + str + "\r\nContent-Length: " + bArr.length + "\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        outputStream.write(bArr);
        outputStream.flush();
    }

    private static String query(String str, String str2) {
        int indexOf = str.indexOf(63);
        if (indexOf < 0) {
            return null;
        }
        for (String str3 : str.substring(indexOf + 1).split("&")) {
            int indexOf2 = str3.indexOf(61);
            if (indexOf2 >= 0 && str2.equals(str3.substring(0, indexOf2))) {
                int i = indexOf2 + 1;
                try {
                    return URLDecoder.decode(str3.substring(i), "UTF-8");
                } catch (Exception unused) {
                    return str3.substring(i);
                }
            }
        }
        return null;
    }

    private static String enc(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception unused) {
            return str;
        }
    }
}
