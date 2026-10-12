package com.raxim.myscoutee.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("myscoutee.notifications")
public record NotificationParameters(
        Integer firebaseWindowStartHour,
        Integer firebaseWindowEndHour,
        Integer maxWorkers,
        Integer maxRetries,
        Integer initialBackoffSeconds,
        Integer collapseWindowSeconds,
        Integer topicThreshold,
        Integer multicastThreshold,
        Integer notificationOutboxPollDelayMs) {
}
