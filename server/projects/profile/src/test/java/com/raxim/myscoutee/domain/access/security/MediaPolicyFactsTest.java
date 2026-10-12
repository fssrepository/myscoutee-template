package com.raxim.myscoutee.domain.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import com.raxim.myscoutee.domain.accounts.api.AccountMediaVisibility;
import com.raxim.myscoutee.domain.accounts.api.FriendshipReads;
import com.raxim.myscoutee.domain.chat.api.ChatMediaVisibility;
import com.raxim.myscoutee.domain.chat.dto.ChatMessageMediaVisibilityDto;

@Tag("standard")
class MediaPolicyFactsTest {
    private final AccountMediaVisibility accounts = mock(AccountMediaVisibility.class);
    private final ChatMediaVisibility messages = mock(ChatMediaVisibility.class);
    private final FriendshipReads friends = mock(FriendshipReads.class);
    private final ChatPolicyFacts chats = mock(ChatPolicyFacts.class);
    private final MediaPolicyFacts policy = new MediaPolicyFacts(accounts, messages, friends, chats);

    @Test void ownDraftAndPublicSeedNeedNoDatabaseRead() {
        assertThat(policy.canRead("owner", "private/images/owner/draft/upload/large.webp")).isTrue();
        assertThat(policy.canRead("viewer", "public/demo/images/example.webp")).isTrue();
        verifyNoInteractions(accounts, messages, friends, chats);
    }

    @Test void profileVisibilityUsesOnlyTheOwnerProjectionAndExistingFriendshipRule() {
        String key = "private/images/owner/owner/upload/large.webp";
        when(accounts.profile("owner")).thenReturn(new AccountMediaVisibility.Profile("public", null, null));
        assertThat(policy.canRead("viewer", key)).isTrue();
        when(accounts.profile("owner")).thenReturn(new AccountMediaVisibility.Profile("private", null, null));
        assertThat(policy.canRead("viewer", key)).isFalse();
        when(accounts.profile("owner")).thenReturn(new AccountMediaVisibility.Profile("friends only", null, null));
        assertThat(policy.canRead("viewer", key)).isFalse();
        when(friends.pairExists("owner", "viewer")).thenReturn(true);
        assertThat(policy.canRead("viewer", key)).isTrue();
        verifyNoInteractions(messages, chats);
    }

    @Test void forwardedMediaRequiresOneCurrentAccessibleConversation() {
        String key = "private/images/author/chat/upload/large.webp";
        when(messages.messages(anyList())).thenReturn(List.of(
                new ChatMessageMediaVisibilityDto("deleted-message-chat", "2026-10-11T00:00:00Z"),
                new ChatMessageMediaVisibilityDto("chat-one", null),
                new ChatMessageMediaVisibilityDto("chat-two", null)));
        assertThat(policy.canRead("viewer", key)).isFalse();
        when(chats.canAccessChat("chat-two", "viewer")).thenReturn(true);
        assertThat(policy.canRead("viewer", key)).isTrue();
        verify(chats, never()).canAccessChat("deleted-message-chat", "viewer");
        verify(messages, times(2)).messages(argThat(urls -> urls.contains("/api/media/private?key=" + key)
                && urls.contains("/api/media/private?key=private/images/author/chat/upload/original")));
        verifyNoInteractions(accounts, friends);
    }

    @Test void missingReferencesAndMalformedKeysNeverBecomePublic() {
        assertThat(policy.canRead("viewer", "private/images/other/draft/upload/large.webp")).isFalse();
        clearInvocations(messages);
        assertThat(policy.canRead("viewer", "private/images/../secret")).isFalse();
        assertThat(policy.canRead(null, "public/demo/image.webp")).isFalse();
        verifyNoInteractions(accounts, messages, friends, chats);
    }
}
