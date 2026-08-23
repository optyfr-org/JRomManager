package jrm.profile.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.profile.data.Archive;
import jrm.profile.data.Entry;

/**
 * Regression: {@code OwnedPath} must keep the zip filesystem open until closed.
 */
@DisplayName("DirScan OwnedPath filesystem lifetime")
class DirScanGetPathTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("OwnedPath returns a usable zip path closed by try-with-resources")
    void ownedPathReturnsUsableZipPath() throws Exception {
        final var zipPath = tempDir.resolve("game.zip");
        final byte[] payload = { 0x01, 0x02, 0x03, 0x04 };
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("rom.bin"));
            zos.write(payload);
            zos.closeEntry();
        }

        final var attrs = Files.readAttributes(zipPath, BasicFileAttributes.class);
        final var archive = new Archive(zipPath.toFile(), zipPath.toFile(), attrs);
        final var entry = archive.add(new Entry("rom.bin", "rom.bin"));

        final Class<?> ownedPathClass = Class.forName("jrm.profile.scan.OwnedPath");
        final Method of = ownedPathClass.getDeclaredMethod("of", Entry.class, Path.class);
        of.setAccessible(true);
        final Method pathMethod = ownedPathClass.getDeclaredMethod("path");
        pathMethod.setAccessible(true);

        final Path path;
        try (var owned = (AutoCloseable) of.invoke(null, entry, null)) {
            path = (Path) pathMethod.invoke(owned);
            assertThat(path.getFileSystem().isOpen()).isTrue();
            assertThat(Files.readAllBytes(path)).isEqualTo(payload);
        }
        assertThat(path.getFileSystem().isOpen()).isFalse();
    }
}
