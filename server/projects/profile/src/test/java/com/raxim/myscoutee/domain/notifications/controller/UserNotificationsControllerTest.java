package com.raxim.myscoutee.domain.notifications.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationPreferenceRequestDto;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.notifications.service.UserNotificationService;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("standard")
class UserNotificationsControllerTest {
    @Mock
    private UserNotificationService userNotificationService;
    @Mock
    private SelectedProfile homeUserProfileService;

    private UserNotificationsController controller;

    @BeforeEach
    void setUp() {
        controller = new UserNotificationsController(userNotificationService, homeUserProfileService);
    }

    @Test
    void delegatesUsingTheResolvedCurrentUserRatherThanTheRequestedId() {
        when(homeUserProfileService.resolveSelectedUserId("requested-user")).thenReturn("principal-user");

        controller.updatePreferences(
                new UserNotificationPreferenceRequestDto(true),
                "requested-user");

        verify(userNotificationService).updateMutedPreference("principal-user", true);
    }

    @Test
    void rejectsNotificationAccessWithoutAResolvedCurrentOrDemoUser() {
        when(homeUserProfileService.resolveSelectedUserId(null)).thenReturn(null);

        assertThatThrownBy(() -> controller.queryPage("new", 20, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }
}
