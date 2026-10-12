package com.raxim.myscoutee.domain.accounts.security.firebase;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;

public interface FirebaseTokenVerifier {
    FirebaseCredential verify(String firebaseToken);
}
