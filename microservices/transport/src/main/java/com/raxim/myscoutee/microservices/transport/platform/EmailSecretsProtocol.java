package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.EmailSecrets;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

public final class EmailSecretsProtocol {
    public static final String ENCRYPT = "/_internal/v1/platform/email-secrets/encrypt";
    public static final String DECRYPT = "/_internal/v1/platform/email-secrets/decrypt";
    public static final int MAX_REQUEST_BYTES = 1024 * 1024, MAX_RESPONSE_BYTES = 1024 * 1024;
    private EmailSecretsProtocol() {}
    public static String[] paths() { return new String[]{ENCRYPT, DECRYPT}; }
    public record Error(String code) {}
    public record Encrypt(EmailSecrets.Purpose purpose, String plaintext) {}
    public record Decrypt(EmailSecrets.Purpose purpose, EncryptedSecret envelope) {}
    public record Encrypted(Boolean resolved, EncryptedSecret value) {}
    public record Plaintext(Boolean resolved, String value) {}
    public static boolean valid(Encrypt value) { return value != null && value.purpose() != null && value.plaintext() != null && !value.plaintext().isBlank() && SecretEnvelopeBounds.text(value.plaintext(), 65536); }
    public static boolean valid(Decrypt value) { return value != null && value.purpose() != null && SecretEnvelopeBounds.envelope(value.envelope()); }
}
