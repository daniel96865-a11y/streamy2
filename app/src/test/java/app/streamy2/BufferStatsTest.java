package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class BufferStatsTest {
    @Test public void aheadIsNeverNegative() {
        assertEquals(12_000L, BufferStats.aheadMs(42_000L, 30_000L));
        assertEquals(0L, BufferStats.aheadMs(10_000L, 30_000L));
        assertEquals(0L, BufferStats.aheadMs(-1L, 0L));
    }

    @Test public void rateFromBytes() {
        assertEquals(8_000_000L, BufferStats.rateBps(1_000_000L, 1000L));
        assertEquals(4_000_000L, BufferStats.rateBps(1_000_000L, 2000L));
        assertEquals(0L, BufferStats.rateBps(0L, 1000L));
        assertEquals(0L, BufferStats.rateBps(1000L, 0L));
        assertEquals(0L, BufferStats.rateBps(-5L, 1000L));
    }

    @Test public void smoothingKeepsFirstSampleAndDamps() {
        assertEquals(5_000L, BufferStats.smooth(0L, 5_000L));
        assertEquals(6_000L, BufferStats.smooth(5_000L, 7_500L));
        assertEquals(5_000L, BufferStats.smooth(5_000L, -1L));
    }

    @Test public void vlcBitrateConversion() {
        // libVLC: kbit/s = value * 8000
        assertEquals(4_000_000L, BufferStats.vlcBitrateToBps(0.5f));
        assertEquals(0L, BufferStats.vlcBitrateToBps(0f));
        assertEquals(0L, BufferStats.vlcBitrateToBps(Float.NaN));
        assertEquals(0L, BufferStats.vlcBitrateToBps(-1f));
    }

    @Test public void formatSeconds() {
        assertEquals("0,0 s", BufferStats.formatSeconds(0L));
        assertEquals("4,5 s", BufferStats.formatSeconds(4_499L));
        assertEquals("12 s", BufferStats.formatSeconds(12_900L));
        assertEquals("1:05 min", BufferStats.formatSeconds(65_000L));
        assertEquals("0,0 s", BufferStats.formatSeconds(-10L));
    }

    @Test public void formatBitrate() {
        assertEquals("–", BufferStats.formatBitrate(0L));
        assertEquals("850 kbit/s", BufferStats.formatBitrate(850_000L));
        assertEquals("1 kbit/s", BufferStats.formatBitrate(200L));
        assertEquals("1,0 Mbit/s", BufferStats.formatBitrate(999_700L));
        assertEquals("4,8 Mbit/s", BufferStats.formatBitrate(4_830_000L));
        assertEquals("12 Mbit/s", BufferStats.formatBitrate(12_400_000L));
    }

    @Test public void secondaryProgress() {
        assertEquals(500, BufferStats.secondaryProgress(30_000L, 0L, 60_000L, 1000));
        assertEquals(1000, BufferStats.secondaryProgress(90_000L, 0L, 60_000L, 1000));
        assertEquals(0, BufferStats.secondaryProgress(10_000L, 20_000L, 60_000L, 1000));
        assertEquals(0, BufferStats.secondaryProgress(30_000L, 0L, 0L, 1000));
        assertEquals(250, BufferStats.secondaryProgress(1_015_000L, 1_000_000L, 60_000L, 1000));
    }

    @Test public void modes() {
        assertEquals(BufferStats.MODE_HUD, BufferStats.normMode(null));
        assertEquals(BufferStats.MODE_HUD, BufferStats.normMode("garbage"));
        assertEquals(BufferStats.MODE_ALWAYS, BufferStats.normMode("always"));
        assertEquals(BufferStats.MODE_OFF, BufferStats.normMode("off"));
        assertEquals(BufferStats.MODE_ALWAYS, BufferStats.nextMode(BufferStats.MODE_HUD));
        assertEquals(BufferStats.MODE_OFF, BufferStats.nextMode(BufferStats.MODE_ALWAYS));
        assertEquals(BufferStats.MODE_HUD, BufferStats.nextMode(BufferStats.MODE_OFF));
        assertEquals("Mit Bedienleiste", BufferStats.modeLabel("hud"));
        assertEquals("Immer", BufferStats.modeLabel("always"));
        assertEquals("Aus", BufferStats.modeLabel("off"));
    }

    @Test public void exoLineHealthy() {
        String line = BufferStats.exoLine(false, 24_000L, 9_600_000L, 4_000_000L, 0);
        assertEquals("Puffer 24 s · ↓ 9,6 Mbit/s · Stream 4,0 Mbit/s · läuft · 0× nachgeladen", line);
        assertEquals(BufferStats.LEVEL_OK, BufferStats.exoLevel(false, 24_000L, 9_600_000L, 4_000_000L));
    }

    @Test public void exoLineBufferingAndSlow() {
        assertEquals("Puffer 0,8 s · ↓ 2,1 Mbit/s · Puffert… · 3× nachgeladen",
                BufferStats.exoLine(true, 800L, 2_100_000L, 0L, 3));
        assertEquals(BufferStats.LEVEL_BAD, BufferStats.exoLevel(true, 800L, 2_100_000L, 0L));

        String slow = BufferStats.exoLine(false, 6_000L, 3_000_000L, 5_000_000L, 1);
        assertTrue(slow, slow.endsWith("Download zu langsam"));
        assertEquals(BufferStats.LEVEL_WARN, BufferStats.exoLevel(false, 6_000L, 3_000_000L, 5_000_000L));
        // Full buffer: loader idles, low rate is not a problem.
        assertFalse(BufferStats.exoLine(false, 40_000L, 300_000L, 5_000_000L, 0).contains("zu langsam"));
        assertEquals(BufferStats.LEVEL_OK, BufferStats.exoLevel(false, 40_000L, 300_000L, 5_000_000L));
        assertEquals(BufferStats.LEVEL_WARN, BufferStats.exoLevel(false, 1_000L, 0L, 0L));
    }

    @Test public void exoPausedStatus() {
        assertTrue(BufferStats.exoLine(false, true, 10_000L, 0L, 0L, 0).contains("pausiert"));
        assertEquals("Puffert…", BufferStats.status(true, true));
    }

    @Test public void tooSlowTolerance() {
        assertFalse(BufferStats.tooSlow(0L, 5_000_000L));
        assertFalse(BufferStats.tooSlow(5_000_000L, 0L));
        assertFalse(BufferStats.tooSlow(4_800_000L, 5_000_000L));
        assertTrue(BufferStats.tooSlow(4_700_000L, 5_000_000L));
    }

    @Test public void vlcLine() {
        assertEquals("Puffert… 42 % · ↓ 3,2 Mbit/s · 1× nachgeladen",
                BufferStats.vlcLine(42f, 3_200_000L, 0L, 0, 1));
        assertEquals("läuft · ↓ – · Stream 4,0 Mbit/s · 7 Bilder verloren · 0× nachgeladen",
                BufferStats.vlcLine(100f, 0L, 4_000_000L, 7, 0));
        assertTrue(BufferStats.vlcLine(-1f, true, 0L, 0L, 0, 0).startsWith("pausiert"));
        assertEquals(BufferStats.LEVEL_BAD, BufferStats.vlcLevel(10f, 0, 0));
        assertEquals(BufferStats.LEVEL_OK, BufferStats.vlcLevel(100f, 0, 0));
        assertEquals(BufferStats.LEVEL_OK, BufferStats.vlcLevel(-1f, 0, 0));
        assertEquals(BufferStats.LEVEL_WARN, BufferStats.vlcLevel(100f, 5, 2));
    }
}
