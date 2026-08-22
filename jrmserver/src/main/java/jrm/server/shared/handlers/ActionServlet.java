package jrm.server.shared.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.server.shared.WebSession;
import jrm.server.shared.actions.CatVerActions;
import jrm.server.shared.actions.NPlayersActions;
import jrm.server.shared.actions.ProfileActions;
import jrm.server.shared.lpr.LongPollingReqMgr;

/**
 * Servlet responsible for handling client action requests and long polling communication.
 * <p>
 * This servlet manages three types of endpoints:
 * <ul>
 * <li><b>/actions/cmd</b> - Processes JSON command requests from clients</li>
 * <li><b>/actions/init</b> - Initializes session and establishes long polling connection</li>
 * <li><b>/actions/lpr</b> - Long polling request endpoint for server-to-client notifications</li>
 * </ul>
 * <p>
 * The long polling mechanism allows the server to push notifications to clients efficiently by holding HTTP connections open until
 * messages are available or a timeout occurs.
 * 
 * @author JRM Project
 * 
 * @version 1.0
 * 
 * @since 1.0
 * 
 * @see WebSession
 * @see LongPollingReqMgr
 */
public class ActionServlet extends HttpServlet {

    /**
     * MIME type constant for JSON responses ({@code "application/json"}).
     * <p>
     * Used to set the {@code Content-Type} header in responses and to validate the {@code Content-Type} header of incoming requests
     * on the {@code /actions/cmd} endpoint.
     */
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";

    /**
     * Handles POST requests for processing client commands.
     * <p>
     * This method processes JSON command requests sent to the /actions/cmd endpoint. It validates the request content length and
     * type, then delegates command processing to the {@link LongPollingReqMgr} associated with the current session.
     * <p>
     * HTTP status codes returned:
     * <ul>
     * <li>{@code 411 Length Required} - if content length is negative (long)</li>
     * <li>{@code 413 Request Entity Too Large} - if content length exceeds int range</li>
     * <li>{@code 400 Bad Request} - if content type is not application/json, or if body is empty</li>
     * <li>{@code 401 Unauthorized} - if there is no HTTP session or the session has no authenticated user</li>
     * <li>{@code 501 Not Implemented} - if request URI does not match /actions/cmd</li>
     * <li>{@code 200 OK} - command processed successfully</li>
     * <li>{@code 500 Internal Server Error} - on unexpected exceptions</li>
     * </ul>
     * 
     * @param req the HTTP servlet request containing the JSON command
     * @param resp the HTTP servlet response for sending acknowledgment
     * 
     * @throws ServletException if a servlet-specific error occurs
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            if ("/actions/cmd".equals(req.getRequestURI())) {
                final WebSession sess = requireAuthenticatedSession(req, resp);
                if (sess == null)
                    return;
                if (req.getContentLengthLong() < 0)
                    resp.setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
                else if (req.getContentLength() < 0)
                    resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                else if (req.getContentLength() > 0) {
                    if (isJsonContentType(req.getContentType())) {
                        final var buf = new byte[req.getContentLength()];
                        req.getInputStream().read(buf, 0, req.getContentLength());
                        new LongPollingReqMgr(sess).process(new String(buf, StandardCharsets.UTF_8));
                        resp.setContentLength(0);
                        resp.setContentType(APPLICATION_JSON_UTF8);
                        resp.setHeader("X-Content-Type-Options", "nosniff");
                        resp.setStatus(HttpServletResponse.SC_OK);
                    } else
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                } else
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else
                resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        } catch (Exception _) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles GET requests for session initialization and long polling.
     * <p>
     * This method processes two types of GET requests:
     * <ul>
     * <li><b>/actions/init</b> - Initializes the session with profile data and establishes long polling</li>
     * <li><b>/actions/lpr</b> - Handles long polling requests for server-to-client notifications</li>
     * </ul>
     * <p>
     * HTTP status codes returned:
     * <ul>
     * <li>{@code 200 OK} - request processed successfully</li>
     * <li>{@code 401 Unauthorized} - if there is no HTTP session or the session has no authenticated user</li>
     * <li>{@code 410 Gone} - if the server is terminating</li>
     * <li>{@code 501 Not Implemented} - if request URI does not match known endpoints</li>
     * <li>{@code 500 Internal Server Error} - on unexpected exceptions</li>
     * </ul>
     * 
     * @param req the HTTP servlet request
     * @param resp the HTTP servlet response
     * 
     * @throws ServletException if a servlet-specific error occurs
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            final WebSession sess = requireAuthenticatedSession(req, resp);
            if (sess == null)
                return;
            switch (req.getRequestURI()) {
                case "/actions/init": {
                    doInit(sess);
                    doLPR(resp, sess);
                    break;
                }
                case "/actions/lpr": {
                    doLPR(resp, sess);
                    break;
                }
                default:
                    resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
                    break;
            }
        } catch (Exception _) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Returns the request {@link WebSession} only when it has an authenticated user; otherwise responds with
     * {@code 401 Unauthorized} and returns {@code null}.
     * <p>
     * Creating an HTTP session is not enough: multi-user {@link WebSession}s stay without a user until login, and
     * {@link jrm.security.Session#getUser()} no longer invents an admin identity on server sessions. The simple
     * single-user server elevates only loopback peers via {@link jrm.server.LocalAdminFilter} before this check runs.
     * </p>
     *
     * @param req  the HTTP request
     * @param resp the HTTP response
     * @return the authenticated session, or {@code null} if the request was rejected
     */
    static WebSession requireAuthenticatedSession(final HttpServletRequest req, final HttpServletResponse resp) {
        final var httpSession = req.getSession(true);
        final WebSession sess = httpSession != null ? (WebSession) httpSession.getAttribute("session") : null;
        if (sess == null || !sess.hasUser()) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        return sess;
    }

    /**
     * Initializes the session with profile-related actions and worker progress.
     * <p>
     * This method performs the following initialization tasks:
     * <ul>
     * <li>Loads profile actions if a current profile is set</li>
     * <li>Loads category/version actions for the current profile</li>
     * <li>Loads N-players actions for the current profile</li>
     * <li>Reloads worker progress if a worker thread is active</li>
     * </ul>
     * 
     * @param sess the web session to initialize
     */
    void doInit(WebSession sess) {
        final var cmd = new LongPollingReqMgr(sess);
        if (sess.getCurrProfile() != null) {
            new ProfileActions(cmd).loaded(sess.getCurrProfile());
            new CatVerActions(cmd).loaded(sess.getCurrProfile());
            new NPlayersActions(cmd).loaded(sess.getCurrProfile());
        }
        if (sess.getWorker() != null && sess.getWorker().isAlive() && sess.getWorker().getProgress() != null)
            sess.getWorker().getProgress().reload(cmd);
    }

    /**
     * Handles long polling requests to deliver server-to-client notifications.
     * <p>
     * This method implements a long polling mechanism where:
     * <ul>
     * <li>The connection is held open for up to 20 seconds waiting for messages</li>
     * <li>Multiple messages (up to 100) can be batched in a single response</li>
     * <li>Messages are encapsulated in a single JSON response</li>
     * <li>If the server is terminating, a GONE status is returned</li>
     * </ul>
     * 
     * @param resp the HTTP servlet response for sending notifications
     * @param sess the web session containing the message queue
     */
    void doLPR(HttpServletResponse resp, WebSession sess) {
        if (WebSession.isTerminate()) {
            resp.setStatus(HttpServletResponse.SC_GONE);
            return;
        }
        try {
            var msg = sess.getLprMsg().poll(20, TimeUnit.SECONDS);
            if (msg == null && WebSession.isTerminate())
                resp.setStatus(HttpServletResponse.SC_GONE);
            else if (msg == null)
                sendResp(resp, null);
            else {
                final var msgs = new ArrayList<String>();
                msgs.add(msg);
                while (msgs.size() < 100) {
                    if (null == (msg = sess.getLprMsg().poll()))
                        break;
                    msgs.add(msg);
                }
                msg = encapsulate(msgs);
                sendResp(resp, msg);
            }
        } catch (InterruptedException _) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Thread.currentThread().interrupt();
        } catch (IOException _) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends the HTTP response with the specified message content.
     * <p>
     * This method sets JSON content type with charset, {@code X-Content-Type-Options: nosniff} (so browsers do not MIME-sniff the
     * body as HTML), and writes the message to the response. If the message is null, it sends an empty response with zero content
     * length. The body is server-built JSON for the authenticated long-poll client and must not be HTML-encoded.
     * 
     * @param resp the HTTP servlet response
     * @param msg the message content to send, or null for empty response
     * 
     * @throws IOException if an I/O error occurs while writing the response
     */
    void sendResp(HttpServletResponse resp, String msg) throws IOException {
        resp.setContentType(APPLICATION_JSON_UTF8);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setStatus(HttpServletResponse.SC_OK);
        if (msg != null) {
            final byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            resp.setContentLength(bytes.length);
            resp.getOutputStream().write(bytes);
        } else
            resp.setContentLength(0);
    }

    /**
     * Returns whether {@code contentType} is JSON, ignoring optional parameters such as {@code charset}.
     *
     * @param contentType the request {@code Content-Type} header value, may be null
     * @return {@code true} if the media type is {@code application/json}
     */
    static boolean isJsonContentType(final String contentType) {
        if (contentType == null || contentType.isBlank())
            return false;
        final int semi = contentType.indexOf(';');
        final String mediaType = (semi < 0 ? contentType : contentType.substring(0, semi)).trim();
        return APPLICATION_JSON.equalsIgnoreCase(mediaType);
    }

    /**
     * Encapsulates multiple messages into a single JSON response.
     * <p>
     * If multiple messages are provided, they are wrapped in a Global.multiCMD JSON structure. If only one message is present, it
     * is returned as-is.
     * 
     * @param msgs the list of messages to encapsulate
     * 
     * @return the encapsulated JSON string, or the single message if only one exists
     */
    String encapsulate(final ArrayList<String> msgs) {
        String msg;
        if (msgs.size() > 1)
            msg = "{\"cmd\":\"Global.multiCMD\",\"params\":[" + msgs.stream().collect(Collectors.joining(",")) + "]}";
        else
            msg = msgs.get(0);
        return msg;
    }
}
