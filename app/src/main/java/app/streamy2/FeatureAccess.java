package app.streamy2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String KEY_PIN = "accessPin";
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

    interface Callback {
        void onResult(Result result);
    }

    private FeatureAccess() {
    }

    static boolean isUnlocked(Context context) {
        String pin = storedPin(context);
        return prefs(context).getBoolean(KEY_UNLOCKED, false) && pin.matches("\\d{8}");
    }

    static boolean isRestrictedUrl(String url) {
        if (url == null) return false;
        return ExtraMediaSource.isRestrictedUrl(url);
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

        String root = base.endsWith("/") ? base : base + "/";
        String origin = root.substring(0, root.length() - 1);
        String deviceId = installId(context);

        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("pin", pin);
            requestJson.put("deviceId", deviceId);

            String endpoint = root + "api/redeem";
            Result first = redeemPost(endpoint, requestJson.toString(), origin, root);
            if (first.ok) {
                markUnlocked(context);
                return first;
            }

            if (isProxyDenied(first)) {
                Result nativeResult = redeemNative(root + "api/redeem-native", pin, deviceId, origin, root);
                if (nativeResult.ok) markUnlocked(context);
                return nativeResult;
            }

            if (first.message.startsWith("Serverfehler 404")) {
                Result retry = redeemPost(endpoint + "/", requestJson.toString(), origin, root);
                if (retry.ok) {
                    markUnlocked(context);
                    return retry;
                }
                if (isProxyDenied(retry)) {
                    Result nativeResult = redeemNative(root + "api/redeem-native", pin, deviceId, origin, root);
                    if (nativeResult.ok) markUnlocked(context);
                    return nativeResult;
                }
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

    private static boolean isProxyDenied(Result result) {
        return result.message.startsWith("Serverfehler 403")
                || result.message.startsWith("Serverfehler 405");
    }

    private static Result redeemPost(String endpoint, String jsonBody, String origin, String referer) throws Exception {
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Origin", origin)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", "Streamy2/" + BuildConfig.VERSION_NAME)
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        return execute(request);
    }

    private static Result redeemNative(String endpoint, String pin, String deviceId,
                                       String origin, String referer) throws Exception {
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Origin", origin)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Streamy-Pin", pin)
                .header("X-Streamy-Device", deviceId)
                .header("User-Agent", "Streamy2/" + BuildConfig.VERSION_NAME)
                .get()
                .build();
        return execute(request);
    }

    private static Result execute(Request request) throws Exception {
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
                String message = json.optString("message", "Freigabe erfolgreich");
                return new Result(true, message);
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

    static void redeemInWebView(Activity activity, String rawPin, Callback callback) {
        redeemInWebViewInternal(activity, rawPin, false, callback);
    }

    static void validateStoredAccess(Activity activity, Callback callback) {
        if (activity == null || callback == null) return;
        String pin = storedPin(activity);
        if (!pin.matches("\\d{8}")) {
            clearUnlocked(activity);
            callback.onResult(new Result(false, "Freigabecode erneut eingeben."));
            return;
        }
        redeemInWebViewInternal(activity, pin, true, callback);
    }

    private static void redeemInWebViewInternal(Activity activity, String rawPin,
                                                boolean forceServerCheck, Callback callback) {
        if (activity == null || callback == null) return;
        if (!forceServerCheck && isUnlocked(activity)) {
            callback.onResult(new Result(true, "Bereits freigeschaltet"));
            return;
        }

        String pin = rawPin == null ? "" : rawPin.replaceAll("[^0-9]", "");
        if (!pin.matches("\\d{8}")) {
            callback.onResult(new Result(false, "Bitte einen 8-stelligen Zahlen-PIN eingeben."));
            return;
        }

        String base = BuildConfig.ACCESS_API_URL == null ? "" : BuildConfig.ACCESS_API_URL.trim();
        if (base.isEmpty()) {
            callback.onResult(new Result(false, "Freigabe momentan nicht verfügbar."));
            return;
        }
        if (!base.endsWith("/")) base += "/";

        final String redeemUrl = base + "#streamy-redeem?pin=" + Uri.encode(pin)
                + "&device=" + Uri.encode(installId(activity));
        final Handler handler = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean(false);

        try {
            final WebView webView = new WebView(activity);
            final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            webView.setAlpha(0.0f);
            webView.setFocusable(false);
            webView.setFocusableInTouchMode(false);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setUserAgentString(settings.getUserAgentString() + " Streamy2-Access/" + BuildConfig.VERSION_NAME);

            final class Finisher {
                void finish(Result result) {
                    if (!finished.compareAndSet(false, true)) return;
                    if (result.ok) {
                        markUnlocked(activity, pin);
                    } else if (forceServerCheck && isDefinitiveRevocation(result)) {
                        clearUnlocked(activity);
                    }
                    try {
                        webView.stopLoading();
                        decor.removeView(webView);
                        webView.destroy();
                    } catch (Throwable ignored) {
                    }
                    callback.onResult(result);
                }
            }
            final Finisher finisher = new Finisher();

            webView.setWebViewClient(new WebViewClient() {
                private boolean handle(Uri uri) {
                    if (uri == null) return false;
                    if ("streamy2".equalsIgnoreCase(uri.getScheme())
                            && "redeem".equalsIgnoreCase(uri.getHost())) {
                        boolean ok = "1".equals(uri.getQueryParameter("ok"));
                        String message = uri.getQueryParameter("message");
                        if (message == null || message.trim().isEmpty()) {
                            message = ok ? "Freigabe erfolgreich" : "Freigabe wurde abgelehnt.";
                        }
                        finisher.finish(new Result(ok, message));
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handle(request == null ? null : request.getUrl());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handle(url == null ? null : Uri.parse(url));
                }
            });

            decor.addView(webView, new ViewGroup.LayoutParams(1, 1));
            handler.postDelayed(() -> finisher.finish(
                    new Result(false, "Freigabe-Server antwortet nicht über die Browser-Prüfung.")), 25000L);
            webView.loadUrl(redeemUrl);
        } catch (Throwable t) {
            callback.onResult(new Result(false, "Browser-Prüfung konnte nicht gestartet werden."));
        }
    }

    static void markUnlocked(Context context) {
        String pin = storedPin(context);
        if (pin.matches("\\d{8}")) {
            markUnlocked(context, pin);
        }
    }

    private static void markUnlocked(Context context, String pin) {
        prefs(context).edit()
                .putBoolean(KEY_UNLOCKED, true)
                .putString(KEY_PIN, pin)
                .apply();
    }

    static void clearUnlocked(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_UNLOCKED, false)
                .remove(KEY_PIN)
                .apply();
    }

    private static String storedPin(Context context) {
        String value = prefs(context).getString(KEY_PIN, "");
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static boolean isDefinitiveRevocation(Result result) {
        if (result == null || result.ok) return false;
        String message = result.message == null ? "" : result.message.toLowerCase();
        // Keep an already valid local grant on transport, parser and
        // "already used" responses. Only an explicit revocation may
        // kick the device out of restricted tabs.
        return message.contains("freigabe wurde gesperrt")
                || message.contains("gerät wurde gesperrt")
                || message.contains("freigabe widerrufen")
                || message.contains("zugang widerrufen")
                || message.contains("revoked");
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
