package app.streamy2;

import android.content.Context;
import android.graphics.Color;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.streamy2.EpgGuide;
import app.streamy2.Models;
import app.streamy2.PlayerActivity;
import java.util.Iterator;
import com.google.common.net.HttpHeaders;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* loaded from: classes.dex */
public class PlayerActivity extends AppCompatActivity {
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private TextView archiveHint;
    private TextView badgeLive;
    private View bottomBar;
    private ImageButton btnPlay;
    private TextView btnPlayer;
    private TextView btnResize;
    private boolean catchup;
    private Models.Channel channel;
    private TextView clock;
    private EpgGuide.Listing current;
    private EpgAdapter epgAdapter;
    private View epgBtns;
    private TextView epgEnd;
    private RecyclerView epgList;
    private TextView epgNext;
    private TextView epgNow;
    private SeekBar epgSeek;
    private View epgSheet;
    private TextView epgStart;
    private TextView errorView;
    private int freezeTicks;
    private DefaultHttpDataSource.Factory http;
    private int index;
    private long lastPos;
    private boolean liveMode;
    private ExoPlayer player;
    private TextView playerSub;
    private TextView playerTitle;
    private PlayerView playerView;
    private int recoverTries;
    private boolean resolving;
    private boolean seeking;
    private View topBar;
    private boolean useVlc;
    private boolean userPaused;
    private boolean foreground;
    private boolean resumePlayback;
    private boolean restartOnResume;
    private long playbackGeneration;
    private long nextEpgSync;
    private boolean vlcStarting;
    private String extraLiveHot;
    private String extraLiveKeep;
    private boolean extraLiveTriedVlc;
    private LiveEngine vlc;
    private ViewGroup vlcHost;
    private boolean vlcSoft;
    private String forceEngine;
    private String lastPlayUrl;
    private String lastExoError = "";
    private String lastVlcError = "";
    private String lastFallbackReason = "";
    private long metaDurationMs;
    private boolean vlcHudArmed;
    private boolean audioOnly;
    private Runnable sleepTimer;
    static final String MUX_TEST_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
    private final List<String> queue = new ArrayList();
    private boolean hud = true;
    private final List<EpgGuide.Listing> programmes = new ArrayList();
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.GERMANY);
    private final Runnable tick = new Runnable() { // from class: app.streamy2.PlayerActivity.1
        @Override // java.lang.Runnable
        public void run() {
            PlayerActivity.this.updateClockAndBar();
            PlayerActivity.this.armVlcHudHideIfPlaying();
            PlayerActivity.UI.postDelayed(this, 1000L);
        }
    };
    private final Runnable hideHud = new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda14
        @Override // java.lang.Runnable
        public final void run() {
            PlayerActivity.this.lambda$new$10();
        }
    };
    private final Runnable watchdog = new Runnable() { // from class: app.streamy2.PlayerActivity.5
        @Override // java.lang.Runnable
        public void run() {
            if (!PlayerActivity.this.foreground || PlayerActivity.this.userPaused) {
                PlayerActivity.this.freezeTicks = 0;
            } else if (PlayerActivity.this.useVlc) {
                if (PlayerActivity.this.vlc == null || !PlayerActivity.this.vlc.isPlaying()) {
                    PlayerActivity.this.freezeTicks++;
                } else {
                    PlayerActivity.this.freezeTicks = 0;
                    if (PlayerActivity.this.errorView != null) {
                        PlayerActivity.this.errorView.setVisibility(8);
                    }
                }
                if (PlayerActivity.this.freezeTicks == 8
                        && (PlayerActivity.this.extraLiveKeep != null || PlayerActivity.this.isExtraLivePlayback())) {
                    PlayerActivity.this.tryExoAfterVlc();
                }
            } else if (PlayerActivity.this.player != null && !PlayerActivity.this.userPaused) {
                int playbackState = PlayerActivity.this.player.getPlaybackState();
                if (playbackState == 2 || (playbackState == 3 && PlayerActivity.this.player.getPlayWhenReady() && !PlayerActivity.this.player.isPlaying())) {
                    PlayerActivity.this.freezeTicks++;
                } else {
                    PlayerActivity.this.freezeTicks = 0;
                }
                if (PlayerActivity.this.freezeTicks == 12
                        && PlayerActivity.this.liveMode
                        && !PlayerActivity.this.catchup
                        && !PlayerActivity.this.isExtraLivePlayback()
                        && "auto".equals(PlayerActivity.this.playerPref())) {
                    // Match the longer start gate used by the Streamy 3 player.
                    // Switching engines too early causes avoidable black frames on slow IPTV feeds.
                    PlayerActivity.this.lastFallbackReason = "Streamy Player länger als 12 s im Puffer";
                    if (PlayerActivity.this.switchToVlc()) {
                        PlayerActivity.this.freezeTicks = 0;
                        PlayerActivity.UI.postDelayed(this, 1000L);
                        return;
                    }
                }
                if (PlayerActivity.this.freezeTicks == 8 && PlayerActivity.this.liveMode && !PlayerActivity.this.catchup) {
                    try {
                        PlayerActivity.this.player.seekToDefaultPosition();
                    } catch (Throwable unused) {
                    }
                }
            } else {
                PlayerActivity.this.freezeTicks = 0;
            }
            if (PlayerActivity.this.freezeTicks >= 18) {
                PlayerActivity.this.freezeTicks = 0;
                PlayerActivity.this.recoverStuck();
            }
            PlayerActivity.UI.postDelayed(this, 1000L);
        }
    };
    private final Runnable extraLivePrefetch = new AnonymousClass6();

    private void maybeVlcIfSilent() {
    }

    public static void open(Context context, String str, String str2, String str3, String str4, boolean z) {
        open(context, str, str2, str3, str4, z, null, 0L);
    }

    public static void open(Context context, String str, String str2, String str3, String str4, boolean z, String forceEngine) {
        open(context, str, str2, str3, str4, z, forceEngine, 0L);
    }

    public static void open(Context context, String str, String str2, String str3, String str4, boolean z, String forceEngine, long durationMs) {
        Intent intent = new Intent(context, (Class<?>) PlayerActivity.class);
        intent.putExtra("url", str);
        intent.putExtra("alt", str2);
        intent.putExtra("title", str3);
        intent.putExtra("sub", str4);
        intent.putExtra("live", z);
        boolean extraLive = (str4 != null && str4.toLowerCase(Locale.US).contains("extra_live"))
                || ExtraLiveSource.isPlayUrl(str) || ExtraLiveSource.isPlayUrl(str2) || ExtraLiveSource.isCdn(str) || ExtraLiveSource.isCdn(str2);
        if (!extraLive && z) {
            Models.Channel playing = App.playing;
            if (playing != null && playing.extraLiveUrl != null && !playing.extraLiveUrl.isEmpty()) {
                extraLive = true;
            }
        }
        intent.putExtra("extra_live", extraLive);
        if (forceEngine != null && !forceEngine.isEmpty()) {
            intent.putExtra("forceEngine", forceEngine);
        }
        if (durationMs > 0) {
            intent.putExtra("durationMs", durationMs);
        }
        context.startActivity(intent);
    }

    /** Parse scraped duration (minutes as digits, or HH:MM:SS / Xm) into ms. */
    public static long parseDurationMs(String str) {
        if (str == null) {
            return 0L;
        }
        String trim = str.trim();
        if (trim.isEmpty()) {
            return 0L;
        }
        try {
            if (trim.matches("\\d+")) {
                long n = Long.parseLong(trim);
                if (n <= 0) {
                    return 0L;
                }
                // Media Extra isoDur stores minutes; values <= 600 treated as minutes
                if (n <= 600L) {
                    return n * 60000L;
                }
                // seconds
                if (n < 100000L) {
                    return n * 1000L;
                }
                return n;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m(?:in)?)?\\s*(?:(\\d+)\\s*s)?", 2).matcher(trim.toLowerCase(java.util.Locale.US));
            if (m.matches() && (m.group(1) != null || m.group(2) != null || m.group(3) != null)) {
                long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
                long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
                long sec = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
                return ((h * 3600) + (min * 60) + sec) * 1000L;
            }
            if (trim.contains(":")) {
                String[] parts = trim.split(":");
                if (parts.length == 3) {
                    return ((Long.parseLong(parts[0]) * 3600) + (Long.parseLong(parts[1]) * 60) + Long.parseLong(parts[2])) * 1000L;
                }
                if (parts.length == 2) {
                    return ((Long.parseLong(parts[0]) * 60) + Long.parseLong(parts[1])) * 1000L;
                }
            }
        } catch (Throwable unused) {
        }
        return 0L;
    }

    public static void openTestExo(Context context) {
        open(context, MUX_TEST_HLS, null, "Mux Test HLS", "ExoPlayer", false, "exo");
    }

    public static void openTestVlc(Context context) {
        open(context, MUX_TEST_HLS, null, "Mux Test HLS", "VLC", false, "vlc");
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        App.playerOpen = true;
        getWindow().addFlags(128);
        setContentView(R.layout.activity_player);
        String stringExtra = getIntent().getStringExtra("url");
        String stringExtra2 = getIntent().getStringExtra("alt");
        this.liveMode = getIntent().getBooleanExtra("live", false);
        this.forceEngine = getIntent().getStringExtra("forceEngine");
        this.metaDurationMs = getIntent().getLongExtra("durationMs", 0L);
        this.vlcHudArmed = false;
        if ("vlc".equals(this.forceEngine)) {
            this.useVlc = true;
        } else if ("exo".equals(this.forceEngine)) {
            this.useVlc = false;
        }
        String stringExtra3 = getIntent().getStringExtra("title");
        String stringExtra4 = getIntent().getStringExtra("sub");
        this.channel = this.liveMode ? App.playing : null;
        this.playerTitle = (TextView) findViewById(R.id.playerTitle);
        this.playerSub = (TextView) findViewById(R.id.playerSub);
        this.errorView = (TextView) findViewById(R.id.playerError);
        this.clock = (TextView) findViewById(R.id.clock);
        this.epgNow = (TextView) findViewById(R.id.epgNow);
        this.epgNext = (TextView) findViewById(R.id.epgNext);
        this.epgStart = (TextView) findViewById(R.id.epgStart);
        this.epgEnd = (TextView) findViewById(R.id.epgEnd);
        this.epgSeek = (SeekBar) findViewById(R.id.epgSeek);
        this.badgeLive = (TextView) findViewById(R.id.badgeLive);
        this.archiveHint = (TextView) findViewById(R.id.archiveHint);
        this.topBar = findViewById(R.id.topBar);
        this.bottomBar = findViewById(R.id.bottomBar);
        this.epgSheet = findViewById(R.id.epgSheet);
        this.epgBtns = findViewById(R.id.epgBtns);
        configureAerioChrome();
        TextView textView = this.playerTitle;
        if (textView != null) {
            if (stringExtra3 == null) {
                stringExtra3 = "Streamy 2";
            }
            textView.setText(Text.clean(stringExtra3));
        }
        TextView textView2 = this.playerSub;
        if (textView2 != null) {
            if (stringExtra4 == null) {
                stringExtra4 = "";
            }
            textView2.setText(Text.clean(stringExtra4));
        }
        View view = this.topBar;
        if (view != null) {
            view.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda26
                @Override // java.lang.Runnable
                public final void run() {
                    PlayerActivity.this.layoutStage();
                }
            });
        }
        View findViewById = findViewById(R.id.btnBack);
        if (findViewById != null) {
            findViewById.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$0(view2);
                }
            });
        }
        View findViewById2 = findViewById(R.id.btnEpg);
        if (findViewById2 != null) {
            findViewById2.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$1(view2);
                }
            });
        }
        View findViewById3 = findViewById(R.id.btnCloseEpg);
        if (findViewById3 != null) {
            findViewById3.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda5
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$2(view2);
                }
            });
        }
        TextView textView3 = this.badgeLive;
        if (textView3 != null) {
            textView3.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda6
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$3(view2);
                }
            });
        }
        ImageButton imageButton = (ImageButton) findViewById(R.id.btnPlay);
        this.btnPlay = imageButton;
        if (imageButton != null) {
            imageButton.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda7
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$4(view2);
                }
            });
        }
        TextView textView4 = (TextView) findViewById(R.id.btnResize);
        this.btnResize = textView4;
        if (textView4 != null) {
            textView4.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda8
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$5(view2);
                }
            });
        }
        TextView textView5 = (TextView) findViewById(R.id.btnPlayer);
        this.btnPlayer = textView5;
        if (textView5 != null) {
            paintPlayerBtn();
            this.btnPlayer.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda9
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$6(view2);
                }
            });
            this.btnPlayer.setOnLongClickListener(new View.OnLongClickListener() {
                @Override // android.view.View.OnLongClickListener
                public boolean onLongClick(View view2) {
                    PlayerActivity.this.showDiagnostics();
                    return true;
                }
            });
        }
        TextView btnDiag = (TextView) findViewById(R.id.btnDiag);
        if (btnDiag != null) {
            btnDiag.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    PlayerActivity.this.showDiagnostics();
                }
            });
        }
        View findViewById4 = findViewById(R.id.btnPrevCh);
        View findViewById5 = findViewById(R.id.btnNextCh);
        if (findViewById4 != null) {
            findViewById4.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda10
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$7(view2);
                }
            });
        }
        if (findViewById5 != null) {
            findViewById5.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda12
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$8(view2);
                }
            });
        }
        boolean z = this.liveMode;
        if (findViewById4 != null) {
            findViewById4.setVisibility(z ? 0 : 8);
        }
        if (findViewById5 != null) {
            findViewById5.setVisibility(z ? 0 : 8);
        }
        if (!this.liveMode) {
            TextView textView6 = this.badgeLive;
            if (textView6 != null) {
                textView6.setVisibility(8);
            }
            if (findViewById2 != null) {
                findViewById2.setVisibility(8);
            }
            TextView textView7 = this.epgNow;
            if (textView7 != null) {
                textView7.setVisibility(8);
            }
            TextView textView8 = this.epgNext;
            if (textView8 != null) {
                textView8.setVisibility(8);
            }
            TextView textView9 = this.archiveHint;
            if (textView9 != null) {
                textView9.setVisibility(8);
            }
            TextView es = this.epgStart;
            if (es != null) {
                es.setVisibility(0);
            }
            TextView ee = this.epgEnd;
            if (ee != null) {
                ee.setVisibility(0);
            }
            SeekBar sb = this.epgSeek;
            if (sb != null) {
                sb.setVisibility(0);
                sb.setEnabled(true);
            }
        }
        View findViewById6 = findViewById(R.id.tapLayer);
        if (findViewById6 != null) {
            findViewById6.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda27
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    PlayerActivity.this.lambda$onCreate$9(view2);
                }
            });
        }
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.epgList);
        this.epgList = recyclerView;
        this.epgAdapter = new EpgAdapter();
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setDescendantFocusability(262144);
            recyclerView.setAdapter(this.epgAdapter);
        }
        TextView textView10 = (TextView) findViewById(R.id.epgNavHint);
        TextView textView11 = (TextView) findViewById(R.id.btnCloseEpg);
        Tv.phoneX(textView11 instanceof TextView ? textView11 : null);
        if (textView10 != null) {
            if (Tv.isTv(this)) {
                textView10.setText("Hoch/Runter durchs Programm  ·  OK abspielen  ·  Zurück schließt");
            } else {
                textView10.setVisibility(8);
            }
        }
        boolean z2 = true;
        if (this.archiveHint != null) {
            Models.Channel channel = this.channel;
            if (channel != null && channel.archive) {
                this.archiveHint.setText("Archiv " + Math.max(1, this.channel.archiveDays) + " Tage · Programm antippen");
            } else {
                this.archiveHint.setText(this.liveMode ? "Kein Catch-up auf diesem Sender" : "");
            }
        }
        SeekBar seekBar = this.epgSeek;
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: app.streamy2.PlayerActivity.2
                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onProgressChanged(SeekBar seekBar2, int i, boolean z3) {
                }

                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onStartTrackingTouch(SeekBar seekBar2) {
                    PlayerActivity.this.seeking = true;
                }

                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onStopTrackingTouch(SeekBar seekBar2) {
                    PlayerActivity.this.seeking = false;
                    PlayerActivity.this.onSeekEpg(seekBar2.getProgress());
                }
            });
        }
        setVolumeControlStream(3);
        buildQueue(stringExtra, stringExtra2);
        HashMap hashMap = new HashMap();
        hashMap.put(HttpHeaders.USER_AGENT, "VLC/3.0.21 LibVLC/3.0.21");
        hashMap.put(HttpHeaders.REFERER, originOf(stringExtra));
        this.http = new DefaultHttpDataSource.Factory().setUserAgent("VLC/3.0.21 LibVLC/3.0.21").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(6000).setReadTimeoutMs(8000).setDefaultRequestProperties((Map<String, String>) hashMap);
        applyHeaders(stringExtra);
        DefaultLoadControl build = buildLoadControl();
        DefaultRenderersFactory extensionRendererMode = new DefaultRenderersFactory(this).setEnableDecoderFallback(true).setExtensionRendererMode(0);
        DefaultTrackSelector defaultTrackSelector = new DefaultTrackSelector(this);
        String audioMode = new Prefs(this).audioMode();
        int maxAudioChannels = "stereo".equals(audioMode) ? 2 : 8;
        DefaultTrackSelector.Parameters.Builder exceedAudioConstraintsIfNecessary = defaultTrackSelector.buildUponParameters().setMaxAudioChannelCount(maxAudioChannels).setAllowAudioMixedMimeTypeAdaptiveness(true).setAllowAudioMixedSampleRateAdaptiveness(true).setAllowAudioMixedChannelCountAdaptiveness(true).setExceedRendererCapabilitiesIfNecessary(true).setExceedAudioConstraintsIfNecessary(true);
        if ("surround".equals(audioMode)) {
            exceedAudioConstraintsIfNecessary.setPreferredAudioMimeTypes(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG);
        } else {
            exceedAudioConstraintsIfNecessary.setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC);
        }
        defaultTrackSelector.setParameters(exceedAudioConstraintsIfNecessary);
        try {
            ExoPlayer build2 = new ExoPlayer.Builder(this).setRenderersFactory(extensionRendererMode).setTrackSelector(defaultTrackSelector).setLoadControl(build).setWakeMode(2).setAudioAttributes(new AudioAttributes.Builder().setUsage(1).setContentType(3).build(), false).setHandleAudioBecomingNoisy(true).build();
            this.player = build2;
            build2.setVolume(1.0f);
            this.player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        } catch (OutOfMemoryError oom) {
            this.player = null;
            try {
                Toast.makeText(this, "Zu wenig Speicher für den Player", Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            this.player = null;
            try {
                Toast.makeText(this, "Player konnte nicht gestartet werden", Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        }
        PlayerView playerView = (PlayerView) findViewById(R.id.playerView);
        this.playerView = playerView;
        this.vlcHost = (ViewGroup) findViewById(R.id.vlcHost);
        if (playerView != null) {
            playerView.setUseController(false);
            if (this.player != null) {
                playerView.setPlayer(this.player);
            }
        }
        new Prefs(this).ensureMobilePlayerDefaults346();
        applyResize();
        hideSystemBars();
        if (Tv.isTv(this)) {
            ImageButton imageButton2 = this.btnPlay;
            if (imageButton2 != null) {
                imageButton2.setFocusable(false);
            }
            View findViewById7 = findViewById(R.id.tapLayer);
            if (findViewById7 != null) {
                findViewById7.setFocusable(false);
            }
            Tv.focusTree(this.bottomBar);
            Tv.focusTree(this.topBar);
            Tv.focusTree(this.epgSheet);
        }
        installPlayerFocusEffects();
        if (this.player != null) {
            this.player.addListener(new AnonymousClass3());
        }
        Handler handler = UI;
        handler.post(new PlayerActivity$$ExternalSyntheticLambda23(this));
        handler.postDelayed(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.this.loadProgrammes();
            }
        }, 400L);
        PlayerView playerView2 = this.playerView;
        if (playerView2 != null) {
            playerView2.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    PlayerActivity.this.playCurrent();
                }
            });
        } else {
            playCurrent();
        }
        handler.post(this.tick);
        handler.post(this.watchdog);
        scheduleHide();
        View view2 = this.topBar;
        if (view2 != null) {
            view2.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda26
                @Override // java.lang.Runnable
                public final void run() {
                    PlayerActivity.this.layoutStage();
                }
            });
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(z2) { // from class: app.streamy2.PlayerActivity.4
            @Override // androidx.activity.OnBackPressedCallback
            public void handleOnBackPressed() {
                if (PlayerActivity.this.epgSheet != null && PlayerActivity.this.epgSheet.getVisibility() == 0) {
                    PlayerActivity.this.closeEpg();
                } else {
                    PlayerActivity.this.leave();
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$0(View view) {
        leave();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$1(View view) {
        toggleEpg();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$2(View view) {
        closeEpg();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$3(View view) {
        playLive();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$4(View view) {
        togglePlay();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$5(View view) {
        toggleResize();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$6(View view) {
        showPlayerOptions();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$7(View view) {
        zap(-1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$8(View view) {
        zap(1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$9(View view) {
        View view2 = this.epgSheet;
        if (view2 == null || view2.getVisibility() != 0) {
            toggleHud();
        } else {
            closeEpg();
        }
    }

    /* renamed from: app.streamy2.PlayerActivity$3, reason: invalid class name */
    class AnonymousClass3 implements Player.Listener {
        AnonymousClass3() {
        }

        @Override // androidx.media3.common.Player.Listener
        public void onIsPlayingChanged(boolean z) {
            PlayerActivity.this.updatePlayIcon();
            if (!z || !foreground || useVlc || userPaused || PlayerActivity.this.player == null) {
                return;
            }
            PlayerActivity.this.player.setVolume(1.0f);
            PlayerActivity.this.userPaused = false;
            PlayerActivity.this.scheduleHide();
        }

        @Override // androidx.media3.common.Player.Listener
        public void onPlaybackStateChanged(int i) {
            if (!foreground || useVlc || userPaused) return;
            if (i == 3) {
                PlayerActivity.this.freezeTicks = 0;
            }
            if (i == 4 && PlayerActivity.this.liveMode && !PlayerActivity.this.userPaused) {
                final long request = playbackGeneration;
                PlayerActivity.UI.postDelayed(new Runnable() { // from class: app.streamy2.PlayerActivity$3$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        if (acceptPlayback(request) && !userPaused && !useVlc) PlayerActivity.AnonymousClass3.this.lambda$onPlaybackStateChanged$0();
                    }
                }, 400L);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onPlaybackStateChanged$0() {
            if (PlayerActivity.this.isFinishing() || PlayerActivity.this.player == null || PlayerActivity.this.userPaused) {
                return;
            }
            PlayerActivity.this.playCurrent();
        }

        @Override // androidx.media3.common.Player.Listener
        public void onTracksChanged(Tracks tracks) {
            try {
                PlayerActivity.this.pickPlayableAudio(tracks);
            } catch (Throwable unused) {
            }
        }

        @Override // androidx.media3.common.Player.Listener
        public void onPlayerError(PlaybackException playbackException) {
            if (!foreground || useVlc || userPaused) return;
            final long request = playbackGeneration;
            String str;
            String str2;
            if (playbackException == null) {
                str = "kein Bild";
            } else {
                str = playbackException.getErrorCodeName();
                Throwable cause = playbackException.getCause();
                if (cause != null && cause.getMessage() != null) {
                    str = str + " · " + cause.getMessage();
                }
            }
            try {
                PlayerActivity.this.lastExoError = str;
                PlayerActivity.this.toastPlaybackError("Player: " + str);
            } catch (Throwable ignored) {}
            if (PlayerActivity.this.extraLiveKeep != null && PlayerActivity.this.recoverTries < 2) {
                PlayerActivity.this.recoverTries++;
                // A provider-side auth/resolve change can leave the cached signature valid-looking
                // but unusable. Force a fresh login/resolve before replaying the canonical URL.
                ExtraLiveSource.invalidateSig();
                PlayerActivity.this.extraLiveHot = null;
                LocalHls.forget(PlayerActivity.this.extraLiveKeep);
                if (PlayerActivity.this.index >= 0 && PlayerActivity.this.index < PlayerActivity.this.queue.size()) {
                    PlayerActivity.this.queue.set(PlayerActivity.this.index, PlayerActivity.this.extraLiveKeep);
                }
                if (PlayerActivity.this.errorView != null) {
                    PlayerActivity.this.errorView.setVisibility(0);
                    PlayerActivity.this.errorView.setText("Live Extra wird neu verbunden…");
                }
                PlayerActivity.UI.postDelayed(new Runnable() { // from class: app.streamy2.PlayerActivity$3$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        if (acceptPlayback(request) && !userPaused && !useVlc) PlayerActivity.AnonymousClass3.this.lambda$onPlayerError$1();
                    }
                }, 400L);
                return;
            }
            if (PlayerActivity.this.errorView != null) {
                PlayerActivity.this.errorView.setVisibility(0);
                PlayerActivity.this.errorView.setText("Live Extra: " + str);
            }
            if (PlayerActivity.this.index + 1 < PlayerActivity.this.queue.size()) {
                PlayerActivity.this.index++;
                if (PlayerActivity.this.errorView != null) {
                    PlayerActivity.this.errorView.setVisibility(8);
                }
                PlayerActivity.this.playCurrent();
                return;
            }
            if (PlayerActivity.this.liveMode && !PlayerActivity.this.catchup
                    && !PlayerActivity.this.isExtraLivePlayback()
                    && "auto".equals(PlayerActivity.this.playerPref())
                    && !PlayerActivity.this.useVlc
                    && !PlayerActivity.this.queue.isEmpty()) {
                // Exo exhausted the real provider URLs: retry the primary URL with VLC.
                PlayerActivity.this.lastFallbackReason = "Exo-Fehler: " + str;
                PlayerActivity.this.index = 0;
                if (PlayerActivity.this.switchToVlc()) {
                    PlayerActivity.this.freezeTicks = 0;
                    return;
                }
            }
            if (PlayerActivity.this.errorView != null) {
                PlayerActivity.this.errorView.setVisibility(0);
                TextView textView = PlayerActivity.this.errorView;
                if (PlayerActivity.this.catchup) {
                    str2 = "Catch-up nicht verfügbar für dieses Programm.";
                } else {
                    str2 = "Stream konnte nicht geladen werden.\nOben auf VLC tippen oder anderen Sender wählen.";
                }
                textView.setText(str2);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onPlayerError$1() {
            if (PlayerActivity.this.isFinishing()) {
                return;
            }
            PlayerActivity.this.playCurrent();
        }
    }


    /* JADX INFO: Access modifiers changed from: private */
    public void showDiagnostics() {
        try {
            String engine = this.useVlc ? "VLC" : "Exo";
            if (this.forceEngine != null) {
                engine = engine + " (force=" + this.forceEngine + ")";
            } else {
                engine = engine + " (pref=" + playerPref() + ")";
            }
            boolean libOk = VlcFactory.isAvailable();
            String hls = LocalHls.isReady()
                    ? ("bereit · Port " + LocalHls.getPort())
                    : "nicht bereit";
            String host = redactHost(this.lastPlayUrl != null ? this.lastPlayUrl
                    : (this.index >= 0 && this.index < this.queue.size() ? this.queue.get(this.index) : null));
            String exo = (this.lastExoError == null || this.lastExoError.isEmpty()) ? "—" : this.lastExoError;
            String vlc = (this.lastVlcError == null || this.lastVlcError.isEmpty())
                    ? ((VlcFactory.lastError == null || VlcFactory.lastError.isEmpty()) ? "—" : VlcFactory.lastError)
                    : this.lastVlcError;
            String fallback = (this.lastFallbackReason == null || this.lastFallbackReason.isEmpty())
                    ? "—" : this.lastFallbackReason;
            String msg = "Engine: " + engine
                    + "\n" + App.lowRamLabel(this)
                    + "\nMedia3: " + exoPlaybackSummary()
                    + "\nVideo: " + selectedTrackSummary(C.TRACK_TYPE_VIDEO)
                    + "\nAudio: " + selectedTrackSummary(C.TRACK_TYPE_AUDIO)
                    + "\nAuto-Fallback: " + fallback
                    + "\nlibVLC: " + (libOk ? "ja" : "nein")
                    + "\nLocalHls: " + hls
                    + "\nHost: " + host
                    + "\nLive Extra: " + (isExtraLivePlayback() ? ExtraLiveSource.diagnosticSummary() : "—")
                    + "\nLive-Extra-DNS: " + (isExtraLivePlayback() ? OkPlay.diagnosticDns() : "—")
                    + "\nExo-Fehler: " + exo
                    + "\nVLC-Fehler: " + vlc;
            PlaybackDiagnostics.show(this, msg);
        } catch (Throwable t) {
            Toast.makeText(this, "Diagnose: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private static String playbackStateLabel(int state) {
        if (state == Player.STATE_BUFFERING) return "Puffert";
        if (state == Player.STATE_READY) return "Bereit";
        if (state == Player.STATE_ENDED) return "Beendet";
        return "Leerlauf";
    }

    private String exoPlaybackSummary() {
        ExoPlayer exo = this.player;
        if (exo == null) return "—";
        try {
            long bufferedMs = Math.max(0L, exo.getBufferedPosition() - exo.getCurrentPosition());
            return playbackStateLabel(exo.getPlaybackState())
                    + " · " + String.format(Locale.GERMANY, "%.1f s Puffer", bufferedMs / 1000.0d);
        } catch (Throwable ignored) {
            return playbackStateLabel(exo.getPlaybackState());
        }
    }

    private String selectedTrackSummary(int type) {
        ExoPlayer exo = this.player;
        if (exo == null) return "—";
        try {
            for (Tracks.Group group : exo.getCurrentTracks().getGroups()) {
                if (group.getType() != type || !group.isSelected()) continue;
                for (int i = 0; i < group.length; i++) {
                    if (!group.isTrackSelected(i)) continue;
                    Format format = group.getTrackFormat(i);
                    String codec = format.codecs;
                    if (codec == null || codec.isEmpty()) codec = format.sampleMimeType;
                    if (codec == null || codec.isEmpty()) codec = "unbekannt";
                    if (type == C.TRACK_TYPE_VIDEO) {
                        String size = (format.width > 0 && format.height > 0)
                                ? (format.width + "×" + format.height) : "Auflösung ?";
                        return size + " · " + codec;
                    }
                    if (type == C.TRACK_TYPE_AUDIO) {
                        String channels = format.channelCount > 0 ? (format.channelCount + "ch") : "?ch";
                        String rate = format.sampleRate > 0 ? (" · " + format.sampleRate + " Hz") : "";
                        return codec + " · " + channels + rate;
                    }
                    return codec;
                }
            }
        } catch (Throwable ignored) {
        }
        return "—";
    }

    private static String redactHost(String url) {
        if (url == null || url.isEmpty()) {
            return "—";
        }
        try {
            Uri parse = Uri.parse(url);
            String host = parse.getHost();
            if (host == null || host.isEmpty()) {
                return "—";
            }
            if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) {
                String q = parse.getQueryParameter("u");
                if (q != null && !q.isEmpty()) {
                    return "local→" + redactHost(q);
                }
                return host + (parse.getPort() > 0 ? (":" + parse.getPort()) : "");
            }
            if (host.length() <= 5) {
                return host.charAt(0) + "***";
            }
            return host.substring(0, 2) + "***" + host.substring(host.length() - 2);
        } catch (Exception unused) {
            return "***";
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void leave() {
        if (isFinishing()) {
            return;
        }
        stopPlayback();
        releasePlayer();
        try {
            Intent intent = new Intent(this, (Class<?>) MainActivity.class);
            intent.addFlags(604110848);
            intent.putExtra("fromPlayer", true);
            startActivity(intent);
        } catch (Throwable unused) {
        }
        finish();
    }

    private void stopPlayback() {
        try {
            UI.removeCallbacks(this.extraLivePrefetch);
        } catch (Throwable unused) {
        }
        try {
            UI.removeCallbacks(this.tick);
        } catch (Throwable unused2) {
        }
        try {
            UI.removeCallbacks(this.watchdog);
        } catch (Throwable unused3) {
        }
        try {
            UI.removeCallbacks(this.hideHud);
        } catch (Throwable unused4) {
        }
        try {
            PlayerView playerView = this.playerView;
            if (playerView != null) {
                playerView.setPlayer(null);
            }
        } catch (Throwable unused5) {
        }
        try {
            LiveEngine liveEngine = this.vlc;
            if (liveEngine != null) {
                liveEngine.pause();
            }
        } catch (Throwable unused6) {
        }
        try {
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(false);
            }
        } catch (Throwable unused7) {
        }
    }

    private void releasePlayer() {
        ExoPlayer exoPlayer = this.player;
        this.player = null;
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
            } catch (Throwable unused) {
            }
            try {
                exoPlayer.clearMediaItems();
            } catch (Throwable unused2) {
            }
            try {
                exoPlayer.release();
            } catch (Throwable unused3) {
            }
        }
        try {
            LiveEngine liveEngine = this.vlc;
            if (liveEngine != null) {
                liveEngine.stop(true);
                this.vlc = null;
            }
        } catch (Throwable unused4) {
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        setIntent(intent);
        this.userPaused = false;
        this.nextEpgSync = 0;
        hideVlc();
        this.programmes.clear();
        this.current = null;
        this.extraLiveKeep = null;
        this.extraLiveHot = null;
        this.recoverTries = 0;
        this.metaDurationMs = intent.getLongExtra("durationMs", 0L);
        this.playerTitle.setText(Text.clean(intent.getStringExtra("title")));
        String stringExtra = intent.getStringExtra("url");
        String stringExtra2 = intent.getStringExtra("alt");
        this.liveMode = intent.getBooleanExtra("live", false);
        this.forceEngine = intent.getStringExtra("forceEngine");
        this.catchup = false;
        this.useVlc = "vlc".equals(this.forceEngine);
        this.vlcSoft = false;
        this.freezeTicks = 0;
        this.channel = this.liveMode ? App.playing : null;
        ExoPlayer exoPlayer = this.player;
        if (exoPlayer != null) {
            exoPlayer.stop();
            this.player.clearMediaItems();
        }
        PlayerView playerView = this.playerView;
        if (playerView != null) {
            playerView.setVisibility(0);
        }
        buildQueue(stringExtra, stringExtra2);
        playCurrent();
        seedEpg();
        loadProgrammes();
        boolean z = this.liveMode;
        View findViewById = findViewById(R.id.btnPrevCh);
        View findViewById2 = findViewById(R.id.btnNextCh);
        if (findViewById != null) {
            findViewById.setVisibility(z ? 0 : 8);
        }
        if (findViewById2 != null) {
            findViewById2.setVisibility(z ? 0 : 8);
        }
        setHud(true);
        scheduleHide();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.core.app.ComponentActivity, android.app.Activity, android.view.Window.Callback
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        if (keyEvent.getAction() != 0) {
            return super.dispatchKeyEvent(keyEvent);
        }
        int keyCode = keyEvent.getKeyCode();
        if (keyCode == 24 || keyCode == 25 || keyCode == 164 || keyCode == 91) {
            return super.dispatchKeyEvent(keyEvent);
        }
        final boolean tv = Tv.isTv(this);
        View view = this.epgSheet;
        if (view != null && view.getVisibility() == 0) {
            if (keyCode == 4 || keyCode == 111 || keyCode == 172 || keyCode == 165) {
                closeEpg();
                return true;
            }
            if (keyCode == 85 || keyCode == 126 || keyCode == 127) {
                togglePlay();
                return true;
            }
            return super.dispatchKeyEvent(keyEvent);
        }
        if (keyCode == 4 || keyCode == 111) {
            leave();
            return true;
        }
        // Dedicated media play/pause keys always toggle
        if (keyCode == 85 || keyCode == 126 || keyCode == 127) {
            togglePlay();
            return true;
        }
        if (keyCode == 79 && !tv) {
            togglePlay();
            return true;
        }
        // OK / Enter / DPAD_CENTER
        if (keyCode == 23 || keyCode == 66 || keyCode == 160) {
            if (tv) {
                if (!this.hud) {
                    setHud(true);
                    scheduleHide();
                    focusTvControls(false);
                    return true;
                }
                // HUD visible: activate focused control (do not force-pause)
                return super.dispatchKeyEvent(keyEvent);
            }
            if (this.hud) {
                return super.dispatchKeyEvent(keyEvent);
            }
            togglePlay();
            return true;
        }
        if (keyCode == 166 || keyCode == 93) {
            zap(1);
            return true;
        }
        if (keyCode == 167 || keyCode == 92) {
            zap(-1);
            return true;
        }
        if ((keyCode == 172 || keyCode == 165) && this.liveMode) {
            setHud(true);
            openEpg();
            return true;
        }
        // DPAD_DOWN
        if (keyCode == 20) {
            if (tv && this.liveMode) {
                if (!this.hud) {
                    setHud(true);
                    scheduleHide();
                    focusTvControls(true);
                    return true;
                }
                // Navigate focus into bottom chips — do not open EPG
                return super.dispatchKeyEvent(keyEvent);
            }
            if (this.liveMode && !this.hud) {
                setHud(true);
                openEpg();
                return true;
            }
        }
        // DPAD_UP
        if (keyCode == 19) {
            if (!this.hud) {
                setHud(true);
                scheduleHide();
                if (tv) {
                    focusTvTop();
                }
                return true;
            }
            if (tv) {
                return super.dispatchKeyEvent(keyEvent);
            }
        }
        // Left / Right / media rewind-ff
        if (keyCode == 21 || keyCode == 89 || keyCode == 22 || keyCode == 90) {
            boolean left = keyCode == 21 || keyCode == 89;
            boolean dpad = keyCode == 21 || keyCode == 22;
            if (!this.hud) {
                setHud(true);
                scheduleHide();
                if (tv && this.liveMode && dpad) {
                    // Show HUD first; seek only once SeekBar is focused
                    SeekBar seekBarShow = this.epgSeek;
                    if (seekBarShow != null) {
                        seekBarShow.requestFocus();
                    } else {
                        focusTvControls(true);
                    }
                    return true;
                }
            }
            if (!this.liveMode) {
                seekBy(left ? -15000L : C.DEFAULT_SEEK_FORWARD_INCREMENT_MS);
                return true;
            }
            if (tv && dpad) {
                View focus = getCurrentFocus();
                if (focus != this.epgSeek) {
                    // Move focus between bottom chips — do not timeshift/rewind
                    return super.dispatchKeyEvent(keyEvent);
                }
            }
            SeekBar seekBar = this.epgSeek;
            if (seekBar != null) {
                int max = Math.max(0, Math.min(1000, seekBar.getProgress() + (left ? -40 : 40)));
                this.epgSeek.setProgress(max);
                onSeekEpg(max);
            }
            return true;
        }
        if (!this.hud) {
            setHud(true);
            scheduleHide();
            return true;
        }
        return super.dispatchKeyEvent(keyEvent);
    }

    private void toggleHud() {
        setHud(!this.hud);
        if (this.hud) {
            scheduleHide();
        }
    }

    private void setHud(boolean z) {
        this.hud = z;
        boolean z2 = false;
        int i = z ? 0 : 8;
        View view = this.topBar;
        if (view != null) {
            view.setVisibility(i);
        }
        ImageButton imageButton = this.btnPlay;
        if (imageButton != null) {
            imageButton.setVisibility(i);
        }
        View view2 = this.epgBtns;
        if (view2 != null) {
            view2.setVisibility(i);
        }
        View view3 = this.epgSheet;
        if (view3 != null && view3.getVisibility() == 0) {
            z2 = true;
        }
        View view4 = this.bottomBar;
        if (view4 != null) {
            view4.setVisibility(z2 ? 8 : i);
        }
        if (z && this.liveMode) {
            bindEpg();
        }
        if (z) {
            if (Tv.isTv(this)) {
                ensureTvControlsFocusable();
            }
            return;
        }
        hideSystemBars();
    }


    private void ensureTvControlsFocusable() {
        if (!Tv.isTv(this)) {
            return;
        }
        int[] ids = new int[]{
                R.id.btnPlay, R.id.btnBack, R.id.btnEpg, R.id.btnPlayer, R.id.btnResize, R.id.btnDiag,
                R.id.btnPrevCh, R.id.btnNextCh, R.id.badgeLive, R.id.epgSeek
        };
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setFocusable(true);
                v.setClickable(true);
            }
        }
        PlayerView pv = this.playerView;
        if (pv != null) {
            pv.setFocusable(false);
            pv.setFocusableInTouchMode(false);
            pv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        ViewGroup host = this.vlcHost;
        if (host != null) {
            host.setFocusable(false);
            host.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        View tap = findViewById(R.id.tapLayer);
        if (tap != null) {
            tap.setFocusable(false);
        }
    }

    private void focusTvControls(boolean bottomChips) {
        ensureTvControlsFocusable();
        View target = null;
        if (bottomChips) {
            target = this.btnPlayer;
            if (target == null || target.getVisibility() != View.VISIBLE) {
                target = findViewById(R.id.btnEpg);
            }
            if (target == null || target.getVisibility() != View.VISIBLE) {
                target = findViewById(R.id.btnResize);
            }
        }
        if (target == null) {
            target = this.btnPlay;
        }
        if (target != null && target.getVisibility() == View.VISIBLE) {
            target.requestFocus();
        }
    }

    private void focusTvTop() {
        ensureTvControlsFocusable();
        View back = findViewById(R.id.btnBack);
        if (back != null) {
            back.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: Access modifiers changed from: private */
    public void armVlcHudHideIfPlaying() {
        if (!this.useVlc || this.userPaused) {
            return;
        }
        LiveEngine liveEngine = this.vlc;
        if (liveEngine == null || !liveEngine.isPlaying()) {
            return;
        }
        updatePlayIcon();
        if (!this.vlcHudArmed) {
            this.vlcHudArmed = true;
            scheduleHide();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onVlcPlaying() {
        this.userPaused = false;
        this.vlcHudArmed = true;
        updatePlayIcon();
        scheduleHide();
    }

    public void scheduleHide() {
        Handler handler = UI;
        handler.removeCallbacks(this.hideHud);
        if (this.useVlc) {
            LiveEngine liveEngine = this.vlc;
            if (liveEngine == null || !liveEngine.isPlaying()) {
                return;
            }
        } else {
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer == null || !exoPlayer.isPlaying()) {
                return;
            }
        }
        handler.postDelayed(this.hideHud, Tv.isTv(this) ? 5000L : 2000);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void layoutStage() {
        hideSystemBars();
    }

    private void hideSystemBars() {
        try {
            getWindow().addFlags(1024);
            // Edge-to-edge so video can use the full panel on phones (notches / gesture bars).
            try {
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);
            } catch (Throwable ignored) {
            }
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    WindowManager.LayoutParams attrs = getWindow().getAttributes();
                    attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    getWindow().setAttributes(attrs);
                } catch (Throwable ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController insetsController = getWindow().getInsetsController();
                if (insetsController != null) {
                    insetsController.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    insetsController.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(5894);
            }
        } catch (Exception unused) {
        }
    }

    private void place(View view, int i, int i2, int i3, int i4) {
        if (view == null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(i, i2);
        layoutParams.gravity = i3;
        layoutParams.topMargin = i4;
        view.setLayoutParams(layoutParams);
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        layoutStage();
        setHud(true);
        scheduleHide();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$new$10() {
        View view = this.epgSheet;
        if ((view == null || view.getVisibility() != 0) && !this.userPaused) {
            setHud(false);
        }
    }

    private void togglePlay() {
        if (useVlc) {
            if (vlc == null) return;
            userPaused = !userPaused;
            if (userPaused) vlc.pause(); else vlc.resume();
        } else {
            if (player == null) return;
            userPaused = !userPaused;
            player.setPlayWhenReady(!userPaused);
        }
        freezeTicks = 0;
        updatePlayIcon();
        setHud(true);
        if (!userPaused) scheduleHide();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updatePlayIcon() {
        ExoPlayer exoPlayer;
        LiveEngine liveEngine;
        if (this.btnPlay == null) {
            return;
        }
        boolean z = true;
        if (!this.useVlc ? (exoPlayer = this.player) == null || !exoPlayer.isPlaying() : (liveEngine = this.vlc) == null || !liveEngine.isPlaying()) {
            z = false;
        }
        this.btnPlay.setImageResource(z ? R.drawable.ic_pause : R.drawable.ic_play);
        this.btnPlay.setContentDescription(z ? "Pause" : "Play");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void seedEpg() {
        Models.Channel channel;
        if (isFinishing() || (channel = this.channel) == null || this.epgNow == null) {
            return;
        }
        if (EpgTime.isCurrent(channel.epg, System.currentTimeMillis())) {
            Models.Channel channel2 = this.channel;
            lambda$seedEpg$11(channel2, channel2.epg, null);
        }
        final Models.Channel channel3 = this.channel;
        IO.execute(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda15
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.this.lambda$seedEpg$12(channel3);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$seedEpg$12(final Models.Channel channel) {
        final ArrayList arrayList = new ArrayList();
        Models.Epg epg = null;
        try {
            if (App.guide != null) {
                epg = App.guide.forChannel(channel, true);
                arrayList.addAll(App.guide.listingsFor(channel));
            }
        } catch (Throwable unused) {
        }
        final Models.Epg epgFinal = epg;
        UI.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda22
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.this.lambda$seedEpg$11(channel, epgFinal, arrayList);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: bindSeededEpg, reason: merged with bridge method [inline-methods] */
    public void lambda$seedEpg$11(Models.Channel channel, Models.Epg epg, List<EpgGuide.Listing> list) {
        Models.Channel channel2;
        if (isFinishing() || (channel2 = this.channel) != channel || this.epgNow == null) {
            return;
        }
        if (epg != null) {
            try {
                channel2.epg = epg;
            } catch (Exception unused) {
                return;
            }
        }
        if (list != null) {
            this.programmes.clear();
            this.programmes.addAll(list);
        }
        if (this.programmes.isEmpty() && EpgTime.isCurrent(this.channel.epg, System.currentTimeMillis())) {
            EpgGuide.Listing listing = new EpgGuide.Listing();
            listing.title = this.channel.epg.title;
            listing.start = this.channel.epg.start;
            listing.stop = this.channel.epg.end;
            this.programmes.add(listing);

        }
        pickCurrent();
        RecyclerView recyclerView = this.epgList;
        if (recyclerView != null && recyclerView.isComputingLayout()) {
            this.epgList.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda11
                @Override // java.lang.Runnable
                public final void run() {
                    PlayerActivity.this.lambda$bindSeededEpg$13();
                }
            });
        } else {
            EpgAdapter epgAdapter = this.epgAdapter;
            if (epgAdapter != null) {
                epgAdapter.notifyDataSetChanged();
            }
        }
        bindEpg();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindSeededEpg$13() {
        EpgAdapter epgAdapter;
        if (isFinishing() || (epgAdapter = this.epgAdapter) == null) {
            return;
        }
        epgAdapter.notifyDataSetChanged();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadProgrammes() {
        final Models.Channel channel;
        if (!this.liveMode || (channel = this.channel) == null) {
            return;
        }
        if ((channel.extraLiveUrl != null && !channel.extraLiveUrl.isEmpty()) || App.api == null) {
            Handler handler = UI;
            handler.postDelayed(new PlayerActivity$$ExternalSyntheticLambda23(this), 600L);
            handler.postDelayed(new PlayerActivity$$ExternalSyntheticLambda23(this), 2000);
            handler.postDelayed(new PlayerActivity$$ExternalSyntheticLambda23(this), 5000L);
            return;
        }
        IO.execute(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda24
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.this.lambda$loadProgrammes$15(channel);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadProgrammes$15(final Models.Channel channel) {
        XtreamApi source = App.api;
        if (source == null) return;
        final List<EpgGuide.Listing> programmes = source.programmes(channel);
        UI.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda13
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.this.lambda$loadProgrammes$14(programmes, channel);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadProgrammes$14(List list, Models.Channel channel) {
        if (isFinishing() || isDestroyed() || this.channel != channel) {
            return;
        }
        if (list != null && !list.isEmpty() && App.guide != null) {
            App.guide.putListings(channel, list);
        }
        seedEpg();
    }

    private void pickCurrent() {
        this.current = EpgTime.current(this.programmes, System.currentTimeMillis());
        if (this.channel == null) return;
        if (this.current == null) {
            this.channel.epg = null;
            return;
        }
        Models.Epg epg = new Models.Epg();
        epg.title = this.current.title;
        epg.start = this.current.start;
        epg.end = this.current.stop;
        for (EpgGuide.Listing item : this.programmes) {
            if (item.start >= this.current.stop) { epg.nextTitle = item.title; break; }
        }
        this.channel.epg = epg;
    }

    private void bindEpg() {
        EpgGuide.Listing listing;
        Models.Channel channel;
        Models.Channel channel2;
        if (this.liveMode) {
            EpgGuide.Listing listing2 = this.current;
            if (listing2 == null && (channel2 = this.channel) != null && EpgTime.isCurrent(channel2.epg, System.currentTimeMillis())) {
                listing2 = new EpgGuide.Listing();
                listing2.title = this.channel.epg.title;
                listing2.start = this.channel.epg.start;
                listing2.stop = this.channel.epg.end;
                this.current = listing2;
            }
            listing = null;
            if (listing2 != null) {
                for (EpgGuide.Listing candidate : this.programmes) {
                    if (candidate.start >= listing2.stop) { listing = candidate; break; }
                }
            }
            if (listing2 != null) {
                TextView textView = this.epgNow;
                if (textView != null) {
                    textView.setText(Text.clean(listing2.title));
                }
                TextView textView2 = this.epgStart;
                if (textView2 != null) {
                    textView2.setText(this.clockFmt.format(new Date(listing2.start)));
                }
                TextView textView3 = this.epgEnd;
                if (textView3 != null) {
                    textView3.setText(this.clockFmt.format(new Date(listing2.stop)));
                }
                TextView textView4 = this.playerSub;
                if (textView4 != null) {
                    textView4.setText(Text.clean(listing2.title) + "  ·  " + this.clockFmt.format(new Date(listing2.start)) + "–" + this.clockFmt.format(new Date(listing2.stop)));
                }
            } else {
                TextView textView5 = this.epgNow;
                if (textView5 != null) {
                    textView5.setText("Keine EPG-Daten");
                }
                TextView textView6 = this.epgStart;
                if (textView6 != null) {
                    textView6.setText("--:--");
                }
                TextView textView7 = this.epgEnd;
                if (textView7 != null) {
                    textView7.setText("--:--");
                }
            }
            if (this.epgNext != null) {
                String str = listing == null ? "" : "Danach: " + Text.clean(listing.title) + "  " + this.clockFmt.format(new Date(listing.start));
                if (str.isEmpty() && (channel = this.channel) != null && channel.epg != null && this.channel.epg.nextTitle != null) {
                    str = "Danach: " + Text.clean(this.channel.epg.nextTitle);
                }
                this.epgNext.setText(str);
            }
            TextView textView8 = this.badgeLive;
            if (textView8 != null) {
                textView8.setText(this.catchup ? "ARCHIV" : "LIVE");
            }
            updateClockAndBar();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateClockAndBar() {
        long currentTimeMillis;
        ExoPlayer exoPlayer;
        TextView textView = this.clock;
        if (textView != null) {
            textView.setText(this.clockFmt.format(new Date()));
        }
        if (liveMode && !catchup) {
            EpgGuide.Listing airing = EpgTime.current(programmes, System.currentTimeMillis());
            if (airing != current) { pickCurrent(); bindEpg(); }
            if (foreground && System.currentTimeMillis() >= nextEpgSync) {
                nextEpgSync = System.currentTimeMillis() + 60000L;
                EpgRefresh.request(this, false, error -> { if (foreground) seedEpg(); });
                seedEpg();
            }
        }
        if (this.seeking || this.epgSeek == null) {
            return;
        }
        if (!this.liveMode) {
            updateVodBar();
            return;
        }
        EpgGuide.Listing listing = this.current;
        if (listing == null) {
            this.epgSeek.setProgress(0);
            return;
        }
        long j = listing.start;
        long max = Math.max(1L, this.current.stop - j);
        if (this.catchup && (exoPlayer = this.player) != null && exoPlayer.getDuration() > 0) {
            currentTimeMillis = this.player.getCurrentPosition() + j;
        } else {
            currentTimeMillis = System.currentTimeMillis();
        }
        this.epgSeek.setProgress((int) Math.max(0L, Math.min(1000L, ((currentTimeMillis - j) * 1000) / max)));
    }

    private void updateVodBar() {
        long vodDuration = vodDuration();
        long vodPosition = vodPosition();
        TextView textView = this.epgStart;
        if (textView != null) {
            textView.setText(fmtMs(vodPosition));
        }
        TextView textView2 = this.epgEnd;
        if (textView2 != null) {
            textView2.setText(vodDuration > 0 ? fmtMs(vodDuration) : "--:--");
        }
        if (vodDuration > 0) {
            this.epgSeek.setProgress((int) Math.max(0L, Math.min(1000L, (vodPosition * 1000) / vodDuration)));
        }
    }

    private long vodDuration() {
        try {
            if (this.useVlc) {
                LiveEngine liveEngine = this.vlc;
                if (liveEngine != null) {
                    long duration = liveEngine.getDurationMs();
                    if (duration > 0) {
                        return duration;
                    }
                }
                return this.metaDurationMs > 0 ? this.metaDurationMs : 0L;
            }
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer == null) {
                return this.metaDurationMs > 0 ? this.metaDurationMs : 0L;
            }
            long duration = exoPlayer.getDuration();
            if (duration <= 0 || duration == -9223372036854775807L) {
                return this.metaDurationMs > 0 ? this.metaDurationMs : 0L;
            }
            return duration;
        } catch (Throwable unused) {
            return this.metaDurationMs > 0 ? this.metaDurationMs : 0L;
        }
    }

    private long vodPosition() {
        try {
            if (this.useVlc) {
                LiveEngine liveEngine = this.vlc;
                if (liveEngine != null) {
                    return Math.max(0L, liveEngine.getPositionMs());
                }
                return 0L;
            }
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer == null) {
                return 0L;
            }
            return Math.max(0L, exoPlayer.getCurrentPosition());
        } catch (Throwable unused) {
            return 0L;
        }
    }

    private void seekTo(long j) {
        try {
            long pos = Math.max(0L, j);
            if (this.useVlc) {
                LiveEngine liveEngine = this.vlc;
                if (liveEngine != null) {
                    liveEngine.seekToMs(pos);
                }
                return;
            }
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer == null) {
                return;
            }
            exoPlayer.seekTo(pos);
        } catch (Throwable unused) {
        }
    }

    private void seekVod(int i) {
        long vodDuration = vodDuration();
        if (vodDuration <= 0) {
            return;
        }
        seekTo((vodDuration * Math.max(0, Math.min(1000, i))) / 1000);
    }

    private void seekBy(long j) {
        long vodDuration = vodDuration();
        long max = Math.max(0L, vodPosition() + j);
        if (vodDuration > 0) {
            max = Math.min(vodDuration - 1000, max);
        }
        seekTo(max);
        updateVodBar();
    }

    private static String fmtMs(long j) {
        if (j < 0) {
            j = 0;
        }
        long j2 = j / 1000;
        long j3 = j2 / 3600;
        long j4 = (j2 % 3600) / 60;
        long j5 = j2 % 60;
        if (j3 > 0) {
            return String.format(Locale.GERMANY, "%d:%02d:%02d", Long.valueOf(j3), Long.valueOf(j4), Long.valueOf(j5));
        }
        return String.format(Locale.GERMANY, "%02d:%02d", Long.valueOf(j4), Long.valueOf(j5));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSeekEpg(int i) {
        if (!this.liveMode) {
            seekVod(i);
            return;
        }
        EpgGuide.Listing listing = this.current;
        if (listing == null) {
            return;
        }
        long max = this.current.start + ((Math.max(1L, listing.stop - this.current.start) * i) / 1000);
        if (this.liveMode) {
            Models.Channel channel = this.channel;
            if (channel != null && channel.archive && max < System.currentTimeMillis() - C.DEFAULT_SEEK_FORWARD_INCREMENT_MS) {
                playCatchup(this.current, max);
            } else {
                if (this.catchup) {
                    return;
                }
                Toast.makeText(this, "Live-Zeit. Zum Zurückblicken Archiv-Sender in der EPG-Liste wählen.", 0).show();
            }
        }
    }

    private void toggleEpg() {
        if (this.epgSheet.getVisibility() == 0) {
            closeEpg();
        } else {
            openEpg();
        }
    }

    private void openEpg() {
        this.epgSheet.setVisibility(0);
        this.bottomBar.setVisibility(8);
        long currentTimeMillis = System.currentTimeMillis();
        int i = 0;
        for (int i2 = 0; i2 < this.programmes.size(); i2++) {
            if (this.programmes.get(i2).start <= currentTimeMillis && this.programmes.get(i2).stop > currentTimeMillis) {
                i = i2;
            }
        }
        RecyclerView recyclerView = this.epgList;
        if (recyclerView == null) {
            recyclerView = (RecyclerView) findViewById(R.id.epgList);
        }
        recyclerView.scrollToPosition(Math.max(0, i - 2));
        final RecyclerView rvFinal = recyclerView;
        final int idxFinal = i;
        recyclerView.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda17
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.lambda$openEpg$16(rvFinal, idxFinal);
            }
        });
    }

    static /* synthetic */ void lambda$openEpg$16(RecyclerView recyclerView, int i) {
        RecyclerView.ViewHolder findViewHolderForAdapterPosition = recyclerView.findViewHolderForAdapterPosition(i);
        if (findViewHolderForAdapterPosition != null) {
            findViewHolderForAdapterPosition.itemView.requestFocus();
        } else {
            recyclerView.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closeEpg() {
        this.epgSheet.setVisibility(8);
        setHud(true);
        scheduleHide();
    }

    private void toggleResize() {
        Prefs prefs = new Prefs(this);
        String current = prefs.resize();
        prefs.setResize("fit".equals(current) ? "zoom" : ("zoom".equals(current) ? "stretch" : "fit"));
        applyResize();
    }

    private void applyResize() {
        String resize = new Prefs(this).resize();
        boolean zoom = "zoom".equals(resize);
        boolean stretch = "stretch".equals(resize);
        PlayerView playerView = this.playerView;
        if (playerView != null) {
            playerView.setResizeMode(stretch ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                    : (zoom ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT));
        }
        ExoPlayer exoPlayer = this.player;
        if (exoPlayer != null) {
            try {
                exoPlayer.setVideoScalingMode(zoom
                        ? C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                        : C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            } catch (Throwable ignored) {
            }
        }
        LiveEngine liveEngine = this.vlc;
        if (liveEngine instanceof VlcEngine) {
            ((VlcEngine) liveEngine).setZoom(zoom);
        }
        TextView textView = this.btnResize;
        if (textView != null) {
            textView.setText(stretch ? "Strecken" : (zoom ? "Füllen" : "Anpassen"));
            textView.setContentDescription("Video-Skalierung: " + resizeLabel());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void playLive() {
        if (this.channel == null) {
            return;
        }
        this.catchup = false;
        this.liveMode = true;
        this.errorView.setVisibility(8);
        buildQueue(this.channel.hlsUrl, this.channel.tsUrl);
        playCurrent();
        pickCurrent();
        bindEpg();
        Toast.makeText(this, "Live", 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void playCatchup(EpgGuide.Listing listing, long j) {
        if (this.channel == null || App.api == null || listing == null) {
            return;
        }
        if (!this.channel.archive) {
            Toast.makeText(this, "Dieser Sender hat kein Archiv.", 0).show();
            return;
        }
        long max = Math.max(listing.start, j);
        if (max >= listing.stop - 30000) {
            max = listing.start;
        }
        long j2 = max;
        if (listing.stop < System.currentTimeMillis() - (Math.max(1, this.channel.archiveDays) * 86400000)) {
            Toast.makeText(this, "Außerhalb des Archivs.", 0).show();
            return;
        }
        this.catchup = true;
        this.liveMode = true;
        this.current = listing;
        this.errorView.setVisibility(8);
        this.queue.clear();
        Iterator<String> it = App.api.timeshiftUrls(this.channel, j2, listing.stop).iterator();
        while (it.hasNext()) {
            addUrl(it.next());
        }
        this.index = 0;
        playCurrent();
        bindEpg();
        this.playerSub.setText("Archiv · " + listing.title);
        this.epgSheet.setVisibility(8);
        this.bottomBar.setVisibility(0);
    }

    private void buildQueue(String str, String str2) {
        invalidatePlayback();
        this.queue.clear();
        // Use only URLs actually supplied by the provider. Guessing ".m3u8"/".ts"
        // creates invalid fallback requests and makes channel changes appear to hang.
        addUrl(str);
        addUrl(str2);
        this.index = 0;
    }

    private void addUrl(String str) {
        if (str == null) {
            return;
        }
        String trim = str.trim();
        if (trim.isEmpty() || this.queue.contains(trim)) {
            return;
        }
        this.queue.add(trim);
    }

    private void applyHeaders(String str) {
        if (this.http == null || str == null) {
            return;
        }
        HashMap hashMap = new HashMap();
        if (ExtraLiveSource.isCdn(str) || ExtraLiveSource.isPlayUrl(str)) {
            hashMap.put(HttpHeaders.USER_AGENT, "okhttp/4.11.0");
            hashMap.put(HttpHeaders.ACCEPT, "*/*");
            this.http.setUserAgent("okhttp/4.11.0");
        } else if (str.contains("gxplayer") || str.contains("/m3u8/") || str.contains("master.txt")) {
            hashMap.put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            hashMap.put(HttpHeaders.REFERER, "https://watch.gxplayer.xyz/");
            hashMap.put(HttpHeaders.ORIGIN, "https://watch.gxplayer.xyz");
            this.http.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        } else {
            hashMap.put(HttpHeaders.USER_AGENT, "VLC/3.0.21 LibVLC/3.0.21");
            hashMap.put(HttpHeaders.REFERER, originOf(str));
            this.http.setUserAgent("VLC/3.0.21 LibVLC/3.0.21");
        }
        this.http.setDefaultRequestProperties((Map<String, String>) hashMap);
    }

    private static String originOf(String str) {
        if (str != null && !str.isEmpty()) {
            try {
                Uri parse = Uri.parse(str);
                return parse.getScheme() + "://" + parse.getHost() + (parse.getPort() > 0 ? ":" + parse.getPort() : "") + "/";
            } catch (Exception unused) {
            }
        }
        return "";
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:15:0x0030 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:17:0x0034 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:19:0x0038 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:28:0x0066 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:52:0x00cb A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:57:0x0110 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:60:0x0118 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:63:0x0163 A[Catch: all -> 0x0169, TRY_LEAVE, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:65:? A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:66:0x0133 A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /* JADX WARN: Removed duplicated region for block: B:67:0x00ef A[Catch: all -> 0x0169, TryCatch #0 {all -> 0x0169, blocks: (B:8:0x0013, B:10:0x0024, B:15:0x0030, B:17:0x0034, B:19:0x0038, B:23:0x003d, B:25:0x0043, B:26:0x004d, B:28:0x0066, B:30:0x006e, B:32:0x0076, B:34:0x0082, B:36:0x008b, B:38:0x0091, B:40:0x0095, B:42:0x009e, B:44:0x00a9, B:45:0x00b0, B:50:0x00c0, B:52:0x00cb, B:54:0x00d4, B:55:0x0103, B:57:0x0110, B:60:0x0118, B:61:0x0149, B:63:0x0163, B:66:0x0133, B:67:0x00ef, B:69:0x00fd, B:71:0x007e), top: B:7:0x0013 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */

    private void toastPlaybackError(final String msg) {
        try {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
                        if (errorView != null) {
                            errorView.setVisibility(0);
                            errorView.setText(msg);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    public void playCurrent() {
        if (isFinishing() || isDestroyed() || !foreground) {
            restartOnResume = true;
            return;
        }
        int i = this.index;
        if (i < 0 || i >= this.queue.size()) {
            TextView textView2 = this.errorView;
            if (textView2 != null) {
                textView2.setVisibility(0);
                this.errorView.setText("Keine Stream-URL.");
            }
            return;
        }
        try {
            String str = this.queue.get(this.index);
            boolean isPlayUrl = ExtraLiveSource.isPlayUrl(str);
            boolean z = isPlayUrl || ExtraLiveSource.isCdn(str);
            if (isPlayUrl) {
                this.extraLiveKeep = str;
            }
            if (z && !isPlayUrl) {
                this.extraLiveHot = str;
            }
            if (isPlayUrl) {
                if (this.resolving) {
                    return;
                }
                this.resolving = true;
                TextView textView3 = this.errorView;
                if (textView3 != null) {
                    textView3.setVisibility(0);
                    this.errorView.setText("Live Extra wird geladen…");
                }
                this.extraLiveKeep = str;
                final String resolveUrl = str;
                final long request = playbackGeneration;
                final Runnable runnable = new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda20
                    @Override // java.lang.Runnable
                    public final void run() {
                        if (request == playbackGeneration) PlayerActivity.this.lambda$playCurrent$17();
                    }
                };
                UI.postDelayed(runnable, 16000);
                IO.execute(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda21
                    @Override // java.lang.Runnable
                    public final void run() {
                        PlayerActivity.this.lambda$playCurrent$19(resolveUrl, runnable, request);
                    }
                });
                return;
            }
            String headerUrl = str;
            if (str.contains("gxplayer") || str.contains("master.txt") || str.contains("/m3u8/")) {
                str = LocalHls.wrap(str);
            }
            this.lastPlayUrl = str;
            this.freezeTicks = 0;
            this.userPaused = false;
            applyHeaders(headerUrl);
            if ("exo".equals(this.forceEngine)) {
                // session fallback or forced Exo — never re-enter VLC
            } else if ((!z && wantVlc()) || "vlc".equals(this.forceEngine)) {
                playWithVlc(str);
                return;
            }
            if (this.player == null) {
                if (!"exo".equals(this.forceEngine) && !"exo".equals(playerPref())) {
                    lastFallbackReason = "Streamy Player konnte nicht initialisiert werden";
                    playWithVlc(str);
                    return;
                }
                throw new IllegalStateException("Streamy Player nicht verfügbar");
            }
            hideVlc();
            this.useVlc = false;
            PlayerView playerView = this.playerView;
            if (playerView != null) {
                playerView.setVisibility(0);
                if (this.playerView.getPlayer() == null) {
                    this.playerView.setPlayer(this.player);
                }
            }
            Uri parse = Uri.parse(str);
            boolean z2 = isHls(str) || z;
            MediaItem.Builder uri = new MediaItem.Builder().setUri(parse);
            if (z2) {
                uri.setMimeType(MimeTypes.APPLICATION_M3U8);
                if (this.liveMode) {
                    uri.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(0.96f).setMaxPlaybackSpeed(1.04f).build());
                }
            } else if (str.toLowerCase(Locale.US).contains(".ts")) {
                uri.setMimeType(MimeTypes.VIDEO_MP2T);
            }
            MediaItem build = uri.build();
            LiveRetry liveRetry = new LiveRetry(this.liveMode && !this.catchup);
            DataSource.Factory factory = this.http;
            if (z) {
                factory = OkPlay.factory();
            }
            MediaSource createMediaSource;
            if (z2) {
                createMediaSource = new HlsMediaSource.Factory(factory).setAllowChunklessPreparation(true).setExtractorFactory(new DefaultHlsExtractorFactory(73, true)).setLoadErrorHandlingPolicy((LoadErrorHandlingPolicy) liveRetry).createMediaSource(build);
            } else {
                createMediaSource = new ProgressiveMediaSource.Factory(factory, new DefaultExtractorsFactory().setTsExtractorFlags(73)).setLoadErrorHandlingPolicy((LoadErrorHandlingPolicy) liveRetry).createMediaSource(build);
            }
            this.player.setMediaSource(createMediaSource);
            this.player.setVolume(1.0f);
            this.player.prepare();
            this.player.setPlayWhenReady(true);
            TextView textView = this.errorView;
            if (textView != null) {
                textView.setVisibility(8);
            }
        } catch (Throwable unused) {
            TextView textView4 = this.errorView;
            String msg = "Wiedergabe fehlgeschlagen" + (unused.getMessage() != null ? (": " + unused.getMessage()) : ".");
            this.lastExoError = msg;
            if (this.liveMode && !this.catchup && !this.useVlc
                    && !isExtraLivePlayback() && "auto".equals(playerPref())
                    && this.index >= 0 && this.index < this.queue.size()) {
                this.lastFallbackReason = msg;
                playWithVlc(this.queue.get(this.index));
                return;
            }
            if (textView4 != null) {
                textView4.setVisibility(0);
                this.errorView.setText(msg);
            }
            toastPlaybackError(msg);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playCurrent$17() {
        if (this.resolving) {
            TextView textView = this.errorView;
            if (textView != null) {
                textView.setVisibility(0);
                this.errorView.setText("Live Extra antwortet langsam. Verbindung wird weiter geprüft…");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playCurrent$19(String str, final Runnable runnable, final long request) {
        String resolved = null;
        try {
            resolved = ExtraLiveSource.resolve(str);
        } catch (Throwable unused) {
            resolved = null;
        }
        final String str2 = resolved;
        UI.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda25
            @Override // java.lang.Runnable
            public final void run() {
                if (acceptPlayback(request)) PlayerActivity.this.lambda$playCurrent$18(runnable, str2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playCurrent$18(Runnable runnable, String str) {
        UI.removeCallbacks(runnable);
        if (isFinishing() || isDestroyed()) {
            this.resolving = false;
            return;
        }
        this.resolving = false;
        if (str == null || str.isEmpty()) {
            TextView textView = this.errorView;
            String err = "Live Extra-Stream nicht erreichbar. Sender erneut tippen.";
            if (ExtraLiveSource.lastError != null && !ExtraLiveSource.lastError.isEmpty()) {
                err = err + " (" + ExtraLiveSource.lastError + ")";
            }
            if (textView != null) {
                textView.setVisibility(0);
                this.errorView.setText(err);
            }
            toastPlaybackError(err);
            return;
        }
        this.extraLiveHot = str;
        int i = this.index;
        if (i >= 0 && i < this.queue.size()) {
            this.queue.set(this.index, str);
        }
        playCurrent();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pickPlayableAudio(Tracks tracks) {
        if (this.player == null || tracks == null) {
            return;
        }
        String mode = new Prefs(this).audioMode();
        Tracks.Group selectedGroup = null;
        int selectedIndex = -1;
        Format selectedFormat = null;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i) && group.isTrackSupported(i)) {
                    selectedGroup = group;
                    selectedIndex = i;
                    selectedFormat = group.getTrackFormat(i);
                    break;
                }
            }
            if (selectedFormat != null) break;
        }

        if ("stereo".equals(mode)) {
            if (selectedFormat != null && (selectedFormat.channelCount <= 0 || selectedFormat.channelCount <= 2)) {
                return;
            }
            AudioPick stereo = bestAudioTrack(tracks, selectedFormat, true);
            if (stereo != null) applyAudioPick(stereo);
            return;
        }

        boolean preferSurround = "surround".equals(mode);
        if (!preferSurround && selectedFormat != null) {
            return;
        }

        AudioPick best = bestAudioTrack(tracks, selectedFormat, false);
        if (best == null) {
            return;
        }
        if (selectedFormat != null) {
            int currentChannels = selectedFormat.channelCount > 0 ? selectedFormat.channelCount : 0;
            int bestChannels = best.format.channelCount > 0 ? best.format.channelCount : 0;
            if (bestChannels <= currentChannels) {
                return;
            }
        }
        applyAudioPick(best);
    }

    private static final class AudioPick {
        final Tracks.Group group;
        final int index;
        final Format format;
        final int score;

        AudioPick(Tracks.Group group, int index, Format format, int score) {
            this.group = group;
            this.index = index;
            this.format = format;
            this.score = score;
        }
    }

    private AudioPick bestAudioTrack(Tracks tracks, Format selectedFormat, boolean stereoOnly) {
        String selectedLanguage = selectedFormat == null || selectedFormat.language == null
                ? "" : selectedFormat.language.trim();
        AudioPick best = null;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                int channels = format.channelCount > 0 ? format.channelCount : 2;
                if (stereoOnly && channels > 2) continue;

                String language = format.language == null ? "" : format.language.trim();
                boolean sameLanguage = selectedLanguage.isEmpty() || selectedLanguage.equalsIgnoreCase(language);
                int score = sameLanguage ? 10000 : 0;
                score += Math.min(channels, 8) * 100;
                String mime = format.sampleMimeType == null ? "" : format.sampleMimeType.toLowerCase(Locale.US);
                if (mime.contains("eac3") || mime.contains("e-ac3")) score += 40;
                else if (mime.contains("ac3")) score += 30;
                else if (mime.contains("dts")) score += 20;
                else if (mime.contains("aac")) score += 10;

                if (best == null || score > best.score) {
                    best = new AudioPick(group, i, format, score);
                }
            }
        }
        return best;
    }

    private void applyAudioPick(AudioPick pick) {
        if (pick == null || this.player == null) return;
        this.player.setTrackSelectionParameters(
                this.player.getTrackSelectionParameters().buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(new TrackSelectionOverride(
                                pick.group.getMediaTrackGroup(), pick.index))
                        .build());
    }

    private boolean wantVlc() {
        if ("exo".equals(this.forceEngine)) {
            return false;
        }
        if ("vlc".equals(this.forceEngine) || this.useVlc) {
            return true;
        }
        return "vlc".equals(playerPref());
    }

    private boolean isExtraLivePlayback() {
        if (liveMode && channel != null) return channel.extraLiveUrl != null && !channel.extraLiveUrl.isEmpty();
        return getIntent() != null && getIntent().getBooleanExtra("extra_live", false);
    }

    /** Source-specific player pref: Live Extra / Live TV / other (VOD). */
    private String playerPref() {
        Prefs prefs = new Prefs(this);
        if (isExtraLivePlayback()) {
            return prefs.playerExtraLive();
        }
        if (this.liveMode) {
            return prefs.playerLive();
        }
        return prefs.player();
    }

    private void setPlayerPref(String str) {
        Prefs prefs = new Prefs(this);
        if (isExtraLivePlayback()) {
            prefs.setPlayerExtraLive(str);
        } else if (this.liveMode) {
            prefs.setPlayerLive(str);
            prefs.setPlayer(str);
        } else {
            prefs.setPlayer(str);
        }
    }


    private DefaultLoadControl buildLoadControl() {
        Prefs prefs = new Prefs(this);
        String buf = prefs.buffer();
        boolean lowRam = App.isLowRam(this);
        boolean tv = Tv.isTv(this);
        int minBuf;
        int maxBuf;
        int playback;
        int afterRebuffer;
        // Low-RAM / TV sticks: keep Exo buffers tight (≈2–8s) to avoid LMK.
        if (lowRam || (tv && !"high".equals(buf) && !"max".equals(buf))) {
            if ("max".equals(buf)) {
                minBuf = 3000; maxBuf = 10000; playback = 1500; afterRebuffer = 2500;
            } else if ("high".equals(buf)) {
                minBuf = 2500; maxBuf = 9000; playback = 1500; afterRebuffer = 2500;
            } else if ("low".equals(buf)) {
                minBuf = 1500; maxBuf = 5000; playback = 1000; afterRebuffer = 1500;
            } else {
                minBuf = 2000; maxBuf = 8000; playback = 1500; afterRebuffer = 2000;
            }
        } else {
            if ("low".equals(buf)) {
                minBuf = 2500; maxBuf = 8000; playback = 1500; afterRebuffer = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
            } else if ("high".equals(buf)) {
                minBuf = 5000; maxBuf = 20000; playback = 2000; afterRebuffer = 4000;
            } else if ("max".equals(buf)) {
                minBuf = 8000; maxBuf = 30000; playback = 2500; afterRebuffer = 5000;
            } else {
                minBuf = 4000; maxBuf = 14000; playback = 1500; afterRebuffer = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
            }
        }
        // For normal live TV, favor quick tuning/zapping unless the user explicitly
        // selected a large buffer. High/max remain untouched for unstable connections.
        if (this.liveMode && !this.catchup && !"high".equals(buf) && !"max".equals(buf)) {
            minBuf = Math.min(minBuf, 1800);
            maxBuf = Math.min(maxBuf, 7000);
            playback = Math.min(playback, 1000);
            afterRebuffer = Math.min(afterRebuffer, 1600);
        }
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(minBuf, maxBuf, playback, afterRebuffer)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private void hideVlc() {
        try {
            LiveEngine liveEngine = this.vlc;
            this.vlc = null;
            if (liveEngine != null) {
                // Fully release native VLC surfaces/resources when switching to Exo
                liveEngine.stop(true);
            }
        } catch (Throwable unused) {
        }
        ViewGroup viewGroup = this.vlcHost;
        if (viewGroup != null) {
            try {
                viewGroup.removeAllViews();
            } catch (Throwable ignored) {
            }
            viewGroup.setVisibility(8);
        }
    }


    private void bindVlcPlaybackListener(LiveEngine liveEngine) {
        final long request = playbackGeneration;
        this.vlcHudArmed = false;
        if (!(liveEngine instanceof VlcEngine)) {
            return;
        }
        ((VlcEngine) liveEngine).setPlaybackListener(new VlcEngine.PlaybackListener() {
            @Override public void onPlaying() {
                PlayerActivity.UI.post(new Runnable() {
                    @Override public void run() {
                        if (acceptPlayback(request) && vlc == liveEngine && useVlc && !userPaused) {
                            PlayerActivity.this.onVlcPlaying();
                        }
                    }
                });
            }
            @Override public void onPaused() {
                PlayerActivity.UI.post(new Runnable() {
                    @Override public void run() {
                        if (acceptPlayback(request) && vlc == liveEngine && useVlc && !userPaused) {
                            PlayerActivity.this.updatePlayIcon();
                        }
                    }
                });
            }
            @Override public void onError() {
                PlayerActivity.UI.post(new Runnable() {
                    @Override public void run() {
                        if (acceptPlayback(request) && vlc == liveEngine && useVlc && !userPaused) {
                            PlayerActivity.this.tryExoAfterVlc();
                        }
                    }
                });
            }
        });
    }

    private void playWithVlc(final String str) {
        this.useVlc = true;
        if (vlcStarting) return;
        final long request = playbackGeneration;
        try {
            ExoPlayer exoPlayer = this.player;
            if (exoPlayer != null) {
                try {
                    exoPlayer.setPlayWhenReady(false);
                } catch (Throwable ignored) {
                }
                try {
                    exoPlayer.stop();
                } catch (Throwable ignored) {
                }
                try {
                    exoPlayer.clearMediaItems();
                } catch (Throwable ignored) {
                }
            }
            PlayerView playerView = this.playerView;
            if (playerView != null) {
                playerView.setPlayer(null);
                this.playerView.setVisibility(8);
            }
            if (this.vlcHost == null) {
                this.vlcHost = (ViewGroup) findViewById(R.id.vlcHost);
            }
            LiveEngine liveEngine = this.vlc;
            if (liveEngine != null) {
                bindVlcPlaybackListener(liveEngine);
                liveEngine.play(str, true ^ this.vlcSoft);
                TextView textView = this.errorView;
                if (textView != null) {
                    textView.setVisibility(8);
                    return;
                }
                return;
            }
            TextView textView2 = this.errorView;
            if (textView2 != null) {
                textView2.setVisibility(0);
                this.errorView.setText("VLC startet…");
            }
            vlcStarting = true;
            IO.execute(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda18
                @Override // java.lang.Runnable
                public final void run() {
                    PlayerActivity.this.lambda$playWithVlc$21(str, request);
                }
            });
        } catch (Throwable unused) {
            this.useVlc = false;
            this.lastVlcError = unused.getMessage() == null ? "VLC-Start fehlgeschlagen." : unused.getMessage();
            TextView textView3 = this.errorView;
            if (textView3 != null) {
                textView3.setVisibility(0);
                this.errorView.setText("VLC-Start fehlgeschlagen.");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playWithVlc$21(final String str, final long request) {
        LiveEngine engine = null;
        try {
            engine = VlcFactory.create(this, this.vlcHost);
        } catch (OutOfMemoryError oom) {
            engine = null;
            UI.post(new Runnable() {
                @Override public void run() {
                    try {
                        Toast.makeText(PlayerActivity.this, "Zu wenig Speicher für VLC", Toast.LENGTH_LONG).show();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable unused) {
            engine = null;
        }
        final LiveEngine liveEngine = engine;
        UI.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda19
            @Override // java.lang.Runnable
            public final void run() {
                if (!acceptPlayback(request) || !useVlc) {
                    if (liveEngine != null) liveEngine.stop(true);
                    return;
                }
                vlcStarting = false;
                PlayerActivity.this.lambda$playWithVlc$20(liveEngine, str);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playWithVlc$20(LiveEngine liveEngine, String str) {
        if (isFinishing() || isDestroyed()) {
            if (liveEngine != null) {
                try {
                    liveEngine.stop(true);
                    return;
                } catch (Throwable unused) {
                    return;
                }
            }
            return;
        }
        if (liveEngine == null) {
            this.useVlc = false;
            String err = VlcFactory.lastError;
            if (err == null || err.isEmpty()) {
                err = "VLC/libVLC nicht verfügbar.";
            }
            this.lastVlcError = err;
            TextView textView = this.errorView;
            if (textView != null) {
                textView.setVisibility(0);
                textView.setText(err);
            }
            toastPlaybackError(err);
            return;
        }
        this.vlc = liveEngine;
        bindVlcPlaybackListener(liveEngine);
        applyResize();
        liveEngine.play(str, !this.vlcSoft);
        TextView textView2 = this.errorView;
        if (textView2 != null) {
            textView2.setVisibility(8);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tryExoAfterVlc() {
        if (this.extraLiveTriedVlc) {
            TextView textView = this.errorView;
            if (textView != null) {
                textView.setVisibility(0);
                this.errorView.setText("Live Extra-Stream kommt nicht.");
            }
            return;
        }
        this.extraLiveTriedVlc = true;
        this.freezeTicks = 0;
        hideVlc();
        this.useVlc = false;
        // forceEngine "vlc" would immediately re-enter playWithVlc — override for this session
        this.forceEngine = "exo";
        TextView textView2 = this.errorView;
        if (textView2 != null) {
            textView2.setVisibility(0);
            this.errorView.setText("VLC fehlgeschlagen → Exo");
        }
        try {
            Toast.makeText(this, "VLC fehlgeschlagen → Exo", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
        paintPlayerBtn();
        playCurrent();
    }

    private boolean isHls(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        if ((lowerCase.contains("127.0.0.1") && lowerCase.contains("/p?")) || lowerCase.contains("master.txt") || lowerCase.contains("/m3u8/")) {
            return true;
        }
        if (lowerCase.contains(".mp4") || lowerCase.contains(".mkv") || lowerCase.contains(".avi")) {
            return false;
        }
        if (lowerCase.contains(".m3u8") || lowerCase.contains("timeshift.php") || lowerCase.contains("/hls/") || lowerCase.contains("/m3u8/") || lowerCase.contains("/alternative_stream/") || lowerCase.contains("master.txt") || ExtraLiveSource.isCdn(str) || ExtraLiveSource.isPlayUrl(str) || lowerCase.contains("/sunshine/")) {
            return true;
        }
        return this.liveMode && !lowerCase.contains(".ts");
    }

    private boolean allowVlc() {
        return !"exo".equals(playerPref());
    }

    private boolean audioNeedsVlc(Tracks tracks) {
        String str;
        Iterator<Tracks.Group> it = tracks.getGroups().iterator();
        loop0: while (true) {
            if (!it.hasNext()) {
                return false;
            }
            Tracks.Group next = it.next();
            if (next.getType() == 1) {
                for (int i = 0; i < next.length; i++) {
                    if (next.isTrackSelected(i) && (str = next.getTrackFormat(i).sampleMimeType) != null) {
                        String lowerCase = str.toLowerCase(Locale.US);
                        if (lowerCase.contains("ac3") || lowerCase.contains("eac3") || lowerCase.contains("dts") || lowerCase.contains("mpeg-l") || lowerCase.contains("mp2") || lowerCase.contains("true-hd")) {
                            break loop0;
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean looksBroadcast(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.contains(".ts") || lowerCase.contains("/live/") || lowerCase.contains("mpegts");
    }

    /**
     * Adapts Streamy 2's data to the player chrome used by Streamy 3 / AerioTV 0.5.9.
     * The playback surface and Streamy 2 provider handling stay native to this activity.
     */
    private void configureAerioChrome() {
        boolean tv = Tv.isTv(this);
        View close = findViewById(R.id.btnBack);
        if (close != null) close.setVisibility(tv ? View.GONE : View.VISIBLE);
        View epgBand = findViewById(R.id.epgBand);
        if (epgBand != null) epgBand.setVisibility(tv && this.liveMode ? View.GONE : View.VISIBLE);

        ImageView logo = (ImageView) findViewById(R.id.playerLogo);
        TextView fallback = (TextView) findViewById(R.id.playerLogoText);
        String name = this.channel == null ? "Streamy 2" : Text.clean(this.channel.name);
        String logoUrl = this.channel == null ? null : this.channel.logo;
        if (logo != null && logoUrl != null && !logoUrl.trim().isEmpty()) {
            logo.setVisibility(View.VISIBLE);
            if (fallback != null) fallback.setVisibility(View.GONE);
            Images.load(logo, logoUrl);
        } else {
            if (logo != null) logo.setVisibility(View.GONE);
            if (fallback != null) {
                fallback.setVisibility(View.VISIBLE);
                fallback.setText(initials(name));
            }
        }
    }

    private static String initials(String value) {
        if (value == null || value.trim().isEmpty()) return "S2";
        String[] words = value.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0)));
            if (out.length() == 2) break;
        }
        return out.length() == 0 ? "S2" : out.toString();
    }

    private void installPlayerFocusEffects() {
        final TextView caption = (TextView) findViewById(R.id.playerFocusCaption);
        int[] ids = new int[]{R.id.btnPrevCh, R.id.btnPlay, R.id.btnNextCh, R.id.btnEpg, R.id.btnPlayer};
        for (int id : ids) {
            final View control = findViewById(id);
            if (control == null) continue;
            control.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) {
                    float scale = focused && Tv.isTv(PlayerActivity.this) ? 1.14f : (focused ? 1.06f : 1.0f);
                    view.animate().scaleX(scale).scaleY(scale).setDuration(90L).start();
                    if (Build.VERSION.SDK_INT >= 21) {
                        view.setElevation(focused ? 12f * getResources().getDisplayMetrics().density : 0f);
                    }
                    if (caption != null) {
                        caption.setText(focused ? String.valueOf(view.getContentDescription()) : "");
                        caption.setAlpha(focused ? 1.0f : 0.75f);
                    }
                    if (focused) scheduleHide();
                }
            });
        }
    }

    private void showPlayerDialog(final AlertDialog dialog, final int initialPosition) {
        dialog.setOnShowListener(ignored -> {
            if (!Tv.isTv(this)) return;
            final android.widget.ListView list = dialog.getListView();
            if (list != null) {
                list.setSelector(R.drawable.bg_player_dialog_item);
                list.setFocusable(true);
                list.setFocusableInTouchMode(false);
                list.setDrawSelectorOnTop(false);
                list.post(() -> {
                    if (list.getCount() > 0) {
                        int position = Math.max(0, Math.min(initialPosition, list.getCount() - 1));
                        list.setSelection(position);
                    }
                    list.requestFocus();
                });
            }
            final View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setFocusable(true);
                negative.setBackgroundResource(R.drawable.bg_btn_sec);
            }
        });
        dialog.show();
    }

    private void showPlayerOptions() {
        final ArrayList<String> items = new ArrayList<>();
        items.add("Untertitel");
        items.add("Audiospur");
        items.add("Wiedergabegeschwindigkeit");
        items.add("Video-Skalierung: " + resizeLabel());
        items.add("Sleep-Timer");
        items.add("Stream-Info");
        items.add(this.audioOnly ? "Video anzeigen" : "Nur Audio");
        if (this.liveMode) items.add("Programm");
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Optionen")
                .setItems(items.toArray(new String[0]), (itemDialog, which) -> {
                    String selected = items.get(which);
                    if (selected.equals("Untertitel")) showTrackOptions(C.TRACK_TYPE_TEXT);
                    else if (selected.equals("Audiospur")) showTrackOptions(C.TRACK_TYPE_AUDIO);
                    else if (selected.equals("Wiedergabegeschwindigkeit")) showSpeedOptions();
                    else if (selected.startsWith("Video-Skalierung")) showResizeOptions();
                    else if (selected.equals("Sleep-Timer")) showSleepTimer();
                    else if (selected.equals("Stream-Info")) showDiagnostics();
                    else if (selected.equals("Nur Audio") || selected.equals("Video anzeigen")) toggleAudioOnly();
                    else if (selected.equals("Programm")) openEpg();
                })
                .setNegativeButton("Schließen", null)
                .create();
        showPlayerDialog(dialog, 0);
    }

    private void showTrackOptions(final int trackType) {
        final ExoPlayer exo = this.player;
        if (exo == null || this.useVlc) {
            Toast.makeText(this, "Spurauswahl ist im Streamy Player verfügbar.", Toast.LENGTH_SHORT).show();
            return;
        }
        final ArrayList<Tracks.Group> groups = new ArrayList<>();
        final ArrayList<Integer> trackIndexes = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();
        int selectedIndex = -1;
        if (trackType == C.TRACK_TYPE_TEXT) {
            labels.add("Aus");
            groups.add(null);
            trackIndexes.add(-1);
            selectedIndex = 0;
        }
        for (Tracks.Group group : exo.getCurrentTracks().getGroups()) {
            if (group.getType() != trackType) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                String title = format.label;
                String language = languageLabel(format.language);
                if (title == null || title.trim().isEmpty()) title = "Spur " + (labels.size() + (trackType == C.TRACK_TYPE_TEXT ? 0 : 1));
                StringBuilder label = new StringBuilder(title);
                if (!language.isEmpty() && !title.toLowerCase(Locale.GERMANY).contains(language.toLowerCase(Locale.GERMANY))) {
                    label.append("  ·  ").append(language);
                }
                if (trackType == C.TRACK_TYPE_AUDIO && format.channelCount > 0) {
                    label.append("  ·  ").append(format.channelCount).append(" Kanäle");
                }
                groups.add(group);
                trackIndexes.add(i);
                labels.add(label.toString());
                if (group.isTrackSelected(i)) selectedIndex = labels.size() - 1;
            }
        }
        if (labels.isEmpty() || (trackType == C.TRACK_TYPE_TEXT && labels.size() == 1)) {
            Toast.makeText(this, trackType == C.TRACK_TYPE_TEXT
                    ? "Der Stream meldet keine Untertitelspuren."
                    : "Der Stream meldet keine Audiospuren.", Toast.LENGTH_LONG).show();
            return;
        }
        final int checked = selectedIndex;
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(trackType == C.TRACK_TYPE_TEXT ? "Untertitel" : "Audiospur")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (itemDialog, which) -> {
                    androidx.media3.common.TrackSelectionParameters.Builder params = exo.getTrackSelectionParameters().buildUpon()
                            .clearOverridesOfType(trackType);
                    Tracks.Group group = groups.get(which);
                    int track = trackIndexes.get(which);
                    if (group == null || track < 0) {
                        params.setTrackTypeDisabled(trackType, true);
                    } else {
                        params.setTrackTypeDisabled(trackType, false)
                                .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), track));
                    }
                    exo.setTrackSelectionParameters(params.build());
                    itemDialog.dismiss();
                })
                .setNegativeButton("Schließen", null)
                .create();
        showPlayerDialog(dialog, checked < 0 ? 0 : checked);
    }

    private static String languageLabel(String language) {
        if (language == null || language.trim().isEmpty() || "und".equalsIgnoreCase(language)) return "";
        try {
            Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
            String label = locale.getDisplayLanguage(Locale.GERMAN);
            return label == null || label.isEmpty() ? language : label;
        } catch (Throwable ignored) {
            return language;
        }
    }

    private void showSpeedOptions() {
        final ExoPlayer exo = this.player;
        if (exo == null || this.useVlc) {
            Toast.makeText(this, "Wiedergabegeschwindigkeit ist im Streamy Player verfügbar.", Toast.LENGTH_SHORT).show();
            return;
        }
        final float[] values = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        final String[] labels = new String[]{"0,5×", "0,75×", "Normal (1,0×)", "1,25×", "1,5×", "2,0×"};
        float current = exo.getPlaybackParameters().speed;
        int checked = 2;
        for (int i = 0; i < values.length; i++) if (Math.abs(current - values[i]) < 0.01f) checked = i;
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Wiedergabegeschwindigkeit")
                .setSingleChoiceItems(labels, checked, (itemDialog, which) -> {
                    exo.setPlaybackParameters(new PlaybackParameters(values[which]));
                    itemDialog.dismiss();
                })
                .setNegativeButton("Schließen", null)
                .create();
        showPlayerDialog(dialog, checked);
    }

    private void showSleepTimer() {
        final int[] minutes = new int[]{0, 15, 30, 45, 60, 90};
        final String[] labels = new String[]{"Aus", "15 Minuten", "30 Minuten", "45 Minuten", "60 Minuten", "90 Minuten"};
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Sleep-Timer")
                .setItems(labels, (itemDialog, which) -> {
                    if (this.sleepTimer != null) UI.removeCallbacks(this.sleepTimer);
                    this.sleepTimer = null;
                    if (minutes[which] > 0) {
                        this.sleepTimer = new Runnable() {
                            @Override public void run() { PlayerActivity.this.leave(); }
                        };
                        UI.postDelayed(this.sleepTimer, minutes[which] * 60_000L);
                        Toast.makeText(this, "Sleep-Timer: " + labels[which], Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Sleep-Timer aus", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Schließen", null)
                .create();
        showPlayerDialog(dialog, 0);
    }

    private void toggleAudioOnly() {
        this.audioOnly = !this.audioOnly;
        ExoPlayer exo = this.player;
        if (exo != null) {
            exo.setTrackSelectionParameters(exo.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, this.audioOnly).build());
        }
        if (this.playerView != null) this.playerView.setVisibility(this.audioOnly ? View.INVISIBLE : View.VISIBLE);
        if (this.vlcHost != null) this.vlcHost.setVisibility(this.audioOnly ? View.INVISIBLE : (this.useVlc ? View.VISIBLE : View.GONE));
        Toast.makeText(this, this.audioOnly ? "Nur Audio" : "Video anzeigen", Toast.LENGTH_SHORT).show();
    }

    private String resizeLabel() {
        String value = new Prefs(this).resize();
        if ("stretch".equals(value)) return "Strecken";
        if ("zoom".equals(value)) return "Füllen";
        return "Anpassen";
    }

    private void showResizeOptions() {
        final String[] values = new String[]{"fit", "zoom", "stretch"};
        final String[] labels = new String[]{"Anpassen", "Füllen", "Strecken"};
        String current = new Prefs(this).resize();
        int checked = "zoom".equals(current) ? 1 : ("stretch".equals(current) ? 2 : 0);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Video-Skalierung")
                .setSingleChoiceItems(labels, checked, (itemDialog, which) -> {
                    new Prefs(this).setResize(values[which]);
                    applyResize();
                    itemDialog.dismiss();
                })
                .setNegativeButton("Schließen", null)
                .create();
        showPlayerDialog(dialog, checked);
    }

    private void cyclePlayer() {
        String player = playerPref();
        String str = "auto";
        if ("auto".equals(player)) {
            str = "exo";
        } else if ("exo".equals(player)) {
            str = "vlc";
        }
        setPlayerPref(str);
        this.forceEngine = "auto".equals(str) ? null : str;
        paintPlayerBtn();
        this.useVlc = "vlc".equals(str);
        this.vlcSoft = false;
        ExoPlayer exoPlayer = this.player;
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
            } catch (Throwable unused) {
            }
        }
        hideVlc();
        this.index = 0;
        playCurrent();
        String scope = isExtraLivePlayback() ? "Live Extra" : (this.liveMode ? "Live" : "VOD");
        Toast.makeText(this, "Player " + scope + ": " + labelPlayer(str), 0).show();
    }

    private void paintPlayerBtn() {
        TextView textView = this.btnPlayer;
        if (textView == null) {
            return;
        }
        String eng = this.forceEngine;
        if (eng == null || eng.isEmpty()) {
            eng = playerPref();
        }
        textView.setText("");
        textView.setContentDescription("Optionen");
    }

    private static String labelPlayer(String str) {
        return "vlc".equals(str) ? "VLC" : "exo".equals(str) ? "Streamy" : "Auto";
    }

    /* JADX WARN: Removed duplicated region for block: B:12:0x0023  */
    /* JADX WARN: Removed duplicated region for block: B:25:0x004f  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private void zap(int i) {
        if (!this.liveMode || App.live == null || App.live.isEmpty()) {
            return;
        }
        Models.Channel channel = this.channel;
        if (channel == null) {
            channel = App.playing;
        }
        String str = channel != null ? channel.id : null;
        int i3 = -1;
        if (str != null) {
            for (int idx = 0; idx < App.live.size(); idx++) {
                Models.Channel channel2 = App.live.get(idx);
                if (channel2 != null && !channel2.header && str.equals(channel2.id)) {
                    i3 = idx;
                    break;
                }
            }
        }
        if (i3 < 0) {
            i3 = 0;
        }
        for (int i2 = 0; i2 < App.live.size(); i2++) {
            i3 = ((i3 + i) + App.live.size()) % App.live.size();
            Models.Channel channel3 = App.live.get(i3);
            if (channel3 != null && !channel3.header) {
                playChannel(channel3);
                return;
            }
        }
    }

    private void playChannel(Models.Channel channel) {
        if (channel == null) return;
        this.userPaused = false;
        this.programmes.clear();
        this.current = null;
        this.nextEpgSync = 0;
        this.forceEngine = null;
        this.extraLiveKeep = null;
        this.extraLiveHot = null;
        this.extraLiveTriedVlc = false;
        this.recoverTries = 0;
        this.lastExoError = "";
        this.lastVlcError = "";
        this.lastFallbackReason = "";
        App.playing = channel;
        this.channel = channel;
        this.liveMode = true;
        this.catchup = false;
        this.useVlc = false;
        this.vlcSoft = false;
        this.freezeTicks = 0;
        final boolean reuseVlc = this.vlc != null && "vlc".equals(playerPref());
        ExoPlayer exoPlayer = this.player;
        if (exoPlayer != null) {
            exoPlayer.stop();
            this.player.clearMediaItems();
        }
        if (reuseVlc) {
            try {
                this.vlc.stop(false);
            } catch (Throwable ignored) {
                hideVlc();
            }
        } else {
            hideVlc();
        }
        PlayerView playerView = this.playerView;
        if (playerView != null) {
            playerView.setVisibility(0);
        }
        this.playerTitle.setText(Text.clean(channel.name));
        this.playerSub.setText("");
        buildQueue(channel.hlsUrl, channel.tsUrl);
        if (channel.extraLiveUrl != null && !channel.extraLiveUrl.isEmpty()) {
            this.queue.clear();
            addUrl(channel.extraLiveUrl);
            this.index = 0;
            this.extraLiveKeep = channel.extraLiveUrl;
            this.extraLiveHot = null;
            this.extraLiveTriedVlc = false;
            this.recoverTries = 0;
        }
        playCurrent();
        seedEpg();
        loadProgrammes();
        setHud(true);
        scheduleHide();
    }

    private void startExtraLivePrefetch() {
        Handler handler = UI;
        handler.removeCallbacks(this.extraLivePrefetch);
        handler.postDelayed(this.extraLivePrefetch, 12000L);
    }

    /* renamed from: app.streamy2.PlayerActivity$6, reason: invalid class name */
    class AnonymousClass6 implements Runnable {
        AnonymousClass6() {
        }

        @Override // java.lang.Runnable
        public void run() {
            final String str = PlayerActivity.this.extraLiveKeep;
            if (str == null || !foreground || userPaused || PlayerActivity.this.isFinishing()) {
                return;
            }
            final long request = playbackGeneration;
            PlayerActivity.IO.execute(new Runnable() { // from class: app.streamy2.PlayerActivity$6$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    PlayerActivity.AnonymousClass6.this.lambda$run$0(str, request);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void lambda$run$0(String str, final long request) {
            final String resolved = ExtraLiveSource.resolve(str);
            UI.post(() -> {
                if (!acceptPlayback(request) || !str.equals(extraLiveKeep) || userPaused) return;
                if (resolved != null && !resolved.isEmpty()) extraLiveHot = resolved;
                UI.postDelayed(this, 15000L);
            });
        }
    }

    private void swapExtraLive(boolean z) {
        int i;
        if (this.extraLiveKeep == null) {
            return;
        }
        String str = this.extraLiveHot;
        if (str != null && (i = this.index) >= 0 && i < this.queue.size()) {
            String str2 = this.queue.get(this.index);
            if (z || !str.equals(str2)) {
                this.queue.set(this.index, str);
                playCurrent();
                return;
            }
        }
        if (this.resolving) {
            return;
        }
        this.resolving = true;
        final String str3 = this.extraLiveKeep;
        final long request = playbackGeneration;
        IO.execute(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda16
            @Override // java.lang.Runnable
            public final void run() {
                PlayerActivity.this.lambda$swapExtraLive$23(str3, request);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$swapExtraLive$23(String str, final long request) {
        final String resolve = ExtraLiveSource.resolve(str);
        UI.post(new Runnable() { // from class: app.streamy2.PlayerActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                if (acceptPlayback(request)) PlayerActivity.this.lambda$swapExtraLive$22(resolve);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$swapExtraLive$22(String str) {
        this.resolving = false;
        if (str == null || str.isEmpty()) {
            if (this.errorView != null) {
                this.errorView.setVisibility(View.VISIBLE);
                this.errorView.setText("Live Extra: " + ExtraLiveSource.diagnosticSummary());
            }
            return;
        }
        this.extraLiveHot = str;
        int i = this.index;
        if (i >= 0 && i < this.queue.size()) {
            this.queue.set(this.index, str);
        }
        playCurrent();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void recoverStuck() {
        if (!foreground || userPaused || isFinishing()) return;
        if (this.extraLiveKeep != null) {
            if (this.resolving) return;
            if (this.recoverTries++ < 2) {
                this.extraLiveHot = null;
                ExtraLiveSource.invalidateSig();
                swapExtraLive(false);
            } else if (this.errorView != null) {
                this.errorView.setVisibility(View.VISIBLE);
                this.errorView.setText("Live Extra antwortet nicht: " + ExtraLiveSource.diagnosticSummary());
            }
            return;
        }
        if (this.liveMode && !this.catchup && this.index + 1 < this.queue.size()) {
            this.index++;
            this.freezeTicks = 0;
            if ("auto".equals(playerPref())) {
                hideVlc();
                this.useVlc = false;
            }
            this.lastFallbackReason = "Alternative Stream-URL wird versucht";
            playCurrent();
            return;
        }
        if (!this.useVlc && this.liveMode && !this.catchup
                && "auto".equals(playerPref()) && allowVlc()) {
            this.lastFallbackReason = "Watchdog: Live-Stream hängt";
            if (switchToVlc()) {
                this.freezeTicks = 0;
                return;
            }
        }
        int i = this.recoverTries;
        if (i < 2) {
            this.recoverTries = i + 1;
            playCurrent();
            return;
        }
        TextView textView = this.errorView;
        if (textView != null) {
            textView.setVisibility(0);
            this.errorView.setText("Sender hängt. Oben auf VLC oder Exo tippen.");
        }
    }

    private boolean switchToVlc() {
        int i = this.index;
        if (i < 0 || i >= this.queue.size()) {
            return false;
        }
        playWithVlc(this.queue.get(this.index));
        return true;
    }

    private boolean acceptPlayback(long request) {
        return foreground && !isFinishing() && !isDestroyed() && request == playbackGeneration;
    }

    private void invalidatePlayback() {
        playbackGeneration++;
        resolving = false;
        vlcStarting = false;
        UI.removeCallbacks(extraLivePrefetch);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        UI.removeCallbacks(tick);
        UI.removeCallbacks(watchdog);
        UI.post(tick);
        UI.post(watchdog);
        if (restartOnResume && !userPaused) {
            restartOnResume = false;
            playCurrent();
        } else if (resumePlayback && !userPaused) {
            if (useVlc && vlc != null) { bindVlcPlaybackListener(vlc); vlc.resume(); }
            else if (player != null) {
                if (playerView != null) playerView.setPlayer(player);
                player.setPlayWhenReady(true);
            }
        }
        resumePlayback = false;
        if (extraLiveKeep != null && !userPaused) startExtraLivePrefetch();
    }

    @Override
    protected void onPause() {
        resumePlayback = !userPaused;
        restartOnResume = resolving || vlcStarting;
        foreground = false;
        invalidatePlayback();
        UI.removeCallbacks(tick);
        UI.removeCallbacks(watchdog);
        UI.removeCallbacks(hideHud);
        if (vlc != null) vlc.pause();
        if (player != null) player.setPlayWhenReady(false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (vlc != null) vlc.pause();
        if (player != null) player.setPlayWhenReady(false);
        super.onStop();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        App.playerOpen = false;
        foreground = false;
        invalidatePlayback();
        stopPlayback();
        releasePlayer();
        super.onDestroy();
    }

    private static final class LiveRetry extends DefaultLoadErrorHandlingPolicy {
        private final int retries;
        private final long retryDelayMs;

        LiveRetry(boolean fastLive) {
            super(fastLive ? 3 : 6);
            this.retries = fastLive ? 3 : 6;
            this.retryDelayMs = fastLive ? 450L : 700L;
        }

        @Override // androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy, androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
        public int getMinimumLoadableRetryCount(int i) {
            return this.retries;
        }

        @Override // androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy, androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
        public long getRetryDelayMsFor(LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
            return this.retryDelayMs;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    class EpgAdapter extends RecyclerView.Adapter<EpgAdapter.VH> {
        private EpgAdapter() {
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public VH onCreateViewHolder(ViewGroup viewGroup, int i) {
            View inflate = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_epg, viewGroup, false);
            inflate.setFocusable(true);
            inflate.setClickable(true);
            return new VH(inflate);
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public void onBindViewHolder(VH vh, int i) {
            if (i < 0 || i >= PlayerActivity.this.programmes.size()) {
                return;
            }
            final EpgGuide.Listing listing = (EpgGuide.Listing) PlayerActivity.this.programmes.get(i);
            if (listing == null) {
                return;
            }
            long currentTimeMillis = System.currentTimeMillis();
            vh.time.setText(PlayerActivity.this.clockFmt.format(new Date(listing.start)));
            vh.title.setText(Text.clean(listing.title));
            boolean z = false;
            boolean z2 = listing.start <= currentTimeMillis && listing.stop > currentTimeMillis;
            final boolean z3 = listing.stop <= currentTimeMillis;
            if (PlayerActivity.this.channel != null && PlayerActivity.this.channel.archive && z3) {
                z = true;
            }
            if (z2) {
                vh.state.setText("LIVE  ·  " + PlayerActivity.this.clockFmt.format(new Date(listing.start)) + "–" + PlayerActivity.this.clockFmt.format(new Date(listing.stop)));
            } else if (z) {
                vh.state.setText("Zurückblicken  ·  " + PlayerActivity.this.clockFmt.format(new Date(listing.start)) + "–" + PlayerActivity.this.clockFmt.format(new Date(listing.stop)));
            } else {
                vh.state.setText(PlayerActivity.this.clockFmt.format(new Date(listing.start)) + "–" + PlayerActivity.this.clockFmt.format(new Date(listing.stop)));
            }
            vh.title.setAlpha((!z3 || z) ? 1.0f : 0.45f);
            final boolean z4 = z2;
            final boolean zArchive = z;
            vh.itemView.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.PlayerActivity$EpgAdapter$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    PlayerActivity.EpgAdapter.this.lambda$onBindViewHolder$0(z4, zArchive, listing, z3, view);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onBindViewHolder$0(boolean z, boolean z2, EpgGuide.Listing listing, boolean z3, View view) {
            if (z) {
                PlayerActivity.this.playLive();
            } else if (z2) {
                PlayerActivity.this.playCatchup(listing, listing.start);
            } else if (z3) {
                Toast.makeText(PlayerActivity.this, "Kein Archiv auf diesem Sender.", 0).show();
            }
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public int getItemCount() {
            return PlayerActivity.this.programmes.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView state;
            TextView time;
            TextView title;

            VH(View view) {
                super(view);
                this.time = (TextView) view.findViewById(R.id.epgTime);
                this.title = (TextView) view.findViewById(R.id.epgTitle);
                this.state = (TextView) view.findViewById(R.id.epgState);
            }
        }
    }
}
