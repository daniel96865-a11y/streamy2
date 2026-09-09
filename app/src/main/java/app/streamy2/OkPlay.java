package app.streamy2;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import com.google.common.net.HttpHeaders;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
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

    static /* synthetic */ boolean lambda$client$0(String str, SSLSession sSLSession) {
        return true;
    }

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
                .header(HttpHeaders.ORIGIN, "https://vavoo.to")
                .header(HttpHeaders.REFERER, "https://vavoo.to/")
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
            if (signature != null && !signature.isEmpty()) {
                rb.header("mediahubmx-signature", signature);
            }
            Response execute = client().newBuilder().connectTimeout(4L, TimeUnit.SECONDS).readTimeout(6L, TimeUnit.SECONDS).callTimeout(8L, TimeUnit.SECONDS).retryOnConnectionFailure(false).build().newCall(rb.post(RequestBody.create(str2, JSON)).build()).execute();
            try {
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
            } finally {
            }
        } catch (Exception unused) {
            return null;
        }
    }

    static OkHttpClient client() {
        if (client != null) {
            return client;
        }
        TrustManager[] trustManagerArr = {new X509TrustManager() { // from class: app.streamy2.OkPlay.1
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
        try {
            SSLContext sSLContext = SSLContext.getInstance("TLS");
            sSLContext.init(null, trustManagerArr, new SecureRandom());
            client = new OkHttpClient.Builder().sslSocketFactory(sSLContext.getSocketFactory(), (X509TrustManager) trustManagerArr[0]).hostnameVerifier(new HostnameVerifier() { // from class: app.streamy2.OkPlay$$ExternalSyntheticLambda0
                @Override // javax.net.ssl.HostnameVerifier
                public final boolean verify(String str, SSLSession sSLSession) {
                    return OkPlay.lambda$client$0(str, sSLSession);
                }
            }).followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true).connectTimeout(15L, TimeUnit.SECONDS).readTimeout(20L, TimeUnit.SECONDS).build();
        } catch (Exception unused) {
            client = new OkHttpClient();
        }
        return client;
    }
}
