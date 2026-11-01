package jrm.server.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link WebSession}.
 */
@DisplayName("WebSession")
class WebSessionTest {

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("close")
    class CloseTest {
        @Test
        @DisplayName("close adds sentinel to lprMsg when empty and removes from allSessions")
        void closeAddsSentinel() {
            final WebSession session = TestWebSessions.newAdminSession("close-test");
            assertThat(session.getLongPollingMessages()).isEmpty();
            session.close();
            assertThat(session.getLongPollingMessages()).contains("");
        }

        @Test
        @DisplayName("close does not add sentinel when lprMsg already has messages")
        void closeNonEmptyLprMsg() {
            final WebSession session = TestWebSessions.newAdminSession("close-nonempty-test");
            session.getLongPollingMessages().add("existing");
            session.close();
            assertThat(session.getLongPollingMessages()).containsExactly("existing");
        }
    }

    @Nested
    @DisplayName("closeAll")
    class CloseAllTest {
        @Test
        @DisplayName("closeAll sets terminate flag and closes all sessions")
        void closeAllSetsTerminate() {
            TestWebSessions.newAdminSession("closeall-1");
            TestWebSessions.newAdminSession("closeall-2");
            WebSession.closeAll();
            assertThat(WebSession.isTerminate()).isTrue();
        }
    }

    @Nested
    @DisplayName("profile list cache")
    class ProfileListTest {
        @Test
        @DisplayName("putProfileList before newProfileList throws NPE")
        void putBeforeNewThrowsNPE(@TempDir final Path tempDir) {
            final WebSession session = TestWebSessions.newAdminSession("profile-npe-test");
            assertThatThrownBy(() -> session.putProfileList(1, tempDir)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("newProfileList + putProfileList + getProfileList round-trip")
        void profileListRoundTrip(@TempDir final Path tempDir) {
            final WebSession session = TestWebSessions.newAdminSession("profile-rt-test");
            session.newProfileList();
            session.putProfileList(1, tempDir);
            assertThat(session.getProfileList(1)).isEqualTo(tempDir);
            assertThat(session.getLastProfileListKey()).isEqualTo(1);
        }

        @Test
        @DisplayName("getLastProfileListKey returns 0 when cache empty")
        void lastKeyEmpty() {
            final WebSession session = TestWebSessions.newAdminSession("profile-empty-test");
            session.newProfileList();
            assertThat(session.getLastProfileListKey()).isZero();
        }

        @Test
        @DisplayName("removeProfileList removes entry")
        void removeProfileList(@TempDir final Path tempDir) {
            final WebSession session = TestWebSessions.newAdminSession("profile-remove-test");
            session.newProfileList();
            session.putProfileList(1, tempDir);
            session.removeProfileList(1);
            assertThat(session.getProfileList(1)).isNull();
        }
    }
}