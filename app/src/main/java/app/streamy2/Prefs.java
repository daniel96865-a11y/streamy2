package app.streamy2;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.media3.exoplayer.DefaultLoadControl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

public class Prefs {
    private static final String PREFS = "streamy2";
    private static final String KEY_PROFILES = "profilesV2";
    private static final String KEY_ACTIVE_PROFILE = "activeProfileV2";
    private static final String DEFAULT_PROFILE = "p1";
    private static final String KEY_PROFILE_SEQ = "profileSeqV2";
    private static final String KEY_PREVIOUS_PROFILE = "previousProfileV2";
    private static volatile String cacheProfileId = DEFAULT_PROFILE;
    private final SharedPreferences p;

    public Prefs(Context context) {
        this.p = context.getSharedPreferences(PREFS, 0);
        ensureProfiles();
        cacheProfileId = activeProfileId();
    }

    private static String pk(String id, String key) {
        return "profile." + id + "." + key;
    }

    private String activeKey(String key) {
        return pk(activeProfileId(), key);
    }

    private synchronized void ensureProfiles() {
        List<String> ids = readProfileIds();
        if (!ids.isEmpty()) {
            String active = this.p.getString(KEY_ACTIVE_PROFILE, "");
            if (active == null || !ids.contains(active)) {
                this.p.edit().putString(KEY_ACTIVE_PROFILE, ids.get(0)).apply();
            }
            return;
        }

        String id = DEFAULT_PROFILE;
        SharedPreferences.Editor e = this.p.edit();
        JSONArray a = new JSONArray();
        a.put(id);
        e.putString(KEY_PROFILES, a.toString());
        e.putString(KEY_ACTIVE_PROFILE, id);

        String legacyName = this.p.getString("name", "");
        String legacyUrl = this.p.getString("url", "");
        String legacyUser = this.p.getString("user", "");
        String legacyPass = this.p.getString("pass", "");
        String legacyFormat = this.p.getString("format", "hls");
        String legacyEpg = this.p.getString("epgUrl", "");
        int legacyInterval = this.p.getInt("epgInterval", 12);
        long legacyLast = this.p.getLong("epgLast", 0L);

        e.putString(pk(id, "name"), legacyName == null ? "" : legacyName);
        e.putString(pk(id, "url"), legacyUrl == null ? "" : legacyUrl);
        e.putString(pk(id, "user"), legacyUser == null ? "" : legacyUser);
        e.putString(pk(id, "pass"), legacyPass == null ? "" : legacyPass);
        e.putString(pk(id, "format"), legacyFormat == null ? "hls" : legacyFormat);
        e.putString(pk(id, "epgUrl"), legacyEpg == null ? "" : legacyEpg);
        e.putInt(pk(id, "epgInterval"), legacyInterval);
        e.putLong(pk(id, "epgLast"), legacyLast);

        String label = legacyName;
        if (label == null || label.trim().isEmpty()) label = legacyUser;
        if (label == null || label.trim().isEmpty()) label = "Playlist 1";
        e.putString(pk(id, "label"), label.trim());
        e.apply();
    }

    private List<String> readProfileIds() {
        ArrayList<String> result = new ArrayList<>();
        String raw = this.p.getString(KEY_PROFILES, "");
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                String id = a.optString(i, "").trim();
                if (!id.isEmpty() && !result.contains(id)) result.add(id);
            }
        } catch (Exception ignored) {
            Quiet.ignored("Prefs", ignored);
        }
        return result;
    }

    private void writeProfileIds(List<String> ids) {
        JSONArray a = new JSONArray();
        for (String id : ids) a.put(id);
        this.p.edit().putString(KEY_PROFILES, a.toString()).apply();
    }

    public List<String> profileIds() {
        ensureProfiles();
        return new ArrayList<>(readProfileIds());
    }

    public int profileCount() {
        return profileIds().size();
    }

    public String activeProfileId() {
        List<String> ids = readProfileIds();
        if (ids.isEmpty()) return DEFAULT_PROFILE;
        String active = this.p.getString(KEY_ACTIVE_PROFILE, ids.get(0));
        return active != null && ids.contains(active) ? active : ids.get(0);
    }

    public String profileDisplayName(String id) {
        if (id == null || id.isEmpty()) return "Playlist";
        String label = this.p.getString(pk(id, "label"), "");
        if (label != null && !label.trim().isEmpty()) return label.trim();
        String n = this.p.getString(pk(id, "name"), "");
        if (n != null && !n.trim().isEmpty()) return n.trim();
        String u = this.p.getString(pk(id, "user"), "");
        if (u != null && !u.trim().isEmpty()) return u.trim();
        List<String> ids = readProfileIds();
        int index = ids.indexOf(id);
        return "Playlist " + (index >= 0 ? index + 1 : 1);
    }

    public List<String> profileNames() {
        ArrayList<String> names = new ArrayList<>();
        for (String id : profileIds()) names.add(profileDisplayName(id));
        return names;
    }

    public String activeProfileName() {
        return profileDisplayName(activeProfileId());
    }

    public boolean setActiveProfile(String id) {
        if (id == null || !readProfileIds().contains(id)) return false;
        this.p.edit().putString(KEY_ACTIVE_PROFILE, id).apply();
        cacheProfileId = id;
        return true;
    }

    public String createProfile(String suggestedName) {
        List<String> ids = profileIds();
        String previous = activeProfileId();
        // Never reuse the id of a deleted playlist: its old catalog cache file could
        // otherwise show up under the new playlist.
        int n = Math.max(this.p.getInt(KEY_PROFILE_SEQ, 1), ids.size()) + 1;
        String id;
        while (ids.contains(id = "p" + n)) n++;
        this.p.edit().putInt(KEY_PROFILE_SEQ, n).putString(KEY_PREVIOUS_PROFILE, previous).apply();
        ids.add(id);
        writeProfileIds(ids);
        String label = suggestedName == null ? "" : suggestedName.trim();
        if (label.isEmpty()) label = "Playlist " + ids.size();
        this.p.edit()
                .putString(pk(id, "label"), label)
                .putString(pk(id, "format"), "hls")
                .putInt(pk(id, "epgInterval"), 12)
                .putString(KEY_ACTIVE_PROFILE, id)
                .apply();
        cacheProfileId = id;
        return id;
    }

    public void deleteActiveProfile() {
        List<String> ids = profileIds();
        String active = activeProfileId();
        if (ids.size() <= 1) {
            clearAccount();
            this.p.edit().putString(pk(active, "label"), "Playlist 1").apply();
            cacheProfileId = active;
            return;
        }
        ids.remove(active);
        SharedPreferences.Editor e = this.p.edit();
        String prefix = "profile." + active + ".";
        for (Map.Entry<String, ?> entry : this.p.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix)) e.remove(entry.getKey());
        }
        JSONArray a = new JSONArray();
        for (String id : ids) a.put(id);
        e.putString(KEY_PROFILES, a.toString());
        e.putString(KEY_ACTIVE_PROFILE, ids.get(0));
        e.apply();
        cacheProfileId = ids.get(0);
    }

    /** Deletes one playlist by id (active or not). The last playlist is only emptied. */
    public synchronized boolean deleteProfile(String id) {
        List<String> ids = profileIds();
        if (id == null || !ids.contains(id)) return false;
        String active = activeProfileId();
        if (id.equals(active)) {
            deleteActiveProfile();
            return true;
        }
        ids.remove(id);
        SharedPreferences.Editor e = this.p.edit();
        String prefix = "profile." + id + ".";
        for (Map.Entry<String, ?> entry : this.p.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix)) e.remove(entry.getKey());
        }
        JSONArray a = new JSONArray();
        for (String x : ids) a.put(x);
        e.putString(KEY_PROFILES, a.toString());
        e.apply();
        return true;
    }

    public boolean renameProfile(String id, String label) {
        if (id == null || !readProfileIds().contains(id) || label == null || label.trim().isEmpty()) return false;
        this.p.edit().putString(pk(id, "label"), label.trim()).apply();
        return true;
    }

    public boolean profileHasAccount(String id) {
        if (id == null) return false;
        String u = this.p.getString(pk(id, "user"), "");
        String url = this.p.getString(pk(id, "url"), "");
        return u != null && !u.trim().isEmpty() && url != null && !url.trim().isEmpty();
    }

    static String normalizeServer(String url) {
        if (url == null) return "";
        String s = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("http://")) s = s.substring(7);
        else if (s.startsWith("https://")) s = s.substring(8);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Existing playlist with the same server and user name, or null. */
    public String findProfile(String url, String user) {
        String server = normalizeServer(url);
        String u = user == null ? "" : user.trim();
        if (server.isEmpty() || u.isEmpty()) return null;
        for (String id : profileIds()) {
            if (server.equals(normalizeServer(this.p.getString(pk(id, "url"), "")))
                    && u.equals(this.p.getString(pk(id, "user"), "").trim())) {
                return id;
            }
        }
        return null;
    }

    /**
     * Saves a playlist from the settings form or from PIN pairing WITHOUT losing the
     * other saved playlists:
     * - active playlist still empty (e.g. just added) -> fill it
     * - same server + user as a saved playlist -> update that one and make it active
     * - same user + password on the active playlist (server moved) -> update active
     * - otherwise -> create a NEW playlist and make it active.
     * Returns the id of the playlist that was written.
     */
    public synchronized String saveAccountAsPlaylist(String name, String url, String user, String pass) {
        String active = activeProfileId();
        if (!profileHasAccount(active)) {
            saveAccount(name, url, user, pass);
            return active;
        }
        String match = findProfile(url, user);
        if (match == null) {
            String activeUser = this.p.getString(pk(active, "user"), "");
            String activePass = this.p.getString(pk(active, "pass"), "");
            if (user != null && user.trim().equals(activeUser == null ? "" : activeUser.trim())
                    && pass != null && pass.equals(activePass)) {
                match = active;
            }
        }
        if (match == null) {
            String label = name == null ? "" : name.trim();
            if (label.isEmpty()) label = user == null ? "" : user.trim();
            match = createProfile(label);
        } else if (!match.equals(active)) {
            setActiveProfile(match);
        }
        saveAccount(name, url, user, pass);
        return match;
    }

    /**
     * If the active playlist is still empty (user tapped "+ hinzufügen" and left
     * without saving) and other playlists exist, remove the empty one and go back to
     * the previously active playlist. Returns true if the active playlist changed.
     */
    public synchronized boolean discardEmptyActiveProfile() {
        List<String> ids = profileIds();
        String active = activeProfileId();
        if (ids.size() <= 1 || profileHasAccount(active)) return false;
        String previous = this.p.getString(KEY_PREVIOUS_PROFILE, "");
        String target = null;
        if (previous != null && !previous.equals(active) && ids.contains(previous) && profileHasAccount(previous)) {
            target = previous;
        } else {
            for (String id : ids) {
                if (!id.equals(active) && profileHasAccount(id)) {
                    target = id;
                    break;
                }
            }
        }
        if (target == null) return false;
        deleteProfile(active);
        setActiveProfile(target);
        return true;
    }

    public File catalogCacheFile(File cacheDir) {
        return profileCacheFile(cacheDir, activeProfileId());
    }

    public File catalogCacheFile(File cacheDir, String profileId) {
        return profileCacheFile(cacheDir, profileId);
    }

    static File catalogCacheFileForActive(File cacheDir) {
        return profileCacheFile(cacheDir, cacheProfileId);
    }

    private static File profileCacheFile(File cacheDir, String rawId) {
        String id = rawId == null ? DEFAULT_PROFILE : rawId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (id.isEmpty()) id = DEFAULT_PROFILE;
        File target = new File(cacheDir, "streamy2-live-cache-" + id + ".json");
        if (DEFAULT_PROFILE.equals(id) && !target.exists()) {
            File old = new File(cacheDir, "streamy2-live-cache.json");
            if (old.isFile() && !old.renameTo(target)) copyFile(old, target);
        }
        return target;
    }

    private static void copyFile(File source, File target) {
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
        } catch (Exception ignored) {
            Quiet.ignored("Prefs", ignored);
        }
    }

    public boolean hasXtream() {
        String string = this.p.getString(activeKey("user"), "");
        String string2 = this.p.getString(activeKey("url"), "");
        return string != null && !string.isEmpty() && string2 != null && !string2.isEmpty();
    }

    public String name() {
        return this.p.getString(activeKey("name"), "");
    }

    public String url() {
        return this.p.getString(activeKey("url"), "");
    }

    public String user() {
        return this.p.getString(activeKey("user"), "");
    }

    public String pass() {
        return this.p.getString(activeKey("pass"), "");
    }

    public String format() {
        return this.p.getString(activeKey("format"), "hls");
    }

    public String accent() {
        return this.p.getString("accent", "blue");
    }

    public String epgUrl() {
        return this.p.getString(activeKey("epgUrl"), "");
    }

    public int epgIntervalHours() {
        return this.p.getInt(activeKey("epgInterval"), 12);
    }

    public long epgLast() {
        return this.p.getLong(activeKey("epgLast"), 0L);
    }

    public void saveAccount(String str, String str2, String str3, String str4) {
        String profile = activeProfileId();
        String name = str == null ? "" : str;
        String url = str2 == null ? "" : str2;
        String user = str3 == null ? "" : str3;
        String pass = str4 == null ? "" : str4;
        String label = name.trim();
        if (label.isEmpty()) label = user.trim();
        if (label.isEmpty()) label = profileDisplayName(profile);
        this.p.edit()
                .putString(pk(profile, "name"), name)
                .putString(pk(profile, "url"), url)
                .putString(pk(profile, "user"), user)
                .putString(pk(profile, "pass"), pass)
                .putString(pk(profile, "label"), label)
                .apply();
    }

    public void clearAccount() {
        String profile = activeProfileId();
        this.p.edit()
                .remove(pk(profile, "name"))
                .remove(pk(profile, "url"))
                .remove(pk(profile, "user"))
                .remove(pk(profile, "pass"))
                .remove(pk(profile, "epgUrl"))
                .remove(pk(profile, "epgLast"))
                .apply();
    }

    public void setFormat(String str) {
        this.p.edit().putString(activeKey("format"), str).apply();
    }

    public void setAccent(String str) {
        this.p.edit().putString("accent", str == null ? "blue" : str).apply();
    }

    public void setEpgUrl(String str) {
        this.p.edit().putString(activeKey("epgUrl"), str == null ? "" : str.trim()).apply();
    }

    public void setEpgIntervalHours(int i) {
        this.p.edit().putInt(activeKey("epgInterval"), i).apply();
    }

    public void setEpgLast(long j) {
        this.p.edit().putLong(activeKey("epgLast"), j).apply();
    }

    public String resize() {
        return this.p.getString("resize", "zoom");
    }

    /**
     * Version 3.46 changes the mobile player's initial picture mode to "Anpassen".
     * Run the migration once so existing mobile installations get the new default,
     * while a later manual selection remains untouched.
     */
    public void ensureMobilePlayerDefaults346() {
        if (!"mobile".equals(BuildConfig.FLAVOR)
                || this.p.getBoolean("mobilePlayerDefaults346", false)) {
            return;
        }
        this.p.edit()
                .putString("resize", "fit")
                .putBoolean("mobilePlayerDefaults346", true)
                .apply();
    }

    public void setResize(String str) {
        String value = "stretch".equals(str) ? "stretch" : ("zoom".equals(str) ? "zoom" : "fit");
        this.p.edit().putString("resize", value).apply();
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

    public String playerLive() {
        if (!this.p.contains("playerLive")) return player();
        return normPlayer(this.p.getString("playerLive", "auto"));
    }

    public void setPlayerLive(String str) {
        this.p.edit().putString("playerLive", normPlayer(str)).apply();
    }

    public String playerExtraLive() {
        if (!this.p.contains("playerExtraLive")) {
            String legacy = player();
            return "vlc".equals(legacy) ? "auto" : legacy;
        }
        return normPlayer(this.p.getString("playerExtraLive", "auto"));
    }

    public void setPlayerExtraLive(String str) {
        this.p.edit().putString("playerExtraLive", normPlayer(str)).apply();
    }

    public String buffer() {
        String string = this.p.getString("buffer", "normal");
        return ("low".equals(string) || "high".equals(string) || "max".equals(string)) ? string : "normal";
    }

    public void setBuffer(String str) {
        if (!"low".equals(str) && !"high".equals(str) && !"max".equals(str)) str = "normal";
        this.p.edit().putString("buffer", str).apply();
    }

    /** Player buffer indicator: "hud" (with controls, default), "always" (small overlay) or "off". */
    public String bufferIndicator() {
        return BufferStats.normMode(this.p.getString("bufferIndicator", BufferStats.MODE_HUD));
    }

    public void setBufferIndicator(String mode) {
        this.p.edit().putString("bufferIndicator", BufferStats.normMode(mode)).apply();
    }

    public int bufferMs() {
        String buffer = buffer();
        switch (buffer) {
            case "low": return 1500;
            case "max": return 8000;
            case "high": return 4000;
            default: return 2500;
        }
    }

    public String audioMode() {
        String value = this.p.getString("audioMode", "auto");
        return ("surround".equals(value) || "stereo".equals(value)) ? value : "auto";
    }

    public void setAudioMode(String value) {
        if (!"surround".equals(value) && !"stereo".equals(value)) value = "auto";
        this.p.edit().putString("audioMode", value).apply();
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
        if (i != 1 && i != 2 && i != 4) i = 2;
        this.p.edit().putInt("posterCols", i).apply();
    }
}
