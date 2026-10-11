package com.raxim.myscoutee.domain.access.api;

import org.springframework.security.core.Authentication;

public interface AccessPermissions extends ConsentAuthorization {
    boolean can(String policyKey, String actorUserId, String subjectUserId, String resourceId);
    String resolveChatActorUserId(String chatId, String profileId);
    boolean canCurrentActorCreateChatMessage(String userId, String chatId);
    boolean canCurrentActorEditChatMessage(String userId, String chatId);
    boolean canCurrentActorDeleteChatMessage(String userId, String chatId);
    boolean canCurrentActorPinChatMessage(String userId, String chatId);
    boolean canCurrentActorManageChatReaction(String userId, String chatId);
    boolean canCurrentActorAddChatAttachment(String userId, String chatId);
    boolean canCurrentActorManageSupportChat(String userId, String chatId);
    boolean canCurrentActorEditProfile(String userId);
    boolean canCurrentActorDeleteProfile(String userId);
    boolean canCurrentActorUploadMedia(String ownerUserId);
    boolean canCurrentActorImportMedia(String ownerUserId);
    boolean canCurrentActorSubmitPrivacyConsent(String userId);
    boolean canAccessChat(String chatId, String actorUserId);
    boolean canManageSupportChat(String chatId, String actorUserId);
    default String resolveActorUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor actor)
            return normalized(actor.getUserId());
        if (principal instanceof String value) return normalized(value);
        return normalized(authentication.getName());
    }
    private static String normalized(String value) {
        String text = value == null ? "" : value.trim();
        return org.springframework.util.StringUtils.hasText(text) ? text : null;
    }
    boolean hasExplicitGrant(
            String subjectUserId,
            String permission,
            String resourceType,
            String resourceId,
            AccessGrantEffect expectedEffect);
    boolean requiresAuthenticatedActor();
}
