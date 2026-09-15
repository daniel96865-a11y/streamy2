package app.streamy2;

/** Monotonic playback progress, independent of a decoder's potentially stale playing flag. */
final class LivePlaybackHealth {
    private long lastProgressAt;
    private long lastPosition = -1;
    private long healthySince = -1;
    private boolean progressed;
    void reset(long now) {
        lastProgressAt = now; lastPosition = -1; healthySince = -1; progressed = false;
    }
    boolean stalled(long now, long position, boolean playing) {
        if (position >= 0 && lastPosition >= 0 && Math.abs(position - lastPosition) >= 50) {
            lastProgressAt = now;
            progressed = true;
            if (healthySince < 0) healthySince = now;
        } else if (!playing || now - lastProgressAt >= 1500) {
            healthySince = -1;
        }
        lastPosition = position;
        return now - lastProgressAt >= (progressed ? 8000 : 12000);
    }
    boolean stable(long now) { return healthySince >= 0 && now - healthySince >= 20000 && now - lastProgressAt < 1500; }
}
