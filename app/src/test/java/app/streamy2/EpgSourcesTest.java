package app.streamy2;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EpgSourcesTest {
    private static final String P = "http://provider.example/xmltv.php?username=u&password=p";

    @Test public void autoUsesProviderFirstThenWeb() {
        List<EpgSources.Source> plan = EpgSources.plan("auto", P, false);
        assertEquals(1 + EpgSources.WEB_URLS.length, plan.size());
        assertEquals(P, plan.get(0).url);
        assertFalse(plan.get(0).web);
        for (int i = 1; i < plan.size(); i++) assertTrue(plan.get(i).web);
    }

    @Test public void providerOnlySkipsWebUnlessBuiltinSectionPresent() {
        List<EpgSources.Source> plan = EpgSources.plan("provider", P, false);
        assertEquals(1, plan.size());
        assertEquals(P, plan.get(0).url);
        // Built-in live section always needs the web EPG.
        plan = EpgSources.plan("provider", P, true);
        assertEquals(1 + EpgSources.WEB_URLS.length, plan.size());
    }

    @Test public void webOnlySkipsProvider() {
        List<EpgSources.Source> plan = EpgSources.plan("web", P, false);
        assertEquals(EpgSources.WEB_URLS.length, plan.size());
        for (EpgSources.Source s : plan) assertTrue(s.web);
    }

    @Test public void noProviderUrlMeansWebOnly() {
        assertEquals(EpgSources.WEB_URLS.length, EpgSources.plan("auto", "", true).size());
        assertEquals(0, EpgSources.plan("provider", "", false).size());
        assertEquals(EpgSources.WEB_URLS.length, EpgSources.plan("provider", null, true).size());
    }

    @Test public void channelPermissions() {
        assertTrue(EpgSources.allowProvider("auto", false));
        assertTrue(EpgSources.allowWeb("auto", false));
        assertTrue(EpgSources.allowProvider("provider", false));
        assertFalse(EpgSources.allowWeb("provider", false));
        assertFalse(EpgSources.allowProvider("web", false));
        assertTrue(EpgSources.allowWeb("web", false));
        for (String m : new String[]{"auto", "provider", "web", null, "x"}) {
            assertFalse("builtin never provider: " + m, EpgSources.allowProvider(m, true));
            assertTrue("builtin always web: " + m, EpgSources.allowWeb(m, true));
        }
    }

    @Test public void normalizeAndLabels() {
        assertEquals("auto", EpgSources.normalize(null));
        assertEquals("auto", EpgSources.normalize("irgendwas"));
        assertEquals("Automatisch", EpgSources.label("auto"));
        assertEquals("Vom Anbieter", EpgSources.label("provider"));
        assertEquals("Aus dem Netz", EpgSources.label("web"));
        Models.Channel c = new Models.Channel();
        c.categoryId = "extra_live";
        assertTrue(EpgSources.isBuiltin(c));
        c.categoryId = "extra_live_pl";
        assertTrue(EpgSources.isBuiltin(c));
        c.categoryId = "12";
        assertFalse(EpgSources.isBuiltin(c));
    }
}
