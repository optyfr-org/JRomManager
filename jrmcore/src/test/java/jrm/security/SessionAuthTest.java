package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for session user binding: server sessions must not auto-elevate to admin.
 */
@DisplayName("Session authentication binding")
class SessionAuthTest {

    @Test
    @DisplayName("server session without user does not invent admin identity")
    void serverSessionNoLazyAdmin() {
        final Session session = new Session("server-unauth");
        assertThat(session.hasUser()).isFalse();
        assertThatThrownBy(session::getUser).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("multi-user constructor with null user stays unauthenticated")
    void multiUserNullStaysUnauthenticated() {
        final Session session = new Session("multi-unauth", null, null);
        assertThat(session.isMultiuser()).isTrue();
        assertThat(session.hasUser()).isFalse();
        assertThatThrownBy(session::getUser).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("setUser attaches authenticated identity")
    void setUserAttaches() {
        final Session session = new Session("server-login");
        session.setUser("alice", new String[] { "user" });
        assertThat(session.hasUser()).isTrue();
        assertThat(session.getUser().getName()).isEqualTo("alice");
        assertThat(session.getUser().isAdmin()).isFalse();
    }

    @Test
    @DisplayName("desktop session still has lazy local admin")
    void desktopSessionLazyAdmin() {
        final Session session = new Session(false, true);
        assertThat(session.hasUser()).isTrue();
        assertThat(session.getUser().getName()).isEqualTo("JRomManager");
        assertThat(session.getUser().isAdmin()).isTrue();
    }
}
