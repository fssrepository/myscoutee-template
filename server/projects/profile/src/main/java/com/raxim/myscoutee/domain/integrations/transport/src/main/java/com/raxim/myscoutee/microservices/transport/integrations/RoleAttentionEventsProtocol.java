package com.raxim.myscoutee.microservices.transport.integrations;

import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;
import com.raxim.myscoutee.microservices.transport.common.ProtocolFields.JobScope;
import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.text;
import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.ids;

import com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents;

public final class RoleAttentionEventsProtocol {
    public static final String ENQUEUE = "/_internal/v1/integrations/role-attention-events/enqueue";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private RoleAttentionEventsProtocol() {}
    public static String[] paths() { return new String[]{ENQUEUE}; }
    private static boolean scope(JobScope value) { return ProtocolFields.boundedJobScope(value); }
    public record Command(JobScope scope, RoleAttentionEvents.RoleAttention notification) {}
    public static boolean valid(Command q) { return q != null && scope(q.scope()) && q.notification() != null && text(q.notification().id(), 512) && text(q.notification().entityType(), 128) && text(q.notification().notificationType(), 128) && ids(q.notification().recipientUserIds()); }
}
