package com.raxim.myscoutee.domain.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.raxim.myscoutee.domain.access.document.AccessGrant;
import com.raxim.myscoutee.domain.access.document.AccessPolicyDefinition;
import com.raxim.myscoutee.domain.access.repository.AccessGrantsRepository;
import com.raxim.myscoutee.domain.access.repository.AccessPolicyDefinitionsRepository;
import com.raxim.myscoutee.domain.access.security.condition.*;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;
import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;

@Tag("standard")
class AccessControlServiceTest {
    private AccessGrantsRepository grants;
    private AccessPolicyDefinitionsRepository definitions;
    private ProfileDirectory profiles;
    private ChatDirectory chats;
    private AccessControlService access;

    @BeforeEach void setup() throws Exception {
        grants = mock(AccessGrantsRepository.class);
        definitions = mock(AccessPolicyDefinitionsRepository.class);
        profiles = mock(ProfileDirectory.class);
        chats = mock(ChatDirectory.class);
        when(grants.findAll()).thenReturn(List.of());
        var policies = new ArrayList<AccessPolicyDefinition>();
        var rows = new ObjectMapper().readTree(Path.of("../../docker/conf/mongodb/myscoutee_db/accessPolicyDefinitions.json").toFile());
        for (var row : rows) {
            var definition = new AccessPolicyDefinition();
            definition.setPolicyKey(row.path("policyKey").asText());
            definition.setPermission(row.path("permission").asText());
            definition.setResourceType(row.path("resourceType").asText());
            definition.setStatus(row.path("status").asText());
            var required = new ArrayList<String>();
            row.path("required").forEach(value -> required.add(value.asText()));
            definition.setRequired(required);
            definition.setAnyOf(List.of());
            policies.add(definition);
        }
        assertThat(policies).hasSize(12);
        when(definitions.findAll()).thenReturn(policies);
        var conditions = new AccessPolicyConditionRegistry(List.of(new ActorMatchesSubjectCondition(),
                new NoExplicitDenyCondition(), new ActorCanAccessChatCondition(), new ActorCanManageSupportChatCondition()));
        var facts = new ChatPolicyFacts(chats, profiles, new AdministratorClassification() {});
        access = new AccessControlService(grants, definitions, new ConfigProperties(), conditions, facts);
        access.loadAccessControlCacheAtStartup();
        clearInvocations(grants, definitions, profiles, chats);
    }

    @Test void ownProfileAndConsentUseCachedPoliciesWithoutAdditionalQueries() {
        for (int i = 0; i < 100; i++) {
            assertThat(access.can("user.profile.edit", "alice", "alice", "alice")).isTrue();
            assertThat(access.can("privacy.consent.submit", "alice", "alice", "alice")).isTrue();
            assertThat(access.can("user.profile.delete", "alice", "bob", "bob")).isFalse();
        }
        verifyNoInteractions(grants, definitions, profiles, chats);
    }

    @Test void explicitDenialStillOverridesOwnershipAndUnknownPoliciesFailClosed() {
        var deny = new AccessGrant();
        deny.setSubjectUserId("alice");
        deny.setResourceType("user");
        deny.setResourceId("alice");
        deny.setPermission("user.profile.edit");
        deny.setEffect("deny");
        when(grants.findAll()).thenReturn(List.of(deny));
        access.refreshAccessGrantCache();
        assertThat(access.can("user.profile.edit", "alice", "alice", "alice")).isFalse();
        assertThat(access.can("uninstalled.feature", "alice", "alice", "alice")).isFalse();
    }

    @Test void personalChatWorksWithoutCommunityAndStillRejectsNonMembers() {
        var chat = new Chat();
        chat.setOwnerUserId("alice");
        chat.setChannelType("contact");
        chat.setMemberIds(List.of("alice", "bob"));
        doReturn(Optional.of(chat)).when(chats).findByOwnerUserIdAndChatId("alice", "chat-1");
        doReturn(List.of(chat)).when(chats).findByChatId("chat-1");
        assertThat(access.can("chat.message.create", "alice", "alice", "chat-1")).isTrue();
        assertThat(access.can("chat.message.create", "bob", "bob", "chat-1")).isTrue();
        assertThat(access.can("chat.message.create", "mallory", "mallory", "chat-1")).isFalse();
        assertThat(access.can("chat.message.create", "alice", "bob", "chat-1")).isFalse();
    }
}
