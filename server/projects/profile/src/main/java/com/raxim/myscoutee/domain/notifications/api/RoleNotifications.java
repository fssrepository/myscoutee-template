package com.raxim.myscoutee.domain.notifications.api;

/** Role events accepted by the notification domain through its existing durable outbox. */
public interface RoleNotifications {
    void releaseAvailable(String version);
    void moderationSubmitted(String kind, String submissionId);

}
