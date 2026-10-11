package com.raxim.myscoutee.domain.email.api;

public record EmailSendResult(
        boolean accepted,
        String providerMessageId,
        String errorMessage,
        boolean outcomeUnknown,
        boolean retryable,
        long retryAfterSeconds,
        boolean suppressed) {
    public EmailSendResult(boolean accepted, String providerMessageId, String errorMessage) {
        this(accepted, providerMessageId, errorMessage, false, false, 0, false);
    }

    public static EmailSendResult unknown(String errorMessage) {
        return new EmailSendResult(false, null, errorMessage, true, false, 0, false);
    }

    public static EmailSendResult retryable(String errorMessage, long retryAfterSeconds) {
        return new EmailSendResult(false, null, errorMessage, false, true, Math.max(0, retryAfterSeconds), false);
    }

    public static EmailSendResult unsubscribed() {
        return new EmailSendResult(false, null, "Recipient unsubscribed.", false, false, 0, true);
    }
}
