package jrm.server.shared.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;
import jrm.server.shared.actions.ActionsMgr;

/**
 * Equivalence test: WS-open init emits the same messages as LPR {@code ActionServlet.doInit} (both delegate to
 * {@link ActionInit#init}).
 */
@DisplayName("ActionInit")
class ActionInitTest {

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Test
    @DisplayName("init helper emits identical payloads for LPR and WS managers")
    void equivalence() {
        final WebSession sess = TestWebSessions.newAdminSession("init-equiv");
        final var lprMsgs = new ArrayList<String>();
        final ActionsMgr lprLike = recordingMgr(sess, lprMsgs);
        final var wsMsgs = new ArrayList<String>();
        final ActionsMgr wsLike = recordingMgr(sess, wsMsgs);
        ActionInit.init(lprLike, sess);
        ActionInit.init(wsLike, sess);
        assertThat(wsMsgs).isEqualTo(lprMsgs);
    }

    private static ActionsMgr recordingMgr(final WebSession sess, final List<String> out) {
        return new ActionsMgr() {
            @Override
            public void send(final String msg) {
                out.add(msg);
            }

            @Override
            public void sendOptional(final String msg) throws IOException {
                out.add(msg);
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public WebSession getSession() {
                return sess;
            }

            @Override
            public void setSession(final WebSession session) {
                // no-op
            }

            @Override
            public void unsetSession(final WebSession session) {
                // no-op
            }
        };
    }
}
