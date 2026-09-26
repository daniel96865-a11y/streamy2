package app.streamy2;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;

/**
 * Applies the user's accent colour (Einstellungen → Darstellung) to every themed resource.
 * Drawables, layouts and styles reference ?attr/streamyAccent* instead of a fixed blue; the
 * default values in Theme.Streamy are the original blue, other accents are theme overlays.
 */
public final class AccentTheme {
    private AccentTheme() {}

    /** Overlay style for an accent id, or 0 for the default (blue = unchanged resources). */
    static int overlayFor(String accentId) {
        String id = Theme.get(accentId).id;
        switch (id) {
            case "sky": return R.style.ThemeOverlay_Streamy_Accent_Sky;
            case "teal": return R.style.ThemeOverlay_Streamy_Accent_Teal;
            case "violet": return R.style.ThemeOverlay_Streamy_Accent_Violet;
            case "rose": return R.style.ThemeOverlay_Streamy_Accent_Rose;
            case "amber": return R.style.ThemeOverlay_Streamy_Accent_Amber;
            default: return 0;
        }
    }

    /** Call in onCreate() before super.onCreate()/setContentView(). Never throws. */
    static String apply(Activity activity) {
        String id = Theme.ALL[0].id;
        try {
            id = Theme.get(new Prefs(activity).accent()).id;
            int style = overlayFor(id);
            if (style != 0) activity.getTheme().applyStyle(style, true);
        } catch (Throwable t) {
            Quiet.ignored("AccentTheme", t);
        }
        return id;
    }

    /** Resolves a colour attribute from the context theme, falling back to {@code fallback}. */
    static int color(Context context, int attr, int fallback) {
        if (context == null) return fallback;
        TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(new int[]{attr});
            return a.getColor(0, fallback);
        } catch (Throwable t) {
            return fallback;
        } finally {
            if (a != null) {
                try { a.recycle(); } catch (Throwable ignored) { Quiet.ignored("AccentTheme", ignored); }
            }
        }
    }

    static int accent(Context context) {
        return color(context, R.attr.streamyAccent, Theme.ALL[0].color);
    }

    /** ARGB colour with replaced alpha channel. */
    static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Linear blend of two opaque colours (t = share of {@code a}). */
    static int blend(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((b >> 16) & 255) + (((a >> 16) & 255) - ((b >> 16) & 255)) * k);
        int g = Math.round(((b >> 8) & 255) + (((a >> 8) & 255) - ((b >> 8) & 255)) * k);
        int bl = Math.round((b & 255) + ((a & 255) - (b & 255)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
