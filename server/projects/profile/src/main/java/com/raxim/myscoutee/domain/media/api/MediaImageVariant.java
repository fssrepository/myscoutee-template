package com.raxim.myscoutee.domain.media.api;

/** Canonical stored-image variants, shared with reference parsing. */
public enum MediaImageVariant {
    SMALL("small", 128, 76),
    MEDIUM("medium", 640, 82),
    LARGE("large", 1280, 86);

    private final String key;
    private final int dimension;
    private final int quality;

    MediaImageVariant(String key, int dimension, int quality) {
        this.key = key;
        this.dimension = dimension;
        this.quality = quality;
    }

    public String key() {
        return key;
    }

    public int quality() {
        return quality;
    }

    public String geometry() {
        return dimension + "x" + dimension + ">";
    }

    public String objectName() {
        return key + ".webp";
    }
}
