package app.streamy2;

import android.app.Application;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlaybackException;
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

    private static ExoPlaybackException decoderInitFailed(Format f) {
        return ExoPlaybackException.createForRenderer(new IllegalStateException("Decoder init failed"),
                "MediaCodecAudioRenderer", 1, f, C.FORMAT_UNSUPPORTED_SUBTYPE, null, false,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }

    @Test public void errorCodesMatchMedia3() {
        assertTrue(AudioPref.isDecoderError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED));
        assertTrue(AudioPref.isDecoderError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED));
        assertTrue(AudioPref.isDecoderError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED));
        assertTrue(AudioPref.isDecoderError(PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED));
        assertFalse(AudioPref.isDecoderError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
        assertFalse(AudioPref.isDecoderError(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW));
    }

    @Test public void failedForcedAc3SwitchesSilentlyToOtherTrack() throws Exception {
        Format ac3 = audio("1", "de", null, MimeTypes.AUDIO_AC3);
        Format aac = audio("2", "de", "Deutsch", MimeTypes.AUDIO_AAC);
        TrackGroup group = new TrackGroup("e", ac3, aac);
        // AC3 playing (forced), then its decoder fails.
        activity.lastExoTracks = tracks(group, new int[]{C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_HANDLED},
                new boolean[]{true, false});
        assertTrue(activity.recoverAudioDecoderError(decoderInitFailed(ac3)));
        assertEquals(AudioPref.RECOVER_OTHER_TRACK, activity.lastAudioRecovery);
        assertEquals(1, (int) player().getTrackSelectionParameters().overrides.get(group).trackIndices.get(0));
        // The failed AC3 track is never force-selected again for this stream.
        Tracks nothing = tracks(group, new int[]{C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_HANDLED},
                new boolean[]{false, false});
        activity.audioForcedKey(null);
        assertTrue(activity.ensureAudioTrack(nothing));
        assertEquals(1, (int) player().getTrackSelectionParameters().overrides.get(group).trackIndices.get(0));
    }

    @Test public void onlyTrackFailsKeepsPictureWithoutMessageAndDoesNotLoop() throws Exception {
        Format ac3 = audio("1", "de", null, MimeTypes.AUDIO_E_AC3);
        TrackGroup group = new TrackGroup("f", ac3);
        Tracks t = tracks(group, new int[]{C.FORMAT_UNSUPPORTED_SUBTYPE}, new boolean[]{true});
        activity.lastExoTracks = t;
        assertTrue(activity.recoverAudioDecoderError(decoderInitFailed(ac3)));
        assertEquals(AudioPref.RECOVER_WITHOUT_AUDIO, activity.lastAudioRecovery); // no VLC fallback here
        assertTrue(player().getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO));
        assertTrue(activity.audioOffByRecovery);
        // No endless forcing of the broken track.
        assertFalse(activity.ensureAudioTrack(tracks(group, new int[]{C.FORMAT_UNSUPPORTED_SUBTYPE}, new boolean[]{false})));
        // Failing again with audio off is a real error -> normal error handling.
        assertFalse(activity.recoverAudioDecoderError(decoderInitFailed(ac3)));
    }

    @Test public void videoDecoderErrorsUseNormalHandling() throws Exception {
        Format h265 = new Format.Builder().setId("v").setSampleMimeType(MimeTypes.VIDEO_H265).build();
        ExoPlaybackException e = ExoPlaybackException.createForRenderer(new IllegalStateException("x"),
                "MediaCodecVideoRenderer", 0, h265, C.FORMAT_HANDLED, null, false,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
        assertFalse(activity.recoverAudioDecoderError(e));
        assertFalse(activity.recoverAudioDecoderError(null));
    }

    @Test public void recoveriesAreLimitedPerStream() throws Exception {
        for (int n = 0; n < 10; n++) {
            Format f = audio("x" + n, "de", null, MimeTypes.AUDIO_AC3);
            TrackGroup g = new TrackGroup("g" + n, f, audio("y" + n, "de", null, MimeTypes.AUDIO_AAC));
            activity.lastExoTracks = tracks(g, new int[]{C.FORMAT_HANDLED, C.FORMAT_HANDLED}, new boolean[]{true, false});
            activity.audioOffByRecovery = false;
            boolean handled = activity.recoverAudioDecoderError(decoderInitFailed(f));
            assertEquals(n < 4, handled);
        }
    }
}
