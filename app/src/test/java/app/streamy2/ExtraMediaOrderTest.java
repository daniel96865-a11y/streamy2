package app.streamy2;

import android.app.Application;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ExtraMediaOrderTest {
    private static Models.Media film(String path) {
        Models.Media m = new Models.Media();
        m.streamUrl = "https://example.invalid" + path;
        m.id = "mk:" + m.streamUrl;
        m.name = path;
        return m;
    }

    @Test public void postIdIsParsedFromDetailUrl() {
        assertEquals(6521L, ExtraMediaSource.postId(film("/films/6521-die-camino-therapie-finde-deinen-weg.html")));
        assertEquals(-1L, ExtraMediaSource.postId(film("/films/")));
        assertEquals(-1L, ExtraMediaSource.postId(null));
    }

    @Test public void newestUploadsComeFirstEvenWhenCinemaTitlesWereAppended() {
        List<Models.Media> list = new ArrayList<>();
        list.add(film("/films/6520-unabomber.html"));
        list.add(film("/films/6521-die-camino-therapie.html"));
        list.add(film("/films/6166-how-to-make-a-killing.html"));
        list.add(film("/films/6395-spider-man-brand-new-day.html"));
        list.add(film("/films/no-id"));
        ExtraMediaSource.sortNewestFirst(list);
        assertEquals(6521L, ExtraMediaSource.postId(list.get(0)));
        assertEquals(6520L, ExtraMediaSource.postId(list.get(1)));
        assertEquals(6395L, ExtraMediaSource.postId(list.get(2)));
        assertEquals(6166L, ExtraMediaSource.postId(list.get(3)));
        assertEquals(-1L, ExtraMediaSource.postId(list.get(4)));
    }

    @Test public void localSearchFiltersByTrimmedCaseInsensitiveTitle() {
        Models.Media m = film("/films/6520-unabomber.html");
        m.name = "UNABOMBER";
        assertTrue(ExtraMediaSource.matchesQuery(m, ""));
        assertTrue(ExtraMediaSource.matchesQuery(m, null));
        assertTrue(ExtraMediaSource.matchesQuery(m, "  bomb "));
        assertTrue(ExtraMediaSource.matchesQuery(m, "Unabomber"));
        assertFalse(ExtraMediaSource.matchesQuery(m, "spider"));
        assertFalse(ExtraMediaSource.matchesQuery(null, ""));
    }
}
