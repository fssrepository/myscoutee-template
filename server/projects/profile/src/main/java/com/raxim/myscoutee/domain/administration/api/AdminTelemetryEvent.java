package com.raxim.myscoutee.domain.administration.api;

import java.util.Map;

public record AdminTelemetryEvent(
        String eventKey,
        String domain,
        String entityId,
        String actorUserId,
        Long amount,
        String occurredAtIso,
        Map<String, String> tags) {
}
