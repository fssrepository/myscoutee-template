package com.raxim.myscoutee.common.config;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.raxim.myscoutee.domain.platform.api.SchedulingPolicy;

/** Native notification limits, configured per deployment without the product affinity catalog. */
@Service
@EnableConfigurationProperties(NotificationParameters.class)
public class PlatformParametersService implements SchedulingPolicy {
    private final NotificationParameters parameters;
    @org.springframework.beans.factory.annotation.Value("${users.account-purge-window-days:30}")
    private Integer accountPurgeWindowDays;

    public PlatformParametersService(NotificationParameters parameters) {
        this.parameters = parameters;
    }

    public int notificationWindowStartHour(int fallback) {
        return clampInt(parameters.firebaseWindowStartHour(), 0, 23, fallback);
    }

    public int notificationWindowEndHour(int fallback) {
        return clampInt(parameters.firebaseWindowEndHour(), 0, 24, fallback);
    }

    public int notificationMaxWorkers(int fallback) {
        return clampInt(parameters.maxWorkers(), 1, 64, fallback);
    }

    public int notificationMaxRetries(int fallback) {
        return clampInt(parameters.maxRetries(), 0, 20, fallback);
    }

    public Duration notificationInitialBackoff(Duration fallback) {
        return seconds(parameters.initialBackoffSeconds(), fallback, 1, 86_400);
    }

    public int notificationCollapseWindowSeconds(int fallback) {
        return clampInt(parameters.collapseWindowSeconds(), 0, 86_400, fallback);
    }

    public int notificationTopicThreshold(int fallback) {
        return clampInt(parameters.topicThreshold(), 1, 1_000_000, fallback);
    }

    public int notificationMulticastThreshold(int fallback) {
        return clampInt(parameters.multicastThreshold(), 1, 1000, fallback);
    }

    public long notificationOutboxPollDelayMs(long fallback) {
        return millis(parameters.notificationOutboxPollDelayMs(), fallback, 1_000L, 3_600_000L);
    }

    public int accountPurgeWindowDays(int fallback) {
        return clampInt(accountPurgeWindowDays, 1, 365, fallback);
    }

    private Duration seconds(Integer value, Duration fallback, int min, int max) {
        int normalized = clampInt(value, min, max, (int) Math.max(0, (fallback == null ? Duration.ZERO : fallback).toSeconds()));
        return Duration.ofSeconds(normalized);
    }

    private long millis(Integer value, long fallback, long min, long max) {
        if (value == null || value.longValue() < min || value.longValue() > max) {
            return Math.max(min, Math.min(max, fallback));
        }
        return value.longValue();
    }

    private int clampInt(Integer value, int min, int max, int fallback) {
        if (value == null || value < min || value > max) {
            return Math.max(min, Math.min(max, fallback));
        }
        return value;
    }
}
