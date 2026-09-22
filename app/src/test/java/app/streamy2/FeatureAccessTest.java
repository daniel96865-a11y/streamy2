package app.streamy2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FeatureAccessTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("streamy2_access", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void freshInstallStartsLocked() {
        assertFalse(FeatureAccess.isUnlocked(context));
    }

    @Test
    public void megakinoUrlsAreRestricted() {
        assertTrue(FeatureAccess.isRestrictedUrl("https://megakino.example/watch"));
        assertTrue(FeatureAccess.isRestrictedUrl("HTTPS://MEGAKINO.EXAMPLE"));
    }

    @Test
    public void normalBrowserUrlsRemainAvailable() {
        assertFalse(FeatureAccess.isRestrictedUrl("https://example.com"));
        assertFalse(FeatureAccess.isRestrictedUrl(null));
    }

    @Test
    public void productionAccessApiIsConfigured() {
        assertTrue(BuildConfig.ACCESS_API_URL.startsWith("https://"));
    }
}
