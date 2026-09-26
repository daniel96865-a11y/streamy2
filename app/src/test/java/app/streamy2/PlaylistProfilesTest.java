package app.streamy2;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaylistProfilesTest {
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("streamy2", 0).edit().clear().commit();
    }

    @Test public void legacyAccountMigratesIntoFirstProfile() {
        SharedPreferences raw = context.getSharedPreferences("streamy2", 0);
        raw.edit()
                .putString("name", "Alt")
                .putString("url", "https://example.test")
                .putString("user", "alice")
                .putString("pass", "secret")
                .putString("format", "ts")
                .putString("epgUrl", "https://example.test/epg.xml")
                .commit();

        Prefs prefs = new Prefs(context);
        assertEquals(1, prefs.profileCount());
        assertEquals("Alt", prefs.activeProfileName());
        assertEquals("https://example.test", prefs.url());
        assertEquals("alice", prefs.user());
        assertEquals("secret", prefs.pass());
        assertEquals("ts", prefs.format());
        assertEquals("https://example.test/epg.xml", prefs.epgUrl());
        assertTrue(prefs.hasXtream());
    }

    @Test public void profilesKeepCredentialsAndCachesSeparated() throws Exception {
        Prefs prefs = new Prefs(context);
        String first = prefs.activeProfileId();
        prefs.saveAccount("Liste A", "https://a.test", "a", "pa");
        prefs.setEpgUrl("https://a.test/epg.xml");

        String second = prefs.createProfile("Liste B");
        prefs.saveAccount("Liste B", "https://b.test", "b", "pb");
        prefs.setEpgUrl("https://b.test/epg.xml");

        assertNotEquals(first, second);
        assertEquals(2, prefs.profileCount());
        assertEquals("https://b.test", prefs.url());
        assertEquals("b", prefs.user());

        File cacheDir = Files.createTempDirectory("streamy-profiles").toFile();
        File secondCache = prefs.catalogCacheFile(cacheDir);

        assertTrue(prefs.setActiveProfile(first));
        assertEquals("Liste A", prefs.activeProfileName());
        assertEquals("https://a.test", prefs.url());
        assertEquals("a", prefs.user());
        assertEquals("https://a.test/epg.xml", prefs.epgUrl());
        File firstCache = prefs.catalogCacheFile(cacheDir);
        assertNotEquals(firstCache.getName(), secondCache.getName());

        assertTrue(prefs.setActiveProfile(second));
        assertEquals("Liste B", prefs.activeProfileName());
        assertEquals("https://b.test", prefs.url());
        assertEquals("b", prefs.user());
        assertEquals("https://b.test/epg.xml", prefs.epgUrl());
    }

    @Test public void audioModeNormalizesAndPersists() {
        Prefs prefs = new Prefs(context);
        assertEquals("auto", prefs.audioMode());
        prefs.setAudioMode("surround");
        assertEquals("surround", prefs.audioMode());
        prefs.setAudioMode("stereo");
        assertEquals("stereo", prefs.audioMode());
        prefs.setAudioMode("invalid");
        assertEquals("auto", prefs.audioMode());
    }

    @Test public void deletingActiveProfileKeepsAnotherProfileAvailable() {
        Prefs prefs = new Prefs(context);
        String first = prefs.activeProfileId();
        prefs.saveAccount("Liste A", "https://a.test", "a", "pa");
        String second = prefs.createProfile("Liste B");
        prefs.saveAccount("Liste B", "https://b.test", "b", "pb");

        prefs.deleteActiveProfile();

        assertEquals(1, prefs.profileCount());
        assertEquals(first, prefs.activeProfileId());
        assertEquals("Liste A", prefs.activeProfileName());
        assertEquals("https://a.test", prefs.url());
        assertFalse(prefs.profileIds().contains(second));
    }

    // --- 3.75: adding a playlist must never replace the previous one ---

    @Test public void savingDifferentServerCreatesSecondPlaylistAndKeepsFirst() {
        Prefs prefs = new Prefs(context);
        String first = prefs.saveAccountAsPlaylist("Liste A", "http://a.test:8080", "alice", "pa");
        String second = prefs.saveAccountAsPlaylist("Liste B", "http://b.test:8080", "bob", "pb");
        assertNotEquals(first, second);
        assertEquals(2, prefs.profileCount());
        assertEquals(second, prefs.activeProfileId());
        assertEquals("http://b.test:8080", prefs.url());
        assertTrue(prefs.setActiveProfile(first));
        assertEquals("http://a.test:8080", prefs.url());
        assertEquals("alice", prefs.user());
        assertEquals("pa", prefs.pass());
    }

    @Test public void resavingSamePlaylistUpdatesInsteadOfDuplicating() {
        Prefs prefs = new Prefs(context);
        String a = prefs.saveAccountAsPlaylist("Liste A", "http://a.test:8080", "alice", "pa");
        prefs.saveAccountAsPlaylist("Liste B", "http://b.test", "bob", "pb");
        // same server (different notation) + user -> update A and select it
        String again = prefs.saveAccountAsPlaylist("A neu", "https://A.test:8080/", "alice", "neu");
        assertEquals(a, again);
        assertEquals(2, prefs.profileCount());
        assertEquals(a, prefs.activeProfileId());
        assertEquals("neu", prefs.pass());
        // server moved: same user + password on the active playlist -> still one entry
        String moved = prefs.saveAccountAsPlaylist("A neu", "http://a2.test", "alice", "neu");
        assertEquals(a, moved);
        assertEquals(2, prefs.profileCount());
        assertEquals("http://a2.test", prefs.url());
    }

    @Test public void activeSelectionPersistsAcrossRestart() {
        Prefs prefs = new Prefs(context);
        String a = prefs.saveAccountAsPlaylist("Liste A", "http://a.test", "alice", "pa");
        String b = prefs.saveAccountAsPlaylist("Liste B", "http://b.test", "bob", "pb");
        assertTrue(prefs.setActiveProfile(a));
        Prefs restarted = new Prefs(context);
        assertEquals(a, restarted.activeProfileId());
        assertEquals(2, restarted.profileCount());
        assertEquals("http://a.test", restarted.url());
        assertTrue(restarted.profileIds().contains(b));
    }

    @Test public void pairingReceiveAddsPlaylistInsteadOfReplacing() {
        // PIN pairing ("PIN vom Handy eingeben") fills the form and saves via
        // saveAccountAsPlaylist, exactly like the Speichern button.
        Prefs prefs = new Prefs(context);
        String tvList = prefs.saveAccountAsPlaylist("TV Liste", "http://tv.test", "tv", "t");
        String fromPhone = prefs.saveAccountAsPlaylist("Vom Handy", "http://phone.test", "phone", "p");
        assertEquals(2, prefs.profileCount());
        assertEquals(fromPhone, prefs.activeProfileId());
        assertTrue(prefs.profileHasAccount(tvList));
        assertEquals("TV Liste", prefs.profileDisplayName(tvList));
    }

    @Test public void addedButUnsavedPlaylistIsDiscardedAndPreviousRestored() {
        Prefs prefs = new Prefs(context);
        String a = prefs.saveAccountAsPlaylist("Liste A", "http://a.test", "alice", "pa");
        String empty = prefs.createProfile(null);
        assertEquals(empty, prefs.activeProfileId());
        assertFalse(prefs.hasXtream());
        assertTrue(prefs.discardEmptyActiveProfile());
        assertEquals(a, prefs.activeProfileId());
        assertEquals(1, prefs.profileCount());
        assertFalse(prefs.discardEmptyActiveProfile());
    }

    @Test public void filledNewPlaylistIsUsedForTheNextSave() {
        Prefs prefs = new Prefs(context);
        String a = prefs.saveAccountAsPlaylist("Liste A", "http://a.test", "alice", "pa");
        String empty = prefs.createProfile(null);
        String saved = prefs.saveAccountAsPlaylist("Liste B", "http://b.test", "bob", "pb");
        assertEquals(empty, saved);
        assertEquals(2, prefs.profileCount());
        assertTrue(prefs.profileHasAccount(a));
    }

    @Test public void deleteAndRenameSingleNonActivePlaylist() {
        Prefs prefs = new Prefs(context);
        String a = prefs.saveAccountAsPlaylist("Liste A", "http://a.test", "alice", "pa");
        String b = prefs.saveAccountAsPlaylist("Liste B", "http://b.test", "bob", "pb");
        String c = prefs.saveAccountAsPlaylist("Liste C", "http://c.test", "carl", "pc");
        assertTrue(prefs.renameProfile(a, "Wohnzimmer"));
        assertEquals("Wohnzimmer", prefs.profileDisplayName(a));
        assertTrue(prefs.deleteProfile(b));
        assertEquals(2, prefs.profileCount());
        assertEquals(c, prefs.activeProfileId());
        assertTrue(prefs.profileHasAccount(a));
        // a deleted id is never handed out again (old catalog cache must not leak)
        String d = prefs.createProfile(null);
        assertNotEquals(b, d);
    }
}
