package com.raxim.myscoutee.domain.media.api;

import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

/** Only server-owned public namespaces are anonymously readable. */
public final class MediaAccessPaths {
    private static final List<String> PUBLIC_PREFIXES = List.of("public/demo/", "public/branding/",
            "images/demo-profiles/", "images/demo-assets/", "images/demo-events/", "images/system/", "payment-cards/",
            "seeded-help/");

    private MediaAccessPaths() {}

    public static String storageSegment(String value) {
        return (value == null ? "" : value.trim()).replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** Resource editors may reference public media or their own uploaded images only. */
    public static boolean ownedImageReference(String reference, String owner) {
        if (reference == null) return false;
        if (!reference.startsWith("/api/media/") && !reference.startsWith("/media/")) return true;
        try {
            var uri = java.net.URI.create(reference);
            String query = uri.getRawQuery();
            if (query == null || !query.startsWith("key=") || query.contains("&")) return false;
            String key = java.net.URLDecoder.decode(query.substring(4), java.nio.charset.StandardCharsets.UTF_8);
            if (!validKey(key)) return false;
            if (isPublicKey(key)) return true;
            String[] parts = key.split("/");
            int offset = "private".equals(parts[0]) ? 1 : 0;
            return parts.length >= offset + 4 && "images".equals(parts[offset])
                    && storageSegment(owner).equals(parts[offset + 1]);
        } catch (IllegalArgumentException ex) { return false; }
    }

    public static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 2048 && !key.startsWith("/")
                && !key.contains("\\") && key.chars().noneMatch(c -> c < 32 || c == 127)
                && Arrays.stream(key.split("/", -1)).noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
    }

    public static boolean isPublicKey(String key) {
        return validKey(key) && PUBLIC_PREFIXES.stream().anyMatch(key::startsWith);
    }
    public static String managedImageSetGroupKey(String mediaUrlOrKey) {
        return managedImageSetGroupKeyFromObjectKey(extractPublicObjectKey(mediaUrlOrKey));
    }

    public static String managedImageSetGroupKeyFromObjectKey(String key) {
        String normalized = (key == null ? "" : key.trim());
        if (!validKey(normalized) || !(normalized.startsWith("images/")
                || normalized.startsWith("private/images/")
                || normalized.startsWith("public/demo/images/")
                || normalized.startsWith("public/branding/images/"))) {
            return "";
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex <= 0 || slashIndex >= normalized.length() - 1) {
            return "";
        }
        String objectName = normalized.substring(slashIndex + 1);
        if ("original".equals(objectName)) {
            return normalized.substring(0, slashIndex);
        }
        for (MediaImageVariant variant : MediaImageVariant.values()) {
            if (variant.objectName().equals(objectName)) {
                return normalized.substring(0, slashIndex);
            }
        }
        return "";
    }

    public static String extractPublicObjectKey(String value) {
        String normalized = (value == null ? "" : value.trim());
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (!normalized.contains("/media/public") && !normalized.contains("/media/private")) {
            return normalized.contains("/") ? "" : normalized;
        }
        int keyIndex = normalized.indexOf("key=");
        if (keyIndex < 0) {
            return "";
        }
        String encoded = normalized.substring(keyIndex + 4);
        int separatorIndex = encoded.indexOf('&');
        if (separatorIndex >= 0) {
            encoded = encoded.substring(0, separatorIndex);
        }
        try {
            return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

}
