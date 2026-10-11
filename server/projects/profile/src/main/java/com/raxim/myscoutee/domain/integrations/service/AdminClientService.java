package com.raxim.myscoutee.domain.integrations.service;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.integrations.repository.AdminClientRepository;

@Service
public class AdminClientService {
    public record Attention(long unreadMessages, String unreadVersion,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long unresolvedReports,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long unresolvedFeedback,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long supportCases, long failedJobs) {}
    public record Overview(Attention attention) {}
    private final AdminClientAccessService access;
    private final AdminClientRepository repository;
    private final Optional<AdminModerationAttention> moderation;
    public AdminClientService(AdminClientAccessService access, AdminClientRepository repository,
            Optional<AdminModerationAttention> moderation) {
        this.access = access; this.repository = repository; this.moderation = moderation;
    }
    public Overview overview(String owner) {
        var user = access.requireAdmin(owner);
        var activity = user.getActivities(); var chat = activity == null ? null : activity.chat();
        var moderationCounts = moderation.map(feature -> feature.counts(chat)).orElse(null);
        return new Overview(
                new Attention(chat == null ? 0 : count(chat.all()), repository.unreadVersion(owner),
                        moderationCounts == null ? null : moderationCounts.unresolvedReports(),
                        moderationCounts == null ? null : moderationCounts.unresolvedFeedback(),
                        moderationCounts == null ? null : moderationCounts.supportCases(),
                        activity == null ? 0 : count(activity.adminJobs())));
    }
    private static long count(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
