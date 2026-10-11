package com.raxim.myscoutee.domain.integrations.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.integrations.repository.AdminClientRepository;

@Service
public class AdminClientService {
    public record Attention(long unreadMessages, String unreadVersion, long unresolvedReports, long unresolvedFeedback,
            long supportCases, long failedJobs) {}
    public record Overview(Attention attention) {}
    private final AdminClientAccessService access;
    private final AdminClientRepository repository;
    public AdminClientService(AdminClientAccessService access, AdminClientRepository repository) { this.access = access; this.repository = repository; }
    public Overview overview(String owner) {
        var user = access.requireAdmin(owner);
        var activity = user.getActivities(); var chat = activity == null ? null : activity.chat();
        var support = chat == null ? null : chat.supportCases();
        return new Overview(
                new Attention(chat == null ? 0 : count(chat.all()), repository.unreadVersion(owner),
                        repository.unresolvedReports(), repository.unresolvedFeedback(), support == null ? 0 : count(support.pending()) + count(support.warned()) + count(support.picked()),
                        activity == null ? 0 : count(activity.adminJobs())));
    }
    private static long count(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
