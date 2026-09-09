package app.streamy2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/* loaded from: classes.dex */
public class InstallReceiver extends BroadcastReceiver {
    public static final String ACTION = "app.streamy2.INSTALL";

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        int intExtra = intent.getIntExtra("android.content.pm.extra.STATUS", 1);
        if (intExtra == -1) {
            Intent intent2 = (Intent) intent.getParcelableExtra("android.intent.extra.INTENT");
            if (intent2 != null) {
                intent2.addFlags(268435456);
                try {
                    context.startActivity(intent2);
                    return;
                } catch (Exception unused) {
                    Toast.makeText(context, "Installer konnte nicht geöffnet werden", 1).show();
                    return;
                }
            }
            return;
        }
        if (intExtra == 0) {
            Toast.makeText(context, "Update installiert", 0).show();
        } else {
            if (intExtra == 3) {
                return;
            }
            String stringExtra = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
            Toast.makeText(context, "Update fehlgeschlagen" + (stringExtra != null ? ": " + stringExtra : ""), 1).show();
        }
    }
}
