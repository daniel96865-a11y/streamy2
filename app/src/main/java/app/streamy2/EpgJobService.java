package app.streamy2;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class EpgJobService extends JobService {
    private JobParameters active;

    @Override public boolean onStartJob(JobParameters params) {
        active = params;
        boolean started = EpgRefresh.request(this, false, error -> {
            if (active == params) {
                active = null;
                jobFinished(params, error != null);
            }
        });
        if (!started) active = null;
        return started;
    }

    @Override public boolean onStopJob(JobParameters params) {
        active = null;
        // The shared refresh may also serve a foreground activity; it can finish its cache write.
        return true;
    }
}
