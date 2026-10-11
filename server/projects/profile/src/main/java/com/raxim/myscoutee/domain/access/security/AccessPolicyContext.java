package com.raxim.myscoutee.domain.access.security;

public record AccessPolicyContext(
        String actorUserId,
        String subjectUserId,
        String permission,
        String resourceType,
        String resourceId) {
}
