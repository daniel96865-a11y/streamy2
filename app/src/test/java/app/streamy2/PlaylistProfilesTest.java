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
}
