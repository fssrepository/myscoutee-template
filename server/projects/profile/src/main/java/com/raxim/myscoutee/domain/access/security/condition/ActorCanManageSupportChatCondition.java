package com.raxim.myscoutee.domain.access.security.condition;

import org.springframework.stereotype.Component;

import com.raxim.myscoutee.domain.access.security.AccessPolicyCondition;
import com.raxim.myscoutee.domain.access.security.AccessPolicyContext;
import com.raxim.myscoutee.domain.access.security.AccessPolicyData;

@Component
public class ActorCanManageSupportChatCondition implements AccessPolicyCondition {
    @Override
    public String name() {
        return "actorCanManageSupportChat";
    }

    @Override
    public boolean matches(AccessPolicyContext context, AccessPolicyData data) {
        return data.canManageSupportChat(context.resourceId(), context.actorUserId());
    }
}
