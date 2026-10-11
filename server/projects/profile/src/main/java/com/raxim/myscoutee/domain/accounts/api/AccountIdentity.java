package com.raxim.myscoutee.domain.accounts.api;
/** Account-local principal; remote consumers use the narrower authenticated actor. */
public interface AccountIdentity extends AuthenticatedActor {
    WorkspaceProfile getUser();

    @Override
    default com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto getProfileIdentity() {
        var profile = getUser();
        return profile == null ? null : new com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto(
                profile.getUserId(), profile.getAccountUserId(), profile.getWorkspaceGroupId());
    }
}
