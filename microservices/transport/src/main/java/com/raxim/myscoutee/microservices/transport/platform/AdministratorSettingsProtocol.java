package com.raxim.myscoutee.microservices.transport.platform;

import java.util.Collection;

public final class AdministratorSettingsProtocol {
    public static final String EMAILS = "/_internal/v1/platform/administrators/emails";
    public static final String CLASSIFY = "/_internal/v1/platform/administrators/classify";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    private AdministratorSettingsProtocol() {}
    public record Emails(Collection<String> emails) {}
    public record Error(String code) {}
    public static String[] paths() { return new String[]{EMAILS, CLASSIFY}; }
    public static boolean valid(Collection<String> emails) {
        return emails != null && emails.size() <= 4096 && emails.stream().allMatch(value -> value == null || value.length() <= 320);
    }
}
