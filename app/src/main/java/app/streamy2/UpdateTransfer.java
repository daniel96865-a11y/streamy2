package app.streamy2;

import android.content.Context;
import android.content.SharedPreferences;

/** The requested update survives the permission screen and process recreation. */
final class UpdateTransfer {
    private final SharedPreferences state;

    UpdateTransfer(Context context) {
        state = context.getSharedPreferences("update_transfer", Context.MODE_PRIVATE);
    }

    boolean matches(Updates.Info info) {
        return info != null && info.versionCode == state.getInt("version", 0)
                && info.apkUrl.equals(state.getString("url", ""));
    }

    void select(Updates.Info info) {
        if (matches(info)) return;
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
    void resetDownload() { state.edit().remove("download").remove("ready").remove("error").commit(); }
}
