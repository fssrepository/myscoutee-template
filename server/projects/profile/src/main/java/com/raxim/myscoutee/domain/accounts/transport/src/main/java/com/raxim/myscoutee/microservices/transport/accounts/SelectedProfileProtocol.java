package com.raxim.myscoutee.microservices.transport.accounts;

public final class SelectedProfileProtocol {
    public static final String PATH = "/_internal/v1/accounts/selected-profile";
    public static String[] paths() { return new String[]{PATH}; }
    public static final String AUDIENCE = "accounts:selected-profile";
    public static final int MAX_REQUEST_BYTES = 16384;
    private SelectedProfileProtocol() {}
    public record Query(String requestedUserId) {}
    public record Result(Boolean resolved, com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto identity) {}
    public record Error(String code) {}
    public static boolean valid(Query query) {
        return query != null && (query.requestedUserId() == null || query.requestedUserId().length() <= 512);
    }
}
