package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class AccountNotificationsProtocol {
    public static final String RECIPIENTS = "/_internal/v1/accounts/account-notifications/recipients";
    public static final String SENDERS = "/_internal/v1/accounts/account-notifications/senders";
    public static final String ROLE_RECIPIENTS = "/_internal/v1/accounts/account-notifications/role-recipients";
    public static final String NOTIFICATION_ACCOUNT_ID = "/_internal/v1/accounts/account-notifications/notification-account-id";
    public static final String MUTED = "/_internal/v1/accounts/account-notifications/muted";
    public static final String SET_MUTED = "/_internal/v1/accounts/account-notifications/set-muted";
    public static final String INCREMENT_UNREAD_COUNTER = "/_internal/v1/accounts/account-notifications/increment-unread-counter";
    public static final String REMOVE_INVALID_PUSH_TOKENS = "/_internal/v1/accounts/account-notifications/remove-invalid-push-tokens";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private AccountNotificationsProtocol() {}
    public static String[] paths() { return new String[]{RECIPIENTS, SENDERS, ROLE_RECIPIENTS, NOTIFICATION_ACCOUNT_ID, MUTED, SET_MUTED, INCREMENT_UNREAD_COUNTER, REMOVE_INVALID_PUSH_TOKENS}; }
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(JobGroupContext.isActive(), JobGroupContext.groupId()); }
    }
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean text(String value, int limit) { return value == null || value.length() <= limit; }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    public record Id(Scope scope, String id) {}
    public record Ids(Scope scope, java.util.Collection<String> ids) {}
    public record Mute(Scope scope, String accountId, Boolean muted) {}
    public record Increment(Scope scope, String accountId, Integer delta) {}
    public record Cleanup(Scope scope, java.util.Collection<String> accountIds, java.util.Set<String> invalidTokens) {}
    public record Recipients(java.util.List<AccountNotifications.Recipient> values) {}
    public record Senders(java.util.List<AccountNotifications.Sender> values) {}
    public record Roles(java.util.List<AccountNotifications.RoleRecipient> values) {}
    public record Text(Boolean resolved, String value) {}
    public record Decision(Boolean value) {}
    public record CleanupResult(Integer removedTokens, Integer changedUsers) {
        public CleanupResult(AccountNotifications.TokenCleanup result) { this(result.removedTokens(), result.changedUsers()); }
    }
    private static boolean ids(java.util.Collection<String> values) { return values != null && values.size() <= 4096 && values.stream().allMatch(value -> text(value, 512)); }
    public static boolean valid(Id value) { return value != null && scope(value.scope()) && text(value.id(), 512); }
    public static boolean valid(Ids value) { return value != null && scope(value.scope()) && ids(value.ids()); }
    public static boolean valid(Mute value) { return value != null && scope(value.scope()) && text(value.accountId(), 512) && value.muted() != null; }
    public static boolean valid(Increment value) { return value != null && scope(value.scope()) && text(value.accountId(), 512) && value.delta() != null; }
    public static boolean valid(Cleanup value) { return value != null && scope(value.scope()) && ids(value.accountIds()) && value.invalidTokens() != null
            && value.invalidTokens().size() <= 4096 && value.invalidTokens().stream().allMatch(token -> token != null && text(token, 8192)); }
}
