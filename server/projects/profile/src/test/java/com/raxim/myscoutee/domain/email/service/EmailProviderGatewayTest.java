package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.raxim.myscoutee.domain.email.config.EmailProperties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Tag("standard")
class EmailProviderGatewayTest {
    @Test void optedOutRecipientSkipsBothConfiguredAndDefaultProviderPaths() {
        var properties = new EmailProperties(); properties.setProvider("sendgrid");
        var provider = mock(EmailProvider.class); when(provider.providerKey()).thenReturn("sendgrid");
        var subscriptions = mock(EmailUnsubscribeService.class);
        var presentation = mock(EmailPresentationService.class);
        var gateway = new EmailProviderGateway(properties, List.of(provider), subscriptions, presentation);
        var message = new EmailSendRequest("qa@example.test", "QA", "Subject", null, "Body", "sender@example.test", "Sender", null, null, Map.of());
        when(subscriptions.isUnsubscribed(message.toEmail())).thenReturn(true);
        assertTrue(gateway.send(message).suppressed());
        assertTrue(gateway.sendConfigured("sendgrid", message, "secret").suppressed());
        verify(provider, never()).send(any()); verify(provider, never()).sendConfigured(any(), any());
        verifyNoInteractions(presentation); verify(subscriptions, never()).link(anyString());
    }
    @Test void allActualSendsReceiveCommonPresentationAndUnsubscribeLink() {
        var properties = new EmailProperties(); properties.setProvider("sendgrid");
        var provider = mock(EmailProvider.class); when(provider.providerKey()).thenReturn("sendgrid");
        var subscriptions = mock(EmailUnsubscribeService.class);
        var presentation = mock(EmailPresentationService.class);
        var gateway = new EmailProviderGateway(properties, List.of(provider), subscriptions, presentation);
        var original = new EmailSendRequest("qa@example.test", "QA", "Subject", null, "Body", "sender@example.test", "Sender", null, null, Map.of());
        var decorated = new EmailSendRequest("qa@example.test", "QA", "Subject", "branded", "Body plus opt-out", "sender@example.test", "Sender", null, null, Map.of());
        when(subscriptions.link(original.toEmail())).thenReturn("https://example.test/#token=private");
        when(presentation.decorate(original, "https://example.test/#token=private")).thenReturn(decorated);
        gateway.send(original); gateway.sendConfigured("sendgrid", original, "secret");
        verify(provider).send(decorated); verify(provider).sendConfigured(decorated, "secret");
    }
}
