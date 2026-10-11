package com.raxim.myscoutee.domain.accounts.api;

/** Account-owned support inbox counters, independent of product activity counters. */
public interface SupportCaseCounters {
    boolean hasSupportCaseCounters(String userId);
    void refreshSupportCaseCountersForAdmins();
}
