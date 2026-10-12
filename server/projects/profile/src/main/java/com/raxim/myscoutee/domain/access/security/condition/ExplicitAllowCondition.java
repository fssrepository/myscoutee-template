package com.raxim.myscoutee.domain.access.security.condition;

import org.springframework.stereotype.Component;

import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;
import com.raxim.myscoutee.domain.access.security.AccessPolicyCondition;
import com.raxim.myscoutee.domain.access.security.AccessPolicyContext;
import com.raxim.myscoutee.domain.access.security.AccessPolicyData;

@Component
public class ExplicitAllowCondition implements AccessPolicyCondition {
    @Override
    public String name() {
        return "explicitAllow";
    }

    @Override
    public boolean matches(AccessPolicyContext context, AccessPolicyData data) {
        return data.hasExplicitGrant(
                context.subjectUserId(),
                context.permission(),
                context.resourceType(),
                context.resourceId(),
                AccessGrantEffect.ALLOW);
    }
}
