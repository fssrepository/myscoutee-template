package com.raxim.myscoutee.domain.media.service;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3CompatibleMediaObjectStoreClient implements MediaObjectStoreClient {
    private final MediaStorageProperties properties;
    private final S3Client s3Client;
    private final java.util.Set<String> ensuredBuckets = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @org.springframework.beans.factory.annotation.Autowired
    public S3CompatibleMediaObjectStoreClient(MediaStorageProperties properties) {
        this(properties, createClient(properties));
    }

    S3CompatibleMediaObjectStoreClient(MediaStorageProperties properties, S3Client s3Client) {
        this.properties = properties;
        this.s3Client = s3Client;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled()
                && s3Client != null
                && StringUtils.hasText(safeText(properties.getBucket()));
    }

    @Override
    public void putObject(String bucket, String key, String contentType, byte[] bytes) {
        ensureBucketExists(bucket);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception exception) {
            if (exception.awsErrorDetails() == null
                    || !"NoSuchBucket".equals(exception.awsErrorDetails().errorCode())) throw exception;
            // The bucket can be removed after startup. Reuse the existing ensure path
            // once, without adding a HEAD request to every successful upload.
            ensuredBuckets.remove(bucket);
            ensureBucketExists(bucket);
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        }
    }

    @Override
    public StoredObject getObject(String bucket, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request);
            return new StoredObject(bytes.asByteArray(), safeText(bytes.response().contentType()));
        } catch (S3Exception ex) {
            return null;
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        if (!isEnabled() || !StringUtils.hasText(safeText(bucket)) || !StringUtils.hasText(safeText(key))) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public java.util.List<DeleteFailure> deleteObjects(String bucket, java.util.List<String> keys) {
        if (!isEnabled()) throw new IllegalStateException("Media storage is unavailable");
        if (keys.isEmpty()) return java.util.List.of();
        if (keys.size() > 1000) throw new IllegalArgumentException("Media delete batch exceeds storage limit");
        var objects = keys.stream().map(key -> software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder().key(key).build()).toList();
        try {
            var result = s3Client.deleteObjects(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.builder()
                    .bucket(bucket).delete(software.amazon.awssdk.services.s3.model.Delete.builder().objects(objects).quiet(true).build())
                    .overrideConfiguration(config -> config.apiCallTimeout(java.time.Duration.ofSeconds(20))
                            .addPlugin(client -> client.overrideConfiguration(overrides -> overrides.retryStrategy(strategy -> strategy.maxAttempts(1)))))
                    .build());
            return result.errors().stream().filter(error -> !"NoSuchKey".equals(error.code()))
                    .map(error -> new DeleteFailure(error.key(), error.code())).toList();
        } catch (S3Exception failure) {
            String code = failure.awsErrorDetails() == null ? "HTTP_" + failure.statusCode() : failure.awsErrorDetails().errorCode();
            return keys.stream().map(key -> new DeleteFailure(key, code)).toList();
        }
    }

    private static S3Client createClient(MediaStorageProperties storageProperties) {
        if (storageProperties == null || !storageProperties.isEnabled() || !StringUtils.hasText(safeText(storageProperties.getBucket()))) {
            return null;
        }
        try {
            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(StringUtils.hasText(safeText(storageProperties.getRegion()))
                            ? storageProperties.getRegion().trim()
                            : "eu-central-1"))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(storageProperties.isPathStyleAccess())
                            .build())
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            safeText(storageProperties.getAccessKey()),
                            safeText(storageProperties.getSecretKey()))));
            if (StringUtils.hasText(safeText(storageProperties.getEndpoint()))) {
                builder.endpointOverride(URI.create(storageProperties.getEndpoint().trim()));
            }
            return builder.build();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private synchronized void ensureBucketExists(String bucket) {
        if (!isEnabled() || ensuredBuckets.contains(bucket) || !StringUtils.hasText(safeText(bucket))) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        ensuredBuckets.add(bucket);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
