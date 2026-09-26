package app.streamy2;

import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SwipeZapPrefsTest {
    @Test public void defaultOnAndPersists() {
        Context ctx = RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("streamy2", 0).edit().clear().commit();
        assertTrue(new Prefs(ctx).swipeZap());
        new Prefs(ctx).setSwipeZap(false);
        assertFalse(new Prefs(ctx).swipeZap());
        new Prefs(ctx).setSwipeZap(true);
        assertTrue(new Prefs(ctx).swipeZap());
    }
}
