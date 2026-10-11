package com.raxim.myscoutee.domain.platform.api;

import java.time.Duration;

public interface NotificationPolicy {
    int notificationWindowStartHour(int fallback);
    int notificationWindowEndHour(int fallback);
    int notificationMaxWorkers(int fallback);
    int notificationMaxRetries(int fallback);
    int notificationCollapseWindowSeconds(int fallback);
    int notificationTopicThreshold(int fallback);
    int notificationMulticastThreshold(int fallback);
    Duration notificationInitialBackoff(Duration fallback);
    long notificationOutboxPollDelayMs(long fallback);
}
