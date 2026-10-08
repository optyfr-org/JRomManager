package jrm.server.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.JsonObject;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link AbstractSessionServlet#fillAndSendJSO}.
 */
@DisplayName("AbstractSessionServlet.fillAndSendJSO")
class AbstractSessionServletTest {

    private WebSession webSession;

    @BeforeEach
    void setUp() {
        webSession = TestWebSessions.newAdminSession("servlet-test");
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Test
    @DisplayName("fills JSON with session, msgs, and settings and sends as text/json")
    void fillAndSendJSO() throws Exception {
        final HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("session")).thenReturn(webSession);
        when(httpSession.getId()).thenReturn("servlet-test");

        final HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getSession()).thenReturn(httpSession);
        lenient().when(req.getHeader("accept-language")).thenReturn("en");

        final StringWriter writer = new StringWriter();
        final HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(writer));

        final var servlet = new AbstractSessionServlet() {
        };
        final JsonObject jso = new JsonObject();
        servlet.fillAndSendJSO(req, resp, jso);

        final String json = writer.toString();
        assertThat(json).contains("\"session\"").contains("\"msgs\"").contains("\"settings\"");
    }

    @Test
    @DisplayName("tolerates missing accept-language header (e.g. curl without -H)")
    void fillAndSendJSONoAcceptLanguage() throws Exception {
        final HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("session")).thenReturn(webSession);
        when(httpSession.getId()).thenReturn("servlet-test");

        final HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getSession()).thenReturn(httpSession);
        lenient().when(req.getHeader("accept-language")).thenReturn(null);

        final StringWriter writer = new StringWriter();
        final HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(writer));

        final var servlet = new AbstractSessionServlet() {
        };
        servlet.fillAndSendJSO(req, resp, new JsonObject());

        assertThat(writer.toString()).contains("\"session\"");
    }
}