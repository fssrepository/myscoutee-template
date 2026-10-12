package com.raxim.myscoutee.domain.notifications.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.notifications.document.NotificationRuleConfig;

public interface NotificationRuleConfigsRepository extends MongoRepository<NotificationRuleConfig, String> {
    Optional<NotificationRuleConfig> findByBaseGroupIdAndRuleKey(String baseGroupId, String ruleKey);
    List<NotificationRuleConfig> findByBaseGroupId(String baseGroupId);
    List<NotificationRuleConfig> findByRuleKeyIn(java.util.Collection<String> ruleKeys);

    default Optional<NotificationRuleConfig> findByRuleKey(String ruleKey) {
        return findByBaseGroupIdAndRuleKey(null, ruleKey);
    }
}
