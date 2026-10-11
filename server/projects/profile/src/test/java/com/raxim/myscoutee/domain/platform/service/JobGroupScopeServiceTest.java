package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@org.junit.jupiter.api.Tag("standard")
class JobGroupScopeServiceTest {
    @Test void personalJobsStartWithoutGroupEventAssetOrAffinityOwners() {
        var profiles = mock(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class);
        var user = new com.raxim.myscoutee.domain.accounts.document.Users(); user.setId("personal");
        doReturn(user).when(profiles).findFirstByUserId("personal");
        doReturn(List.of(user)).when(profiles).findByUserIdIn(List.of("personal"));
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class, () -> profiles);
            context.register(JobGroupScopeService.class);
            context.refresh();
            var service = context.getBean(JobGroupScopeService.class);
            JobGroupContext.run(null, () -> {
                assertThat(service.forUser("personal")).isNull();
                assertThat(service.forTarget("user", "personal")).isNull();
                assertThat(service.matchesWorkspace(null)).isTrue();
                assertThat(service.workspaceScope().ids()).isEmpty();
                assertThat(service.usersByGroup(List.of("personal")))
                        .containsEntry(null, List.of("personal"));
            });
        }
        assertThat(JobGroupContext.isActive()).isFalse();
        verify(profiles).findByUserIdIn(List.of("personal"));
    }

    @Test void configuredWorkspaceFailureCannotBecomeAnEmptyPersonalScope() {
        var workspace = mock(com.raxim.myscoutee.domain.platform.api.JobWorkspaceDirectory.class);
        when(workspace.workspaceBaseIds()).thenThrow(new IllegalStateException("owner unavailable"));
        var service = new JobGroupScopeService(mock(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class),
                java.util.Optional.of(workspace), List.of());
        assertThatThrownBy(() -> JobGroupContext.run(null, service::workspaceScope))
                .isInstanceOf(IllegalStateException.class).hasMessage("owner unavailable");
        assertThat(JobGroupContext.isActive()).isFalse();
    }

}
