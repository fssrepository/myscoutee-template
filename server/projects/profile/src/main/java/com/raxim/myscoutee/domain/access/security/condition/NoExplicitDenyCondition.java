package com.raxim.myscoutee.domain.access.security.condition;

import org.springframework.stereotype.Component;

import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;
import com.raxim.myscoutee.domain.access.security.AccessPolicyCondition;
import com.raxim.myscoutee.domain.access.security.AccessPolicyContext;
import com.raxim.myscoutee.domain.access.security.AccessPolicyData;

@Component
public class NoExplicitDenyCondition implements AccessPolicyCondition {
    @Override
    public String name() {
        return "noExplicitDeny";
    }

    @Override
    public boolean matches(AccessPolicyContext context, AccessPolicyData data) {
        return !data.hasExplicitGrant(
                context.subjectUserId(),
                context.permission(),
                context.resourceType(),
                context.resourceId(),
                AccessGrantEffect.DENY);
    }
}
