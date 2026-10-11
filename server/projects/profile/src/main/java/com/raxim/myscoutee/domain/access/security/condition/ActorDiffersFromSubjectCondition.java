package com.raxim.myscoutee.domain.access.security.condition;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.access.security.AccessPolicyCondition;
import com.raxim.myscoutee.domain.access.security.AccessPolicyContext;
import com.raxim.myscoutee.domain.access.security.AccessPolicyData;

@Component
public class ActorDiffersFromSubjectCondition implements AccessPolicyCondition {
    @Override
    public String name() {
        return "actorDiffersFromSubject";
    }

    @Override
    public boolean matches(AccessPolicyContext context, AccessPolicyData data) {
        return StringUtils.hasText(context.actorUserId())
                && StringUtils.hasText(context.subjectUserId())
                && !context.actorUserId().equals(context.subjectUserId());
    }
}
