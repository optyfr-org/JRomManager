package jrm.profile.scan;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.scan.DirScan.Options;
import jrm.security.Session;

/**
 * Regression: server-mode Dir2Dat must refuse destinations outside the write sandbox.
 */
@DisplayName("Dir2Dat path validation")
class Dir2DatPathValidationTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session serverSession;
    private ProgressHandler progress;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("admin"));
        Files.createDirectories(tempDir.resolve("src"));
        serverSession = new Session("dir2dat-path", "admin", new String[] { "admin" });
        progress = mock(ProgressHandler.class);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("server rejects destination outside base path")
    void serverRejectsOutsideDestination() {
        final File src = tempDir.resolve("src").toFile();
        final File outside = tempDir.resolve("..").resolve("evil.dat").toAbsolutePath().normalize().toFile();

        assertThatThrownBy(() -> new Dir2Dat(serverSession, src, outside, progress,
                EnumSet.of(Options.USE_PARALLELISM), ExportType.MAME, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes workspace");
    }

    @Test
    @DisplayName("desktop session allows arbitrary destination path")
    void desktopAllowsArbitraryDestination() throws Exception {
        final Session desktop = new Session(false, true);
        final Path desktopSrc = Files.createTempDirectory("dir2dat-desktop-src");
        final Path desktopDst = Files.createTempFile("dir2dat-desktop", ".dat");
        try {
            assertThatCode(() -> new Dir2Dat(desktop, desktopSrc.toFile(), desktopDst.toFile(), progress,
                    EnumSet.of(Options.USE_PARALLELISM), ExportType.MAME, Map.of()))
                    .doesNotThrowAnyException();
        } finally {
            Files.deleteIfExists(desktopDst);
            // best-effort cleanup of empty temp dir
            try {
                Files.walk(desktopSrc).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception _) {
                        // ignore
                    }
                });
            } catch (Exception _) {
                // ignore
            }
        }
    }
}
