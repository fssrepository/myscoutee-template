package com.raxim.myscoutee.domain.access.security;

public interface AccessPolicyCondition {
    String name();

    boolean matches(AccessPolicyContext context, AccessPolicyData data);
}
