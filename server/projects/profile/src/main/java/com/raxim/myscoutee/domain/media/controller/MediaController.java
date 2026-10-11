package com.raxim.myscoutee.domain.media.controller;

import java.time.Duration;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import com.raxim.myscoutee.domain.media.dto.MediaDtos.MediaImageImportRequestDto;
import com.raxim.myscoutee.domain.media.dto.MediaDtos.MediaImageSetDto;
import com.raxim.myscoutee.domain.media.dto.MediaDtos.MediaImageUploadResponseDto;
import com.raxim.myscoutee.domain.media.dto.MediaDtos.MediaUploadResponseDto;
import com.raxim.myscoutee.domain.access.api.AccessPolicyKeys;
import com.raxim.myscoutee.domain.access.api.RequiresPolicy;
import com.raxim.myscoutee.domain.media.service.MediaStorageService;
import com.raxim.myscoutee.domain.media.api.MediaStorage.StoredImageSet;
import com.raxim.myscoutee.domain.media.api.MediaStorage.StoredMediaObject;
import com.raxim.myscoutee.domain.media.api.MediaAccessPaths;

@RestController
@RequestMapping("/media")
public class MediaController {
    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPolicy(
            key = AccessPolicyKeys.USER_MEDIA_UPLOAD,
            subject = "#ownerId",
            resourceId = "#ownerId",
            skipWhenAuthenticationOptional = true)
    public MediaImageUploadResponseDto uploadImage(
            @RequestParam("ownerId") String ownerId,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam("image") MultipartFile image) {
        StoredImageSet imageSet = mediaStorageService.storeImageSet(ownerId, entityId, image);
        if (imageSet == null || imageSet.imageUrl() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image upload unavailable.");
        }
        return imageUploadResponse(true, imageSet);
    }

    @PostMapping(path = "/images/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresPolicy(
            key = AccessPolicyKeys.USER_MEDIA_IMPORT,
            subject = "#request?.ownerId()",
            resourceId = "#request?.ownerId()",
            skipWhenAuthenticationOptional = true)
    public MediaImageUploadResponseDto importImage(@RequestBody MediaImageImportRequestDto request) {
        String ownerId = request == null ? null : request.ownerId();
        StoredImageSet imageSet = mediaStorageService.importImageSet(
                ownerId,
                request == null ? null : request.entityId(),
                request == null ? null : request.imageUrl());
        return imageUploadResponse(imageSet != null && imageSet.imageUrl() != null, imageSet);
    }

    @PostMapping(path = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPolicy(
            key = AccessPolicyKeys.USER_MEDIA_UPLOAD,
            subject = "#ownerId",
            resourceId = "#ownerId",
            skipWhenAuthenticationOptional = true)
    public MediaUploadResponseDto uploadAudio(
            @RequestParam("ownerId") String ownerId,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam("audio") MultipartFile audio) {
        String audioUrl = mediaStorageService.storeAudio(ownerId, entityId, audio);
        if (audioUrl == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Audio upload unavailable.");
        }
        return new MediaUploadResponseDto(true, audioUrl);
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPolicy(key = AccessPolicyKeys.USER_MEDIA_UPLOAD, subject = "#ownerId", resourceId = "#ownerId")
    public MediaUploadResponseDto uploadDocument(@RequestParam String ownerId, @RequestParam String entityId,
            @RequestParam("document") MultipartFile document) {
        String url = mediaStorageService.storeDocument(ownerId, entityId, document);
        if (url == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document upload unavailable or unsupported file.");
        return new MediaUploadResponseDto(true, url);
    }

    @GetMapping("/public")
    public ResponseEntity<byte[]> loadPublic(@RequestParam("key") String key) {
        if (!MediaAccessPaths.validKey(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media key.");
        if (!MediaAccessPaths.isPublicKey(key)) {
            // Retained private references must never bypass authentication via the old public route.
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .location(URI.create("/api/media/private?key=" + UriUtils.encodeQueryParam(key, StandardCharsets.UTF_8)))
                    .cacheControl(CacheControl.noStore()).build();
        }
        return loadMedia(key, true);
    }

    @GetMapping("/private")
    @RequiresPolicy(key = AccessPolicyKeys.MEDIA_READ, subject = "#actorUserId", resourceId = "#key")
    public ResponseEntity<byte[]> loadPrivate(@RequestParam("key") String key) {
        if (!MediaAccessPaths.validKey(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media key.");
        return loadMedia(key, false);
    }

    private ResponseEntity<byte[]> loadMedia(String key, boolean publicMedia) {
        StoredMediaObject stored = mediaStorageService.loadPublicObject(key);
        if (stored == null || stored.bytes() == null || stored.bytes().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        }
        var response = ResponseEntity.ok()
                .contentType(resolveMediaType(stored.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(publicMedia ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic() : CacheControl.noStore().cachePrivate());
        if (key.startsWith("private/documents/")) response.header("Content-Disposition", "attachment");
        return response.body(stored.bytes());
    }

    private MediaType resolveMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private MediaImageUploadResponseDto imageUploadResponse(boolean uploaded, StoredImageSet imageSet) {
        if (!uploaded || imageSet == null) {
            return new MediaImageUploadResponseDto(false, null, null);
        }
        MediaImageSetDto dto = new MediaImageSetDto(
                imageSet.originalUrl(),
                imageSet.smallUrl(),
                imageSet.mediumUrl(),
                imageSet.largeUrl());
        return new MediaImageUploadResponseDto(
                true,
                imageSet.imageUrl(),
                imageSet.imageUrl(),
                dto,
                imageSet.originalUrl(),
                imageSet.smallUrl(),
                imageSet.mediumUrl(),
                imageSet.largeUrl());
    }
}
