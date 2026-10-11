package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendResult;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.email.config.EmailProperties;

@Service
public class EmailProviderGateway {
    private final EmailProperties emailProperties;
    private final EmailUnsubscribeService subscriptions;
    private final EmailPresentationService presentation;
    private final Map<String, EmailProvider> providersByKey = new LinkedHashMap<>();

    public EmailProviderGateway(
            EmailProperties emailProperties,
            List<EmailProvider> providers, EmailUnsubscribeService subscriptions, EmailPresentationService presentation) {
        this.emailProperties = emailProperties;
        this.subscriptions = subscriptions; this.presentation = presentation;
        for (EmailProvider provider : providers) {
            if (provider == null || !StringUtils.hasText(provider.providerKey())) {
                continue;
            }
            providersByKey.put(provider.providerKey().trim().toLowerCase(Locale.ROOT), provider);
        }
    }

    public String currentProviderKey() {
        String configured = normalizeProviderKey(emailProperties.getProvider());
        return providersByKey.containsKey(configured) ? configured : "noop";
    }

    public EmailSendResult send(EmailSendRequest request) {
        return deliver(providersByKey.getOrDefault(currentProviderKey(), providersByKey.get("noop")), request, null);
    }

    public EmailSendResult sendConfigured(String providerKey, EmailSendRequest request, String credential) {
        EmailProvider provider = providersByKey.get(normalizeProviderKey(providerKey));
        if (provider == null) return new EmailSendResult(false, null, "Email provider is unavailable.");
        return deliver(provider, request, credential);
    }

    private EmailSendResult deliver(EmailProvider provider, EmailSendRequest request, String credential) {
        if (subscriptions.isUnsubscribed(request.toEmail())) return EmailSendResult.unsubscribed();
        EmailSendRequest decorated = presentation.decorate(request, subscriptions.link(request.toEmail()));
        return credential == null ? provider.send(decorated) : provider.sendConfigured(decorated, credential);
    }

    private String normalizeProviderKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "noop" : normalized;
    }
}
