package com.raxim.myscoutee.domain.email.api;

import java.util.Map;

public record EmailSendRequest(
        String toEmail,
        String toName,
        String subject,
        String htmlBody,
        String textBody,
        String fromEmail,
        String fromName,
        String replyTo,
        String category,
        Map<String, String> metadata) {
}
