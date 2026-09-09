package app.streamy2;

import android.content.Context;
import android.view.ViewGroup;

/* loaded from: classes.dex */
final class VlcFactory {
    static volatile boolean probed;
    static volatile boolean available;
    static volatile String lastError = "";

    VlcFactory() {
    }

    static boolean isAvailable() {
        if (probed) {
            return available;
        }
        try {
            Class.forName("org.videolan.libvlc.LibVLC");
            available = true;
            lastError = "";
        } catch (Throwable t) {
            available = false;
            lastError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        }
        probed = true;
        return available;
    }

    static LiveEngine create(Context context, ViewGroup viewGroup) {
        try {
            VlcEngine vlcEngine = new VlcEngine(context, viewGroup);
            vlcEngine.prepare();
            available = true;
            probed = true;
            lastError = "";
            return vlcEngine;
        } catch (Throwable t) {
            available = false;
            probed = true;
            lastError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            return null;
        }
    }
}
