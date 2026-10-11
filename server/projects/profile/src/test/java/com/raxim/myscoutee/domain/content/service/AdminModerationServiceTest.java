package com.raxim.myscoutee.domain.content.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.raxim.myscoutee.domain.accounts.api.*;
import com.raxim.myscoutee.domain.administration.api.*;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.*;
import com.raxim.myscoutee.domain.chat.api.*;
import com.raxim.myscoutee.domain.content.document.*;
import com.raxim.myscoutee.domain.content.repository.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;

@Tag("standard")
class AdminModerationServiceTest {
    private final AdministrativeActors actors = mock(AdministrativeActors.class);
    private final UserReportSubmissionsRepository reports = mock(UserReportSubmissionsRepository.class);
    private final UserFeedbackSubmissionsRepository feedback = mock(UserFeedbackSubmissionsRepository.class);
    private final ProfileSummaries profiles = mock(ProfileSummaries.class);
    private final ChatDirectory chats = mock(ChatDirectory.class);
    private final ChatReview review = mock(ChatReview.class);
    private final SupportMessages support = mock(SupportMessages.class);
    private final SupportCaseCounters counters = mock(SupportCaseCounters.class);
    private final OperationalTelemetry telemetry = mock(OperationalTelemetry.class);
    private final NotificationDelivery notifications = mock(NotificationDelivery.class);
    private final ProfileModeration moderation = mock(ProfileModeration.class);
    private final AdminModerationService service = new AdminModerationService(actors, reports, feedback, profiles, chats, review, support, counters, telemetry, notifications);
    @BeforeEach void setup() {
        when(actors.resolveAdminActor("admin")).thenReturn(new AdminUserDto("admin", "Admin", "AD", "admin@example.test", "", "", List.of()));
        when(counters.hasSupportCaseCounters("admin")).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "profileModeration", moderation);
    }
    @Test void warningWritesSupportBeforeMarkingTheReport() {
        var report=report();when(reports.findByTargetUserIdOrderByCreatedDateDesc("target")).thenReturn(List.of(report));
        when(reports.findById("report")).thenReturn(Optional.of(report));
        doThrow(new IllegalStateException("support write failed")).when(support).sendAdminSupportMessage("target", "admin", "Admin", "AD", "Warning", "warned");
        assertThatThrownBy(()->service.warn("target",new AdminActionRequestDto("admin","Warning","report"))).hasMessage("support write failed");
        verify(reports,never()).save(any());verifyNoInteractions(telemetry);
    }
    @Test void feedbackResolutionNotifiesOnceAndDoesNotCreateSupportChat() {
        var item=new UserFeedbackSubmission();item.setId("feedback");item.setUserId("member");item.setSubject("Suggestion");
        when(feedback.findById("feedback")).thenReturn(Optional.of(item));when(feedback.findAllByOrderByCreatedDateDesc()).thenReturn(List.of(item));
        service.resolveFeedback("feedback",new AdminReviewResolveRequestDto("admin",true));
        service.resolveFeedback("feedback",new AdminReviewResolveRequestDto("admin",true));
        var intent=org.mockito.ArgumentCaptor.forClass(NotificationDelivery.NotificationIntent.class);verify(notifications).enqueue(intent.capture());
        assertThat(intent.getValue().recipientUserIds()).containsExactly("member");assertThat(intent.getValue().entityId()).isEqualTo("feedback");
        verifyNoInteractions(support);assertThat(item.getResolvedByAdminUserId()).isEqualTo("admin");
    }
    @Test void onlyReportedUsersCanBeBlocked() {
        assertThatThrownBy(()->service.block("target",new AdminActionRequestDto("admin","",null))).hasMessageContaining("403");
        verifyNoInteractions(moderation,support);
    }
    @Test void blockDelegatesStatusAndDurableSupportNoticeToTheAccountOwner() {
        var report=report();when(reports.findByTargetUserIdOrderByCreatedDateDesc("target")).thenReturn(List.of(report));
        var target=mock(ProfileSummary.class);when(target.getUserId()).thenReturn("target");doReturn(Optional.of(target)).when(profiles).findByUserId("target");
        when(moderation.block(eq("target"),any())).thenReturn(true);
        service.block("target",new AdminActionRequestDto("admin","Review required",null));
        verify(moderation).block("target",new ProfileModeration.SupportNotice("admin","Admin","AD","Review required"));
        verify(telemetry).record("admin.user.blocked","moderation","target","admin");verifyNoInteractions(support);
    }
    private UserReportSubmission report(){var report=new UserReportSubmission();report.setId("report");report.setUserId("member");report.setTargetUserId("target");return report;}
}
