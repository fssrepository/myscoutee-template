package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.EmailSecrets;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Purpose-scoped owner operation; no master key, arbitrary-purpose method or automatic retry. */
public final class RemoteEmailSecrets extends RemoteReadTransport implements EmailSecrets {
    private final ObjectMapper mapper;
    public RemoteEmailSecrets(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public EncryptedSecret encryptEmailSecret(Purpose purpose, String plaintext) {
        var command = new EmailSecretsProtocol.Encrypt(purpose, plaintext);
        if (!EmailSecretsProtocol.valid(command)) throw new IllegalArgumentException("Invalid email-secrets request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > EmailSecretsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("email-secrets request exceeds byte limit");
        var result = exchange(EmailSecretsProtocol.ENCRYPT, bytes, new TypeReference<EmailSecretsProtocol.Encrypted>() {},
                "email-secrets.encrypt", EmailSecretsProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.resolved()) && value.value() != null && SecretEnvelopeBounds.envelope(value.value()));
        return result.value();
    }
    @Override public String decryptEmailSecret(Purpose purpose, EncryptedSecret envelope) {
        var command = new EmailSecretsProtocol.Decrypt(purpose, envelope);
        if (!EmailSecretsProtocol.valid(command)) throw new IllegalArgumentException("Invalid email-secrets request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > EmailSecretsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("email-secrets request exceeds byte limit");
        var result = exchange(EmailSecretsProtocol.DECRYPT, bytes, new TypeReference<EmailSecretsProtocol.Plaintext>() {},
                "email-secrets.decrypt", EmailSecretsProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.resolved()) && SecretEnvelopeBounds.text(value.value(), 65536));
        return result.value();
    }
}
