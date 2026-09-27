package app.streamy2;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class)
public class PhoneFullscreenTest {

    private PlayerActivity launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences("streamy2", 0).edit().clear().commit();
        new Prefs(RuntimeEnvironment.getApplication()).setResize("fit");
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), PlayerActivity.class)
                .putExtra("url", "http://127.0.0.1:9/film.mp4")
                .putExtra("title", "Testfilm")
                .putExtra("live", false)
                .putExtra("forceEngine", "exo");
        ActivityController<PlayerActivity> c = Robolectric.buildActivity(PlayerActivity.class, intent);
        c.create().start().postCreate(null).resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(Tv.isTv(c.get()));
        return c.get();
    }

    @Test public void videoContainerHasNoInsetPadding() {
        PlayerActivity a = launch();
        View root = a.findViewById(R.id.playerRoot);
        View decor = a.getWindow().getDecorView();
        for (View v = root; v != null && v != decor; ) {
            assertFalse("fitsSystemWindows on " + v, v.getFitsSystemWindows());
            assertEquals(0, v.getPaddingLeft());
            assertEquals(0, v.getPaddingTop());
            assertEquals(0, v.getPaddingRight());
            assertEquals(0, v.getPaddingBottom());
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        View pv = a.findViewById(R.id.playerView);
        assertEquals(-1, pv.getLayoutParams().width);
        assertEquals(-1, pv.getLayoutParams().height);
    }

    @Test public void pinchTogglesFillAndFitAndIsRemembered() {
        PlayerActivity a = launch();
        PlayerView pv = a.findViewById(R.id.playerView);
        TextView overlay = a.findViewById(R.id.zapOverlay);
        a.onPinchFinished(1.4f);
        assertEquals("zoom", new Prefs(a).resize());
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, pv.getResizeMode());
        assertEquals(View.VISIBLE, overlay.getVisibility());
        assertTrue(overlay.getText().toString().startsWith("Ausfüllen"));
        a.onPinchFinished(0.6f);
        assertEquals("fit", new Prefs(a).resize());
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, pv.getResizeMode());
        assertEquals("Einpassen", overlay.getText().toString());
        // Tiny movement changes nothing.
        a.onPinchFinished(1.02f);
        assertEquals("fit", new Prefs(a).resize());
    }

    @Test public void twoFingerSpreadOnVideoSelectsFill() {
        PlayerActivity a = launch();
        View tap = a.findViewById(R.id.tapLayer);
        tap.layout(0, 0, 2400, 1080);
        long t = SystemClock.uptimeMillis();
        tap.dispatchTouchEvent(ev(t, MotionEvent.ACTION_DOWN, 1100, 540));
        float[] half = {200, 260, 340, 420, 500, 560};
        tap.dispatchTouchEvent(ev2(t + 10, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 1200, 200));
        for (int i = 0; i < half.length; i++) {
            tap.dispatchTouchEvent(ev2(t + 30 + i * 20, MotionEvent.ACTION_MOVE, 1200, half[i]));
        }
        tap.dispatchTouchEvent(ev2(t + 200, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 1200, 560));
        tap.dispatchTouchEvent(ev(t + 220, MotionEvent.ACTION_UP, 640, 540));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("zoom", new Prefs(a).resize());
    }

    private static MotionEvent ev(long t, int action, float x, float y) {
        return MotionEvent.obtain(t, t, action, x, y, 0);
    }

    /** Two pointers mirrored around cx, distance 2*half horizontally. */
    private static MotionEvent ev2(long t, int action, float cx, float half) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = i == 0 ? cx - half : cx + half;
            coords[i].y = 540;
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        return MotionEvent.obtain(t - 10, t, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0);
    }
}
