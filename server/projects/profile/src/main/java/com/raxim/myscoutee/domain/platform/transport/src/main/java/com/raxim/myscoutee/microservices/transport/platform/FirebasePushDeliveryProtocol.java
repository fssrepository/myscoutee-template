package com.raxim.myscoutee.microservices.transport.platform;

import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.text;

import java.util.List;
import java.util.Map;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.*;

public final class FirebasePushDeliveryProtocol {
    public static final String SINGLE = "/_internal/v1/platform/firebase-push/single";
    public static final String BATCH = "/_internal/v1/platform/firebase-push/batch";
    public static final String MULTICAST = "/_internal/v1/platform/firebase-push/multicast";
    public static final int MAX_REQUEST_BYTES = 8 * 1024 * 1024, MAX_RESPONSE_BYTES = 512 * 1024;
    private FirebasePushDeliveryProtocol() {}
    public static String[] paths() { return new String[]{SINGLE, BATCH, MULTICAST}; }
    public record Single(PushMessage message) {}
    public record Batch(List<PushMessage> messages, List<String> tokens) {}
    public record Multicast(PushMulticast message, List<String> tokens) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean token(String value) { return value != null && !value.isBlank() && text(value, 8192); }
    private static boolean tokens(List<String> values) { return values != null && !values.isEmpty() && values.size() <= 500 && values.stream().allMatch(FirebasePushDeliveryProtocol::token); }
    private static boolean data(Map<String,String> value) {
        return value != null && value.size() <= 128 && value.entrySet().stream()
                .allMatch(entry -> entry.getKey() != null && text(entry.getKey(), 256) && entry.getValue() != null && text(entry.getValue(), 8192));
    }
    private static boolean webpush(WebPush value) {
        return value != null && text(value.title(), 8192) && text(value.body(), 8192) && text(value.icon(), 8192)
                && text(value.badge(), 8192) && text(value.tag(), 8192) && text(value.urgency(), 128) && text(value.link(), 8192);
    }
    private static boolean message(PushMessage value) {
        return value != null && ((token(value.token()) && value.topic() == null) || (token(value.topic()) && value.token() == null))
                && data(value.data()) && webpush(value.webPush());
    }
    public static boolean valid(Single value) { return value != null && message(value.message()); }
    public static boolean valid(Batch value) {
        return value != null && value.messages() != null && tokens(value.tokens()) && value.messages().size() == value.tokens().size()
                && value.messages().stream().allMatch(FirebasePushDeliveryProtocol::message)
                && value.messages().stream().map(PushMessage::token).toList().equals(value.tokens());
    }
    public static boolean valid(Multicast value) {
        return value != null && value.message() != null && tokens(value.tokens()) && value.tokens().equals(value.message().tokens())
                && data(value.message().data()) && webpush(value.message().webPush());
    }
    public static boolean valid(FirebaseBatchSendResult value) {
        return value != null && value.successCount() >= 0 && value.failureCount() >= 0 && value.successCount() <= 500 && value.failureCount() <= 500
                && value.responses() != null && value.responses().size() <= 500
                && value.responses().stream().allMatch(result -> result != null && text(result.errorCode(), 128));
    }
}
