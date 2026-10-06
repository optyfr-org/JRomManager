package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("single-user server session (unrestricted)")
    class SingleUserServerTest {
        private Session singleUserSession;

        @BeforeEach
        void setUpSingleUser() {
            singleUserSession = new Session("path-singleuser");
            singleUserSession.setUser("JRomManager", new String[] { "admin" });
        }

        @Test
        @DisplayName("isUnrestricted only for single-user server sessions")
        void isUnrestrictedFlag() {
            assertThat(PathAbstractor.isUnrestricted(singleUserSession)).isTrue();
            assertThat(PathAbstractor.isUnrestricted(adminSession)).isFalse();
            assertThat(PathAbstractor.isUnrestricted(userSession)).isFalse();
            assertThat(PathAbstractor.isUnrestricted(null)).isFalse();
        }

        @Test
        @DisplayName("every path is writeable")
        void everythingWriteable() {
            assertThat(PathAbstractor.isWriteable(singleUserSession, "C:/anywhere/roms")).isTrue();
            assertThat(PathAbstractor.isWriteable(singleUserSession, "%shared/roms")).isTrue();
            assertThat(PathAbstractor.isWriteable(singleUserSession, Path.of("/anywhere/roms"))).isTrue();
            assertThatCode(() -> PathAbstractor.getWritableAbsolutePath(singleUserSession, "C:/anywhere/roms.dat"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("absolute paths outside workspace resolve without sandbox checks")
        void absolutePathsUnrestricted() {
            final Path outside = tempDir.resolve("elsewhere").resolve("out.dat").toAbsolutePath().normalize();
            assertThat(PathAbstractor.getAbsolutePath(singleUserSession, outside.toString())).isEqualTo(outside);
        }

        @Test
        @DisplayName("placeholder forgery checks still apply")
        void placeholderForgeryStillRejected() {
            assertThatThrownBy(() -> PathAbstractor.getAbsolutePath(singleUserSession, "%work/../../outside.dat"))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> PathAbstractor.getAbsolutePath(singleUserSession, "%shared/../../outside.dat"))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("multi-user sessions still reject sandbox escapes")
        void multiUserStillSandboxed() {
            // Must sit outside every allowed root (jrommanager.dir, java.io.tmpdir, user.dir);
            // a sibling of user.dir qualifies on any OS since the repo is never inside tmpdir.
            final Path outside = Path.of(System.getProperty("user.dir")).resolveSibling("jrm-sandbox-escape-probe")
                    .resolve("out.dat").toAbsolutePath().normalize();
            assertThatThrownBy(() -> PathAbstractor.getAbsolutePath(adminSession, outside.toString()))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
