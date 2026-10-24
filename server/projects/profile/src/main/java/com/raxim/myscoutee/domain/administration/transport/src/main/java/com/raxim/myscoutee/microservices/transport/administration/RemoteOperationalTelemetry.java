package com.raxim.myscoutee.microservices.transport.administration;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Existing owner operation through bounded scoped HTTP. No automatic retries. */
public final class RemoteOperationalTelemetry extends RemoteReadTransport implements OperationalTelemetry {
    private final ObjectMapper mapper;
    public RemoteOperationalTelemetry(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RemoteOperationalTelemetry.class);
    @Override public void record(String eventKey, String domain, String entityId, String actorUserId) {
        record(eventKey, domain, entityId, actorUserId, 1L, java.util.Map.of());
    }
    @Override public void record(String eventKey, String domain, String entityId, String actorUserId, long amount) {
        record(eventKey, domain, entityId, actorUserId, amount, java.util.Map.of());
    }
    @Override public void record(String eventKey, String domain, String entityId, String actorUserId, long amount, java.util.Map<String, String> tags) {
        var command = new OperationalTelemetryProtocol.Command(OperationalTelemetryProtocol.Scope.current(), eventKey, domain, entityId, actorUserId, amount, tags);
        if (!OperationalTelemetryProtocol.valid(command)) throw new IllegalArgumentException("Invalid operational telemetry command");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > OperationalTelemetryProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Operational telemetry exceeds byte limit");
        try {
            exchange(OperationalTelemetryProtocol.PATH, bytes, new TypeReference<OperationalTelemetryProtocol.Receipt>() {},
                    "operational-telemetry.record", OperationalTelemetryProtocol.MAX_RESPONSE_BYTES,
                    value -> Boolean.TRUE.equals(value.handled()));
        } catch (RemoteReadException unavailable) {
            // Native telemetry is best effort. A lost acknowledgment is not retried or called durable delivery.
            LOGGER.warn("Unable to enqueue remote admin telemetry event {}: {}", eventKey, unavailable.getClass().getSimpleName());
        }
    }
}
