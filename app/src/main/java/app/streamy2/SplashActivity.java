package app.streamy2;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Short branded startup screen shared by TV and mobile. */
public final class SplashActivity extends Activity {
    private static final long START_DELAY_MS = 1450L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable openMain;
    private final java.util.List<Animator> animators = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash);
        hideSystemUi();

        ImageView logo = findViewById(R.id.splashLogo);
        ProgressBar progress = findViewById(R.id.splashProgress);
        TextView status = findViewById(R.id.splashStatus);

        progress.setMax(100);
        progress.setProgress(0);
        if (animationsEnabled(this)) {
            startIntroAnimations(logo, progress);
        } else {
            // System setting "remove animations": show the final state right away.
            View glow = findViewById(R.id.splashGlow);
            if (glow != null) glow.setAlpha(0.8f);
            progress.setProgress(100);
        }

        handler.postDelayed(() -> status.setText(R.string.splash_starting), 900L);
        openMain = () -> {
            if (isFinishing()) return;
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        };
        handler.postDelayed(openMain, START_DELAY_MS);
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

    /**
     * Cheap intro: logo fade/scale with overshoot, pulsing glow behind it, staggered
     * title/subtitle, and a shimmer over the progress bar. Runs in parallel with the
     * existing start timer, so startup is not delayed.
     */
    private void startIntroAnimations(ImageView logo, ProgressBar progress) {
        float density = getResources().getDisplayMetrics().density;
        logo.setAlpha(0f);
        logo.setScaleX(0.85f);
        logo.setScaleY(0.85f);
        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600L)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        View glow = findViewById(R.id.splashGlow);
        if (glow != null) {
            glow.setAlpha(0f);
            glow.setScaleX(0.9f);
            glow.setScaleY(0.9f);
            ObjectAnimator pulseAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.45f, 0.95f);
            ObjectAnimator pulseX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.92f, 1.06f);
            ObjectAnimator pulseY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.92f, 1.06f);
            for (ObjectAnimator a : new ObjectAnimator[]{pulseAlpha, pulseX, pulseY}) {
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setRepeatMode(ValueAnimator.REVERSE);
            }
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(pulseAlpha, pulseX, pulseY);
            pulse.setDuration(900L);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.setStartDelay(450L);
            pulse.start();
            animators.add(pulse);
        }

        int[] staggered = new int[]{R.id.splashTitle, R.id.splashSubtitle};
        for (int i = 0; i < staggered.length; i++) {
            View v = findViewById(staggered[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(14f * density);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(220L + i * 120L)
                    .setDuration(420L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        ObjectAnimator loading = ObjectAnimator.ofInt(progress, "progress", 0, 100);
        loading.setDuration(START_DELAY_MS - 100L);
        loading.setInterpolator(new AccelerateDecelerateInterpolator());
        loading.start();
        animators.add(loading);

        final View shimmer = findViewById(R.id.splashShimmer);
        final View track = findViewById(R.id.splashTrack);
        if (shimmer != null && track != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                track.setClipToOutline(true);
            }
            track.post(() -> {
                if (isFinishing()) return;
                float from = -shimmer.getWidth();
                float to = track.getWidth();
                shimmer.setTranslationX(from);
                shimmer.setAlpha(1f);
                ObjectAnimator sweep = ObjectAnimator.ofFloat(shimmer, View.TRANSLATION_X, from, to);
                sweep.setDuration(1000L);
                sweep.setStartDelay(200L);
                sweep.setRepeatCount(ValueAnimator.INFINITE);
                sweep.setInterpolator(new AccelerateDecelerateInterpolator());
                sweep.start();
                animators.add(sweep);
            });
        }
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        if (openMain != null) handler.removeCallbacks(openMain);
        for (Animator a : animators) {
            try { a.cancel(); } catch (Throwable ignored) { Quiet.ignored("SplashActivity", ignored); }
        }
        animators.clear();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
