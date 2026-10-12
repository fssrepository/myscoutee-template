package com.raxim.myscoutee.domain.access.security.condition;

import org.springframework.stereotype.Component;
import com.raxim.myscoutee.domain.access.security.AccessPolicyCondition;
import com.raxim.myscoutee.domain.access.security.AccessPolicyContext;
import com.raxim.myscoutee.domain.access.security.AccessPolicyData;
import com.raxim.myscoutee.domain.access.security.MediaPolicyFacts;

@Component
public class ActorCanReadMediaCondition implements AccessPolicyCondition {
    private final MediaPolicyFacts media;

    public ActorCanReadMediaCondition(MediaPolicyFacts media) { this.media = media; }

    @Override public String name() { return "actorCanReadMedia"; }

    @Override public boolean matches(AccessPolicyContext context, AccessPolicyData data) {
        return media.canRead(context.actorUserId(), context.resourceId());
    }
}
