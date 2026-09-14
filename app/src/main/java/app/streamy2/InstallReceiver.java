package app.streamy2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/* loaded from: classes.dex */
public class InstallReceiver extends BroadcastReceiver {
    public static final String ACTION = "app.streamy2.INSTALL";

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                    return;
                } catch (Exception unused) {
                    Toast.makeText(context, "Installer konnte nicht geöffnet werden", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "Update installiert", Toast.LENGTH_SHORT).show();
            return;
        }
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            return;
        }
        String systemMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Toast.makeText(context, mapStatus(status, systemMsg), Toast.LENGTH_LONG).show();
    }

    static String mapStatus(int status, String systemMsg) {
        String lower = systemMsg == null ? "" : systemMsg.toLowerCase();
        boolean signature = lower.contains("signature")
                || lower.contains("signatures do not match")
                || lower.contains("update_incompatible");
        if (signature) {
            return "Die Signatur passt nicht zur installierten App. Bitte eine passende Update-APK verwenden."
                    + (systemMsg != null && !systemMsg.isEmpty() ? (" (" + systemMsg + ")") : "");
        }
        String base;
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                base = "Diese APK ist mit dem Gerät nicht kompatibel";
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                base = "Update blockiert (Sicherheitsrichtlinie)";
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                base = "Update-Konflikt mit vorhandener Installation";
                break;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                base = "Update-APK ungültig oder beschädigt";
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                base = "Nicht genug Speicherplatz für das Update";
                break;
            case PackageInstaller.STATUS_FAILURE:
            default:
                base = "Update fehlgeschlagen";
                break;
        }
        if (systemMsg != null && !systemMsg.isEmpty()) {
            return base + ": " + systemMsg;
        }
        return base;
    }
}
