package com.raxim.myscoutee.microservices.transport.access;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import com.raxim.myscoutee.microservices.transport.security.DelegatedActorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@RestController
public final class AccessPermissionsEndpoint {
    private final AccessPermissions owner;
    private final ObjectMapper mapper;
    private final ActorDelegation.Verifier verifier;
    public AccessPermissionsEndpoint(AccessPermissions owner, ObjectMapper mapper, ActorDelegation.Verifier verifier) {
        this.owner = owner; this.mapper = mapper; this.verifier = verifier;
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN, produces = "application/json")
    public ResponseEntity<?> can(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN, AccessPermissionsProtocol.CanQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.can(query.policyKey(), query.actorUserId(), query.subjectUserId(), query.resourceId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.RESOLVE_CHAT_ACTOR_USER_ID, produces = "application/json")
    public ResponseEntity<?> resolveChatActorUserId(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.RESOLVE_CHAT_ACTOR_USER_ID, AccessPermissionsProtocol.ResolveChatActorUserIdQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.ActorResult(true, owner.resolveChatActorUserId(query.chatId(), query.profileId())));
    }




























    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_CREATE_CHAT_MESSAGE, produces = "application/json")
    public ResponseEntity<?> canCurrentActorCreateChatMessage(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_CREATE_CHAT_MESSAGE, AccessPermissionsProtocol.CanCurrentActorCreateChatMessageQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorCreateChatMessage(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE, produces = "application/json")
    public ResponseEntity<?> canCurrentActorEditChatMessage(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE, AccessPermissionsProtocol.CanCurrentActorEditChatMessageQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorEditChatMessage(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_CHAT_MESSAGE, produces = "application/json")
    public ResponseEntity<?> canCurrentActorDeleteChatMessage(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_CHAT_MESSAGE, AccessPermissionsProtocol.CanCurrentActorDeleteChatMessageQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorDeleteChatMessage(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_PIN_CHAT_MESSAGE, produces = "application/json")
    public ResponseEntity<?> canCurrentActorPinChatMessage(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_PIN_CHAT_MESSAGE, AccessPermissionsProtocol.CanCurrentActorPinChatMessageQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorPinChatMessage(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_MANAGE_CHAT_REACTION, produces = "application/json")
    public ResponseEntity<?> canCurrentActorManageChatReaction(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_MANAGE_CHAT_REACTION, AccessPermissionsProtocol.CanCurrentActorManageChatReactionQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorManageChatReaction(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_ADD_CHAT_ATTACHMENT, produces = "application/json")
    public ResponseEntity<?> canCurrentActorAddChatAttachment(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_ADD_CHAT_ATTACHMENT, AccessPermissionsProtocol.CanCurrentActorAddChatAttachmentQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorAddChatAttachment(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_MANAGE_SUPPORT_CHAT, produces = "application/json")
    public ResponseEntity<?> canCurrentActorManageSupportChat(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_MANAGE_SUPPORT_CHAT, AccessPermissionsProtocol.CanCurrentActorManageSupportChatQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorManageSupportChat(query.userId(), query.chatId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_PROFILE, produces = "application/json")
    public ResponseEntity<?> canCurrentActorEditProfile(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_PROFILE, AccessPermissionsProtocol.CanCurrentActorEditProfileQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorEditProfile(query.userId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_PROFILE, produces = "application/json")
    public ResponseEntity<?> canCurrentActorDeleteProfile(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_PROFILE, AccessPermissionsProtocol.CanCurrentActorDeleteProfileQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorDeleteProfile(query.userId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_UPLOAD_MEDIA, produces = "application/json")
    public ResponseEntity<?> canCurrentActorUploadMedia(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_UPLOAD_MEDIA, AccessPermissionsProtocol.CanCurrentActorUploadMediaQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorUploadMedia(query.ownerUserId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_IMPORT_MEDIA, produces = "application/json")
    public ResponseEntity<?> canCurrentActorImportMedia(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_IMPORT_MEDIA, AccessPermissionsProtocol.CanCurrentActorImportMediaQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorImportMedia(query.ownerUserId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_SUBMIT_PRIVACY_CONSENT, produces = "application/json")
    public ResponseEntity<?> canCurrentActorSubmitPrivacyConsent(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_CURRENT_ACTOR_SUBMIT_PRIVACY_CONSENT, AccessPermissionsProtocol.CanCurrentActorSubmitPrivacyConsentQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canCurrentActorSubmitPrivacyConsent(query.userId())));
    }

















    @PostMapping(value = AccessPermissionsProtocol.CAN_ACCESS_CHAT, produces = "application/json")
    public ResponseEntity<?> canAccessChat(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_ACCESS_CHAT, AccessPermissionsProtocol.CanAccessChatQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canAccessChat(query.chatId(), query.actorUserId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.CAN_MANAGE_SUPPORT_CHAT, produces = "application/json")
    public ResponseEntity<?> canManageSupportChat(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.CAN_MANAGE_SUPPORT_CHAT, AccessPermissionsProtocol.CanManageSupportChatQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.canManageSupportChat(query.chatId(), query.actorUserId())));
    }
    @PostMapping(value = AccessPermissionsProtocol.HAS_EXPLICIT_GRANT, produces = "application/json")
    public ResponseEntity<?> hasExplicitGrant(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.HAS_EXPLICIT_GRANT, AccessPermissionsProtocol.HasExplicitGrantQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.hasExplicitGrant(query.subjectUserId(), query.permission(), query.resourceType(), query.resourceId(), query.expectedEffect())));
    }
    @PostMapping(value = AccessPermissionsProtocol.REQUIRES_AUTHENTICATED_ACTOR, produces = "application/json")
    public ResponseEntity<?> requiresAuthenticatedActor(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccessPermissionsProtocol.REQUIRES_AUTHENTICATED_ACTOR, AccessPermissionsProtocol.RequiresAuthenticatedActorQuery.class,
                AccessPermissionsProtocol::valid, query -> new AccessPermissionsProtocol.Decision(owner.requiresAuthenticatedActor()));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, String path, Class<T> type,
            Predicate<T> valid, Function<T, Object> action) throws java.io.IOException {
        try {
            var proofs = Collections.list(request.getHeaders(ActorDelegation.HEADER));
            if (proofs.size() > 1) throw new AccessDeniedException("Ambiguous actor proof");
            byte[] bytes = ReadQueryBodies.readBytes(request, AccessPermissionsProtocol.MAX_REQUEST_BYTES);
            var principal = proofs.isEmpty() ? null : verifier.verifyPrincipal(proofs.getFirst(),
                    AccessPermissionsProtocol.AUDIENCE, "POST", path, bytes);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            // A service capability is never the application's current actor.
            try (var ignored = principal == null ? DelegatedActorContext.withoutActor() : DelegatedActorContext.open(principal)) {
                return response(200, action.apply(query));
            }
        } catch (AccessDeniedException denied) { return response(403, new AccessPermissionsProtocol.Error("INVALID_DELEGATION")); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AccessPermissionsProtocol.Error(invalid.code())); }
        catch (ResponseStatusException rejected) {
            int status = rejected.getStatusCode().value();
            return response(status >= 400 && status < 500 ? status : 503, new AccessPermissionsProtocol.Error("OWNER_REJECTED"));
        }
        catch (RuntimeException unavailable) { return response(503, new AccessPermissionsProtocol.Error("ACCESS_PERMISSIONS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
