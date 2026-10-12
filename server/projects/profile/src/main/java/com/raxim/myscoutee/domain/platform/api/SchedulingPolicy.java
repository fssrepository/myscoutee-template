package com.raxim.myscoutee.domain.platform.api;

public interface SchedulingPolicy extends NotificationPolicy {
    int accountPurgeWindowDays(int fallback);
}
