package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public final class AuthenticationModeEndpoint {
    private final AuthenticationMode owner;
    public AuthenticationModeEndpoint(AuthenticationMode owner) { this.owner = owner; }
    @GetMapping(value = AuthenticationModeProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> read() {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Objects.requireNonNull(owner.isFirebaseEnabled()));
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new AuthenticationModeProtocol.Error("AUTHENTICATION_MODE_UNAVAILABLE"));
        }
    }
}
