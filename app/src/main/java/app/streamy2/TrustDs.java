package app.streamy2;

import android.net.Uri;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/* loaded from: classes.dex */
final class TrustDs extends BaseDataSource {
    private HttpURLConnection conn;
    private final Map<String, String> extra;
    private InputStream in;
    private long remaining;
    private final String ua;



    TrustDs(String str, Map<String, String> map) {
        super(true);
        this.remaining = -1L;
        this.ua = str == null ? "okhttp/4.11.0" : str;
        this.extra = map == null ? new HashMap<>() : map;
    }

    static DataSource.Factory factory(final String str, final Map<String, String> map) {
        return new DataSource.Factory() { // from class: app.streamy2.TrustDs$$ExternalSyntheticLambda1
            @Override // androidx.media3.datasource.DataSource.Factory
            public final DataSource createDataSource() {
                return TrustDs.lambda$factory$0(str, map);
            }
        };
    }

    static /* synthetic */ DataSource lambda$factory$0(String str, Map map) {
        return new TrustDs(str, map);
    }

    /* JADX WARN: Code restructure failed: missing block: B:48:0x0110, code lost:
    
        r0.disconnect();
     */
    @Override // androidx.media3.datasource.DataSource
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    public long open(DataSpec dataSpec) throws IOException {
        String str;
        close();
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(dataSpec.uri.toString()).openConnection();

            httpURLConnection.setConnectTimeout(15000);
            httpURLConnection.setReadTimeout(10000);
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, this.ua);
            httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "*/*");
            httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "identity");
            httpURLConnection.setRequestProperty(HttpHeaders.CONNECTION, "close");
            Map<String, String> map = this.extra;
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (dataSpec.position > 0) {
                long j = dataSpec.length != -1 ? (dataSpec.position + dataSpec.length) - 1 : -1L;
                if (j > 0) {
                    str = "bytes=" + dataSpec.position + "-" + j;
                } else {
                    str = "bytes=" + dataSpec.position + "-";
                }
                httpURLConnection.setRequestProperty(HttpHeaders.RANGE, str);
            }
            int responseCode = httpURLConnection.getResponseCode();
            InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            if (errorStream != null && responseCode < 400) {
                this.conn = httpURLConnection;
                this.in = errorStream;
                long contentLength = httpURLConnection.getContentLength();
                this.remaining = contentLength > 0 ? contentLength : -1L;
                transferStarted(dataSpec);
                return this.remaining;
            }
            throw new IOException("HTTP " + responseCode);
        } catch (IOException e) {
            close();
            throw e;
        } catch (Exception e2) {
            close();
            throw new IOException(e2);
        }
    }

    @Override // androidx.media3.common.DataReader
    public int read(byte[] bArr, int i, int i2) throws IOException {
        if (i2 == 0) {
            return 0;
        }
        if (this.remaining == 0) {
            return -1;
        }
        try {
            int read = this.in.read(bArr, i, i2);
            if (read == -1) {
                return -1;
            }
            long j = this.remaining;
            if (j != -1) {
                this.remaining = j - read;
            }
            bytesTransferred(read);
            return read;
        } catch (IOException e) {
            throw e;
        } catch (Exception e2) {
            throw new IOException(e2);
        }
    }

    @Override // androidx.media3.datasource.DataSource
    public Uri getUri() {
        try {
            HttpURLConnection httpURLConnection = this.conn;
            if (httpURLConnection != null) {
                return Uri.parse(httpURLConnection.getURL().toString());
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    @Override // androidx.media3.datasource.DataSource
    public void close() {
        try {
            InputStream inputStream = this.in;
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception unused) {
            Quiet.ignored("TrustDs", unused);
        }
        this.in = null;
        try {
            HttpURLConnection httpURLConnection = this.conn;
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        } catch (Exception unused2) {
            Quiet.ignored("TrustDs", unused2);
        }
        this.conn = null;
        this.remaining = -1L;
    }


}
