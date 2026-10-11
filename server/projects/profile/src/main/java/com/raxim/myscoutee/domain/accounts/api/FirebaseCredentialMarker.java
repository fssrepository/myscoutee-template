package com.raxim.myscoutee.domain.accounts.api;

/** In-process classification of the credential produced by the native Firebase verifier. Not a wire claim. */
public interface FirebaseCredentialMarker {
    static boolean isPresent(org.springframework.security.core.Authentication authentication) {
        return authentication != null && authentication.getCredentials() instanceof FirebaseCredentialMarker;
    }
}
