package com.raxim.myscoutee.domain.accounts.api;

import java.util.List;

public interface AccountMediaVisibility {
    Profile profile(String userId);
    record Profile(String profileStatus, String accountUserId, String workspaceGroupId) {}
}
