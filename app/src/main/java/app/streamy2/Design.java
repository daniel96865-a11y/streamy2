package app.streamy2;

/**
 * Einstellungen → Darstellung → Design: "Dunkel" (default) or "OLED-Schwarz" (pure black
 * backgrounds, very dark surfaces). Works together with every accent colour.
 */
final class Design {
    static final String DARK = "dark";
    static final String OLED = "oled";
    static final String[] VALUES = {DARK, OLED};
    static final String[] LABELS = {"Dunkel", "OLED-Schwarz"};

    /** Background / surface colours per design (ARGB), mirrored in res/values/colors.xml. */
    static final int DARK_BG = 0xFF131419;
    static final int DARK_CARD = 0xFF1E1F24;
    static final int DARK_ELEVATED = 0xFF2C2D33;
    static final int OLED_BG = 0xFF000000;
    static final int OLED_CARD = 0xFF0B0B0D;
    static final int OLED_ELEVATED = 0xFF18181B;

    private Design() {
    }

    static String normalize(String value) {
        return OLED.equals(value) ? OLED : DARK;
    }

    static String label(String value) {
        return OLED.equals(normalize(value)) ? LABELS[1] : LABELS[0];
    }

    static boolean isOled(String value) {
        return OLED.equals(normalize(value));
    }

    /** Theme overlay for a design, 0 for Dunkel (= Theme.Streamy defaults). */
    static int overlayFor(String value) {
        return isOled(value) ? R.style.ThemeOverlay_Streamy_Design_Oled : 0;
    }

    static int background(String value) {
        return isOled(value) ? OLED_BG : DARK_BG;
    }

    static int card(String value) {
        return isOled(value) ? OLED_CARD : DARK_CARD;
    }

    static int elevated(String value) {
        return isOled(value) ? OLED_ELEVATED : DARK_ELEVATED;
    }
}
