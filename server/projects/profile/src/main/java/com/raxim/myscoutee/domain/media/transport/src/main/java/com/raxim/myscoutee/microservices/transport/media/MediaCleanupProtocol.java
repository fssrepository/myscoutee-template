package com.raxim.myscoutee.microservices.transport.media;

import java.util.*;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class MediaCleanupProtocol {
    public static final String DELETE_PUBLIC_OBJECT_OR_THROW = "/_internal/v1/media/media-cleanup/delete";
    public static final int MAX_REQUEST_BYTES = 16384, MAX_RESPONSE_BYTES = 16384;
    private MediaCleanupProtocol() {}
    public static String[] paths() { return new String[]{DELETE_PUBLIC_OBJECT_OR_THROW}; }
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(JobGroupContext.isActive(), JobGroupContext.groupId()); }
    }
    public record UserCommand(Scope scope, String reference) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean id(String value) { return value != null && !value.isBlank() && value.length() <= 512; }
    private static boolean scope(Scope value) {
        return value != null && value.active() != null && (value.groupId() == null || id(value.groupId()))
                && (value.active() || value.groupId() == null);
    }
    public static boolean valid(UserCommand value) { return value != null && scope(value.scope()) && (value.reference() == null || value.reference().length() <= 8192); }
}
