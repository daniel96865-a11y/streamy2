package app.streamy2;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public class PlaybackDiagnosticsTest {
    @Test public void readsNativeCrashAndAnrFromAndroidHistory(){
        Context c=RuntimeEnvironment.getApplication();
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        Shadows.shadowOf(manager).addApplicationExitInfo(c.getPackageName(),101,5,11);
        Shadows.shadowOf(manager).addApplicationExitInfo(c.getPackageName(),102,6,0);
        String report=PlaybackDiagnostics.report(c,"Engine: VLC");
        assertTrue(report.contains("Nativer Absturz"));assertTrue(report.contains("Status/Signal 11"));
        assertTrue(report.contains("App reagierte nicht (ANR)"));assertTrue(report.contains("früheren App-Versionen"));
    }
    @Test @Config(sdk=28) public void oldAndroidShowsLimitationWithoutCallingNewApi(){
        String report=PlaybackDiagnostics.report(RuntimeEnvironment.getApplication(),"Engine: Exo");
        assertTrue(report.contains("erst ab Android 11"));assertFalse(report.contains("konnte nicht gelesen"));
    }
    @Test public void emptyHistoryDoesNotClaimNoCrash(){
        String report=PlaybackDiagnostics.report(RuntimeEnvironment.getApplication(),"");
        assertTrue(report.contains("Keine gespeicherten Beendigungen"));
        assertTrue(report.contains("schließt einen Absturz nicht aus"));
    }
    @Test public void redactsUrlsAndCredentialsAndBoundsText(){
        String report=PlaybackDiagnostics.clean("https://example.test/user/secret?token=abc password=secret token=secret");
        assertFalse(report.contains("secret"));assertFalse(report.contains("example.test"));assertFalse(report.contains("abc"));
        assertTrue(PlaybackDiagnostics.clean("x".repeat(9000)).length()<=2001);
    }
    @Test public void updateAndSignalAreNotMisreportedAsCrashes(){
        assertTrue(PlaybackDiagnostics.reason(16).contains("kein Absturznachweis"));
        assertEquals("Durch Signal beendet",PlaybackDiagnostics.reason(2));
        assertEquals("Ursache unbekannt",PlaybackDiagnostics.reason(999));
    }
}
