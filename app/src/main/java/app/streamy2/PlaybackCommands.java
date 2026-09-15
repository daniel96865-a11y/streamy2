package app.streamy2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Serial native operations; obsolete channel requests never start after a slow stop. */
final class PlaybackCommands {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "Streamy-VLC"));
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;
    long next() { return generation.incrementAndGet(); }
    long generation() { return generation.get(); }
    boolean current(long request) { return !closed && generation.get() == request; }
    synchronized void submit(long request, Runnable action) {
        if (!closed) worker.execute(() -> { if (current(request)) action.run(); });
    }
    synchronized void close(Runnable cleanup) {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        worker.execute(cleanup);
        worker.shutdown();
    }
}
