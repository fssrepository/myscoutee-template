package com.raxim.myscoutee.domain.media.api;

import java.time.Instant;
import java.util.List;

/** Durable deletion intent. Callers own the decision that a reference is no longer used. */
public interface MediaDeletionQueue {
    String KEY = "media-deletion";
    void enqueue(String baseGroupId, List<String> references);
    void run(String actor, Instant startedAt);
}
