package app.streamy2;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.media3.exoplayer.DefaultLoadControl;

/* loaded from: classes.dex */
public class Prefs {
    private final SharedPreferences p;

    public Prefs(Context context) {
        this.p = context.getSharedPreferences("streamy2", 0);
    }

    public boolean hasXtream() {
        String string = this.p.getString("user", "");
        String string2 = this.p.getString("url", "");
        return (string == null || string.isEmpty() || string2 == null || string2.isEmpty()) ? false : true;
    }

    public String name() {
        return this.p.getString("name", "");
    }

    public String url() {
        return this.p.getString("url", "");
    }

    public String user() {
        return this.p.getString("user", "");
    }

    public String pass() {
        return this.p.getString("pass", "");
    }

    public String format() {
        return this.p.getString("format", "hls");
    }

    public String accent() {
        return this.p.getString("accent", "blue");
    }

    public String epgUrl() {
        return this.p.getString("epgUrl", "");
    }

    public int epgIntervalHours() {
        return this.p.getInt("epgInterval", 12);
    }

    public long epgLast() {
        return this.p.getLong("epgLast", 0L);
    }

    public void saveAccount(String str, String str2, String str3, String str4) {
        SharedPreferences.Editor edit = this.p.edit();
        if (str == null) {
            str = "";
        }
        SharedPreferences.Editor putString = edit.putString("name", str);
        if (str2 == null) {
            str2 = "";
        }
        SharedPreferences.Editor putString2 = putString.putString("url", str2);
        if (str3 == null) {
            str3 = "";
        }
        SharedPreferences.Editor putString3 = putString2.putString("user", str3);
        if (str4 == null) {
            str4 = "";
        }
        putString3.putString("pass", str4).apply();
    }

    public void clearAccount() {
        this.p.edit().remove("name").remove("url").remove("user").remove("pass").apply();
    }

    public void setFormat(String str) {
        this.p.edit().putString("format", str).apply();
    }

    public void setAccent(String str) {
        SharedPreferences.Editor edit = this.p.edit();
        if (str == null) {
            str = "blue";
        }
        edit.putString("accent", str).apply();
    }

    public void setEpgUrl(String str) {
        this.p.edit().putString("epgUrl", str == null ? "" : str.trim()).apply();
    }

    public void setEpgIntervalHours(int i) {
        this.p.edit().putInt("epgInterval", i).apply();
    }

    public void setEpgLast(long j) {
        this.p.edit().putLong("epgLast", j).apply();
    }

    public String resize() {
        return this.p.getString("resize", "zoom");
    }

    public void setResize(String str) {
        this.p.edit().putString("resize", "zoom".equals(str) ? "zoom" : "fit").apply();
    }

    private static String normPlayer(String string) {
        return ("vlc".equals(string) || "exo".equals(string)) ? string : "auto";
    }

    public String player() {
        return normPlayer(this.p.getString("player", "auto"));
    }

    public void setPlayer(String str) {
        this.p.edit().putString("player", normPlayer(str)).apply();
    }

    /** Fixed player for Xtream / Live TV (non-Vavoo). Migrates from legacy player() when unset. */
    public String playerLive() {
        if (!this.p.contains("playerLive")) {
            return player();
        }
        return normPlayer(this.p.getString("playerLive", "auto"));
    }

    public void setPlayerLive(String str) {
        this.p.edit().putString("playerLive", normPlayer(str)).apply();
    }

    /** Fixed player for Vavoo. Migrates from legacy player() when unset.
     * Legacy "vlc" migrates to auto so users are not stuck without Exo fallback. */
    public String playerVavoo() {
        if (!this.p.contains("playerVavoo")) {
            String legacy = player();
            if ("vlc".equals(legacy)) {
                return "auto";
            }
            return legacy;
        }
        return normPlayer(this.p.getString("playerVavoo", "auto"));
    }

    public void setPlayerVavoo(String str) {
        this.p.edit().putString("playerVavoo", normPlayer(str)).apply();
    }

    public String buffer() {
        String string = this.p.getString("buffer", "normal");
        return ("low".equals(string) || "high".equals(string) || "max".equals(string)) ? string : "normal";
    }

    public void setBuffer(String str) {
        if (!"low".equals(str) && !"high".equals(str) && !"max".equals(str)) {
            str = "normal";
        }
        this.p.edit().putString("buffer", str).apply();
    }

    /** VLC now honors the same user buffer setting as Exo. */
    public int vlcBufferMs(boolean tv) {
        switch (buffer()) {
            case "low": return tv ? 1000 : 800;
            case "high": return tv ? 3500 : 3000;
            case "max": return 5000;
            default: return tv ? 2800 : 2200;
        }
    }

    public int bufferMs() {
        String buffer = buffer();
        buffer.hashCode();
        switch (buffer) {
            case "low":
                return DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
            case "max":
                return 15000;
            case "high":
                return 8000;
            default:
                return 5000;
        }
    }

    public int skippedUpdate() {
        return this.p.getInt("skipUp", 0);
    }

    public void setSkippedUpdate(int i) {
        this.p.edit().putInt("skipUp", i).apply();
    }

    public boolean filtersOpen() {
        return this.p.getBoolean("filters", false);
    }

    public void setFiltersOpen(boolean z) {
        this.p.edit().putBoolean("filters", z).apply();
    }

    public int posterColumns() {
        int c = this.p.getInt("posterCols", 2);
        return (c == 1 || c == 2 || c == 4) ? c : 2;
    }

    public void setPosterColumns(int i) {
        if (i != 1 && i != 2 && i != 4) {
            i = 2;
        }
        this.p.edit().putInt("posterCols", i).apply();
    }
}
