package com.raxim.myscoutee.domain.content.api;

/** Selected native account-erasure actions for moderation records. */
public interface ContentPrivacy {
    void purgeFeedback(String userId);
    void purgeReports(String userId);
}
