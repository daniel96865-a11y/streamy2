package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * 3.82: TV remote in the film/series player. Controls hidden: LEFT/RIGHT seek.
 * Controls visible: LEFT/RIGHT move focus between the buttons, no seeking, unless
 * the seekbar is focused. Back closes the controls first.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class, qualifiers = "television")
public class TvPlayerKeysTest {

    @Test
    public void decisionTable() {
        // hidden controls: films/series and catch-up seek, plain live keeps old behaviour
        assertEquals(PlayerActivity.TV_KEY_SHOW_AND_SEEK, PlayerActivity.tvHorizontalKeyAction(false, false, true, false, false));
        assertEquals(PlayerActivity.TV_KEY_SHOW_AND_SEEK, PlayerActivity.tvHorizontalKeyAction(false, false, true, true, true));
        assertEquals(PlayerActivity.TV_KEY_LEGACY, PlayerActivity.tvHorizontalKeyAction(false, false, true, true, false));
        // visible controls: navigate unless the seekbar has focus
        assertEquals(PlayerActivity.TV_KEY_NAVIGATE, PlayerActivity.tvHorizontalKeyAction(true, false, true, false, false));
        assertEquals(PlayerActivity.TV_KEY_NAVIGATE, PlayerActivity.tvHorizontalKeyAction(true, false, true, true, true));
        assertEquals(PlayerActivity.TV_KEY_SEEK, PlayerActivity.tvHorizontalKeyAction(true, true, true, false, false));
        // media keys (REW/FF) always seek
        assertEquals(PlayerActivity.TV_KEY_SEEK, PlayerActivity.tvHorizontalKeyAction(true, false, false, false, false));
        assertEquals(PlayerActivity.TV_KEY_SEEK, PlayerActivity.tvHorizontalKeyAction(false, false, false, false, false));
    }

    private static void makeTv(Context context) {
        UiModeManager um = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (um != null) shadowOf(um).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
    }

    private static boolean hud(PlayerActivity a) throws Exception {
        Field f = PlayerActivity.class.getDeclaredField("hud");
        f.setAccessible(true);
        return f.getBoolean(a);
    }

    private static boolean press(PlayerActivity a, int keyCode) {
        boolean handled = a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        shadowOf(Looper.getMainLooper()).idle();
        return handled;
    }

    @Test
    public void vodRemoteSeeksOnlyWhenControlsHiddenOrSeekbarFocused() throws Exception {
        makeTv(RuntimeEnvironment.getApplication());
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), PlayerActivity.class)
                .putExtra("url", "http://127.0.0.1:9/film.mp4")
                .putExtra("title", "Testfilm")
                .putExtra("live", false)
                .putExtra("forceEngine", "exo");
        ActivityController<PlayerActivity> c = Robolectric.buildActivity(PlayerActivity.class, intent);
        makeTv(c.get());
        c.create().start().postCreate(null).resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        PlayerActivity a = c.get();
        assertTrue("TV mode", Tv.isTv(a));
        View seek = a.findViewById(R.id.epgSeek);
        View play = a.findViewById(R.id.btnPlay);

        // Back with controls visible only closes the controls.
        if (!hud(a)) press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(hud(a));
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse("controls closed", hud(a));
        assertFalse("player stays open", a.isFinishing());

        // Controls hidden: RIGHT seeks and shows the controls with the seekbar focused.
        int seeks = a.keySeeks;
        assertTrue(press(a, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(seeks + 1, a.keySeeks);
        assertTrue(hud(a));
        assertSame("seekbar focused", seek, a.getCurrentFocus());

        // Seekbar focused: RIGHT keeps seeking.
        assertTrue(press(a, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(seeks + 2, a.keySeeks);

        // Button focused: RIGHT is left to focus navigation, no seek.
        assertTrue(play.requestFocus());
        assertFalse("not consumed -> focus navigation", press(a, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(seeks + 2, a.keySeeks);
        assertTrue(hud(a));
        View next = play.focusSearch(View.FOCUS_RIGHT);
        assertNotNull("a control to the right", next);
        assertNotSame(play, next);
        assertNotSame(seek, next);
        assertEquals(R.id.epgSeek, play.getNextFocusUpId());
        assertEquals(R.id.btnPlay, seek.getNextFocusDownId());

        // Media key FF still seeks with controls open.
        press(a, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        assertEquals(seeks + 3, a.keySeeks);

        // Back: first closes controls, second leaves the player.
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(hud(a));
        assertFalse(a.isFinishing());
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.isFinishing());
    }
}
