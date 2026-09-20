package app.streamy2;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Bundle;
import app.streamy2.Models;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;

/* loaded from: classes.dex */
public class App extends Application {
    public static XtreamApi api;
    public static EpgGuide guide;
    public static List<Models.Channel> live;
    public static Models.Channel playing;
    /** True while PlayerActivity is in the foreground / alive. */
    public static volatile boolean playerOpen;

    private static volatile boolean lowRamCached;
    private static volatile boolean lowRamValue;
    private static volatile boolean lowRamProbed;

    /** True when device is low-RAM or totalMem looks weak (&lt; ~1.5 GiB). */
    public static boolean isLowRam(Context context) {
        if (lowRamProbed) {
            return lowRamValue;
        }
        boolean low = false;
        try {
            ActivityManager am = (ActivityManager) context.getApplicationContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                if (am.isLowRamDevice()) {
                    low = true;
                } else {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    // Treat < 1.5 GiB total as weak TV-stick class
                    if (mi.totalMem > 0 && mi.totalMem < 1536L * 1024L * 1024L) {
                        low = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        lowRamValue = low;
        lowRamCached = low;
        lowRamProbed = true;
        return low;
    }

    public static boolean isLowRam() {
        return lowRamProbed && lowRamValue;
    }

    public static String lowRamLabel(Context context) {
        return isLowRam(context) ? "Low-RAM-Modus: aktiv" : "Low-RAM-Modus: aus";
    }

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        // Migrate the former single Xtream account into the first playlist profile
        // and initialise the profile-aware cache before MainActivity reads it.
        new Prefs(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                PlaylistUiBinder.bind(activity);
            }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
        if (guide == null) {
            guide = new EpgGuide();
        }
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cookieManager);
        } catch (Throwable unused) {
        }
        EpgRefresh.schedule(this);
        try {
            LocalHls.start();
        } catch (Throwable ignored) {
        }
        // Never probe libVLC on Application.onCreate — cold start must stay light.
        // VlcFactory.isAvailable() / create() run on first player use only.
    }

    @Override // android.app.Application, android.content.ComponentCallbacks
    public void onLowMemory() {
        super.onLowMemory();
        trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
    }

    @Override // android.app.Application, android.content.ComponentCallbacks2
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            trimMemory(level);
        }
    }

    static boolean isAggressiveTrim(int level) {
        // TRIM_MEMORY_UI_HIDDEN (20) is not a stronger form of RUNNING_CRITICAL (15).
        // Android's constants are categories, so numeric >= RUNNING_CRITICAL would
        // wrongly classify every hidden/background UI as critical memory pressure.
        return level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE;
    }

    private void trimMemory(int level) {
        try {
            if (guide != null) {
                if (isAggressiveTrim(level) || isLowRam()) {
                    guide.trim(true);
                } else {
                    guide.trim(false);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            BrowserController.trimForMemory(isAggressiveTrim(level));
        } catch (Throwable ignored) {
        }
        try {
            System.gc();
        } catch (Throwable ignored) {
        }
    }
}
