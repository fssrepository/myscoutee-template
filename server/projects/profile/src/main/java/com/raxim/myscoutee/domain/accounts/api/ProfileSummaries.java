package com.raxim.myscoutee.domain.accounts.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileSummaries {
    Optional<? extends ProfileSummary> findByUserId(String userId);
    List<? extends ProfileSummary> findByUserIdIn(Collection<String> userIds);
    long countByUserIdIn(Collection<String> ids);
    List<? extends ProfileSummary> pageByUserIdIn(Collection<String> ids, int offset, int limit);
}
