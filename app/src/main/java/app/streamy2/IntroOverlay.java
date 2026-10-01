package app.streamy2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * "Farbwellen" start animation (3.90), shown on top of the first screen and cross-faded
 * into it. Cheap on low-RAM TV sticks: the waves are a handful of large radial-gradient
 * ellipses drawn into a small hardware layer (1/3 of the screen) that the GPU scales up,
 * so the soft look costs almost no fill rate; no per-pixel CPU work, no bitmaps.
 * Everything is stopped and detached after the fade-out. With system animations off a
 * static gradient is shown instead.
 */
final class IntroOverlay extends FrameLayout {
    interface Listener {
        void onIntroFinished();
    }

    /** Waves are drawn at 1/SCALE resolution and scaled up (they are soft anyway). */
    static final int SCALE = 3;

    final IntroTiming timing;
    final Waves waves;
    final Dots dots;
    final int background;
    final int[] palette;
    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean finished;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            tick();
            if (running) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            tick();
            if (running) handler.postDelayed(this, 100L);
        }
    };

    IntroOverlay(Context context, int background, int accent, boolean reduced, long now) {
        super(context);
        this.background = 0xFF000000 | background;
        this.palette = IntroWaves.palette(accent);
        this.timing = new IntroTiming(now, reduced);
        setClickable(true);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        waves = new Waves(context, this);
        dots = new Dots(context, this);
        addView(waves);
        addView(dots, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Adds the overlay on top of the activity content and starts it. */
    static IntroOverlay show(Activity activity, Listener listener) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return null;
        IntroOverlay o = new IntroOverlay(activity, AccentTheme.background(activity), AccentTheme.accent(activity),
                !SplashActivity.animationsEnabled(activity), SystemClock.uptimeMillis());
        o.listener = listener;
        content.addView(o, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        o.start();
        return o;
    }

    void start() {
        if (running || finished) return;
        running = true;
        if (timing.reduced) {
            handler.post(poll);
        } else {
            Choreographer.getInstance().postFrameCallback(frame);
        }
        tick();
    }

    boolean isShowing() {
        return !finished;
    }

    void markReady() {
        timing.markReady(SystemClock.uptimeMillis());
    }

    /** Remote key / tap: fade out now. */
    void skip() {
        timing.skip(SystemClock.uptimeMillis());
    }

    void tick() {
        long now = SystemClock.uptimeMillis();
        if (timing.done(now)) {
            finish();
            return;
        }
        setAlpha(timing.backgroundAlpha(now));
        if (!timing.reduced) waves.invalidate();
        float hint = timing.hintAlpha(now);
        if (hint > 0f || dots.alpha > 0f) {
            dots.alpha = hint;
            dots.invalidate();
        }
    }

    /** Stops the animation, detaches and releases everything. */
    void finish() {
        if (finished) return;
        finished = true;
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        handler.removeCallbacksAndMessages(null);
        waves.release();
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
        Listener l = listener;
        listener = null;
        if (l != null) l.onIntroFinished();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        handler.removeCallbacksAndMessages(null);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) skip();
        return true;
    }

    @Override public boolean hasOverlappingRendering() {
        return true;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = getMeasuredWidth(), h = getMeasuredHeight();
        waves.measure(MeasureSpec.makeMeasureSpec(Math.max(1, (w + SCALE - 1) / SCALE), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, (h + SCALE - 1) / SCALE), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        waves.layout(0, 0, waves.getMeasuredWidth(), waves.getMeasuredHeight());
        waves.setPivotX(0f);
        waves.setPivotY(0f);
        waves.setScaleX((r - l) / (float) Math.max(1, waves.getMeasuredWidth()));
        waves.setScaleY((b - t) / (float) Math.max(1, waves.getMeasuredHeight()));
    }

    /** The aurora bands (small, GPU-scaled hardware layer). */
    static final class Waves extends View {
        private final IntroOverlay owner;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] blobs = new float[IntroWaves.COUNT * IntroWaves.STRIDE];
        private RadialGradient[] shaders;
        static final float R = 100f;

        Waves(Context c, IntroOverlay owner) {
            super(c);
            this.owner = owner;
            setLayerType(LAYER_TYPE_HARDWARE, null);
            shaders = new RadialGradient[owner.palette.length];
            for (int i = 0; i < shaders.length; i++) {
                int col = owner.palette[i] & 0xFFFFFF;
                shaders[i] = new RadialGradient(0f, 0f, R,
                        new int[]{0xFF000000 | col, 0x8C000000 | col, 0x2E000000 | col, col},
                        new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
            }
        }

        void release() {
            shaders = null;
            setLayerType(LAYER_TYPE_NONE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            drawFrame(canvas, SystemClock.uptimeMillis());
        }

        /** Draws the waves as they look at {@code now} (also used for previews/tests). */
        void drawFrame(Canvas canvas, long now) {
            canvas.drawColor(owner.background);
            RadialGradient[] sh = shaders;
            if (sh == null) return;
            float in = owner.timing.wavesAlpha(now) / Math.max(0.001f, owner.timing.backgroundAlpha(now));
            if (in <= 0.001f) return;
            float w = getWidth(), h = getHeight();
            IntroWaves.frame(owner.timing.seconds(now), w, h, blobs);
            for (int k = 0; k < blobs.length; k += IntroWaves.STRIDE) {
                int a = Math.round(255f * Math.min(1f, blobs[k + 4] * in));
                if (a <= 0) continue;
                paint.setShader(sh[(int) blobs[k + 5]]);
                paint.setAlpha(a);
                canvas.save();
                canvas.translate(blobs[k], blobs[k + 1]);
                canvas.scale(blobs[k + 2] / R, blobs[k + 3] / R);
                canvas.drawCircle(0f, 0f, R, paint);
                canvas.restore();
            }
        }
    }

    /** Subtle loading hint (three soft dots), only when loading takes longer. */
    static final class Dots extends View {
        private final IntroOverlay owner;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float alpha;

        Dots(Context c, IntroOverlay owner) {
            super(c);
            this.owner = owner;
            int col = owner.palette[0];
            int mix = AccentTheme.blend(col, 0xFFFFFFFF, 0.6f);
            paint.setColor(mix);
        }

        @Override protected void onDraw(Canvas canvas) {
            if (alpha <= 0f) return;
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() - 56f * d;
            long now = SystemClock.uptimeMillis();
            for (int i = 0; i < 3; i++) {
                float pulse = owner.timing.reduced ? 0.7f
                        : 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(now / 260.0 - i * 0.9));
                paint.setAlpha(Math.round(255f * alpha * pulse * 0.8f));
                canvas.drawCircle(cx + (i - 1) * 14f * d, cy, 3.2f * d, paint);
            }
        }
    }
}
