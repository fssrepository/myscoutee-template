package com.raxim.myscoutee.domain.accounts.api;

import java.util.List;

/** Account facts needed by the existing scheduled-email evaluator and delivery audit. */
public interface EmailProfile {
    String getId();
    String getUserId();
    String getWorkspaceGroupId();
    String getStatus();
    String getProfileStatus();
    String getName();
    String getEmail();
    String getCity();
    String getPartitionKey();
    Integer getCompletion();
    ActivityCounts getActivities();
    List<? extends Device> getDevices();
    interface ActivityCounts { Integer events(); Integer hosting(); }
    interface Device {
        String getLastSeenAtIso();
        String getLastRegisteredAtIso();
        Boolean getNotificationsEnabled();
        boolean hasPushToken();
    }
}
