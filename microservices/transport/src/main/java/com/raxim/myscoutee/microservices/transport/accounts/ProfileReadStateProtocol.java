package com.raxim.myscoutee.microservices.transport.accounts;

import java.util.Collection;

public final class ProfileReadStateProtocol {
    public static final String COUNTERS = "/_internal/v1/accounts/profile-read-state/counters";
    public static final String AUDIENCE = "accounts:profile-read-state";
    public static final int MAX_REQUEST_BYTES = 2097152;
    private ProfileReadStateProtocol() {}
    public static String[] paths() { return new String[]{COUNTERS}; }
    public record CounterQuery(String userId) {}
    public record Error(String code) {}
    public static boolean valid(CounterQuery query) { return query != null && id(query.userId()); }

    private static boolean id(String value) { return value == null || value.length() <= 512; }
}
