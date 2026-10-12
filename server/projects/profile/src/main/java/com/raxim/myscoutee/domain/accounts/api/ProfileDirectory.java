package com.raxim.myscoutee.domain.accounts.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileDirectory {
    Optional<? extends WorkspaceProfile> findByUserId(String userId);
    WorkspaceProfile findFirstByUserId(String id);
    List<? extends WorkspaceProfile> findByUserIdIn(Collection<String> userIds);
}
