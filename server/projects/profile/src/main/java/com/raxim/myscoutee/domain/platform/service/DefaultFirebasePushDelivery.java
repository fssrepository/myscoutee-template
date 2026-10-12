package com.raxim.myscoutee.domain.platform.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.raxim.myscoutee.domain.platform.api.FirebaseRuntime;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;

@Service
public class DefaultFirebasePushDelivery implements FirebasePushDelivery {
    private final FirebaseRuntime firebaseRuntime;

    public DefaultFirebasePushDelivery(
            FirebaseRuntime firebaseRuntime) {
        this.firebaseRuntime = firebaseRuntime;
    }

    @Override
    public void send(PushMessage message) {
        try { firebaseRuntime.messaging().send(message(message)); }
        catch (FirebaseMessagingException failed) { throw new DeliveryFailed(failed); }
    }

    @Override
    public FirebasePushDelivery.FirebaseBatchSendResult sendAll(List<PushMessage> messages, List<String> tokens) {
        try { return toResult(firebaseRuntime.messaging().sendEach(messages.stream().map(this::message).toList())); }
        catch (FirebaseMessagingException failed) { throw new DeliveryFailed(failed); }
    }

    @Override
    public FirebasePushDelivery.FirebaseBatchSendResult sendMulticast(PushMulticast message, List<String> tokens) {
        try { return toResult(firebaseRuntime.messaging().sendEachForMulticast(multicast(message))); }
        catch (FirebaseMessagingException failed) { throw new DeliveryFailed(failed); }
    }

    private Message message(PushMessage message) {
        var builder = Message.builder().putAllData(message.data()).setWebpushConfig(webpush(message.webPush(), message.data()));
        if (message.token() != null) builder.setToken(message.token());
        if (message.topic() != null) builder.setTopic(message.topic());
        return builder.build();
    }
    private MulticastMessage multicast(PushMulticast message) {
        return MulticastMessage.builder().addAllTokens(message.tokens()).putAllData(message.data())
                .setWebpushConfig(webpush(message.webPush(), message.data())).build();
    }
    private WebpushConfig webpush(WebPush presentation, java.util.Map<String,String> data) {
        var notification = WebpushNotification.builder().setTitle(presentation.title()).setBody(presentation.body())
                .setIcon(presentation.icon()).setBadge(presentation.badge()).setTag(presentation.tag())
                .setTimestampMillis(presentation.timestampMillis()).build();
        return WebpushConfig.builder().putHeader("Urgency", presentation.urgency()).putAllData(data)
                .setFcmOptions(WebpushFcmOptions.withLink(presentation.link())).setNotification(notification).build();
    }

    private FirebasePushDelivery.FirebaseBatchSendResult toResult(BatchResponse response) {
        if (response == null) {
            return new FirebasePushDelivery.FirebaseBatchSendResult(0, 0, List.of());
        }
        List<FirebasePushDelivery.FirebaseTokenSendResult> responses = response.getResponses() == null
                ? List.of()
                : response.getResponses().stream()
                        .map(this::toResult)
                        .toList();
        return new FirebasePushDelivery.FirebaseBatchSendResult(response.getSuccessCount(), response.getFailureCount(), responses);
    }

    private FirebasePushDelivery.FirebaseTokenSendResult toResult(SendResponse response) {
        if (response == null) {
            return new FirebasePushDelivery.FirebaseTokenSendResult(false, null);
        }
        MessagingErrorCode errorCode = response.getException() == null
                ? null
                : response.getException().getMessagingErrorCode();
        return new FirebasePushDelivery.FirebaseTokenSendResult(response.isSuccessful(), errorCode == null ? null : errorCode.name());
    }
}
