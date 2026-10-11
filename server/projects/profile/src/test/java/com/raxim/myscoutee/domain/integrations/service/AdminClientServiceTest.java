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
    @Test void returnsRetainedOwnerCountsWithoutCreatingAnAnalyticsSnapshot() {
        var access = mock(AdminClientAccessService.class); var repository = mock(AdminClientRepository.class);
        when(access.requireAdmin("admin")).thenReturn(new Users());
        when(repository.unreadVersion("admin")).thenReturn("version-1");
        when(repository.unresolvedReports()).thenReturn(3L); when(repository.unresolvedFeedback()).thenReturn(2L);
        var attention = new AdminClientService(access, repository).overview("admin").attention();
        assertEquals("version-1", attention.unreadVersion()); assertEquals(3, attention.unresolvedReports());
        assertEquals(2, attention.unresolvedFeedback()); assertEquals(0, attention.supportCases());
        verify(repository).unreadVersion("admin"); verify(repository).unresolvedReports(); verify(repository).unresolvedFeedback();
        verifyNoMoreInteractions(repository);
    }
    @Test void deniedOwnersDoNotReadCounts() {
        var access = mock(AdminClientAccessService.class); var repository = mock(AdminClientRepository.class);
        when(access.requireAdmin("member")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new AdminClientService(access, repository).overview("member"));
        verifyNoInteractions(repository);
    }
}
