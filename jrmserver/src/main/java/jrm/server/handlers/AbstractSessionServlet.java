package jrm.server.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.ResourceBundle;

import com.eclipsesource.json.JsonObject;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.locale.Messages;
import jrm.server.shared.WebSession;

/**
 * An abstract base servlet that provides helper functionality for managing web sessions, handling localization based on client
 * headers, and sending JSON responses.
 * <p>
 * Subclasses can utilize {@link #fillAndSendJSO} to consistently populate and dispatch JSON objects containing session identifiers,
 * localized messages, and user settings.
 */

public abstract class AbstractSessionServlet extends HttpServlet {

    /**
     * Fills the provided JsonObject with session information, localized messages, and user settings, then sends it as a JSON
     * response to the client.
     * <p>
     * This method retrieves the current web session, extracts the session ID, and adds it to the JSON object. It also loads
     * localized messages based on the client's "Accept-Language" header and includes them in the response. Finally, it adds the
     * user's settings to the JSON object before sending it back to the client.
     *
     * @param req the HttpServletRequest object that contains the request made by the client
     * @param resp the HttpServletResponse object that contains the response to be sent to the client
     * @param jso the JsonObject to be filled with session information, localized messages, and user settings before being sent as a
     *        response
     * 
     * @throws IOException if an I/O error occurs while writing the response
     */
    protected void fillAndSendJSO(HttpServletRequest req, HttpServletResponse resp, JsonObject jso) throws IOException {
        final var ws = (WebSession) req.getSession().getAttribute("session");
        final var sessionid = req.getSession().getId();
        jso.add("session", sessionid);
        final var msgs = new JsonObject();
        List<LanguageRange> lr = LanguageRange.parse(req.getHeader("accept-language"));
        ws.setMsgs(Messages.loadBundle(!lr.isEmpty() ? Locale.lookup(lr, Arrays.asList(Locale.getAvailableLocales())) : Locale.getDefault()));
        ResourceBundle rb = ws.getMsgs();
        rb.keySet().forEach(k -> {
            if (k != null && !k.isEmpty())
                msgs.add(k, rb.getString(k));
        });
        jso.add("msgs", msgs);
        jso.add("settings", ws.getUser().getSettings().asJSO());
        final var jsonStr = jso.toString();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/json");
        resp.setContentLength(jsonStr.getBytes(StandardCharsets.UTF_8).length);
        resp.getWriter().write(jsonStr);
        resp.getWriter().flush();
    }
}
