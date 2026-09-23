package app.streamy2;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResolveResponseTest {
    @Test public void resolveReportsAuthenticationFailureForSignatureRefresh() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(403).setBody("private details"));
            server.start();
            OkPlay.ResolveResponse result = OkPlay.postResolve(
                    server.url("/mediahubmx-resolve.json").toString(), "{}", "old-signature");
            assertEquals(403, result.status);
            assertNull(result.body);
            assertEquals("HTTP 403", result.failure);
            assertEquals("old-signature", server.takeRequest().getHeader("mediahubmx-signature"));
        }
    }

    @Test public void resolveReturnsPlayableResponseBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("[{\"url\":\"https://example.org/live.m3u8\"}]"));
            server.start();
            OkPlay.ResolveResponse result = OkPlay.postResolve(
                    server.url("/mediahubmx-resolve.json").toString(), "{}", "signature");
            assertEquals(200, result.status);
            assertTrue(result.body.contains("live.m3u8"));
            assertEquals("", result.failure);
        }
    }
}
