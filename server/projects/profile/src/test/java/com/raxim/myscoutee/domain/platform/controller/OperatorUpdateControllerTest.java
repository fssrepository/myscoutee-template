package com.raxim.myscoutee.domain.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateApprovalDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateEventPageDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateJobDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateOverviewDto;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateService;

@org.junit.jupiter.api.Tag("standard")
class OperatorUpdateControllerTest {
    @Test
    void approvalUsesOnlyAnnouncementReferenceAndAuthenticatedOperator() {
        OperatorUpdateService service = mock(OperatorUpdateService.class);
        OperatorUpdateController controller = new OperatorUpdateController(service, mock(com.raxim.myscoutee.domain.platform.service.OperatorUpdateReleaseService.class));
        OperatorUpdateApprovalDto approval =
                new OperatorUpdateApprovalDto(
                        "ann_0123456789abcdef0123456789abcdef");
        OperatorUpdateJobDto expected = status("upd_0123456789abcdef0123456789abcdef");
        var authentication = new UsernamePasswordAuthenticationToken(
                "operator-user-1",
                null,
                List.of(new SimpleGrantedAuthority("O")));
        when(service.approve(approval, "operator-user-1")).thenReturn(expected);

        assertThat(controller.approve(approval, authentication)).isSameAs(expected);
        verify(service).approve(approval, "operator-user-1");
    }

    @Test
    void statusAndReconnectableEventCursorAreForwarded() {
        OperatorUpdateService service = mock(OperatorUpdateService.class);
        OperatorUpdateController controller = new OperatorUpdateController(service, mock(com.raxim.myscoutee.domain.platform.service.OperatorUpdateReleaseService.class));
        String jobId = "upd_0123456789abcdef0123456789abcdef";
        OperatorUpdateJobDto expected = status(jobId);
        OperatorUpdateEventPageDto expectedEvents =
                new OperatorUpdateEventPageDto(List.of(), 12L, false);
        when(service.status(jobId)).thenReturn(expected);
        when(service.events(jobId, 10L, 25)).thenReturn(expectedEvents);

        assertThat(controller.status(jobId)).isSameAs(expected);
        assertThat(controller.events(jobId, 10L, 25)).isSameAs(expectedEvents);
        verify(service).status(jobId);
        verify(service).events(jobId, 10L, 25);
    }

    @Test
    void overviewSupportsReloadWithoutAPreviouslyKnownJobId() {
        OperatorUpdateService service = mock(OperatorUpdateService.class);
        OperatorUpdateController controller = new OperatorUpdateController(service, mock(com.raxim.myscoutee.domain.platform.service.OperatorUpdateReleaseService.class));
        OperatorUpdateJobDto job =
                status("upd_0123456789abcdef0123456789abcdef");
        OperatorUpdateOverviewDto expected =
                new OperatorUpdateOverviewDto(true, "1.0.0", job);
        when(service.overview()).thenReturn(expected);

        assertThat(controller.overview()).isSameAs(expected);
        verify(service).overview();
    }

    @Test
    void controllerRequiresOperatorAuthorityWithoutAdminFallback() {
        PreAuthorize annotation =
                OperatorUpdateController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('O')");
        assertThat(annotation.value()).doesNotContain("'A'");
    }

    private OperatorUpdateJobDto status(String jobId) {
        return new OperatorUpdateJobDto(
                1,
                jobId,
                "CHECKING",
                5,
                "Checking.",
                "2026-07-28T19:30:00Z",
                "2026-07-28T19:30:00Z",
                "operator-user-1",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "1.0.0",
                "1.2.3",
                "stable",
                "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                null);
    }
}
