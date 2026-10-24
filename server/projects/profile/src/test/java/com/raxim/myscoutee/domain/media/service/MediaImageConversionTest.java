package com.raxim.myscoutee.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.raxim.myscoutee.domain.media.api.MediaImageVariant;
import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;

@org.junit.jupiter.api.Tag("standard")
class MediaImageConversionTest {
    @TempDir Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void converterUsesPrivateDirectoryAndCleansFilesOnSuccessAndFailure(boolean success) throws Exception {
        Path command = temporaryDirectory.resolve("converter");
        Files.writeString(command, """
                #!/bin/sh
                set -eu
                printf '%s' "$1" > "$(dirname "$0")/input-path"
                stat -c '%a' "$(dirname "$1")" > "$(dirname "$0")/directory-mode"
                """ + (success ? "cp -- \"$1\" \"$8\"\n" : "exit 1\n"));
        Files.setPosixFilePermissions(command, PosixFilePermissions.fromString("rwx------"));
        var storage = new MediaStorageService(new MediaStorageProperties(), mock(MediaObjectStoreClient.class));
        byte[] source = {1, 2, 3};
        byte[] result = ReflectionTestUtils.invokeMethod(storage, "runImageMagickVariant",
                command.toString(), "photo.png", "image/png", source, MediaImageVariant.SMALL);
        if (success) assertThat(result).isEqualTo(source);
        else assertThat(result).isNull();
        assertThat(Files.readString(temporaryDirectory.resolve("directory-mode")).trim()).isEqualTo("700");
        Path input = Path.of(Files.readString(temporaryDirectory.resolve("input-path")));
        assertThat(input).doesNotExist();
        assertThat(input.getParent()).doesNotExist();
    }
}
