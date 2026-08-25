package jrm.profile.fix.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.profile.data.Container;
import jrm.profile.scan.options.FormatOptions;

@DisplayName("OpenContainer.deleteEmptyFolders")
class OpenContainerDeleteEmptyFoldersTest {

    @TempDir
    Path tempDir;

    private OpenContainer action;

    @BeforeEach
    void setUp() {
        action = new OpenContainer(mock(Container.class), FormatOptions.ZIP, 0L);
    }

    @Test
    @DisplayName("deletes a nested empty directory tree")
    void deletesNestedEmptyDirs() throws IOException {
        final Path root = Files.createDirectories(tempDir.resolve("empty").resolve("a").resolve("b"));
        final Path base = root.getParent().getParent();

        assertThat(action.deleteEmptyFolders(base.toFile())).isZero();
        assertThat(base).doesNotExist();
    }

    @Test
    @DisplayName("keeps folders that still contain files and returns leftover size")
    void keepsFoldersWithFiles() throws IOException {
        final Path base = Files.createDirectories(tempDir.resolve("keep"));
        final Path empty = Files.createDirectories(base.resolve("empty"));
        final Path payload = base.resolve("data.bin");
        Files.writeString(payload, "abc", StandardCharsets.UTF_8);

        assertThat(action.deleteEmptyFolders(base)).isEqualTo(3L);
        assertThat(empty).doesNotExist();
        assertThat(payload).exists();
        assertThat(base).exists();
    }

    @Test
    @DisplayName("does not overflow on a deep directory chain")
    void capsDeepDirectoryChain() throws IOException {
        Path current = Files.createDirectories(tempDir.resolve("deep"));
        final Path base = current;
        for (int i = 0; i < OpenContainer.MAX_FOLDER_DEPTH + 5; i++)
            current = Files.createDirectories(current.resolve("d"));

        assertThatCode(() -> action.deleteEmptyFolders(base.toFile())).doesNotThrowAnyException();
        assertThat(base).exists();
        assertThat(current).exists();
    }

    @Test
    @DisplayName("returns zero for a null folder")
    void nullFolderReturnsZero() {
        assertThat(action.deleteEmptyFolders((java.io.File) null)).isZero();
        assertThat(action.deleteEmptyFolders((Path) null)).isZero();
    }
}
