package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.transport.platform.JobAudienceProtocol;
import com.raxim.myscoutee.microservices.transport.common.RemoteCommandTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The outbox owner retains collapse, grouping, persistence and delivery policy. No automatic retries. */
public final class RemoteNotificationDelivery extends RemoteCommandTransport implements NotificationDelivery {
    private final ObjectMapper mapper;
    public RemoteNotificationDelivery(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public Optional<? extends Receipt> enqueue(NotificationIntent intent) { return send(NotificationDeliveryProtocol.ENQUEUE, null, intent); }
    @Override public Optional<? extends Receipt> enqueueOnce(String operationId, NotificationIntent intent) { return send(NotificationDeliveryProtocol.ONCE, operationId, intent); }
    private Optional<? extends Receipt> send(String path, String operationId, NotificationIntent intent) {
        if (intent == null) return Optional.empty(); // Canonical outbox has no side effects for null intents.
        var command = new NotificationDeliveryProtocol.Command(new JobAudienceProtocol.Context(JobGroupContext.isActive(), JobGroupContext.groupId()), operationId, intent);
        if (!NotificationDeliveryProtocol.valid(command)) throw new IllegalArgumentException("Notification intent exceeds transport limits");
        byte[] body = mapper.writeValueAsBytes(command);
        if (body.length > NotificationDeliveryProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Notification intent exceeds transport limits");
        var result = producerCommand(path, body, new TypeReference<NotificationDeliveryProtocol.Result>() {},
                "notifications.outbox", 16384, value -> Boolean.TRUE.equals(value.completed())
                        && (value.receipt() == null || value.receipt().id() != null && !value.receipt().id().isBlank()), operationId);
        return Optional.ofNullable(result.receipt());
    }
}
