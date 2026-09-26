package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Launches MainActivity in TV mode (UiModeManager = television) on old Android
 * versions used by Fire TV (Fire OS 6 = API 25, Fire OS 7 = API 28) and walks
 * through the settings screen with D-pad focus. Regression for the 3.73 TV crash.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 28, 34}, application = Application.class, qualifiers = "television")
public class TvMainActivityLaunchTest {

    private static void makeTv(Context context) {
        UiModeManager um = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (um != null) shadowOf(um).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
    }

    private static ActivityController<MainActivity> launch(boolean tv) {
        if (tv) makeTv(RuntimeEnvironment.getApplication());
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        if (tv) makeTv(controller.get());
        try {
            controller.create().start().postCreate(null).resume().visible();
            ShadowLooper.idleMainLooper();
        } catch (Throwable t) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            String trace = sw.toString();
            System.err.println("LAUNCH CRASH: " + trace);
            throw new AssertionError("MainActivity launch crashed (tv=" + tv + "):\n" + trace.substring(0, Math.min(6000, trace.length())), t);
        }
        return controller;
    }

    private static void collectFocusable(View v, List<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v.isFocusable()) out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectFocusable(g.getChildAt(i), out);
        }
    }

    @Test
    public void tvLaunchOpenSettingsAndWalkWithDpadDoesNotCrash() {
        ActivityController<MainActivity> controller = launch(true);
        MainActivity activity = controller.get();
        assertTrue("test must run in TV mode", Tv.isTv(activity));

        activity.findViewById(R.id.btnSettings).performClick();
        ShadowLooper.idleMainLooper();
        View pane = activity.findViewById(R.id.settingsPane);
        assertEquals(View.VISIBLE, pane.getVisibility());

        int[] heads = {R.id.headAccount, R.id.headPlay, R.id.headEpg, R.id.headLook};
        for (int id : heads) {
            View head = activity.findViewById(id);
            // open (or toggle) every section
            if (head != null) head.performClick();
            ShadowLooper.idleMainLooper();
        }

        // Walk every focusable in the settings pane with D-pad Down and Up.
        List<View> focusables = new ArrayList<>();
        collectFocusable(pane, focusables);
        assertFalse(focusables.isEmpty());
        for (int dir : new int[]{View.FOCUS_DOWN, View.FOCUS_UP, View.FOCUS_LEFT, View.FOCUS_RIGHT}) {
            for (View v : focusables) {
                v.requestFocus();
                View next = v.focusSearch(dir);
                if (next != null) next.requestFocus();
                ShadowLooper.idleMainLooper();
            }
        }
        // Click accent dots and simple option chips (re-paints theme / focus states).
        View accentRow = activity.findViewById(R.id.accentRow);
        if (accentRow instanceof ViewGroup && ((ViewGroup) accentRow).getChildCount() > 1) {
            ((ViewGroup) accentRow).getChildAt(1).performClick();
            ((ViewGroup) accentRow).getChildAt(0).performClick();
        }
        ShadowLooper.idleMainLooper();

        activity.getOnBackPressedDispatcher().onBackPressed();
        ShadowLooper.idleMainLooper();
        assertEquals(View.GONE, pane.getVisibility());

        controller.pause().stop().destroy();
    }

    @Test
    public void phoneLaunchStillWorks() {
        ActivityController<MainActivity> controller = launch(false);
        MainActivity activity = controller.get();
        assertFalse(Tv.isTv(activity));
        activity.findViewById(R.id.btnSettings).performClick();
        ShadowLooper.idleMainLooper();
        activity.getOnBackPressedDispatcher().onBackPressed();
        ShadowLooper.idleMainLooper();
        controller.pause().stop().destroy();
    }
}
