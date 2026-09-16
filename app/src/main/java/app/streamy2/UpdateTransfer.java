package app.streamy2;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;

/** The requested update survives the permission screen and process recreation. */
final class UpdateTransfer {
    private final SharedPreferences state;
    private final File installFile;

    UpdateTransfer(Context context) {
        state = context.getSharedPreferences("update_transfer", Context.MODE_PRIVATE);
        installFile = new File(context.getFilesDir(), "streamy-install.apk");
    }

    boolean matches(Updates.Info info) {
        return info != null && info.versionCode == state.getInt("version", 0)
                && info.apkUrl.equals(state.getString("url", ""));
    }

    void select(Updates.Info info) {
        if (matches(info)) {
            // Same URL/versionCode can still have a missing or wiped ready file.
            String path = readyPath();
            if (!path.isEmpty() && !new File(path).isFile()) {
                state.edit().remove("ready").remove("download").remove("error").commit();
            }
            return;
        }
        deleteReadyFile();
        state.edit().clear().putString("url", info.apkUrl)
                .putInt("version", info.versionCode).putString("name", info.versionName).commit();
    }

    Updates.Info info() {
        Updates.Info info = new Updates.Info();
        info.apkUrl = state.getString("url", "");
        info.versionName = state.getString("name", "");
        info.versionCode = state.getInt("version", 0);
        return info;
    }

    long downloadId() { return state.getLong("download", -1); }
    void downloading(long id) { state.edit().putLong("download", id).remove("ready").remove("error").commit(); }
    String readyPath() { return state.getString("ready", ""); }
    void ready(String path) { state.edit().putString("ready", path).remove("error").commit(); }
    String error() { return state.getString("error", ""); }
    void failed(String message) { state.edit().putString("error", message).commit(); }

    void resetDownload() {
        deleteReadyFile();
        state.edit().remove("download").remove("ready").remove("error").commit();
    }

    void clearReady() {
        deleteReadyFile();
        state.edit().remove("ready").remove("error").commit();
    }

    private void deleteReadyFile() {
        String path = readyPath();
        if (!path.isEmpty()) new File(path).delete();
        if (installFile.isFile()) installFile.delete();
        File part = new File(installFile.getPath() + ".part");
        if (part.isFile()) part.delete();
    }
}
