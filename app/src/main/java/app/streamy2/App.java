package app.streamy2;

import android.app.Application;
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
        LocalHls.start();
    }
}
