package com.raxim.myscoutee.microservices.transport.email;

import com.raxim.myscoutee.domain.email.api.EmailRendering;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class EmailRenderingEndpoint {
    private final EmailRendering owner;
    private final ObjectMapper mapper;
    public EmailRenderingEndpoint(EmailRendering owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = EmailRenderingProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> read(HttpServletRequest request) throws java.io.IOException {
        try {
            byte[] body = ReadQueryBodies.readBytes(request, EmailRenderingProtocol.MAX_REQUEST_BYTES);
            EmailRenderingProtocol.Query query;
            try {
                query = mapper.readerFor(EmailRenderingProtocol.Query.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!EmailRenderingProtocol.valid(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, new EmailRenderingProtocol.Result(true, owner.renderText(query.template(), query.variables())));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new EmailRenderingProtocol.Error(invalid.code())); }
        
        catch (RuntimeException unavailable) { return response(503, new EmailRenderingProtocol.Error("EMAIL_RENDERING_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
