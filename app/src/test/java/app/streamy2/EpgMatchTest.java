package app.streamy2;

import android.app.Application;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class EpgMatchTest {

    @Test public void normalizationOfQualityRegionAndUmlauts() {
        assertEquals("rtl", EpgGuide.normName("RTL HD"));
        assertEquals("rtl", EpgGuide.normName("RTL FHD .b"));
        assertEquals("rtl", EpgGuide.normName("DE: RTL"));
        assertEquals("rtl", EpgGuide.normName("|DE| RTL 4K"));
        assertEquals("rtl", EpgGuide.normName("DE | RTL UHD"));
        assertEquals("rtl", EpgGuide.normName("RTL (BACKUP) .c"));
        assertEquals("prosieben", EpgGuide.normName("ProSieben +1"));
        assertEquals("prosieben", EpgGuide.normName("Pro7 HD"));
        assertEquals("sat1", EpgGuide.normName("SAT.1 HD"));
        assertEquals("servus tv", EpgGuide.normName("Servus TV Österreich"));
        assertEquals("tele 5", EpgGuide.normName("Télé 5"));
        assertEquals("kabel eins", EpgGuide.normName("Kabel1 HD"));
        assertEquals("rtlzwei", EpgGuide.normName("RTL 2 FHD"));
        assertEquals("ntv", EpgGuide.normName("n-tv HD"));
        assertEquals("sky cinema premieren 24", EpgGuide.normName("Sky Cinema Premieren +24 HD"));
    }

    @Test public void timeshiftDetection() {
        assertEquals(3600000L, EpgGuide.timeshiftMs("ProSieben +1"));
        assertEquals(3600000L, EpgGuide.timeshiftMs("RTL +1 HD .b"));
        assertEquals(7200000L, EpgGuide.timeshiftMs("Sky Cinema +2"));
        assertEquals(0L, EpgGuide.timeshiftMs("Sky Cinema Premieren +24"));
        assertEquals(0L, EpgGuide.timeshiftMs("SPORT1+"));
        assertEquals(0L, EpgGuide.timeshiftMs("SAT.1 HD+"));
        assertEquals(0L, EpgGuide.timeshiftMs(null));
    }

    private static final SimpleDateFormat XT = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    static { XT.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static String t(long ms) { return XT.format(new Date(ms)); }

    private static File xml(String name, String body) throws Exception {
        File f = new File(RuntimeEnvironment.getApplication().getCacheDir(), name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            // With a UTF-8 BOM like several web XMLTV files.
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" + body + "</tv>").getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static String channel(String id, String name) {
        return "<channel id=\"" + id + "\"><display-name>" + name + "</display-name></channel>";
    }

    private static String prog(String id, long start, long stop, String title) {
        return "<programme start=\"" + t(start) + "\" stop=\"" + t(stop) + "\" channel=\"" + id + "\"><title>" + title + "</title></programme>";
    }

    private static Models.Channel ch(String name, boolean builtin) {
        Models.Channel c = new Models.Channel();
        c.name = name;
        c.id = (builtin ? "extra_live:" : "") + name;
        c.categoryId = builtin ? "extra_live" : "7";
        return c;
    }

    @Test public void sourceSelectionTimeshiftAndNowNext() throws Exception {
        long h = 3600000L;
        long now = System.currentTimeMillis() / 1000L * 1000L;
        long slot = now - (now % h); // current full hour
        File provider = xml("prov.xml",
                channel("rtl.prov", "RTL")
                        + prog("rtl.prov", slot, slot + h, "Anbieter Jetzt")
                        + prog("rtl.prov", slot + h, slot + 2 * h, "Anbieter Danach"));
        File web = xml("web.xml",
                channel("RTL.de", "RTL") + channel("VOX.de", "VOX") + channel("PRO7.de", "ProSieben")
                        + prog("RTL.de", slot, slot + h, "Netz Jetzt")
                        + prog("RTL.de", slot + h, slot + 2 * h, "Netz Danach")
                        + prog("VOX.de", now - 30 * 60000L, now + 30 * 60000L, "Vox Film")
                        // overlapping slot that starts before the film ends must not become "next"
                        + prog("VOX.de", now + 10 * 60000L, now + 70 * 60000L, "Vox Alt")
                        + prog("VOX.de", now + 30 * 60000L, now + 90 * 60000L, "Vox Danach")
                        + prog("PRO7.de", slot - h, slot, "Pro7 Vorher")
                        + prog("PRO7.de", slot, slot + h, "Pro7 Jetzt"));
        EpgGuide g = new EpgGuide();
        g.loadFileMerge(provider, false);
        g.loadFileMerge(web, true);

        g.sourceMode = "auto";
        assertEquals("Anbieter Jetzt", g.forChannel(ch("RTL HD", false)).title);
        assertEquals("Anbieter Danach", g.forChannel(ch("RTL HD", false)).nextTitle);
        assertEquals("Vox Film", g.forChannel(ch("VOX FHD", false)).title); // gap filled from web
        assertEquals("Vox Danach", g.forChannel(ch("VOX FHD", false)).nextTitle);
        // Built-in section always uses the web EPG, even when the provider has the channel.
        assertEquals("Netz Jetzt", g.forChannel(ch("RTL .b", true)).title);

        g.sourceMode = "provider";
        assertEquals("Anbieter Jetzt", g.forChannel(ch("RTL", false)).title);
        assertNull(g.forChannel(ch("VOX", false)));
        assertEquals("Netz Jetzt", g.forChannel(ch("RTL .c", true)).title);

        g.sourceMode = "web";
        assertEquals("Netz Jetzt", g.forChannel(ch("RTL", false)).title);
        assertEquals("Netz Danach", g.forChannel(ch("RTL", false)).nextTitle);

        // Time-shift +1: shows what ran an hour earlier on the main channel.
        g.sourceMode = "auto";
        Models.Epg shifted = g.forChannel(ch("ProSieben +1", true));
        assertEquals("Pro7 Vorher", shifted.title);
        assertEquals(slot, shifted.start);
        assertEquals("Pro7 Jetzt", g.forChannel(ch("ProSieben HD", true)).title);
        List<EpgGuide.Listing> list = g.listingsFor(ch("ProSieben +1", true));
        assertEquals(slot, list.get(0).start);

        // Unified apply keeps built-in, playlist and +1 rows apart.
        java.util.ArrayList<Models.Channel> rows = new java.util.ArrayList<>();
        Models.Channel a = ch("RTL HD", false), b = ch("RTL .s", true), c = ch("ProSieben +1 .b", true), d = ch("ProSieben .b", true);
        rows.add(a); rows.add(b); rows.add(c); rows.add(d);
        g.apply(rows);
        assertEquals("Anbieter Jetzt", a.epg.title);
        assertEquals("Netz Jetzt", b.epg.title);
        assertEquals("Pro7 Vorher", c.epg.title);
        assertEquals("Pro7 Jetzt", d.epg.title);
    }
}
