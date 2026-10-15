package com.raxim.myscoutee.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@org.junit.jupiter.api.Tag("standard")
class MediaDocumentStorageTest {
    private final MediaObjectStoreClient objects = mock(MediaObjectStoreClient.class);
    private final MediaStorageProperties properties = new MediaStorageProperties();
    private final MediaStorageService storage = new MediaStorageService(properties, objects);

    @AfterEach void clearMode() { MongoSessionModeContext.clear(); }

    @Test void demoDocumentsRemainPrivateAndTheirStoredBytesCanBeRead() {
        properties.setEnabled(true);
        when(objects.isEnabled()).thenReturn(true);
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        byte[] bytes = "Saved document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new MockMultipartFile("document", "agenda.txt", "text/plain", bytes);
        String url = storage.storeDocument("group:work:author", "campaign-draft", file);
        assertThat(url).startsWith("/api/media/private?key=private/documents/group-work-author/campaign-draft/");
        String key = url.substring(url.indexOf("key=") + 4);
        verify(objects).putObject(properties.getBucket(), key, "text/plain", bytes);
        when(objects.getObject(properties.getBucket(), key)).thenReturn(new MediaObjectStoreClient.StoredObject(bytes, "text/plain"));
        assertThat(storage.loadPublicObject(key).bytes()).isEqualTo(bytes);
    }

    @Test void oversizedDocumentsAreRejectedBeforeReadingOrStoringBytes() throws Exception {
        var file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertThat(storage.storeDocument("author", "draft", file)).isNull();
        verify(file, never()).getBytes();
        verifyNoInteractions(objects);
    }

    @Test void readingAlreadyStoredDocumentsDoesNotApplyAnUploadSizeLimit() {
        properties.setEnabled(true);
        when(objects.isEnabled()).thenReturn(true);
        String key = "private/documents/author/draft/existing.txt";
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        when(objects.getObject(properties.getBucket(), key)).thenReturn(new MediaObjectStoreClient.StoredObject(bytes, "text/plain"));
        assertThat(storage.loadPublicObject(key).bytes()).isSameAs(bytes);
    }
}
