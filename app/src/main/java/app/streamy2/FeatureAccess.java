package app.streamy2;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

final class FeatureAccess {
    private static final String PREFS = "streamy2_access";
    private static final String KEY_UNLOCKED = "extrasUnlocked";
    private static final String KEY_INSTALL_ID = "installId";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

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
        if (!pin.matches("\\d{8}")) {
            return new Result(false, "Bitte einen 8-stelligen Zahlen-PIN eingeben.");
        }

        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("pin", pin);
            requestJson.put("deviceId", installId(context));

            String endpoint = base.endsWith("/") ? base + "api/redeem" : base + "/api/redeem";
            Result first = redeemAt(endpoint, requestJson.toString());
            if (first.ok) {
                markUnlocked(context);
                return first;
            }

            // Manche Reverse-Proxies unterscheiden zwischen /api/redeem und /api/redeem/.
            // Nur bei einem reinen HTTP-Routingfehler einmal mit Slash wiederholen.
            if (first.message.startsWith("Serverfehler 404") || first.message.startsWith("Serverfehler 405")) {
                Result retry = redeemAt(endpoint + "/", requestJson.toString());
                if (retry.ok) markUnlocked(context);
                return retry;
            }
            return first;
        } catch (UnknownHostException e) {
            return new Result(false, "Freigabe-Server konnte nicht gefunden werden (DNS).");
        } catch (SocketTimeoutException e) {
            return new Result(false, "Freigabe-Server antwortet nicht rechtzeitig.");
        } catch (SSLException e) {
            return new Result(false, "Sichere Verbindung zum Freigabe-Server fehlgeschlagen (TLS).");
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName();
            return new Result(false, "Freigabe konnte nicht geprüft werden (" + detail + ").");
        }
    }

    private static Result redeemAt(String endpoint, String jsonBody) throws Exception {
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "Streamy2/" + BuildConfig.VERSION_NAME)
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            int code = response.code();
            String body = response.body() == null ? "" : response.body().string().trim();

            JSONObject json = null;
            if (!body.isEmpty() && body.startsWith("{")) {
                try {
                    json = new JSONObject(body);
                } catch (Exception ignored) {
                }
            }

            if (code >= 200 && code < 300 && json != null && json.optBoolean("ok", false)) {
                return new Result(true, "Freigabe erfolgreich");
            }

            if (json != null) {
                String message = json.optString("message", "");
                if (message.isEmpty()) message = json.optString("error", "");
                if (!message.isEmpty()) return new Result(false, message);
            }

            if (code >= 300 && code < 400) {
                return new Result(false, "Freigabe-Server hat unerwartet weitergeleitet (" + code + ").");
            }
            if (code >= 400) {
                return new Result(false, "Serverfehler " + code + " bei der Freigabe.");
            }
            return new Result(false, "Ungültige Antwort vom Freigabe-Server.");
        }
    }

    static void markUnlocked(Context context) {
        prefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply();
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
}
