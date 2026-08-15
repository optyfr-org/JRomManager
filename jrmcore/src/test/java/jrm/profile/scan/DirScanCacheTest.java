package jrm.profile.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.security.Session;

/**
 * Regression tests for {@link DirScan} cache serialization and deserialization.
 * <p>
 * These tests verify that the ObjectInputFilter allowlist used when loading the cache covers every type that legitimately appears in
 * {@code containersByName}, including nested enum classes such as {@code jrm.profile.data.Entry$Type} and
 * {@code jrm.profile.data.Entity$Status}, so that a second scan can reuse the cache instead of falling back to an empty map.
 * </p>
 */
@DisplayName("DirScan cache tests")
class DirScanCacheTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler handler;
    private CapturingLogHandler logHandler;

    /**
     * Initializes a real server-mode session and a non-cancelling progress handler before each test.
     */
    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("dirscan-cache-test", "JRomManager", new String[] { "admin" });
        handler = mock(ProgressHandler.class, withSettings().stubOnly());
        when(handler.isCancel()).thenReturn(false);
        logHandler = new CapturingLogHandler();
        Logger.getGlobal().addHandler(logHandler);
    }

    /**
     * Clears the work-directory system property and removes the capturing log handler after each test.
     */
    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
        Logger.getGlobal().removeHandler(logHandler);
    }

    /**
     * Verifies that two consecutive scans of the same non-empty directory write a cache and successfully load it on the second run.
     * The second scan must not log an {@link InvalidClassException} caused by the deserialization filter rejecting legitimate types.
     *
     * @throws Exception if the test setup or scan fails
     */
    @Test
    @Timeout(60)
    @DisplayName("second scan should reuse the cache without InvalidClassException")
    void secondScanShouldReuseCacheWithoutInvalidClassException() throws Exception {
        // Arrange: a non-empty destination directory
        final var dstDir = tempDir.resolve("roms");
        Files.createDirectories(dstDir);
        Files.write(dstDir.resolve("rom1.bin"), new byte[] { 0x01, 0x02, 0x03, 0x04 });
        Files.write(dstDir.resolve("rom2.bin"), new byte[] { 0x05, 0x06, 0x07, 0x08 });
        final var dstFile = dstDir.toFile();

        final var options = EnumSet.of(DirScan.Options.IS_DEST);

        // Act: first scan writes the cache
        final var scan1 = new DirScan(session, dstFile, handler, options);
        assertThat(scan1).isNotNull();

        // Assert: a cache file was written
        final var cacheDir = session.getUser().getSettings().getWorkPath().resolve("cache").toFile();
        assertThat(cacheDir).exists();
        final File[] cacheFiles = cacheDir.listFiles();
        assertThat(cacheFiles).isNotNull().anyMatch(File::isFile);

        // Act: second scan of the same directory must load the cache
        logHandler.clear();
        final var scan2 = new DirScan(session, dstFile, handler, options);
        assertThat(scan2).isNotNull();

        // Assert: no InvalidClassException was logged
        assertThat(logHandler.getRecords())
                .noneSatisfy(logRecord -> {
                    final Throwable thrown = logRecord.getThrown();
                    assertThat(thrown)
                            .as("log record should not carry an InvalidClassException")
                            .satisfiesAnyOf(
                                    t -> assertThat(t).isNull(),
                                    t -> assertThat(t).isNotInstanceOf(InvalidClassException.class));
                });
    }

    /**
     * Simple log handler that records all published {@link LogRecord}s for later assertions.
     */
    private static final class CapturingLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            records.add(logRecord);
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() throws SecurityException {
            // no-op
        }

        List<LogRecord> getRecords() {
            return List.copyOf(records);
        }

        void clear() {
            records.clear();
        }
    }
}
