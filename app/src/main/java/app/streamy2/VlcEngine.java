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
                    try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private boolean attached;
    private final Context ctx;
    private final ViewGroup host;
    private VLCVideoLayout layout;
    private LibVLC lib;
    private MediaPlayer player;
    private final MediaPlayer.EventListener eventListener = new MediaPlayer.EventListener() {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            if (event == null) {
                return;
            }
            if (event.type == MediaPlayer.Event.EncounteredError) {
                toastError("VLC EncounteredError");
            } else if (event.type == MediaPlayer.Event.Playing) {
                PlaybackListener l = VlcEngine.this.playbackListener;
                if (l != null) {
                    try { l.onPlaying(); } catch (Throwable ignored) {}
                }
            } else if (event.type == MediaPlayer.Event.Paused || event.type == MediaPlayer.Event.Stopped) {
                PlaybackListener l = VlcEngine.this.playbackListener;
                if (l != null) {
                    try { l.onPaused(); } catch (Throwable ignored) {}
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
            arrayList.add("--network-caching=2500");
            arrayList.add("--live-caching=2500");
            arrayList.add("--http-reconnect");
            arrayList.add("--drop-late-frames");
            arrayList.add("--skip-frames");
            arrayList.add("--avcodec-hw=any");
            arrayList.add("--clock-jitter=0");
            arrayList.add("--clock-synchro=0");
            if (!Tv.isTv(this.ctx)) {
                arrayList.add("--stereo-mode=1");
            }
            this.lib = new LibVLC(this.ctx, arrayList);
            this.player = new MediaPlayer(this.lib);
            try {
                this.player.setEventListener(eventListener);
            } catch (Throwable ignored) {
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
            ensureLayout();
            try {
                this.player.stop();
            } catch (Exception unused) {
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
                Media media = new Media(this.lib, Uri.parse(str));
                media.setHWDecoderEnabled(z, false);
                int i = Tv.isTv(this.ctx) ? 2800 : 2200;
                media.addOption(":network-caching=" + i);
                media.addOption(":live-caching=" + i);
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
                }
                if (this.attached) {
                    try {
                        this.player.detachViews();
                    } catch (Exception unused2) {
                    }
                    this.attached = false;
                }
                if (z) {
                    try {
                        this.player.setEventListener(null);
                    } catch (Exception unused4) {
                    }
                    try {
                        this.player.release();
                    } catch (Exception unused3) {
                    }
                    this.player = null;
                }
            }
        } catch (Throwable unused4) {
        }
        if (z && (libVLC = this.lib) != null) {
            try {
                libVLC.release();
            } catch (Exception unused5) {
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
        }
    }
}
