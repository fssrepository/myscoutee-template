package com.raxim.myscoutee.domain.integrations.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.integrations.document.IntegrationToken;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationSettingsDto;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationTokenCreateRequestDto;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationTokenCreatedDto;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationTokenDto;
import com.raxim.myscoutee.domain.integrations.repository.IntegrationTokensRepository;

@Service
public class IntegrationTokenService {
    public static final String MCP_SCOPE = "mcp";
    public static final String ADMIN_SCOPE = "admin-client";
    public static final String INTEGRATION_SCOPE = "integration";
    public static final int MAX_ACTIVE_TOKENS = 3;
    public static final int MAX_BATCH_SIZE = 1000;
    private static final int DEFAULT_EXPIRY_DAYS = 90;
    private static final int MAX_EXPIRY_DAYS = 365;
    private static final String TOKEN_PREFIX = "msc_";

    private final IntegrationTokensRepository tokensRepository;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public IntegrationTokenService(
            IntegrationTokensRepository tokensRepository,
            MongoTemplate mongoTemplate) {
        this.tokensRepository = tokensRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public IntegrationSettingsDto settings(String ownerUserId, String baseUrl) {
        return settings(ownerUserId, baseUrl, INTEGRATION_SCOPE);
    }

    public IntegrationSettingsDto settings(String ownerUserId, String baseUrl, String scope) {
        return settings(ownerUserId, baseUrl, scope, activeTokens(ownerUserId));
    }

    List<IntegrationToken> activeTokens(String ownerUserId) {
        return tokensRepository.findByOwnerUserIdOrderByCreatedAtIsoDesc(requireOwner(ownerUserId)).stream()
                .filter(this::isActive).toList();
    }

    IntegrationSettingsDto settings(String ownerUserId, String baseUrl, String scope, List<IntegrationToken> active) {
        requireOwner(ownerUserId);
        List<IntegrationTokenDto> activeTokens = active.stream()
                .filter(token -> matchesScope(token, scope))
                .map(this::toDto)
                .toList();
        return new IntegrationSettingsDto(
                baseUrl,
                MAX_ACTIVE_TOKENS,
                MAX_BATCH_SIZE,
                activeTokens);
    }

    public synchronized IntegrationTokenCreatedDto create(
            String ownerUserId,
            IntegrationTokenCreateRequestDto request) {
        return create(ownerUserId, request, INTEGRATION_SCOPE);
    }

    public synchronized IntegrationTokenCreatedDto create(String ownerUserId, IntegrationTokenCreateRequestDto request, String scope) {
        String owner = requireOwner(ownerUserId);
        long activeCount = tokensRepository.findByOwnerUserIdOrderByCreatedAtIsoDesc(owner).stream()
                .filter(this::isActive).filter(token -> matchesScope(token, scope))
                .count();
        if (activeCount >= MAX_ACTIVE_TOKENS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A user can have at most " + MAX_ACTIVE_TOKENS + " active integration keys.");
        }

        int expiresInDays = request == null || request.expiresInDays() == null
                ? DEFAULT_EXPIRY_DAYS
                : request.expiresInDays();
        if (expiresInDays < 1 || expiresInDays > MAX_EXPIRY_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Token expiration must be between 1 and " + MAX_EXPIRY_DAYS + " days.");
        }

        String value = generateTokenValue();
        Instant now = Instant.now();
        IntegrationToken token = new IntegrationToken();
        token.setId(UUID.randomUUID().toString());
        token.setOwnerUserId(owner);
        token.setScope(scope);
        token.setName(normalizeName(request == null ? null : request.name(), activeCount + 1));
        token.setTokenHash(hash(value));
        token.setTokenPrefix(value.substring(0, Math.min(value.length(), 12)));
        token.setCreatedAtIso(now.toString());
        token.setExpiresAtIso(now.plus(expiresInDays, ChronoUnit.DAYS).toString());
        IntegrationToken saved = tokensRepository.save(token);
        return new IntegrationTokenCreatedDto(toDto(saved), value);
    }

    public void revoke(String ownerUserId, String tokenId) { revoke(ownerUserId, tokenId, INTEGRATION_SCOPE); }

    public void revoke(String ownerUserId, String tokenId, String scope) {
        String owner = requireOwner(ownerUserId);
        String id = normalize(tokenId);
        IntegrationToken token = tokensRepository.findByIdAndOwnerUserId(id, owner).filter(value -> matchesScope(value, scope))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration key not found."));
        if (!StringUtils.hasText(token.getRevokedAtIso())) {
            token.setRevokedAtIso(Instant.now().toString());
            tokensRepository.save(token);
        }
    }

    public String authenticate(String rawToken, String clientId, String clientAddress) {
        AuthenticatedIntegrationClient client = authenticateClient(rawToken, clientId, clientAddress);
        return client == null ? null : client.ownerUserId();
    }

    public AuthenticatedIntegrationClient authenticateClient(
            String rawToken,
            String clientId,
            String clientAddress) {
        return authenticateClient(rawToken, clientId, clientAddress, INTEGRATION_SCOPE);
    }

    public AuthenticatedIntegrationClient authenticateClient(String rawToken, String clientId, String clientAddress, String scope) {
        String value = normalize(rawToken);
        String normalizedClientId = normalizeClientId(clientId);
        if (!StringUtils.hasText(value)
                || !value.startsWith(TOKEN_PREFIX)
                || !StringUtils.hasText(normalizedClientId)) {
            return null;
        }
        String now = Instant.now().toString();
        String clientIdHash = hash(normalizedClientId);
        Criteria claimableClient = new Criteria().orOperator(
                Criteria.where("claimedClientIdHash").exists(false),
                Criteria.where("claimedClientIdHash").is(null),
                Criteria.where("claimedClientIdHash").is(""),
                Criteria.where("claimedClientIdHash").is(clientIdHash));
        Criteria activeToken = new Criteria().andOperator(
                Criteria.where("tokenHash").is(hash(value)),
                Criteria.where("revokedAtIso").in(null, ""),
                Criteria.where("expiresAtIso").gt(now),
                claimableClient, INTEGRATION_SCOPE.equals(scope)
                        ? Criteria.where("scope").in(null, "", INTEGRATION_SCOPE)
                        : Criteria.where("scope").is(scope));
        IntegrationToken token = mongoTemplate.findAndModify(
                Query.query(activeToken),
                new Update()
                        .set("claimedClientIdHash", clientIdHash)
                        .set("lastUsedAtIso", now),
                FindAndModifyOptions.options().returnNew(true),
                IntegrationToken.class);
        if (token == null || !isActive(token) || !matchesScope(token, scope)) {
            return null;
        }
        if (!StringUtils.hasText(token.getClaimedAtIso())) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(token.getId())
                            .and("claimedAtIso").in(null, "")),
                    new Update()
                            .set("claimedAtIso", now)
                            .set("claimedAddress", normalizeAddress(clientAddress)),
                    IntegrationToken.class);
        }
        return new AuthenticatedIntegrationClient(
                normalize(token.getOwnerUserId()),
                token.getId(),
                normalizedClientId, scope);
    }

    public boolean isActiveForOwner(String ownerUserId, String tokenId) {
        String owner = normalize(ownerUserId);
        String id = normalize(tokenId);
        return StringUtils.hasText(owner)
                && StringUtils.hasText(id)
                && tokensRepository.findByIdAndOwnerUserId(id, owner).filter(this::isActive).filter(token -> matchesScope(token, INTEGRATION_SCOPE)).isPresent();
    }

    private boolean matchesScope(IntegrationToken token, String scope) {
        return scope.equals(StringUtils.hasText(token.getScope()) ? token.getScope() : INTEGRATION_SCOPE);
    }

    boolean isActive(IntegrationToken token) {
        if (token == null || StringUtils.hasText(token.getRevokedAtIso())) {
            return false;
        }
        try {
            return Instant.parse(token.getExpiresAtIso()).isAfter(Instant.now());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    IntegrationTokenDto toDto(IntegrationToken token) {
        return new IntegrationTokenDto(
                token.getId(),
                token.getName(),
                token.getTokenPrefix(),
                token.getCreatedAtIso(),
                token.getExpiresAtIso(),
                token.getClaimedAtIso(),
                token.getClaimedAddress(),
                token.getLastUsedAtIso());
    }

    private String generateTokenValue() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String normalizeName(String value, long sequence) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return "Integration key " + sequence;
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String requireOwner(String ownerUserId) {
        String owner = normalize(ownerUserId);
        if (!StringUtils.hasText(owner)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An authenticated user is required.");
        }
        return owner;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeClientId(String value) {
        String normalized = normalize(value);
        try {
            return UUID.fromString(normalized).toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String normalizeAddress(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    public record AuthenticatedIntegrationClient(
            String ownerUserId,
            String tokenId,
            String clientId,
            String scope,
            com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode accessMode,
            String grantId) implements com.raxim.myscoutee.domain.integrations.api.IntegrationClientIdentity {
        public AuthenticatedIntegrationClient(String ownerUserId, String tokenId, String clientId, String scope,
                com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode accessMode) {
            this(ownerUserId, tokenId, clientId, scope, accessMode, null);
        }
        public AuthenticatedIntegrationClient(String ownerUserId, String tokenId, String clientId) {
            this(ownerUserId, tokenId, clientId, INTEGRATION_SCOPE);
        }
        public AuthenticatedIntegrationClient(String ownerUserId, String tokenId, String clientId, String scope) {
            this(ownerUserId, tokenId, clientId, scope,
                    com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.WRITE);
        }
        public AuthenticatedIntegrationClient withAccess(com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode mode) {
            return new AuthenticatedIntegrationClient(ownerUserId, tokenId, clientId, scope, mode, grantId);
        }
    }
}
