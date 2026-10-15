package com.raxim.myscoutee.domain.media.service;

import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.media.api.MediaDeletionQueue;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;

@Service
public class MediaDeletionWorker implements MediaDeletionQueue {
    private final MediaDeletionService deletions;
    private final NotificationJobs jobs;
    private final ScheduledMongoSessionRunner databases;
    public MediaDeletionWorker(MediaDeletionService deletions, NotificationJobs jobs, ScheduledMongoSessionRunner databases) {
        this.deletions = deletions; this.jobs = jobs; this.databases = databases;
    }
    @Override public void enqueue(String group, List<String> references) { deletions.enqueue(group, references); }
    @Scheduled(fixedDelayString="${media.deletion-check-delay-ms:10000}")
    public void tick() {
        databases.runAcrossConfiguredDatabases(() -> jobs.forEachGroup(KEY, () -> {
            Instant now = Instant.now();
            if (jobs.beginScheduledRun(KEY, "media.deletion.started", "scheduler", now) != null) run("scheduler", now);
        }));
    }
    @Override public void run(String actor, Instant startedAt) {
        try {
            var result = deletions.process(Instant.now());
            jobs.recordRun(KEY, result.deleted(), result.failed() == 0 ? "completed" : "failed",
                    result.failed() == 0 ? "media.deletion.completed" : "media.deletion.storage.failed", actor, startedAt);
        } catch (RuntimeException failure) {
            jobs.recordRun(KEY, 0, "failed", "media.deletion.failed", actor, startedAt);
        }
    }
}
