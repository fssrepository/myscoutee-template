package com.raxim.myscoutee.domain.media.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.*;
import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;
import com.raxim.myscoutee.domain.media.document.MediaDeletion;

@Tag("standard")
class MediaDeletionServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    MediaObjectStoreClient objects = mock(MediaObjectStoreClient.class);
    BulkOperations bulk = mock(BulkOperations.class);
    @BeforeEach void setup() { when(mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, MediaDeletion.class)).thenReturn(bulk); }
    MediaStorageProperties properties = new MediaStorageProperties();
    MediaDeletionService service = new MediaDeletionService(mongo, objects, properties);
    Instant now = Instant.parse("2026-10-14T14:00:00Z");
    MediaDeletion item(String id, String key) { return new MediaDeletion(id, null, key, now, now, 1, null, null, "processing"); }
    void batch(List<MediaDeletion> batch) {
        when(objects.isEnabled()).thenReturn(true);
        when(mongo.find(any(Query.class), eq(MediaDeletion.class))).thenReturn(batch);
    }
    @Test void batchDeletesExactKeysAndAcknowledgesOnlySuccessfulEntries() {
        batch(List.of(item("one", "private/documents/owner/entity/a.pdf"), item("two", "private/audio/owner/entity/b.webm")));
        when(objects.deleteObjects(anyString(), anyList())).thenReturn(List.of(new MediaObjectStoreClient.DeleteFailure("private/audio/owner/entity/b.webm", "SlowDown")));
        assertThat(service.process(now)).isEqualTo(new MediaDeletionService.Result(1, 1));
        verify(objects).deleteObjects(properties.getBucket(), List.of("private/documents/owner/entity/a.pdf", "private/audio/owner/entity/b.webm"));
        var removed = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(removed.capture(), eq(MediaDeletion.class));
        assertThat(removed.getValue().getQueryObject().get("_id").toString()).contains("one").doesNotContain("two");
        assertThat(removed.getValue().getQueryObject()).containsKey("claim");
        var updates = ArgumentCaptor.forClass(Update.class);
        verify(bulk).updateMulti(any(Query.class), updates.capture());
        assertThat(updates.getValue().getUpdateObject().toString()).contains("nextAttemptAt", "SlowDown", "retry");
        verify(bulk).execute();
    }
    @Test void lostStorageReplyRetainsEveryEntryForIdempotentRetry() {
        batch(List.of(item("one", "private/documents/owner/entity/a.pdf")));
        when(objects.deleteObjects(anyString(), anyList())).thenThrow(new IllegalStateException("timeout"));
        assertThat(service.process(now)).isEqualTo(new MediaDeletionService.Result(0, 1));
        verify(mongo, never()).remove(any(Query.class), eq(MediaDeletion.class));
    }
    @Test void thirdFailureIsTerminalAndIsNotScheduledAgain() {
        batch(List.of(new MediaDeletion("one", null, "private/documents/owner/entity/a.pdf", now, now, 3, null, null, "processing")));
        when(objects.deleteObjects(anyString(), anyList())).thenReturn(List.of(new MediaObjectStoreClient.DeleteFailure("private/documents/owner/entity/a.pdf", "SlowDown")));
        assertThat(service.process(now)).isEqualTo(new MediaDeletionService.Result(0, 1));
        var update = ArgumentCaptor.forClass(Update.class);
        verify(bulk).updateMulti(any(Query.class), update.capture());
        assertThat(update.getValue().getUpdateObject().get("$set").toString()).contains("failed", "SlowDown");
        assertThat(update.getValue().getUpdateObject().get("$unset").toString()).contains("nextAttemptAt");
    }
    @Test void accessDeniedIsTerminalOnFirstAttempt() {
        batch(List.of(item("one", "private/documents/owner/entity/a.pdf")));
        when(objects.deleteObjects(anyString(), anyList())).thenReturn(List.of(new MediaObjectStoreClient.DeleteFailure("private/documents/owner/entity/a.pdf", "AccessDenied")));
        service.process(now);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(bulk).updateMulti(any(Query.class), update.capture());
        assertThat(update.getValue().getUpdateObject().get("$set").toString()).contains("failed", "AccessDenied");
    }
    @Test void emptyQueueDoesNotCallStorageAndUsesBoundedDueQuery() {
        batch(List.of());
        assertThat(service.process(now)).isEqualTo(new MediaDeletionService.Result(0, 0));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(MediaDeletion.class));
        assertThat(query.getValue().getLimit()).isEqualTo(100);
        assertThat(query.getValue().getQueryObject()).containsKeys("baseGroupId", "nextAttemptAt");
        verify(objects, never()).deleteObjects(anyString(), anyList());
    }
    @Test void disabledStorageCannotAcknowledgePendingWork() {
        assertThatThrownBy(() -> service.process(now)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mongo);
    }
    @Test void enqueueIsOneBulkOfIdempotentInsertOnlyIntentsWithoutStorageCalls() {
        var bulk = mock(BulkOperations.class);
        when(mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, MediaDeletion.class)).thenReturn(bulk);
        service.enqueue("myscoutee-community", List.of("/api/media/private?key=private/documents/owner/entity/a.pdf"));
        var update = ArgumentCaptor.forClass(Update.class);
        verify(bulk).upsert(any(Query.class), update.capture()); verify(bulk).execute();
        assertThat(update.getValue().getUpdateObject().keySet()).containsExactly("$setOnInsert");
        verifyNoInteractions(objects);
    }
    @Test void imageVariantExpandsToExistingImageSetAndInvalidPathIsRejectedBeforeAnyWrite() {
        assertThat(MediaDeletionService.keys("/api/media/private?key=private/images/owner/entity/id/small.webp"))
                .containsExactly("private/images/owner/entity/id/original", "private/images/owner/entity/id/small.webp",
                        "private/images/owner/entity/id/medium.webp", "private/images/owner/entity/id/large.webp");
        assertThatThrownBy(() -> service.enqueue(null, List.of("/api/media/private?key=../outside"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mongo, objects);
    }
}
