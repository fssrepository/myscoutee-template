package com.raxim.myscoutee.domain.integrations.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.accounts.api.AccountIdentity;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;
import com.raxim.myscoutee.domain.integrations.controller.IntegrationRoleReadController;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.AuthenticatedIntegrationClient;

@Tag("standard")
class IntegrationCapabilitiesServiceTest {
    final IntegrationRoleAccessService roles = mock(IntegrationRoleAccessService.class);
    final WorkspaceProfile profile = mock(WorkspaceProfile.class);
    final IntegrationCapabilitiesService access = new IntegrationCapabilitiesService(Optional.empty(), roles);

    UsernamePasswordAuthenticationToken authentication(Mode mode) {
        when(profile.getUserId()).thenReturn("owner");
        var identity = mock(AccountIdentity.class);
        when(identity.getUser()).thenReturn(profile);
        var auth = new UsernamePasswordAuthenticationToken(identity, null, List.of());
        auth.setDetails(new AuthenticatedIntegrationClient("owner", "token", "client", "mcp", mode, "grant"));
        return auth;
    }

    @Test void memberReadUsesOnlyRetainedCommunicationOperations() {
        assertThat(access.describe(authentication(Mode.READ)).operations())
                .containsExactly("listChats", "chatDetails", "chatMessages", "listNotifications");
        assertThat(access.describe(authentication(Mode.WRITE)).operations()).isEmpty();
    }

    @Test void administratorReadExposesOnlyNativeAttentionOverview() {
        when(roles.privileged(profile)).thenReturn(true);
        when(roles.admin(profile)).thenReturn(true);
        assertThat(access.describe(authentication(Mode.READ)).operations()).containsExactly("adminOverview");
        assertThat(access.describe(authentication(Mode.FULL)).operations()).containsExactly("adminOverview");
        assertThat(access.describe(authentication(Mode.WRITE)).operations()).isEmpty();
        assertThatThrownBy(() -> access.requireRead(authentication(Mode.READ), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void operatorCannotAcquireUnselectedAccountingOrMemberReads() {
        when(roles.privileged(profile)).thenReturn(true);
        when(roles.operator(profile)).thenReturn(true);
        assertThat(access.describe(authentication(Mode.FULL)).operations()).isEmpty();
    }

    @Test void roleRemovalOrPackDowngradeBlocksBeforeOverviewRead() {
        var admin = mock(AdminClientService.class);
        var controller = new IntegrationRoleReadController(access, admin);
        var expected = new AdminClientService.Overview(null);
        when(roles.admin(profile)).thenReturn(true);
        when(admin.overview("owner")).thenReturn(expected);
        assertThat(controller.adminOverview(authentication(Mode.READ))).isSameAs(expected);
        clearInvocations(admin);
        assertThatThrownBy(() -> controller.adminOverview(authentication(Mode.WRITE)))
                .isInstanceOf(ResponseStatusException.class);
        when(roles.admin(profile)).thenReturn(false);
        assertThatThrownBy(() -> controller.adminOverview(authentication(Mode.READ)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(admin);
    }
}
