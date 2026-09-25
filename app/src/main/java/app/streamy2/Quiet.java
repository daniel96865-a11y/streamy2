package app.streamy2;

import android.util.Log;

/** Lightweight logging for exceptions that are intentionally ignored. */
final class Quiet {
    private Quiet() {}

    /** Logs an ignored exception at WARN level without a stack trace. Never throws. */
    static void ignored(String tag, Throwable error) {
        try {
            Log.w(tag, "Ignored: " + error);
        } catch (Throwable logUnavailable) {
            // android.util.Log is unavailable in plain JVM unit tests; nothing else to do.
        }
    }
}
