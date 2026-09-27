package app.streamy2;

import java.util.ArrayList;
import java.util.List;

/**
 * Which EPG sources feed which channels.
 * <ul>
 *   <li>"auto" (default): provider EPG (Xtream xmltv.php / M3U url-tvg / manual URL) first,
 *       gaps filled from the EPG aus dem Netz.</li>
 *   <li>"provider": only the provider EPG for playlist channels.</li>
 *   <li>"web": only the EPG aus dem Netz for playlist channels.</li>
 * </ul>
 * The built-in live section has no provider EPG and always uses the web EPG.
 */
final class EpgSources {
    static final String AUTO = "auto";
    static final String PROVIDER = "provider";
    static final String WEB = "web";

    /** Category id prefix of the built-in live section (see ExtraLiveSource.CAT_ID). */
    static final String BUILTIN_CATEGORY = "extra_live";

    /**
     * Freely accessible XMLTV files, updated daily, gzip. Order = priority for names.
     * DE (two independent feeds for gaps), then AT and CH for the Austrian/Swiss channels.
     */
    static final String[] WEB_URLS = {
            "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz",
            "https://epg.pw/xmltv/epg_DE.xml.gz",
            "https://epgshare01.online/epgshare01/epg_ripper_AT1.xml.gz",
            "https://epgshare01.online/epgshare01/epg_ripper_CH1.xml.gz"
    };

    static final class Source {
        final String url;
        final boolean web;

        Source(String url, boolean web) {
            this.url = url;
            this.web = web;
        }
    }

    private EpgSources() {
    }

    static String normalize(String mode) {
        if (PROVIDER.equals(mode) || WEB.equals(mode)) return mode;
        return AUTO;
    }

    static String label(String mode) {
        String m = normalize(mode);
        if (PROVIDER.equals(m)) return "Vom Anbieter";
        if (WEB.equals(m)) return "Aus dem Netz";
        return "Automatisch";
    }

    static boolean isBuiltin(Models.Channel channel) {
        return channel != null && channel.categoryId != null && channel.categoryId.startsWith(BUILTIN_CATEGORY);
    }

    static boolean allowProvider(String mode, boolean builtin) {
        return !builtin && !WEB.equals(normalize(mode));
    }

    static boolean allowWeb(String mode, boolean builtin) {
        return builtin || !PROVIDER.equals(normalize(mode));
    }

    /**
     * Ordered download plan. Provider first so its names win in "auto".
     * @param builtinPresent the built-in live section is in the list (always needs web EPG)
     */
    static List<Source> plan(String mode, String providerUrl, boolean builtinPresent) {
        List<Source> out = new ArrayList<>();
        String provider = providerUrl == null ? "" : providerUrl.trim();
        if (!provider.isEmpty() && allowProvider(mode, false)) out.add(new Source(provider, false));
        if (allowWeb(mode, false) || builtinPresent) {
            for (String url : WEB_URLS) {
                if (!url.equals(provider)) out.add(new Source(url, true));
            }
        }
        return out;
    }

    static boolean hasBuiltin(List<Models.Channel> live) {
        if (live == null) return true;
        try {
            for (Models.Channel c : new ArrayList<>(live)) {
                if (isBuiltin(c)) return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }
}
