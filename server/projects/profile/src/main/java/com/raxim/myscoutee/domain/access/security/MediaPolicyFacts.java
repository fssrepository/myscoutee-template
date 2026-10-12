package com.raxim.myscoutee.domain.access.security;

import com.raxim.myscoutee.domain.accounts.api.FriendshipReads;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import com.raxim.myscoutee.domain.media.api.MediaAccessPaths;

/** Current resource visibility; only small indexed metadata reads, never event graph reconstruction. */
@Component
public class MediaPolicyFacts {
    private final com.raxim.myscoutee.domain.accounts.api.AccountMediaVisibility accounts;
    private final com.raxim.myscoutee.domain.chat.api.ChatMediaVisibility chat;
    private final FriendshipReads friendships;
    private final ChatPolicyFacts chats;

    public MediaPolicyFacts(com.raxim.myscoutee.domain.accounts.api.AccountMediaVisibility accounts,
            com.raxim.myscoutee.domain.chat.api.ChatMediaVisibility chat,
            FriendshipReads friendships, ChatPolicyFacts chats) {
        this.accounts = accounts;
        this.chat = chat;
        this.friendships = friendships;
        this.chats = chats;
    }

    public boolean canRead(String viewer, String key) {
        if (viewer == null || viewer.isBlank() || !MediaAccessPaths.validKey(key)) return false;
        if (MediaAccessPaths.isPublicKey(key)) return true;
        String[] parts = key.split("/");
        int offset = "private".equals(parts[0]) ? 1 : 0;
        if (parts.length < offset + 4 || !List.of("images", "audio", "documents").contains(parts[offset])) return false;
        String owner = parts[offset + 1];
        String entity = parts[offset + 2];
        if (MediaAccessPaths.storageSegment(viewer).equals(owner)) return true; // Upload preview, including unsaved own drafts.
        List<String> urls = variantUrls(key, "images".equals(parts[offset]));
        if ("images".equals(parts[offset]) && (owner.equals(entity) || "profile-onboarding".equals(entity))) {
            var profile = accounts.profile(owner);
            return profile != null && profile.profileStatus() != null
                    && !profile.profileStatus().isBlank() && visible(profile.profileStatus(), owner, viewer);
        }
        // A forwarded attachment is readable when any current chat grants access.
        for (var message : chat.messages(urls)) {
            if ((message.deletedAtIso() == null || message.deletedAtIso().isBlank())
                    && chats.canAccessChat(message.chatId(), viewer)) return true;
        }
        return false;
    }

    private boolean visible(String visibility, String owner, String viewer) {
        if (viewer.equals(owner)) return true;
        String value = visibility == null ? "" : visibility.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "public" -> true;
            case "friends only" -> friendships.pairExists(owner, viewer);
            default -> false;
        };
    }

    private List<String> variantUrls(String key, boolean image) {
        List<String> keys = new ArrayList<>(List.of(key));
        if (image) {
            String group = key.substring(0, key.lastIndexOf('/') + 1);
            for (String name : List.of("original", "small.webp", "medium.webp", "large.webp")) keys.add(group + name);
        }
        List<String> urls = new ArrayList<>();
        for (String value : keys) {
            String encoded = UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
            for (String prefix : List.of("/api/media/private?key=", "/media/private?key=", "/media/public?key=", "/api/media/public?key=")) {
                urls.add(prefix + encoded);
            }
        }
        return urls;
    }
}
