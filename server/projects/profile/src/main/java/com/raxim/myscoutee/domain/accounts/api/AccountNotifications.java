package com.raxim.myscoutee.domain.accounts.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Account-owned notification data and commands; no persistence types cross this boundary. */
public interface AccountNotifications {
    List<Recipient> recipients(Collection<String> profileIds);
    List<Sender> senders(Collection<String> profileIds);
    List<RoleRecipient> roleRecipients(Collection<String> emails);
    String notificationAccountId(String profileId);
    boolean muted(String accountId);
    void setMuted(String accountId, boolean muted);
    void incrementUnreadCounter(String accountId, int delta);
    TokenCleanup removeInvalidPushTokens(Collection<String> accountIds, Set<String> invalidTokens);

    record Device(String deviceId, String token, @com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) boolean enabled) {}
    record Recipient(String userId, String accountUserId, String workspaceGroupId,
            @com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) boolean hasDeliveryLocation, List<Device> devices) {
        public Recipient { devices = List.copyOf(devices); }
    }
    record Sender(String userId, String name, String avatarUrl) {}
    record RoleRecipient(String userId, String email, @com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) boolean operator) {}
    record TokenCleanup(int removedTokens, int changedUsers) {}
}
