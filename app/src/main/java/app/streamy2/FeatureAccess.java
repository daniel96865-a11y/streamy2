package app.streamy2;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

final class FeatureAccess {
    private static final String PREFS = "streamy2_access";
    private static final String KEY_UNLOCKED = "extrasUnlocked";
    private static final String KEY_INSTALL_ID = "installId";

    static final class Result {
        final boolean ok;
        final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }
    }

    private FeatureAccess() {
    }

    static boolean isUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_UNLOCKED, false);
    }

    static boolean isRestrictedUrl(String url) {
        if (url == null) return false;
        return url.toLowerCase().contains("megakino");
    }

    static Result redeem(Context context, String rawPin) {
        if (isUnlocked(context)) {
            return new Result(true, "Bereits freigeschaltet");
        }
        String base = BuildConfig.ACCESS_API_URL == null ? "" : BuildConfig.ACCESS_API_URL.trim();
        if (base.isEmpty()) {
            return new Result(false, "Freigabe momentan nicht verfügbar.");
        }
        String pin = rawPin == null ? "" : rawPin.replaceAll("[^0-9]", "");
        if (!pin.matches("\\d{6,12}")) {
            return new Result(false, "Ungültiger Zahlen-PIN.");
        }
        HttpURLConnection connection = null;
        try {
            String endpoint = base.endsWith("/") ? base + "api/redeem" : base + "/api/redeem";
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Streamy2/" + BuildConfig.VERSION_NAME);

            JSONObject request = new JSONObject();
            request.put("pin", pin);
            request.put("deviceId", installId(context));
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();
            InputStream in = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = read(in);
            JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            if (code >= 200 && code < 300 && json.optBoolean("ok", false)) {
                prefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply();
                return new Result(true, "Freigabe erfolgreich");
            }
            String error = json.optString("message", "");
            if (error.isEmpty()) error = json.optString("error", "");
            if (error.isEmpty()) error = "PIN wurde nicht akzeptiert.";
            return new Result(false, error);
        } catch (Exception e) {
            return new Result(false, "Freigabe konnte nicht geprüft werden. Internetverbindung prüfen.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String installId(Context context) {
        SharedPreferences preferences = prefs(context);
        String value = preferences.getString(KEY_INSTALL_ID, "");
        if (value != null && !value.isEmpty()) return value;
        value = UUID.randomUUID().toString();
        preferences.edit().putString(KEY_INSTALL_ID, value).apply();
        return value;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 128 * 1024) break;
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
