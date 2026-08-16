package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("IOUtils path containment")
class IOUtilsPathTraversalTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {
        "../evil-outside.txt",
        "..\\evil-outside.txt",
        "../../evil-outside.txt",
        "subdir/../../evil-outside.txt",
        "/etc/passwd",
        "C:/Windows/System32/evil.dll",
        "file\0.txt"
    })
    @DisplayName("should reject unsafe entry paths")
    void shouldRejectUnsafeEntryPaths(String maliciousEntry) {
        final Path base = tempDir.resolve("base");
        assertThatThrownBy(() -> IOUtils.resolveContainedPath(base, maliciousEntry))
            .isInstanceOf(IOException.class)
            .hasMessageMatching(".*(escapes base directory|absolute|null byte).*");
    }

    @Test
    @DisplayName("should accept safe relative paths under base")
    void shouldAcceptSafeRelativePaths() throws IOException {
        final Path base = Files.createDirectories(tempDir.resolve("base"));
        final Path resolved = IOUtils.resolveContainedPath(base, "subdir/safe.txt");
        assertThat(resolved.normalize().startsWith(base.toAbsolutePath().normalize())).isTrue();
        assertThat(resolved.getFileName()).hasToString("safe.txt");
    }

    @Test
    @DisplayName("File overload stays under base")
    void fileOverloadStaysUnderBase() throws IOException {
        final Path base = Files.createDirectories(tempDir.resolve("base"));
        final var resolved = IOUtils.resolveContainedFile(base.toFile(), "a/b.bin");
        assertThat(resolved.toPath().normalize().startsWith(base.toAbsolutePath().normalize())).isTrue();
    }
}
