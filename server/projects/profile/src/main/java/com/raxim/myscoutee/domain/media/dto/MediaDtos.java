package com.raxim.myscoutee.domain.media.dto;

public final class MediaDtos {
    public record ImageEventReference(String id, String title, String organizerId, String organizerName, String location) {}
    public record ImageDetailsDto(String location, String caption, ImageEventReference event) {
        public ImageDetailsDto(String location, String caption) { this(location, caption, null); }
    }

    public static java.util.Map<String, ImageDetailsDto> normalizeImageDetails(
            java.util.Map<String, ImageDetailsDto> details, java.util.List<String> urls) {
        var result = new java.util.LinkedHashMap<String, ImageDetailsDto>();
        if (details == null || urls == null) return result;
        for (String url : urls) {
            var detail = details.get(url);
            if (detail == null) continue;
            String location = detail.location() == null ? "" : detail.location().trim();
            String caption = detail.caption() == null ? "" : detail.caption().replaceAll("\\s+", " ").trim();
            if (location.length() > 240 || caption.length() > 40) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Image details exceed the allowed length");
            }
            result.put(url, new ImageDetailsDto(location, caption, detail.event()));
        }
        return result;
    }

    private MediaDtos() {
    }

    public record MediaImageUploadResponseDto(
            Boolean uploaded,
            String imageUrl,
            String url,
            MediaImageSetDto imageSet,
            String originalUrl,
            String smallUrl,
            String mediumUrl,
            String largeUrl) {
        public MediaImageUploadResponseDto(Boolean uploaded, String imageUrl, String url) {
            this(uploaded, imageUrl, url, null, null, null, null, null);
        }
    }

    public record MediaImageSetDto(
            String originalUrl,
            String smallUrl,
            String mediumUrl,
            String largeUrl) {
    }

    public record MediaImageImportRequestDto(
            String ownerId,
            String entityId,
            String imageUrl) {
    }

    public record MediaUploadResponseDto(
            Boolean uploaded,
            String url) {
    }
}
