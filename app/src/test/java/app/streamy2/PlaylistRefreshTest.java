package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/** 3.80: manual "Playlist aktualisieren" reloads the active playlist from the server. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34}, application = Application.class)
public class PlaylistRefreshTest {

    private MockWebServer server;
    private final AtomicInteger channels = new AtomicInteger(2);
    private final AtomicBoolean fail = new AtomicBoolean(false);
    private final AtomicInteger streamRequests = new AtomicInteger();
    private final AtomicReference<String> lastCacheControl = new AtomicReference<>();

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String action = request.getRequestUrl() == null ? null : request.getRequestUrl().queryParameter("action");
                if (fail.get()) return new MockResponse().setResponseCode(500);
                if ("get_live_categories".equals(action)) {
                    return new MockResponse().setBody("[{\"category_id\":\"1\",\"category_name\":\"DE\"}]");
                }
                if ("get_live_streams".equals(action)) {
                    streamRequests.incrementAndGet();
                    lastCacheControl.set(request.getHeader("Cache-Control"));
                    StringBuilder body = new StringBuilder("[");
                    for (int i = 1; i <= channels.get(); i++) {
                        if (i > 1) body.append(',');
                        body.append("{\"stream_id\":\"").append(i).append("\",\"name\":\"Sender ").append(i)
                                .append("\",\"category_id\":\"1\"}");
                    }
                    return new MockResponse().setBody(body.append(']').toString());
                }
                if (action == null) {
                    return new MockResponse().setBody("{\"user_info\":{\"auth\":1,\"status\":\"Active\"}}");
                }
                return new MockResponse().setBody("[]");
            }
        });
        server.start();
        Context app = RuntimeEnvironment.getApplication();
        new Prefs(app).saveAccountAsPlaylist("Test", server.url("/").toString(), "user", "pass");
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void waitFor(java.util.function.BooleanSupplier done) throws Exception {
        for (int i = 0; i < 200 && !done.getAsBoolean(); i++) {
            Thread.sleep(25);
            idle();
        }
        Thread.sleep(50);
        idle();
    }

    private ActivityController<MainActivity> launch() throws Exception {
        App.live = null;
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        c.create().start().postCreate(null).resume().visible();
        MainActivity a = c.get();
        waitFor(() -> App.live != null && App.live.size() == 2);
        PlaylistUiBinder.bind(a);
        idle();
        return c;
    }

    private static TextView settingsRefreshButton(MainActivity a) {
        View label = a.findViewById(R.id.activeLabel);
        View button = ((ViewGroup) label.getParent()).findViewWithTag(PlaylistUiBinder.REFRESH_TAG);
        assertNotNull("settings must contain the playlist refresh button", button);
        return (TextView) button;
    }

    @Test
    public void refreshButtonReloadsPlaylistFromNetwork() throws Exception {
        ActivityController<MainActivity> c = launch();
        MainActivity a = c.get();
        assertEquals(2, App.live.size());

        // Button above the Live-TV list is visible with the playlist label.
        TextView listButton = a.findViewById(R.id.btnRefreshMedia);
        assertEquals(View.VISIBLE, listButton.getVisibility());
        assertEquals("Playlist aktualisieren", listButton.getText().toString());
        assertNotNull("vector icon", listButton.getCompoundDrawablesRelative()[0]);

        channels.set(5);
        int before = streamRequests.get();
        listButton.performClick();
        waitFor(() -> !a.isPlaylistRefreshing());
        assertTrue("must hit the network", streamRequests.get() > before);
        assertEquals("no-cache", lastCacheControl.get());
        assertEquals("Playlist aktualisiert: 5 Sender", ShadowToast.getTextOfLatestToast());
        assertEquals(5, App.live.size());

        // Settings button does the same.
        TextView settingsButton = settingsRefreshButton(a);
        assertEquals(View.VISIBLE, settingsButton.getVisibility());
        assertTrue(settingsButton.isFocusable());
        channels.set(7);
        settingsButton.performClick();
        waitFor(() -> !a.isPlaylistRefreshing());
        assertEquals("Playlist aktualisiert: 7 Sender", ShadowToast.getTextOfLatestToast());
        assertEquals(7, App.live.size());
        c.pause().stop().destroy();
    }

    @Test
    public void failedRefreshKeepsListAndShowsError() throws Exception {
        ActivityController<MainActivity> c = launch();
        MainActivity a = c.get();
        fail.set(true);
        a.refreshPlaylist();
        waitFor(() -> !a.isPlaylistRefreshing());
        String toast = ShadowToast.getTextOfLatestToast();
        assertTrue(toast, toast.startsWith("Playlist konnte nicht aktualisiert werden"));
        assertEquals("old list stays", 2, App.live.size());
        TextView listButton = a.findViewById(R.id.btnRefreshMedia);
        assertTrue("button usable again", listButton.isEnabled());
        c.pause().stop().destroy();
    }

    @Test
    public void errorMessages() {
        assertEquals("Playlist konnte nicht aktualisiert werden: Server nicht erreichbar. Internetverbindung prüfen.",
                MainActivity.playlistRefreshError(new java.net.UnknownHostException("x")));
        assertEquals("Playlist konnte nicht aktualisiert werden: HTTP 500",
                MainActivity.playlistRefreshError(new java.util.concurrent.ExecutionException(new java.io.IOException("HTTP 500"))));
    }
}
