package com.raxim.myscoutee.microservices.transport.platform;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.platform.api.EmailSecrets;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class EmailSecretsEndpoint {
    private final EmailSecrets owner;
    private final ObjectMapper mapper;
    public EmailSecretsEndpoint(EmailSecrets owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = EmailSecretsProtocol.ENCRYPT, produces = "application/json")
    public ResponseEntity<?> encrypt(HttpServletRequest request) throws java.io.IOException {
        return read(request, EmailSecretsProtocol.Encrypt.class, EmailSecretsProtocol::valid, command -> new EmailSecretsProtocol.Encrypted(true, owner.encryptEmailSecret(command.purpose(), command.plaintext())));
    }
    @PostMapping(value = EmailSecretsProtocol.DECRYPT, produces = "application/json")
    public ResponseEntity<?> decrypt(HttpServletRequest request) throws java.io.IOException {
        return read(request, EmailSecretsProtocol.Decrypt.class, EmailSecretsProtocol::valid, command -> new EmailSecretsProtocol.Plaintext(true, owner.decryptEmailSecret(command.purpose(), command.envelope())));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, EmailSecretsProtocol.MAX_REQUEST_BYTES);
            T query = ReadQueryBodies.decode(bytes, mapper, type, valid, true);
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new EmailSecretsProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new EmailSecretsProtocol.Error("EMAIL_SECRETS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
