package jrm.profile.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.data.Archive;
import jrm.profile.data.Entry;
import jrm.security.Session;

/**
 * Regression: {@code DirScan.getPath} must return a path whose zip filesystem is still open.
 */
@DisplayName("DirScan getPath filesystem lifetime")
class DirScanGetPathTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("dirscan-getpath-test", "JRomManager", new String[] { "admin" });
        handler = mock(ProgressHandler.class, withSettings().stubOnly());
        when(handler.isCancel()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("getPath returns a usable zip path that callers can close")
    void getPathReturnsUsableZipPath() throws Exception {
        final var romsDir = tempDir.resolve("roms");
        Files.createDirectories(romsDir);
        final var zipPath = romsDir.resolve("game.zip");
        final byte[] payload = { 0x01, 0x02, 0x03, 0x04 };
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("rom.bin"));
            zos.write(payload);
            zos.closeEntry();
        }

        final var scan = new DirScan(session, romsDir.toFile(), handler, EnumSet.of(DirScan.Options.IS_DEST));
        final var attrs = Files.readAttributes(zipPath, BasicFileAttributes.class);
        final var archive = new Archive(zipPath.toFile(), zipPath.toFile(), attrs);
        final var entry = archive.add(new Entry("rom.bin", "rom.bin"));

        final Method getPath = DirScan.class.getDeclaredMethod("getPath", Entry.class);
        getPath.setAccessible(true);
        final Path path = (Path) getPath.invoke(scan, entry);
        try {
            assertThat(path.getFileSystem().isOpen()).isTrue();
            assertThat(Files.readAllBytes(path)).isEqualTo(payload);
        } finally {
            path.getFileSystem().close();
        }
        assertThat(path.getFileSystem().isOpen()).isFalse();
    }
}
