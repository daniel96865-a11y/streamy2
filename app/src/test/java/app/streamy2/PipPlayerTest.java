package app.streamy2;

import android.app.Application;
import android.content.pm.PackageManager;
import android.view.View;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Bild-in-Bild in the player: mobile only, UI hidden in PiP, close stops playback. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PipPlayerTest {
    private ActivityController<PlayerActivity> controller;
    private PlayerActivity activity;

    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("streamy2", 0).edit().clear().commit();
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true);
        controller = Robolectric.buildActivity(PlayerActivity.class);
        controller.create().start().resume();
        activity = controller.get();
    }

    @After public void cleanup() {
        try {
            controller.pause().stop().destroy();
        } catch (Throwable ignored) {
            // already finished/destroyed by the test
        }
    }

    @Test public void allowedOnlyInMobileAppAndWithSetting() {
        boolean mobile = Pip.isMobileFlavor(BuildConfig.FLAVOR);
        assertEquals(mobile, activity.pipAllowed());
        View btn = activity.findViewById(R.id.btnPip);
        assertNotNull(btn);
        assertEquals(mobile ? View.VISIBLE : View.GONE, btn.getVisibility());
        new Prefs(activity).setPipEnabled(false);
        assertFalse(activity.pipAllowed());
        assertFalse(activity.enterPip());
    }

    @Test public void prefsDefaultOn() {
        Prefs p = new Prefs(activity);
        assertTrue(p.pipEnabled());
        assertTrue(p.pipAuto());
        p.setPipAuto(false);
        assertFalse(p.pipAuto());
    }

    @Test public void paramsBuildWithoutVideo() {
        if (!Pip.isMobileFlavor(BuildConfig.FLAVOR)) return;
        assertNotNull(activity.buildPipParams());
    }

    @Test public void controlsAndOverlaysHiddenInPip() {
        activity.onPipModeChanged(true, true);
        assertTrue(activity.inPip);
        View top = activity.findViewById(R.id.topBar);
        View bottom = activity.findViewById(R.id.bottomBar);
        assertNotEquals(View.VISIBLE, top.getVisibility());
        assertNotEquals(View.VISIBLE, bottom.getVisibility());
        assertNotEquals(View.VISIBLE, activity.findViewById(R.id.playerError).getVisibility());
        assertNotEquals(View.VISIBLE, activity.findViewById(R.id.bufferOverlay).getVisibility());
        activity.onConfigurationChanged(activity.getResources().getConfiguration()); // PiP resize
        assertNotEquals(View.VISIBLE, top.getVisibility());
    }

    @Test public void tapBackToFullPlayerKeepsPlaying() {
        activity.onPipModeChanged(true, true);
        activity.onPipModeChanged(false, true);
        assertFalse(activity.inPip);
        assertFalse(activity.isFinishing());
        assertTrue(activity.pipExpandPending);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.topBar).getVisibility());
    }

    @Test public void closingPipStopsAndFinishes() {
        activity.onPipModeChanged(true, true);
        activity.onPipModeChanged(false, false);
        assertTrue(activity.pipClosed);
        assertTrue(activity.isFinishing());
    }

    @Test public void closedWindowDetectedInOnStop() {
        activity.onPipModeChanged(true, true);
        activity.onPipModeChanged(false, true); // looked like "expanded" ...
        controller.pause().stop();              // ... but the activity got stopped: closed
        assertTrue(activity.pipClosed);
        assertTrue(activity.isFinishing());
    }

    @Test public void normalHomeWithoutPipOnlyPauses() {
        controller.pause().stop();
        assertFalse(activity.pipClosed);
        assertFalse(activity.isFinishing());
    }

    @Test public void pipDeclaredOnlyInMobileManifest() throws Exception {
        File main = new File("src/main/AndroidManifest.xml");
        File mobile = new File("src/mobile/AndroidManifest.xml");
        if (!main.exists()) return; // other working directory
        String m = new String(Files.readAllBytes(main.toPath()), StandardCharsets.UTF_8);
        String mo = new String(Files.readAllBytes(mobile.toPath()), StandardCharsets.UTF_8);
        assertFalse(m.contains("supportsPictureInPicture"));
        assertTrue(mo.contains("android:supportsPictureInPicture=\"true\""));
        assertTrue(mo.contains("android:resizeableActivity=\"true\""));
    }
}
