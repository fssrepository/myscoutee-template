package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@Tag("standard")
class ProfileStatusWorkTest {
    @Test void lifecycleStartsWithoutSupportAndDoesNotDiscardUndeliveredSupportIntent() {
        var users = mock(UsersRepository.class);
        var mongo = mock(MongoTemplate.class);
        var databases = mock(ScheduledMongoSessionRunner.class);
        var changes = mock(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class);
        var user = new Users(); user.setUserId("profile");
        when(users.save(user)).thenReturn(user);
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(UsersRepository.class, () -> users);
            context.registerBean(MongoTemplate.class, () -> mongo);
            context.registerBean(ScheduledMongoSessionRunner.class, () -> databases);
            context.registerBean(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class, () -> changes);
            context.register(ProfileStatusWork.class);
            context.refresh();
            clearInvocations(mongo);
            var work = context.getBean(ProfileStatusWork.class);
            work.save(user);
            var query = ArgumentCaptor.forClass(Query.class);
            var update = ArgumentCaptor.forClass(Update.class);
            verify(mongo).updateFirst(query.capture(), update.capture(), eq(Users.class));
            assertThat(query.getValue().getQueryObject()).containsEntry("profileStatusWork", user.getProfileStatusWork());
            assertThat(update.getValue().getUpdateObject().toJson()).contains("$unset", "profileStatusWork");
            clearInvocations(mongo);
            user.setProfileSupportWork(List.of(new Users.ProfileSupportWork("receipt", "admin", "Admin", "A", "Reason")));
            work.save(user);
            assertThat(user.getProfileStatusWork()).isNotBlank();
            assertThat(user.getProfileSupportWork()).hasSize(1);
            verifyNoInteractions(mongo);
        }
    }

}
