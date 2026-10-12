package com.raxim.myscoutee.domain.integrations.dto;

import java.util.List;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationTokenDto;

public final class McpDtos {
    private McpDtos() {}
    public record ClientRequest(String name, String redirectUri) {}
    public record Registration(
            @com.fasterxml.jackson.annotation.JsonProperty("client_name") String clientName,
            @com.fasterxml.jackson.annotation.JsonProperty("redirect_uris") List<String> redirectUris,
            @com.fasterxml.jackson.annotation.JsonProperty("token_endpoint_auth_method") String authMethod,
            @com.fasterxml.jackson.annotation.JsonProperty("grant_types") List<String> grantTypes,
            @com.fasterxml.jackson.annotation.JsonProperty("response_types") List<String> responseTypes,
            String scope) {}
    public record Client(IntegrationTokenDto token, String redirectUri, boolean manual, String clientId) {}
    public record Created(Client client, String secret) {}
    public record Settings(String resource, int maxClients, boolean remoteEnabled, List<Client> clients,
            com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode accessMode) {
        public Settings(String resource, int maxClients, boolean remoteEnabled, List<Client> clients) {
            this(resource, maxClients, remoteEnabled, clients,
                    com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.WRITE);
        }
    }
    public record Authorization(String clientId, String redirectUri, String resource, String scope,
            String responseType, String codeChallenge, String codeChallengeMethod, String state) {}
    public record Consent(Authorization authorization, boolean approve, String profileId) {
        public Consent(Authorization authorization, boolean approve) { this(authorization, approve, null); }
    }
    public record Context(String clientName, String profileName, String groupName, String redirectUri, String profileId,
            com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode accessMode) {}
    public record Redirect(String url) {}
}
