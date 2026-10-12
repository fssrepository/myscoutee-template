package com.raxim.myscoutee.microservices.transport.platform;

import java.util.Collection;

public final class JobAudienceProtocol {
    public static final String SCOPE = "/_internal/v1/platform/job-audience/workspaces";
    public static final String USER = "/_internal/v1/platform/job-audience/user";
    public static final String TARGET = "/_internal/v1/platform/job-audience/target";
    public static final String USERS = "/_internal/v1/platform/job-audience/users";
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private JobAudienceProtocol() {}
    public record Context(Boolean active, String groupId) {}
    public record ScopeQuery(Context context) {}
    public record UserQuery(Context context, String userId) {}
    public record TargetQuery(Context context, String type, String id) {}
    public record UsersQuery(Context context, Collection<String> userIds) {}
    public record Group(Boolean resolved, String groupId) {}
    public record ScopeResult(java.util.Set<String> ids, Boolean included) {}
    public record Error(String code) {}
    public static String[] paths() { return new String[]{SCOPE, USER, TARGET, USERS}; }
    public static boolean bounded(String value) { return value == null || value.length() <= 512; }
    public static boolean valid(Context context) {
        return context != null && context.active() != null && bounded(context.groupId()) && (context.active() || context.groupId() == null);
    }
    public static boolean valid(Collection<String> ids) {
        return ids != null && ids.size() <= 4096 && ids.stream().allMatch(JobAudienceProtocol::bounded);
    }
}
