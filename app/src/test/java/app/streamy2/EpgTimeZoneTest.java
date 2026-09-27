package app.streamy2;

import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/** XMLTV times → absolute instants → shown in German time, around both DST switches. */
public class EpgTimeZoneTest {
    private static String berlin(long ms) {
        return EpgTime.format("dd.MM. HH:mm").format(new Date(ms));
    }

    @Test public void springForward2026() {
        // 29.03.2026: 02:00 CET → 03:00 CEST (01:00 UTC).
        assertEquals("29.03. 01:59", berlin(EpgGuide.parseXmltvTime("20260329005900 +0000")));
        assertEquals("29.03. 03:00", berlin(EpgGuide.parseXmltvTime("20260329010000 +0000")));
        assertEquals("29.03. 03:30", berlin(EpgGuide.parseXmltvTime("20260329033000 +0200")));
        assertEquals("29.03. 01:30", berlin(EpgGuide.parseXmltvTime("20260329013000 +0100")));
    }

    @Test public void fallBack2026() {
        // 25.10.2026: 03:00 CEST → 02:00 CET (01:00 UTC).
        assertEquals("25.10. 02:30", berlin(EpgGuide.parseXmltvTime("20261025003000 +0000"))); // CEST
        assertEquals("25.10. 02:00", berlin(EpgGuide.parseXmltvTime("20261025010000 +0000"))); // CET
        long a = EpgGuide.parseXmltvTime("20261025023000 +0200");
        long b = EpgGuide.parseXmltvTime("20261025023000 +0100");
        assertEquals(3600000L, b - a); // same wall clock twice, one hour apart
    }

    @Test public void offsetsFormatsAndBareTimes() {
        long utc = EpgGuide.parseXmltvTime("20260715180000 +0000");
        assertEquals(utc, EpgGuide.parseXmltvTime("20260715200000 +0200"));
        assertEquals(utc, EpgGuide.parseXmltvTime("20260715200000 +02:00"));
        assertEquals(utc, EpgGuide.parseXmltvTime("20260715200000+0200"));
        assertEquals(utc, EpgGuide.parseXmltvTime("20260715130000 -0500"));
        // Without offset the wall clock is German time (summer: UTC+2, winter: UTC+1).
        assertEquals(utc, EpgGuide.parseXmltvTime("20260715200000"));
        assertEquals(EpgGuide.parseXmltvTime("20261215190000 +0000"), EpgGuide.parseXmltvTime("20261215200000"));
        assertEquals("15.07. 20:00", berlin(utc));
        assertEquals(0L, EpgGuide.parseXmltvTime("kaputt"));
        assertEquals(0L, EpgGuide.parseXmltvTime(null));
    }

    @Test public void displayIgnoresDeviceZone() {
        java.util.TimeZone old = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            assertEquals("15.07. 20:00", berlin(EpgGuide.parseXmltvTime("20260715180000 +0000")));
            assertEquals("15.12. 20:00", berlin(EpgGuide.parseXmltvTime("20261215190000 +0000")));
        } finally {
            java.util.TimeZone.setDefault(old);
        }
    }
}
