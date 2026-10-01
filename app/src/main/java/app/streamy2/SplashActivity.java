package app.streamy2;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Launcher entry shared by TV and mobile. Since 3.90 it shows nothing itself: it opens the
 * main screen right away, which plays the "Farbwellen" start animation ({@link IntroOverlay})
 * on top of the first screen and cross-fades into it.
 */
public final class SplashActivity extends Activity {
    /** Intent extra: play the start animation in MainActivity. */
    static final String EXTRA_INTRO = "app.streamy2.intro";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent main = new Intent(this, MainActivity.class);
        main.putExtra(EXTRA_INTRO, true);
        startActivity(main);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }

    /** False when the user turned animations off (animator duration scale 0). */
    static boolean animationsEnabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (Throwable t) {
            return true;
        }
    }
}
