package com.raxim.myscoutee.domain.accounts.service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.raxim.myscoutee.domain.accounts.api.ActivityCounters;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;

@Service
public class UserActivityCountersService implements ActivityCounters {
    private static final List<String> DEMO_ADMIN_IDS = List.of("admin-demo-ava", "admin-demo-noel");
    private final UsersRepository usersRepository;
    private final MongoTemplate mongoTemplate;
    private final ChatDirectory chatDirectory;
    public UserActivityCountersService(UsersRepository usersRepository, MongoTemplate mongoTemplate, ChatDirectory chatDirectory) {
        this.usersRepository = usersRepository;
        this.mongoTemplate = mongoTemplate;
        this.chatDirectory = chatDirectory;
    }

    public void refreshSupportCaseCountersForAdmins() {
        Map<String, Users> adminsById = new LinkedHashMap<>();
        for (Users admin : usersRepository.findByHostTier("Admin")) {
            String userId = normalizeId(admin == null ? null : admin.getUserId());
            if (StringUtils.hasText(userId)) {
                adminsById.put(userId, admin);
            }
        }
        for (Users admin : usersRepository.findByUserIdIn(DEMO_ADMIN_IDS)) {
            String userId = normalizeId(admin == null ? null : admin.getUserId());
            if (StringUtils.hasText(userId)) {
                adminsById.put(userId, admin);
            }
        }
        Map<String, SupportCaseCounters> byBase = new LinkedHashMap<>();
        for (Users admin : adminsById.values()) {
            String base = admin.getWorkspaceGroupId();
            SupportCaseCounters counters = byBase.computeIfAbsent(base, this::resolveSupportCaseCounters);
            updateSupportCaseCounters(admin.getUserId(), counters);
        }
    }

    private SupportCaseCounters resolveSupportCaseCounters(String baseGroupId) {
        var counts = chatDirectory.supportCaseCounts(baseGroupId);
        int pending = counts.getOrDefault("pending", 0), warned = counts.getOrDefault("warned", 0), picked = counts.getOrDefault("picked", 0), solved = counts.getOrDefault("solved", 0), blocked = counts.getOrDefault("blocked", 0);
        return new SupportCaseCounters(pending, warned, picked, solved, blocked, pending + warned + picked + solved + blocked);
    }

    private void updateSupportCaseCounters(String userId, SupportCaseCounters counters) {
        String normalizedUserId = normalizeId(userId);
        if (!StringUtils.hasText(normalizedUserId) || counters == null) {
            return;
        }
        Update update = new Update()
                .set("activities.chat.supportCases.pending", count(counters.pending()))
                .set("activities.chat.supportCases.warned", count(counters.warned()))
                .set("activities.chat.supportCases.picked", count(counters.picked()))
                .set("activities.chat.supportCases.solved", count(counters.solved()))
                .set("activities.chat.supportCases.blocked", count(counters.blocked()))
                .set("activities.chat.supportCases.all", count(counters.all()));
        updateUserActivityFields(normalizedUserId, update);
    }

    private void updateUserActivityFields(String userId, Update update) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("userId").is(userId)), update, Users.class);
    }
    private String normalizeId(String value) { return value == null ? "" : value.trim(); }
    private int count(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private record SupportCaseCounters(
            int pending,
            int warned,
            int picked,
            int solved,
            int blocked,
            int all) {
    }

    @Override public boolean hasSupportCaseCounters(String userId) {
        Users user = userId == null ? null : usersRepository.findByUserId(userId).orElse(null);
        var activities = user == null ? null : user.getActivities();
        var chat = activities == null ? null : activities.chat();
        return chat != null && chat.supportCases() != null;
    }
}
