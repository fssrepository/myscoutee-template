package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendResult;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NoopEmailProvider implements EmailProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoopEmailProvider.class);

    @Override
    public String providerKey() {
        return "noop";
    }

    @Override
    public EmailSendResult send(EmailSendRequest request) {
        if (request == null || !StringUtils.hasText(request.toEmail())) {
            return new EmailSendResult(false, null, "Missing recipient email.");
        }
        LOGGER.info("NOOP email accepted for {} with subject '{}'.", request.toEmail().trim(), safeText(request.subject()));
        return new EmailSendResult(true, "noop-" + UUID.randomUUID(), null);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
