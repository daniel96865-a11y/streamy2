package app.streamy2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Date;
import java.util.List;

/**
 * Start screen: "Läuft gerade" hero, rows (Zuletzt geschaut, Favoriten, Läuft gerade) and
 * category chips. Empty rows are hidden. TV: landscape rows with remote focus (accent glow +
 * slight scale); phone: the same rows in portrait.
 */
final class HomeScreen {
    interface Listener {
        void onHomePlay(Models.Channel channel);
        void onHomeGuide(String categoryId);
        void onHomeCategory(String categoryId);
        void onHomeFavoriteToggle(Models.Channel channel);
    }

    static final float FOCUS_SCALE = 1.07f;

    private final Context ctx;
    private final LinearLayout body;
    private final Listener listener;
    private final boolean tv;
    private final float density;
    private final int accent, accentFg, card, elevated, fg = 0xFFF5F5F7, muted = 0xFF8E8E93;

    HomeScreen(Context ctx, LinearLayout body, Listener listener) {
        this.ctx = ctx;
        this.body = body;
        this.listener = listener;
        this.tv = Tv.isTv(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.accent = AccentTheme.accent(ctx);
        this.accentFg = AccentTheme.color(ctx, R.attr.streamyOnAccent, 0xFF061428);
        this.card = AccentTheme.card(ctx);
        this.elevated = AccentTheme.elevated(ctx);
    }

    /** Current programme: list EPG if it covers now, else the guide. */
    static HomeRows.EpgLookup lookup(final EpgGuide guide) {
        return new HomeRows.EpgLookup() {
            @Override public Models.Epg current(Models.Channel c, long now) {
                if (c == null) return null;
                if (HomeRows.covers(c.epg, now)) return c.epg;
                if (guide == null) return c.epg;
                try {
                    Models.Epg e = guide.forChannel(c);
                    return e != null ? e : c.epg;
                } catch (Throwable t) {
                    return c.epg;
                }
            }
        };
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable focused(int color, float radiusDp) {
        GradientDrawable d = rounded(color, radiusDp);
        d.setStroke(dp(2.5f), accent);
        return d;
    }

    /** Accent glow + slight scale while focused. */
    private void focusEffect(final View v, final int color, final float radius) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(rounded(color, radius));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineSpotShadowColor(accent);
            v.setOutlineAmbientShadowColor(accent);
        }
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.setBackground(has ? focused(color, radius) : rounded(color, radius));
                float s = has ? FOCUS_SCALE : 1f;
                view.animate().scaleX(s).scaleY(s).translationZ(has ? dp(10) : 0).setDuration(140).start();
            }
        });
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(tv ? 15 : 14, primary ? accentFg : fg, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_btn_sec);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMarginEnd(dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    /** Logo with initials placeholder underneath. */
    private FrameLayout logo(Models.Channel c, int w, int h, float textSp) {
        FrameLayout box = new FrameLayout(ctx);
        box.setBackground(rounded(elevated, 12));
        TextView ini = text(textSp, 0xFFFFFFFF, true);
        ini.setGravity(Gravity.CENTER);
        ini.setText(GuideColors.initials(c.name));
        GradientDrawable chip = rounded(GuideColors.forName(c.name), 10);
        ini.setBackground(chip);
        int s = Math.min(w, h) * 2 / 3;
        box.addView(ini, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        ImageView img = new ImageView(ctx);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(8);
        img.setPadding(pad, pad, pad, pad);
        box.addView(img, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
        if (c.logo != null && !c.logo.isEmpty()) {
            Images.load(img, c.logo);
        }
        return box;
    }

    /** Thin EPG progress line. */
    static final class ProgressLine extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        int percent;
        int track = 0x33FFFFFF;
        int color;

        ProgressLine(Context c, int color) {
            super(c);
            this.color = color;
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(), rad = h / 2f;
            paint.setColor(track);
            r.set(0, 0, w, h);
            canvas.drawRoundRect(r, rad, rad, paint);
            if (percent > 0) {
                paint.setColor(color);
                r.set(0, 0, w * Math.min(100, percent) / 100f, h);
                canvas.drawRoundRect(r, rad, rad, paint);
            }
        }
    }

    private ProgressLine progress(int percent) {
        ProgressLine p = new ProgressLine(ctx, accent);
        p.percent = percent;
        return p;
    }

    private String timeRange(Models.Epg e) {
        if (e == null || e.start <= 0 || e.end <= 0) return "";
        java.text.SimpleDateFormat f = EpgTime.format("HH:mm");
        return f.format(new Date(e.start)) + " – " + f.format(new Date(e.end));
    }

    private int side() {
        return dp(tv ? 32 : 16);
    }

    /** Rebuilds the screen. Returns the first focusable view (hero button) or null. */
    View render(HomeRows.Home home, final List<String> favorites, final long now) {
        body.removeAllViews();
        body.setPadding(0, dp(4), 0, dp(tv ? 24 : 96));
        if (home == null || home.isEmpty()) {
            TextView t = text(15, muted, false);
            t.setText("Noch keine Sender. Wiedergabeliste in den Einstellungen hinzufügen.");
            t.setPadding(side(), dp(32), side(), dp(32));
            body.addView(t);
            return null;
        }
        View first = null;
        if (home.hero != null) first = hero(home.hero, home.heroEpg, favorites, now);
        View firstChip = chips(home.chips);
        for (HomeRows.Row row : home.rows) addRow(row, now);
        return first != null ? first : firstChip;
    }

    private View hero(final Models.Channel c, Models.Epg e, List<String> favorites, long now) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(tv ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setBackground(rounded(this.card, tv ? 22 : 18));
        int pad = dp(tv ? 24 : 16);
        card.setPadding(pad, pad, pad, pad);
        card.setGravity(tv ? Gravity.CENTER_VERTICAL : Gravity.START);

        int lw = dp(tv ? 220 : 120), lh = dp(tv ? 124 : 68);
        FrameLayout logo = logo(c, lw, lh, tv ? 30 : 20);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(lw, lh);
        if (tv) llp.setMarginEnd(dp(24));
        else llp.bottomMargin = dp(12);
        card.addView(logo, llp);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(tv ? 13 : 12, accent, true);
        label.setText(HomeRows.covers(e, now) ? "LÄUFT GERADE" : "ZULETZT GESCHAUT");
        label.setLetterSpacing(0.08f);
        col.addView(label);
        TextView ch = text(tv ? 15 : 14, muted, false);
        ch.setText(Text.clean(c.name) + (c.number > 0 ? "  ·  " + c.number : ""));
        col.addView(ch);
        TextView title = text(tv ? 28 : 21, fg, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        boolean live = HomeRows.covers(e, now);
        title.setText(live && e.title != null && !e.title.isEmpty() ? Text.clean(e.title) : Text.clean(c.name));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(2);
        col.addView(title, tlp);
        if (live) {
            TextView meta = text(tv ? 14 : 13, muted, false);
            String next = e.nextTitle != null && !e.nextTitle.isEmpty() ? "  ·  Danach: " + Text.clean(e.nextTitle) : "";
            meta.setText(timeRange(e) + "  ·  " + HomeRows.remaining(e, now) + next);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(meta);
            ProgressLine p = progress(HomeRows.progress(e, now));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            plp.topMargin = dp(10);
            plp.setMarginEnd(dp(tv ? 120 : 0));
            col.addView(p, plp);
        }
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView watch = button("Jetzt ansehen", true);
        TextView guide = button("Programm", false);
        final boolean fav = favorites != null && favorites.contains(HomeRows.key(c));
        TextView favBtn = button(fav ? "Favorit entfernen" : "Favorit", false);
        buttons.addView(watch);
        buttons.addView(guide);
        buttons.addView(favBtn);
        HorizontalScrollView bs = new HorizontalScrollView(ctx);
        bs.setHorizontalScrollBarEnabled(false);
        bs.setClipToPadding(false);
        bs.addView(buttons);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(14);
        col.addView(bs, blp);
        card.addView(col, new LinearLayout.LayoutParams(tv ? 0 : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, tv ? 1f : 0f));

        watch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                listener.onHomePlay(c);
            }
        });
        guide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                listener.onHomeGuide(null);
            }
        });
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                listener.onHomeFavoriteToggle(c);
            }
        });

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(side(), dp(8), side(), dp(4));
        body.addView(card, clp);
        return watch;
    }

    private View chips(List<HomeRows.Chip> chips) {
        if (chips == null || chips.isEmpty()) return null;
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(side(), dp(14), side(), dp(4));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);
        TextView guide = chip("Programm", true);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                listener.onHomeGuide(null);
            }
        });
        row.addView(guide);
        for (final HomeRows.Chip c : chips) {
            TextView t = chip(c.name + "  " + c.count, false);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onHomeCategory(c.id);
                }
            });
            row.addView(t);
        }
        body.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return guide;
    }

    private TextView chip(String label, boolean primary) {
        TextView t = text(tv ? 14 : 13, primary ? accentFg : fg, primary);
        t.setText(label);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_chip);
        t.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(tv ? 38 : 36));
        lp.setMarginEnd(dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    private void addRow(HomeRows.Row row, long now) {
        if (row.channels == null || row.channels.isEmpty()) return;
        TextView title = text(tv ? 18 : 17, fg, true);
        title.setText(row.title);
        title.setPadding(side(), dp(16), side(), dp(6));
        body.addView(title);
        RecyclerView rv = new RecyclerView(ctx);
        rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
        rv.setClipToPadding(false);
        rv.setClipChildren(false);
        int vpad = dp(tv ? 10 : 4);
        rv.setPadding(side() - dp(6), vpad, side(), vpad);
        rv.setHasFixedSize(true);
        rv.setNestedScrollingEnabled(false);
        rv.setAdapter(new TileAdapter(row.channels, now));
        body.setClipChildren(false);
        body.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    final class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {
        private final List<Models.Channel> items;
        private final long now;
        private final HomeRows.EpgLookup epg = lookup(App.guide);

        TileAdapter(List<Models.Channel> items, long now) {
            this.items = items;
            this.now = now;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout root;
            final FrameLayout logoBox;
            final TextView name, programme, time;
            final ProgressLine bar;

            Holder(LinearLayout root, FrameLayout logoBox, TextView name, TextView programme, TextView time, ProgressLine bar) {
                super(root);
                this.root = root;
                this.logoBox = logoBox;
                this.name = name;
                this.programme = programme;
                this.time = time;
                this.bar = bar;
            }
        }

        @Override public int getItemCount() {
            return items.size();
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            int w = dp(tv ? 220 : 150);
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            int p = dp(10);
            root.setPadding(p, p, p, p);
            focusEffect(root, card, 16);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            root.setLayoutParams(lp);
            FrameLayout logoBox = new FrameLayout(ctx);
            root.addView(logoBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(tv ? 96 : 78)));
            TextView name = text(tv ? 15 : 14, fg, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(8);
            root.addView(name, nlp);
            TextView programme = text(tv ? 13 : 12, 0xFFC7C7CC, false);
            programme.setSingleLine(true);
            programme.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(programme);
            TextView time = text(tv ? 12 : 11, muted, false);
            time.setSingleLine(true);
            root.addView(time);
            ProgressLine bar = progress(0);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            blp.topMargin = dp(6);
            root.addView(bar, blp);
            return new Holder(root, logoBox, name, programme, time, bar);
        }

        @Override public void onBindViewHolder(final Holder h, int position) {
            final Models.Channel c = items.get(position);
            h.logoBox.removeAllViews();
            int w = dp(tv ? 200 : 130), hh = dp(tv ? 96 : 78);
            View logo = logo(c, w, hh, tv ? 22 : 18);
            h.logoBox.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            h.name.setText(Text.clean(c.name) + (c.number > 0 && tv ? "  ·  " + c.number : ""));
            Models.Epg e = epg.current(c, now);
            boolean live = HomeRows.covers(e, now);
            h.programme.setText(live && e.title != null ? Text.clean(e.title) : "Keine Programmdaten");
            h.time.setText(live ? timeRange(e) + "  ·  " + HomeRows.remaining(e, now) : "");
            h.time.setVisibility(live ? View.VISIBLE : View.GONE);
            h.bar.percent = live ? HomeRows.progress(e, now) : 0;
            h.bar.setVisibility(live ? View.VISIBLE : View.INVISIBLE);
            h.bar.invalidate();
            h.root.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onHomePlay(c);
                }
            });
            h.root.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    listener.onHomeFavoriteToggle(c);
                    return true;
                }
            });
        }
    }
}
