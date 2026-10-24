package com.raxim.myscoutee.microservices.transport.common;

import java.util.Collection;

/** Bounds shared by the typed transport envelopes; owners validate required fields separately. */
public final class ProtocolFields {
    private ProtocolFields() {}

    /** Nullable text is part of these envelopes' existing wire contract. */
    public static boolean text(String value, int limit) {
        return value == null || value.length() <= limit;
    }

    /** The collection is required; its elements may be null, blank or at most 512 characters. */
    public static boolean ids(Collection<String> values) {
        return values != null && values.size() <= 4096
                && values.stream().allMatch(value -> text(value, 512));
    }
    /** Shared privacy envelope; component names/order retain the existing HTTP JSON contract. */
    public record JobScope(Boolean active, String groupId) {
        public static JobScope current() {
            return new JobScope(com.raxim.myscoutee.domain.platform.api.JobGroupContext.isActive(),
                    com.raxim.myscoutee.domain.platform.api.JobGroupContext.groupId());
        }
    }
    public record UserCommand(JobScope scope, String userId) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    /** Privacy commands require an owner ID; read-directory envelopes may still contain nulls. */
    public static boolean requiredId(String value) {
        return value != null && !value.isBlank() && value.length() <= 512;
    }
    public static boolean requiredIds(Collection<String> values) {
        return values != null && values.size() <= 4096 && values.stream().allMatch(ProtocolFields::requiredId);
    }
    /** An inactive job has no group; an active Dating job intentionally has a null group. */
    public static boolean jobScope(Boolean active, String groupId) {
        return active != null && (groupId == null || requiredId(groupId)) && (active || groupId == null);
    }


    /** Non-privacy transport scopes historically allow blank, bounded group text. */
    public static boolean boundedJobScope(JobScope value) {
        return value != null && value.active() != null && text(value.groupId(), 512)
                && (value.active() || value.groupId() == null);
    }
    public static boolean privacyScope(JobScope value) {
        return value != null && jobScope(value.active(), value.groupId());
    }
    public static boolean validUserCommand(UserCommand value) {
        return value != null && privacyScope(value.scope()) && requiredId(value.userId());
    }

}
