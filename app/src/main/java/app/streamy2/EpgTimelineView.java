package app.streamy2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * Timeline programme guide drawn on a canvas: channels on the left, half-hour time axis,
 * programme blocks sized by duration, red "Jetzt" line, focused block with accent border.
 * Only visible rows/blocks are drawn and programme lists are loaded lazily per row, so big
 * playlists stay fast on low-RAM devices. DPAD (TV) and touch scrolling (phone).
 */
public class EpgTimelineView extends View {
    /** Programmes of one channel, sorted by start. */
    public interface RowSource {
        List<EpgGuide.Listing> listings(Models.Channel channel);
    }

    public interface Listener {
        void onFocusChanged(Models.Channel channel, EpgGuide.Listing listing);
        void onOpen(Models.Channel channel, EpgGuide.Listing listing);
        void onVisibleTime(long centerTime);
    }

    private final float density;
    private final boolean tv;
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint chName = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint initials = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tick = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nowText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final OverScroller scroller;
    private final GestureDetector gestures;

    private final List<Models.Channel> channels = new ArrayList<>();
    private RowSource source;
    private Listener listener;
    private final LruCache<String, List<EpgGuide.Listing>> rows = new LruCache<>(120);
    private final HashSet<String> logoLoading = new HashSet<>();

    final int colW, rowH, headerH, pad, radius;
    float pxPerMin;
    long origin;
    long end;
    float scrollX, scrollY;
    int focusRow = 0;
    long focusAnchor;
    EpgGuide.Listing focused;
    private long now = System.currentTimeMillis();

    final int accent, accentFg, bg, surface, card, elevated, past, live, fg, muted, subtle, danger;

    public EpgTimelineView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        tv = Tv.isTv(context);
        colW = dp(tv ? 190 : 104);
        rowH = dp(tv ? 46 : 58);
        headerH = dp(tv ? 34 : 34);
        pad = dp(tv ? 10 : 8);
        // Sensible scale before the first layout (focus/scroll calls may come earlier).
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        pxPerMin = Math.max(dp(100), screenW - colW - pad) / (tv ? 180f : 110f);
        radius = dp(tv ? 9 : 10);
        accent = AccentTheme.accent(context);
        accentFg = AccentTheme.color(context, R.attr.streamyOnAccent, 0xFF061428);
        bg = AccentTheme.background(context);
        card = AccentTheme.card(context);
        elevated = AccentTheme.elevated(context);
        surface = AccentTheme.color(context, R.attr.streamySurface, 0xFF18191E);
        past = AccentTheme.blend(card, bg, 0.6f);
        live = AccentTheme.blend(elevated, card, 0.55f);
        fg = 0xFFF5F5F7;
        muted = 0xFF8E8E93;
        subtle = 0xFF636366;
        danger = 0xFFFF6B6B;
        title.setColor(fg);
        title.setTextSize(sp(tv ? 15 : 14));
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        sub.setColor(muted);
        sub.setTextSize(sp(tv ? 12.5f : 12));
        chName.setColor(fg);
        chName.setTextSize(sp(tv ? 15 : 12));
        chName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        initials.setColor(0xFFFFFFFF);
        initials.setTextAlign(Paint.Align.CENTER);
        initials.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        tick.setColor(muted);
        tick.setTextSize(sp(tv ? 14 : 12.5f));
        nowText.setColor(0xFF2A0606);
        nowText.setTextSize(sp(tv ? 12 : 11.5f));
        nowText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        nowText.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        scroller = new OverScroller(context);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                scrollBy(dx, dy);
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                scroller.fling((int) scrollX, (int) scrollY, (int) -vx, (int) -vy,
                        0, (int) maxScrollX(), 0, (int) maxScrollY());
                postInvalidateOnAnimation();
                return true;
            }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                tapAt(e.getX(), e.getY());
                return true;
            }
        });
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setBackgroundColor(bg);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    // --- data ---

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** Channels, row source and the time axis [from, to]. */
    public void setData(List<Models.Channel> list, RowSource rowSource, long from, long to) {
        channels.clear();
        if (list != null) channels.addAll(list);
        source = rowSource;
        rows.evictAll();
        origin = Timeline.floorHalfHour(from);
        end = Math.max(to, origin + Timeline.DAY);
        requestLayout();
        invalidate();
    }

    List<EpgGuide.Listing> row(int i) {
        if (i < 0 || i >= channels.size()) return Collections.emptyList();
        Models.Channel c = channels.get(i);
        String key = i + "|" + GuideActions.key(c);
        List<EpgGuide.Listing> l = rows.get(key);
        if (l == null) {
            l = source == null ? null : source.listings(c);
            loads++;
            if (l == null) l = Collections.emptyList();
            rows.put(key, l);
        }
        return l;
    }

    private int loads;

    /** Number of rows whose programmes were fetched so far (lazy loading; tests). */
    int loadedRows() {
        return loads;
    }

    public Models.Channel focusedChannel() {
        return focusRow >= 0 && focusRow < channels.size() ? channels.get(focusRow) : null;
    }

    public EpgGuide.Listing focusedListing() {
        return focused;
    }

    public int channelCount() {
        return channels.size();
    }

    // --- geometry ---

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float grid = Math.max(dp(100), w - colW - pad);
        pxPerMin = grid / (tv ? 180f : 110f);
        scrollX = Timeline.clamp(scrollX, 0, maxScrollX());
        scrollY = Timeline.clamp(scrollY, 0, maxScrollY());
    }

    float gridLeft() {
        return colW + pad;
    }

    float gridWidth() {
        return Math.max(1, getWidth() - gridLeft());
    }

    float gridHeight() {
        return Math.max(1, getHeight() - headerH);
    }

    float maxScrollX() {
        return Math.max(0, Timeline.x(end, origin, pxPerMin) - gridWidth());
    }

    float maxScrollY() {
        return Math.max(0, channels.size() * (float) rowH - gridHeight());
    }

    void scrollBy(float dx, float dy) {
        scrollX = Timeline.clamp(scrollX + dx, 0, maxScrollX());
        scrollY = Timeline.clamp(scrollY + dy, 0, maxScrollY());
        notifyVisibleTime();
        invalidate();
    }

    private void smoothTo(float x, float y) {
        x = Timeline.clamp(x, 0, maxScrollX());
        y = Timeline.clamp(y, 0, maxScrollY());
        scroller.forceFinished(true);
        scroller.startScroll((int) scrollX, (int) scrollY, (int) (x - scrollX), (int) (y - scrollY), 220);
        postInvalidateOnAnimation();
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = Timeline.clamp(scroller.getCurrX(), 0, maxScrollX());
            scrollY = Timeline.clamp(scroller.getCurrY(), 0, maxScrollY());
            notifyVisibleTime();
            postInvalidateOnAnimation();
        }
    }

    private long lastNotified;

    private void notifyVisibleTime() {
        if (listener == null || pxPerMin <= 0) return;
        long center = Timeline.timeAt(scrollX + gridWidth() / 2f, origin, pxPerMin);
        if (Math.abs(center - lastNotified) > 10 * Timeline.MIN) {
            lastNotified = center;
            listener.onVisibleTime(center);
        }
    }

    /** Show {@code time} near the left edge (half an hour before), keeping the row. */
    public void scrollToTime(long time, boolean smooth) {
        float x = Timeline.x(time, origin, pxPerMin) - 30 * pxPerMin;
        if (smooth) smoothTo(x, scrollY);
        else {
            scrollX = Timeline.clamp(x, 0, maxScrollX());
            invalidate();
        }
        notifyVisibleTime();
    }

    /** Focus the programme at {@code time} in {@code rowIndex}. */
    public void focusAt(int rowIndex, long time, boolean smooth) {
        if (channels.isEmpty()) return;
        focusRow = Timeline.vertical(channels.size(), rowIndex, 0);
        focusAnchor = time;
        List<EpgGuide.Listing> l = row(focusRow);
        int i = Timeline.indexAt(l, time);
        focused = i >= 0 ? l.get(i) : null;
        ensureFocusVisible(smooth);
        fireFocus();
        invalidate();
    }

    private void ensureFocusVisible(boolean smooth) {
        float y = Timeline.scrollToRow(scrollY, gridHeight(), rowH, focusRow);
        float x = scrollX;
        if (focused != null) {
            float[] b = Timeline.block(focused.start, focused.stop, origin, pxPerMin);
            x = Timeline.scrollToShow(scrollX, gridWidth(), b[0], b[1], dp(24));
        } else {
            x = Timeline.x(focusAnchor, origin, pxPerMin) - gridWidth() / 3f;
        }
        if (smooth) smoothTo(x, y);
        else {
            scrollX = Timeline.clamp(x, 0, maxScrollX());
            scrollY = Timeline.clamp(y, 0, maxScrollY());
        }
    }

    private void fireFocus() {
        if (listener != null) listener.onFocusChanged(focusedChannel(), focused);
    }

    // --- input ---

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (channels.isEmpty()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dx = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                List<EpgGuide.Listing> l = row(focusRow);
                if (l.isEmpty()) {
                    focusAnchor += dx * Timeline.HALF_HOUR;
                    focusAnchor = Math.max(origin, Math.min(end, focusAnchor));
                    ensureFocusVisible(true);
                    fireFocus();
                    invalidate();
                    return true;
                }
                int cur = focused == null ? Timeline.indexAt(l, focusAnchor) : l.indexOf(focused);
                if (cur < 0) cur = Timeline.indexAt(l, focusAnchor);
                int next = Timeline.horizontal(l, cur, dx);
                if (next == cur && focused != null) return true; // edge of the data
                focused = l.get(next);
                focusAnchor = Math.max(focused.start, origin);
                ensureFocusVisible(true);
                fireFocus();
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int dy = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                int next = Timeline.vertical(channels.size(), focusRow, dy);
                if (next == focusRow) {
                    return dy < 0 ? super.onKeyDown(keyCode, event) : true; // up leaves to the info panel
                }
                long viewStart = Timeline.timeAt(scrollX, origin, pxPerMin);
                long anchor = Timeline.anchor(focused, viewStart);
                if (focused == null) anchor = focusAnchor;
                focusRow = next;
                List<EpgGuide.Listing> l = row(focusRow);
                int i = Timeline.indexAt(l, anchor);
                focused = i >= 0 ? l.get(i) : null;
                focusAnchor = anchor;
                ensureFocusVisible(true);
                fireFocus();
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP: {
                int page = Math.max(1, (int) (gridHeight() / rowH) - 1);
                boolean down = keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN;
                focusAt(focusRow + (down ? page : -page), focusAnchor, true);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (listener != null) listener.onOpen(focusedChannel(), focused);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return handled || super.onTouchEvent(event);
    }

    private void tapAt(float x, float y) {
        if (y < headerH) return;
        int r = (int) ((y - headerH + scrollY) / rowH);
        if (r < 0 || r >= channels.size()) return;
        if (x < colW) {
            // Channel cell: play the channel live.
            focusRow = r;
            focused = null;
            List<EpgGuide.Listing> l = row(r);
            int i = Timeline.indexAt(l, now);
            if (i >= 0 && l.get(i).start <= now && now < l.get(i).stop) focused = l.get(i);
            fireFocus();
            invalidate();
            if (listener != null) listener.onOpen(focusedChannel(), focused);
            return;
        }
        long t = Timeline.timeAt(x - gridLeft() + scrollX, origin, pxPerMin);
        List<EpgGuide.Listing> l = row(r);
        EpgGuide.Listing hit = null;
        for (EpgGuide.Listing it : l) {
            if (it.start <= t && t < it.stop) {
                hit = it;
                break;
            }
        }
        boolean again = hit != null && hit == focused && r == focusRow;
        focusRow = r;
        focused = hit;
        focusAnchor = t;
        fireFocus();
        invalidate();
        if (again && listener != null) listener.onOpen(focusedChannel(), focused);
    }

    // --- drawing ---

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            now = System.currentTimeMillis();
            invalidate();
            postDelayed(this, 30_000L);
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(clockTick);
        postDelayed(clockTick, 30_000L);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(clockTick);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pxPerMin <= 0) return;
        now = System.currentTimeMillis();
        int w = getWidth();
        float gl = gridLeft();
        java.text.SimpleDateFormat hm = EpgTime.format("HH:mm");

        // Rows (lazy: only the visible range).
        int[] vis = Timeline.visibleRows(scrollY, gridHeight(), rowH, channels.size());
        long tFrom = Timeline.timeAt(scrollX, origin, pxPerMin);
        long tTo = Timeline.timeAt(scrollX + gridWidth(), origin, pxPerMin);
        canvas.save();
        canvas.clipRect(0, headerH, w, getHeight());
        float focusL = 0, focusT = 0, focusW = 0;
        boolean drawFocus = false;
        for (int r = vis[0]; r <= vis[1]; r++) {
            float top = headerH + r * (float) rowH - scrollY;
            drawChannel(canvas, channels.get(r), top, r == focusRow);
            List<EpgGuide.Listing> l = row(r);
            canvas.save();
            canvas.clipRect(gl, top, w, top + rowH);
            if (l.isEmpty()) {
                rect.set(gl + 2, top + 2, w - 2, top + rowH - 4);
                fill.setColor(past);
                canvas.drawRoundRect(rect, radius, radius, fill);
                sub.setColor(subtle);
                canvas.drawText("Keine Programmdaten", gl + pad + 6, top + rowH / 2f + sub.getTextSize() / 3f, sub);
                sub.setColor(muted);
                if (r == focusRow && isFocused()) {
                    drawFocus = true;
                    focusL = gl + 2; focusT = top + 2; focusW = w - gl - 4;
                }
            }
            for (EpgGuide.Listing it : l) {
                if (it.stop <= tFrom || it.start >= tTo) continue;
                float[] b = Timeline.block(it.start, it.stop, origin, pxPerMin);
                float left = gl + b[0] - scrollX + 2;
                boolean isFocus = r == focusRow && it == focused;
                if (isFocus) {
                    drawFocus = true;
                    focusL = left; focusT = top + 2; focusW = b[1];
                    continue; // drawn last, above neighbours
                }
                drawBlock(canvas, it, left, top + 2, b[1], false, hm);
            }
            canvas.restore();
        }
        if (drawFocus) {
            canvas.save();
            canvas.clipRect(gl - dp(4), headerH, w, getHeight());
            if (focused != null) drawBlock(canvas, focused, focusL, focusT, focusW, true, hm);
            else drawFocusFrame(canvas, focusL, focusT, focusW);
            canvas.restore();
        }
        canvas.restore();

        // Header: time axis.
        fill.setColor(bg);
        canvas.drawRect(0, 0, w, headerH, fill);
        for (long t : Timeline.ticks(tFrom - Timeline.HALF_HOUR, tTo)) {
            float x = gl + Timeline.x(t, origin, pxPerMin) - scrollX;
            if (x < gl - 1 || x > w) continue;
            fill.setColor(0x14FFFFFF);
            canvas.drawRect(x, headerH * 0.18f, x + Math.max(1, density), headerH * 0.95f, fill);
            canvas.drawText(hm.format(new Date(t)), x + dp(6), headerH * 0.62f, tick);
        }
        // "Jetzt" line + label.
        float nx = Timeline.nowLine(now, origin, pxPerMin, scrollX, gridWidth());
        if (!Float.isNaN(nx)) {
            float x = gl + nx;
            fill.setColor(danger);
            canvas.drawRect(x - density, headerH * 0.75f, x + density, getHeight(), fill);
            String label = "Jetzt " + hm.format(new Date(now));
            float tw = nowText.measureText(label) + dp(14);
            float cx = Timeline.clamp(x, gl + tw / 2f, w - tw / 2f);
            rect.set(cx - tw / 2f, headerH * 0.1f, cx + tw / 2f, headerH * 0.1f + nowText.getTextSize() + dp(8));
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fill);
            canvas.drawText(label, cx, rect.centerY() + nowText.getTextSize() / 3f, nowText);
        }
        // Channel column mask in the header.
        fill.setColor(bg);
        canvas.drawRect(0, 0, gl - 1, headerH, fill);
        tick.setColor(fg);
        canvas.drawText(Timeline.dayLabel(Timeline.dayStart(Timeline.timeAt(scrollX + gridWidth() / 2f, origin, pxPerMin)), now),
                dp(4), headerH * 0.62f, tick);
        tick.setColor(muted);
    }

    private void drawChannel(Canvas c, Models.Channel ch, float top, boolean current) {
        rect.set(0, top + 2, colW, top + rowH - 4);
        fill.setColor(current ? AccentTheme.blend(accent, surface, 0.10f) : surface);
        c.drawRoundRect(rect, radius, radius, fill);
        if (current) {
            stroke.setColor(AccentTheme.withAlpha(accent, 110));
            stroke.setStrokeWidth(density);
            c.drawRoundRect(rect, radius, radius, stroke);
        }
        float logo = rowH - dp(tv ? 14 : 16);
        float lx = dp(tv ? 34 : 6);
        float ly = top + (rowH - 2 - logo) / 2f;
        if (tv && ch != null && ch.number > 0) {
            sub.setColor(subtle);
            c.drawText(String.format(java.util.Locale.GERMANY, "%02d", ch.number), dp(8), top + rowH / 2f + sub.getTextSize() / 3f, sub);
            sub.setColor(muted);
        }
        drawLogo(c, ch, lx, ly, logo);
        float tx = lx + logo + dp(tv ? 10 : 6);
        float avail = colW - tx - dp(6);
        if (avail > dp(20) && ch != null) {
            CharSequence n = TextUtils.ellipsize(Text.clean(ch.name), chName, avail, TextUtils.TruncateAt.END);
            c.drawText(n, 0, n.length(), tx, top + rowH / 2f + chName.getTextSize() / 3f, chName);
        }
    }

    private void drawLogo(Canvas c, Models.Channel ch, float x, float y, float size) {
        rect.set(x, y, x + size, y + size);
        Bitmap b = ch == null ? null : Images.cachedBitmap(ch.logo);
        if (b != null) {
            fill.setColor(0xFF26272D);
            c.drawRoundRect(rect, size * 0.22f, size * 0.22f, fill);
            float s = Math.min((size - 4) / b.getWidth(), (size - 4) / b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            src.set(0, 0, b.getWidth(), b.getHeight());
            RectF dst = new RectF(x + (size - bw) / 2f, y + (size - bh) / 2f, x + (size + bw) / 2f, y + (size + bh) / 2f);
            c.drawBitmap(b, src, dst, fill);
            return;
        }
        int color = GuideColors.forName(ch == null ? "" : ch.name);
        fill.setColor(color);
        c.drawRoundRect(rect, size * 0.22f, size * 0.22f, fill);
        initials.setTextSize(size * 0.36f);
        c.drawText(GuideColors.initials(ch == null ? "" : ch.name), x + size / 2f, y + size / 2f + initials.getTextSize() / 3f, initials);
        if (ch != null && ch.logo != null && !ch.logo.isEmpty() && logoLoading.add(ch.logo)) {
            Images.loadBitmap(getContext(), ch.logo, new Images.BitmapCallback() {
                @Override public void onBitmap(String url, Bitmap bitmap) {
                    if (bitmap != null) invalidate();
                }
            });
        }
    }

    private void drawBlock(Canvas c, EpgGuide.Listing it, float left, float top, float width, boolean focus, java.text.SimpleDateFormat hm) {
        float h = rowH - 6;
        boolean isPast = it.stop <= now;
        boolean isNow = it.start <= now && now < it.stop;
        if (focus) {
            float grow = dp(3);
            rect.set(left - grow, top - grow / 2f, left + width + grow, top + h + grow / 2f);
            fill.setColor(AccentTheme.withAlpha(accent, 46));
            RectF glow = new RectF(rect.left - dp(5), rect.top - dp(5), rect.right + dp(5), rect.bottom + dp(5));
            c.drawRoundRect(glow, radius + dp(5), radius + dp(5), fill);
            fill.setColor(AccentTheme.blend(accent, elevated, 0.22f));
        } else {
            rect.set(left, top, left + width, top + h);
            fill.setColor(isPast ? past : (isNow ? live : card));
        }
        c.drawRoundRect(rect, radius, radius, fill);
        if (isNow && !focus) {
            float p = GuideActions.progress(epgOf(it), now) / 100f;
            fill.setColor(AccentTheme.withAlpha(accent, 180));
            c.drawRect(rect.left + 1, rect.bottom - dp(3), rect.left + 1 + (rect.width() - 2) * p, rect.bottom, fill);
        }
        if (focus) {
            stroke.setColor(accent);
            stroke.setStrokeWidth(dp(2.5f));
            c.drawRoundRect(rect, radius, radius, stroke);
        }
        float textLeft = Math.max(rect.left, gridLeft()) + pad;
        float avail = rect.right - textLeft - pad;
        if (avail < dp(14)) return;
        title.setColor(isPast && !focus ? 0xFF9A9AA0 : fg);
        CharSequence t = TextUtils.ellipsize(Text.clean(it.title), title, avail, TextUtils.TruncateAt.END);
        float base = rect.top + h * 0.44f;
        c.drawText(t, 0, t.length(), textLeft, base, title);
        CharSequence s = TextUtils.ellipsize(hm.format(new Date(it.start)) + " – " + hm.format(new Date(it.stop)), sub, avail, TextUtils.TruncateAt.END);
        c.drawText(s, 0, s.length(), textLeft, base + sub.getTextSize() * 1.35f, sub);
    }

    private void drawFocusFrame(Canvas c, float left, float top, float width) {
        rect.set(left, top, left + width, top + rowH - 6);
        stroke.setColor(accent);
        stroke.setStrokeWidth(dp(2.5f));
        c.drawRoundRect(rect, radius, radius, stroke);
    }

    private static Models.Epg epgOf(EpgGuide.Listing l) {
        Models.Epg e = new Models.Epg();
        e.start = l.start;
        e.end = l.stop;
        e.title = l.title;
        return e;
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }
}
