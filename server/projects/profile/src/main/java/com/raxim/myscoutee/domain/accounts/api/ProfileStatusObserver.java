package com.raxim.myscoutee.domain.accounts.api;

/** Synchronous propagation of a persisted profile status, before its work marker is cleared. */
public interface ProfileStatusObserver {
    void onProfileStatusChanged(String userId);
}
