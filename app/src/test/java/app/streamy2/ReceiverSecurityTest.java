package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import app.streamy2.build.ReceiverPermissionClassVisitor;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Exercise the real, instrumented AndroidX fallback used on Android 9–12. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ReceiverSecurityTest {
    // AGP's unit-test dependency classpath contains the original AndroidX classes.
    // Load the real dependency through the production bytecode visitor so these
    // tests exercise the same guard and permission name that are packaged in the APK.
    private static void register(Context context, BroadcastReceiver receiver, IntentFilter filter) throws Exception {
        ClassLoader parent = ContextCompat.class.getClassLoader();
        ClassLoader transformed = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(ContextCompat.class.getName())
                        && !name.startsWith(ContextCompat.class.getName() + "$")) return super.loadClass(name, resolve);
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    try (InputStream input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        if (name.equals(ContextCompat.class.getName())) {
                            ClassWriter writer = new ClassWriter(0);
                            new ClassReader(bytes).accept(new ReceiverPermissionClassVisitor(writer), 0);
                            bytes = writer.toByteArray();
                        }
                        found = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
                }
                if (resolve) resolveClass(found);
                return found;
            }
        };
        try {
            transformed.loadClass(ContextCompat.class.getName())
                    .getMethod("registerReceiver", Context.class, BroadcastReceiver.class, IntentFilter.class, int.class)
                    .invoke(null, context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new AssertionError(e.getCause());
        }
    }

    @Test public void ownBroadcastWorksWithTheNewManifestPermission() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(app.getPackageName()
                + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION_2026");
        AtomicInteger received = new AtomicInteger();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { received.incrementAndGet(); }
        };
        String action = app.getPackageName() + ".RECEIVER_MIGRATION_TEST";
        register(app, receiver, new IntentFilter(action));
        try {
            app.sendBroadcast(new Intent(action));
            ShadowLooper.idleMainLooper();
            assertEquals(1, received.get());
        } finally {
            app.unregisterReceiver(receiver);
        }
    }

    @Test public void missingSignaturePermissionStillRejectsRegistration() {
        Context unprivileged = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public int checkPermission(String permission, int pid, int uid) {
                return PackageManager.PERMISSION_DENIED;
            }
        };
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { }
        };
        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                register(unprivileged, receiver, new IntentFilter("app.streamy2.UNPRIVILEGED_TEST")));
        assertTrue(failure.getMessage().contains("required by your application"));
    }
}
