package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Existing native worker operations, single attempt only; workflow acknowledgment recovery is separate. */
public final class RemoteAccountNotifications extends RemoteReadTransport implements AccountNotifications {
    private final ObjectMapper mapper;
    public RemoteAccountNotifications(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public java.util.List<Recipient> recipients(java.util.Collection<String> profileIds) {
        var command = new AccountNotificationsProtocol.Ids(AccountNotificationsProtocol.Scope.current(), profileIds);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.RECIPIENTS, bytes, new TypeReference<AccountNotificationsProtocol.Recipients>() {},
                "account-notifications.recipients", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> value.values() != null && value.values().size() <= 4096 && value.values().stream().allMatch(java.util.Objects::nonNull));
        return result.values();
    }
    @Override public java.util.List<Sender> senders(java.util.Collection<String> profileIds) {
        var command = new AccountNotificationsProtocol.Ids(AccountNotificationsProtocol.Scope.current(), profileIds);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.SENDERS, bytes, new TypeReference<AccountNotificationsProtocol.Senders>() {},
                "account-notifications.senders", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> value.values() != null && value.values().size() <= 4096 && value.values().stream().allMatch(java.util.Objects::nonNull));
        return result.values();
    }
    @Override public java.util.List<RoleRecipient> roleRecipients(java.util.Collection<String> emails) {
        var command = new AccountNotificationsProtocol.Ids(AccountNotificationsProtocol.Scope.current(), emails);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.ROLE_RECIPIENTS, bytes, new TypeReference<AccountNotificationsProtocol.Roles>() {},
                "account-notifications.role-recipients", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> value.values() != null && value.values().size() <= 4096 && value.values().stream().allMatch(java.util.Objects::nonNull));
        return result.values();
    }
    @Override public String notificationAccountId(String profileId) {
        var command = new AccountNotificationsProtocol.Id(AccountNotificationsProtocol.Scope.current(), profileId);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.NOTIFICATION_ACCOUNT_ID, bytes, new TypeReference<AccountNotificationsProtocol.Text>() {},
                "account-notifications.notification-account-id", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.resolved()) && (value.value() == null || value.value().length() <= 512));
        return result.value();
    }
    @Override public boolean muted(String accountId) {
        var command = new AccountNotificationsProtocol.Id(AccountNotificationsProtocol.Scope.current(), accountId);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.MUTED, bytes, new TypeReference<AccountNotificationsProtocol.Decision>() {},
                "account-notifications.muted", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> value.value() != null);
        return result.value();
    }
    @Override public void setMuted(String accountId, boolean muted) {
        var command = new AccountNotificationsProtocol.Mute(AccountNotificationsProtocol.Scope.current(), accountId, muted);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.SET_MUTED, bytes, new TypeReference<AccountNotificationsProtocol.Receipt>() {},
                "account-notifications.set-muted", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.completed()));
    }
    @Override public void incrementUnreadCounter(String accountId, int delta) {
        var command = new AccountNotificationsProtocol.Increment(AccountNotificationsProtocol.Scope.current(), accountId, delta);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.INCREMENT_UNREAD_COUNTER, bytes, new TypeReference<AccountNotificationsProtocol.Receipt>() {},
                "account-notifications.increment-unread-counter", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.completed()));
    }
    @Override public TokenCleanup removeInvalidPushTokens(java.util.Collection<String> accountIds, java.util.Set<String> invalidTokens) {
        var command = new AccountNotificationsProtocol.Cleanup(AccountNotificationsProtocol.Scope.current(), accountIds, invalidTokens);
        if (!AccountNotificationsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-notifications request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountNotificationsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-notifications request exceeds byte limit");
        var result = exchange(AccountNotificationsProtocol.REMOVE_INVALID_PUSH_TOKENS, bytes, new TypeReference<AccountNotificationsProtocol.CleanupResult>() {},
                "account-notifications.remove-invalid-push-tokens", AccountNotificationsProtocol.MAX_RESPONSE_BYTES, value -> value.removedTokens() != null && value.changedUsers() != null && value.removedTokens() >= 0 && value.changedUsers() >= 0);
        return new TokenCleanup(result.removedTokens(), result.changedUsers());
    }
}
