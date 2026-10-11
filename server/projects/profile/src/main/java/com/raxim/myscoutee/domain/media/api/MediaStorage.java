package com.raxim.myscoutee.domain.media.api;

import org.springframework.web.multipart.MultipartFile;

public interface MediaStorage extends MediaCleanup, ImageUploads {
    String storeImage(String ownerId, String entityId, MultipartFile image);
    String storeAudio(String ownerId, String entityId, MultipartFile audio);
    String storeDocument(String ownerId, String entityId, MultipartFile file);
    String importImage(String ownerId, String entityId, String imageUrl);
    StoredImageSet importImageSet(String ownerId, String entityId, String imageUrl);
    String storeImageBytes(
            String ownerId,
            String entityId,
            String originalFilename,
            String contentTypeValue,
            byte[] bytes);
    StoredImageSet storeImageBytesSet(
            String ownerId,
            String entityId,
            String originalFilename,
            String contentTypeValue,
            byte[] bytes);
    StoredMediaObject loadPublicObject(String key);
    boolean isEnabled();
    String managedImageSetGroupKey(String mediaUrlOrKey);
    record StoredMediaObject(
            byte[] bytes,
            String contentType) {
    }

    record StoredImageSet(
            String imageUrl,
            String originalUrl,
            String smallUrl,
            String mediumUrl,
            String largeUrl) {
    }

}
