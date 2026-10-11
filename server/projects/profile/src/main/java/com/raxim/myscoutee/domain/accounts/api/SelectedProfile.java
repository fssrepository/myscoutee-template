package com.raxim.myscoutee.domain.accounts.api;

/** Resolves an actor through the existing authenticated profile-selection rules. */
public interface SelectedProfile {
    com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto resolveSelectedIdentity(String requestedUserId);
    default String resolveSelectedUserId(String requestedUserId) {
        var identity = resolveSelectedIdentity(requestedUserId);
        return identity == null ? null : identity.userId();
    }
}
