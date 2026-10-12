package com.raxim.myscoutee.domain.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.raxim.myscoutee.domain.media.dto.MediaDtos.MediaImageImportRequestDto;
import com.raxim.myscoutee.domain.media.service.MediaStorageService;
import com.raxim.myscoutee.domain.media.api.MediaStorage.StoredImageSet;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("standard")
class MediaControllerTest {
    @Mock
    private MediaStorageService mediaStorageService;
    @Mock
    private MultipartFile multipartFile;

    @Test
    void uploadImageReportsUnavailableWhenStorageFails() {
        MediaController controller = new MediaController(mediaStorageService);

        assertThatThrownBy(() -> controller.uploadImage("owner-1", "slot-0", multipartFile))
                .hasMessageContaining("Image upload unavailable");
    }

    @Test
    void uploadAudioReportsUnavailableWhenStorageFails() {
        MediaController controller = new MediaController(mediaStorageService);

        assertThatThrownBy(() -> controller.uploadAudio("owner-1", "message-1", multipartFile))
                .hasMessageContaining("Audio upload unavailable");
    }

    @Test
    void uploadImageReturnsImageSetVariants() {
        MediaController controller = new MediaController(mediaStorageService);
        when(mediaStorageService.storeImageSet("owner-1", "slot-0", multipartFile))
                .thenReturn(new StoredImageSet(
                        "/media/public?key=images/owner-1/slot-0/1/large.webp",
                        "/media/public?key=images/owner-1/slot-0/1/original",
                        "/media/public?key=images/owner-1/slot-0/1/small.webp",
                        "/media/public?key=images/owner-1/slot-0/1/medium.webp",
                        "/media/public?key=images/owner-1/slot-0/1/large.webp"));

        var response = controller.uploadImage("owner-1", "slot-0", multipartFile);

        assertThat(response.uploaded()).isTrue();
        assertThat(response.imageUrl()).endsWith("/large.webp");
        assertThat(response.imageSet().smallUrl()).endsWith("/small.webp");
        assertThat(response.mediumUrl()).endsWith("/medium.webp");
    }

    @Test
    void importImageReturnsUnsuccessfulWhenStorageFails() {
        MediaController controller = new MediaController(mediaStorageService);

        var response = controller.importImage(new MediaImageImportRequestDto(
                "owner-1",
                "slot-0",
                "https://cdn.example.com/avatar.jpg"));

        org.assertj.core.api.Assertions.assertThat(response.uploaded()).isFalse();
    }
}
