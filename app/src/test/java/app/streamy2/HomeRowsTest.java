package app.streamy2;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Start screen rows (3.89): recents, favourites, "Läuft gerade", chips, hero. */
public class HomeRowsTest {
    private static final long NOW = 1_790_000_000_000L;

    private static Models.Channel ch(String id, String name, String cat) {
        Models.Channel c = new Models.Channel();
        c.id = id;
        c.name = name;
        c.categoryId = cat;
        return c;
    }

    private static Models.Epg epg(long start, long end, String title) {
        Models.Epg e = new Models.Epg();
        e.start = start;
        e.end = end;
        e.title = title;
        return e;
    }

    private static HomeRows.EpgLookup lookup(final Map<String, Models.Epg> map) {
        return new HomeRows.EpgLookup() {
            @Override public Models.Epg current(Models.Channel c, long now) {
                return map.get(c.id);
            }
        };
    }

    private static List<Models.Channel> channels() {
        List<Models.Channel> l = new ArrayList<>();
        Models.Channel header = ch("h", "Deutschland", "1");
        header.header = true;
        l.add(header);
        l.add(ch("1", "Das Erste", "1"));
        l.add(ch("2", "ZDF", "1"));
        l.add(ch("3", "Sport 1", "2"));
        l.add(ch("4", "Kika", "3"));
        return l;
    }

    private static List<Models.Category> cats() {
        return Arrays.asList(new Models.Category("1", "Vollprogramm"), new Models.Category("2", "Sport"),
                new Models.Category("3", "Kinder"), new Models.Category("9", "Leer"));
    }

    @Test public void idListsRoundTripAndDeduplicate() {
        assertEquals(Arrays.asList("a", "b"), HomeRows.splitIds("a\nb\na\n"));
        assertTrue(HomeRows.splitIds(null).isEmpty());
        assertEquals("a\nb", HomeRows.joinIds(Arrays.asList("a", "", "b", "x\ny")));
        assertEquals(Arrays.asList("c", "a", "b"), HomeRows.pushRecent(Arrays.asList("a", "b", "c"), "c", 15));
        assertEquals(Arrays.asList("d", "a"), HomeRows.pushRecent(Arrays.asList("a", "b", "c"), "d", 2));
        List<String> fav = new ArrayList<>(Arrays.asList("1"));
        assertTrue(HomeRows.toggle(fav, "2"));
        assertFalse(HomeRows.toggle(fav, "1"));
        assertEquals(Arrays.asList("2"), fav);
    }

    @Test public void emptyRowsAreHidden() {
        HomeRows.Home home = HomeRows.build(channels(), cats(), new ArrayList<String>(), new ArrayList<String>(),
                lookup(new HashMap<String, Models.Epg>()), NOW);
        assertTrue(home.rows.isEmpty());
        assertNull(home.hero);
        // Chips still allow browsing the categories.
        assertFalse(home.chips.isEmpty());
        assertTrue(HomeRows.build(null, null, null, null, null, NOW).isEmpty());
    }

    @Test public void rowsInOrderWithUnknownIdsSkipped() {
        Map<String, Models.Epg> map = new HashMap<>();
        map.put("2", epg(NOW - 10 * 60_000L, NOW + 20 * 60_000L, "heute"));
        map.put("4", epg(NOW - 60 * 60_000L, NOW - 1, "vorbei"));
        HomeRows.Home home = HomeRows.build(channels(), cats(), Arrays.asList("3", "gone", "1"), Arrays.asList("2", "h"),
                lookup(map), NOW);
        assertEquals(3, home.rows.size());
        assertEquals(HomeRows.ROW_RECENT, home.rows.get(0).title);
        assertEquals("3", home.rows.get(0).channels.get(0).id);
        assertEquals("1", home.rows.get(0).channels.get(1).id);
        assertEquals(2, home.rows.get(0).channels.size());
        assertEquals(HomeRows.ROW_FAVORITES, home.rows.get(1).title);
        assertEquals(1, home.rows.get(1).channels.size()); // header "h" is never a channel tile
        assertEquals(HomeRows.ROW_NOW, home.rows.get(2).title);
        assertEquals(1, home.rows.get(2).channels.size()); // only ZDF has a current programme
        // Hero: first channel with a running programme (recents have none → favourite ZDF).
        assertEquals("2", home.hero.id);
        assertEquals("heute", home.heroEpg.title);
    }

    @Test public void heroFallsBackToLastWatchedWithoutEpg() {
        HomeRows.Home home = HomeRows.build(channels(), cats(), Arrays.asList("4", "1"), null,
                lookup(new HashMap<String, Models.Epg>()), NOW);
        assertEquals("4", home.hero.id);
        assertNull(home.heroEpg);
    }

    @Test public void chipsCountChannelsAndSkipEmptyCategories() {
        List<HomeRows.Chip> chips = HomeRows.chips(cats(), channels());
        assertEquals("Alle Sender", chips.get(0).name);
        assertEquals(4, chips.get(0).count);
        assertEquals(4, chips.size()); // "Leer" has no channels
        assertEquals("Vollprogramm", chips.get(1).name);
        assertEquals(2, chips.get(1).count);
        assertEquals("1", chips.get(1).id);
    }

    @Test public void progressAndRemainingTime() {
        Models.Epg e = epg(NOW - 30 * 60_000L, NOW + 90 * 60_000L, "x");
        assertEquals(25, HomeRows.progress(e, NOW));
        assertEquals(0, HomeRows.progress(e, e.start - 1));
        assertEquals(100, HomeRows.progress(e, e.end + 1));
        assertEquals("noch 1 Std. 30 Min.", HomeRows.remaining(e, NOW));
        assertEquals("noch 35 Min.", HomeRows.remaining(epg(NOW - 1, NOW + 35 * 60_000L, "y"), NOW));
        assertEquals("noch 2 Std.", HomeRows.remaining(epg(NOW - 1, NOW + 120 * 60_000L, "y"), NOW));
        assertEquals("", HomeRows.remaining(epg(NOW + 1, NOW + 5, "z"), NOW));
        assertTrue(HomeRows.covers(e, NOW));
        assertFalse(HomeRows.covers(null, NOW));
    }

    @Test public void nowPlayingRespectsLimits() {
        List<Models.Channel> many = new ArrayList<>();
        Map<String, Models.Epg> map = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            many.add(ch("c" + i, "Sender " + i, "1"));
            map.put("c" + i, epg(NOW - 1, NOW + 1000, "p"));
        }
        assertEquals(HomeRows.MAX_ROW, HomeRows.nowPlaying(many, lookup(map), NOW, HomeRows.MAX_ROW, HomeRows.NOW_SCAN_LIMIT).size());
        assertEquals(5, HomeRows.nowPlaying(many, lookup(map), NOW, 50, 5).size());
    }

    @Test public void keyUsesIdOrName() {
        assertEquals("7", HomeRows.key(ch("7", "A", null)));
        assertEquals("Arte", HomeRows.key(ch(null, "Arte", null)));
        assertNull(HomeRows.key(null));
    }
}
