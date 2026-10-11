package com.raxim.myscoutee.domain.accounts.api;
public interface AccountDevices {
    void register(String userId, String deviceId, String firebaseToken, String platform, Boolean enabled);
    void remove(String userId, String deviceId, String firebaseToken);
}
