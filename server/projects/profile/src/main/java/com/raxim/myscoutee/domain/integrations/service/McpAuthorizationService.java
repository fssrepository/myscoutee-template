package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.integrations.repository.IntegrationTokensRepository;
import com.raxim.myscoutee.domain.integrations.repository.McpAuthorizationRepository;
import com.raxim.myscoutee.domain.integrations.repository.McpOAuthClientsRepository;

import static com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.MCP_SCOPE;
import static com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.hash;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.raxim.myscoutee.domain.integrations.document.McpOAuthClient;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.integrations.document.IntegrationToken;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.document.McpAuthorization;
import com.raxim.myscoutee.domain.integrations.dto.McpDtos;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationTokenCreateRequestDto;

import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.AuthenticatedIntegrationClient;

/** OAuth protocol adapter. Profile keys, revocation and all API authorization retain their existing owners. */
@Service
public class McpAuthorizationService {
    public static final String SCOPE = "myscoutee:api";
    private final IntegrationTokenService keys;
    private final IntegrationTokensRepository tokens;
    private final McpAuthorizationRepository grants;
    private final ProfileDirectory users;
    private final Optional<McpWorkspaceAccess> workspaces;
    private final IntegrationPublicEndpointResolver endpoint;
    private final McpOAuthClientsRepository registrations;
    private final RuntimeConfiguration config;
    private final ScheduledMongoSessionRunner databases;
    private final SecureRandom random = new SecureRandom();

    public McpAuthorizationService(IntegrationTokenService keys, IntegrationTokensRepository tokens,
            McpAuthorizationRepository grants, ProfileDirectory users, Optional<McpWorkspaceAccess> workspaces,
            IntegrationPublicEndpointResolver endpoint, McpOAuthClientsRepository registrations,
            RuntimeConfiguration config, ScheduledMongoSessionRunner databases) {
        this.keys = keys; this.tokens = tokens; this.grants = grants; this.users = users;
        this.workspaces = workspaces; this.endpoint = endpoint;
        this.registrations = registrations; this.config = config;
        this.databases = databases;
    }
    public String origin() {
        URI api = URI.create(endpoint.resolve());
        if (!"https".equals(api.getScheme()) && !("http".equals(api.getScheme())
                && api.getHost() != null && List.of("localhost", "127.0.0.1", "[::1]").contains(api.getHost()))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "HTTPS integration URL required");
        }
        return api.getScheme() + "://" + api.getRawAuthority();
    }
    public String resource() { return origin() + "/mcp/private"; }
    public Map<String, Object> metadata() {
        String root = origin();
        return Map.of("issuer", root, "authorization_endpoint", root + "/mcp/authorize",
                "token_endpoint", root + "/api/mcp/oauth/token",
                "registration_endpoint", root + "/api/mcp/oauth/register",
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of("authorization_code", "refresh_token"),
                "token_endpoint_auth_methods_supported", List.of("none", "client_secret_basic", "client_secret_post"),
                "code_challenge_methods_supported", List.of("S256"),
                "scopes_supported", List.of(SCOPE), "authorization_response_iss_parameter_supported", true);
    }
    public Map<String, Object> resourceMetadata() {
        return Map.of("resource", resource(), "authorization_servers", List.of(origin()),
                "scopes_supported", List.of(SCOPE), "bearer_methods_supported", List.of("header"));
    }
    public McpDtos.Settings settings(String owner) {
        return settings(keys.activeTokens(owner), access(owner));
    }
    McpDtos.Settings settings(List<IntegrationToken> active, IntegrationAccess access) {
        var clients = active.stream()
                .filter(this::isClient).map(t -> new McpDtos.Client(keys.toDto(t).withAccess(access.clientMode(t.getId())), t.getRedirectUri(), t.getOauthClientId() == null, t.getOauthClientId() == null ? t.getId() : t.getOauthClientId())).toList();
        return new McpDtos.Settings(resource(), IntegrationTokenService.MAX_ACTIVE_TOKENS,
                realmMatches(), clients, access.mcpMode());
    }
    public McpDtos.Created create(String owner, McpDtos.ClientRequest input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 80)
            throw error("invalid_request");
        String redirect = input.redirectUri() == null || input.redirectUri().isEmpty() ? "" : redirectUri(input.redirectUri());
        var created = keys.create(owner, new IntegrationTokenCreateRequestDto(input.name(), 90), MCP_SCOPE);
        var token = tokens.findByIdAndOwnerUserId(created.token().id(), owner).orElseThrow();
        token.setRedirectUri(redirect);
        tokens.save(token);
        return new McpDtos.Created(new McpDtos.Client(created.token(), redirect, true, created.token().id()), created.value());
    }
    public void revoke(String owner, String id) {
        keys.revoke(owner, id, MCP_SCOPE);
        grants.revokeClient(id);
    }
    /** RFC 7591 public clients use S256 PKCE, with no user-bound secret to copy. */
    public synchronized Map<String, Object> register(McpDtos.Registration input) {
        if (input == null || input.clientName() == null || input.clientName().isBlank()
                || input.clientName().length() > 80 || input.redirectUris() == null
                || input.redirectUris().isEmpty() || input.redirectUris().size() > 5
                || (input.authMethod() != null && !"none".equals(input.authMethod()))
                || (input.scope() != null && !SCOPE.equals(input.scope()))
                || (input.responseTypes() != null && !List.of("code").equals(input.responseTypes()))
                || (input.grantTypes() != null && (input.grantTypes().isEmpty()
                    || !input.grantTypes().contains("authorization_code")
                    || input.grantTypes().stream().anyMatch(g -> !"authorization_code".equals(g) && !"refresh_token".equals(g)))))
            throw error("invalid_client_metadata");
        var redirects = input.redirectUris().stream().map(McpAuthorizationService::redirectUri).distinct().toList();
        if (registrations.countByCreatedAtAfter(Instant.now().minusSeconds(60)) >= 60
                || registrations.count() >= 10000)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "temporarily_unavailable");
        var client = new McpOAuthClient();
        client.id = "mcp_" + UUID.randomUUID(); client.name = input.clientName().trim();
        client.redirectUris = redirects; client.createdAt = Instant.now();
        client.expiresAt = client.createdAt.plusSeconds(90L * 86400);
        registrations.insert(client);
        return Map.of("client_id", client.id, "client_id_issued_at", client.createdAt.getEpochSecond(),
                "client_name", client.name, "redirect_uris", redirects,
                "token_endpoint_auth_method", "none", "grant_types", List.of("authorization_code", "refresh_token"),
                "response_types", List.of("code"), "scope", SCOPE);
    }
    public McpDtos.Context context(String actor, McpDtos.Authorization request) {
        var client = validate(request);
        String owner = client.owner() == null ? actor : client.owner();
        requireProfile(actor, owner);
        var profile = users.findByUserId(owner).orElseThrow(() -> error("access_denied"));
        String group = profile.getWorkspaceGroupId() == null || profile.getWorkspaceGroupId().isBlank() ? null
                : workspaces.orElseThrow(() -> error("access_denied")).name(profile.getWorkspaceGroupId());
        String tokenId = client.owner() == null ? existingConnection(owner, client.id()).map(IntegrationToken::getId).orElse("") : client.id();
        var permissions = profile.getIntegrationAccess() == null ? IntegrationAccess.initial() : profile.getIntegrationAccess();
        return new McpDtos.Context(client.name(), profile.getName(), group, request.redirectUri(), owner, permissions.effective(tokenId, true));
    }
    public McpDtos.Redirect authorize(String actor, McpDtos.Consent consent) {
        if (consent == null) throw error("invalid_request");
        var request = consent.authorization();
        var client = validate(request);
        String owner = client.owner() == null ? consent.profileId() : client.owner();
        if (owner == null || (consent.profileId() != null && !owner.equals(consent.profileId()))) throw error("access_denied");
        requireProfile(actor, owner);
        String code = randomValue("mcc_");
        if (consent.approve()) {
            var permissions = access(owner);
            if (permissions.mcpMode() == IntegrationAccess.Mode.BLOCKED) throw error("access_denied");
            var connection = client.owner() == null ? connection(owner, client, request.redirectUri()) : requireClient(client.id());
            if (permissions.effective(connection.getId(), true) == IntegrationAccess.Mode.BLOCKED) throw error("access_denied");
            var value = new McpAuthorization();
            value.id = hash(code); value.kind = "code"; value.clientId = client.id(); value.tokenId = connection.getId();
            value.ownerUserId = owner; value.redirectUri = request.redirectUri();
            value.resource = request.resource(); value.challenge = request.codeChallenge();
            value.expiresAt = Instant.now().plusSeconds(300);
            grants.save(value);
        }
        String callback = request.redirectUri() + (URI.create(request.redirectUri()).getRawQuery() == null ? "?" : "&")
                + (consent.approve() ? "code=" + code : "error=access_denied")
                + "&iss=" + encode(origin()) + (request.state() == null ? "" : "&state=" + encode(request.state()));
        return new McpDtos.Redirect(callback);
    }
    private synchronized IntegrationToken connection(String owner, ClientIdentity client, String redirect) {
        var existing = existingConnection(owner, client.id());
        if (existing.isPresent()) return existing.get();
        var created = keys.create(owner, new IntegrationTokenCreateRequestDto(client.name(), 90), MCP_SCOPE);
        var token = tokens.findByIdAndOwnerUserId(created.token().id(), owner).orElseThrow();
        // The generated integration secret is never exposed; only OAuth grants can use this connection.
        token.setOauthClientId(client.id()); token.setRedirectUri(redirect);
        return tokens.save(token);
    }
    private java.util.Optional<IntegrationToken> existingConnection(String owner, String clientId) {
        return tokens.findByOwnerUserIdOrderByCreatedAtIsoDesc(owner).stream()
                .filter(this::isClient).filter(t -> clientId.equals(t.getOauthClientId())).findFirst();
    }
    public Map<String, Object> exchange(Map<String, String> input, String clientId, String secret, String address) {
        ClientIdentity client = clientIdentity(clientId);
        if (client.owner() == null) {
            if (secret != null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_client");
        } else {
            var identity = keys.authenticateClient(secret, clientId, address, MCP_SCOPE);
            if (identity == null || !identity.tokenId().equals(clientId))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_client");
        }
        if (!resource().equals(input.get("resource"))) throw error("invalid_target");
        if (input.containsKey("scope") && !SCOPE.equals(input.get("scope"))) throw error("invalid_scope");
        String access = randomValue("mca_"); String refresh = randomValue("mcr_");
        McpAuthorization grant;
        if ("authorization_code".equals(input.get("grant_type"))) {
            String verifier = input.getOrDefault("code_verifier", "");
            if (!verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw error("invalid_grant");
            var code = grants.consumeCode(hash(bounded(input.get("code"))), clientId,
                    input.get("redirect_uri"), resource(), hash(verifier));
            if (code == null) throw error("invalid_grant");
            var connection = grantConnection(code);
            if (connection == null || (client.owner() != null && !client.owner().equals(code.ownerUserId))) throw error("invalid_grant");
            grant = new McpAuthorization();
            grant.id = UUID.randomUUID().toString(); grant.kind = "grant";
            grant.clientId = clientId; grant.tokenId = connection.getId(); grant.ownerUserId = code.ownerUserId; grant.resource = resource();
            grant.accessHash = hash(access); grant.refreshHash = hash(refresh); grant.usedRefreshHashes = List.of();
            grant.accessExpiresAt = Instant.now().plusSeconds(3600);
            grant.expiresAt = Instant.parse(connection.getExpiresAtIso());
            grants.save(grant);
        } else if ("refresh_token".equals(input.get("grant_type"))) {
            String refreshHash = hash(bounded(input.get("refresh_token")));
            grant = grants.rotate(refreshHash, clientId, resource(), hash(access), hash(refresh));
            if (grant == null) {
                grants.revokeReplayedRefresh(refreshHash, clientId);
                throw error("invalid_grant");
            }
            if (grantConnection(grant) == null) throw error("invalid_grant");
        } else throw error("unsupported_grant_type");
        return Map.of("access_token", access, "refresh_token", refresh, "token_type", "Bearer",
                "expires_in", Math.min(3600, Math.max(0, grant.expiresAt.getEpochSecond() - Instant.now().getEpochSecond())), "scope", SCOPE);
    }
    public AuthenticatedIntegrationClient authenticate(String bearer) {
        if (bearer == null || !bearer.matches("mca_[A-Za-z0-9_-]{43}")) return null;
        var grant = grants.access(hash(bearer), resource());
        if (grant == null) return null;
        var connection = grantConnection(grant);
        if (connection == null) return null;
        return new AuthenticatedIntegrationClient(grant.ownerUserId, connection.getId(), grant.clientId, MCP_SCOPE,
                IntegrationAccess.Mode.WRITE, grant.id);
    }
    /** Long-lived subscriptions survive access-token refresh, but never their authorizing grant. */
    public boolean activeSubscriptionGrant(String grantId, String owner, String tokenId, String clientId) {
        if (!realmMatches()) return false;
        return subscriptionGrantMatches(grantId, owner, tokenId, clientId);
    }
    /** Shared databases have one REAL worker pass, including grants issued in DEMO mode. */
    public boolean activeDeliveryGrant(String grantId, String owner, String tokenId, String clientId) {
        if (!realmMatches() && databases.hasDistinctDemoDatabase()) return false;
        return subscriptionGrantMatches(grantId, owner, tokenId, clientId);
    }
    private boolean subscriptionGrantMatches(String grantId, String owner, String tokenId, String clientId) {
        var grant = grants.activeGrant(grantId, owner, clientId, resource());
        // Keep the same established manual-client token binding as normal OAuth requests.
        var connection = grant == null ? null : grantConnection(grant);
        return connection != null && connection.getId().equals(tokenId);
    }
    private ClientIdentity validate(McpDtos.Authorization r) {
        if (!realmMatches()) throw error("access_denied");
        if (r == null || r.redirectUri() == null || !"code".equals(r.responseType()) || !"S256".equals(r.codeChallengeMethod())
                || r.codeChallenge() == null || !r.codeChallenge().matches("[A-Za-z0-9_-]{43}")
                || (r.state() != null && r.state().length() > 1024)) throw error("invalid_request");
        var client = clientIdentity(r.clientId());
        if (!client.redirects().contains(r.redirectUri())) throw error("invalid_request");
        if (!resource().equals(r.resource())) throw error("invalid_target");
        if (!SCOPE.equals(r.scope())) throw error("invalid_scope");
        return client;
    }
    private boolean realmMatches() {
        return MongoSessionModeContext.currentMode() == (config.isMcpDemoEnabled() ? MongoSessionMode.DEMO : MongoSessionMode.REAL);
    }
    private record ClientIdentity(String id, String name, String owner, List<String> redirects) {}
    private ClientIdentity clientIdentity(String id) {
        if (id == null || id.length() > 80) throw error("invalid_client");
        if (id.startsWith("mcp_")) {
            var registered = registrations.findById(id).filter(c -> c.expiresAt.isAfter(Instant.now()))
                    .orElseThrow(() -> error("invalid_client"));
            return new ClientIdentity(id, registered.name, null, registered.redirectUris);
        }
        var key = requireClient(id);
        if (key.getOauthClientId() != null) throw error("invalid_client");
        var redirects = key.getRedirectUri() == null || key.getRedirectUri().isBlank()
                ? config.getMcpManualRedirectUris().stream().map(McpAuthorizationService::redirectUri).distinct().toList()
                : List.of(key.getRedirectUri());
        return new ClientIdentity(id, key.getName(), key.getOwnerUserId(), redirects);
    }
    private IntegrationToken grantConnection(McpAuthorization grant) {
        String id = grant.tokenId == null ? grant.clientId : grant.tokenId;
        var token = tokens.findById(id).filter(this::isClient).orElse(null);
        if (token == null || !token.getOwnerUserId().equals(grant.ownerUserId)
                || !Objects.equals(grant.clientId, token.getOauthClientId() == null ? token.getId() : token.getOauthClientId())) return null;
        if (token.getOauthClientId() != null && registrations.findById(token.getOauthClientId())
                .filter(c -> c.expiresAt.isAfter(Instant.now())).isEmpty()) return null;
        if (access(token.getOwnerUserId()).effective(token.getId(), true) == IntegrationAccess.Mode.BLOCKED) return null;
        return token;
    }
    private IntegrationAccess access(String owner) {
        var user = users.findByUserId(owner).orElseThrow(() -> error("access_denied"));
        return user.getIntegrationAccess() == null ? IntegrationAccess.initial() : user.getIntegrationAccess();
    }
    private void requireProfile(String actor, String owner) {
        requireAccount(actor, owner);
        var profile = users.findByUserId(owner).orElseThrow(() -> error("access_denied"));
        if (profile.getWorkspaceGroupId() != null && !profile.getWorkspaceGroupId().isBlank()) {
            workspaces.orElseThrow(() -> error("access_denied")).requireProfile(owner, profile);
        }
    }
    private IntegrationToken requireClient(String id) {
        if (id == null || id.length() > 80) throw error("invalid_client");
        return tokens.findById(id).filter(this::isClient).orElseThrow(() -> error("invalid_client"));
    }
    private boolean isClient(IntegrationToken t) {
        return keys.isActive(t) && MCP_SCOPE.equals(t.getScope());
    }
    private void requireAccount(String actor, String owner) {
        var a = users.findByUserId(actor).orElseThrow(() -> error("access_denied"));
        var o = users.findByUserId(owner).orElseThrow(() -> error("access_denied"));
        String aa = a.getAccountUserId() == null || a.getAccountUserId().isBlank() ? a.getUserId() : a.getAccountUserId();
        String oa = o.getAccountUserId() == null || o.getAccountUserId().isBlank() ? o.getUserId() : o.getAccountUserId();
        if (!aa.equals(oa)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "access_denied");
    }
    static String redirectUri(String value) {
        try {
            if (value == null || value.length() > 2048) throw error("invalid_request");
            var uri = URI.create(value);
            boolean secure = "https".equals(uri.getScheme());
            boolean loopback = "http".equals(uri.getScheme()) && uri.getHost() != null && List.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
            if ((!secure && !loopback) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || value.contains("*")) throw error("invalid_request");
            return value;
        } catch (IllegalArgumentException ex) { throw error("invalid_request"); }
    }
    private String randomValue(String prefix) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String bounded(String value) {
        if (value == null || value.length() > 256) throw error("invalid_grant");
        return value;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static ResponseStatusException error(String value) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, value); }
}
