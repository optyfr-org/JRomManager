package jrm.server;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jrm.server.shared.WebSession;

/**
 * Loopback-only elevation for the simple (unauthenticated) server.
 * <p>
 * HTTP sessions are created without a user. Privileged identity is bound only when the direct TCP peer is a loopback
 * address, so binding to {@code 0.0.0.0} does not grant admin to remote clients.
 * </p>
 */
public final class LocalAdminAccess {

    private static final String LOCAL_USER = "JRomManager";
    private static final String[] LOCAL_ROLES = { "admin" };

    private LocalAdminAccess() {
    }

    /**
     * @return {@code true} if {@code request.getRemoteAddr()} is a loopback address
     */
    public static boolean isLoopback(final HttpServletRequest request) {
        if (request == null)
            return false;
        final String addr = request.getRemoteAddr();
        if (addr == null || addr.isBlank())
            return false;
        try {
            return InetAddress.getByName(addr).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return "127.0.0.1".equals(addr) || "::1".equals(addr) || "0:0:0:0:0:0:0:1".equals(addr);
        }
    }

    /**
     * If the HTTP session has a {@link WebSession} without a user and the peer is loopback, bind the local admin identity.
     * No-op when already authenticated, missing session attribute, or non-loopback.
     *
     * @return the web session after possible elevation, or {@code null}
     */
    public static WebSession ensureLocalAdmin(final HttpServletRequest request) {
        if (request == null)
            return null;
        final HttpSession httpSession = request.getSession(true);
        if (httpSession == null)
            return null;
        final Object attr = httpSession.getAttribute("session");
        if (!(attr instanceof WebSession ws))
            return null;
        if (!ws.hasUser() && isLoopback(request))
            ws.setUser(LOCAL_USER, LOCAL_ROLES);
        return ws;
    }
}
