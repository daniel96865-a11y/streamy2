package app.streamy2;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
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
                    // Treat &lt; 1.5 GiB total as weak TV-stick class
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
        if (guide == null) {
            guide = new EpgGuide();
        }
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cookieManager);
        } catch (Throwable unused) {
        }
        Vavoo.trustSsl();
        try {
            LocalHls.start();
        } catch (Throwable ignored) {
        }
        // Do not eagerly probe/load libVLC on low-RAM devices — defer until first VLC use.
        if (!isLowRam(this)) {
            try {
                VlcFactory.isAvailable();
            } catch (Throwable ignored) {
            }
        }
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

    private void trimMemory(int level) {
        try {
            if (guide != null) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
                        || level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                        || isLowRam()) {
                    guide.trim(true);
                } else {
                    guide.trim(false);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            BrowserController.trimForMemory(level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
                    || level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL);
        } catch (Throwable ignored) {
        }
        try {
            System.gc();
        } catch (Throwable ignored) {
        }
    }
}
