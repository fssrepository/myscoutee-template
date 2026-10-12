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
