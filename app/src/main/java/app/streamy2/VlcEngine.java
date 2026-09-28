package app.streamy2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.net.Uri;
import android.view.ViewGroup;
import java.util.ArrayList;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

/* loaded from: classes.dex */
final class VlcEngine implements LiveEngine {
    interface PlaybackListener {
        void onPlaying();
        void onPaused();
        void onError();
        /** End of stream. For live TV this means the server closed or the URL expired. */
        void onEnded();
    }

    private PlaybackListener playbackListener;

    void setPlaybackListener(PlaybackListener playbackListener) {
        this.playbackListener = playbackListener;
    }

    private void toastError(final String msg) {
        try {
            VlcFactory.lastError = msg;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
                }
            });
        } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
    }

    private boolean attached;
    /** true = fill/zoom (crop), false = fit (letterbox). Default fill for phones/TV live. */
    private boolean zoomWanted = true;
    private volatile String scaleMode = "fit";
    private final Context ctx;
    private final ViewGroup host;
    private VLCVideoLayout layout;
    private LibVLC lib;
    private MediaPlayer player;
    // Buffer indicator statistics (written from VLC event thread / UI tick, read on UI thread).
    private volatile float statBufferingPct = -1f;
    private volatile boolean statPlayingSeen;
    private volatile int statRebuffers;
    private volatile long statInputBps;
    private volatile long statDemuxBps;
    private volatile int statLostPictures;
    private String statUrl;
    /** Live-Verzögerung for the next play() (0 = off / VOD / catch-up). */
    private volatile long liveDelayMs;

    void setLiveDelayMs(long ms) {
        this.liveDelayMs = Math.max(0L, ms);
    }

    /** Current video {width, height, sarNum, sarDen}, or null when unknown (for Bild-in-Bild). */
    int[] videoSize() {
        try {
            MediaPlayer mp = this.player;
            if (mp == null) return null;
            org.videolan.libvlc.interfaces.IMedia.VideoTrack t = mp.getCurrentVideoTrack();
            if (t == null || t.width <= 0 || t.height <= 0) return null;
            return new int[]{t.width, t.height, t.sarNum, t.sarDen};
        } catch (Throwable e) {
            Quiet.ignored("VlcEngine", e);
            return null;
        }
    }

    long liveDelayMs() {
        return this.liveDelayMs;
    }
    private final MediaPlayer.EventListener eventListener = new MediaPlayer.EventListener() {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            if (event == null) {
                return;
            }
            if (event.type == MediaPlayer.Event.EncounteredError) {
                toastError("VLC EncounteredError");
                PlaybackListener l = VlcEngine.this.playbackListener;
                if (l != null) {
                    try { l.onError(); } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
                }
            } else if (event.type == MediaPlayer.Event.Buffering) {
                try {
                    float pct = event.getBuffering();
                    if (pct < 100f && VlcEngine.this.statPlayingSeen && VlcEngine.this.statBufferingPct >= 100f) {
                        VlcEngine.this.statRebuffers++;
                    }
                    VlcEngine.this.statBufferingPct = pct;
                } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
            } else if (event.type == MediaPlayer.Event.ESAdded || event.type == MediaPlayer.Event.ESSelected) {
                VlcEngine.this.ensureAudioTrack();
            } else if (event.type == MediaPlayer.Event.Playing) {
                VlcEngine.this.ensureAudioTrack();
                VlcEngine.this.statPlayingSeen = true;
                PlaybackListener l = VlcEngine.this.playbackListener;
                if (l != null) {
                    try { l.onPlaying(); } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
                }
            } else if (event.type == MediaPlayer.Event.EndReached) {
                PlaybackListener l = VlcEngine.this.playbackListener;
                if (l != null) {
                    try { l.onEnded(); } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
                }
            } else if (event.type == MediaPlayer.Event.Paused || event.type == MediaPlayer.Event.Stopped) {
                PlaybackListener l = VlcEngine.this.playbackListener;
                if (l != null) {
                    try { l.onPaused(); } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
                }
            }
        }
    };

    VlcEngine(Context context, ViewGroup viewGroup) {
        this.ctx = context.getApplicationContext();
        this.host = viewGroup;
    }

    void prepare() {
        if (this.lib == null || this.player == null) {
            ArrayList arrayList = new ArrayList();
            arrayList.add("--aout=opensles");
            int cacheMs = new Prefs(this.ctx).bufferMs();
            arrayList.add("--network-caching=" + cacheMs);
            arrayList.add("--live-caching=" + cacheMs);
            arrayList.add("--http-reconnect");
            arrayList.add("--drop-late-frames");
            arrayList.add("--skip-frames");
            arrayList.add("--avcodec-hw=any");
            arrayList.add("--clock-jitter=0");
            arrayList.add("--clock-synchro=0");
            String audioMode = new Prefs(this.ctx).audioMode();
            if (!"surround".equals(audioMode)) {
                arrayList.add("--stereo-mode=1");
            }
            String[] langs = AudioPref.languageCodes(new Prefs(this.ctx).audioLanguage());
            if (langs.length > 0) arrayList.add("--audio-language=" + android.text.TextUtils.join(",", langs));
            this.lib = new LibVLC(this.ctx, arrayList);
            this.player = new MediaPlayer(this.lib);
            try {
                this.player.setEventListener(eventListener);
            } catch (Throwable ignored) {
                Quiet.ignored("VlcEngine", ignored);
            }
            VlcFactory.available = true;
            VlcFactory.probed = true;
            VlcFactory.lastError = "";
        }
    }

    void play(String str) {
        play(str, true);
    }

    @Override // app.streamy2.LiveEngine
    public void play(final String str, final boolean z) {
        if (str == null || str.isEmpty() || this.host == null) {
            return;
        }
        try {
            prepare();
            if (this.lib == null || this.player == null) {
                toastError("VLC nicht initialisiert");
                return;
            }
            if (!str.equals(this.statUrl)) {
                this.userAudioChoice = false;
                this.statUrl = str;
                this.statRebuffers = 0;
                this.statLostPictures = 0;
            }
            this.statBufferingPct = -1f;
            this.statPlayingSeen = false;
            this.statInputBps = 0L;
            this.statDemuxBps = 0L;
            ensureLayout();
            try {
                this.player.stop();
            } catch (Exception unused) {
                Quiet.ignored("VlcEngine", unused);
            }
            this.host.setVisibility(0);
            this.layout.setVisibility(0);
            this.layout.post(new Runnable() { // from class: app.streamy2.VlcEngine$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    VlcEngine.this.lambda$play$0(str, z);
                }
            });
        } catch (Throwable unused2) {
            toastError("VLC Startfehler: " + (unused2.getMessage() == null ? unused2.getClass().getSimpleName() : unused2.getMessage()));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$play$0(String str, boolean z) {
        VLCVideoLayout vLCVideoLayout;
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer != null && (vLCVideoLayout = this.layout) != null) {
                if (!this.attached) {
                    mediaPlayer.attachViews(vLCVideoLayout, null, false, false);
                    this.attached = true;
                }
                applyVideoScale();
                Media media = new Media(this.lib, Uri.parse(str));
                media.setHWDecoderEnabled(z, false);
                int i = new Prefs(this.ctx).bufferMs();
                media.addOption(":network-caching=" + i);
                media.addOption(":live-caching=" + i);
                // Live-Verzögerung (HLS/DASH via VLC's adaptive module): start further behind
                // the live edge and allow a larger buffer. Plain TS streams: no live window.
                int liveDelay = LiveDelay.vlcLiveDelayMs(this.liveDelayMs);
                if (liveDelay > 0) {
                    media.addOption(":adaptive-livedelay=" + liveDelay);
                    media.addOption(":adaptive-maxbuffer=" + LiveDelay.vlcMaxBufferMs(this.liveDelayMs));
                }
                media.addOption(":http-reconnect");
                media.addOption(":clock-jitter=0");
                media.addOption(":clock-synchro=0");
                if (str.contains("vavoo") || str.contains("sunshine") || str.contains("mediahubmx") || str.contains("ngolpdky") || str.contains("kool.to") || str.contains("127.0.0.1")) {
                    media.addOption(":http-user-agent=okhttp/4.11.0");
                } else if (str.contains("gxplayer") || str.contains("master.txt") || str.contains("/m3u8/")) {
                    media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
                    media.addOption(":http-referrer=https://watch.gxplayer.xyz/");
                }
                if (!z) {
                    media.addOption(":codec=avcodec,none");
                }
                this.player.setMedia(media);
                media.release();
                this.player.setVolume(100);
                this.player.play();
            }
        } catch (Throwable unused) {
            toastError("VLC Wiedergabe fehlgeschlagen: " + (unused.getMessage() == null ? unused.getClass().getSimpleName() : unused.getMessage()));
        }
    }


    // --- 3.85: always an audio track, preferred language ---
    private volatile boolean userAudioChoice;

    int[] audioTrackIds() {
        try {
            MediaPlayer.TrackDescription[] tracks = this.player == null ? null : this.player.getAudioTracks();
            if (tracks == null) return new int[0];
            int n = 0;
            for (MediaPlayer.TrackDescription t : tracks) if (t != null && t.id >= 0) n++;
            int[] ids = new int[n];
            int k = 0;
            for (MediaPlayer.TrackDescription t : tracks) if (t != null && t.id >= 0) ids[k++] = t.id;
            return ids;
        } catch (Throwable t) {
            Quiet.ignored("VlcEngine", t);
            return new int[0];
        }
    }

    String[] audioTrackNames() {
        try {
            MediaPlayer.TrackDescription[] tracks = this.player == null ? null : this.player.getAudioTracks();
            if (tracks == null) return new String[0];
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (MediaPlayer.TrackDescription t : tracks) {
                if (t == null || t.id < 0) continue;
                String name = t.name == null || t.name.trim().isEmpty() ? "Spur " + (names.size() + 1) : t.name.trim();
                names.add(name);
            }
            return names.toArray(new String[0]);
        } catch (Throwable t) {
            Quiet.ignored("VlcEngine", t);
            return new String[0];
        }
    }

    int currentAudioTrack() {
        try {
            return this.player == null ? -1 : this.player.getAudioTrack();
        } catch (Throwable t) {
            return -1;
        }
    }

    void selectAudioTrack(int id, boolean manual) {
        try {
            if (manual) this.userAudioChoice = true;
            if (this.player != null) this.player.setAudioTrack(id);
        } catch (Throwable t) {
            Quiet.ignored("VlcEngine", t);
        }
    }

    /** No audio track active (-1) or not the preferred language → pick one (see AudioPref). */
    void ensureAudioTrack() {
        try {
            MediaPlayer mp = this.player;
            if (mp == null) return;
            MediaPlayer.TrackDescription[] tracks = mp.getAudioTracks();
            if (tracks == null) return;
            int current = mp.getAudioTrack();
            java.util.ArrayList<AudioPref.Track> infos = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
            for (MediaPlayer.TrackDescription t : tracks) {
                if (t == null || t.id < 0) continue;
                infos.add(new AudioPref.Track(null, t.name, true, false, t.id == current));
                ids.add(t.id);
            }
            String pref = new Prefs(this.ctx).audioLanguage();
            if (!AudioPref.needsPick(infos, pref, this.userAudioChoice)) return;
            int pick = AudioPref.choose(infos, pref);
            if (pick >= 0 && ids.get(pick) != current) mp.setAudioTrack(ids.get(pick));
        } catch (Throwable t) {
            Quiet.ignored("VlcEngine", t);
        }
    }

    /** Apply Fit vs Füllen to the VLC surface (Exo uses PlayerView resizeMode). */
    void setZoom(boolean zoom) {
        setScaleMode(zoom ? "zoom" : "fit");
    }

    /** "fit" = Einpassen, "zoom" = Ausfüllen (crop), "stretch" = Strecken. */
    void setScaleMode(String mode) {
        this.scaleMode = "zoom".equals(mode) || "stretch".equals(mode) ? mode : "fit";
        this.zoomWanted = "zoom".equals(this.scaleMode);
        applyVideoScale();
    }

    /** Re-fit the surface after rotation / window size changes. */
    void refreshSurface() {
        applyVideoScale();
    }

    static MediaPlayer.ScaleType scaleTypeFor(String mode) {
        if ("zoom".equals(mode)) return MediaPlayer.ScaleType.SURFACE_FIT_SCREEN;
        if ("stretch".equals(mode)) return MediaPlayer.ScaleType.SURFACE_FILL;
        return MediaPlayer.ScaleType.SURFACE_BEST_FIT;
    }

    private void applyVideoScale() {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer == null) {
                return;
            }
            // Before 3.84 "Strecken" fell back to best-fit on VLC.
            mediaPlayer.setVideoScale(scaleTypeFor(this.scaleMode));
            try {
                mediaPlayer.updateVideoSurfaces();
            } catch (Throwable ignored) {
                Quiet.ignored("VlcEngine", ignored);
            }
        } catch (Throwable ignored) {
            Quiet.ignored("VlcEngine", ignored);
        }
    }

    private void ensureLayout() {
        if (this.layout != null || this.host == null) {
            return;
        }
        this.layout = new VLCVideoLayout(this.host.getContext());
        this.host.setVisibility(0);
        this.host.addView(this.layout, new ViewGroup.LayoutParams(-1, -1));
    }

    @Override // app.streamy2.LiveEngine
    public boolean isPlaying() {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer != null) {
                return mediaPlayer.isPlaying();
            }
            return false;
        } catch (Exception unused) {
            return false;
        }
    }

    @Override // app.streamy2.LiveEngine
    public void pause() {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
                return;
            }
            this.player.pause();
        } catch (Exception unused) {
            Quiet.ignored("VlcEngine", unused);
        }
    }

    @Override // app.streamy2.LiveEngine
    public void resume() {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer != null) {
                mediaPlayer.play();
            }
        } catch (Exception unused) {
            Quiet.ignored("VlcEngine", unused);
        }
    }

    @Override // app.streamy2.LiveEngine
    public void toggle() {
        if (isPlaying()) {
            pause();
        } else {
            resume();
        }
    }

    @Override // app.streamy2.LiveEngine
    public void stop(boolean z) {
        LibVLC libVLC;
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.stop();
                } catch (Exception unused) {
                    Quiet.ignored("VlcEngine", unused);
                }
                if (this.attached) {
                    try {
                        this.player.detachViews();
                    } catch (Exception unused2) {
                        Quiet.ignored("VlcEngine", unused2);
                    }
                    this.attached = false;
                }
                if (z) {
                    try {
                        this.player.setEventListener(null);
                    } catch (Exception unused4) {
                        Quiet.ignored("VlcEngine", unused4);
                    }
                    try {
                        this.player.release();
                    } catch (Exception unused3) {
                        Quiet.ignored("VlcEngine", unused3);
                    }
                    this.player = null;
                }
            }
        } catch (Throwable unused4) {
            Quiet.ignored("VlcEngine", unused4);
        }
        if (z && (libVLC = this.lib) != null) {
            try {
                libVLC.release();
            } catch (Exception unused5) {
                Quiet.ignored("VlcEngine", unused5);
            }
            this.lib = null;
        }
        VLCVideoLayout vLCVideoLayout = this.layout;
        if (vLCVideoLayout != null) {
            vLCVideoLayout.setVisibility(8);
        }
        ViewGroup viewGroup = this.host;
        if (viewGroup != null) {
            viewGroup.setVisibility(8);
        }
    }

    @Override
    public long getPositionMs() {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer == null) {
                return 0L;
            }
            long t = mediaPlayer.getTime();
            return t > 0 ? t : 0L;
        } catch (Throwable unused) {
            return 0L;
        }
    }

    @Override
    public long getDurationMs() {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer == null) {
                return 0L;
            }
            long len = mediaPlayer.getLength();
            return len > 0 ? len : 0L;
        } catch (Throwable unused) {
            return 0L;
        }
    }

    @Override
    public void seekToMs(long j) {
        try {
            MediaPlayer mediaPlayer = this.player;
            if (mediaPlayer == null) {
                return;
            }
            mediaPlayer.setTime(Math.max(0L, j));
        } catch (Throwable unused) {
            Quiet.ignored("VlcEngine", unused);
        }
    }

    @Override // app.streamy2.LiveEngine
    public float bufferingPercent() { return this.statBufferingPct; }

    @Override // app.streamy2.LiveEngine
    public int rebufferCount() { return this.statRebuffers; }

    @Override // app.streamy2.LiveEngine
    public long inputBitrateBps() { return this.statInputBps; }

    @Override // app.streamy2.LiveEngine
    public long demuxBitrateBps() { return this.statDemuxBps; }

    @Override // app.streamy2.LiveEngine
    public int lostPictures() { return this.statLostPictures; }

    @Override // app.streamy2.LiveEngine
    public void refreshStats() {
        MediaPlayer mp = this.player;
        if (mp == null) return;
        org.videolan.libvlc.interfaces.IMedia media = null;
        try {
            media = mp.getMedia();
            if (media == null) return;
            org.videolan.libvlc.interfaces.IMedia.Stats st = media.getStats();
            if (st != null) {
                this.statInputBps = BufferStats.smooth(this.statInputBps, BufferStats.vlcBitrateToBps(st.inputBitrate));
                this.statDemuxBps = BufferStats.smooth(this.statDemuxBps, BufferStats.vlcBitrateToBps(st.demuxBitrate));
                this.statLostPictures = Math.max(0, st.lostPictures);
            }
        } catch (Throwable ignored) {
            Quiet.ignored("VlcEngine", ignored);
        } finally {
            if (media != null) {
                try { media.release(); } catch (Throwable ignored) { Quiet.ignored("VlcEngine", ignored); }
            }
        }
    }
}
