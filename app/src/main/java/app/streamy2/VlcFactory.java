package app.streamy2;

import android.content.Context;
import android.view.ViewGroup;

/* loaded from: classes.dex */
final class VlcFactory {
    VlcFactory() {
    }

    static LiveEngine create(Context context, ViewGroup viewGroup) {
        VlcEngine vlcEngine = new VlcEngine(context, viewGroup);
        vlcEngine.prepare();
        return vlcEngine;
    }
}
