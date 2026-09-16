package app.streamy2;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** A visible, resumable download and user-confirmed installation. */
public final class UpdateActivity extends Activity {
    private static final int INSTALL_REQUEST = 501;
    private static final String MIME = "application/vnd.android.package-archive";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean COPYING = new AtomicBoolean();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::advance;
    private UpdateTransfer transfer;
    private TextView status;
    private ProgressBar progress;
    private Button action;
    private boolean resumed;
    private boolean installerOpened;

    static Intent intent(Context context, Updates.Info info) {
        return new Intent(context, UpdateActivity.class).putExtra("url", info.apkUrl)
                .putExtra("version", info.versionCode).putExtra("name", info.versionName);
    }

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        transfer = new UpdateTransfer(this);
        Updates.Info requested = new Updates.Info();
        requested.apkUrl = getIntent().getStringExtra("url");
        requested.versionName = getIntent().getStringExtra("name");
        requested.versionCode = getIntent().getIntExtra("version", 0);
        if (requested.apkUrl != null && !requested.apkUrl.isEmpty()) {
            if (!transfer.matches(requested)) removeSystemDownload();
            transfer.select(requested);
        }
        installerOpened = savedState != null && savedState.getBoolean("installerOpened");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText("Streamy 2 aktualisieren"); title.setTextSize(24);
        content.addView(title);
        status = new TextView(this); status.setTextSize(17); status.setPadding(0, padding, 0, padding);
        content.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        content.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        action = new Button(this); action.setVisibility(View.GONE);
        content.addView(action, new LinearLayout.LayoutParams(-1, -2));
        Button close = new Button(this); close.setText("Zurück"); close.setOnClickListener(v -> finish());
        content.addView(close, new LinearLayout.LayoutParams(-1, -2));
        setContentView(content);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        // After returning from the system installer: success = versionCode >= target.
        // RESULT_OK alone is unreliable. Same versionCode+URL ready cache is cleared.
        if (installerOpened) {
            Updates.Info info = transfer.info();
            if (info.versionCode > 0 && installedVersionCode() >= info.versionCode) {
                removeSystemDownload(); transfer.resetDownload(); finish();
                return;
            }
            forceFreshDownload("Lade Update erneut …");
            return;
        }
        advance();
    }

    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(poll); super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("installerOpened", installerOpened); super.onSaveInstanceState(state);
    }

    private DownloadManager downloads() {
        return (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
    }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    private void showAction(String label, View.OnClickListener click) {
        action.setText(label); action.setVisibility(View.VISIBLE); action.setOnClickListener(click);
        action.requestFocus();
    }

    private int installedVersionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Drop ready+download state and force a fresh DownloadManager fetch (cache-bust URL). */
    private void forceFreshDownload(String message) {
        removeSystemDownload();
        transfer.resetDownload();
        installerOpened = false;
        status.setText(message);
        action.setVisibility(View.GONE);
        beginDownload();
    }

    private void advance() {
        handler.removeCallbacks(poll);
        if (!resumed || isFinishing()) return;
        Updates.Info info = transfer.info();
        if (info.apkUrl.isEmpty()) { showError("Kein Download-Link vorhanden."); return; }
        int installed = installedVersionCode();
        if (installed >= info.versionCode && info.versionCode > 0) {
            removeSystemDownload(); transfer.resetDownload(); finish();
            return;
        }
        if (installerOpened) {
            status.setText("Die Installation wurde geöffnet. Falls du sie abgebrochen hast, kannst du sie erneut starten.");
            showAction("Installation erneut öffnen", v -> {
                installerOpened = false;
                String ready = transfer.readyPath();
                File file = ready.isEmpty() ? null : new File(ready);
                if (file == null || !file.isFile()) {
                    forceFreshDownload("Lade Update erneut …");
                    return;
                }
                try {
                    validateArchive(this, file, info.versionCode);
                    advance();
                } catch (Exception invalid) {
                    forceFreshDownload("Lade Update erneut …");
                }
            });
            return;
        }
        if (!allowed()) {
            status.setText("Erlaube Streamy 2, Apps zu installieren. Kehre danach hierher zurück; das Update wird fortgesetzt.");
            showAction("Installation erlauben", v -> openPermissionSettings());
            return;
        }
        action.setVisibility(View.GONE);
        if (!transfer.error().isEmpty()) { showError(transfer.error()); return; }
        if (COPYING.get()) {
            status.setText("Update wird heruntergeladen oder geprüft …");
            handler.postDelayed(poll, 500); return;
        }
        String ready = transfer.readyPath();
        if (!ready.isEmpty()) {
            File readyFile = new File(ready);
            if (!readyFile.isFile()) {
                transfer.clearReady();
            } else {
                try {
                    validateArchive(this, readyFile, info.versionCode);
                    openInstaller(readyFile);
                    return;
                } catch (Exception invalid) {
                    transfer.resetDownload();
                    showError(detail(invalid));
                    return;
                }
            }
        }
        long id = transfer.downloadId();
        if (id < 0) { beginDownload(); return; }
        try (Cursor cursor = downloads().query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                showError("Der Download ist nicht mehr vorhanden. Bitte erneut laden."); return;
            }
            int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long loaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (state == DownloadManager.STATUS_FAILED) {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                showError("Download fehlgeschlagen (" + reason + "). Bitte erneut versuchen."); return;
            }
            if (state == DownloadManager.STATUS_SUCCESSFUL) { prepareDownloaded(id); return; }
            int percent = total > 0 ? (int) Math.min(100, loaded * 100 / total) : 0;
            progress.setProgress(percent);
            status.setText(state == DownloadManager.STATUS_PAUSED ? "Download pausiert – warte auf Verbindung …"
                    : total > 0 ? "Update wird geladen: " + percent + " %"
                    : "Update wird geladen …");
            handler.postDelayed(poll, 700);
        } catch (Exception e) { showError("Download-Status konnte nicht gelesen werden: " + detail(e)); }
    }

    private void openPermissionSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception first) {
            try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
            catch (Exception second) { showError("Öffne in den Android-Einstellungen die Installation unbekannter Apps und erlaube Streamy 2."); }
        }
    }

    /** Cache-bust so DownloadManager does not reuse a prior response for the same URL. */
    static String downloadUrl(String apkUrl, int versionCode) {
        if (apkUrl == null || apkUrl.isEmpty()) return apkUrl;
        String sep = apkUrl.contains("?") ? "&" : "?";
        return apkUrl + sep + "v=" + versionCode + "&t=" + System.currentTimeMillis();
    }

    private void beginDownload() {
        status.setText("Update-Download startet …");
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null || downloads() == null) throw new IllegalStateException("Systemdownload nicht verfügbar");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Download-Ordner fehlt");
            File target = File.createTempFile("streamy-update-", ".apk", dir);
            if (!target.delete()) throw new IllegalStateException("Download-Ziel konnte nicht vorbereitet werden");
            String url = downloadUrl(transfer.info().apkUrl, transfer.info().versionCode);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Streamy 2 " + transfer.info().versionName)
                    .setDescription("Update wird geladen")
                    .setMimeType(MIME).setAllowedOverMetered(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(target));
            transfer.downloading(downloads().enqueue(request));
            handler.postDelayed(poll, 300);
        } catch (Exception unavailable) {
            // Some Fire TV devices do not provide Android's DownloadManager.
            prepareDownloaded(-1);
        }
    }

    private void prepareDownloaded(long id) {
        if (!COPYING.compareAndSet(false, true)) return;
        final Context context = getApplicationContext();
        final Updates.Info info = transfer.info();
        status.setText(id >= 0 ? "Download fertig – APK wird geprüft …" : "Update wird direkt geladen …");
        WORKER.execute(() -> {
            UpdateTransfer state = new UpdateTransfer(context);
            File ready = new File(context.getFilesDir(), "streamy-install.apk");
            File part = new File(ready.getPath() + ".part");
            try {
                if (id < 0) {
                    Updates.download(downloadUrl(info.apkUrl, info.versionCode), part);
                } else {
                    DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    Uri uri = manager.getUriForDownloadedFile(id);
                    if (uri == null) throw new IllegalStateException("Download-Datei fehlt");
                    try (InputStream in = context.getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(part)) {
                        if (in == null) throw new IllegalStateException("Download-Datei ist nicht lesbar");
                        byte[] buffer = new byte[65536]; long total = 0; int count;
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            if (total > 300L * 1024 * 1024) throw new IllegalStateException("APK zu groß");
                            out.write(buffer, 0, count);
                        }
                    }
                }
                validateArchive(context, part, info.versionCode);
                ready.delete();
                if (!part.renameTo(ready)) throw new IllegalStateException("APK konnte nicht gespeichert werden");
                if (state.matches(info)) state.ready(ready.getAbsolutePath());
            } catch (Exception e) {
                part.delete();
                ready.delete();
                if (state.matches(info)) {
                    state.clearReady();
                    state.failed(detail(e).contains("Signatur")
                            ? detail(e)
                            : "Update konnte nicht vorbereitet werden: " + detail(e));
                }
            } finally {
                part.delete(); COPYING.set(false);
                handler.post(() -> { if (resumed && !isDestroyed()) advance(); });
            }
        });
        handler.postDelayed(poll, 700);
    }

    static void validateArchive(Context context, File file, int expectedVersion) throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            if (zip.getEntry("AndroidManifest.xml") == null || zip.getEntry("classes.dex") == null)
                throw new IllegalStateException("Keine vollständige APK");
        }
        PackageManager pm = context.getPackageManager();
        // Metadata without signing flags — some PackageManager stubs only match flag 0.
        PackageInfo archive = pm.getPackageArchiveInfo(file.getPath(), 0);
        if (archive == null || !context.getPackageName().equals(archive.packageName)
                || archive.versionCode != expectedVersion)
            throw new IllegalStateException("Die APK passt nicht zum angeforderten Streamy-Update");
        int flags = signatureFlags();
        PackageInfo archiveSigned = pm.getPackageArchiveInfo(file.getPath(), flags);
        if (archiveSigned == null) archiveSigned = archive;
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        if (!signingCertsMatch(installed, archiveSigned)) {
            file.delete();
            throw new IllegalStateException(
                    "Die Signatur der Update-APK passt nicht zur installierten App (Cache/Signatur). "
                            + "Alte Datei wurde verworfen. Bitte erneut laden.");
        }
    }

    static int signatureFlags() {
        return Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    static boolean signingCertsMatch(PackageInfo installed, PackageInfo archive) {
        Set<String> installedCerts = certKeys(installed);
        Set<String> archiveCerts = certKeys(archive);
        // Robolectric / unsigned edge: nothing to compare.
        if (installedCerts.isEmpty() && archiveCerts.isEmpty()) return true;
        if (installedCerts.isEmpty() || archiveCerts.isEmpty()) return false;
        for (String key : archiveCerts) {
            if (installedCerts.contains(key)) return true;
        }
        return false;
    }

    private static Set<String> certKeys(PackageInfo info) {
        Set<String> keys = new HashSet<>();
        if (info == null) return keys;
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            SigningInfo signing = info.signingInfo;
            signatures = signing.hasMultipleSigners()
                    ? signing.getApkContentsSigners()
                    : signing.getSigningCertificateHistory();
        }
        if ((signatures == null || signatures.length == 0) && info.signatures != null) {
            signatures = info.signatures;
        }
        if (signatures == null) return keys;
        for (Signature signature : signatures) {
            if (signature != null) keys.add(Arrays.toString(signature.toByteArray()));
        }
        return keys;
    }

    static Intent installerIntent(Context context, File file, String action) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".file", file);
        Intent intent = new Intent(action).setDataAndType(uri, MIME);
        intent.setClipData(ClipData.newRawUri("Streamy Update", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        return intent;
    }

    private void openInstaller(File file) {
        try {
            // startActivity works even when package visibility filters queryIntentActivities.
            startActivityForResult(installerIntent(this, file, Intent.ACTION_INSTALL_PACKAGE), INSTALL_REQUEST);
            installerOpened = true;
        } catch (Exception first) {
            try {
                startActivityForResult(installerIntent(this, file, Intent.ACTION_VIEW), INSTALL_REQUEST);
                installerOpened = true;
            } catch (Exception second) { showError("Android-Installer konnte nicht geöffnet werden: " + detail(second)); }
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != INSTALL_REQUEST) return;
        // RESULT_OK is unreliable — success = installed versionCode >= target (checked in onResume).
        installerOpened = true;
    }

    private void showError(String message) {
        transfer.failed(message); status.setText(message);
        showAction("Erneut versuchen", v -> {
            removeSystemDownload(); transfer.resetDownload(); installerOpened = false; advance();
        });
    }

    private void removeSystemDownload() {
        long id = transfer.downloadId();
        if (id >= 0) try { downloads().remove(id); } catch (Exception ignored) { }
    }

    private static String detail(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
