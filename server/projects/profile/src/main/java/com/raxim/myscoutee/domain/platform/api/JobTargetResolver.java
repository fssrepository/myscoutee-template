package com.raxim.myscoutee.domain.platform.api;

import java.util.function.Function;

/** An installed owner's existing target lookup, using the shared workspace/user resolution. */
public interface JobTargetResolver {
    String jobTargetType();
    String jobGroupForTarget(String id, Function<String, String> workspaceGroup,
            Function<String, String> userGroup);
}
