package app.streamy2;

import static org.junit.Assert.*;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** 3.81: VOD time labels under the seekbar (position left, duration right, same format). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34}, application = Application.class)
public class PlayerTimeLabelTest {

    @Test
    public void longMovieUsesHoursForPositionToo() {
        String[] l = PlayerActivity.vodTimeLabels(52_000L, (1 * 3600 + 50 * 60 + 40) * 1000L);
        assertEquals("0:00:52", l[0]);
        assertEquals("1:50:40", l[1]);
    }

    @Test
    public void shortClipStaysMinutesSeconds() {
        String[] l = PlayerActivity.vodTimeLabels(65_000L, 20 * 60_000L);
        assertEquals("01:05", l[0]);
        assertEquals("20:00", l[1]);
    }

    @Test
    public void unknownDurationKeepsPlaceholder() {
        String[] l = PlayerActivity.vodTimeLabels(30_000L, 0L);
        assertEquals("00:30", l[0]);
        assertEquals("--:--", l[1]);
        String[] h = PlayerActivity.vodTimeLabels(3_700_000L, 0L);
        assertEquals("1:01:40", h[0]);
    }

    @Test
    public void negativeValuesClampToZero() {
        assertEquals("0:00:00", PlayerActivity.vodTimeLabels(-5L, 7_200_000L)[0]);
    }
}
