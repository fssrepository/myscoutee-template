package com.raxim.myscoutee.domain.administration.api;

/** Stateless profile classification. It does not establish an authenticated administrator identity. */
public interface AdministratorClassification {
    default boolean isAdminUser(com.raxim.myscoutee.domain.accounts.api.ProfileSummary user) {
        if (user == null) return false;
        return "admin".equalsIgnoreCase(text(user.getHostTier()))
                || "admin workspace".equalsIgnoreCase(text(user.getStatusText()))
                || java.util.List.of("admin-demo-ava", "admin-demo-noel").contains(text(user.getUserId()));
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
