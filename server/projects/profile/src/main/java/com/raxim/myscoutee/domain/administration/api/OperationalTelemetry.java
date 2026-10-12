package com.raxim.myscoutee.domain.administration.api;

import java.util.Map;

public interface OperationalTelemetry {
    void record(String eventKey, String domain, String entityId, String actorUserId);
    void record(String eventKey, String domain, String entityId, String actorUserId, long amount);
    void record(String eventKey, String domain, String entityId, String actorUserId, long amount, Map<String, String> tags);
}
