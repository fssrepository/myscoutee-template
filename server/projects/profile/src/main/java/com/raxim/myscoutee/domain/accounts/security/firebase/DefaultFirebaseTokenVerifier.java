package com.raxim.myscoutee.domain.accounts.security.firebase;

import org.springframework.stereotype.Service;

import com.google.firebase.auth.FirebaseToken;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;
import com.raxim.myscoutee.domain.accounts.security.firebase.exception.FirebaseTokenInvalidException;
import com.raxim.myscoutee.domain.platform.api.FirebaseRuntime;

@Service
public class DefaultFirebaseTokenVerifier implements FirebaseTokenVerifier {
    private final FirebaseRuntime firebaseRuntime;

    public DefaultFirebaseTokenVerifier(
            FirebaseRuntime firebaseRuntime) {
        this.firebaseRuntime = firebaseRuntime;
    }

    @Override
    public FirebaseCredential verify(String firebaseToken) {
        if (firebaseToken == null || firebaseToken.isBlank()) {
            throw invalidToken();
        }
        try {
            FirebaseToken decodedToken = firebaseRuntime
                    .authentication()
                    .verifyIdToken(firebaseToken);
            return new FirebaseCredential(decodedToken);
        } catch (Exception exception) {
            /*
             * Upstream Firebase failures can contain project identifiers and
             * provider details. Authentication callers only need a stable,
             * non-sensitive rejection and must never receive that diagnostic.
             */
            throw invalidToken();
        }
    }

    private FirebaseTokenInvalidException invalidToken() {
        return new FirebaseTokenInvalidException(
                "Invalid Firebase authentication token.");
    }
}
