package app.streamy2;

import android.app.Application;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** The Exo side: an unselected (e.g. "unsupported" per metadata) audio track gets selected. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AudioAutoSelectTest {
    private ActivityController<PlayerActivity> controller;
    private PlayerActivity activity;

    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("streamy2", 0).edit().clear().commit();
        controller = Robolectric.buildActivity(PlayerActivity.class);
        controller.create().start().resume();
        activity = controller.get();
    }

    @After public void cleanup() {
        controller.pause().stop().destroy();
    }

    private ExoPlayer player() throws Exception {
        Field f = PlayerActivity.class.getDeclaredField("player");
        f.setAccessible(true);
        return (ExoPlayer) f.get(activity);
    }

    private static Tracks tracks(TrackGroup group, int[] support, boolean[] selected) {
        return new Tracks(ImmutableList.of(new Tracks.Group(group, false, support, selected)));
    }

    private static Format audio(String id, String lang, String label, String mime) {
        return new Format.Builder().setId(id).setSampleMimeType(mime).setLanguage(lang).setLabel(label)
                .setChannelCount(2).setSampleRate(48000).build();
    }

    @Test public void lonelyUnselectedTrackIsForcedOnce() throws Exception {
        TrackGroup group = new TrackGroup("a", audio("1", "de", null, MimeTypes.AUDIO_AC3));
        Tracks t = tracks(group, new int[]{C.FORMAT_UNSUPPORTED_SUBTYPE}, new boolean[]{false});
        assertTrue(activity.ensureAudioTrack(t));
        TrackSelectionOverride o = player().getTrackSelectionParameters().overrides.get(group);
        assertNotNull(o);
        assertEquals(0, (int) o.trackIndices.get(0));
        // Same stream/track again: no endless re-selection.
        assertFalse(activity.ensureAudioTrack(t));
        assertEquals(1, activity.audioAutoPicks);
    }

    @Test public void germanLabelWithoutTagWins() throws Exception {
        TrackGroup group = new TrackGroup("b",
                audio("1", null, "English", MimeTypes.AUDIO_AAC),
                audio("2", null, "Deutsch", MimeTypes.AUDIO_AAC));
        Tracks t = tracks(group, new int[]{C.FORMAT_HANDLED, C.FORMAT_HANDLED}, new boolean[]{false, false});
        assertTrue(activity.ensureAudioTrack(t));
        assertEquals(1, (int) player().getTrackSelectionParameters().overrides.get(group).trackIndices.get(0));
    }

    @Test public void selectedTrackAndManualChoiceAreKept() throws Exception {
        TrackGroup group = new TrackGroup("c",
                audio("1", "en", null, MimeTypes.AUDIO_AAC),
                audio("2", "de", null, MimeTypes.AUDIO_AAC));
        Tracks englishPlaying = tracks(group, new int[]{C.FORMAT_HANDLED, C.FORMAT_HANDLED}, new boolean[]{true, false});
        activity.userAudioChoice = true;
        assertFalse(activity.ensureAudioTrack(englishPlaying));
        activity.userAudioChoice = false;
        assertTrue(activity.ensureAudioTrack(englishPlaying)); // German preferred by default
        new Prefs(activity).setAudioLanguage("en");
        Tracks germanPlaying = tracks(new TrackGroup("d",
                audio("1", "en", null, MimeTypes.AUDIO_AAC),
                audio("2", "de", null, MimeTypes.AUDIO_AAC)),
                new int[]{C.FORMAT_HANDLED, C.FORMAT_HANDLED}, new boolean[]{false, true});
        assertTrue(activity.ensureAudioTrack(germanPlaying));
    }
}
