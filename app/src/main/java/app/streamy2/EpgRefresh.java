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

    static long intervalMillis(int hours) {
        return (hours == 6 || hours == 24 ? hours : 12) * 3600000L;
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
        if (!BUSY.compareAndSet(false, true)) return false;
        Context app = context.getApplicationContext();
        EpgGuide guide = App.guide;
        if (guide == null) App.guide = guide = new EpgGuide();
        final EpgGuide target = guide;
        target.loading = true;
        IO.execute(() -> {
            String error = null;
            try { error = refresh(app, target, force); }
            catch (Exception e) { error = "EPG-Aktualisierung fehlgeschlagen"; }
            finally {
                target.error = error;
                target.loading = false;
                BUSY.set(false);
                final String result = error;
                if (completion != null) UI.post(() -> completion.done(result));
            }
        });
        return true;
    }

    private static String refresh(Context context, EpgGuide guide, boolean force) throws Exception {
        Prefs prefs = new Prefs(context);
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        String primary = prefs.epgUrl();
        if (primary.isEmpty() && prefs.hasXtream()) {
            primary = new XtreamApi(prefs.url(), prefs.user(), prefs.pass(), prefs.format()).xmltvUrl();
        }
        if (!primary.isEmpty()) sources.add(primary);
        for (String url : ExtraLiveSource.EPG_URLS) sources.add(url);
        String key = digest(primary);
        if (!key.equals(sourceKey)) { guide.clear(); hydratedAt = 0; sourceKey = key; }
        long now = System.currentTimeMillis();
        boolean hydrate = guide.programmeCount == 0 || due(hydratedAt, now, 30 * 60000L);
        List<String> failures = new ArrayList<>();
        boolean downloaded = false;
        int fallbackLoaded = 0;
        for (String url : sources) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            boolean fallback = !url.equals(primary);
            if (fallback && fallbackLoaded > 0 && App.isLowRam()) break;
            File cache = new File(context.getFilesDir(), "epg/" + digest(url) + ".xml");
            boolean fresh = cache.isFile() && !due(cache.lastModified(), now, intervalMillis(prefs.epgIntervalHours()));
            boolean loaded = false;
            if (hydrate && cache.isFile()) {
                try { guide.loadFileMerge(cache); loaded = true; }
                catch (Exception ignored) { fresh = false; }
            }
            if (force || !fresh) {
                Long lastFailure = failedAt.get(url);
                if (!force && lastFailure != null && !due(lastFailure, now, 5 * 60000L)) {
                    failures.add(fallback ? "Zusatzquelle" : "Playlist-EPG");
                    if (fallback && loaded) fallbackLoaded++;
                    continue;
                }
                try {
                    guide.loadUrlMerge(url, cache, true);
                    failedAt.remove(url);
                    downloaded = true;
                    loaded = true;
                } catch (Exception e) {
                    failedAt.put(url, now);
                    failures.add(fallback ? "Zusatzquelle" : "Playlist-EPG");
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
