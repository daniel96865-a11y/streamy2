package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;

public class PinchZoomTest {
    @Test public void spreadFillsPinchFits() {
        assertEquals("zoom", PinchZoom.decide("fit", 1.4f));
        assertEquals("fit", PinchZoom.decide("zoom", 0.6f));
        assertEquals("zoom", PinchZoom.decide("stretch", 1.2f));
        assertEquals("fit", PinchZoom.decide("stretch", 0.8f));
    }

    @Test public void smallMovementKeepsMode() {
        assertEquals("fit", PinchZoom.decide("fit", 1.05f));
        assertEquals("zoom", PinchZoom.decide("zoom", 0.95f));
        assertEquals("stretch", PinchZoom.decide("stretch", 1.0f));
        assertEquals("fit", PinchZoom.decide(null, 1.0f));
    }

    @Test public void labels() {
        assertEquals("Einpassen", PinchZoom.label("fit"));
        assertEquals("Ausfüllen", PinchZoom.label("zoom"));
        assertEquals("Strecken", PinchZoom.label("stretch"));
        assertEquals("Einpassen", PinchZoom.label(null));
    }
}
