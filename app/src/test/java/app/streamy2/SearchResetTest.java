package app.streamy2;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Phone regression (3.79): after searching, switching tab/section, picking a result
 * or pressing Back must leave the search (query cleared, field unfocused, keyboard
 * hidden) instead of keeping the search filter on every section.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class)
public class SearchResetTest {

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }

    private static ActivityController<MainActivity> launch() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        c.create().start().postCreate(null).resume().visible();
        idle();
        return c;
    }

    private static int count(MainActivity a) {
        RecyclerView list = a.findViewById(R.id.list);
        return list.getAdapter() == null ? 0 : list.getAdapter().getItemCount();
    }

    private static InputMethodManager imm(MainActivity a) {
        return (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /** Focus the search field, open the keyboard and type a query. */
    private static EditText typeSearch(MainActivity a, String text) {
        EditText search = a.findViewById(R.id.search);
        search.setFocusableInTouchMode(true);
        assertTrue("search must take focus", search.requestFocus());
        imm(a).showSoftInput(search, 0);
        search.setText(text);
        idle();
        assertTrue(shadowOf(imm(a)).isSoftInputVisible());
        return search;
    }

    private static void assertSearchClosed(MainActivity a, EditText search) {
        assertEquals("", search.getText().toString());
        assertFalse("search field must lose focus", search.hasFocus());
        assertFalse("keyboard must be hidden", shadowOf(imm(a)).isSoftInputVisible());
        assertFalse(a.searchActive());
    }

    @Test
    public void switchingTabClearsSearchAndHidesKeyboard() {
        ActivityController<MainActivity> c = launch();
        MainActivity a = c.get();
        a.findViewById(R.id.tabSeries).performClick();
        idle();
        int allSeries = count(a);
        a.findViewById(R.id.tabMovies).performClick();
        idle();
        int allMovies = count(a);
        assertTrue("demo catalog should have movies", allMovies > 1);

        EditText search = typeSearch(a, "nebelwacht");
        int filtered = count(a);
        assertTrue("search filters the movie list", filtered < allMovies);

        // Leave search by switching to another section.
        a.findViewById(R.id.tabSeries).performClick();
        idle();
        assertSearchClosed(a, search);
        assertEquals("series tab shows normal content", allSeries, count(a));

        // Back to movies: full list again, no leftover filter, keyboard stays closed.
        a.findViewById(R.id.tabMovies).performClick();
        idle();
        assertEquals(allMovies, count(a));
        assertSearchClosed(a, search);

        // Search again, then tap the *same* section: also leaves search.
        typeSearch(a, "nebelwacht");
        assertTrue(count(a) < allMovies);
        a.findViewById(R.id.tabMovies).performClick();
        idle();
        assertSearchClosed(a, search);
        assertEquals(allMovies, count(a));

        // Resume (e.g. back from player) must not bring the keyboard back.
        c.pause().resume();
        idle();
        assertFalse(search.hasFocus());
        assertFalse(shadowOf(imm(a)).isSoftInputVisible());
        c.pause().stop().destroy();
    }

    @Test
    public void backClosesSearchFirst() {
        ActivityController<MainActivity> c = launch();
        MainActivity a = c.get();
        a.findViewById(R.id.tabMovies).performClick();
        idle();
        int allMovies = count(a);
        EditText search = typeSearch(a, "nebelwacht");
        assertTrue(count(a) < allMovies);

        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertSearchClosed(a, search);
        assertEquals(allMovies, count(a));
        assertFalse("activity must stay open after closing search", a.isFinishing());
        c.pause().stop().destroy();
    }

    @Test
    public void openingResultHidesKeyboardAndReleasesField() {
        ActivityController<MainActivity> c = launch();
        MainActivity a = c.get();
        a.findViewById(R.id.tabMovies).performClick();
        idle();
        EditText search = typeSearch(a, "nebelwacht");
        Models.Media hit = new Models.Media();
        hit.id = "vod:test";
        hit.name = "Nebelwacht";
        a.onMedia(hit);
        idle();
        assertFalse(search.hasFocus());
        assertFalse(shadowOf(imm(a)).isSoftInputVisible());
        c.pause().stop().destroy();
    }
}
