package app.streamy2;

import android.animation.ObjectAnimator;
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

        logo.setAlpha(0f);
        logo.setScaleX(0.92f);
        logo.setScaleY(0.92f);
        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(520L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        progress.setMax(100);
        progress.setProgress(0);
        ObjectAnimator loading = ObjectAnimator.ofInt(progress, "progress", 0, 100);
        loading.setDuration(START_DELAY_MS - 100L);
        loading.setInterpolator(new AccelerateDecelerateInterpolator());
        loading.start();

        handler.postDelayed(() -> status.setText(R.string.splash_starting), 900L);
        openMain = () -> {
            if (isFinishing()) return;
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        };
        handler.postDelayed(openMain, START_DELAY_MS);
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
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
