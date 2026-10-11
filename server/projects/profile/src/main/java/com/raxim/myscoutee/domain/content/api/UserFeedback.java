package com.raxim.myscoutee.domain.content.api;

public interface UserFeedback {
    String submit(String userId, String category, String subject, String details);
    /** A stable ID retains the result of an explicitly retried distributed submission. */
    String submitOnce(String requestId, String userId, String category, String subject, String details);
}
