package jrm.server.shared;

import java.nio.file.Path;
import jrm.fullserver.security.Login;
import jrm.server.shared.lpr.LongPollingReqMgr;

/**
 * Shared test fixtures for building {@link WebSession} instances and resetting static global state between tests.
 *
 * <p>
 * Several classes in the {@code jrmserver} subproject hold static mutable state (session registries, caches, termination flags).
 * Tests that touch this state must call {@link #resetStaticState()} in an {@code @AfterEach} hook to keep the suite hermetic.
 * </p>
 */
public final class TestWebSessions {

    /** Private constructor — utility class. */
    private TestWebSessions() {
    }

    /**
     * Builds a real single-user {@link WebSession} with administrative privileges. The session is registered in the global
     * {@code allSessions} map; callers should reset static state after use.
     *
     * @param id the session identifier
     * @return a new admin {@link WebSession}
     */
    public static WebSession newAdminSession(final String id) {
        return new WebSession(id, "admin", new String[] { "admin" });
    }

    /**
     * Builds a real multi-user {@link WebSession} with the given user and roles.
     *
     * @param id the session identifier
     * @param user the username
     * @param roles the roles array
     * @return a new {@link WebSession}
     */
    public static WebSession newSession(final String id, final String user, final String[] roles) {
        return new WebSession(id, user, roles);
    }

    /**
     * Clears all static global state touched by the {@code jrmserver} tests:
     * <ul>
     * <li>{@link WebSession#getTerminate() terminate} flag reset to {@code false}</li>
     * <li>{@code WebSession.allSessions} map cleared</li>
     * <li>{@code LongPollingReqMgr.cmds} map cleared</li>
     * <li>{@code Login.cache} map cleared and {@code Login.cachetime} reset</li>
     * </ul>
     * Call this from {@code @AfterEach} of any test that constructs a {@link WebSession}, {@link LongPollingReqMgr}, or
     * {@link Login}.
     */
    public static void resetStaticState() {
        WebSession.setTerminate(false);
        WebSession.getAllSessions().clear();
        LongPollingReqMgr.getCmds().clear();
        jrm.server.shared.ws.WsActionMgr.getWsCmds().clear();
        Login.getCache().clear();
        Login.setCachetime(System.currentTimeMillis());
    }

    /**
     * Sets the {@code jrommanager.dir} system property to the given work path, so that {@code AbstractServer.getWorkPath()} and
     * {@link PathAbstractor} resolve relative paths against it during tests.
     *
     * @param workPath the work path to set
     */
    public static void setWorkPath(final Path workPath) {
        System.setProperty("jrommanager.dir", workPath.toString());
    }
}