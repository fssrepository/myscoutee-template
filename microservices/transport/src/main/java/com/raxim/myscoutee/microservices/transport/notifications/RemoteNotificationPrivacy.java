package com.raxim.myscoutee.microservices.transport.notifications;

import java.util.*;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.notifications.api.NotificationPrivacy;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The existing account purge job owns retries and stage ordering; transport never retries. */
public final class RemoteNotificationPrivacy extends RemoteReadTransport implements NotificationPrivacy {
    private final ObjectMapper mapper;
    public RemoteNotificationPrivacy(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public void purgeDeliveryData(String userId) {
        var command = new NotificationPrivacyProtocol.UserCommand(NotificationPrivacyProtocol.Scope.current(), userId);
        send(NotificationPrivacyProtocol.PURGE_DELIVERY_DATA, command, NotificationPrivacyProtocol.valid(command));
    }
    @Override public void anonymizeRuleAuthors(String userId) {
        var command = new NotificationPrivacyProtocol.UserCommand(NotificationPrivacyProtocol.Scope.current(), userId);
        send(NotificationPrivacyProtocol.ANONYMIZE_RULE_AUTHORS, command, NotificationPrivacyProtocol.valid(command));
    }
    private void send(String path, Object command, boolean valid) {
        exchange(path, encode(command, valid), new TypeReference<NotificationPrivacyProtocol.Receipt>() {},
                "notification-privacy.command", NotificationPrivacyProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.completed()));
    }
    private byte[] encode(Object command, boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid purge command");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > NotificationPrivacyProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Purge command exceeds byte limit");
        return bytes;
    }
}
