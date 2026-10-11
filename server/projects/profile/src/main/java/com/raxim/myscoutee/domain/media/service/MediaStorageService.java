package com.raxim.myscoutee.domain.media.service;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;
import com.raxim.myscoutee.domain.media.service.MediaObjectStoreClient;
import com.raxim.myscoutee.domain.media.service.MediaObjectStoreClient.StoredObject;
import com.raxim.myscoutee.domain.media.api.MediaAccessPaths;
import com.raxim.myscoutee.domain.media.api.MediaImageVariant;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class MediaStorageService implements com.raxim.myscoutee.domain.media.api.MediaStorage {
    private static final String IMAGE_MEDIA_KIND = "images";
    private static final String AUDIO_MEDIA_KIND = "audio";
    private static final String IMAGE_VARIANT_CONTENT_TYPE = "image/webp";
    private static final String ORIGINAL_IMAGE_OBJECT_NAME = "original";
    private static final List<String> IMAGE_MAGICK_COMMANDS = List.of("magick", "convert");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES = Set.of(
            "audio/mpeg",
            "audio/mp4",
            "audio/aac",
            "audio/ogg",
            "audio/webm",
            "audio/wav",
            "audio/x-wav");
    private static final Duration REMOTE_IMAGE_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REMOTE_IMAGE_REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration IMAGE_VARIANT_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_REMOTE_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REMOTE_IMAGE_REDIRECTS = 3;

    private final MediaStorageProperties properties;
    private final MediaObjectStoreClient objectStoreClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REMOTE_IMAGE_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public MediaStorageService(
            MediaStorageProperties properties,
            MediaObjectStoreClient objectStoreClient) {
        this.properties = properties;
        this.objectStoreClient = objectStoreClient;
    }

    public String storeImage(String ownerId, String entityId, MultipartFile image) {
        StoredImageSet imageSet = storeImageSet(ownerId, entityId, image);
        return imageSet == null ? null : imageSet.imageUrl();
    }

    public StoredImageSet storeImageSet(String ownerId, String entityId, MultipartFile image) {
        if (!isEnabled() || image == null || image.isEmpty()) {
            return null;
        }
        try {
            return storeImageBytesSet(
                    ownerId,
                    entityId,
                    image.getOriginalFilename(),
                    image.getContentType(),
                    image.getBytes());
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    public StoredImageSet storeImportedImageSet(String ownerId, String entityId, MultipartFile image) {
        if (!isEnabled() || image == null || image.isEmpty()) return null;
        String owner = sanitizeSegment(ownerId), entity = sanitizeSegment(entityId);
        String contentType = normalizeContentType(image.getContentType());
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(entity) || !isAllowedImageContentType(contentType)) return null;
        try {
            byte[] bytes = image.getBytes();
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(contentType.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            String key = "import-" + java.util.HexFormat.of().formatHex(digest.digest(bytes));
            return storeImageObjectSet(owner, entity, image.getOriginalFilename(), contentType, bytes, key, false);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read import image", error);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public String storeAudio(String ownerId, String entityId, MultipartFile audio) {
        if (!isEnabled() || audio == null || audio.isEmpty()) {
            return null;
        }
        try {
            return storeMediaBytes(
                    AUDIO_MEDIA_KIND,
                    ownerId,
                    entityId,
                    audio.getOriginalFilename(),
                    audio.getContentType(),
                    audio.getBytes(),
                    "audio/");
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    public String storeDocument(String ownerId, String entityId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10485760 || !List.of(
                "application/pdf", "text/plain", "text/csv", "application/msword", "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet").contains(file.getContentType())) return null;
        try { return storeMediaBytes("documents", ownerId, entityId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), ""); }
        catch (IOException ex) { return null; }
    }

    public String importImage(String ownerId, String entityId, String imageUrl) {
        StoredImageSet imageSet = importImageSet(ownerId, entityId, imageUrl);
        return imageSet == null ? null : imageSet.imageUrl();
    }

    public StoredImageSet importImageSet(String ownerId, String entityId, String imageUrl) {
        if (!isEnabled()) {
            return null;
        }
        URI remoteImageUri = normalizeRemoteImageUri(imageUrl);
        if (remoteImageUri == null) {
            return null;
        }
        ImportedImage importedImage = downloadRemoteImage(remoteImageUri, 0);
        if (importedImage == null) {
            return null;
        }
        return storeImageBytesSet(
                ownerId,
                entityId,
                importedImage.originalFilename(),
                importedImage.contentType(),
                importedImage.bytes());
    }

    public String storeImageBytes(
            String ownerId,
            String entityId,
            String originalFilename,
            String contentTypeValue,
            byte[] bytes) {
        StoredImageSet imageSet = storeImageBytesSet(ownerId, entityId, originalFilename, contentTypeValue, bytes);
        return imageSet == null ? null : imageSet.imageUrl();
    }

    public StoredImageSet storeImageBytesSet(
            String ownerId,
            String entityId,
            String originalFilename,
            String contentTypeValue,
            byte[] bytes) {
        if (!isEnabled() || bytes == null || bytes.length == 0) {
            return null;
        }
        String normalizedOwnerId = sanitizeSegment(ownerId);
        if (!StringUtils.hasText(normalizedOwnerId)) {
            return null;
        }
        String contentType = normalizeContentType(contentTypeValue);
        if (!isAllowedImageContentType(contentType)) {
            return null;
        }
        try {
            return storeImageObjectSet(
                    normalizedOwnerId,
                    sanitizeSegment(entityId),
                    originalFilename,
                    contentType,
                    bytes);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String storeMediaBytes(
            String mediaKind,
            String ownerId,
            String entityId,
            String originalFilename,
            String contentTypeValue,
            byte[] bytes,
            String requiredContentTypePrefix) {
        if (!isEnabled() || bytes == null || bytes.length == 0) {
            return null;
        }
        String normalizedMediaKind = sanitizeSegment(mediaKind);
        String normalizedOwnerId = sanitizeSegment(ownerId);
        String normalizedEntityId = sanitizeSegment(entityId);
        if (!StringUtils.hasText(normalizedMediaKind) || !StringUtils.hasText(normalizedOwnerId)) {
            return null;
        }
        String contentType = normalizeContentType(contentTypeValue);
        if (!contentType.startsWith(safeText(requiredContentTypePrefix))) {
            return null;
        }
        if ("image/".equals(requiredContentTypePrefix) && !isAllowedImageContentType(contentType)) {
            return null;
        }
        if ("audio/".equals(requiredContentTypePrefix) && !ALLOWED_AUDIO_CONTENT_TYPES.contains(contentType)) {
            return null;
        }
        try {
            String objectKey = String.join("/",
                    uploadNamespace(normalizedEntityId),
                    normalizedMediaKind,
                    normalizedOwnerId,
                    StringUtils.hasText(normalizedEntityId) ? normalizedEntityId : "shared",
                    Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + resolveExtension(originalFilename, contentType));
            objectStoreClient.putObject(properties.getBucket(), objectKey, contentType, bytes);
            return publicMediaUrl(objectKey);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public StoredMediaObject loadPublicObject(String key) {
        if (!isEnabled()) {
            return null;
        }
        String normalizedKey = safeText(key);
        if (!StringUtils.hasText(normalizedKey)) {
            return null;
        }
        StoredObject storedObject = objectStoreClient.getObject(properties.getBucket(), normalizedKey);
        if (storedObject == null || storedObject.bytes() == null || storedObject.bytes().length == 0) {
            return null;
        }
        return new StoredMediaObject(storedObject.bytes(), storedObject.contentType());
    }

    public void deletePublicObjectOrThrow(String mediaUrlOrKey) {
        if (!isEnabled()) {
            return;
        }
        String key = MediaAccessPaths.extractPublicObjectKey(mediaUrlOrKey);
        if (!StringUtils.hasText(key)) {
            return;
        }
        if (deleteManagedImageSetObjects(key)) {
            return;
        }
        objectStoreClient.deleteObject(properties.getBucket(), key);
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && objectStoreClient != null
                && objectStoreClient.isEnabled()
                && StringUtils.hasText(safeText(properties.getBucket()));
    }

    private StoredImageSet storeImageObjectSet(
            String normalizedOwnerId,
            String normalizedEntityId,
            String originalFilename,
            String contentType,
            byte[] bytes) {
        return storeImageObjectSet(normalizedOwnerId, normalizedEntityId, originalFilename, contentType, bytes,
                Instant.now().toEpochMilli() + "-" + UUID.randomUUID(), true);
    }

    private StoredImageSet storeImageObjectSet(String normalizedOwnerId, String normalizedEntityId,
            String originalFilename, String contentType, byte[] bytes, String groupId, boolean discardPartial) {
        String imageGroupKey = String.join("/", uploadNamespace(normalizedEntityId), IMAGE_MEDIA_KIND,
                normalizedOwnerId, StringUtils.hasText(normalizedEntityId) ? normalizedEntityId : "shared", groupId);
        String originalKey = imageGroupKey + "/" + ORIGINAL_IMAGE_OBJECT_NAME;
        objectStoreClient.putObject(properties.getBucket(), originalKey, contentType, bytes);
        String originalUrl = publicMediaUrl(originalKey);

        Map<MediaImageVariant, String> variantUrls = storeImageVariants(imageGroupKey, originalFilename, contentType, bytes);
        String smallUrl = variantUrls.get(MediaImageVariant.SMALL);
        String mediumUrl = variantUrls.get(MediaImageVariant.MEDIUM);
        String largeUrl = variantUrls.get(MediaImageVariant.LARGE);
        if (StringUtils.hasText(smallUrl) && StringUtils.hasText(mediumUrl) && StringUtils.hasText(largeUrl)) {
            return new StoredImageSet(largeUrl, originalUrl, smallUrl, mediumUrl, largeUrl);
        }
        // A concurrent/retried import owns these same keys. Never delete a
        // completed peer upload because this attempt failed to convert a variant.
        if (discardPartial) deleteManagedImageSetObjects(originalKey);
        return null;
    }

    private Map<MediaImageVariant, String> storeImageVariants(
            String imageGroupKey,
            String originalFilename,
            String contentType,
            byte[] bytes) {
        EnumMap<MediaImageVariant, String> urls = new EnumMap<>(MediaImageVariant.class);
        try {
            for (MediaImageVariant variant : MediaImageVariant.values()) {
                byte[] converted = convertImageVariant(originalFilename, contentType, bytes, variant);
                if (converted == null || converted.length == 0) {
                    return Map.of();
                }
                String objectKey = imageGroupKey + "/" + variant.objectName();
                objectStoreClient.putObject(properties.getBucket(), objectKey, IMAGE_VARIANT_CONTENT_TYPE, converted);
                urls.put(variant, publicMediaUrl(objectKey));
            }
            return urls;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private byte[] convertImageVariant(
            String originalFilename,
            String contentType,
            byte[] bytes,
            MediaImageVariant variant) {
        for (String commandName : IMAGE_MAGICK_COMMANDS) {
            byte[] converted = runImageMagickVariant(commandName, originalFilename, contentType, bytes, variant);
            if (converted != null && converted.length > 0) {
                return converted;
            }
        }
        return null;
    }

    private byte[] runImageMagickVariant(
            String commandName,
            String originalFilename,
            String contentType,
            byte[] bytes,
            MediaImageVariant variant) {
        Path inputPath = null;
        Path outputPath = null;
        try {
            inputPath = Files.createTempFile("myscoutee-image-source-", resolveTempExtension(originalFilename, contentType));
            outputPath = Files.createTempFile("myscoutee-image-" + variant.key() + "-", ".webp");
            Files.write(inputPath, bytes);
            Files.deleteIfExists(outputPath);
            Process process = new ProcessBuilder(
                    commandName,
                    inputPath.toString(),
                    "-auto-orient",
                    "-strip",
                    "-resize",
                    variant.geometry(),
                    "-quality",
                    Integer.toString(variant.quality()),
                    outputPath.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(IMAGE_VARIANT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputPath)) {
                return null;
            }
            byte[] converted = Files.readAllBytes(outputPath);
            return converted.length == 0 ? null : converted;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            deleteTempFile(inputPath);
            deleteTempFile(outputPath);
        }
    }

    private void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String resolveTempExtension(String originalFilename, String contentType) {
        String extension = resolveExtension(originalFilename, contentType);
        return extension.matches("\\.[a-z0-9]{1,8}") ? extension : ".img";
    }

    private boolean isAllowedImageContentType(String contentType) {
        return StringUtils.hasText(contentType) && ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType);
    }

    private String publicMediaUrl(String objectKey) {
        return (MediaAccessPaths.isPublicKey(objectKey) ? "/media/public?key=" : "/api/media/private?key=")
                + UriUtils.encodeQueryParam(objectKey, StandardCharsets.UTF_8);
    }

    private String uploadNamespace(String entityId) {
        if (MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO) return "public/demo";
        var actor = SecurityContextHolder.getContext().getAuthentication();
        if ("deployment-branding-logo".equals(entityId) && actor != null && actor.isAuthenticated()
                && actor.getAuthorities().stream().anyMatch(authority -> "O".equals(authority.getAuthority()))) return "public/branding";
        return "private";
    }

    private boolean deleteManagedImageSetObjects(String key) {
        String imageGroupKey = MediaAccessPaths.managedImageSetGroupKeyFromObjectKey(key);
        if (!StringUtils.hasText(imageGroupKey)) {
            return false;
        }
        objectStoreClient.deleteObject(properties.getBucket(), imageGroupKey + "/" + ORIGINAL_IMAGE_OBJECT_NAME);
        for (MediaImageVariant variant : MediaImageVariant.values()) {
            objectStoreClient.deleteObject(properties.getBucket(), imageGroupKey + "/" + variant.objectName());
        }
        return true;
    }

    public String managedImageSetGroupKey(String mediaUrlOrKey) {
        return MediaAccessPaths.managedImageSetGroupKey(mediaUrlOrKey);
    }

    private String normalizeContentType(String value) {
        String normalized = safeText(value).toLowerCase(Locale.ROOT);
        int parameterIndex = normalized.indexOf(';');
        return parameterIndex >= 0 ? normalized.substring(0, parameterIndex).trim() : normalized;
    }

    private URI normalizeRemoteImageUri(String value) {
        String normalized = safeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String scheme = safeText(uri.getScheme()).toLowerCase(Locale.ROOT);
        String host = safeText(uri.getHost()).toLowerCase(Locale.ROOT);
        if ((!"http".equals(scheme) && !"https".equals(scheme)) || !StringUtils.hasText(host) || StringUtils.hasText(uri.getUserInfo())) {
            return null;
        }
        if (isBlockedRemoteHost(host)) {
            return null;
        }
        return uri;
    }

    private ImportedImage downloadRemoteImage(URI uri, int redirectCount) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REMOTE_IMAGE_REQUEST_TIMEOUT)
                    .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif,image/*;q=0.8,*/*;q=0.1")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int statusCode = response.statusCode();
            if (statusCode >= 300 && statusCode < 400) {
                if (redirectCount >= MAX_REMOTE_IMAGE_REDIRECTS) {
                    return null;
                }
                Optional<String> location = response.headers().firstValue("location");
                if (location.isEmpty()) {
                    return null;
                }
                URI redirectUri = normalizeRemoteImageUri(uri.resolve(location.get()).toString());
                return redirectUri == null ? null : downloadRemoteImage(redirectUri, redirectCount + 1);
            }
            if (statusCode < 200 || statusCode >= 300) {
                return null;
            }
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (declaredLength > MAX_REMOTE_IMAGE_BYTES) {
                return null;
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_REMOTE_IMAGE_BYTES) {
                return null;
            }
            String contentType = resolveRemoteImageContentType(
                    uri,
                    response.headers().firstValue("content-type").orElse(""));
            if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
                return null;
            }
            return new ImportedImage(bytes, contentType, resolveRemoteImageFilename(uri, contentType));
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String resolveRemoteImageContentType(URI uri, String contentTypeValue) {
        String contentType = normalizeContentType(contentTypeValue);
        if (ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            return contentType;
        }
        String path = safeText(uri.getPath()).toLowerCase(Locale.ROOT);
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        return "";
    }

    private String resolveRemoteImageFilename(URI uri, String contentType) {
        String path = safeText(uri.getPath());
        int slashIndex = path.lastIndexOf('/');
        String filename = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
        filename = sanitizeSegment(filename);
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            return filename;
        }
        return "source-image" + resolveExtension("", contentType);
    }

    private boolean isBlockedRemoteHost(String host) {
        if (!StringUtils.hasText(host)
                || "localhost".equals(host)
                || host.endsWith(".localhost")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedRemoteAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            return true;
        }
    }

    private boolean isBlockedRemoteAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return true;
    }

    private String sanitizeSegment(String value) {
        return com.raxim.myscoutee.domain.media.api.MediaAccessPaths.storageSegment(value);
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String safeFileName = safeText(originalFilename);
        int dotIndex = safeFileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < safeFileName.length() - 1) {
            return safeFileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4", "audio/aac" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/webm" -> ".webm";
            case "audio/wav", "audio/x-wav" -> ".wav";
            default -> contentType.startsWith("audio/") ? ".audio" : ".jpg";
        };
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record ImportedImage(
            byte[] bytes,
            String contentType,
            String originalFilename) {
    }


}
