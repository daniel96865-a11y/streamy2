package app.streamy2;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One refresh policy for settings, channel lists, player and background jobs. */
final class EpgRefresh {
    private static final int JOB_ID = 32901;
    private static final java.util.concurrent.ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static long hydratedAt;
    private static String sourceKey = "";
    private static final java.util.Map<String, Long> failedAt = new java.util.HashMap<>();

    interface Completion { void done(String error); }

    /**
     * 3.93: a request while another refresh runs (background job, Live Extra, tab switch) used
     * to be dropped silently, so its caller never learned that the EPG arrived and the visible
     * rows were not rebound. Such callers now wait for the running refresh instead.
     */
    private static final List<Completion> WAITING = new ArrayList<>();
    /** Whether a request was folded into the running refresh (App.live may have changed). */
    private static boolean joined;

    /** Test hook: number of callers waiting for the running refresh. */
    static int waitingCount() { synchronized (WAITING) { return WAITING.size(); } }

    static long intervalMillis(int hours) {
        return (hours == 6 || hours == 24 ? hours : 12) * 3600000L;
    }

    /**
     * Re-parsing every cached XMLTV file used to happen every 30 minutes, also while a
     * stream was playing (the player polls EpgRefresh every minute). On weak TV sticks the
     * CPU/GC spike drained the small live buffer → buffering after ~30 min. While the
     * player is open the in-memory window (≥4 h ahead) is still valid, so re-hydrate rarely.
     */
    static long hydrateIntervalMillis(boolean playerOpen) {
        return playerOpen ? 3L * 3600000L : 30L * 60000L;
    }

    static boolean due(long last, long now, long interval) {
        return last <= 0 || now < last || now - last >= interval;
    }

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        long interval = intervalMillis(new Prefs(context).epgIntervalHours());
        JobInfo old = scheduler.getPendingJob(JOB_ID);
        if (old != null && old.getIntervalMillis() == interval) return;
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, EpgJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(interval).setPersisted(true).build());
    }

    static boolean request(Context context, boolean force, Completion completion) {
        if (!BUSY.compareAndSet(false, true)) {
            synchronized (WAITING) {
                if (BUSY.get()) {
                    joined = true;
                    if (completion != null) WAITING.add(completion);
                    return false;
                }
            }
            // The running refresh just finished: start a new one.
            return request(context, force, completion);
        }
        Context app = context.getApplicationContext();
        EpgGuide guide = App.guide;
        if (guide == null) App.guide = guide = new EpgGuide();
        final EpgGuide target = guide;
        target.sourceMode = EpgSources.normalize(new Prefs(app).epgSource());
        target.loading = true;
        IO.execute(() -> {
            // Parsing multi-MB XMLTV must never compete with video decoding / segment loads.
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); }
            catch (Throwable ignored) { Quiet.ignored("EpgRefresh", ignored); }
            String error = null;
            try { error = refresh(app, target, force); }
            catch (Exception e) { error = "EPG-Aktualisierung fehlgeschlagen"; }
            finally {
                final List<Completion> waiting = new ArrayList<>();
                boolean rejoin;
                synchronized (WAITING) {
                    rejoin = joined;
                    joined = false;
                }
                if (rejoin) {
                    // Channels may have been replaced/merged while the refresh ran.
                    try { target.apply(App.live); }
                    catch (Throwable ignored) { Quiet.ignored("EpgRefresh", ignored); }
                }
                synchronized (WAITING) {
                    target.error = error;
                    target.loading = false;
                    waiting.addAll(WAITING);
                    WAITING.clear();
                    joined = false;
                    BUSY.set(false);
                }
                final String result = error;
                UI.post(() -> {
                    if (completion != null) completion.done(result);
                    for (Completion c : waiting) {
                        try { c.done(result); }
                        catch (Throwable t) { Quiet.ignored("EpgRefresh", t); }
                    }
                });
            }
        });
        return true;
    }

    private static String refresh(Context context, EpgGuide guide, boolean force) throws Exception {
        Prefs prefs = new Prefs(context);
        String primary = prefs.epgUrl();
        if (primary.isEmpty() && prefs.hasXtream()) {
            primary = new XtreamApi(prefs.url(), prefs.user(), prefs.pass(), prefs.format()).xmltvUrl();
        }
        String mode = EpgSources.normalize(prefs.epgSource());
        List<EpgSources.Source> plan = EpgSources.plan(mode, primary, EpgSources.hasBuiltin(App.live));
        String key = digest(mode + "|" + primary);
        if (!key.equals(sourceKey)) { guide.clear(); hydratedAt = 0; sourceKey = key; }
        guide.sourceMode = mode;
        long now = System.currentTimeMillis();
        boolean hydrate = guide.programmeCount == 0 || due(hydratedAt, now, hydrateIntervalMillis(App.playerOpen));
        List<String> failures = new ArrayList<>();
        boolean downloaded = false;
        int fallbackLoaded = 0;
        for (EpgSources.Source source : plan) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            String url = source.url;
            boolean fallback = source.web;
            if (fallback && fallbackLoaded > 0 && App.isLowRam()) break;
            File cache = new File(context.getFilesDir(), "epg/" + digest(url) + ".xml");
            boolean fresh = cache.isFile() && !due(cache.lastModified(), now, intervalMillis(prefs.epgIntervalHours()));
            boolean loaded = false;
            if (hydrate && cache.isFile()) {
                try { guide.loadFileMerge(cache, source.web); loaded = true; }
                catch (Exception ignored) { fresh = false; }
            }
            if (force || !fresh) {
                Long lastFailure = failedAt.get(url);
                if (!force && lastFailure != null && !due(lastFailure, now, 5 * 60000L)) {
                    failures.add(fallback ? "EPG aus dem Netz" : "Playlist-EPG");
                    if (fallback && loaded) fallbackLoaded++;
                    continue;
                }
                try {
                    guide.loadUrlMerge(url, cache, true, source.web);
                    failedAt.remove(url);
                    downloaded = true;
                    loaded = true;
                } catch (Exception e) {
                    failedAt.put(url, now);
                    failures.add(fallback ? "EPG aus dem Netz" : "Playlist-EPG");
                }
            } else if (!hydrate) loaded = true;
            if (fallback && loaded) fallbackLoaded++;
        }
        hydratedAt = now;
        guide.apply(App.live);
        if (downloaded) prefs.setEpgLast(System.currentTimeMillis());
        return failures.isEmpty() ? null : "Nicht erreichbar: " + android.text.TextUtils.join(", ", new LinkedHashSet<>(failures)) + ". Gespeicherte Daten werden weiter genutzt.";
    }

    private static String digest(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : hash) result.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return result.toString();
    }
}
