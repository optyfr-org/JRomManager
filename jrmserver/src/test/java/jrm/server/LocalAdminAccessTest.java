package jrm.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link LocalAdminAccess}.
 */
@DisplayName("LocalAdminAccess")
class LocalAdminAccessTest {

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("isLoopback")
    class IsLoopbackTest {
        @Test
        @DisplayName("accepts IPv4 and IPv6 loopback")
        void acceptsLoopback() {
            assertThat(LocalAdminAccess.isLoopback(requestWithRemote("127.0.0.1"))).isTrue();
            assertThat(LocalAdminAccess.isLoopback(requestWithRemote("::1"))).isTrue();
        }

        @Test
        @DisplayName("rejects non-loopback and null")
        void rejectsRemote() {
            assertThat(LocalAdminAccess.isLoopback(requestWithRemote("192.168.1.10"))).isFalse();
            assertThat(LocalAdminAccess.isLoopback(requestWithRemote("10.0.0.5"))).isFalse();
            assertThat(LocalAdminAccess.isLoopback(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("ensureLocalAdmin")
    class EnsureLocalAdminTest {
        @Test
        @DisplayName("elevates unauthenticated session for loopback peer")
        void elevatesLoopback() {
            final WebSession ws = new WebSession("local-elevate");
            assertThat(ws.hasUser()).isFalse();
            final HttpServletRequest req = requestWithSession(ws, "127.0.0.1");

            final WebSession result = LocalAdminAccess.ensureLocalAdmin(req);

            assertThat(result).isSameAs(ws);
            assertThat(ws.hasUser()).isTrue();
            assertThat(ws.getUser().getName()).isEqualTo("JRomManager");
            assertThat(ws.getUser().isAdmin()).isTrue();
        }

        @Test
        @DisplayName("does not elevate for remote peer")
        void noElevateRemote() {
            final WebSession ws = new WebSession("remote-no-elevate");
            final HttpServletRequest req = requestWithSession(ws, "203.0.113.50");

            LocalAdminAccess.ensureLocalAdmin(req);

            assertThat(ws.hasUser()).isFalse();
        }

        @Test
        @DisplayName("leaves already authenticated session unchanged")
        void leavesAuthenticated() {
            final WebSession ws = TestWebSessions.newSession("already-auth", "alice", new String[] { "user" });
            final HttpServletRequest req = requestWithSession(ws, "127.0.0.1");

            LocalAdminAccess.ensureLocalAdmin(req);

            assertThat(ws.getUser().getName()).isEqualTo("alice");
            assertThat(ws.getUser().isAdmin()).isFalse();
        }
    }

    private static HttpServletRequest requestWithRemote(final String remoteAddr) {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }

    private static HttpServletRequest requestWithSession(final WebSession ws, final String remoteAddr) {
        final HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("session")).thenReturn(ws);
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getSession(true)).thenReturn(httpSession);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }
}
