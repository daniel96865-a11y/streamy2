package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatesFeedTest {
    private static final String FEED = "{\"versionCode\":208,\"versionName\":\"3.88\",\"apkUrl\":\"https://raw.githubusercontent.com/daniel96865-a11y/streamy2/main/docs/downloads/Streamy2-TV-3.88.apk\",\"changelog\":\"3.88: Bild-in-Bild in der Mobile-App (Taste im Player oder automatisch beim Verlassen), Einstellung unter Wiedergabe.\"}\n";

    @Test public void channelsReadTheirOwnFeedPlusApiCopy() {
        String[] tv = Updates.feedsFor("tv-new");
        assertEquals(3, tv.length);
        assertTrue(tv[0].endsWith("/docs/streamy2-tv.json"));
        assertTrue(tv[1].endsWith("/docs/streamy2-tv.txt"));
        assertTrue(tv[2].startsWith("https://api.github.com/repos/daniel96865-a11y/streamy2/contents/docs/streamy2-tv.json"));
        String[] mobile = Updates.feedsFor("mobile");
        assertEquals(3, mobile.length);
        assertTrue(mobile[0].endsWith("/docs/streamy2-mobile.json"));
        assertTrue(mobile[2].contains("/contents/docs/streamy2-mobile.json"));
        String[] legacy = Updates.feedsFor("legacy");
        assertEquals(2, legacy.length);
        assertTrue(legacy[0].endsWith("/docs/streamy2.json"));
    }

    @Test public void parsesCurrentFeedFormat() {
        Updates.Info info = Updates.parse(FEED);
        assertNotNull(info);
        assertEquals(208, info.versionCode);
        assertEquals("3.88", info.versionName);
        assertTrue(info.apkUrl.endsWith("Streamy2-TV-3.88.apk"));
        assertTrue(info.changelog.startsWith("3.88:"));
        assertNull(Updates.parse(""));
        assertNull(Updates.parse("{\"versionCode\":208}"));
    }

    @Test public void staleCdnCopyLosesAgainstFreshApiCopy() {
        Updates.Info stale = Updates.parse(FEED.replace("208", "207").replace("3.88", "3.87"));
        Updates.Info fresh = Updates.parse(FEED);
        Updates.Info best = Updates.newer(Updates.newer(Updates.newer(null, stale), stale), fresh);
        assertEquals(208, best.versionCode);
        assertSame(fresh, Updates.newer(fresh, stale));
        assertSame(fresh, Updates.newer(fresh, null));
        Updates.Info broken = new Updates.Info();
        broken.versionCode = 999;
        assertSame(fresh, Updates.newer(fresh, broken)); // no apkUrl
    }
}
