package com.raxim.myscoutee.domain.email.api;

public interface EmailSending {
    boolean enabled();
    EmailSendResult send(EmailSendRequest content);
}
