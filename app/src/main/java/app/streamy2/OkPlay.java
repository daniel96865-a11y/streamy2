package app.streamy2;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import com.google.common.net.HttpHeaders;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class OkPlay {
    static volatile String diagnosticDns = "DNS: noch nicht geprüft";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static volatile OkHttpClient client;



    OkPlay() {
    }

    static DataSource.Factory factory() {
        return new OkHttpDataSource.Factory(client()).setUserAgent("okhttp/4.12.0");
    }

    static String postJson(String str, String str2) {
        return postJson(str, str2, null);
    }

    static String postJson(String str, String str2, String signature) {
        return postJson(str, str2, signature, 20);
    }

    static String postJsonFast(String str, String str2, String signature) {
        return postJson(str, str2, signature, 7);
    }

    static final class ResolveResponse {
        final String body;
        final int status;
        final String failure;

        ResolveResponse(String body, int status, String failure) {
            this.body = body;
            this.status = status;
            this.failure = failure;
        }
    }

    static ResolveResponse postResolve(String url, String json, String signature) {
        try {
            Request request = new Request.Builder().url(url)
                    .header(HttpHeaders.USER_AGENT, "MediaHubMX/2")
                    .header(HttpHeaders.ORIGIN, "https://vavoo.to")
                    .header(HttpHeaders.REFERER, "https://vavoo.to/")
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "de")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                    .header("mediahubmx-signature", signature)
                    .post(RequestBody.create(json, JSON)).build();
            try (Response response = client().newBuilder()
                    .connectTimeout(7, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .callTimeout(12, TimeUnit.SECONDS)
                    .build().newCall(request).execute()) {
                ResponseBody body = response.body();
                return new ResolveResponse(response.isSuccessful() && body != null ? body.string() : null,
                        response.code(), response.isSuccessful() ? "" : "HTTP " + response.code());
            }
        } catch (Exception error) {
            // Never include response bodies or signed stream URLs in diagnostics.
            return new ResolveResponse(null, 0, error instanceof java.net.SocketTimeoutException
                    || error instanceof java.io.InterruptedIOException ? "Zeitüberschreitung" : "Netzwerkfehler");
        }
    }

    private static String postJson(String str, String str2, String signature, int timeoutSeconds) {
        try {
            Request.Builder rb = new Request.Builder().url(str)
                .header(HttpHeaders.USER_AGENT, "MediaHubMX/2")
                .header(HttpHeaders.ACCEPT, "*/*")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de")
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .header(HttpHeaders.CONNECTION, "close")
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
            if (signature != null && !signature.isEmpty()) {
                rb.header("mediahubmx-signature", signature);
            }
            try (Response execute = client().newBuilder().connectTimeout(Math.min(8, timeoutSeconds), TimeUnit.SECONDS).readTimeout(Math.min(15, timeoutSeconds), TimeUnit.SECONDS).callTimeout(timeoutSeconds, TimeUnit.SECONDS).retryOnConnectionFailure(true).build().newCall(rb.post(RequestBody.create(str2, JSON)).build()).execute()) {
                if (!execute.isSuccessful()) {
                    if (execute != null) {
                        execute.close();
                    }
                    return null;
                }
                ResponseBody body = execute.body();
                String string = body == null ? null : body.string();
                if (execute != null) {
                    execute.close();
                }
                return string;
            }
        } catch (Exception unused) {
            return null;
        }
    }

    private static boolean isLiveExtraHost(String hostname) {
        if (hostname == null) return false;
        String h = hostname.toLowerCase();
        return h.equals("ngolpdkyoctjcddxshli469r.org")
                || h.endsWith(".ngolpdkyoctjcddxshli469r.org");
    }

    private static List<InetAddress> lookupWithFallback(String hostname) throws UnknownHostException {
        try {
            List<InetAddress> system = Dns.SYSTEM.lookup(hostname);
            diagnosticDns = "DNS: System OK · " + hostname;
            return system;
        } catch (UnknownHostException systemError) {
            diagnosticDns = "DNS: System fehlgeschlagen · " + hostname;
            if (!isLiveExtraHost(hostname)) throw systemError;
            List<InetAddress> fallback = dohLookup(hostname);
            if (fallback != null && !fallback.isEmpty()) {
                diagnosticDns = "DNS: Fallback OK · " + hostname + " · " + fallback.size() + " IP";
                return fallback;
            }
            diagnosticDns = "DNS: System + Fallback fehlgeschlagen · " + hostname;
            throw systemError;
        }
    }

    private static List<InetAddress> dohLookup(String hostname) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String q = URLEncoder.encode(hostname, "UTF-8");
            URL url = new URL("https://1.1.1.1/dns-query?name=" + q + "&type=A");
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(4500);
            connection.setRequestProperty(HttpHeaders.ACCEPT, "application/dns-json");
            connection.setRequestProperty(HttpHeaders.USER_AGENT, "Streamy2/3.59");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;

            InputStream in = connection.getInputStream();
            if (in == null) return null;
            reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);

            JSONObject json = new JSONObject(body.toString());
            JSONArray answers = json.optJSONArray("Answer");
            if (answers == null) return null;

            ArrayList<InetAddress> out = new ArrayList<>();
            for (int i = 0; i < answers.length(); i++) {
                JSONObject answer = answers.optJSONObject(i);
                if (answer == null || answer.optInt("type", 0) != 1) continue;
                String ip = answer.optString("data", "").trim();
                if (ip.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
                    out.add(InetAddress.getByName(ip));
                }
            }
            return out;
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            if (connection != null) connection.disconnect();
        }
    }

    static String diagnosticDns() {
        return diagnosticDns == null || diagnosticDns.isEmpty() ? "DNS: —" : diagnosticDns;
    }

    static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .dns(OkPlay::lookupWithFallback)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }
}
