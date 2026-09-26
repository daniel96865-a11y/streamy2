package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/** Without a saved playlist the refresh buttons stay hidden and explain what to do. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34}, application = Application.class)
public class PlaylistRefreshDemoTest {
    @Test
    public void noPlaylistHidesButtons() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        c.create().start().postCreate(null).resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        MainActivity a = c.get();
        PlaylistUiBinder.bind(a);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, a.findViewById(R.id.btnRefreshMedia).getVisibility());
        View label = a.findViewById(R.id.activeLabel);
        View button = ((ViewGroup) label.getParent()).findViewWithTag(PlaylistUiBinder.REFRESH_TAG);
        assertNotNull(button);
        assertEquals(View.GONE, button.getVisibility());
        a.refreshPlaylist();
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("Keine Playlist gespeichert"));
        assertFalse(a.isPlaylistRefreshing());
        c.pause().stop().destroy();
    }
}
