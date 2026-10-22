package com.raxim.myscoutee.domain.access.security;

import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;
public interface AccessPolicyData extends com.raxim.myscoutee.domain.access.api.AccessPolicyFacts {


    boolean hasExplicitGrant(
            String subjectUserId,
            String permission,
            String resourceType,
            String resourceId,
            AccessGrantEffect expectedEffect);
}
