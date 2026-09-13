package app.streamy2;

import java.io.IOException;
import javax.net.ssl.HttpsURLConnection;
import okhttp3.Request;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.Test;
import static org.junit.Assert.*;

public class TlsRegressionTest {
    @Test public void untrustedCertificateIsRejectedByBothClients() throws Exception {
        HeldCertificate certificate=new HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build();
        HandshakeCertificates certificates=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        try(MockWebServer server=new MockWebServer()) {
            server.useHttps(certificates.sslSocketFactory(),false); server.start();
            assertThrows(IOException.class,()->OkPlay.client().newCall(new Request.Builder().url(server.url("/")).build()).execute());
            HttpsURLConnection connection=(HttpsURLConnection)server.url("/").url().openConnection();
            connection.setConnectTimeout(2000);connection.setReadTimeout(2000);
            try { assertThrows(IOException.class,connection::getInputStream); }
            finally { connection.disconnect(); }
        }
    }
}
