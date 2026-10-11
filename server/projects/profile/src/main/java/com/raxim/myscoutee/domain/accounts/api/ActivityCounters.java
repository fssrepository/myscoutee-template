package com.raxim.myscoutee.domain.accounts.api;
public interface ActivityCounters {
    boolean hasSupportCaseCounters(String userId);
    void refreshSupportCaseCountersForAdmins();
}
