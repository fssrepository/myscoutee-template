package com.raxim.myscoutee.domain.access.security;

import java.util.List;

public class AccessPolicy {
    private final String policyKey;
    private final String permission;
    private final String resourceType;
    private final List<AccessPolicyCondition> required;
    private final List<AccessPolicyCondition> anyOf;

    public AccessPolicy(
            String policyKey,
            String permission,
            String resourceType,
            List<AccessPolicyCondition> required,
            List<AccessPolicyCondition> anyOf) {
        this.policyKey = policyKey;
        this.permission = permission;
        this.resourceType = resourceType;
        this.required = required == null ? List.of() : List.copyOf(required);
        this.anyOf = anyOf == null ? List.of() : List.copyOf(anyOf);
    }

    public String policyKey() {
        return policyKey;
    }

    public String permission() {
        return permission;
    }

    public String resourceType() {
        return resourceType;
    }

    public boolean allows(String actorUserId, String subjectUserId, String resourceId, AccessPolicyData data) {
        return allows(new AccessPolicyContext(actorUserId, subjectUserId, permission, resourceType, resourceId), data);
    }

    public boolean allows(AccessPolicyContext context, AccessPolicyData data) {
        for (AccessPolicyCondition condition : required) {
            if (!condition.matches(context, data)) {
                return false;
            }
        }
        if (anyOf.isEmpty()) {
            return true;
        }
        for (AccessPolicyCondition condition : anyOf) {
            if (condition.matches(context, data)) {
                return true;
            }
        }
        return false;
    }
}
