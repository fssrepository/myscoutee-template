package com.raxim.myscoutee.domain.accounts.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.accounts.document.UserLoginAudit;
import com.raxim.myscoutee.domain.accounts.document.UserSessionState;
import com.raxim.myscoutee.domain.accounts.document.UserSessionState.ActiveSession;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSessionLoginRequestDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSessionLoginResponseDto;
import com.raxim.myscoutee.domain.accounts.repository.UserLoginAuditsRepository;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class UserLoginSessionService {
    public static final String SESSION_ID_HEADER = com.raxim.myscoutee.domain.accounts.api.AccountMediaSessions.SESSION_ID_HEADER;

    private static final int MAX_OPAQUE_ID_LENGTH = 128;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final java.time.Duration SESSION_TTL = java.time.Duration.ofHours(8);
    private static final String SECURITY_NOTIFICATION_TITLE_KEY =
            "notification.security.login.title";
    private static final String SECURITY_NEW_LOGIN_MESSAGE_KEY =
            "notification.security.login.new.message";
    private static final String SECURITY_BLOCKED_LOGIN_MESSAGE_KEY =
            "notification.security.login.blocked.message";

    private final MongoTemplate mongoTemplate;
    private final UserLoginAuditsRepository userLoginAuditsRepository;
    private final NotificationDelivery notificationOutboxService;
    private final int maxActiveSessions;

    public UserLoginSessionService(
            MongoTemplate mongoTemplate,
            UserLoginAuditsRepository userLoginAuditsRepository,
            NotificationDelivery notificationOutboxService,
            @Value("${auth.max-active-sessions:-1}") int maxActiveSessions) {
        this.mongoTemplate = mongoTemplate;
        this.userLoginAuditsRepository = userLoginAuditsRepository;
        this.notificationOutboxService = notificationOutboxService;
        if (maxActiveSessions != -1 && maxActiveSessions < 1) {
            throw new IllegalArgumentException("auth.max-active-sessions must be -1 or a positive number.");
        }
        this.maxActiveSessions = maxActiveSessions;
    }

    public UserSessionLoginResponseDto registerLogin(
            String requestedSessionId,
            UserSessionLoginRequestDto request,
            HttpServletRequest servletRequest) {
        String userId = requiredCurrentUserId();
        String sessionId = requiredOpaqueId(requestedSessionId, "Session id is required.");
        String attemptId = requiredOpaqueId(
                request == null ? null : request.attemptId(),
                "Login attempt id is required.");
        String provider = verifiedProvider(request == null ? null : request.provider());
        ClientDescriptor client = clientDescriptor(servletRequest);

        UserLoginAudit prior = userLoginAuditsRepository.findFirstByAttemptId(attemptId).orElse(null);
        if (prior != null) {
            if (!userId.equals(safeText(prior.getUserId()))
                    || !sessionId.equals(safeText(prior.getSessionId()))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Login attempt identity is already in use.");
            }
            return responseFromAudit(prior, activeSessionCount(userId), true);
        }

        String nowIso = Instant.now().toString();
        ensureState(userId, nowIso);
        if (com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode()
                != com.raxim.myscoutee.common.config.mongo.MongoSessionMode.DEMO) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(userId)),
                    new Update().pull("activeSessions", new Document("$or", List.of(
                            new Document("accessTokenExpiresAt", new Document("$lte", Instant.now())),
                            new Document("accessTokenExpiresAt", new Document("$exists", false))))), UserSessionState.class);
        }
        UserSessionState before = findState(userId);
        ActiveSession current = findActiveSession(before, sessionId);
        boolean existingSession = current != null;
        boolean accepted = existingSession;
        UserSessionState after = before;

        if (existingSession) {
            touchSession(userId, sessionId, nowIso);
            after = findState(userId);
        } else {
            ActiveSession next = new ActiveSession();
            next.setSessionId(sessionId);
            if (com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode()
                    != com.raxim.myscoutee.common.config.mongo.MongoSessionMode.DEMO) {
                next.setAccessTokenExpiresAt(Instant.now().plus(SESSION_TTL));
            }
            next.setClientLabel(client.label());
            next.setProvider(provider);
            next.setCreatedAtIso(nowIso);
            next.setLastSeenAtIso(nowIso);
            Criteria admission = new Criteria().andOperator(
                    Criteria.where("_id").is(userId),
                    Criteria.where("activeSessions.sessionId").ne(sessionId));
            Query admissionQuery = Query.query(admission);
            if (maxActiveSessions > 0) {
                admissionQuery.addCriteria(Criteria.where("activeSessions." + (maxActiveSessions - 1)).exists(false));
            }
            after = mongoTemplate.findAndModify(
                    admissionQuery,
                    new Update()
                            .push("activeSessions", next)
                            .set("updatedDate", nowIso),
                    FindAndModifyOptions.options().returnNew(true),
                    UserSessionState.class);
            accepted = after != null;
            if (!accepted) {
                after = findState(userId);
                existingSession = findActiveSession(after, sessionId) != null;
                accepted = existingSession;
            }
        }

        if (!accepted && maxActiveSessions == -1) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Unable to register the session.");
        }

        String outcome = accepted
                ? UserLoginAudit.OUTCOME_ACCEPTED
                : UserLoginAudit.OUTCOME_BLOCKED_LIMIT;
        UserLoginAudit audit = saveAudit(
                userId,
                sessionId,
                attemptId,
                provider,
                outcome,
                client,
                servletRequest,
                nowIso);
        int activeCount = activeSessionCount(after);
        if ((!existingSession && accepted && activeCount > 1) || !accepted) {
            enqueueSecurityWarning(audit, accepted);
        }
        return responseFromAudit(audit, activeCount, existingSession);
    }

    public boolean revokeCurrentSession(String requestedSessionId) {
        String userId = requiredCurrentUserId();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String sessionId = authentication != null && authentication.getCredentials() instanceof
                com.raxim.myscoutee.domain.accounts.security.session.AccountSessionCredential credential
                ? credential.sessionId() : normalizeOpaqueId(requestedSessionId);
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        var result = mongoTemplate.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(userId),
                        Criteria.where("activeSessions.sessionId").is(sessionId))),
                new Update()
                        .pull("activeSessions", new Document("sessionId", sessionId))
                        .set("updatedDate", Instant.now().toString()),
                UserSessionState.class);
        return result.getModifiedCount() > 0L;
    }

    public boolean isCurrentSessionActive(String requestedSessionId) {
        String userId = requiredCurrentUserId();
        String sessionId = normalizeOpaqueId(requestedSessionId);
        return StringUtils.hasText(sessionId)
                && mongoTemplate.exists(
                        Query.query(new Criteria().andOperator(
                                Criteria.where("_id").is(userId),
                                Criteria.where("activeSessions.sessionId").is(sessionId))),
                        UserSessionState.class);
    }

    public boolean isCurrentUserEnrolled() {
        String userId = requiredCurrentUserId();
        return mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(userId)),
                UserSessionState.class);
    }

    private void ensureState(String userId, String nowIso) {
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(userId)),
                new Update()
                        .setOnInsert("userId", userId)
                        .setOnInsert("activeSessions", List.of())
                        .setOnInsert("createdDate", nowIso)
                        .setOnInsert("updatedDate", nowIso),
                UserSessionState.class);
    }

    private void touchSession(String userId, String sessionId, String nowIso) {
        mongoTemplate.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(userId),
                        Criteria.where("activeSessions.sessionId").is(sessionId))),
                new Update()
                        .set("activeSessions.$.lastSeenAtIso", nowIso)
                        .set("updatedDate", nowIso),
                UserSessionState.class);
    }

    private UserLoginAudit saveAudit(
            String userId,
            String sessionId,
            String attemptId,
            String provider,
            String outcome,
            ClientDescriptor client,
            HttpServletRequest request,
            String nowIso) {
        UserLoginAudit audit = new UserLoginAudit();
        audit.setId(new ObjectId().toHexString());
        audit.setAttemptId(attemptId);
        audit.setUserId(userId);
        audit.setSessionId(sessionId);
        audit.setClientLabel(client.label());
        audit.setBrowser(client.browser());
        audit.setOperatingSystem(client.operatingSystem());
        audit.setDeviceType(client.deviceType());
        audit.setProvider(provider);
        audit.setOutcome(outcome);
        audit.setSourceAddress(truncate(request == null ? null : request.getRemoteAddr(), 128));
        audit.setUserAgentHash(sha256(request == null ? null : request.getHeader("User-Agent")));
        audit.setAttemptedAtIso(nowIso);
        try {
            return userLoginAuditsRepository.save(audit);
        } catch (DuplicateKeyException exception) {
            UserLoginAudit raced = userLoginAuditsRepository.findFirstByAttemptId(attemptId).orElse(null);
            if (raced != null
                    && userId.equals(safeText(raced.getUserId()))
                    && sessionId.equals(safeText(raced.getSessionId()))) {
                return raced;
            }
            throw exception;
        }
    }

    private void enqueueSecurityWarning(UserLoginAudit audit, boolean accepted) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = authentication != null && authentication.getAuthorities().stream().anyMatch(a -> "O".equals(a.getAuthority()))
                ? "/operator" : authentication != null && authentication.getAuthorities().stream().anyMatch(a -> "A".equals(a.getAuthority()))
                ? "/admin" : "/game";
        enqueueSecurityWarning(audit, accepted, path);
    }

    private void enqueueSecurityWarning(UserLoginAudit audit, boolean accepted, String path) {
        String attemptId = safeText(audit.getAttemptId());
        String clientLabel = safeText(audit.getClientLabel());
        String messageKey = accepted
                ? SECURITY_NEW_LOGIN_MESSAGE_KEY
                : SECURITY_BLOCKED_LOGIN_MESSAGE_KEY;
        String fallback = accepted
                ? "New login from " + clientLabel + "."
                : "Blocked login attempt from " + clientLabel + " because the active-session limit has been reached.";
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("notification_title_key", SECURITY_NOTIFICATION_TITLE_KEY);
        payload.put("notification_message_key", messageKey);
        payload.put("clientLabel", clientLabel);
        payload.put("loginOutcome", safeText(audit.getOutcome()));
        payload.put("loginAttemptId", attemptId);
        payload.put("notification_tone", accepted ? "warning" : "danger");
        notificationOutboxService.enqueueOnce("security-login:" + attemptId, new NotificationIntent(
                "security-login",
                "security",
                attemptId,
                "account",
                List.of(audit.getUserId()),
                "Security alert",
                fallback,
                path,
                null,
                null,
                "security-login-" + attemptId,
                payload,
                audit.getUserId()));
    }

    private UserSessionLoginResponseDto responseFromAudit(
            UserLoginAudit audit,
            int activeCount,
            boolean existingSession) {
        boolean accepted = UserLoginAudit.OUTCOME_ACCEPTED.equals(audit.getOutcome());
        return new UserSessionLoginResponseDto(
                accepted,
                existingSession,
                audit.getOutcome(),
                activeCount,
                maxActiveSessions,
                accepted ? null : "The active-session limit has been reached.", null);
    }

    public UserSessionLoginResponseDto completeLogin(UserSessionLoginResponseDto response, String sessionId) {
        if (!response.accepted() || com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode()
                == com.raxim.myscoutee.common.config.mongo.MongoSessionMode.DEMO) return response;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof FirebasePrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is required.");
        }
        String userId = requiredCurrentUserId();
        sessionId = requiredOpaqueId(sessionId, "Session id is required.");
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String token = encoder.encodeToString(userId.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)) + "."
                + java.util.HexFormat.of().formatHex(nonce);
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        var update = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(userId).and("activeSessions.sessionId").is(sessionId)),
                new Update().set("activeSessions.$.accessTokenHash", sha256(token))
                        .set("activeSessions.$.accessTokenExpiresAt", expiresAt)
                        .set("activeSessions.$.emailVerified", principal.isEmailVerified()),
                UserSessionState.class);
        if (update.getMatchedCount() != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "App session is not active.");
        }
        var user = principal.getUser();
        String name = user == null || !StringUtils.hasText(user.getName()) ? principal.getUsername() : user.getName();
        String initials = user == null || !StringUtils.hasText(user.getInitials())
                ? name.substring(0, 1).toUpperCase(Locale.ROOT) : user.getInitials();
        var profile = new com.raxim.myscoutee.domain.accounts.dto.AuthDtos.AppSessionProfileDto(
                userId, name, principal.getEmail() == null ? "" : principal.getEmail(), initials,
                user == null || user.getImages() == null ? null : user.getImages().stream()
                        .filter(StringUtils::hasText).findFirst().orElse(null));
        var session = new com.raxim.myscoutee.domain.accounts.dto.AuthDtos.AppSessionDto(
                sessionId, token, expiresAt.toString(), profile);
        return new UserSessionLoginResponseDto(response.accepted(), response.existingSession(), response.outcome(),
                response.activeSessionCount(), response.maxActiveSessions(), response.message(), session);
    }

    public com.raxim.myscoutee.domain.accounts.security.session.AccountSessionCredential authenticateAccessToken(String token) {
        if (token == null || token.length() > 1024) return null;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !parts[2].matches("[0-9a-f]{64}")) return null;
        String userId;
        String sessionId;
        try {
            var decoder = java.util.Base64.getUrlDecoder();
            userId = safeText(new String(decoder.decode(parts[0]), StandardCharsets.UTF_8));
            sessionId = normalizeOpaqueId(new String(decoder.decode(parts[1]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        if (userId.isEmpty() || userId.length() > 256 || sessionId.isEmpty()) return null;
        Criteria active = Criteria.where("sessionId").is(sessionId).and("accessTokenHash").is(sha256(token))
                .and("accessTokenExpiresAt").gt(Instant.now());
        Query query = Query.query(Criteria.where("_id").is(userId).and("activeSessions").elemMatch(active));
        query.fields().elemMatch("activeSessions", active);
        UserSessionState state = mongoTemplate.findOne(query, UserSessionState.class);
        ActiveSession session = findActiveSession(state, sessionId);
        return session == null ? null : new com.raxim.myscoutee.domain.accounts.security.session.AccountSessionCredential(
                userId, sessionId, session.isEmailVerified());
    }

    private UserSessionState findState(String userId) {
        return mongoTemplate.findById(userId, UserSessionState.class);
    }

    private ActiveSession findActiveSession(UserSessionState state, String sessionId) {
        if (state == null || state.getActiveSessions() == null) {
            return null;
        }
        return state.getActiveSessions().stream()
                .filter(item -> item != null && sessionId.equals(safeText(item.getSessionId())))
                .findFirst()
                .orElse(null);
    }

    private int activeSessionCount(String userId) {
        return activeSessionCount(findState(userId));
    }

    private int activeSessionCount(UserSessionState state) {
        return state == null || state.getActiveSessions() == null
                ? 0
                : state.getActiveSessions().size();
    }

    private String requiredCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof
                com.raxim.myscoutee.domain.accounts.security.session.AccountSessionCredential session) return session.userId();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        String userId = principal instanceof FirebasePrincipal firebasePrincipal
                ? safeText(firebasePrincipal.getUserId())
                : "";
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required.");
        }
        return userId;
    }

    private String verifiedProvider(String requestedProvider) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object credentials = authentication == null ? null : authentication.getCredentials();
        if (credentials instanceof FirebaseCredential firebaseCredential) {
            if (StringUtils.hasText(firebaseCredential.getGoogleId())) {
                return "google";
            }
            if (StringUtils.hasText(firebaseCredential.getFacebookId())) {
                return "facebook";
            }
            return "firebase";
        }
        if (credentials instanceof AccountLoginService.PasswordCredential) return "password";
        String normalized = safeText(requestedProvider).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "google", "facebook", "email", "firebase", "demo" -> normalized;
            default -> "unknown";
        };
    }

    private ClientDescriptor clientDescriptor(HttpServletRequest request) {
        String userAgent = safeText(request == null ? null : request.getHeader("User-Agent"));
        String lower = userAgent.toLowerCase(Locale.ROOT);
        String browser = lower.contains("edg/")
                ? "Edge"
                : lower.contains("firefox/")
                        ? "Firefox"
                        : lower.contains("chrome/") || lower.contains("crios/")
                                ? "Chrome"
                                : lower.contains("safari/")
                                        ? "Safari"
                                        : "Browser";
        String operatingSystem = lower.contains("android")
                ? "Android"
                : lower.contains("iphone") || lower.contains("ipad")
                        ? "iOS"
                        : lower.contains("windows")
                                ? "Windows"
                                : lower.contains("mac os") || lower.contains("macintosh")
                                        ? "macOS"
                                        : lower.contains("linux")
                                                ? "Linux"
                                                : "unknown OS";
        String deviceType = lower.contains("mobile") || lower.contains("android")
                || lower.contains("iphone")
                        ? "mobile"
                        : "desktop";
        return new ClientDescriptor(
                browser,
                operatingSystem,
                deviceType,
                browser + " on " + operatingSystem + " " + deviceType);
    }

    private String requiredOpaqueId(String value, String message) {
        String normalized = normalizeOpaqueId(value);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOpaqueId(String value) {
        String normalized = safeText(value);
        if (normalized.length() > MAX_OPAQUE_ID_LENGTH
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            return "";
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(safeText(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash user-agent metadata.", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = safeText(value);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record ClientDescriptor(
            String browser,
            String operatingSystem,
            String deviceType,
            String label) {
    }
}
