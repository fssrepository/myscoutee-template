package com.raxim.myscoutee.microservices.transport.access;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteAccessPermissions extends RemoteReadTransport implements AccessPermissions {
    private final ObjectMapper mapper;
    private final ActorDelegation.Signer signer;
    public RemoteAccessPermissions(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, ActorDelegation.Signer signer) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper; this.signer = signer;
    }
    @Override public boolean can(String policyKey, String actorUserId, String subjectUserId, String resourceId) {
        var query = new AccessPermissionsProtocol.CanQuery(policyKey, actorUserId, subjectUserId, resourceId);
        return read(AccessPermissionsProtocol.CAN, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public String resolveChatActorUserId(String chatId, String profileId) {
        var query = new AccessPermissionsProtocol.ResolveChatActorUserIdQuery(chatId, profileId);
        return read(AccessPermissionsProtocol.RESOLVE_CHAT_ACTOR_USER_ID, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.ActorResult>() {}, value -> Boolean.TRUE.equals(value.resolved()) && (value.userId() == null || value.userId().length() <= 512)).userId();
    }




























    @Override public boolean canCurrentActorCreateChatMessage(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorCreateChatMessageQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_CREATE_CHAT_MESSAGE, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorEditChatMessage(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorEditChatMessageQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorDeleteChatMessage(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorDeleteChatMessageQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_CHAT_MESSAGE, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorPinChatMessage(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorPinChatMessageQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_PIN_CHAT_MESSAGE, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorManageChatReaction(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorManageChatReactionQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_MANAGE_CHAT_REACTION, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorAddChatAttachment(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorAddChatAttachmentQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_ADD_CHAT_ATTACHMENT, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorManageSupportChat(String userId, String chatId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorManageSupportChatQuery(userId, chatId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_MANAGE_SUPPORT_CHAT, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorEditProfile(String userId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorEditProfileQuery(userId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_PROFILE, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorDeleteProfile(String userId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorDeleteProfileQuery(userId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_PROFILE, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorUploadMedia(String ownerUserId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorUploadMediaQuery(ownerUserId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_UPLOAD_MEDIA, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorImportMedia(String ownerUserId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorImportMediaQuery(ownerUserId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_IMPORT_MEDIA, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canCurrentActorSubmitPrivacyConsent(String userId) {
        var query = new AccessPermissionsProtocol.CanCurrentActorSubmitPrivacyConsentQuery(userId);
        return read(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_SUBMIT_PRIVACY_CONSENT, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }

















    @Override public boolean canAccessChat(String chatId, String actorUserId) {
        var query = new AccessPermissionsProtocol.CanAccessChatQuery(chatId, actorUserId);
        return read(AccessPermissionsProtocol.CAN_ACCESS_CHAT, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean canManageSupportChat(String chatId, String actorUserId) {
        var query = new AccessPermissionsProtocol.CanManageSupportChatQuery(chatId, actorUserId);
        return read(AccessPermissionsProtocol.CAN_MANAGE_SUPPORT_CHAT, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean hasExplicitGrant(String subjectUserId, String permission, String resourceType, String resourceId, AccessGrantEffect expectedEffect) {
        var query = new AccessPermissionsProtocol.HasExplicitGrantQuery(subjectUserId, permission, resourceType, resourceId, expectedEffect);
        return read(AccessPermissionsProtocol.HAS_EXPLICIT_GRANT, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    @Override public boolean requiresAuthenticatedActor() {
        var query = new AccessPermissionsProtocol.RequiresAuthenticatedActorQuery();
        return read(AccessPermissionsProtocol.REQUIRES_AUTHENTICATED_ACTOR, query, AccessPermissionsProtocol.valid(query),
                new TypeReference<AccessPermissionsProtocol.Decision>() {}, value -> value.allowed() != null).allowed();
    }
    private <T> T read(String path, Object query, boolean valid, TypeReference<T> type, java.util.function.Predicate<T> resultValid) {
        if (!valid) throw new IllegalArgumentException("Invalid access-permission query");
        byte[] bytes = mapper.writeValueAsBytes(query);
        if (bytes.length > AccessPermissionsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request exceeds limit");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> headers = Map.of();
        // Media cookies are validated by the public authorization owner, but confer
        // no account authority. Query the existing explicit-identity read policy;
        // do not promote this credential into an account delegation.
        boolean mediaRead = AccessPermissionsProtocol.CAN.equals(path)
                && query instanceof AccessPermissionsProtocol.CanQuery decision
                && "media.read".equals(decision.policyKey())
                && authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof String mediaUser
                && mediaUser.equals(decision.actorUserId()) && mediaUser.equals(decision.subjectUserId())
                && authentication.getAuthorities().size() == 1
                && "MEDIA".equals(authentication.getAuthorities().iterator().next().getAuthority());
        if (!mediaRead && authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && !(authentication instanceof com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter.ServiceAuthentication)) {
            headers = Map.of(ActorDelegation.HEADER, signer.issueForCurrent(AccessPermissionsProtocol.AUDIENCE, "POST", path, bytes));
        }
        try {
            return exchangeAuthorized(path, bytes, type, "access-permissions." + path.substring(path.lastIndexOf('/') + 1),
                    AccessPermissionsProtocol.MAX_RESPONSE_BYTES, resultValid, headers);
        } catch (RemoteReadException rejected) {
            if (rejected.status() == 403 && "INVALID_DELEGATION".equals(rejected.remoteCode()))
                throw new org.springframework.security.access.AccessDeniedException("Invalid actor delegation");
            if (rejected.status() >= 400 && rejected.status() < 500 && "OWNER_REJECTED".equals(rejected.remoteCode()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(rejected.status()));
            throw rejected;
        }
    }
}
