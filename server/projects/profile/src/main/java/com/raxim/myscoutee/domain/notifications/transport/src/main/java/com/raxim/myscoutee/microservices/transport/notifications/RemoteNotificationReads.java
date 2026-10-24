package com.raxim.myscoutee.microservices.transport.notifications;

import com.raxim.myscoutee.domain.notifications.api.NotificationReads;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Bounded scoped call to the existing owner; no automatic retries. */
public final class RemoteNotificationReads extends RemoteReadTransport implements NotificationReads {
    private final ObjectMapper mapper;
    private final com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Signer signer;
    public RemoteNotificationReads(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Signer signer) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper; this.signer = signer;
    }
    @Override public com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsPageResponseDto queryPage(String userId, String bucket, Integer limit, String cursor, String workspace) {
        var command = new NotificationReadsProtocol.Query(NotificationReadsProtocol.Scope.current(), userId, bucket, limit, cursor, workspace);
        if (!NotificationReadsProtocol.valid(command)) throw new IllegalArgumentException("Invalid notification-reads request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > NotificationReadsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("notification-reads request exceeds byte limit");
        var result = ownerExchange(NotificationReadsProtocol.PAGE, bytes, new TypeReference<com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsPageResponseDto>() {},
                "notification-reads.page", NotificationReadsProtocol.MAX_RESPONSE_BYTES, v -> true);
        return result;
    }
    @Override public int currentUnreadCount(String userId) {
        var query = new NotificationReadsProtocol.UnreadQuery(NotificationReadsProtocol.Scope.current(), userId);
        if (!NotificationReadsProtocol.valid(query)) throw new IllegalArgumentException("Invalid notification unread-count request");
        byte[] bytes = mapper.writeValueAsBytes(query);
        return ownerExchange(NotificationReadsProtocol.UNREAD, bytes, new TypeReference<Integer>() {},
                "notification-reads.unread", 16384, value -> value >= 0);
    }
    private <T> T ownerExchange(String path, byte[] bytes, TypeReference<T> type, String operation, int limit,
            java.util.function.Predicate<T> valid) {
        try { return exchangeAuthorized(path, bytes, type, operation, limit, valid,
                    java.util.Map.of(com.raxim.myscoutee.microservices.transport.security.ActorDelegation.HEADER,
                            signer.issueForCurrent(NotificationReadsProtocol.AUDIENCE, "POST", path, bytes))); }
        catch (RemoteReadException failure) {
            if (failure.status() >= 400 && failure.status() < 500 && "OWNER_REJECTED".equals(failure.remoteCode()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(failure.status()));
            throw failure;
        }
    }

}
