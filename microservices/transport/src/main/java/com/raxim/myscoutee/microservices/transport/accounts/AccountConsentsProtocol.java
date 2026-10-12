package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.AccountConsents;

public final class AccountConsentsProtocol {
    public static final String REVISION = "/_internal/v1/accounts/account-consents/revision";
    public static final String LATEST = "/_internal/v1/accounts/account-consents/latest";
    public static final String RECORD = "/_internal/v1/accounts/account-consents/record";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private AccountConsentsProtocol() {}
    public static String[] paths() { return new String[]{REVISION, LATEST, RECORD}; }
    public record Error(String code) {}
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(com.raxim.myscoutee.domain.platform.api.JobGroupContext.isActive(), com.raxim.myscoutee.domain.platform.api.JobGroupContext.groupId()); }
    }
    private static boolean text(String value, int limit) { return value == null || value.length() <= limit; }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    private static boolean ids(java.util.Collection<String> values) { return values != null && values.size() <= 4096 && values.stream().allMatch(value -> text(value, 512)); }
    public record Query(Scope scope, String userId, String revisionId) {}
    public record Save(Scope scope, String userId, String revisionId, Integer version, java.util.List<String> optionalSections, String source) {}
    public record Result(Boolean resolved, com.raxim.myscoutee.domain.accounts.api.AccountConsents.Consent value) {}
    public static boolean valid(Query q) { return q != null && scope(q.scope()) && text(q.userId(),512) && text(q.revisionId(),512); }
    public static boolean valid(Save q) { return q != null && scope(q.scope()) && q.version()!=null && text(q.userId(),512) && text(q.revisionId(),512) && (q.optionalSections()==null || ids(q.optionalSections())) && text(q.source(),8192); }
}
