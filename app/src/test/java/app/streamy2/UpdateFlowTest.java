package app.streamy2;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import app.streamy2.BuildConfig;
import android.content.pm.Signature;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class)
public class UpdateFlowTest {
    private Context context;
    private Updates.Info info;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        // Robolectric gives each test a new data directory while AndroidX caches
        // paths statically. Attach the real provider as Android does per process.
        android.content.pm.ProviderInfo providerInfo = context.getPackageManager().resolveContentProvider(
                context.getPackageName() + ".file", android.content.pm.PackageManager.GET_META_DATA);
        new androidx.core.content.FileProvider().attachInfo(context, providerInfo);
        context.getSharedPreferences("update_transfer", 0).edit().clear().commit();
        info = new Updates.Info();
        info.versionCode = BuildConfig.VERSION_CODE + 1;
        info.versionName = "99.0";
        info.apkUrl = "https://example.org/streamy-update.apk";
        shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(false);
    }

    @Test public void permissionScreenRecreationKeepsRequestedUpdateAndThenStartsDownload() {
        Intent launch = UpdateActivity.intent(context, info);
        ActivityController<UpdateActivity> first = Robolectric.buildActivity(UpdateActivity.class, launch).setup();
        assertEquals("Installation erlauben", button(first.get()).getText().toString());
        button(first.get()).performClick();
        Intent settings = shadowOf(first.get()).getNextStartedActivity();
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settings.getAction());
        assertEquals("package:" + context.getPackageName(), settings.getData().toString());
        Bundle saved = new Bundle();
        first.pause().saveInstanceState(saved).stop().destroy();
        shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
        ActivityController<UpdateActivity> restored = Robolectric.buildActivity(UpdateActivity.class, new Intent(context, UpdateActivity.class))
                .create(saved).start().resume().visible();
        UpdateTransfer state = new UpdateTransfer(context);
        assertEquals(info.apkUrl, state.info().apkUrl);
        assertTrue(state.downloadId() >= 0);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        assertNotNull(shadowOf(manager).getRequest(state.downloadId()));
        restored.pause().stop().destroy();
    }

    @Test public void recreationObservesExistingDownloadInsteadOfEnqueuingAnother() {
        shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
        ActivityController<UpdateActivity> first = Robolectric.buildActivity(UpdateActivity.class, UpdateActivity.intent(context, info)).setup();
        long id = new UpdateTransfer(context).downloadId();
        assertTrue(id >= 0);
        first.pause().stop().destroy();
        ActivityController<UpdateActivity> next = Robolectric.buildActivity(UpdateActivity.class, new Intent(context, UpdateActivity.class)).setup();
        assertEquals(id, new UpdateTransfer(context).downloadId());
        next.pause().stop().destroy();
    }

    @Test public void differentUpdateCannotReuseOldCompletedApk() {
        UpdateTransfer first = new UpdateTransfer(context); first.select(info);
        first.downloading(17); first.ready("/old/update.apk");
        Updates.Info later = new Updates.Info(); later.apkUrl = "https://example.org/new.apk";
        later.versionCode = BuildConfig.VERSION_CODE + 2; later.versionName = "99.1";
        new UpdateTransfer(context).select(later);
        UpdateTransfer restored = new UpdateTransfer(context);
        assertEquals(-1, restored.downloadId()); assertEquals("", restored.readyPath());
        assertEquals(later.apkUrl, restored.info().apkUrl);
    }

    @Test public void completedDownloadIsRecoveredAndOpensInstallerWithoutCompletionBroadcast() throws Exception {
        shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long id = manager.enqueue(new DownloadManager.Request(android.net.Uri.parse(info.apkUrl)));
        shadowOf(shadowOf(manager).getRequest(id)).setStatus(DownloadManager.STATUS_SUCCESSFUL);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"AndroidManifest.xml", "classes.dex"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(1); zip.closeEntry();
            }
        }
        android.net.Uri downloadUri = manager.getUriForDownloadedFile(id);
        assertNotNull(downloadUri);
        shadowOf(context.getContentResolver()).registerInputStream(downloadUri, new java.io.ByteArrayInputStream(bytes.toByteArray()));
        File part = new File(context.getFilesDir(), "streamy-install.apk.part");
        File ready = new File(context.getFilesDir(), "streamy-install.apk");
        PackageInfo archive = new PackageInfo(); archive.packageName = context.getPackageName(); archive.versionCode = info.versionCode;
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(part.getPath(), archive);
        // advance() re-validates the ready file after rename
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(ready.getPath(), archive);
        UpdateTransfer state = new UpdateTransfer(context); state.select(info); state.downloading(id);
        ActivityController<UpdateActivity> restored = Robolectric.buildActivity(UpdateActivity.class,
                new Intent(context, UpdateActivity.class)).setup();
        Intent installer = null;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (installer == null && System.nanoTime() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle();
            installer = shadowOf(restored.get()).getNextStartedActivity();
            if (installer == null) Thread.sleep(10);
        }
        assertEquals("", state.error());
        assertNotNull("Completed APK must open the system installer", installer);
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, installer.getAction());
        assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(new File(state.readyPath()).toPath()));
        restored.pause().stop().destroy();
    }

    @Test public void failedSystemDownloadShowsRetryAndNeverInstallsPreviousApk() {
        shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long id = manager.enqueue(new DownloadManager.Request(android.net.Uri.parse(info.apkUrl)));
        shadowOf(shadowOf(manager).getRequest(id)).setStatus(DownloadManager.STATUS_FAILED);
        UpdateTransfer state = new UpdateTransfer(context); state.select(info); state.downloading(id);
        ActivityController<UpdateActivity> restored = Robolectric.buildActivity(UpdateActivity.class,
                new Intent(context, UpdateActivity.class)).setup();
        assertTrue(state.error().startsWith("Download fehlgeschlagen"));
        assertEquals("Erneut versuchen", button(restored.get()).getText().toString());
        assertNull(shadowOf(restored.get()).getNextStartedActivity());
        restored.pause().stop().destroy();
    }

    @Test public void installerGetsReadableContentUriWithoutPackageVisibilityQueries() throws Exception {
        File file = new File(context.getFilesDir(), "installer-test.apk");
        byte[] bytes = new byte[]{1,2,3}; Files.write(file.toPath(), bytes);
        for (String action : new String[]{Intent.ACTION_INSTALL_PACKAGE, Intent.ACTION_VIEW}) {
            Intent intent = UpdateActivity.installerIntent(context, file, action);
            assertEquals("content", intent.getData().getScheme());
            assertEquals(intent.getData(), intent.getClipData().getItemAt(0).getUri());
            assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            try (java.io.InputStream in = context.getContentResolver().openInputStream(intent.getData())) {
                assertArrayEquals(bytes, in.readAllBytes());
            }
        }
    }

    @Test public void wrongOrStaleApkIsRejectedBeforeOpeningInstaller() throws Exception {
        File file = new File(context.getFilesDir(), "archive-test.apk");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (String name : new String[]{"AndroidManifest.xml", "classes.dex"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(1); zip.closeEntry();
            }
        }
        int target = BuildConfig.VERSION_CODE + 1;
        PackageInfo archive = new PackageInfo(); archive.packageName = context.getPackageName(); archive.versionCode = target - 1;
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(file.getPath(), archive);
        assertThrows(IllegalStateException.class, () -> UpdateActivity.validateArchive(context, file, target));
        archive.versionCode = target; archive.packageName = "other.app";
        assertThrows(IllegalStateException.class, () -> UpdateActivity.validateArchive(context, file, target));
        archive.packageName = context.getPackageName();
        UpdateActivity.validateArchive(context, file, target);
    }


    @Test public void selectClearsReadyWhenCachedFileMissing() {
        UpdateTransfer first = new UpdateTransfer(context); first.select(info);
        first.ready("/missing/streamy-install.apk");
        new UpdateTransfer(context).select(info);
        UpdateTransfer restored = new UpdateTransfer(context);
        assertEquals("", restored.readyPath());
        assertEquals(info.apkUrl, restored.info().apkUrl);
    }

    @Test public void downloadUrlAddsCacheBustQuery() {
        String first = UpdateActivity.downloadUrl("https://example.org/a.apk", 157);
        assertTrue(first.contains("?v=157&t="));
        String second = UpdateActivity.downloadUrl("https://example.org/a.apk?x=1", 157);
        assertTrue(second.contains("&v=157&t="));
    }

    @Test public void signatureMismatchRejectsArchiveAndMentionsCache() throws Exception {
        File file = new File(context.getFilesDir(), "sig-mismatch.apk");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (String name : new String[]{"AndroidManifest.xml", "classes.dex"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(1); zip.closeEntry();
            }
        }
        PackageInfo archive = new PackageInfo();
        int target = BuildConfig.VERSION_CODE + 1;
        archive.packageName = context.getPackageName();
        archive.versionCode = target;
        archive.signatures = new Signature[]{new Signature("001122")};
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(file.getPath(), archive);
        PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        installed.signatures = new Signature[]{new Signature("aabbcc")};
        shadowOf(context.getPackageManager()).installPackage(installed);
        Exception thrown = assertThrows(IllegalStateException.class,
                () -> UpdateActivity.validateArchive(context, file, target));
        assertTrue(thrown.getMessage().contains("Signatur"));
        assertTrue(thrown.getMessage().contains("Cache"));
        assertFalse(file.exists());
    }

    @Test public void matchingSignaturesPassValidation() throws Exception {
        File file = new File(context.getFilesDir(), "sig-ok.apk");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (String name : new String[]{"AndroidManifest.xml", "classes.dex"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(1); zip.closeEntry();
            }
        }
        Signature shared = new Signature("deadbeef");
        PackageInfo archive = new PackageInfo();
        int target = BuildConfig.VERSION_CODE + 1;
        archive.packageName = context.getPackageName();
        archive.versionCode = target;
        archive.signatures = new Signature[]{shared};
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(file.getPath(), archive);
        PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        installed.signatures = new Signature[]{shared};
        shadowOf(context.getPackageManager()).installPackage(installed);
        UpdateActivity.validateArchive(context, file, target);
        assertTrue(file.exists());
    }

    @Test public void installFailureDoesNotRecommendDeletingAppDataOrMislabelCompatibility() {
        assertFalse(InstallReceiver.mapStatus(android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "ABI mismatch").contains("Signatur"));
        String signature = InstallReceiver.mapStatus(android.content.pm.PackageInstaller.STATUS_FAILURE, "signatures do not match");
        assertFalse(signature.contains("deinstallieren")); assertFalse(signature.contains("3.10"));
    }

    private Button button(UpdateActivity activity) {
        return findAction((ViewGroup) activity.findViewById(android.R.id.content));
    }
    private Button findAction(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view instanceof Button && view.getVisibility() == View.VISIBLE
                    && !"Zurück".contentEquals(((Button) view).getText())) return (Button) view;
            if (view instanceof ViewGroup) { Button result = findAction((ViewGroup) view); if (result != null) return result; }
        }
        return null;
    }
}
