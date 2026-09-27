package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoFitTest {
    private static void assertSize(int w, int h, int[] s) {
        assertEquals("width", w, s[0]);
        assertEquals("height", h, s[1]);
    }

    @Test public void portraitPhoneUsesFullWidth() {
        // 1080x2400 phone in portrait, 16:9 film -> full width, height by aspect.
        assertSize(1080, 608, VideoFit.size(1080, 2400, 1920, 1080, 1f, "fit"));
        assertSize(1080, 810, VideoFit.size(1080, 2400, 640, 480, 1f, "fit"));
    }

    @Test public void landscapePhoneUsesFullHeight() {
        // 2400x1080 (20:9) in landscape: 16:9 fills the height, bars only left/right.
        assertSize(1920, 1080, VideoFit.size(2400, 1080, 1920, 1080, 1f, "fit"));
        // 21:9 cinema scope on the same phone: full width.
        assertSize(2400, 1029, VideoFit.size(2400, 1080, 2560, 1097, 1f, "fit"));
    }

    @Test public void tabletAndAnamorphicSources() {
        assertSize(2560, 1440, VideoFit.size(2560, 1600, 1280, 720, 1f, "fit"));
        // 720x576 PAL with 16:9 pixel aspect -> shown as 16:9.
        assertSize(1920, 1080, VideoFit.size(2400, 1080, 720, 576, 64f / 45f, "fit"));
    }

    @Test public void zoomFillsAndCrops() {
        assertSize(2400, 1350, VideoFit.size(2400, 1080, 1920, 1080, 1f, "zoom"));
        assertEquals(20, VideoFit.cropPercent(2400, 1080, 1920, 1080, 1f));
        assertEquals(100, VideoFit.coveragePercent(2400, 1080, 1920, 1080, 1f, "zoom"));
        assertEquals(80, VideoFit.coveragePercent(2400, 1080, 1920, 1080, 1f, "fit"));
        // Portrait zoom fills the height.
        assertSize(4267, 2400, VideoFit.size(1080, 2400, 1920, 1080, 1f, "zoom"));
    }

    @Test public void stretchAndUnknownSizes() {
        assertSize(2400, 1080, VideoFit.size(2400, 1080, 1920, 1080, 1f, "stretch"));
        assertSize(2400, 1080, VideoFit.size(2400, 1080, 0, 0, 1f, "fit"));
        assertSize(0, 0, VideoFit.size(0, 0, 1920, 1080, 1f, "fit"));
        assertEquals(0, VideoFit.cropPercent(2400, 1080, 0, 0, 1f));
        // Exact aspect match: no bars, no crop.
        assertSize(1920, 1080, VideoFit.size(1920, 1080, 1280, 720, 1f, "fit"));
        assertEquals(0, VideoFit.cropPercent(1920, 1080, 1280, 720, 1f));
    }
}
