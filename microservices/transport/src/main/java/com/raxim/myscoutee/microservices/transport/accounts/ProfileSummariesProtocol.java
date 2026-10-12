package com.raxim.myscoutee.microservices.transport.accounts;

import java.util.Collection;
import com.raxim.myscoutee.domain.accounts.dto.ProfileSummaryDto;

public final class ProfileSummariesProtocol {
    public static final String ROOT = "/_internal/v1/accounts/profile-summaries";
    public static final String STATUS = ROOT + "/status";
    public static final String SINGLE = ROOT + "/single";
    public static final String BATCH = ROOT + "/batch";
    public static final String COUNT = ROOT + "/count";
    public static final String PAGE = ROOT + "/page";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    public static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private ProfileSummariesProtocol() {}
    public static String[] paths() { return new String[] {STATUS, SINGLE, BATCH, COUNT, PAGE}; }
    public record StatusQuery(String status) {}
    public static boolean valid(StatusQuery query) { return query != null && id(query.status()); }
    public record SingleQuery(String userId) {}
    public record BatchQuery(Collection<String> ids) {}
    public record PageQuery(Collection<String> ids, Integer offset, Integer limit) {}

    public record SingleResult(Boolean resolved, ProfileSummaryDto value) {}
    public record Error(String code) {}
    private static boolean id(String value) { return value == null || value.length() <= 512; }
    private static boolean ids(Collection<String> values) {
        return values != null && values.size() <= 4096 && values.stream().allMatch(ProfileSummariesProtocol::id);
    }
    public static boolean valid(SingleQuery query) { return query != null && id(query.userId()); }
    public static boolean valid(BatchQuery query) { return query != null && ids(query.ids()); }


    public static boolean valid(PageQuery query) {
        return query != null && ids(query.ids()) && query.offset() != null && query.offset() >= 0
                && query.limit() != null && query.limit() > 0 && query.limit() <= 4096;
    }
}
