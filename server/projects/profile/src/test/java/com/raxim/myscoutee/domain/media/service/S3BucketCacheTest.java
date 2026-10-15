package com.raxim.myscoutee.domain.media.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

@Tag("standard")
class S3BucketCacheTest {
    @Test void retryableStorageFailureMakesExactlyOneHttpAttemptPerQueueAttempt() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "<Error><Code>SlowDown</Code><Message>Retry later</Message></Error>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(503, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var s3 = S3Client.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .endpointOverride(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .forcePathStyle(true)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")))
                .build()) {
            var properties = new MediaStorageProperties(); properties.setEnabled(true);
            var store = new S3CompatibleMediaObjectStoreClient(properties, s3);
            org.assertj.core.api.Assertions.assertThat(store.deleteObjects("bucket", java.util.List.of("one", "two")))
                    .containsExactly(new MediaObjectStoreClient.DeleteFailure("one", "SlowDown"),
                            new MediaObjectStoreClient.DeleteFailure("two", "SlowDown"));
            org.assertj.core.api.Assertions.assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    @Test void bulkDeleteUsesOneRequestAndReturnsOnlyActualPerKeyFailures() {
        var s3 = mock(S3Client.class);
        var store = new S3CompatibleMediaObjectStoreClient(new MediaStorageProperties(), s3);
        when(s3.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().errors(
                S3Error.builder().key("missing").code("NoSuchKey").build(),
                S3Error.builder().key("failed").code("SlowDown").build()).build());
        org.assertj.core.api.Assertions.assertThat(store.deleteObjects("bucket", java.util.List.of("done", "missing", "failed")))
                .containsExactly(new MediaObjectStoreClient.DeleteFailure("failed", "SlowDown"));
        verify(s3).deleteObjects(argThat((DeleteObjectsRequest request) -> request.delete().quiet()
                && request.delete().objects().size() == 3));
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }
    @Test void deletedBucketIsRevalidatedOnceAndDifferentBucketsAreNotConflated() {
        var properties = new MediaStorageProperties(); properties.setEnabled(true); properties.setBucket("one");
        var s3 = mock(S3Client.class);
        var store = new S3CompatibleMediaObjectStoreClient(properties, s3);
        store.putObject("one", "key", "text/plain", new byte[0]);
        store.putObject("two", "key", "text/plain", new byte[0]);
        verify(s3, times(2)).headBucket(any(HeadBucketRequest.class));
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(S3Exception.builder()
                .statusCode(404).awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("NoSuchBucket").build()).build()).thenReturn(PutObjectResponse.builder().build());
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        store.putObject("one", "key", "text/plain", new byte[0]);
        verify(s3, times(3)).headBucket(any(HeadBucketRequest.class));
        verify(s3).createBucket(any(CreateBucketRequest.class));
        verify(s3, times(4)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
