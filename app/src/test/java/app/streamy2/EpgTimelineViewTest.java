package app.streamy2;

import static org.junit.Assert.*;

import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Remote navigation in the timeline grid (3.89): left/right through time, up/down through channels, OK. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class EpgTimelineViewTest {
    private static final long MIN = Timeline.MIN;
    private final long base = Timeline.dayStart(System.currentTimeMillis()) + 20 * 60 * MIN;

    private static EpgGuide.Listing l(long start, long stop, String title) {
        EpgGuide.Listing x = new EpgGuide.Listing();
        x.start = start;
        x.stop = stop;
        x.title = title;
        return x;
    }

    private static Models.Channel ch(String id) {
        Models.Channel c = new Models.Channel();
        c.id = id;
        c.name = "Sender " + id;
        return c;
    }

    private EpgTimelineView view(final List<String> opened) {
        ContextThemeWrapper ctx = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Streamy);
        EpgTimelineView v = new EpgTimelineView(ctx);
        List<Models.Channel> channels = new ArrayList<>();
        for (int i = 0; i < 300; i++) channels.add(ch("c" + i));
        v.setData(channels, new EpgTimelineView.RowSource() {
            @Override public List<EpgGuide.Listing> listings(Models.Channel c) {
                List<EpgGuide.Listing> row = new ArrayList<>();
                if ("c2".equals(c.id)) return row; // channel without programme data
                if ("c1".equals(c.id)) {
                    row.add(l(base - 30 * MIN, base + 50 * MIN, c.id + " Serie"));
                    row.add(l(base + 50 * MIN, base + 140 * MIN, c.id + " Doku"));
                    return row;
                }
                row.add(l(base, base + 15 * MIN, c.id + " Nachrichten"));
                row.add(l(base + 15 * MIN, base + 120 * MIN, c.id + " Film"));
                row.add(l(base + 120 * MIN, base + 180 * MIN, c.id + " Talk"));
                return row;
            }
        }, base - 6 * 60 * MIN, base + 6 * 60 * MIN);
        v.setListener(new EpgTimelineView.Listener() {
            @Override public void onFocusChanged(Models.Channel channel, EpgGuide.Listing listing) {
            }

            @Override public void onOpen(Models.Channel channel, EpgGuide.Listing listing) {
                opened.add(channel.id + ":" + (listing == null ? "-" : listing.title));
            }

            @Override public void onVisibleTime(long centerTime) {
            }
        });
        v.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1280, 600);
        return v;
    }

    private static void press(View v, int key) {
        v.onKeyDown(key, new KeyEvent(KeyEvent.ACTION_DOWN, key));
    }

    @Test public void dpadMovesThroughTimeAndChannels() {
        List<String> opened = new ArrayList<>();
        EpgTimelineView v = view(opened);
        v.focusAt(0, base + 5 * MIN, false);
        assertEquals("c0 Nachrichten", v.focusedListing().title);
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("c0 Film", v.focusedListing().title);
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("c0 Talk", v.focusedListing().title);
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT); // end of data: stays
        assertEquals("c0 Talk", v.focusedListing().title);
        press(v, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals("c0 Film", v.focusedListing().title);
        // Down keeps the time: 20:15 on c1 is inside "Serie".
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(1, v.focusRow);
        assertEquals("Sender c1", v.focusedChannel().name);
        assertEquals("c1 Serie", v.focusedListing().title);
        // Channel without data: no programme focused, but the row is.
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(2, v.focusRow);
        assertNull(v.focusedListing());
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(3, v.focusRow);
        assertNotNull(v.focusedListing());
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(1, opened.size());
        assertTrue(opened.get(0), opened.get(0).startsWith("c3:"));
    }

    @Test public void edgesAndPaging() {
        EpgTimelineView v = view(new ArrayList<String>());
        v.focusAt(0, base, false);
        // Up on the first row is not consumed (focus may move to the info panel).
        assertFalse(v.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertEquals(0, v.focusRow);
        press(v, KeyEvent.KEYCODE_PAGE_DOWN);
        assertTrue(v.focusRow > 5);
        v.focusAt(10_000, base, false);
        assertEquals(299, v.focusRow);
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(299, v.focusRow);
        // Only a handful of rows were loaded lazily, not all 300.
        assertTrue(v.loadedRows() < 60);
    }
}
