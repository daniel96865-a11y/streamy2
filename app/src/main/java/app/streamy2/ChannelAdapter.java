package app.streamy2;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.streamy2.ChannelAdapter;
import app.streamy2.Models;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes.dex */
public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {
    private static final Pattern HD = Pattern.compile("(HD/4K|FHD|UHD|4K|HD|RAW)");
    private RecyclerView attached;
    private final Listener listener;
    private final List<Object> items = new ArrayList();
    private final Handler epgUi = new Handler(Looper.getMainLooper());
    private final Handler numberUi = new Handler(Looper.getMainLooper());
    private final StringBuilder numberBuffer = new StringBuilder();
    private Toast numberToast;
    private final Runnable numberTuneRun = new Runnable() {
        @Override
        public void run() {
            ChannelAdapter.this.tuneEnteredNumber();
        }
    };
    private final Runnable epgNotifyRun = new Runnable() {
        @Override
        public void run() {
            ChannelAdapter.this.notifyEpgNow();
        }
    };
    private int gridColumns = 1;
    private static final int TYPE_ROW = 0;
    private static final int TYPE_POSTER = 1;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.GERMANY);

    public interface Listener {
        void onChannel(Models.Channel channel);

        void onMedia(Models.Media media);

        void onNeedPlot(Models.Media media, int i);

        void onPlayMedia(Models.Media media);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public /* bridge */ /* synthetic */ void onBindViewHolder(VH vh, int i, List list) {
        onBindViewHolder2(vh, i, (List<Object>) list);
    }

    public ChannelAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setChannels(List<Models.Channel> list) {
        this.items.clear();
        if (list != null) {
            this.items.addAll(list);
        }
        safeNotify();
    }

    public void setMedia(List<Models.Media> list) {
        this.items.clear();
        if (list != null) {
            this.items.addAll(list);
        }
        safeNotify();
    }

    public void setGridColumns(int cols) {
        int c = cols < 1 ? 1 : cols;
        if (this.gridColumns == c) {
            return;
        }
        this.gridColumns = c;
        safeNotify();
    }

    public int getGridColumns() {
        return this.gridColumns;
    }

    public boolean isPosterGrid() {
        return this.gridColumns > 1;
    }

    private void safeNotify() {
        RecyclerView recyclerView = this.attached;
        if (recyclerView == null || !recyclerView.isComputingLayout()) {
            notifyDataSetChanged();
        } else {
            this.attached.post(new Runnable() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    ChannelAdapter.this.notifyDataSetChanged();
                }
            });
        }
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attached = recyclerView;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.epgUi.removeCallbacks(this.epgNotifyRun);
        this.numberUi.removeCallbacks(this.numberTuneRun);
        this.numberBuffer.setLength(0);
        if (this.numberToast != null) {
            this.numberToast.cancel();
            this.numberToast = null;
        }
        if (this.attached == recyclerView) {
            this.attached = null;
        }
    }

    public void patchEpg(String str, Models.Epg epg) {
        for (int i = 0; i < this.items.size(); i++) {
            Object obj = this.items.get(i);
            if (obj instanceof Models.Channel) {
                Models.Channel channel = (Models.Channel) obj;
                if (str.equals(channel.id)) {
                    channel.epg = epg;
                    notifyItemChanged(i, "epg");
                    return;
                }
            }
        }
    }

    public Object getItem(int i) {
        if (i < 0 || i >= this.items.size()) {
            return null;
        }
        return this.items.get(i);
    }

    public void notifyEpg() {
        this.epgUi.removeCallbacks(this.epgNotifyRun);
        this.epgUi.postDelayed(this.epgNotifyRun, 90);
    }

    void notifyEpgNow() {
        RecyclerView recyclerView = this.attached;
        if (recyclerView != null && recyclerView.isComputingLayout()) {
            this.epgUi.post(this.epgNotifyRun);
            return;
        }
        int size = this.items.size();
        if (size <= 0) {
            return;
        }
        int first = 0;
        int count = size;
        if (recyclerView != null && (recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
            LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
            int a = lm.findFirstVisibleItemPosition();
            int b = lm.findLastVisibleItemPosition();
            if (a < 0) {
                return;
            }
            first = Math.max(0, a - 8);
            int last = Math.min(size - 1, Math.max(a, b) + 8);
            count = last - first + 1;
        } else if (size > 48) {
            // Don't rebind thousands of rows (Fire TV ANR after 3.26 progress bars).
            count = 48;
        }
        if (count > 0) {
            notifyItemRangeChanged(first, count, "epg");
        }
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public long getItemId(int i) {
        int hashCode;
        if (i < 0 || i >= this.items.size()) {
            return -1L;
        }
        Object obj = this.items.get(i);
        if (obj instanceof Models.Channel) {
            Object obj2 = ((Models.Channel) obj).id;
            StringBuilder sb = new StringBuilder("C:");
            if (obj2 == null) {
                obj2 = Integer.valueOf(i);
            }
            hashCode = sb.append(obj2).toString().hashCode();
        } else {
            if (!(obj instanceof Models.Media)) {
                return i;
            }
            Models.Media media = (Models.Media) obj;
            Object obj3 = media.id;
            StringBuilder append = new StringBuilder().append(media.series ? "S:" : "M:");
            if (obj3 == null) {
                obj3 = Integer.valueOf(i);
            }
            hashCode = append.append(obj3).toString().hashCode();
        }
        return hashCode;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onViewRecycled(VH vh) {
        Images.unbind(vh.logo);
        super.onViewRecycled(vh);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public int getItemCount() {
        return this.items.size();
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public int getItemViewType(int position) {
        if (this.gridColumns > 1 && position >= 0 && position < this.items.size()
                && (this.items.get(position) instanceof Models.Media)) {
            return TYPE_POSTER;
        }
        return TYPE_ROW;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public VH onCreateViewHolder(ViewGroup viewGroup, int i) {
        int layout = i == TYPE_POSTER ? R.layout.item_poster : R.layout.item_channel;
        View inflate = LayoutInflater.from(viewGroup.getContext()).inflate(layout, viewGroup, false);
        inflate.setFocusable(true);
        inflate.setClickable(true);
        inflate.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda2
            @Override // android.view.View.OnFocusChangeListener
            public final void onFocusChange(View view, boolean z) {
                view.setSelected(z);
            }
        });
        return new VH(inflate);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onBindViewHolder(VH vh, int i) {
        if (i < 0 || i >= this.items.size()) {
            return;
        }
        Object obj = this.items.get(i);
        if (obj instanceof Models.Channel) {
            bindChannel(vh, (Models.Channel) obj);
        } else if (obj instanceof Models.Media) {
            bindMedia(vh, (Models.Media) obj);
        }
    }

    /* renamed from: onBindViewHolder, reason: avoid collision after fix types in other method */
    public void onBindViewHolder2(VH vh, int i, List<Object> list) {
        if (!list.isEmpty() && (this.items.get(i) instanceof Models.Channel)) {
            Models.Channel channel = (Models.Channel) this.items.get(i);
            if (vh.sub != null) {
                vh.sub.setVisibility(android.view.View.VISIBLE);
                vh.sub.setText(epgLine(channel));
            }
            bindEpgProgress(vh, channel);
        } else {
            super.onBindViewHolder(vh, i, list);
        }
    }

    private void bindChannel(VH vh, final Models.Channel channel) {
        if (vh.num != null) {
            vh.num.setVisibility(channel.header ? View.INVISIBLE : View.VISIBLE);
            vh.num.setText(channel.header ? "" : String.valueOf(channel.number));
        }
        if (vh.live != null) {
            vh.live.setVisibility(channel.header ? 8 : 0);
        }
        vh.title.setText(styled(channel.name));
        if (vh.sub != null) {
            vh.sub.setVisibility(android.view.View.VISIBLE);
            vh.sub.setMaxLines(2);
            vh.sub.setSingleLine(false);
            vh.sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            // Phone + TV: keep „Jetzt: …“ readable
            if (!Tv.isTv(vh.itemView.getContext())) {
                vh.sub.setTextSize(12.0f);
            } else {
                vh.sub.setTextSize(13.0f);
            }
            vh.sub.setText(epgLine(channel));
        }
        bindEpgProgress(vh, channel);
        sizeLogo(vh, 48, 48);
        vh.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        applyLogo(vh, channel.logo, channel.name);
        if (vh.plot != null) {
            vh.plot.setVisibility(8);
        }
        vh.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda0
            @Override // android.view.View.OnFocusChangeListener
            public final void onFocusChange(View view, boolean z) {
                view.setSelected(z);
            }
        });
        vh.itemView.setOnLongClickListener(null);
        vh.itemView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                return ChannelAdapter.this.handleChannelNumberKey(view, keyCode, event);
            }
        });
        vh.itemView.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChannelAdapter.this.lambda$bindChannel$2(channel, view);
            }
        });
        vh.itemView.setAlpha(channel.header ? 0.55f : 1.0f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindChannel$2(Models.Channel channel, View view) {
        Listener listener;
        if (channel.header || (listener = this.listener) == null) {
            return;
        }
        listener.onChannel(channel);
    }

    private boolean handleChannelNumberKey(View view, int keyCode, KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || !Tv.isTv(view.getContext())) {
            return false;
        }
        int digit = digitFromKeyCode(keyCode);
        if (digit >= 0) {
            if (this.numberBuffer.length() >= 6) {
                this.numberBuffer.setLength(0);
            }
            this.numberBuffer.append(digit);
            this.numberUi.removeCallbacks(this.numberTuneRun);
            showNumberToast(view, "Sender " + this.numberBuffer);
            this.numberUi.postDelayed(this.numberTuneRun, 1300L);
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                && this.numberBuffer.length() > 0) {
            this.numberUi.removeCallbacks(this.numberTuneRun);
            tuneEnteredNumber();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && this.numberBuffer.length() > 0) {
            this.numberBuffer.deleteCharAt(this.numberBuffer.length() - 1);
            this.numberUi.removeCallbacks(this.numberTuneRun);
            if (this.numberBuffer.length() > 0) {
                showNumberToast(view, "Sender " + this.numberBuffer);
                this.numberUi.postDelayed(this.numberTuneRun, 1300L);
            }
            return true;
        }
        return false;
    }

    private static int digitFromKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    private void tuneEnteredNumber() {
        if (this.numberBuffer.length() == 0) {
            return;
        }
        int number;
        try {
            number = Integer.parseInt(this.numberBuffer.toString());
        } catch (NumberFormatException e) {
            this.numberBuffer.setLength(0);
            return;
        }
        this.numberBuffer.setLength(0);
        Models.Channel found = findChannelByNumber(number);
        if (found != null && !found.header && this.listener != null) {
            this.listener.onChannel(found);
            return;
        }
        RecyclerView recyclerView = this.attached;
        if (recyclerView != null) {
            showNumberToast(recyclerView, "Sender " + number + " nicht vorhanden");
        }
    }

    private Models.Channel findChannelByNumber(int number) {
        List<Models.Channel> all = App.live;
        if (all != null) {
            for (Models.Channel channel : all) {
                if (channel != null && !channel.header && channel.number == number) {
                    return channel;
                }
            }
        }
        for (Object item : this.items) {
            if (item instanceof Models.Channel) {
                Models.Channel channel = (Models.Channel) item;
                if (!channel.header && channel.number == number) {
                    return channel;
                }
            }
        }
        return null;
    }

    private void showNumberToast(View view, String text) {
        if (this.numberToast != null) {
            this.numberToast.cancel();
        }
        this.numberToast = Toast.makeText(view.getContext(), text, Toast.LENGTH_SHORT);
        this.numberToast.show();
    }

    private void bindMedia(final VH vh, final Models.Media media) {
        vh.itemView.setOnKeyListener(null);
        if (vh.num != null) {
            vh.num.setVisibility(8);
        }
        if (vh.live != null) {
            vh.live.setVisibility(8);
        }
        hideEpgProgress(vh);
        vh.title.setText(media.name == null ? "" : media.name);
        String join = join(media.genre, media.year);
        if (media.rating != null && !media.rating.isEmpty()) {
            join = join(join, media.rating);
        }
        if (media.duration != null && !media.duration.isEmpty()) {
            join = join(join, media.duration);
        }
        if (vh.sub != null) {
            vh.sub.setText(join);
            vh.sub.setVisibility(0);
        }
        boolean isTv = Tv.isTv(vh.itemView.getContext());
        boolean posterGrid = this.gridColumns > 1;
        if (posterGrid) {
            // Width-driven 2:3 posters so phone/TV columns fill available space (no tiny fixed DP).
            android.util.DisplayMetrics dm = vh.itemView.getResources().getDisplayMetrics();
            int gutter = dp(vh.logo, this.gridColumns >= 4 ? 10 : 14);
            int colW = Math.max(dp(vh.logo, 72), (dm.widthPixels / Math.max(1, this.gridColumns)) - gutter);
            int phPx = Math.round(colW * 1.5f);
            int minH = dp(vh.logo, this.gridColumns >= 4 ? (isTv ? 140 : 130) : (isTv ? 200 : 180));
            int maxH = dp(vh.logo, this.gridColumns >= 4 ? (isTv ? 220 : 260) : (isTv ? 320 : 360));
            phPx = Math.max(minH, Math.min(maxH, phPx));
            ViewGroup.LayoutParams lp = vh.logo.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = phPx;
            vh.logo.setLayoutParams(lp);
            ViewGroup.LayoutParams lp2 = vh.logoText.getLayoutParams();
            lp2.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp2.height = phPx;
            vh.logoText.setLayoutParams(lp2);
            vh.logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            vh.title.setTextSize(isTv ? 14.0f : 12.0f);
            if (vh.sub != null) {
                vh.sub.setTextSize(isTv ? 12.0f : 10.0f);
                vh.sub.setMaxLines(1);
                vh.sub.setSingleLine(true);
            }
            if (vh.plot != null) {
                vh.plot.setVisibility(8);
                vh.plot.setOnClickListener(null);
            }
        } else {
            sizeLogo(vh, isTv ? 64 : 56, isTv ? 92 : 80);
            vh.logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (isTv) {
                vh.title.setTextSize(17.0f);
                if (vh.sub != null) {
                    vh.sub.setTextSize(13.0f);
                }
            } else {
                vh.title.setTextSize(14.0f);
                if (vh.sub != null) {
                    vh.sub.setTextSize(11.0f);
                }
            }
        }
        applyLogo(vh, media.poster, media.name);
        final int i = isTv ? 6 : 12;
        final int i2 = 3;
        if (!posterGrid && vh.plot != null) {
            String replace = media.plot != null ? media.plot.trim().replace('\n', ' ') : "";
            if (replace.isEmpty() || replace.equals("Beschreibung wird geladen…")) {
                vh.plot.setText(isTv ? "OK für Details" : "Beschreibung wird geladen…");
                vh.plot.setVisibility(0);
            } else {
                vh.plot.setText(replace);
                vh.plot.setVisibility(0);
            }
            vh.plot.setTextSize(isTv ? 14.0f : 12.0f);
            vh.plot.setMaxLines(vh.itemView.hasFocus() ? i : 3);
            vh.plot.setFocusable(false);
            vh.plot.setClickable(!isTv);
            vh.plot.setOnClickListener(isTv ? null : new View.OnClickListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    ChannelAdapter.lambda$bindMedia$3(vh, i2, i, view);
                }
            });
        }
        vh.itemView.setAlpha(1.0f);
        vh.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda5
            @Override // android.view.View.OnFocusChangeListener
            public final void onFocusChange(View view, boolean z) {
                ChannelAdapter.this.lambda$bindMedia$4(vh, i, i2, media, view, z);
            }
        });
        vh.itemView.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChannelAdapter.this.lambda$bindMedia$5(media, view);
            }
        });
        vh.itemView.setOnLongClickListener(new View.OnLongClickListener() { // from class: app.streamy2.ChannelAdapter$$ExternalSyntheticLambda7
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                boolean lambda$bindMedia$6;
                lambda$bindMedia$6 = ChannelAdapter.this.lambda$bindMedia$6(media, view);
                return lambda$bindMedia$6;
            }
        });
        if (isTv || this.listener == null) {
            return;
        }
        if (media.plot == null || media.plot.trim().isEmpty()) {
            this.listener.onNeedPlot(media, vh.getBindingAdapterPosition());
        }
    }

    static /* synthetic */ void lambda$bindMedia$3(VH vh, int i, int i2, View view) {
        int maxLines = vh.plot.getMaxLines();
        TextView textView = vh.plot;
        if (maxLines <= i) {
            i = i2;
        }
        textView.setMaxLines(i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindMedia$4(VH vh, int i, int i2, Models.Media media, View view, boolean z) {
        view.setSelected(z);
        if (vh.plot != null && vh.plot.getVisibility() == 0) {
            TextView textView = vh.plot;
            if (!z) {
                i = i2;
            }
            textView.setMaxLines(i);
        }
        if (!z || this.listener == null) {
            return;
        }
        if (media.plot == null || media.plot.trim().isEmpty()) {
            this.listener.onNeedPlot(media, vh.getBindingAdapterPosition());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindMedia$5(Models.Media media, View view) {
        Listener listener = this.listener;
        if (listener != null) {
            listener.onMedia(media);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$bindMedia$6(Models.Media media, View view) {
        Listener listener = this.listener;
        if (listener == null) {
            return true;
        }
        listener.onPlayMedia(media);
        return true;
    }

    private void sizeLogo(VH vh, int i, int i2) {
        int dp = dp(vh.logo, i);
        int dp2 = dp(vh.logo, i2);
        ViewGroup.LayoutParams layoutParams = vh.logo.getLayoutParams();
        layoutParams.width = dp;
        layoutParams.height = dp2;
        vh.logo.setLayoutParams(layoutParams);
        ViewGroup.LayoutParams layoutParams2 = vh.logoText.getLayoutParams();
        layoutParams2.width = dp;
        layoutParams2.height = dp2;
        vh.logoText.setLayoutParams(layoutParams2);
    }

    private static int dp(View view, int i) {
        return Math.round(i * view.getResources().getDisplayMetrics().density);
    }

    private void applyLogo(VH vh, String str, String str2) {
        if (str != null && (str.startsWith("http") || str.startsWith("file:"))) {
            vh.logo.setVisibility(0);
            vh.logoText.setVisibility(8);
            Images.load(vh.logo, str);
        } else {
            vh.logo.setVisibility(8);
            vh.logo.setImageDrawable(null);
            vh.logoText.setVisibility(0);
            vh.logoText.setText(initials(str2));
            vh.logoText.setBackgroundColor(colorFor(str2));
        }
    }


    private void bindEpgProgress(VH vh, Models.Channel channel) {
        if (vh.epgProgress == null) {
            return;
        }
        int progress = EpgBar.progressPermille(channel == null ? null : channel.epg, System.currentTimeMillis());
        if (progress < 0 || (channel != null && channel.header)) {
            vh.epgProgress.hide();
            return;
        }
        vh.epgProgress.setFraction(progress / 1000f);
    }

    private static void hideEpgProgress(VH vh) {
        if (vh.epgProgress != null) {
            vh.epgProgress.hide();
        }
    }

    private String epgLine(Models.Channel channel) {
        if (!EpgTime.isCurrent(channel.epg, System.currentTimeMillis())) {
            // Prefer a quiet placeholder over a silent blank on Vavoo/Live while EPG catches up
            if (!channel.header && channel.vavooUrl != null && !channel.vavooUrl.isEmpty()) {
                return "EPG…";
            }
            return (channel.header || channel.categoryName == null || channel.categoryName.isEmpty()) ? "" : channel.categoryName;
        }
        String str = "Jetzt: " + Text.clean(channel.epg.title);
        return (channel.epg.start <= 0 || channel.epg.end <= 0) ? str : str + "  " + this.clock.format(new Date(channel.epg.start)) + "–" + this.clock.format(new Date(channel.epg.end));
    }

    private static CharSequence styled(String str) {
        if (str == null) {
            str = "";
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        Matcher matcher = HD.matcher(str);
        int i = 0;
        while (matcher.find()) {
            spannableStringBuilder.append((CharSequence) str.substring(i, matcher.start()));
            int length = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) matcher.group());
            spannableStringBuilder.setSpan(new SuperscriptSpan(), length, spannableStringBuilder.length(), 33);
            spannableStringBuilder.setSpan(new RelativeSizeSpan(0.65f), length, spannableStringBuilder.length(), 33);
            spannableStringBuilder.setSpan(new StyleSpan(1), length, spannableStringBuilder.length(), 33);
            i = matcher.end();
        }
        spannableStringBuilder.append((CharSequence) str.substring(i));
        return spannableStringBuilder;
    }

    private static String initials(String str) {
        String str2 = "";
        String trim = str == null ? "" : str.replaceFirst("(?i)^DE:\\s*", "").replace("#", "").trim();
        if (trim.isEmpty()) {
            return "?";
        }
        String[] split = trim.split("\\s+");
        String substring = split[0].isEmpty() ? "" : split[0].substring(0, 1);
        if (split.length > 1 && !split[1].isEmpty()) {
            str2 = split[1].substring(0, 1);
        } else if (split[0].length() > 1) {
            str2 = split[0].substring(1, 2);
        }
        return (substring + str2).toUpperCase(Locale.GERMAN);
    }

    private static int colorFor(String str) {
        return Color.HSVToColor(new float[]{Math.abs((str == null ? 0 : str.hashCode()) % 360), 0.45f, 0.35f});
    }

    private static String join(String str, String str2) {
        if (str == null) {
            str = "";
        }
        if (str2 == null) {
            str2 = "";
        }
        return (str.isEmpty() || str2.isEmpty()) ? str.isEmpty() ? str2 : str : str + " · " + str2;
    }

    public static class VH extends RecyclerView.ViewHolder {
        EpgBar epgProgress;
        TextView live;
        ImageView logo;
        TextView logoText;
        TextView num;
        TextView plot;
        TextView sub;
        TextView title;

        VH(View view) {
            super(view);
            this.num = (TextView) view.findViewById(R.id.num);
            this.logoText = (TextView) view.findViewById(R.id.logoText);
            this.logo = (ImageView) view.findViewById(R.id.logo);
            this.title = (TextView) view.findViewById(R.id.title);
            this.sub = (TextView) view.findViewById(R.id.sub);
            this.epgProgress = (EpgBar) view.findViewById(R.id.epgProgress);
            this.live = (TextView) view.findViewById(R.id.live);
            this.plot = (TextView) view.findViewById(R.id.plot);
        }
    }
}
