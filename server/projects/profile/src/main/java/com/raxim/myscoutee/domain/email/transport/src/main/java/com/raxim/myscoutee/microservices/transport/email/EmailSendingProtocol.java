package com.raxim.myscoutee.microservices.transport.email;

import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;
import com.raxim.myscoutee.microservices.transport.common.ProtocolFields.JobScope;
import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.text;
import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.ids;

import com.raxim.myscoutee.domain.email.api.EmailSending;

public final class EmailSendingProtocol {
    public static final String ENABLED = "/_internal/v1/email/email-sending/enabled";
    public static final String SEND = "/_internal/v1/email/email-sending/send";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private EmailSendingProtocol() {}
    public static String[] paths() { return new String[]{ENABLED, SEND}; }
    private static boolean scope(JobScope value) { return ProtocolFields.boundedJobScope(value); }
    public record Query(JobScope scope) {}
    public record Decision(Boolean value) {}
    public record Send(JobScope scope, com.raxim.myscoutee.domain.email.api.EmailSendRequest content) {}
    public static boolean valid(Query q) { return q != null && scope(q.scope()); }
    public static boolean valid(Send q) { return q != null && scope(q.scope()) && q.content()!=null; }
}
