package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CachePathGuard")
class CachePathGuardTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("cache-path-guard-test", "JRomManager", new String[] { "admin" });
    }

    @AfterEach
    void tearDown() {
        CacheIntegrityKey.clearCache();
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("protects cache, nfo, results, and hmac key filenames")
    void protectsCacheFilenames() {
        assertThat(CachePathGuard.isProtectedFilename("mame.dat.cache")).isTrue();
        assertThat(CachePathGuard.isProtectedFilename("mame.dat.nfo")).isTrue();
        assertThat(CachePathGuard.isProtectedFilename("deadbeef.results")).isTrue();
        assertThat(CachePathGuard.isProtectedFilename(CacheIntegrityKey.FILENAME)).isTrue();
        assertThat(CachePathGuard.isProtectedFilename("mame.dat")).isFalse();
        assertThat(CachePathGuard.isProtectedFilename("notes.txt")).isFalse();
    }

    @Test
    @DisplayName("protects work cache, reports, work, and settings directories")
    void protectsWorkSubdirectories() {
        final var work = session.getUser().getSettings().getWorkPath();
        assertThat(CachePathGuard.isProtectedLocation(session, work.resolve("cache"))).isTrue();
        assertThat(CachePathGuard.isProtectedLocation(session, work.resolve("reports").resolve("a"))).isTrue();
        assertThat(CachePathGuard.isProtectedLocation(session, work.resolve("work"))).isTrue();
        assertThat(CachePathGuard.isProtectedLocation(session, work.resolve("settings"))).isTrue();
        assertThat(CachePathGuard.isProtectedLocation(session, work.resolve("xmlfiles"))).isFalse();
        assertThat(CachePathGuard.isProtectedLocation(session, work.resolve("reports-backup"))).isFalse();
    }

    @Test
    @DisplayName("protects hmac key file under settings")
    void protectsHmacKeyFile() {
        final var keyFile = CacheIntegrityKey.keyFile(session);
        assertThat(CachePathGuard.isProtectedFile(session, keyFile)).isTrue();
        assertThat(CachePathGuard.isProtectedTarget(session, keyFile.getParent(), CacheIntegrityKey.FILENAME)).isTrue();
    }
}
