package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** 3.90: "Farbwellen" start animation replaces the 3.82 logo splash (TV and mobile). */
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

    @org.junit.Before
    public void pauseChoreographer() {
        // Frames only run when the test advances the clock (otherwise Robolectric advances the
        // clock itself on every frame of an endless animation).
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
    }

    @After
    public void reset() {
        setScale(1f);
        Prefs p = new Prefs(RuntimeEnvironment.getApplication());
        p.setDesign(Design.DARK);
        p.setAccent("blue");
    }

    private static Context app() {
        return RuntimeEnvironment.getApplication();
    }

    private static void makeTv(Context context) {
        UiModeManager um = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (um != null) shadowOf(um).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION);
    }

    private static ActivityController<MainActivity> launchWithIntro() {
        return launchWithIntro(false);
    }

    private static ActivityController<MainActivity> launchWithIntro(boolean tv) {
        if (tv) makeTv(app());
        Intent i = new Intent(app(), MainActivity.class).putExtra(SplashActivity.EXTRA_INTRO, true);
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class, i);
        if (tv) makeTv(c.get());
        c.create().start().postCreate(null).resume().visible();
        return c;
    }

    private static IntroOverlay overlayIn(MainActivity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof IntroOverlay) return (IntroOverlay) content.getChildAt(i);
        }
        return null;
    }

    @Test
    public void oldLogoSplashIsGone() {
        Context c = app();
        String pkg = c.getPackageName();
        for (String id : new String[]{"splashLogo", "splashGlow", "splashTitle", "splashSubtitle", "splashShimmer",
                "splashProgress", "splashTrack", "splashStatus"}) {
            assertEquals(id, 0, c.getResources().getIdentifier(id, "id", pkg));
        }
        assertEquals(0, c.getResources().getIdentifier("activity_splash", "layout", pkg));
        assertEquals(0, c.getResources().getIdentifier("streamy_logo", "drawable", pkg));
        assertEquals(0, c.getResources().getIdentifier("splash_glow", "drawable", pkg));
    }

    @Test
    public void launcherOpensMainWithIntroAndShowsNothingItself() {
        ActivityController<SplashActivity> c = Robolectric.buildActivity(SplashActivity.class).setup();
        Intent next = shadowOf(c.get()).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra(SplashActivity.EXTRA_INTRO, false));
        assertTrue(c.get().isFinishing());
    }

    @Test
    public void wavesCrossFadeIntoFirstScreenAndRelease() {
        ActivityController<MainActivity> c = launchWithIntro();
        MainActivity a = c.get();
        IntroOverlay o = overlayIn(a);
        assertNotNull("overlay shown", o);
        assertSame(o, a.intro);
        assertFalse(o.timing.reduced);
        assertEquals(Design.DARK_BG, o.background);
        assertEquals(0xFF5B9DFF, o.palette[0]);
        // No playlist (demo): ready right away, ends after the minimum time with a fade-out.
        assertTrue(o.timing.isReady());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
        assertNotNull(overlayIn(a));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IntroTiming.MIN_HOLD_MS + IntroTiming.FADE_OUT_MS));
        shadowOf(Looper.getMainLooper()).idle();
        assertNull("overlay removed after the transition", overlayIn(a));
        assertNull(a.intro);
        assertFalse(o.isShowing());
        c.pause().stop().destroy();
    }

    @Test
    public void notReadyKeepsFlowingWithHintThenEnds() {
        IntroOverlay o = new IntroOverlay(app(), Design.DARK_BG, 0xFF5B9DFF, false, SystemClock.uptimeMillis());
        ViewGroup root = new android.widget.FrameLayout(app());
        root.addView(o);
        o.start();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3600));
        assertTrue(o.isShowing());
        assertTrue(o.dots.alpha > 0f);
        o.markReady();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IntroTiming.FADE_OUT_MS + 100));
        assertFalse(o.isShowing());
        assertEquals(0, root.getChildCount());
    }

    @Test
    public void remoteKeySkipsAndFocusLandsOnStartScreenOnTv() throws Exception {
        // Remote control: the TV window is not in touch mode.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        ActivityController<MainActivity> c = launchWithIntro(true);
        MainActivity a = c.get();
        assertNotNull(a.intro);
        // First screen with a playlist = Start screen (3.89 rows).
        java.lang.reflect.Method setTab = MainActivity.class.getDeclaredMethod("setTab", int.class);
        setTab.setAccessible(true);
        setTab.invoke(a, MainActivity.TAB_HOME);
        shadowOf(Looper.getMainLooper()).idle();
        // Keys during the animation only end it; they do not move focus underneath.
        assertTrue(a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN)));
        assertNotNull(a.intro);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IntroTiming.FADE_OUT_MS + 100));
        assertNull(a.intro);
        View home = a.findViewById(R.id.homePane);
        assertEquals(View.VISIBLE, home.getVisibility());
        View f = a.getCurrentFocus();
        assertNotNull("focus after the intro; touchMode=" + home.isInTouchMode() + " first=" + a.homeFirst
                + " shown=" + (a.homeFirst != null && a.homeFirst.isShown()), f);
        assertTrue("focus on the Start screen", home.hasFocus());
        c.pause().stop().destroy();
    }

    @Test
    public void oledBackgroundIsPureBlackAndAccentIsUsed() {
        Prefs p = new Prefs(app());
        p.setDesign(Design.OLED);
        p.setAccent("rose");
        ActivityController<MainActivity> c = launchWithIntro();
        IntroOverlay o = overlayIn(c.get());
        assertNotNull(o);
        assertEquals(0xFF000000, o.background);
        assertEquals(0xFF000000 | (Theme.get("rose").color & 0xFFFFFF), o.palette[0]);
        Bitmap bmp = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        o.waves.layout(0, 0, 64, 36);
        o.waves.drawFrame(new Canvas(bmp), o.timing.start + 1000);
        c.pause().stop().destroy();
    }

    @Test
    public void reducedMotionShowsStaticGradient() {
        setScale(0f);
        ActivityController<MainActivity> c = launchWithIntro();
        IntroOverlay o = overlayIn(c.get());
        assertNotNull(o);
        assertTrue(o.timing.reduced);
        assertEquals(IntroTiming.STATIC_T, o.timing.seconds(o.timing.start + 5000), 0f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IntroTiming.REDUCED_MIN_MS + 200));
        assertNull(overlayIn(c.get()));
        c.pause().stop().destroy();
    }

    @Test
    public void noIntroWithoutExtraOrAfterRecreate() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        assertNull(overlayIn(c.get()));
        assertNull(c.get().intro);
        c.pause().stop().destroy();
    }
}
