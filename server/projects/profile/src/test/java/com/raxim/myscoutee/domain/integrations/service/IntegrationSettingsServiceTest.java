package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.document.IntegrationToken;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.*;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@org.junit.jupiter.api.Tag("standard")
class IntegrationSettingsServiceTest {
    final IntegrationTokenService tokens = mock(IntegrationTokenService.class);
    final McpAuthorizationService mcp = mock(McpAuthorizationService.class);
    final UsersRepository users = mock(UsersRepository.class);
    final IntegrationPublicEndpointResolver endpoint = mock(IntegrationPublicEndpointResolver.class);
    final IntegrationSettingsService service = new IntegrationSettingsService(tokens, mcp,
            new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(users), endpoint);

    Users owner(long revision) {
        var owner = new Users(); owner.setUserId("owner");
        owner.setIntegrationAccess(new IntegrationAccess(revision, IntegrationAccess.Mode.WRITE, Map.of()));
        when(users.findByUserId("owner")).thenReturn(Optional.of(owner));
        return owner;
    }
    IntegrationToken token(String id, String scope) {
        var value = new IntegrationToken(); value.setId(id); value.setOwnerUserId("owner"); value.setScope(scope); return value;
    }
    IntegrationSettingsUpdateDto request(long revision, String id) {
        return new IntegrationSettingsUpdateDto(revision, IntegrationAccess.Mode.FULL,
                List.of(new IntegrationClientAccessDto(id, IntegrationAccess.Mode.BLOCKED)));
    }

    @org.junit.jupiter.api.BeforeEach void wireAccountAccessOwner() { org.springframework.test.util.ReflectionTestUtils.setField(service, "accountIntegrationAccess", new com.raxim.myscoutee.domain.accounts.service.AccountIntegrationAccessService(users)); }
    @Test void validatesWholeRequestBeforeWritingAndRejectsForeignOrAdminKeys() {
        owner(3);
        when(tokens.activeTokens("owner")).thenReturn(List.of(token("api", "integration"), token("operator", "admin-client")));
        for (String invalid : List.of("foreign", "revoked", "operator"))
            assertThatThrownBy(() -> service.save("owner", request(3, invalid))).isInstanceOf(ResponseStatusException.class);
        verify(users, never()).updateIntegrationAccess(anyString(), anyLong(), any());
    }

    @Test void rejectsStaleDraftBeforeAnyMutation() {
        owner(4);
        assertThatThrownBy(() -> service.save("owner", request(3, "api"))).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(tokens, mcp);
        verify(users, never()).updateIntegrationAccess(anyString(), anyLong(), any());
    }

    @Test void savesOneRevisionedValueAndKeepsOtherClientsMode() {
        var owner = owner(3);
        owner.setIntegrationAccess(new IntegrationAccess(3, IntegrationAccess.Mode.WRITE, Map.of("mcp", IntegrationAccess.Mode.FULL)));
        when(tokens.activeTokens("owner")).thenReturn(List.of(token("api", "integration"), token("mcp", "mcp")));
        when(endpoint.resolve()).thenReturn("https://example.test/api/integrations/v1");
        when(tokens.settings(eq("owner"), anyString(), eq("integration"), anyList()))
                .thenReturn(new IntegrationSettingsDto("https://example.test/api/integrations/v1", 3, 1000, List.of()));
        when(users.updateIntegrationAccess(eq("owner"), eq(3L), any())).thenAnswer(call -> {
            owner.setIntegrationAccess(call.getArgument(2)); return 1L;
        });
        service.save("owner", request(3, "api"));
        var saved = ArgumentCaptor.forClass(IntegrationAccess.class);
        verify(users).updateIntegrationAccess(eq("owner"), eq(3L), saved.capture());
        assertThat(saved.getValue().revision()).isEqualTo(4);
        assertThat(saved.getValue().mcp()).isEqualTo(IntegrationAccess.Mode.FULL);
        assertThat(saved.getValue().clients()).containsEntry("api", IntegrationAccess.Mode.BLOCKED).containsEntry("mcp", IntegrationAccess.Mode.FULL);
    }

    @Test void mainMcpPackCapsEachClientButDoesNotCapApiClients() {
        for (var main : IntegrationAccess.Mode.values()) for (var client : IntegrationAccess.Mode.values()) {
            var access = new IntegrationAccess(0, main, Map.of("key", client));
            assertThat(access.effective("key", true).ordinal()).isEqualTo(Math.min(main.ordinal(), client.ordinal()));
            assertThat(access.effective("key", false)).isEqualTo(client);
        }
        assertThat(IntegrationAccess.initial().effective("new", true)).isEqualTo(IntegrationAccess.Mode.BLOCKED);
    }

    @Test void personalSettingsStartWithoutBusinessSummariesAndKeepRevisionedClientAccess() {
        var owner = owner(3);
        var active = List.of(token("api", "integration"));
        when(tokens.activeTokens("owner")).thenReturn(active);
        when(endpoint.resolve()).thenReturn("https://example.test/api/integrations/v1");
        when(tokens.settings("owner", "https://example.test/api/integrations/v1", "integration", active))
                .thenReturn(new IntegrationSettingsDto("https://example.test/api/integrations/v1", 3, 1000, List.of()));
        var mcpSettings = new com.raxim.myscoutee.domain.integrations.dto.McpDtos.Settings(
                "https://example.test/api/mcp", 3, true, List.of(), IntegrationAccess.Mode.WRITE);
        when(mcp.settings(eq(active), any())).thenReturn(mcpSettings);
        when(users.updateIntegrationAccess(eq("owner"), eq(3L), any())).thenAnswer(call -> {
            owner.setIntegrationAccess(call.getArgument(2)); return 1L;
        });
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            var beans = context.getBeanFactory();
            beans.registerSingleton("tokens", tokens);
            beans.registerSingleton("mcp", mcp);
            beans.registerSingleton("profiles", new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(users));
            beans.registerSingleton("endpoint", endpoint);
            beans.registerSingleton("access", new com.raxim.myscoutee.domain.accounts.service.AccountIntegrationAccessService(users));
            context.register(IntegrationSettingsService.class);
            context.refresh();
            var personal = context.getBean(IntegrationSettingsService.class);
            var loaded = personal.load("owner");
            var json = new tools.jackson.databind.ObjectMapper().valueToTree(loaded);
            assertThat(json.has("affiliate")).isFalse(); assertThat(json.has("participants")).isFalse();
            assertThat(loaded.mcp()).isSameAs(mcpSettings);
            assertThat(loaded.accessRevision()).isEqualTo(3);
            var saved = personal.save("owner", request(3, "api"));
            assertThat(saved.accessRevision()).isEqualTo(4);
            verify(users).updateIntegrationAccess(eq("owner"), eq(3L), any());
        }
    }

    @Test void administratorSettingsStillSkipBusinessSummaries() {
        owner(3);
        when(tokens.activeTokens("owner")).thenReturn(List.of());
        when(tokens.settings("owner", "/api/admin-client/v1", "admin-client", List.of()))
                .thenReturn(new IntegrationSettingsDto("/api/admin-client/v1", 3, 1000, List.of()));
        assertThat(service.load("owner", true).baseUrl()).isEqualTo("/api/admin-client/v1");
        verifyNoInteractions(endpoint);
    }
    @Test void readPackCapsFullClientAndKeepsApiDefaultWrite() {
        var access = new IntegrationAccess(1, IntegrationAccess.Mode.READ, Map.of("mcp", IntegrationAccess.Mode.FULL));
        assertThat(access.effective("mcp", true)).isEqualTo(IntegrationAccess.Mode.READ);
        assertThat(IntegrationAccess.initial().effective("api", false)).isEqualTo(IntegrationAccess.Mode.WRITE);
    }

}
