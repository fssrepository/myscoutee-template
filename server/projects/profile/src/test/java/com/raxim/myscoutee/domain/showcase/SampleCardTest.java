package com.raxim.myscoutee.domain.showcase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.showcase.controller.SampleCardController;
import com.raxim.myscoutee.domain.showcase.repository.SampleCardRepository;
import com.raxim.myscoutee.domain.showcase.service.SampleCardService;

@Tag("standard")
class SampleCardTest {
    @Test void invalidPagesNeverReachMongo() {
        var repository = mock(SampleCardRepository.class);
        var service = new SampleCardService(repository);
        for (int[] page : new int[][] {{-1,12},{1001,12},{0,0},{0,51}}) {
            assertThatThrownBy(() -> service.query(page[0],page[1]))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
        verifyNoInteractions(repository);
    }
    @Test void anonymousRequestNeverReadsCards() {
        var profiles = mock(SelectedProfile.class);
        var service = mock(SampleCardService.class);
        var controller = new SampleCardController(profiles,service);
        assertThatThrownBy(() -> controller.query(0,12))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        verifyNoInteractions(service);
    }
    @Test void selectedMemberCanReadTheRequestedPage() {
        var profiles = mock(SelectedProfile.class);
        var service = mock(SampleCardService.class);
        when(profiles.resolveSelectedUserId(null)).thenReturn("member");
        new SampleCardController(profiles,service).query(1,2);
        verify(service).query(1,2);
    }
}
