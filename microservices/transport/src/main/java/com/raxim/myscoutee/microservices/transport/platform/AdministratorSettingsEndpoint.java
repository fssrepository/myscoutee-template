package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class AdministratorSettingsEndpoint {
    private final AdministratorSettings owner;
    private final ObjectMapper mapper;
    public AdministratorSettingsEndpoint(AdministratorSettings owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @GetMapping(value = AdministratorSettingsProtocol.EMAILS, produces = "application/json")
    public ResponseEntity<?> emails() {
        try { return response(200, owner.adminEmails()); }
        catch (RuntimeException unavailable) { return response(503, new AdministratorSettingsProtocol.Error("ADMINISTRATOR_SETTINGS_UNAVAILABLE")); }
    }
    @PostMapping(value = AdministratorSettingsProtocol.CLASSIFY, produces = "application/json")
    public ResponseEntity<?> classify(HttpServletRequest request) throws java.io.IOException {
        try {
            byte[] body = ReadQueryBodies.readBytes(request, AdministratorSettingsProtocol.MAX_REQUEST_BYTES);
            AdministratorSettingsProtocol.Emails query;
            try {
                query = mapper.readerFor(AdministratorSettingsProtocol.Emails.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (query == null || !AdministratorSettingsProtocol.valid(query.emails())) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, owner.adminEmailsAmong(query.emails()));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AdministratorSettingsProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new AdministratorSettingsProtocol.Error("ADMINISTRATOR_SETTINGS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
