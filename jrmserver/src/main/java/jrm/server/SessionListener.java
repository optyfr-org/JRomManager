package jrm.server;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import jrm.misc.Log;
import jrm.server.shared.WebSession;
import lombok.RequiredArgsConstructor;

/**
 * Listener for HTTP session lifecycle events within the JRomManager web server. This class implements {@link HttpSessionListener}
 * and is responsible for creating and destroying {@link WebSession} instances in response to servlet container session events.
 * <p>
 * When a new HTTP session is created, this listener instantiates a corresponding {@link WebSession} and stores it as a session
 * attribute under the key {@code "session"}. When the session is destroyed (either due to timeout or explicit invalidation), the
 * listener retrieves the associated {@link WebSession} and closes it to release resources.
 * </p>
 * <p>
 * The listener supports two operational modes controlled by the {@link #multi} flag:
 * </p>
 * <ul>
 * <li><b>Single-session mode ({@code multi = false}):</b> Each HTTP session gets its own isolated {@link WebSession} instance with
 * a unique session identifier. This is the recommended mode for production environments to ensure proper session isolation and
 * security.</li>
 * <li><b>Multi-session mode ({@code multi = true}):</b> All HTTP sessions share a common {@link WebSession} instance, allowing for
 * shared state across sessions. This mode is primarily intended for testing purposes or specialized scenarios where shared state is
 * desired.</li>
 * </ul>
 *
 * @since 1.0
 * 
 * @see WebSession
 * @see HttpSessionListener
 */
@RequiredArgsConstructor
public class SessionListener implements HttpSessionListener {
    /**
     * Flag indicating whether to operate in multi-session mode.
     * <p>
     * When {@code true}, all HTTP sessions share the same {@link WebSession} instance, allowing for shared state across sessions.
     * When {@code false}, each HTTP session gets its own isolated {@link WebSession} instance.
     * </p>
     * <p>
     * In a production environment, it is generally recommended to use single-session mode ({@code multi = false}) to ensure proper
     * session management and security. Multi-session mode is primarily intended for testing purposes or scenarios where shared
     * state is desired across multiple sessions.
     * </p>
     *
     * @see WebSession
     */
    final boolean multi;

    /**
     * Called by the servlet container when an HTTP session is about to be destroyed. This method retrieves the {@link WebSession}
     * associated with the session and closes it to release any held resources.
     * <p>
     * The method performs the following steps:
     * </p>
     * <ol>
     * <li>Logs a debug message indicating that the session is being destroyed.</li>
     * <li>Retrieves the {@link WebSession} instance from the session attributes using the key {@code "session"}.</li>
     * <li>If a {@link WebSession} exists, invokes its {@link WebSession#close()} method to clean up resources and mark the session
     * as terminated.</li>
     * </ol>
     *
     * @param se the {@link HttpSessionEvent} containing information about the session that is being destroyed
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        Log.debug(() -> "Destroying session " + se.getSession().getId());
        WebSession ws = (WebSession) se.getSession().getAttribute("session");
        if (ws != null)
            ws.close();
    }

    /**
     * Called by the servlet container when a new HTTP session is created. This method instantiates a new {@link WebSession} and
     * associates it with the HTTP session as a session attribute.
     * <p>
     * The type of {@link WebSession} created depends on the value of the {@link #multi} flag:
     * </p>
     * <ul>
     * <li>If {@code multi} is {@code true}, an unauthenticated multi-user {@link WebSession} is created (user assigned only after
     * successful login).</li>
     * <li>If {@code multi} is {@code false}, an unauthenticated single-user {@link WebSession} is created. The simple server binds
     * local admin only via {@link LocalAdminAccess} for loopback peers — never at session creation.</li>
     * </ul>
     * <p>
     * The newly created {@link WebSession} is stored in the session attributes under the key {@code "session"}, making it
     * accessible to servlets and filters throughout the session's lifetime.
     * </p>
     *
     * @param se the {@link HttpSessionEvent} containing information about the session that is being created
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        Log.debug(() -> "Creating session " + se.getSession().getId());
        final WebSession ws;
        if (multi) {
            // Unauthenticated until Login.setUser; do not invent admin/"server" roles.
            ws = new WebSession(se.getSession().getId(), null, null);
        } else {
            // Unauthenticated until LocalAdminFilter elevates loopback requests.
            ws = new WebSession(se.getSession().getId());
        }
        se.getSession().setAttribute("session", ws);
    }

}
