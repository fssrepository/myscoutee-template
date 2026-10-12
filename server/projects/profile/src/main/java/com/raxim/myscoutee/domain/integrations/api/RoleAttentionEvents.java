package com.raxim.myscoutee.domain.integrations.api;

import java.util.List;

public interface RoleAttentionEvents {
    void enqueueRoleAttention(RoleAttention notification);

    record RoleAttention(String id, String entityType, String notificationType, List<String> recipientUserIds) {
        public RoleAttention { recipientUserIds = List.copyOf(recipientUserIds); }
    }
}
