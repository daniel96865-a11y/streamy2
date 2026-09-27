package app.streamy2;

import java.util.List;
import java.util.Locale;

/**
 * Preferred audio language and automatic audio track choice (Exo and VLC).
 * Guarantees that a stream with audio tracks always gets one: preferred language
 * (by language tag or by label such as "Deutsch"), decodable tracks first, then the
 * stream's default flag, then the first track.
 */
final class AudioPref {
    static final String DE = "de";
    static final String EN = "en";
    static final String TR = "tr";
    static final String PL = "pl";
    static final String AUTO = "auto";

    static final String[] VALUES = {DE, EN, TR, PL, AUTO};
    static final String[] LABELS = {"Deutsch", "Englisch", "Türkisch", "Polnisch", "Original"};

    /** One audio track as seen by the selection logic. */
    static final class Track {
        String language;
        String label;
        boolean supported = true;
        boolean isDefault;
        boolean selected;

        Track() {
        }

        Track(String language, String label, boolean supported, boolean isDefault, boolean selected) {
            this.language = language;
            this.label = label;
            this.supported = supported;
            this.isDefault = isDefault;
            this.selected = selected;
        }
    }

    private AudioPref() {
    }

    static String normalize(String value) {
        for (String v : VALUES) if (v.equals(value)) return v;
        return DE;
    }

    static String label(String value) {
        String v = normalize(value);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(v)) return LABELS[i];
        return LABELS[0];
    }

    private static String[] codes(String pref) {
        switch (normalize(pref)) {
            case EN: return new String[]{"en", "eng"};
            case TR: return new String[]{"tr", "tur"};
            case PL: return new String[]{"pl", "pol"};
            case AUTO: return new String[0];
            default: return new String[]{"de", "deu", "ger", "gsw"};
        }
    }

    private static String[] words(String pref) {
        switch (normalize(pref)) {
            case EN: return new String[]{"english", "englisch", "eng", "en"};
            case TR: return new String[]{"turkce", "türkçe", "türkisch", "turkisch", "turkish", "tur", "tr"};
            case PL: return new String[]{"polski", "polnisch", "polish", "pol", "pl"};
            case AUTO: return new String[0];
            default: return new String[]{"deutsch", "german", "germany", "deutschland", "ger", "deu", "de"};
        }
    }

    /** ISO 639 codes for Exo/VLC ("de" → de, deu, ger). Empty for Original. */
    static String[] languageCodes(String pref) {
        return codes(pref).clone();
    }

    /** Does a track (language tag or label) match the preferred language? */
    static boolean matches(String pref, String language, String label) {
        String[] codes = codes(pref);
        if (codes.length == 0) return false;
        if (language != null) {
            String tag = language.trim().toLowerCase(Locale.US).replace('_', '-');
            int dash = tag.indexOf('-');
            if (dash > 0) tag = tag.substring(0, dash);
            for (String c : codes) if (c.equals(tag)) return true;
        }
        if (label != null) {
            String l = label.toLowerCase(Locale.GERMAN);
            for (String token : l.split("[^\\p{L}\\p{N}]+")) {
                if (token.isEmpty()) continue;
                for (String w : words(pref)) if (w.equals(token)) return true;
            }
        }
        return false;
    }

    /** Index of the track to play, or -1 if there are none. */
    static int choose(List<Track> tracks, String pref) {
        if (tracks == null || tracks.isEmpty()) return -1;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            if (t == null) continue;
            int score = 0;
            if (t.supported) score += 4000;              // audible beats silent
            if (matches(pref, t.language, t.label)) score += 2000;
            if (t.isDefault) score += 100;
            score -= i;                                  // otherwise first track
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /**
     * Should the app pick a track itself?
     * Always when nothing is selected. Otherwise only when the user did not choose manually,
     * a language is preferred, the current track is not in it and a decodable one is.
     */
    static boolean needsPick(List<Track> tracks, String pref, boolean userChose) {
        if (tracks == null || tracks.isEmpty()) return false;
        Track selected = null;
        for (Track t : tracks) if (t != null && t.selected) { selected = t; break; }
        if (selected == null) return true;
        if (userChose || AUTO.equals(normalize(pref))) return false;
        if (matches(pref, selected.language, selected.label)) return false;
        for (Track t : tracks) {
            if (t != null && t != selected && t.supported && matches(pref, t.language, t.label)) return true;
        }
        return false;
    }
}
