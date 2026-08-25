package jrm.fx.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ProfilePanelController.searchDats")
class ProfilePanelControllerSearchDatsTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("collects dat and xml files and ignores other files")
    void collectsDatAndXmlFiles() throws IOException {
        final Path root = Files.createDirectories(tempDir.resolve("dats"));
        final Path dat = Files.writeString(root.resolve("mame.dat"), "dat", StandardCharsets.UTF_8);
        final Path xml = Files.writeString(root.resolve("list.xml"), "xml", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("notes.txt"), "no", StandardCharsets.UTF_8);
        final Path nested = Files.createDirectories(root.resolve("sub"));
        final Path nestedDat = Files.writeString(nested.resolve("soft.dat"), "soft", StandardCharsets.UTF_8);

        final var found = ProfilePanelController.searchDats(root.toFile(), new ArrayList<>());

        assertThat(found).containsExactlyInAnyOrder(dat.toFile(), xml.toFile(), nestedDat.toFile());
    }

    @Test
    @DisplayName("accepts a single matching file")
    void acceptsSingleMatchingFile() throws IOException {
        final Path dat = Files.writeString(tempDir.resolve("only.dat"), "dat", StandardCharsets.UTF_8);

        assertThat(ProfilePanelController.searchDats(dat.toFile(), new ArrayList<>())).containsExactly(dat.toFile());
    }

    @Test
    @DisplayName("does not overflow on a deep directory chain")
    void capsDeepDirectoryChain() throws IOException {
        Path current = Files.createDirectories(tempDir.resolve("deep"));
        final File root = current.toFile();
        for (int i = 0; i < ProfilePanelController.MAX_DAT_SEARCH_DEPTH + 5; i++)
            current = Files.createDirectories(current.resolve("d"));
        Files.writeString(current.resolve("hidden.dat"), "dat", StandardCharsets.UTF_8);

        final var found = new ArrayList<File>();
        assertThatCode(() -> ProfilePanelController.searchDats(root, found)).doesNotThrowAnyException();
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("returns the given list for a null file")
    void nullFileReturnsSameList() {
        final var files = new ArrayList<File>();
        assertThat(ProfilePanelController.searchDats(null, files)).isSameAs(files).isEmpty();
    }
}
