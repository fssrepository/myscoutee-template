package com.raxim.myscoutee.domain.access.api;

/** Facts shared by authorization and its policy-data adapter. */
public interface AccessPolicyFacts {
    boolean canAccessChat(String chatId, String actorUserId);
    boolean canManageSupportChat(String chatId, String actorUserId);
}
