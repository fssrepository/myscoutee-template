package com.raxim.myscoutee.domain.notifications.api;

public interface NotificationPrivacy {
    void purgeDeliveryData(String userId);
    void anonymizeRuleAuthors(String userId);
}
