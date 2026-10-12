package com.raxim.myscoutee.microservices.transport.help;

import java.util.*;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class HelpPrivacyProtocol {
    public static final String ANONYMIZE_AUTHORS = "/_internal/v1/help/help-privacy/anonymize-authors";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private HelpPrivacyProtocol() {}
    public static String[] paths() { return new String[]{ANONYMIZE_AUTHORS}; }
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(JobGroupContext.isActive(), JobGroupContext.groupId()); }
    }
    public record UserCommand(Scope scope, String userId) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean id(String value) { return value != null && !value.isBlank() && value.length() <= 512; }
    private static boolean scope(Scope value) {
        return value != null && value.active() != null && (value.groupId() == null || id(value.groupId()))
                && (value.active() || value.groupId() == null);
    }
    public static boolean valid(UserCommand value) { return value != null && scope(value.scope()) && id(value.userId()); }
}
