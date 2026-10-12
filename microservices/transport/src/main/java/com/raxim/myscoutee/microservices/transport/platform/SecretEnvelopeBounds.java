package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

/** Envelope size bounds only; AES, key-id and purpose validation stay in the canonical crypto owner. */
final class SecretEnvelopeBounds {
    private SecretEnvelopeBounds() {}
    static boolean text(String value, int maximum) { return value == null || value.length() <= maximum; }
    static boolean envelope(EncryptedSecret value) {
        return value == null || text(value.getAlgorithm(), 64) && text(value.getKeyId(), 256)
                && text(value.getNonce(), 128) && text(value.getCiphertext(), 300000);
    }
}
