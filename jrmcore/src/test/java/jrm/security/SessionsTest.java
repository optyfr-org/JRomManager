package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sessions mode guards")
class SessionsTest {

    @AfterEach
    void tearDown() {
        Sessions.setSingleMode(false);
        Sessions.setSingleSession(null);
    }

    @Test
    @DisplayName("getSession(boolean, boolean) requires single-session mode")
    void desktopSessionRequiresSingleMode() {
        Sessions.setSingleMode(false);
        assertThatThrownBy(() -> Sessions.getSession(false, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getSession(boolean, boolean) creates the shared desktop session")
    void desktopSessionCreatesOnce() {
        Sessions.setSingleMode(true);
        final Session first = Sessions.getSession(false, true);
        final Session second = Sessions.getSession(true, false);
        assertThat(first).isNotNull().isSameAs(second);
        assertThat(first.hasUser()).isTrue();
    }

    @Test
    @DisplayName("getSession(String) requires multi-session mode")
    void lookupRequiresMultiMode() {
        Sessions.setSingleMode(true);
        assertThatThrownBy(() -> Sessions.getSession("any")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("setSession(String) requires multi-session mode")
    void registerRequiresMultiMode() {
        Sessions.setSingleMode(true);
        assertThatThrownBy(() -> Sessions.setSession("any")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("setSession registers a session that getSession can look up")
    void registerThenLookup() {
        Sessions.setSingleMode(false);
        final String id = "sessions-test-" + System.nanoTime();
        assertThat(Sessions.getSession(id)).isNull();
        Sessions.setSession(id);
        assertThat(Sessions.getSession(id)).isNotNull();
        assertThat(Sessions.getSession(id).getSessionId()).isEqualTo(id);
    }
}
