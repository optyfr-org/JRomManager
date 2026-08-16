package jrm.server.handlers;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.Log;

/**
 * Servlet to handle session-related requests. This servlet is used to create a new session and return the session ID to the client.
 * The session ID is stored in a cookie, so the client can use it for subsequent requests.
 */
public class SessionServlet extends AbstractSessionServlet {
    /**
     * Handles POST requests to create a new session and return the session ID to the client. The session ID is stored in a cookie,
     * so the client can use it for subsequent requests. The response is a JSON object containing the session ID. If an error
     * occurs, a 500 Internal Server Error status is returned.
     * <p>
     * The method retrieves the current session from the request, checks if the user is authenticated, and returns a JSON response
     * with the authentication status and admin privileges. If any exceptions occur during processing, it logs the error and returns
     * an internal server error status. The fillAndSendJSO method is used to send the JSON response back to the client, and it is
     * assumed to be implemented in the AbstractSessionServlet class, which this servlet extends. The method also handles any
     * exceptions that may occur during processing and logs them using the Log class.
     * <p>
     * Note: The actual implementation of the fillAndSendJSO method and how the session is managed (e.g., how the session ID is
     * generated and stored in a cookie) is not shown in this code snippet, but it is assumed to be handled appropriately in the
     * AbstractSessionServlet class or elsewhere in the application.
     * <p>
     * Example usage:
     * 
     * <pre>
     * <code class='language-http'>
     * POST /session HTTP/1.1
     * Host: example.com
     * Content-Type: application/json
     * </code>
     * </pre>
     * 
     * Response:
     * 
     * <pre>
     * <code class='language-http'>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * {
     *     "authenticated": true,
     *     "admin": false
     * }
     * </code>
     * </pre>
     * 
     * If an error occurs:
     * 
     * <pre>
     * <code class='language-http'>
     * HTTP/1.1 500 Internal Server Error
     * Content-Type: application/json</code><code class='language-json'>
     * {
     *     "error": "An error occurred while processing the request."
     * }
     * </code>
     * </pre>
     * 
     * The method is designed to handle any exceptions that may occur during the processing of the request, ensuring that the server
     * responds with an appropriate error status and logs the error details for debugging purposes. The use of a JSON response
     * allows for easy integration with client-side applications that can parse the JSON data to determine the authentication status
     * and admin privileges of the user.
     *
     * @param req the HttpServletRequest object that contains the request the client made to the servlet
     * @param resp the HttpServletResponse object that contains the response the servlet returns to the client
     * 
     * @throws ServletException if the request could not be handled
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            fillAndSendJSO(req, resp, new JsonObject());
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
