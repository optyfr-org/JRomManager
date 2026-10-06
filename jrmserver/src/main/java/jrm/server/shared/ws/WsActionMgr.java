package jrm.server.shared.ws;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.eclipsesource.json.Json;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jrm.misc.Log;
import jrm.server.shared.WebSession;
import jrm.server.shared.actions.ActionsMgr;
import lombok.Getter;

/**
 * {@link ActionsMgr} implementation that pushes server messages over a Jakarta WebSocket socket.
 * <p>
 * Mirrors {@code LongPollingReqMgr} but writes socket-only: {@link #send(String)} never touches the
 * {@code lprMsg} queue (the queue is paused while WS is open). {@link #sendOptional(String)} has the same
 * socket-only semantics; coalescing lives in {@code ProgressActions} (200ms gate), not here.
 * </p>
 * <p>
 * <b>Live-manager resolution rule:</b> worker threads spawn from request threads holding whatever manager created
 * them (an LPR manager from {@code /actions/cmd}, or a stale manager). Server pushes must reach the live socket,
 * therefore any code resolving the "current manager for session" (workers, {@code ProgressActions} created during WS
 * {@code onMessage}, LPR {@code /actions/cmd} arriving while a WS is open) must prefer the registered
 * {@link WsActionMgr} from {@link #wsCmds} when it {@link #isOpen()}, falling back to the LPR manager otherwise.
 * Use {@link #resolveFor(WebSession, ActionsMgr)} or {@link #forSession(WebSession)} for this lookup.
 * </p>
 * <p>
 * Concurrency: registry is a {@link ConcurrentHashMap}; socket/handle references are {@code volatile} with no
 * {@code synchronized} in the push path (FullServer dispatches on virtual threads; monitor pinning must be avoided).
 * </p>
 */
public class WsActionMgr implements ActionsMgr {

    /**
     * Live socket managers indexed by {@link WebSession} id. Holds at most one entry per session; a second
     * {@code onOpen} replaces the entry and closes the previous socket.
     */
    @Getter
    private static final Map<String, WsActionMgr> wsCmds = new ConcurrentHashMap<>();

    /** The web session associated with this manager. */
    private WebSession session;

    /** The live Jakarta WebSocket session. Volatile for safe publication across threads. */
    private volatile jakarta.websocket.Session wsSession;

    /** The HTTP session captured at handshake, used only to touch {@code lastAccessedTime}. */
    private volatile HttpSession httpSession;

    /**
     * Creates a manager for the given session and socket. Does not auto-register; callers register via
     * {@link #setSession(WebSession)} or a direct {@link #wsCmds} put that captures the replaced entry.
     *
     * @param session the {@link WebSession}, must not be null
     * @param wsSession the Jakarta WebSocket session, may be null in tests
     * @param httpSession the HTTP session captured at handshake, may be null
     * @throws NullPointerException if session is null
     */
    public WsActionMgr(final WebSession session, final jakarta.websocket.Session wsSession, final HttpSession httpSession) {
        if (session == null)
            throw new NullPointerException("Session not found");
        this.session = session;
        this.wsSession = wsSession;
        this.httpSession = httpSession;
    }

    /**
     * Processes an incoming client message, routing through the shared action pipeline with this manager so that
     * resulting worker progress pushes go back over the socket.
     *
     * @param msg the raw JSON message
     */
    public void process(final String msg) {
        Log.debug("processing client message over ws");
        touch();
        processActions(this, Json.parse(msg).asObject());
    }

    @Override
    public void setSession(final WebSession session) {
        if (session == null)
            throw new NullPointerException("Session not found");
        this.session = session;
        wsCmds.put(session.getSessionId(), this);
    }

    @Override
    public void unsetSession(final WebSession session) {
        saveSettings();
        // Identity-safe removal: a replaced socket's onClose must not evict the new entry.
        wsCmds.remove(session.getSessionId(), this);
    }

    @Override
    public void send(final String msg) throws IOException {
        final var s = wsSession;
        if (s == null || !s.isOpen())
            return;
        try {
            s.getAsyncRemote().sendText(msg);
            touch();
        } catch (final IllegalStateException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void sendOptional(final String msg) throws IOException {
        // Socket-only: no queue to coalesce against; ProgressActions already gates best-effort pushes.
        send(msg);
    }

    @Override
    public boolean isOpen() {
        final var s = wsSession;
        return s != null && s.isOpen();
    }

    @Override
    public WebSession getSession() {
        return session;
    }

    /**
     * Returns the underlying Jakarta WebSocket session (for replacement/identity guards).
     *
     * @return the websocket session, may be null in tests
     */
    public jakarta.websocket.Session getWsSession() {
        return wsSession;
    }

    /**
     * Touches the HTTP session so WS-only traffic keeps it alive (WS frames do not update
     * {@code lastAccessedTime}), and refreshes the {@link WebSession} last-action timestamp.
     */
    void touch() {
        final var h = httpSession;
        if (h != null) {
            try {
                h.setAttribute("lastWsAction", Instant.now());
            } catch (final IllegalStateException e) {
                Log.debug("ws http session already invalidated");
            } catch (final Exception e) {
                Log.debug(() -> "ws touch failed: " + e.getMessage());
            }
        }
        final var s = session;
        if (s != null)
            s.setLastAction(Instant.now());
    }

    /**
     * Returns the live manager for the given session, or {@code null} when no WS is registered.
     *
     * @param session the web session
     * @return the registered manager or {@code null}
     */
    public static WsActionMgr forSession(final WebSession session) {
        if (session == null)
            return null;
        return wsCmds.get(session.getSessionId());
    }

    /**
     * Resolves the manager that server pushes should use: the live {@link WsActionMgr} when one is registered and
     * {@link #isOpen()}, otherwise the provided fallback (typically a fresh {@code LongPollingReqMgr}).
     *
     * @param session the web session
     * @param fallback the fallback manager
     * @return the live WS manager or the fallback
     */
    public static ActionsMgr resolveFor(final WebSession session, final ActionsMgr fallback) {
        final var live = forSession(session);
        if (live != null && live.isOpen())
            return live;
        return fallback;
    }

    /**
     * Closes the underlying socket with a normal closure. Best-effort; ignores failures.
     *
     * @param code the close code
     * @param reason the reason phrase
     */
    public void closeSocket(final int code, final String reason) {
        final var s = wsSession;
        if (s != null && s.isOpen()) {
            try {
                s.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason));
            } catch (final Exception e) {
                Log.debug(() -> "ws close failed: " + e.getMessage());
            }
        }
    }

    /**
     * Closes the socket registered for the given session id, if any.
     *
     * @param sessionId the web session id
     */
    public static void closeFor(final String sessionId) {
        if (sessionId == null)
            return;
        final var mgr = wsCmds.get(sessionId);
        if (mgr != null)
            mgr.closeSocket(1000, "session closed");
    }

    /**
     * Closes all open sockets with a normal closure (server going away). Entries are removed by the subsequent
     * {@code onClose} callbacks via the identity guard; settings are persisted separately via
     * {@link #saveAllSettings()}.
     */
    public static void closeAllSockets() {
        wsCmds.forEach((_, mgr) -> mgr.closeSocket(1000, "server going away"));
    }

    private void saveSettings() {
        if (session != null) {
            if (session.getCurrProfile() != null)
                session.getCurrProfile().saveSettings();
            if (session.hasUser())
                session.getUser().getSettings().saveSettings();
            session = null;
            wsSession = null;
            httpSession = null;
        }
    }

    /**
     * Persists settings across all registered WS sessions.
     */
    public static void saveAllSettings() {
        wsCmds.forEach((_, mgr) -> mgr.saveSettings());
    }
}
