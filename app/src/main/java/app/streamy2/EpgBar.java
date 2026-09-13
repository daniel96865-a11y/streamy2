package app.streamy2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Lightweight EPG elapsed bar. Fire TV / Android TV hangs if each Live row uses
 * {@link android.widget.ProgressBar} (animated refresh + clip drawable + layout).
 */
public final class EpgBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    /** 0..1 when showing; negative = draw nothing (height still reserved by caller). */
    private float fraction = -1f;

    public EpgBar(Context context) {
        super(context);
        init(context);
    }

    public EpgBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EpgBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_NONE, null);
        track.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
        track.setColor(context.getResources().getColor(R.color.epg_track, context.getTheme()));
        fill.setColor(context.getResources().getColor(R.color.accent, context.getTheme()));
    }

    /** @param value 0..1 fill, or &lt; 0 to hide the fill/track */
    public void setFraction(float value) {
        float next = value;
        if (next >= 0f) {
            if (next > 1f) {
                next = 1f;
            }
        } else {
            next = -1f;
        }
        if (Math.abs(this.fraction - next) < 0.004f) {
            return;
        }
        this.fraction = next;
        invalidate();
    }

    public void hide() {
        setFraction(-1f);
    }

    /**
     * @return 0..1000 progress, or -1 when the bar should be hidden
     */
    static int progressPermille(Models.Epg epg, long now) {
        if (epg == null || epg.start <= 0 || epg.end <= epg.start) {
            return -1;
        }
        if (now < epg.start || now >= epg.end) {
            return -1;
        }
        long duration = epg.end - epg.start;
        long elapsed = now - epg.start;
        int pct = (int) ((elapsed * 1000L) / duration);
        if (pct < 0) {
            return 0;
        }
        if (pct > 1000) {
            return 1000;
        }
        return pct;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || this.fraction < 0f) {
            return;
        }
        float r = h * 0.5f;
        this.rect.set(0f, 0f, w, h);
        canvas.drawRoundRect(this.rect, r, r, this.track);
        if (this.fraction > 0f) {
            this.rect.right = Math.max(r * 2f, w * this.fraction);
            canvas.drawRoundRect(this.rect, r, r, this.fill);
        }
    }
}
