package app.streamy2;

import android.app.Activity;
import android.widget.Toast;
import java.util.Date;

/** Starts a live channel or an archived programme from the programme guide. */
final class LivePlay {
    private LivePlay() {
    }

    static String forceEngine(Prefs prefs, boolean extraLive) {
        String pref = extraLive ? prefs.playerExtraLive() : prefs.playerLive();
        return "vlc".equals(pref) || "exo".equals(pref) ? pref : null;
    }

    static String subtitle(Models.Epg epg) {
        if (epg == null || epg.title == null || epg.title.isEmpty()) return "";
        String s = epg.title;
        if (epg.start > 0 && epg.end > 0) {
            java.text.SimpleDateFormat f = EpgTime.format("HH:mm");
            s = s + "  ·  " + f.format(new Date(epg.start)) + "–" + f.format(new Date(epg.end));
        }
        return s;
    }

    static boolean isExtraLive(Models.Channel c) {
        return c != null && c.extraLiveUrl != null && !c.extraLiveUrl.isEmpty();
    }

    /** Live playback of {@code channel}. */
    static void live(Activity activity, Models.Channel channel, Models.Epg epg) {
        if (activity == null || channel == null || channel.header) return;
        Prefs prefs = new Prefs(activity);
        boolean extra = isExtraLive(channel);
        if (extra && !FeatureAccess.isUnlocked(activity)) {
            Toast.makeText(activity, "Freigabecode erforderlich", Toast.LENGTH_SHORT).show();
            return;
        }
        App.playing = channel;
        String sub = subtitle(epg != null ? epg : channel.epg);
        try {
            if (extra) {
                PlayerActivity.open(activity, channel.extraLiveUrl, channel.extraLiveUrl, channel.name,
                        sub.isEmpty() ? "Live Extra" : sub, true, forceEngine(prefs, true));
            } else {
                PlayerActivity.open(activity, channel.hlsUrl, channel.tsUrl, channel.name, sub, true, forceEngine(prefs, false));
            }
        } catch (Throwable t) {
            Toast.makeText(activity, "Player konnte nicht starten", Toast.LENGTH_SHORT).show();
        }
    }

    /** Can {@code listing} be played from the archive of {@code channel}? */
    static boolean canCatchup(Models.Channel channel, EpgGuide.Listing listing, long now) {
        if (channel == null || listing == null || !channel.archive || isExtraLive(channel) || App.api == null) return false;
        if (listing.start >= now) return false;
        long days = Math.max(1, channel.archiveDays);
        return listing.stop >= now - days * Timeline.DAY;
    }

    /** Archived programme from its beginning. */
    static void catchup(Activity activity, Models.Channel channel, EpgGuide.Listing listing) {
        if (activity == null || channel == null || listing == null) return;
        Prefs prefs = new Prefs(activity);
        App.playing = channel;
        try {
            PlayerActivity.openCatchup(activity, channel.hlsUrl, channel.tsUrl, channel.name,
                    "Archiv · " + Text.clean(listing.title), forceEngine(prefs, false),
                    listing.start, listing.stop, listing.title, listing.desc);
        } catch (Throwable t) {
            Toast.makeText(activity, "Player konnte nicht starten", Toast.LENGTH_SHORT).show();
        }
    }
}
