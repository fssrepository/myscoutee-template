package com.raxim.myscoutee.domain.media.service;

public interface MediaObjectStoreClient {
    boolean isEnabled();

    void putObject(String bucket, String key, String contentType, byte[] bytes);

    StoredObject getObject(String bucket, String key);

    void deleteObject(String bucket, String key);

    record DeleteFailure(String key, String code) {}
    /** Returns per-key failures; missing objects are successful idempotent deletes. */
    default java.util.List<DeleteFailure> deleteObjects(String bucket, java.util.List<String> keys) {
        for (String key : keys) deleteObject(bucket, key);
        return java.util.List.of();
    }

    record StoredObject(
            byte[] bytes,
            String contentType) {
    }
}
