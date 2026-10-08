package jrm.server.shared.ws;

import java.time.Instant;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import jrm.misc.Log;
import jrm.server.shared.WebSession;

/**
 * Jakarta WebSocket endpoint for the optional actions channel ({@code /ws}).
 * <p>
 * Inbound client JSON commands are routed through the same {@code processActions} pipeline as LPR, with the
 * {@link WsActionMgr} as the push manager so responses and worker progress go back over this socket. Initial seeding
 * reuses the shared {@link ActionInit#init} helper (same payloads as {@code GET /actions/init}), so the WS-mode client
 * never calls {@code /actions/init}.
 * </p>
 * <p>
 * Single-socket policy: the {@link WsActionMgr#wsCmds} registry holds at most one manager per session id; a second
 * {@code onOpen} closes the previous socket (normal closure) and replaces the entry. Two browser tabs sharing one
 * {@code JSESSIONID} therefore converge on the newest tab; the replaced tab falls back to LPR with re-init.
 * </p>
 */
@ServerEndpoint(value = "/ws", configurator = ActionSocket.Configurator.class)
public class ActionSocket {

    /** Captures the HTTP session from the upgrade handshake for auth + keep-alive touches. */
    public static class Configurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(final ServerEndpointConfig config, final HandshakeRequest request, final jakarta.websocket.HandshakeResponse response) {
            final var httpSession = (HttpSession) request.getHttpSession();
            if (httpSession != null)
                config.getUserProperties().put(HttpSession.class.getName(), httpSession);
            super.modifyHandshake(config, request, response);
        }
    }

    @OnOpen
    public void onOpen(final Session wsSession, final EndpointConfig config) {
        final var httpSession = config != null ? (HttpSession) config.getUserProperties().get(HttpSession.class.getName()) : null;
        final WebSession webSession = httpSession != null ? (WebSession) httpSession.getAttribute("session") : null;
        if (webSession == null || !webSession.hasUser()) {
            closeSilently(wsSession, 4401, "unauthorized");
            return;
        }
        webSession.setLastAction(Instant.now());
        if (httpSession != null) {
            try {
                // setAttribute does not refresh lastAccessedTime; this no-op write does.
                httpSession.setMaxInactiveInterval(httpSession.getMaxInactiveInterval());
            } catch (final Exception e) {
                Log.debug(() -> "ws open touch failed: " + e.getMessage());
            }
        }
        final var mgr = new WsActionMgr(webSession, wsSession, httpSession);
        // Single-socket policy: replace + close previous.
        final var prev = WsActionMgr.getWsCmds().put(webSession.getSessionId(), mgr);
        if (prev != null && prev != mgr) {
            prev.unsetSession(webSession);
            prev.closeSocket(1000, "replaced by new socket");
        }
        try {
            ActionInit.init(mgr, webSession);
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

    @OnMessage
    public void onMessage(final String message, final Session wsSession) {
        if (message == null)
            return;
        final var mgr = lookupFor(wsSession);
        if (mgr == null)
            return;
        final var session = mgr.getSession();
        if (session == null || !session.hasUser()) {
            closeSilently(wsSession, 4401, "unauthorized");
            return;
        }
        mgr.process(message);
    }

    @OnClose
    public void onClose(final Session wsSession, final CloseReason reason) {
        final var mgr = lookupFor(wsSession);
        if (mgr == null)
            return;
        final var session = mgr.getSession();
        if (session != null)
            mgr.unsetSession(session);
    }

    @OnError
    public void onError(final Session wsSession, final Throwable error) {
        if (error != null)
            Log.debug(() -> "ws error: " + error.getMessage());
        // Let onClose perform deregistration; force-close a broken socket here.
        if (wsSession != null && wsSession.isOpen())
            closeSilently(wsSession, 1011, "endpoint error");
    }

    /**
     * Finds the registered manager owning the given socket (identity match), so a stale {@code onClose} from a
     * replaced socket never resolves to the new entry.
     */
    private static WsActionMgr lookupFor(final Session wsSession) {
        if (wsSession == null)
            return null;
        for (final var mgr : WsActionMgr.getWsCmds().values()) {
            if (mgr.getWsSession() == wsSession)
                return mgr;
        }
        return null;
    }

    private static void closeSilently(final Session wsSession, final int code, final String reason) {
        if (wsSession == null || !wsSession.isOpen())
            return;
        try {
            wsSession.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason));
        } catch (final Exception e) {
            Log.debug(() -> "ws close failed: " + e.getMessage());
        }
    }
}
