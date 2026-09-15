package app.streamy2;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.function.Supplier;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

final class VlcEngine implements LiveEngine {
    interface PlaybackListener {
        void onPlaying();
        void onPaused();
        void onError();
        default void onEnded() { onPaused(); }
    }
    interface Events { void event(int type, long value); }
    interface Backend {
        void events(Events listener);
        void attach(VLCVideoLayout layout);
        void detach();
        void stop();
        void play(String url, boolean hardware, int cacheMs);
        void pause();
        void resume();
        void seek(long position);
        void release();
    }
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private final Context ctx;
    private final ViewGroup host;
    private final Supplier<Backend> factory;
    private final PlaybackCommands commands = new PlaybackCommands();
    private volatile Backend backend;
    private VLCVideoLayout layout;
    private boolean attached;
    private volatile boolean closed, playing, paused;
    private volatile long position, duration;
    private PlaybackListener playbackListener;

    VlcEngine(Context context, ViewGroup host) {
        this(context, host, () -> new NativeBackend(context.getApplicationContext()));
    }
    VlcEngine(Context context, ViewGroup host, Supplier<Backend> factory) {
        this.ctx = context.getApplicationContext();
        this.host = host;
        this.factory = factory;
    }
    void setPlaybackListener(PlaybackListener listener) { playbackListener = listener; }
    // VlcFactory prepares the native engine off the main thread before exposing it.
    void prepare() { if (backend == null && !closed) backend = factory.get(); }

    @Override public void play(String url, boolean hardware) {
        if (closed || url == null || url.isEmpty() || host == null || backend == null) return;
        final long request = commands.next();
        final PlaybackListener listener = playbackListener;
        final Backend nativePlayer = backend;
        playing = false; paused = false; position = 0; duration = 0;
        try {
            if (layout == null) {
                layout = new VLCVideoLayout(host.getContext());
                host.addView(layout, new ViewGroup.LayoutParams(-1, -1));
            }
            host.setVisibility(0);
            layout.setVisibility(0);
            if (!attached) { nativePlayer.attach(layout); attached = true; }
            final int cacheMs = new Prefs(ctx).vlcBufferMs(Tv.isTv(ctx));
            commands.submit(request, () -> {
                try {
                    nativePlayer.events(null);
                    nativePlayer.stop();
                    if (!commands.current(request)) return;
                    nativePlayer.events((type, value) -> UI.post(() -> {
                        if (!commands.current(request)) return;
                        PlaybackListener target = playbackListener;
                        if (type == MediaPlayer.Event.TimeChanged) position = Math.max(0, value);
                        else if (type == MediaPlayer.Event.LengthChanged) duration = Math.max(0, value);
                        else if (type == MediaPlayer.Event.Playing) {
                            playing = !paused;
                            if (paused) commands.submit(request, nativePlayer::pause);
                            else if (target != null) target.onPlaying();
                        } else if (type == MediaPlayer.Event.Paused || type == MediaPlayer.Event.Stopped) {
                            playing = false;
                            if (target != null) target.onPaused();
                        } else if (type == MediaPlayer.Event.EndReached) {
                            playing = false;
                            if (target != null) target.onEnded();
                        } else if (type == MediaPlayer.Event.EncounteredError) {
                            playing = false;
                            if (!paused && target != null) target.onError();
                        }
                    }));
                    nativePlayer.play(url, hardware, cacheMs);
                    if (paused) nativePlayer.pause();
                } catch (Throwable e) { fail(request, listener, e); }
            });
        } catch (Throwable e) { fail(request, listener, e); }
    }
    private void fail(long request, PlaybackListener listener, Throwable error) {
        UI.post(() -> {
            if (!commands.current(request)) return;
            playing = false;
            VlcFactory.lastError = "VLC-Wiedergabe fehlgeschlagen (" + error.getClass().getSimpleName() + ")";
            PlaybackListener target = playbackListener;
            if (target != null) target.onError();
        });
    }
    @Override public boolean isPlaying() { return playing && !paused && !closed; }
    @Override public long getPositionMs() { return position; }
    @Override public long getDurationMs() { return duration; }
    @Override public void pause() {
        paused = true; playing = false;
        Backend nativePlayer = backend;
        if (closed || nativePlayer == null) return;
        commands.submit(commands.generation(), () -> { try { nativePlayer.pause(); } catch (Throwable ignored) {} });
    }
    @Override public void resume() {
        paused = false;
        Backend nativePlayer = backend;
        if (closed || nativePlayer == null) return;
        commands.submit(commands.generation(), () -> { try { nativePlayer.resume(); } catch (Throwable ignored) {} });
    }
    @Override public void toggle() { if (isPlaying()) pause(); else resume(); }
    @Override public void seekToMs(long value) {
        Backend nativePlayer = backend;
        if (closed || nativePlayer == null) return;
        commands.submit(commands.generation(), () -> { try { nativePlayer.seek(Math.max(0, value)); } catch (Throwable ignored) {} });
    }
    @Override public void stop(boolean release) {
        if (closed) return;
        playing = false; paused = true; position = 0; duration = 0;
        final Backend nativePlayer = backend;
        if (!release) {
            long request = commands.next();
            commands.submit(request, () -> {
                try { if (nativePlayer != null) { nativePlayer.events(null); nativePlayer.stop(); } }
                catch (Throwable ignored) {}
            });
            return;
        }
        closed = true;
        playbackListener = null;
        // Stop first on worker, detach views on UI, then release native resources off UI.
        commands.close(() -> {
            try { if (nativePlayer != null) { nativePlayer.events(null); nativePlayer.stop(); } }
            catch (Throwable ignored) {}
            UI.post(() -> {
                if (nativePlayer != null && attached) {
                    try { nativePlayer.detach(); } catch (Throwable ignored) {}
                    attached = false;
                }
                if (layout != null) { host.removeView(layout); layout = null; }
                // A replacement engine may already use the host: never hide it from old cleanup.
                if (nativePlayer != null) java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try { nativePlayer.release(); } catch (Throwable ignored) {}
                });
                backend = null;
            });
        });
    }
    private static final class NativeBackend implements Backend {
        private final LibVLC lib;
        private final MediaPlayer player;
        NativeBackend(Context ctx) {
            ArrayList<String> options = new ArrayList<>();
            options.add("--aout=opensles"); options.add("--http-reconnect");
            options.add("--drop-late-frames"); options.add("--skip-frames"); options.add("--avcodec-hw=any");
            options.add("--clock-jitter=0"); options.add("--clock-synchro=0");
            if (!Tv.isTv(ctx)) options.add("--stereo-mode=1");
            lib = new LibVLC(ctx, options);
            player = new MediaPlayer(lib);
        }
        public void events(Events listener) {
            player.setEventListener(listener == null ? null : event -> {
                long value = event.type == MediaPlayer.Event.TimeChanged ? event.getTimeChanged()
                        : event.type == MediaPlayer.Event.LengthChanged ? event.getLengthChanged() : 0;
                listener.event(event.type, value);
            });
        }
        public void attach(VLCVideoLayout layout) { player.attachViews(layout, null, false, false); }
        public void detach() { player.detachViews(); }
        public void stop() { player.stop(); }
        public void pause() { if (player.isPlaying()) player.pause(); }
        public void resume() { player.play(); }
        public void seek(long position) { player.setTime(position); }
        public void release() { player.release(); lib.release(); }
        public void play(String url, boolean hardware, int cacheMs) {
            Media media = new Media(lib, Uri.parse(url));
            try {
                media.setHWDecoderEnabled(hardware, false);
                media.addOption(":network-caching=" + cacheMs);
                media.addOption(":live-caching=" + cacheMs);
                media.addOption(":http-reconnect");
                media.addOption(":clock-jitter=0"); media.addOption(":clock-synchro=0");
                if (url.contains("vavoo") || url.contains("sunshine") || url.contains("mediahubmx") || url.contains("ngolpdky") || url.contains("kool.to") || url.contains("127.0.0.1")) {
                    media.addOption(":http-user-agent=okhttp/4.11.0");
                } else if (url.contains("gxplayer") || url.contains("master.txt") || url.contains("/m3u8/")) {
                    media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
                    media.addOption(":http-referrer=https://watch.gxplayer.xyz/");
                }
                if (!hardware) media.addOption(":codec=avcodec,none");
                player.setMedia(media);
            } finally { media.release(); }
            player.setVolume(100); player.play();
        }
    }
}
