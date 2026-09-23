package app.streamy2;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import com.google.common.net.HttpHeaders;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/* loaded from: classes.dex */
final class OkPlay {
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
            try (Response execute = client().newBuilder().connectTimeout(8L, TimeUnit.SECONDS).readTimeout(15L, TimeUnit.SECONDS).callTimeout(20L, TimeUnit.SECONDS).retryOnConnectionFailure(true).build().newCall(rb.post(RequestBody.create(str2, JSON)).build()).execute()) {
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

    static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }
}
