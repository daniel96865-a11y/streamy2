import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks actual APK signatures and Android's permission-owner migration rule.
 * Usage: java -cp BUILD_TOOLS/lib/apksigner.jar tools/VerifyUpgrade.java OLD.apk NEW.apk BUILD_TOOLS/aapt
 * This is an offline preflight, not an on-device package-installation test.
 */
class VerifyUpgrade {
    static String aapt(String binary, File apk, String command) throws Exception {
        ProcessBuilder pb = command.equals("badging")
                ? new ProcessBuilder(binary, "dump", "badging", apk.getPath())
                : new ProcessBuilder(binary, "dump", "xmltree", apk.getPath(), "AndroidManifest.xml");
        Process p = pb.redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException("aapt failed: " + text);
        return text;
    }

    static String capture(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) throw new IllegalStateException("Missing APK metadata: " + regex);
        return m.group(1);
    }

    static Set<String> declaredPermissions(String manifest) {
        Set<String> result = new HashSet<>();
        boolean permission = false;
        for (String line : manifest.split("\n")) {
            if (line.startsWith("    E: ")) permission = line.startsWith("    E: permission (");
            else if (permission && line.startsWith("      A: android:name(")) {
                result.add(capture(line, "=\"([^\"]+)\""));
            }
        }
        return result;
    }

    static ApkVerifier.Result verify(File apk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(28).setMaxCheckedPlatformVersion(36).build().verify();
        if (!result.isVerified() || result.getSignerCertificates().size() != 1)
            throw new IllegalStateException("APK signature validation failed: " + apk);
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("OLD.apk NEW.apk AAPT");
        File oldApk = new File(args[0]), newApk = new File(args[1]);
        String oldInfo = aapt(args[2], oldApk, "badging");
        String newInfo = aapt(args[2], newApk, "badging");
        String packageName = capture(oldInfo, "package: name='([^']+)'");
        if (!packageName.equals(capture(newInfo, "package: name='([^']+)'")))
            throw new IllegalStateException("Package name changed; this would be a separate app");
        if (Long.parseLong(capture(newInfo, "versionCode='(\\d+)'"))
                <= Long.parseLong(capture(oldInfo, "versionCode='(\\d+)'")))
            throw new IllegalStateException("Update versionCode must increase");
        if (Integer.parseInt(capture(newInfo, "sdkVersion:'(\\d+)'")) < 28)
            throw new IllegalStateException("Secure key rotation requires minSdk 28");

        X509Certificate oldCert = verify(oldApk).getSignerCertificates().get(0);
        ApkVerifier.Result newResult = verify(newApk);
        X509Certificate newCert = newResult.getSignerCertificates().get(0);
        if (!oldCert.equals(newCert)) {
            SigningCertificateLineage lineage = newResult.getSigningCertificateLineage();
            if (lineage == null || !lineage.isCertificateInLineage(oldCert)
                    || !lineage.isCertificateLatestInLineage(newCert))
                throw new IllegalStateException("Update signer is not a descendant of the installed signer");
            SigningCertificateLineage.SignerCapabilities caps = lineage.getSignerCapabilities(oldCert);
            if (!caps.hasInstalledData()) throw new IllegalStateException("Installed-data capability is missing");

            // InstallPackageHelper checks the PERMISSION capability even when the
            // same package redeclares a permission it owned before key rotation.
            Set<String> common = declaredPermissions(aapt(args[2], oldApk, "xmltree"));
            common.retainAll(declaredPermissions(aapt(args[2], newApk, "xmltree")));
            if (!common.isEmpty() && !caps.hasPermission())
                throw new IllegalStateException("INSTALL_FAILED_DUPLICATE_PERMISSION: " + common);
            if (caps.hasPermission() || caps.hasRollback() || caps.hasSharedUid() || caps.hasAuth())
                throw new IllegalStateException("The compromised signing key retains unwanted capabilities");
        }
        System.out.println("Upgrade preflight passed: package, version, signatures, lineage and permission ownership");
    }
}
