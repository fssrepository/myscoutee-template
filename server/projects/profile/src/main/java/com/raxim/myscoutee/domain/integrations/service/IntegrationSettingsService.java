package com.raxim.myscoutee.domain.integrations.service;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.*;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;

/** Coordinates the existing API and MCP settings into one load/save boundary. */
@Service
public class IntegrationSettingsService {
    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.domain.accounts.api.AccountIntegrationAccess accountIntegrationAccess;
    private final IntegrationTokenService tokens;
    private final McpAuthorizationService mcp;
    private final ProfileDirectory users;
    private final IntegrationPublicEndpointResolver endpoint;

    public IntegrationSettingsService(IntegrationTokenService tokens, McpAuthorizationService mcp,
            ProfileDirectory users, IntegrationPublicEndpointResolver endpoint) {
        this.tokens = tokens; this.mcp = mcp; this.users = users;
        this.endpoint = endpoint;
    }

    public IntegrationSettingsDto load(String owner) { return load(owner, false); }

    public IntegrationSettingsDto load(String owner, boolean admin) {
        var user = users.findByUserId(owner).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
        var access = user.getIntegrationAccess() == null ? IntegrationAccess.initial() : user.getIntegrationAccess();
        var active = tokens.activeTokens(owner);
        var api = tokens.settings(owner, admin ? "/api/admin-client/v1" : endpoint.resolve(),
                admin ? IntegrationTokenService.ADMIN_SCOPE : IntegrationTokenService.INTEGRATION_SCOPE, active);
        return new IntegrationSettingsDto(api.baseUrl(), api.maxActiveTokens(), api.maxBatchSize(),
                api.tokens().stream().map(token -> token.withAccess(access.clientMode(token.id()))).toList(),
                mcp.settings(active, access), access.revision());
    }

    public IntegrationSettingsDto save(String owner, IntegrationSettingsUpdateDto request) { return save(owner, request, false); }

    public IntegrationSettingsDto save(String owner, IntegrationSettingsUpdateDto request, boolean admin) {
        if (request == null || request.mcpAccess() == null || request.accessRevision() < 0
                || request.clients() == null || request.clients().size() > 2 * IntegrationTokenService.MAX_ACTIVE_TOKENS)
            throw error(HttpStatus.BAD_REQUEST);
        var user = users.findByUserId(owner).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
        var previous = user.getIntegrationAccess() == null ? IntegrationAccess.initial() : user.getIntegrationAccess();
        if (previous.revision() != request.accessRevision()) throw error(HttpStatus.CONFLICT);
        var allActive = tokens.activeTokens(owner);
        var active = allActive.stream()
                .filter(token -> List.of(admin ? IntegrationTokenService.ADMIN_SCOPE : IntegrationTokenService.INTEGRATION_SCOPE, IntegrationTokenService.MCP_SCOPE)
                        .contains(token.getScope() == null || token.getScope().isBlank()
                                ? IntegrationTokenService.INTEGRATION_SCOPE : token.getScope()))
                .map(token -> token.getId()).collect(java.util.stream.Collectors.toSet());
        Map<String, IntegrationAccess.Mode> next = new LinkedHashMap<>();
        allActive.forEach(token -> next.put(token.getId(), previous.clientMode(token.getId())));
        var changed = new java.util.HashSet<String>();
        for (var client : request.clients()) {
            if (client == null || client.id() == null || client.accessMode() == null || !changed.add(client.id()))
                throw error(HttpStatus.BAD_REQUEST);
            if (!active.contains(client.id())) throw error(HttpStatus.CONFLICT);
            next.put(client.id(), client.accessMode());
        }
        var saved = new IntegrationAccess(previous.revision() + 1, request.mcpAccess(), Map.copyOf(next));
        if (accountIntegrationAccess.updateIntegrationAccess(owner, previous.revision(), saved) != 1) throw error(HttpStatus.CONFLICT);
        return load(owner, admin);
    }

    private static ResponseStatusException error(HttpStatus status) {
        return new ResponseStatusException(status, "Integration settings are invalid or have changed; reload the settings.");
    }
}
