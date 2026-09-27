package app.streamy2;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioPrefTest {
    private static AudioPref.Track t(String lang, String label, boolean supported, boolean def, boolean selected) {
        return new AudioPref.Track(lang, label, supported, def, selected);
    }

    @Test public void germanByTagAndLabel() {
        for (String tag : new String[]{"de", "deu", "ger", "de-DE", "DE", "de_AT"}) {
            assertTrue(tag, AudioPref.matches("de", tag, null));
        }
        assertTrue(AudioPref.matches("de", null, "Deutsch"));
        assertTrue(AudioPref.matches("de", "und", "Audio German Stereo"));
        assertTrue(AudioPref.matches("de", null, "Track 1 - [Deutsch]"));
        assertFalse(AudioPref.matches("de", "en", "English"));
        assertFalse(AudioPref.matches("de", null, "Dolby Digital"));
        assertFalse(AudioPref.matches("de", null, "Deluxe"));
        assertTrue(AudioPref.matches("en", "eng", null));
        assertTrue(AudioPref.matches("en", null, "Englisch"));
        assertFalse(AudioPref.matches("auto", "de", "Deutsch"));
    }

    @Test public void prefersLanguageThenFallsBackToFirst() {
        List<AudioPref.Track> tracks = Arrays.asList(
                t("en", "English", true, true, false),
                t(null, "Deutsch", true, false, false));
        assertEquals(1, AudioPref.choose(tracks, "de"));
        assertEquals(0, AudioPref.choose(tracks, "en"));
        assertEquals(0, AudioPref.choose(tracks, "auto")); // default flag
        List<AudioPref.Track> noGerman = Arrays.asList(
                t("fr", null, true, false, false),
                t("it", null, true, false, false));
        assertEquals(0, AudioPref.choose(noGerman, "de"));
        assertEquals(-1, AudioPref.choose(Arrays.<AudioPref.Track>asList(), "de"));
    }

    @Test public void decodableBeatsSilentPreferredLanguage() {
        List<AudioPref.Track> tracks = Arrays.asList(
                t("de", "Deutsch AC3", false, true, false),
                t("en", "English", true, false, false));
        assertEquals(1, AudioPref.choose(tracks, "de"));
        // A single track is taken even if it looks undecodable: always audio if possible.
        assertEquals(0, AudioPref.choose(Arrays.asList(t("de", null, false, false, false)), "de"));
    }

    @Test public void noDefaultFlagAndNothingSelectedAlwaysPicks() {
        List<AudioPref.Track> tracks = Arrays.asList(t("de", null, true, false, false));
        assertTrue(AudioPref.needsPick(tracks, "de", false));
        assertTrue(AudioPref.needsPick(tracks, "auto", true)); // nothing selected → pick even after manual choice
        assertEquals(0, AudioPref.choose(tracks, "de"));
    }

    @Test public void wrongLanguageFixedUnlessUserChose() {
        List<AudioPref.Track> tracks = Arrays.asList(
                t("en", null, true, true, true),
                t("de", null, true, false, false));
        assertTrue(AudioPref.needsPick(tracks, "de", false));
        assertFalse(AudioPref.needsPick(tracks, "de", true));
        assertFalse(AudioPref.needsPick(tracks, "auto", false));
        assertFalse(AudioPref.needsPick(tracks, "en", false));
        // German only as undecodable track: keep the audible one.
        List<AudioPref.Track> silentGerman = Arrays.asList(
                t("en", null, true, true, true),
                t("de", null, false, false, false));
        assertFalse(AudioPref.needsPick(silentGerman, "de", false));
        assertFalse(AudioPref.needsPick(Arrays.<AudioPref.Track>asList(), "de", false));
    }

    @Test public void settingValues() {
        assertEquals("de", AudioPref.normalize(null));
        assertEquals("de", AudioPref.normalize("xx"));
        assertEquals("Original", AudioPref.label("auto"));
        assertEquals("Deutsch", AudioPref.label("de"));
        assertArrayEquals(new String[]{"de", "deu", "ger", "gsw"}, AudioPref.languageCodes("de"));
        assertEquals(0, AudioPref.languageCodes("auto").length);
    }

    private static AudioPref.Track failed(AudioPref.Track t) {
        t.failed = true;
        return t;
    }

    @Test public void failedTracksAreNeverChosenAgain() {
        List<AudioPref.Track> tracks = Arrays.asList(
                failed(t("de", null, false, true, false)),   // AC3 whose decoder failed
                t("en", null, true, false, false));
        assertEquals(1, AudioPref.choose(tracks, "de"));
        assertTrue(AudioPref.needsPick(tracks, "de", false));
        List<AudioPref.Track> allFailed = Arrays.asList(failed(t("de", null, false, true, false)));
        assertEquals(-1, AudioPref.choose(allFailed, "de"));
        assertFalse(AudioPref.needsPick(allFailed, "de", false)); // no loop over broken tracks
        // Selected English, German exists but failed: keep English.
        List<AudioPref.Track> germanFailed = Arrays.asList(
                t("en", null, true, false, true),
                failed(t("de", null, true, false, false)));
        assertFalse(AudioPref.needsPick(germanFailed, "de", false));
    }

    @Test public void decoderErrorRecovery() {
        assertTrue(AudioPref.isDecoderError(4001)); // ERROR_CODE_DECODER_INIT_FAILED
        assertTrue(AudioPref.isDecoderError(4005));
        assertTrue(AudioPref.isDecoderError(5001)); // ERROR_CODE_AUDIO_TRACK_INIT_FAILED
        assertFalse(AudioPref.isDecoderError(2001)); // network
        assertFalse(AudioPref.isDecoderError(3001)); // parsing
        List<AudioPref.Track> other = Arrays.asList(
                failed(t("de", null, false, true, false)),
                t("de", "Deutsch AAC", true, false, false));
        assertEquals(AudioPref.RECOVER_OTHER_TRACK, AudioPref.recoveryAction(other, "de", true, false));
        List<AudioPref.Track> none = Arrays.asList(failed(t("de", null, false, true, false)));
        assertEquals(AudioPref.RECOVER_VLC, AudioPref.recoveryAction(none, "de", true, false));
        assertEquals(AudioPref.RECOVER_WITHOUT_AUDIO, AudioPref.recoveryAction(none, "de", false, false));
        assertEquals(AudioPref.RECOVER_WITHOUT_AUDIO,
                AudioPref.recoveryAction(Arrays.<AudioPref.Track>asList(), "de", false, false));
        // Audio already off and it still fails: a real error, shown to the user.
        assertEquals(AudioPref.RECOVER_NONE, AudioPref.recoveryAction(none, "de", true, true));
    }
}
