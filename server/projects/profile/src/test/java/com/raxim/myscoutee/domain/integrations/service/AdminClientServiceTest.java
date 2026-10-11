package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;
import com.raxim.myscoutee.domain.integrations.repository.AdminClientRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("standard")
class AdminClientServiceTest {
    @Test void rechecksStoredRoleAndAccountStateOnEveryRequest() {
        var users = mock(ProfileDirectory.class);
        var profile = new Users(); profile.setUserId("admin"); profile.setHostTier("Admin"); profile.setProfileStatus("public");
        doReturn(Optional.of(profile)).when(users).findByUserId("admin");
        var access = new AdminClientAccessService(users, new AdministratorClassification() {});
        assertSame(profile, access.requireAdmin("admin"));
        for (String status : new String[]{"blocked", "deleted", "inactive"}) {
            profile.setProfileStatus(status);
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> access.requireAdmin("admin"));
        }
        profile.setProfileStatus("public"); profile.setHostTier("Member");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> access.requireAdmin("admin"));
    }
    @Test void omittedModerationOmitsOnlyItsJsonFieldsAndKeepsTheAdminRead() {
        var repository = mock(AdminClientRepository.class);
        var access = mock(AdminClientAccessService.class);
        when(access.requireAdmin("admin")).thenReturn(new Users());
        when(repository.unreadVersion("admin")).thenReturn("revision");
        var result = new AdminClientService(access, repository, java.util.Optional.empty()).overview("admin");
        var mapper = new tools.jackson.databind.ObjectMapper();
        assertEquals(mapper.readTree("""
                {"unreadMessages":0,"unreadVersion":"revision","failedJobs":0}
                """), mapper.readTree(mapper.writeValueAsString(result)).get("attention"));
        verify(access).requireAdmin("admin");
        verify(repository).unreadVersion("admin");
        verifyNoMoreInteractions(repository);
    }

    @Test void deniedOwnersDoNotReadCounts() {
        var access = mock(AdminClientAccessService.class); var repository = mock(AdminClientRepository.class);
        when(access.requireAdmin("member")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new AdminClientService(access, repository, Optional.empty()).overview("member"));
        verifyNoInteractions(repository);
    }
}
