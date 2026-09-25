package app.streamy2;

import android.app.Application;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DataRegressionTest {
    private MockWebServer server;
    private File directory;
    @Before public void setup() throws Exception { server = new MockWebServer(); server.start(); directory = Files.createTempDirectory("streamy-test").toFile(); }
    @After public void cleanup() throws Exception { server.shutdown(); for (File file : directory.listFiles()) file.delete(); directory.delete(); }

    @Test public void failedCategoryDoesNotDiscardMoviesOrSeries() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String action = request.getRequestUrl().queryParameter("action");
                if ("get_vod_categories".equals(action)) return new MockResponse().setResponseCode(503);
                if ("get_series_categories".equals(action)) return new MockResponse().setBody("[{\"category_id\":\"1\",\"category_name\":\"Series\"}]");
                if ("get_vod_streams".equals(action)) return new MockResponse().setBody("[{\"stream_id\":1,\"name\":\"Film\",\"container_extension\":\"mp4\"}]");
                return new MockResponse().setBody("[{\"series_id\":2,\"name\":\"Serie\"}]");
            }
        });
        Models.Catalog catalog = new Models.Catalog();
        XtreamApi api = new XtreamApi(server.url("/").toString(), "user", "password", "hls");
        Exception error = assertThrows(Exception.class, () -> api.loadLibrary(catalog));
        assertTrue(error.getMessage().contains("Filmkategorien"));
        assertEquals(1, catalog.vod.size()); assertEquals(1, catalog.series.size()); assertEquals(1, catalog.seriesCats.size());
        assertEquals(4, server.getRequestCount());
    }

    @Test public void http404PropagatesAndPreservesPreviousApk() throws Exception {
        File file = new File(directory, "update.apk"); Files.write(file.toPath(), new byte[]{1, 2, 3});
        server.enqueue(new MockResponse().setResponseCode(404));
        Exception error = assertThrows(Exception.class, () -> Updates.download(server.url("/update.apk").toString(), file));
        assertTrue(error.getMessage().contains("404"));
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(file.toPath()));
        assertFalse(new File(file + ".part").exists());
    }

    @Test public void validApkDownloadSucceeds() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(body)) {
            for (String name : new String[]{"AndroidManifest.xml", "classes.dex"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(new byte[]{1,2,3}); zip.closeEntry();
            }
        }
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/vnd.android.package-archive").setBody(new okio.Buffer().write(body.toByteArray())));
        File file = new File(directory, "update.apk"); Updates.download(server.url("/update.apk").toString(), file);
        assertArrayEquals(body.toByteArray(), Files.readAllBytes(file.toPath()));
    }

    private String xml() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        long now = System.currentTimeMillis();
        return "<tv><channel id=\"test\"><display-name>Test</display-name></channel><programme channel=\"test\" start=\"" + format.format(new Date(now-60000)) + "\" stop=\"" + format.format(new Date(now+3600000)) + "\"><title>Jetzt</title></programme></tv>";
    }
    @Test public void smallXmltvAndGzipAreAcceptedAndDuplicateCacheIsReused() throws Exception {
        for (boolean gzip : new boolean[]{false,true}) {
            byte[] body = xml().getBytes(StandardCharsets.UTF_8);
            if (gzip) { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try(GZIPOutputStream gz = new GZIPOutputStream(bytes)) { gz.write(body); } body = bytes.toByteArray(); }
            assertTrue(body.length < 100*1024);
            server.enqueue(new MockResponse().setBody(new okio.Buffer().write(body)));
            EpgGuide guide = new EpgGuide(); File cache = new File(directory, "guide" + gzip + ".xml");
            String url = server.url(gzip ? "/guide.gz" : "/guide.xml").toString();
            guide.loadUrlMerge(url, cache, true);
            int requests = server.getRequestCount();
            assertEquals(1, guide.programmeCount);
            guide.loadUrlMerge(url, cache, false);
            assertEquals(requests, server.getRequestCount()); assertEquals(1, guide.programmeCount);
        }
    }
    @Test public void invalidXmlCannotReplaceGoodCache() throws Exception {
        EpgGuide guide = new EpgGuide(); File cache = new File(directory, "guide.xml");
        String url = server.url("/guide.xml").toString();
        server.enqueue(new MockResponse().setBody(xml())); guide.loadUrlMerge(url, cache, true);
        byte[] previous = Files.readAllBytes(cache.toPath());
        server.enqueue(new MockResponse().setBody("<html><body>Service temporarily unavailable, please retry.</body></html>"));
        assertThrows(Exception.class, () -> guide.loadUrlMerge(url, cache, true));
        assertArrayEquals(previous, Files.readAllBytes(cache.toPath())); assertEquals(1, guide.programmeCount);
    }
    @Test public void futureProgrammeIsNeverCurrentAndExpiredEpgIsCleared() {
        long now = System.currentTimeMillis(); EpgGuide.Listing future = new EpgGuide.Listing(); future.start=now+60000; future.stop=now+120000; future.title="Später";
        assertNull(EpgTime.current(Collections.singletonList(future), now));
        Models.Channel channel=new Models.Channel(); channel.id="test"; channel.name="Test"; channel.epg=new Models.Epg(); channel.epg.start=now-120000; channel.epg.end=now-1; channel.epg.title="Vorbei";
        new EpgGuide().apply(Collections.singletonList(channel)); assertNull(channel.epg);
        future.start=now; assertSame(future,EpgTime.current(Collections.singletonList(future),now));
        assertNull(EpgTime.current(Collections.singletonList(future),future.stop));
    }
    @Test public void refreshHonoursEachConfiguredInterval() {
        for (int hours : new int[]{6,12,24}) {
            long interval=EpgRefresh.intervalMillis(hours); assertEquals(hours*3600000L,interval);
            assertFalse(EpgRefresh.due(1000,1000+interval-1,interval));
            assertTrue(EpgRefresh.due(1000,1000+interval,interval));
            assertTrue(EpgRefresh.due(0,1000,interval));
        }
    }
    @Test public void epgIsNotReparsedEveryHalfHourWhilePlaying() {
        assertEquals(30L*60000L,EpgRefresh.hydrateIntervalMillis(false));
        assertTrue(EpgRefresh.hydrateIntervalMillis(true)>=2L*3600000L);
    }
    @Test public void changingIntervalReplacesPersistedBackgroundJob() {
        android.content.Context context=org.robolectric.RuntimeEnvironment.getApplication();
        android.app.job.JobScheduler scheduler=(android.app.job.JobScheduler)context.getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE);
        for(int hours:new int[]{12,6,24}) {
            new Prefs(context).setEpgIntervalHours(hours); EpgRefresh.schedule(context);
            assertEquals(1,scheduler.getAllPendingJobs().size());
            android.app.job.JobInfo job=scheduler.getAllPendingJobs().get(0);
            assertEquals(hours*3600000L,job.getIntervalMillis()); assertTrue(job.isPersisted());
            assertEquals(EpgJobService.class.getName(),job.getService().getClassName());
        }
    }
}
