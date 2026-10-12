package com.raxim.myscoutee.domain.media.api;

import org.springframework.web.multipart.MultipartFile;

/** Existing image-set upload used by integration resource synchronization. */
public interface ImageUploads {
    MediaStorage.StoredImageSet storeImageSet(String ownerId, String entityId, MultipartFile image);
    /** Import retry: identical owner/entity/content keeps the same image-set keys. */
    MediaStorage.StoredImageSet storeImportedImageSet(String ownerId, String entityId, MultipartFile image);
}

