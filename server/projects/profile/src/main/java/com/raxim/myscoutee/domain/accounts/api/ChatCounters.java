package com.raxim.myscoutee.domain.accounts.api;
import java.util.Collection;
public interface ChatCounters {
    record Change(String ownerUserId, String context, int delta) {}
    void increment(Change change);
    void increment(Collection<Change> changes);
}
