package com.raxim.myscoutee.domain.access.security;

import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;
public interface AccessPolicyData {
    boolean canAccessChat(String chatId, String actorUserId);

    boolean canManageSupportChat(String chatId, String actorUserId);

    boolean hasExplicitGrant(
            String subjectUserId,
            String permission,
            String resourceType,
            String resourceId,
            AccessGrantEffect expectedEffect);
}
