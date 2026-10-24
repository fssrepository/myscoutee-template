package com.raxim.myscoutee.domain.media.service;

import com.raxim.myscoutee.domain.media.api.MediaAccessPaths;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("standard")
class MediaAccessPathsTest {
    @Test
    void storageSegmentsKeepStableNamesAndHandleLongPaddingWithoutRegexBacktracking() {
        assertThat(MediaAccessPaths.storageSegment(null)).isEmpty();
        assertThat(MediaAccessPaths.storageSegment("  --owner / resource--  ")).isEqualTo("owner-resource");
        assertThat(MediaAccessPaths.storageSegment("owner.name_v1")).isEqualTo("owner.name_v1");
        assertThat(MediaAccessPaths.storageSegment("-".repeat(100_000))).isEmpty();
    }
    @Test
    void managedImageCleanupRecognizesEveryCurrentUploadNamespace() {
        for (String prefix : new String[]{"images/", "private/images/", "public/demo/images/", "public/branding/images/"}) {
            String group = prefix + "owner/entity/upload";
            assertThat(MediaAccessPaths.managedImageSetGroupKeyFromObjectKey(group + "/original")).isEqualTo(group);
            for (var variant : com.raxim.myscoutee.domain.media.api.MediaImageVariant.values()) {
                assertThat(MediaAccessPaths.managedImageSetGroupKeyFromObjectKey(group + "/" + variant.objectName())).isEqualTo(group);
            }
        }
        for (String key : new String[]{"private/images/../original", "private/audio/owner/original",
                "public/other/images/owner/original", "private/images//original"}) {
            assertThat(MediaAccessPaths.managedImageSetGroupKeyFromObjectKey(key)).isEmpty();
        }
    }

    @Test
    void bundledHelpImagesArePublicWhileUploadedMediaRemainsProtected() {
        assertThat(MediaAccessPaths.isPublicKey("seeded-help/c00cddb3611da2aff481b1c9.svg")).isTrue();
        assertThat(MediaAccessPaths.isPublicKey("images/demo-profiles/user/small.webp")).isTrue();
        assertThat(MediaAccessPaths.isPublicKey("private/images/user/slot/large.webp")).isFalse();
        assertThat(MediaAccessPaths.isPublicKey("images/user/slot/large.webp")).isFalse();
        assertThat(MediaAccessPaths.isPublicKey("seeded-help-private/image.svg")).isFalse();
    }

    @Test
    void publicHelpPrefixDoesNotAllowPathTraversalOrMalformedKeys() {
        for (String key : new String[] {
                "seeded-help/../private/image.svg", "seeded-help/./image.svg",
                "seeded-help//image.svg", "seeded-help/", "/seeded-help/image.svg",
                "seeded-help/..\\private\\image.svg", "seeded-help/image\n.svg"
        }) {
            assertThat(MediaAccessPaths.isPublicKey(key)).as(key).isFalse();
        }
    }
    @Test
    void legacyManagedImageGroupingUsesTheCanonicalVariantNames() {
        for (var variant : com.raxim.myscoutee.domain.media.api.MediaImageVariant.values()) {
            String reference = "/api/media/public?key=images%2Fowner%2Fupload%2F" + variant.objectName();
            assertThat(MediaAccessPaths.managedImageSetGroupKey(reference)).isEqualTo("images/owner/upload");
        }
        assertThat(MediaAccessPaths.managedImageSetGroupKey("/api/media/public?key=images%2Fowner%2Fupload%2Foriginal&unused=true"))
                .isEqualTo("images/owner/upload");
        assertThat(MediaAccessPaths.managedImageSetGroupKey("https://outside.invalid/image.webp")).isEmpty();
        assertThat(MediaAccessPaths.managedImageSetGroupKey("/api/media/public?key=%broken")).isEmpty();
        assertThat(MediaAccessPaths.managedImageSetGroupKey(null)).isEmpty();
    }
}
