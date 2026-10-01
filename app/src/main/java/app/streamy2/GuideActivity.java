package app.streamy2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Programme guide as a timeline grid (TV and phone). */
public class GuideActivity extends AppCompatActivity implements EpgTimelineView.Listener {
    static final String EXTRA_CATEGORY = "category";

    private EpgTimelineView grid;
    private TextView infoChannel, infoTitle, infoMeta, infoDesc;
    private TextView btnWatch, btnStart;
    private LinearLayout dayRow;
    private final List<Integer> days = new ArrayList<>();
    private final List<TextView> dayChips = new ArrayList<>();
    private boolean tv;
    private int accent, accentFg;

    public static void open(Activity from, String categoryId) {
        Intent i = new Intent(from, GuideActivity.class);
        if (categoryId != null) i.putExtra(EXTRA_CATEGORY, categoryId);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AccentTheme.apply(this);
        super.onCreate(savedInstanceState);
        tv = Tv.isTv(this);
        accent = AccentTheme.accent(this);
        accentFg = AccentTheme.color(this, R.attr.streamyOnAccent, 0xFF061428);
        setContentView(build());
        load();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return t;
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(tv ? 15 : 14, primary ? accentFg : 0xFFF5F5F7, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_btn_sec);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(tv ? 40 : 40));
        lp.setMarginEnd(dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AccentTheme.background(this));
        int side = dp(tv ? 32 : 14);
        root.setPadding(side, dp(tv ? 18 : 12), side, dp(tv ? 12 : 0));

        // Header: title + day switcher.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(tv ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        head.setGravity(tv ? Gravity.CENTER_VERTICAL : Gravity.START);
        TextView title = text(tv ? 22 : 20, 0xFFF5F5F7, true);
        title.setText("Programm");
        head.addView(title, new LinearLayout.LayoutParams(tv ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, tv ? 1f : 0f));
        HorizontalScrollView dayScroll = new HorizontalScrollView(this);
        dayScroll.setHorizontalScrollBarEnabled(false);
        dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        dayScroll.addView(dayRow);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!tv) dlp.topMargin = dp(8);
        head.addView(dayScroll, dlp);
        root.addView(head);

        // Info panel for the focused programme.
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AccentTheme.card(this));
        bg.setCornerRadius(dp(tv ? 18 : 16));
        info.setBackground(bg);
        info.setPadding(dp(tv ? 22 : 16), dp(tv ? 16 : 14), dp(tv ? 22 : 16), dp(tv ? 16 : 14));
        infoChannel = text(tv ? 14 : 13, 0xFF8E8E93, false);
        infoTitle = text(tv ? 26 : 20, 0xFFF5F5F7, true);
        infoTitle.setSingleLine(true);
        infoTitle.setEllipsize(TextUtils.TruncateAt.END);
        infoMeta = text(tv ? 14 : 13, 0xFF8E8E93, false);
        infoDesc = text(tv ? 15 : 14, 0xFFC7C7CC, false);
        infoDesc.setMaxLines(tv ? 2 : 3);
        infoDesc.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(infoChannel);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        info.addView(infoTitle, tlp);
        info.addView(infoMeta);
        LinearLayout.LayoutParams dsc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dsc.topMargin = dp(6);
        info.addView(infoDesc, dsc);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        btnWatch = button("Jetzt ansehen", true);
        btnStart = button("Von Anfang an", false);
        buttons.addView(btnWatch);
        buttons.addView(btnStart);
        HorizontalScrollView bscroll = new HorizontalScrollView(this);
        bscroll.setHorizontalScrollBarEnabled(false);
        bscroll.addView(buttons);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);
        info.addView(bscroll, blp);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(tv ? 12 : 10);
        ilp.bottomMargin = dp(tv ? 10 : 8);
        root.addView(info, ilp);

        grid = new EpgTimelineView(this);
        grid.setListener(this);
        grid.setId(View.generateViewId());
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        btnWatch.setNextFocusDownId(grid.getId());
        btnStart.setNextFocusDownId(grid.getId());

        btnWatch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Models.Channel c = grid.focusedChannel();
                if (c != null) LivePlay.live(GuideActivity.this, c, currentEpg(c));
            }
        });
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Models.Channel c = grid.focusedChannel();
                EpgGuide.Listing l = grid.focusedListing();
                if (LivePlay.canCatchup(c, l, System.currentTimeMillis())) LivePlay.catchup(GuideActivity.this, c, l);
            }
        });
        return root;
    }

    /** Channels of the playlist (optionally one category), without section headers. */
    static List<Models.Channel> guideChannels(List<Models.Channel> all, String categoryId) {
        ArrayList<Models.Channel> out = new ArrayList<>();
        if (all == null) return out;
        boolean filter = categoryId != null && !categoryId.isEmpty() && !"all".equals(categoryId);
        for (Models.Channel c : all) {
            if (c == null || c.header) continue;
            if (filter && !categoryId.equals(c.categoryId)) continue;
            out.add(c);
        }
        return out;
    }

    /** Listings for a channel: full guide data, else the current programme from the list. */
    static List<EpgGuide.Listing> listingsFor(EpgGuide guide, Models.Channel c) {
        List<EpgGuide.Listing> l = guide == null ? null : guide.listingsFor(c);
        if (l != null && !l.isEmpty()) return l;
        ArrayList<EpgGuide.Listing> out = new ArrayList<>();
        if (c != null && c.epg != null && c.epg.start > 0 && c.epg.end > c.epg.start) {
            EpgGuide.Listing it = new EpgGuide.Listing();
            it.start = c.epg.start;
            it.stop = c.epg.end;
            it.title = c.epg.title == null ? "" : c.epg.title;
            out.add(it);
        }
        return out;
    }

    private Models.Epg currentEpg(Models.Channel c) {
        List<EpgGuide.Listing> l = listingsFor(App.guide, c);
        EpgGuide.Listing cur = EpgTime.current(l, System.currentTimeMillis());
        if (cur == null) return c.epg;
        Models.Epg e = new Models.Epg();
        e.start = cur.start;
        e.end = cur.stop;
        e.title = cur.title;
        return e;
    }

    private void load() {
        final EpgGuide guide = App.guide;
        List<Models.Channel> list = guideChannels(App.live, getIntent().getStringExtra(EXTRA_CATEGORY));
        long now = System.currentTimeMillis();
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        int sample = 0;
        for (Models.Channel c : list) {
            if (++sample > 80) break;
            List<EpgGuide.Listing> l = listingsFor(guide, c);
            if (l.isEmpty()) continue;
            min = Math.min(min, l.get(0).start);
            max = Math.max(max, l.get(l.size() - 1).stop);
        }
        long from = Timeline.dayStart(now, -7), to = Timeline.dayStart(now, 8);
        if (min == Long.MAX_VALUE) {
            min = Timeline.dayStart(now);
            max = Timeline.dayStart(now, 1);
        }
        long axisFrom = Math.max(from, Math.min(min, Timeline.dayStart(now)));
        long axisTo = Math.min(to, Math.max(max, Timeline.dayStart(now, 1)));
        grid.setData(list, new EpgTimelineView.RowSource() {
            @Override public List<EpgGuide.Listing> listings(Models.Channel channel) {
                return listingsFor(guide, channel);
            }
        }, axisFrom, axisTo);
        days.clear();
        days.addAll(Timeline.days(min, max, now, 7, 7));
        buildDays(now);
        int row = 0;
        if (App.playing != null) {
            int i = list.indexOf(App.playing);
            if (i >= 0) row = i;
        }
        final int startRow = row;
        grid.post(new Runnable() {
            @Override public void run() {
                grid.scrollToTime(System.currentTimeMillis(), false);
                grid.focusAt(startRow, System.currentTimeMillis(), false);
                grid.requestFocus();
            }
        });
        if (list.isEmpty()) {
            infoTitle.setText("Keine Sender");
            infoMeta.setText("Bitte zuerst eine Wiedergabeliste laden.");
            btnWatch.setVisibility(View.GONE);
            btnStart.setVisibility(View.GONE);
        }
    }

    private void buildDays(long now) {
        dayRow.removeAllViews();
        dayChips.clear();
        for (final int d : days) {
            TextView chip = text(tv ? 14 : 13, 0xFFF5F5F7, false);
            chip.setText(Timeline.dayLabel(Timeline.dayStart(now, d), now));
            chip.setBackgroundResource(R.drawable.bg_btn_sec);
            chip.setGravity(Gravity.CENTER);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setPadding(dp(14), 0, dp(14), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(tv ? 36 : 34));
            lp.setMarginStart(dp(8));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    long t = System.currentTimeMillis();
                    long target = d == 0 ? t : Timeline.dayStart(t, d) + 18 * 60 * Timeline.MIN;
                    grid.focusAt(grid.focusRow, target, true);
                    grid.scrollToTime(target, true);
                    paintDays(d);
                }
            });
            dayRow.addView(chip, lp);
            dayChips.add(chip);
        }
        paintDays(0);
    }

    private void paintDays(int selected) {
        for (int i = 0; i < dayChips.size(); i++) {
            boolean on = days.get(i) == selected;
            TextView c = dayChips.get(i);
            c.setBackgroundResource(on ? R.drawable.bg_btn : R.drawable.bg_btn_sec);
            c.setTextColor(on ? accentFg : 0xFFF5F5F7);
        }
    }

    // --- EpgTimelineView.Listener ---

    @Override public void onFocusChanged(Models.Channel channel, EpgGuide.Listing listing) {
        updateInfo(channel, listing);
    }

    @Override public void onVisibleTime(long centerTime) {
        long now = System.currentTimeMillis();
        long day = Timeline.dayStart(centerTime);
        for (int d : days) {
            if (Timeline.dayStart(now, d) == day) {
                paintDays(d);
                return;
            }
        }
    }

    @Override public void onOpen(Models.Channel channel, EpgGuide.Listing listing) {
        if (channel == null) return;
        long now = System.currentTimeMillis();
        int action = GuideActions.onOpen(listing, now, LivePlay.canCatchup(channel, listing, now));
        if (action == GuideActions.PLAY_LIVE) {
            LivePlay.live(this, channel, currentEpg(channel));
        } else if (action == GuideActions.PLAY_CATCHUP) {
            LivePlay.catchup(this, channel, listing);
        } else {
            // Future or not archived: show the info panel actions.
            if (btnWatch.getVisibility() == View.VISIBLE) btnWatch.requestFocus();
        }
    }

    void updateInfo(Models.Channel c, EpgGuide.Listing l) {
        if (c == null) return;
        long now = System.currentTimeMillis();
        java.text.SimpleDateFormat hm = EpgTime.format("HH:mm");
        String chLine = Text.clean(c.name) + (c.number > 0 ? "  ·  " + c.number : "");
        if (l == null) {
            infoChannel.setText(chLine);
            infoTitle.setText(Text.clean(c.name));
            infoMeta.setText("Keine Programmdaten");
            infoDesc.setText("");
            infoDesc.setVisibility(View.GONE);
        } else {
            String state = GuideActions.state(l, now);
            infoChannel.setText((state.isEmpty() ? "" : state + "  ·  ") + chLine);
            infoTitle.setText(Text.clean(l.title));
            long mins = Math.max(1, (l.stop - l.start) / Timeline.MIN);
            String meta = Timeline.dayLabel(Timeline.dayStart(l.start), now) + "  ·  " + hm.format(new Date(l.start))
                    + " – " + hm.format(new Date(l.stop)) + "  ·  " + mins + " Min.";
            if (l.start <= now && now < l.stop) {
                Models.Epg e = new Models.Epg();
                e.start = l.start;
                e.end = l.stop;
                meta += "  ·  " + GuideActions.remaining(e, now);
            }
            infoMeta.setText(meta);
            String desc = l.desc == null ? "" : Text.clean(l.desc).trim();
            infoDesc.setText(desc);
            infoDesc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
        }
        boolean current = l == null || (l.start <= now && now < l.stop);
        btnWatch.setText(current ? "Jetzt ansehen" : "Live ansehen");
        btnWatch.setVisibility(View.VISIBLE);
        btnStart.setVisibility(LivePlay.canCatchup(c, l, now) ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_GUIDE) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
