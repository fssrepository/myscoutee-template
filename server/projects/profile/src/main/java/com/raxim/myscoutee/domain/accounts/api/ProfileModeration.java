package com.raxim.myscoutee.domain.accounts.api;

/** Moderation commands reload the account-owned document by ID. */
public interface ProfileModeration {
    record SupportNotice(String adminUserId, String adminName, String adminInitials, String text) {}
    boolean block(String userId);
    boolean block(String userId, SupportNotice notice);
    void unblock(String userId);
}
