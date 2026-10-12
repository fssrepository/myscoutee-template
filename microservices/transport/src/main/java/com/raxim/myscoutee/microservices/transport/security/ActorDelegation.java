package com.raxim.myscoutee.microservices.transport.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import com.raxim.myscoutee.domain.accounts.api.AccountIdentity;
import com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor;
import com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/** Short-lived identity-issued authority, bound to one operation and exact request body. */
public final class ActorDelegation {
    public static final String HEADER = "X-MyScoutee-Actor-Delegation";
    public static final String ISSUER = "myscoutee:accounts:delegation:v1";
    private ActorDelegation() {}
    public interface Signer {
        String issueForCurrent(String audience, String method, String path, byte[] body);
        String issue(String userId, String audience, String method, String path, byte[] body);
    }
    public record Actor(String profileId, String accountId, String workspaceGroupId) {}
    /** Constructible only after this verifier has checked the operation-bound signature. */
    public static final class VerifiedPrincipal implements AuthenticatedActor,
            org.springframework.security.core.AuthenticatedPrincipal {
        private final ProfileIdentityDto profile;
        private final String email;
        private final String delegation;
        private final Instant expiresAt;
        private final String mode;
        private final List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities;
        private VerifiedPrincipal(ProfileIdentityDto profile, String email, List<String> authorities, String delegation, Instant expiresAt, String mode) {
            this.profile = profile;
            this.email = email;
            this.delegation = delegation;
            this.expiresAt = expiresAt;
            this.mode = mode;
            this.authorities = authorities.stream()
                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();
        }
        @Override public String getUserId() { return profile.userId(); }
        @Override public String getEmail() { return email; }
        @Override public String getName() { return email.isBlank() ? getUserId() : email; }
        @Override public ProfileIdentityDto getProfileIdentity() { return profile; }
        @Override public List<org.springframework.security.core.authority.SimpleGrantedAuthority> getAuthorities() { return authorities; }
        String delegation() { return delegation; }
        void requireCurrentMode() {
            if (!mode.equals(MongoSessionModeContext.currentMode().name()))
                throw new AccessDeniedException("Actor delegation mode mismatch");
        }
    }
    public static String digest(byte[] body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
    private static RSAPublicKey publicKey(String encoded) {
        try {
            var key = (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (key.getModulus().bitLength() < 2048) throw new IllegalArgumentException("Delegation key too small");
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid delegation public key"); }
    }
    private static String optional(String value) { return value == null ? "" : value; }
    public static final class Issuer implements Signer {
        private final JwtEncoder encoder;
        private final Clock clock;
        public Issuer(String privateKey, String publicKey, Clock clock) {
            try {
                var privateRsa = (RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
                var publicRsa = publicKey(publicKey);
                if (!privateRsa.getModulus().equals(publicRsa.getModulus())) throw new IllegalArgumentException("Delegation keys do not match");
                encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new com.nimbusds.jose.jwk.RSAKey.Builder(publicRsa).privateKey(privateRsa).build())));
                this.clock = clock;
            } catch (GeneralSecurityException invalid) { throw new IllegalArgumentException("Invalid delegation private key"); }
        }
        public String issueForCurrent(String audience, String method, String path, byte[] body) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor principal))
                throw new AccessDeniedException("Authenticated selected profile required");
            return issue(principal.getUserId(), audience, method, path, body);
        }
        public String issue(String userId, String audience, String method, String path, byte[] body) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof VerifiedPrincipal delegated) {
                if (!delegated.getUserId().equals(userId)) throw new AccessDeniedException("Actor mismatch");
                return issueDerived(delegated, audience, method, path, digest(body));
            }
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof AccountIdentity principal)
                    || userId == null || !userId.equals(principal.getUserId()) || principal.getUser() == null
                    || !userId.equals(principal.getUser().getUserId())) throw new AccessDeniedException("Authenticated selected profile required");
            var now = clock.instant();
            String email = optional(principal.getEmail());
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
            if (email.length() > 512 || !validAuthorities(authorities))
                throw new AccessDeniedException("Invalid actor authority");
            return encode(new ProfileIdentityDto(userId, principal.getUser().getAccountUserId(),
                    principal.getUser().getWorkspaceGroupId()), email, authorities, now, now.plusSeconds(30),
                    audience, method, path, digest(body));
        }
        String issueDerived(VerifiedPrincipal parent, String audience, String method, String path, String bodyDigest) {
            parent.requireCurrentMode();
            Instant now = clock.instant();
            if (!parent.expiresAt.isAfter(now)) throw new AccessDeniedException("Expired actor delegation");
            // Delegation never renews a session or extends the original authority window.
            return encode(parent.profile, parent.email, parent.authorities.stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList(),
                    now, parent.expiresAt, audience, method, path, bodyDigest);
        }
        private String encode(ProfileIdentityDto profile, String email, List<String> authorities,
                Instant now, Instant expiresAt, String audience, String method, String path, String bodyDigest) {
            var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(profile.userId()).audience(List.of(audience))
                    .issuedAt(now).notBefore(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                    .claim("account", optional(profile.accountUserId()))
                    .claim("workspace", optional(profile.workspaceGroupId()))
                    .claim("account_present", profile.accountUserId() != null)
                    .claim("workspace_present", profile.workspaceGroupId() != null)
                    .claim("authority_version", 1).claim("email", email).claim("authorities", authorities)
                    .claim("mode", MongoSessionModeContext.currentMode().name()).claim("method", method)
                    .claim("path", path).claim("body_sha256", bodyDigest).build();
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
            if (token.length() > 8192) throw new AccessDeniedException("Actor delegation exceeds limit");
            return token;
        }
    }
    private static boolean validAuthorities(List<?> authorities) {
        return authorities != null && authorities.size() <= 64 && authorities.stream()
                .allMatch(value -> value instanceof String text && !text.isBlank() && text.length() <= 256);
    }
    public static final class Verifier {
        private final NimbusJwtDecoder decoder;
        private final Clock clock;
        public Verifier(String publicKey, Clock clock) {
            decoder = NimbusJwtDecoder.withPublicKey(publicKey(publicKey)).build();
            decoder.setJwtValidator(new JwtIssuerValidator(ISSUER));
            this.clock = clock;
        }
        public Actor verify(String token, String audience, String method, String path, byte[] body) {
            return actor(verifiedJwt(token, audience, method, path, digest(body)));
        }
        public VerifiedPrincipal verifyPrincipal(String token, String audience, String method, String path, byte[] body) {
            return principal(verifiedJwt(token, audience, method, path, digest(body)), token);
        }
        /** Broker-only: authorize forwarding by the signed recipient, without executing the parent operation. */
        VerifiedPrincipal verifyForRecipient(String token, Set<String> permittedAudiences) {
            try {
                if (token == null || token.length() > 8192) throw new IllegalArgumentException();
                var jwt = decoder.decode(token);
                if (jwt.getAudience().size() != 1 || !permittedAudiences.contains(jwt.getAudience().getFirst()))
                    throw new IllegalArgumentException();
                String method = jwt.getClaimAsString("method"), path = jwt.getClaimAsString("path");
                String bodyDigest = jwt.getClaimAsString("body_sha256");
                if (method == null || !method.matches("GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS")
                        || path == null || !path.startsWith("/") || path.length() > 4096
                        || bodyDigest == null || !bodyDigest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
                return principal(verifiedJwt(token, jwt.getAudience().getFirst(), method, path, bodyDigest), token);
            } catch (RuntimeException invalid) { throw new AccessDeniedException("Invalid actor delegation"); }
        }
        private VerifiedPrincipal principal(Jwt jwt, String token) {
            try {
                if (!(jwt.getClaims().get("authority_version") instanceof Number version) || version.doubleValue() != 1d
                        || !(jwt.getClaims().get("email") instanceof String email) || email.length() > 512
                        || !(jwt.getClaims().get("authorities") instanceof List<?> authorities)
                        || !(jwt.getClaims().get("account_present") instanceof Boolean accountPresent)
                        || !(jwt.getClaims().get("workspace_present") instanceof Boolean workspacePresent)
                        || !validAuthorities(authorities)) throw new IllegalArgumentException();
                var actor = actor(jwt);
                if ((!accountPresent && !actor.accountId().isEmpty())
                        || (!workspacePresent && !actor.workspaceGroupId().isEmpty())) throw new IllegalArgumentException();
                var profile = new ProfileIdentityDto(actor.profileId(), accountPresent ? actor.accountId() : null,
                        workspacePresent ? actor.workspaceGroupId() : null);
                return new VerifiedPrincipal(profile, email, authorities.stream().map(String.class::cast).toList(), token, jwt.getExpiresAt(), jwt.getClaimAsString("mode"));
            } catch (RuntimeException invalid) { throw new AccessDeniedException("Invalid actor delegation"); }
        }
        private static Actor actor(Jwt jwt) {
            return new Actor(jwt.getSubject(), jwt.getClaimAsString("account"), jwt.getClaimAsString("workspace"));
        }
        private Jwt verifiedJwt(String token, String audience, String method, String path, String bodyDigest) {
            try {
                if (token == null || token.length() > 8192) throw new IllegalArgumentException();
                var jwt = decoder.decode(token);
                var now = clock.instant();
                if (!List.of(audience).equals(jwt.getAudience()) || jwt.getIssuedAt() == null || jwt.getNotBefore() == null
                        || jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                        || jwt.getSubject().length()>512 || jwt.getId()==null
                        || jwt.getIssuedAt().isAfter(now.plusSeconds(2)) || jwt.getNotBefore().isAfter(now.plusSeconds(2))
                        || !jwt.getExpiresAt().isAfter(now) || jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plusSeconds(30))
                        || !method.equals(jwt.getClaimAsString("method")) || !path.equals(jwt.getClaimAsString("path"))
                        || !MongoSessionModeContext.currentMode().name().equals(jwt.getClaimAsString("mode"))
                        || !bodyDigest.equals(jwt.getClaimAsString("body_sha256"))
                        || !(jwt.getClaims().get("account") instanceof String account) || account.length()>512
                        || !(jwt.getClaims().get("workspace") instanceof String workspace) || workspace.length()>512)
                    throw new IllegalArgumentException();
                return jwt;
            } catch (RuntimeException invalid) { throw new AccessDeniedException("Invalid actor delegation"); }
        }
    }
}
