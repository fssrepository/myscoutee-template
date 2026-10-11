package com.raxim.myscoutee.domain.integrations.controller;

import java.util.Map;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.raxim.myscoutee.domain.integrations.dto.McpEventDtos.*;
import com.raxim.myscoutee.domain.integrations.service.McpEventsService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationCapabilitiesService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.AuthenticatedIntegrationClient;

/** Internal authenticated protocol adapter. These operations are not AI write tools. */
@RestController
@RequestMapping("/integrations/v1/mcp/events")
public class McpEventsController {
    private final McpEventsService events;
    private final IntegrationCapabilitiesService capabilities;
    public McpEventsController(McpEventsService events, IntegrationCapabilitiesService capabilities) {
        this.events = events; this.capabilities = capabilities;
    }
    @GetMapping public Map<String, Object> list(Authentication auth, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return protocol(() -> events.catalog(client(auth)));
    }
    @PostMapping("/subscribe") public Map<String, Object> subscribe(Authentication auth, @RequestBody Request request,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return protocol(() -> events.subscribe(client(auth), request));
    }
    @PostMapping("/unsubscribe") public Map<String, Object> unsubscribe(Authentication auth, @RequestBody Request request,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return protocol(() -> events.unsubscribe(client(auth), request));
    }
    private AuthenticatedIntegrationClient client(Authentication auth) {
        var client = capabilities.requireClient(auth);
        if (client.grantId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return client;
    }
    private Map<String, Object> protocol(Supplier<Object> operation) {
        try { return Map.of("result", operation.get()); }
        catch (McpEventsService.ProtocolException error) { return Map.of("error", error.failure); }
        catch (org.springframework.dao.OptimisticLockingFailureException | org.springframework.dao.DuplicateKeyException conflict) {
            return Map.of("error", new Failure(-32000, "Subscription changed concurrently; retry", Map.of("reason", "conflict")));
        }
    }
}
