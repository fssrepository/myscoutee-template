package com.raxim.myscoutee.microservices.transport.access;

import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;

/** Explicit native policy queries; no policy implementation or document crosses the boundary. */
public final class AccessPermissionsProtocol {
    public static final String ROOT = "/_internal/v1/access/permissions";
    public static final String AUDIENCE = "access:permissions";
    public static final int MAX_REQUEST_BYTES = 65536, MAX_RESPONSE_BYTES = 16384;
    private AccessPermissionsProtocol() {}
    public record Decision(Boolean allowed) {}
    public record ActorResult(Boolean resolved, String userId) {}
    public record Error(String code) {}
    private static boolean id(String value) { return value == null || value.length() <= 512; }
    public static final String CAN = ROOT + "/can";
    public record CanQuery(String policyKey, String actorUserId, String subjectUserId, String resourceId) {}
    public static boolean valid(CanQuery query) { return query != null && id(query.policyKey()) && id(query.actorUserId()) && id(query.subjectUserId()) && id(query.resourceId()); }
    public static final String RESOLVE_CHAT_ACTOR_USER_ID = ROOT + "/resolve-chat-actor-user-id";
    public record ResolveChatActorUserIdQuery(String chatId, String profileId) {}
    public static boolean valid(ResolveChatActorUserIdQuery query) { return query != null && id(query.chatId()) && id(query.profileId()); }
    public static final String CAN_CURRENT_ACTOR_CREATE_CHAT_MESSAGE = ROOT + "/can-current-actor-create-chat-message";
    public record CanCurrentActorCreateChatMessageQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorCreateChatMessageQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE = ROOT + "/can-current-actor-edit-chat-message";
    public record CanCurrentActorEditChatMessageQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorEditChatMessageQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_DELETE_CHAT_MESSAGE = ROOT + "/can-current-actor-delete-chat-message";
    public record CanCurrentActorDeleteChatMessageQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorDeleteChatMessageQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_PIN_CHAT_MESSAGE = ROOT + "/can-current-actor-pin-chat-message";
    public record CanCurrentActorPinChatMessageQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorPinChatMessageQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_MANAGE_CHAT_REACTION = ROOT + "/can-current-actor-manage-chat-reaction";
    public record CanCurrentActorManageChatReactionQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorManageChatReactionQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_ADD_CHAT_ATTACHMENT = ROOT + "/can-current-actor-add-chat-attachment";
    public record CanCurrentActorAddChatAttachmentQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorAddChatAttachmentQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_MANAGE_SUPPORT_CHAT = ROOT + "/can-current-actor-manage-support-chat";
    public record CanCurrentActorManageSupportChatQuery(String userId, String chatId) {}
    public static boolean valid(CanCurrentActorManageSupportChatQuery query) { return query != null && id(query.userId()) && id(query.chatId()); }
    public static final String CAN_CURRENT_ACTOR_EDIT_PROFILE = ROOT + "/can-current-actor-edit-profile";
    public record CanCurrentActorEditProfileQuery(String userId) {}
    public static boolean valid(CanCurrentActorEditProfileQuery query) { return query != null && id(query.userId()); }
    public static final String CAN_CURRENT_ACTOR_DELETE_PROFILE = ROOT + "/can-current-actor-delete-profile";
    public record CanCurrentActorDeleteProfileQuery(String userId) {}
    public static boolean valid(CanCurrentActorDeleteProfileQuery query) { return query != null && id(query.userId()); }
    public static final String CAN_CURRENT_ACTOR_UPLOAD_MEDIA = ROOT + "/can-current-actor-upload-media";
    public record CanCurrentActorUploadMediaQuery(String ownerUserId) {}
    public static boolean valid(CanCurrentActorUploadMediaQuery query) { return query != null && id(query.ownerUserId()); }
    public static final String CAN_CURRENT_ACTOR_IMPORT_MEDIA = ROOT + "/can-current-actor-import-media";
    public record CanCurrentActorImportMediaQuery(String ownerUserId) {}
    public static boolean valid(CanCurrentActorImportMediaQuery query) { return query != null && id(query.ownerUserId()); }
    public static final String CAN_CURRENT_ACTOR_SUBMIT_PRIVACY_CONSENT = ROOT + "/can-current-actor-submit-privacy-consent";
    public record CanCurrentActorSubmitPrivacyConsentQuery(String userId) {}
    public static boolean valid(CanCurrentActorSubmitPrivacyConsentQuery query) { return query != null && id(query.userId()); }
    public static final String CAN_ACCESS_CHAT = ROOT + "/can-access-chat";
    public record CanAccessChatQuery(String chatId, String actorUserId) {}
    public static boolean valid(CanAccessChatQuery query) { return query != null && id(query.chatId()) && id(query.actorUserId()); }
    public static final String CAN_MANAGE_SUPPORT_CHAT = ROOT + "/can-manage-support-chat";
    public record CanManageSupportChatQuery(String chatId, String actorUserId) {}
    public static boolean valid(CanManageSupportChatQuery query) { return query != null && id(query.chatId()) && id(query.actorUserId()); }
    public static final String HAS_EXPLICIT_GRANT = ROOT + "/has-explicit-grant";
    public record HasExplicitGrantQuery(String subjectUserId, String permission, String resourceType, String resourceId, AccessGrantEffect expectedEffect) {}
    public static boolean valid(HasExplicitGrantQuery query) { return query != null && id(query.subjectUserId()) && id(query.permission()) && id(query.resourceType()) && id(query.resourceId()); }
    public static final String REQUIRES_AUTHENTICATED_ACTOR = ROOT + "/requires-authenticated-actor";
    public record RequiresAuthenticatedActorQuery() {}
    public static boolean valid(RequiresAuthenticatedActorQuery query) { return query != null; }
    public static String[] paths() { return new String[] {CAN, RESOLVE_CHAT_ACTOR_USER_ID, CAN_CURRENT_ACTOR_CREATE_CHAT_MESSAGE, CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE, CAN_CURRENT_ACTOR_DELETE_CHAT_MESSAGE, CAN_CURRENT_ACTOR_PIN_CHAT_MESSAGE, CAN_CURRENT_ACTOR_MANAGE_CHAT_REACTION, CAN_CURRENT_ACTOR_ADD_CHAT_ATTACHMENT, CAN_CURRENT_ACTOR_MANAGE_SUPPORT_CHAT, CAN_CURRENT_ACTOR_EDIT_PROFILE, CAN_CURRENT_ACTOR_DELETE_PROFILE, CAN_CURRENT_ACTOR_UPLOAD_MEDIA, CAN_CURRENT_ACTOR_IMPORT_MEDIA, CAN_CURRENT_ACTOR_SUBMIT_PRIVACY_CONSENT, CAN_ACCESS_CHAT, CAN_MANAGE_SUPPORT_CHAT, HAS_EXPLICIT_GRANT, REQUIRES_AUTHENTICATED_ACTOR}; }
}
