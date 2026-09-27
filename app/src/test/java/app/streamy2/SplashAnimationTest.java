package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** 3.82: animated splash, no animation when the system turned animations off. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class)
public class SplashAnimationTest {

    /** Same effect as the developer option "animator duration scale" (hidden API). */
    private static void setScale(float scale) {
        try {
            java.lang.reflect.Method m = android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
            m.setAccessible(true);
            m.invoke(null, scale);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @After
    public void resetScale() {
        setScale(1f);
    }

    @Test
    public void animatedSplashStartsAndHasAllParts() {
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();
        assertTrue(SplashActivity.animationsEnabled(a));
        for (int id : new int[]{R.id.splashLogo, R.id.splashGlow, R.id.splashTitle, R.id.splashSubtitle,
                R.id.splashTrack, R.id.splashProgress, R.id.splashShimmer, R.id.splashStatus}) {
            assertNotNull("view " + id, a.findViewById(id));
        }
        // Logo starts small and transparent, then animates in.
        View logo = a.findViewById(R.id.splashLogo);
        assertTrue(logo.getScaleX() <= 1f);
        c.pause().stop().destroy();
    }

    @Test
    public void noAnimationWhenSystemAnimationsOff() {
        setScale(0f);
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        SplashActivity a = c.get();
        assertFalse(SplashActivity.animationsEnabled(a));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1f, a.findViewById(R.id.splashLogo).getAlpha(), 0.001f);
        assertEquals(1f, a.findViewById(R.id.splashTitle).getAlpha(), 0.001f);
        assertEquals(100, ((ProgressBar) a.findViewById(R.id.splashProgress)).getProgress());
        assertEquals(0f, a.findViewById(R.id.splashShimmer).getAlpha(), 0.001f);
        c.pause().stop().destroy();
    }
}
