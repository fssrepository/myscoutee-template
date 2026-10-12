package com.raxim.myscoutee.domain.platform.api;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

/** Email-only purposes; the platform master key and arbitrary-purpose crypto are never exposed. */
public interface EmailSecrets {
    enum Purpose {
        PROVIDER("operator-email-provider"), UNSUBSCRIBE("email-unsubscribe-v1");
        private final String aadPurpose;
        Purpose(String value) { aadPurpose = value; }
        public String aadPurpose() { return aadPurpose; }
    }
    EncryptedSecret encryptEmailSecret(Purpose purpose, String plaintext);
    String decryptEmailSecret(Purpose purpose, EncryptedSecret envelope);
}
