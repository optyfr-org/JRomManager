package jrm.fullserver.handlers;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.Log;
import jrm.server.handlers.AbstractSessionServlet;
import jrm.server.shared.WebSession;

/**
 * Servlet implementation for managing user sessions.
 * <p>
 * This servlet handles POST requests to manage user sessions. It retrieves the current session from the request, checks if the user
 * is authenticated, and returns a JSON response indicating the authentication status and whether the user has admin privileges. If
 * any exceptions occur during processing, it logs the error and returns an internal server error status.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 */
public class SessionServlet extends AbstractSessionServlet {
    /**
     * Handles POST requests to manage user sessions. It retrieves the current session, checks authentication status, and returns a
     * JSON response with the authentication status and admin privileges. If an error occurs, it logs the error and returns an
     * internal server error status.
     * 
     * @param req The HttpServletRequest object containing the client's request.
     * @param resp The HttpServletResponse object for sending the response back to the client.
     * 
     * @throws ServletException if a servlet-specific error occurs during processing.
     * @throws IOException if an I/O error occurs during processing of the request or response.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            final var jso = new JsonObject();
            jso.add("authenticated", true);
            final var ws = (WebSession) req.getSession().getAttribute("session");
            jso.add("admin", ws.getUser().isAdmin());
            fillAndSendJSO(req, resp, jso);
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
