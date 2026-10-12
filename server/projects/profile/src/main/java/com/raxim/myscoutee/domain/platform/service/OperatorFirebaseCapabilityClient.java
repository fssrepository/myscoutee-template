package com.raxim.myscoutee.domain.platform.service;

/**
 * Narrow boundary for explicitly requested operator Firebase capability
 * checks. Implementations must use an isolated Firebase application and must
 * never replace or delete the process-wide default application.
 */
public interface OperatorFirebaseCapabilityClient {
    record FirebaseWebConfiguration(
            String apiKey,
            String authDomain,
            String storageBucket,
            String messagingSenderId,
            String appId,
            String measurementId,
            String vapidKey) {
    }

    void testAuthentication(
            String projectId,
            String serviceAccountJson,
            FirebaseWebConfiguration webConfiguration);

    void sendTestMessage(
            String projectId,
            String serviceAccountJson,
            FirebaseWebConfiguration webConfiguration,
            String browserReadinessToken,
            String optionalDestinationToken,
            String notificationTitle);
}
