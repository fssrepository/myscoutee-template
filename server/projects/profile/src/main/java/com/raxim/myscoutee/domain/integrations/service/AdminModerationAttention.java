package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.ChatCounts;

/** Optional report/feedback/support fields in the authenticated admin overview. */
interface AdminModerationAttention {
    Counts counts(ChatCounts chat);
    record Counts(long unresolvedReports, long unresolvedFeedback, long supportCases) {}
}
