package com.raxim.myscoutee.microservices.transport.email;

import com.raxim.myscoutee.domain.email.api.EmailLinks;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public final class EmailLinksEndpoint {
    private final EmailLinks owner;
    public EmailLinksEndpoint(EmailLinks owner) { this.owner = owner; }
    @GetMapping(value = EmailLinksProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> baseUrl() {
        try { return response(200, new EmailLinksProtocol.Result(true, owner.getBaseUrl())); }
        catch (RuntimeException unavailable) { return response(503, new EmailLinksProtocol.Error("EMAIL_LINKS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
