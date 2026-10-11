package com.raxim.myscoutee.domain.access.api;

public interface ConsentAuthorization {
    boolean canCurrentActorSubmitPrivacyConsent(String userId);
}
