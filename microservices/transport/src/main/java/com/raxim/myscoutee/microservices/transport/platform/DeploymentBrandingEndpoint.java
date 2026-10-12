package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public final class DeploymentBrandingEndpoint {
    private final DeploymentBranding owner;
    public DeploymentBrandingEndpoint(DeploymentBranding owner) { this.owner = owner; }
    @GetMapping(value = DeploymentBrandingProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> read() {
        try {
            var value = java.util.Objects.requireNonNull(owner.resolve());
            return response(200, new DeploymentBrandingProtocol.Result(true, value));
        } catch (RuntimeException unavailable) {
            return response(503, new DeploymentBrandingProtocol.Error("BRANDING_UNAVAILABLE"));
        }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
