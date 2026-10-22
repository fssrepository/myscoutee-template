package com.raxim.myscoutee.microservices.transport.platform;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Notification outbox retains retry decisions; this transport never repeats a provider send. */
public final class RemoteFirebasePushDelivery extends RemoteReadTransport implements FirebasePushDelivery {
    private final ObjectMapper mapper;
    public RemoteFirebasePushDelivery(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public void send(PushMessage message) {
        var command = new FirebasePushDeliveryProtocol.Single(message);
        exchange(FirebasePushDeliveryProtocol.SINGLE, encode(command, FirebasePushDeliveryProtocol.valid(command)),
                new TypeReference<FirebasePushDeliveryProtocol.Receipt>() {}, "firebase-push.single", FirebasePushDeliveryProtocol.MAX_RESPONSE_BYTES,
                value -> Boolean.TRUE.equals(value.completed()));
    }
    @Override public FirebaseBatchSendResult sendAll(List<PushMessage> messages, List<String> tokens) {
        var command = new FirebasePushDeliveryProtocol.Batch(messages, tokens);
        return exchange(FirebasePushDeliveryProtocol.BATCH, encode(command, FirebasePushDeliveryProtocol.valid(command)),
                new TypeReference<FirebaseBatchSendResult>() {}, "firebase-push.batch", FirebasePushDeliveryProtocol.MAX_RESPONSE_BYTES,
                FirebasePushDeliveryProtocol::valid);
    }
    @Override public FirebaseBatchSendResult sendMulticast(PushMulticast message, List<String> tokens) {
        var command = new FirebasePushDeliveryProtocol.Multicast(message, tokens);
        return exchange(FirebasePushDeliveryProtocol.MULTICAST, encode(command, FirebasePushDeliveryProtocol.valid(command)),
                new TypeReference<FirebaseBatchSendResult>() {}, "firebase-push.multicast", FirebasePushDeliveryProtocol.MAX_RESPONSE_BYTES,
                FirebasePushDeliveryProtocol::valid);
    }
    private byte[] encode(Object command, boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid Firebase push request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > FirebasePushDeliveryProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Firebase push request exceeds limit");
        return bytes;
    }
}
