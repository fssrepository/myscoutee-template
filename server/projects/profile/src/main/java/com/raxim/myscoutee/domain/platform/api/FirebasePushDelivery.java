package com.raxim.myscoutee.domain.platform.api;

import java.util.ArrayList;
import java.util.List;


public interface FirebasePushDelivery {
    void send(PushMessage message);

    FirebaseBatchSendResult sendAll(List<PushMessage> messages, List<String> tokens);

    FirebaseBatchSendResult sendMulticast(PushMulticast message, List<String> tokens);

    final class DeliveryFailed extends RuntimeException {
        public DeliveryFailed(Throwable cause) { super("Firebase delivery failed", cause); }
    }

    /** Message content prepared by notifications; Firebase SDK instances remain at the platform owner. */
    record PushMessage(String token, String topic, java.util.Map<String, String> data, WebPush webPush) {
        public PushMessage { data = java.util.Map.copyOf(data); }
    }
    record PushMulticast(List<String> tokens, java.util.Map<String, String> data, WebPush webPush) {
        public PushMulticast { tokens = List.copyOf(tokens); data = java.util.Map.copyOf(data); }
    }
    record WebPush(String title, String body, String icon, String badge, String tag,
            @com.fasterxml.jackson.annotation.JsonProperty(required = true)
            @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) long timestampMillis,
            String urgency, String link) {}

    record FirebaseBatchSendResult(@com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) int successCount,
            @com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) int failureCount, List<FirebaseTokenSendResult> responses) {
        public FirebaseBatchSendResult {
            responses = responses == null ? List.of() : List.copyOf(responses);
        }

        public static FirebaseBatchSendResult allSuccessful(int count) {
            List<FirebaseTokenSendResult> responses = new ArrayList<>();
            for (int index = 0; index < Math.max(0, count); index += 1) {
                responses.add(FirebaseTokenSendResult.success());
            }
            return new FirebaseBatchSendResult(Math.max(0, count), 0, responses);
        }
    }

    record FirebaseTokenSendResult(@com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) boolean successful, String errorCode) {
        public static FirebaseTokenSendResult success() {
            return new FirebaseTokenSendResult(true, null);
        }
    }
}
