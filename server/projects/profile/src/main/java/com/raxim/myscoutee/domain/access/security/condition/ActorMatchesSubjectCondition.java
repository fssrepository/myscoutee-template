package com.raxim.myscoutee.domain.access.security.condition;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.access.security.AccessPolicyCondition;
import com.raxim.myscoutee.domain.access.security.AccessPolicyContext;
import com.raxim.myscoutee.domain.access.security.AccessPolicyData;

@Component
public class ActorMatchesSubjectCondition implements AccessPolicyCondition {
    @Override
    public String name() {
        return "actorMatchesSubject";
    }

    @Override
    public boolean matches(AccessPolicyContext context, AccessPolicyData data) {
        return StringUtils.hasText(context.actorUserId())
                && context.actorUserId().equals(context.subjectUserId());
    }
}
