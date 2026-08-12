package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Write-authorization tests for {@link PathAbstractor}: non-admins must not write under {@code %shared}.
 */
@DisplayName("PathAbstractor write access")
class PathAbstractorWriteAccessTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session adminSession;
    private Session userSession;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("shared"));
        Files.createDirectories(tempDir.resolve("users").resolve("admin"));
        Files.createDirectories(tempDir.resolve("users").resolve("user"));
        adminSession = new Session("path-write-admin", "admin", new String[] { "admin" });
        userSession = new Session("path-write-user", "user", new String[] { "user" });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("non-admin cannot write %shared abstract path")
    void nonAdminCannotWriteSharedAbstract() {
        assertThat(PathAbstractor.isWriteable(userSession, "%shared")).isFalse();
        assertThat(PathAbstractor.isWriteable(userSession, "%shared/roms")).isFalse();
        assertThatThrownBy(() -> PathAbstractor.requireWriteable(userSession, "%shared/roms"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> PathAbstractor.getWritableAbsolutePath(userSession, "%shared/roms"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("admin can write %shared abstract path")
    void adminCanWriteSharedAbstract() {
        assertThat(PathAbstractor.isWriteable(adminSession, "%shared")).isTrue();
        assertThat(PathAbstractor.isWriteable(adminSession, "%shared/roms")).isTrue();
        assertThatCode(() -> PathAbstractor.getWritableAbsolutePath(adminSession, "%shared/roms")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("any user can write %work abstract path")
    void anyUserCanWriteWork() {
        assertThat(PathAbstractor.isWriteable(userSession, "%work")).isTrue();
        assertThat(PathAbstractor.isWriteable(userSession, "%work/roms")).isTrue();
        assertThatCode(() -> PathAbstractor.getWritableAbsolutePath(userSession, "%work/roms")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("non-admin cannot write absolute path under shared root")
    void nonAdminCannotWriteAbsoluteShared() {
        final Path sharedChild = tempDir.resolve("users").resolve("shared").resolve("victim").toAbsolutePath().normalize();
        assertThat(PathAbstractor.isWriteable(userSession, sharedChild)).isFalse();
        assertThatThrownBy(() -> PathAbstractor.requireWriteable(userSession, sharedChild)).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("getAbsolutePath still resolves %shared for read (non-admin)")
    void getAbsolutePathStillResolvesSharedForRead() {
        final Path resolved = PathAbstractor.getAbsolutePath(userSession, "%shared/roms");
        assertThat(resolved.toString().replace('\\', '/')).contains("users/shared");
    }
}
