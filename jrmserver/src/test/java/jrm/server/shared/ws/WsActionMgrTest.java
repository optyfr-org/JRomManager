package jrm.server.shared.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.RemoteEndpoint;
import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;
import jrm.server.shared.lpr.LongPollingReqMgr;

/**
 * Unit tests for {@link WsActionMgr}.
 */
@DisplayName("WsActionMgr")
class WsActionMgrTest {

    private WebSession webSession;
    private jakarta.websocket.Session wsSession;
    private RemoteEndpoint.Async async;
    private WsActionMgr mgr;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        webSession = TestWebSessions.newAdminSession("ws-test");
        wsSession = mock(jakarta.websocket.Session.class);
        when(wsSession.isOpen()).thenReturn(true);
        async = mock(RemoteEndpoint.Async.class);
        when(wsSession.getAsyncRemote()).thenReturn(async);
        mgr = new WsActionMgr(webSession, wsSession, mock(HttpSession.class));
        mgr.setSession(webSession);
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("send / sendOptional")
    class SendTest {
        @Test
        @DisplayName("send writes socket-only, never the lpr queue")
        void sendSocketOnly() throws IOException {
            mgr.send("{\"cmd\":\"test\"}");
            assertThat(webSession.getLprMsg()).isEmpty();
        }

        @Test
        @DisplayName("send skips silently when socket closed")
        void sendSkipsWhenClosed() {
            when(wsSession.isOpen()).thenReturn(false);
            assertThatCode(() -> mgr.send("{\"cmd\":\"test\"}")).doesNotThrowAnyException();
            assertThat(webSession.getLprMsg()).isEmpty();
        }

        @Test
        @DisplayName("sendOptional mirrors send (socket-only)")
        void sendOptionalSocketOnly() throws IOException {
            mgr.sendOptional("{\"cmd\":\"test\"}");
            assertThat(webSession.getLprMsg()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isOpen")
    class IsOpenTest {
        @Test
        @DisplayName("tracks the socket state")
        void tracksSocket() {
            assertThat(mgr.isOpen()).isTrue();
            when(wsSession.isOpen()).thenReturn(false);
            assertThat(mgr.isOpen()).isFalse();
        }

        @Test
        @DisplayName("null socket is closed")
        void nullSocketClosed() {
            assertThat(new WsActionMgr(TestWebSessions.newAdminSession("ws-null"), null, null).isOpen()).isFalse();
        }
    }

    @Nested
    @DisplayName("setSession / getSession")
    class SessionTest {
        @Test
        @DisplayName("returns the associated session")
        void returnsSession() {
            assertThat(mgr.getSession()).isSameAs(webSession);
        }

        @Test
        @DisplayName("null session throws NullPointerException")
        void nullSessionThrows() {
            assertThatThrownBy(() -> mgr.setSession(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new WsActionMgr(null, wsSession, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("process")
    class ProcessTest {
        @Test
        @DisplayName("processes Global.ping without reply")
        void pingNoReply() {
            mgr.process("{\"cmd\":\"Global.ping\"}");
            assertThat(webSession.getLprMsg()).isEmpty();
            assertThat(webSession.getLastAction()).isNotNull();
        }

        @Test
        @DisplayName("unknown command does not throw")
        void unknownCommand() {
            assertThatCode(() -> mgr.process("{\"cmd\":\"Unknown.command\"}")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("resolveFor")
    class ResolveTest {
        @Test
        @DisplayName("prefers the live WS manager when open")
        void prefersLive() {
            final var fallback = new LongPollingReqMgr(webSession);
            assertThat(WsActionMgr.resolveFor(webSession, fallback)).isSameAs(mgr);
        }

        @Test
        @DisplayName("falls back to LPR when socket closed")
        void fallsBackWhenClosed() {
            when(wsSession.isOpen()).thenReturn(false);
            final var fallback = new LongPollingReqMgr(webSession);
            assertThat(WsActionMgr.resolveFor(webSession, fallback)).isSameAs(fallback);
        }
    }

    @Nested
    @DisplayName("unsetSession identity guard")
    class UnsetTest {
        @Test
        @DisplayName("closing a replaced socket does not evict the new entry")
        void staleCloseKeepsNew() {
            final WebSession sess = TestWebSessions.newAdminSession("ws-replace");
            final var oldSocket = mock(jakarta.websocket.Session.class);
            when(oldSocket.isOpen()).thenReturn(false);
            final var oldMgr = new WsActionMgr(sess, oldSocket, null);
            WsActionMgr.getWsCmds().put(sess.getSessionId(), oldMgr);
            final var newSocket = mock(jakarta.websocket.Session.class);
            when(newSocket.isOpen()).thenReturn(true);
            final var newMgr = new WsActionMgr(sess, newSocket, null);
            WsActionMgr.getWsCmds().put(sess.getSessionId(), newMgr);
            // Stale onClose for the replaced socket:
            oldMgr.unsetSession(sess);
            assertThat(WsActionMgr.getWsCmds().get(sess.getSessionId())).isSameAs(newMgr);
        }
    }
}
