package com.raxim.myscoutee.domain.platform.api;
public interface FirebaseRuntime {
    boolean isAuthenticationEnabled();
    com.google.firebase.auth.FirebaseAuth authentication();
    com.google.firebase.messaging.FirebaseMessaging messaging();
    void refreshAfterConfigurationChange();
}
