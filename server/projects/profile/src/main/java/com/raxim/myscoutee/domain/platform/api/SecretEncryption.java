package com.raxim.myscoutee.domain.platform.api;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;
public interface SecretEncryption {
    EncryptedSecret encrypt(String purpose, String plaintext);
    String decrypt(String purpose, EncryptedSecret envelope);
}
