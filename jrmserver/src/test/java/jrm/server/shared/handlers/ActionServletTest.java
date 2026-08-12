package jrm.server.shared.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link ActionServlet}.
 */
@DisplayName("ActionServlet")
class ActionServletTest {

    private WebSession webSession;
    private ActionServlet servlet;

    @BeforeEach
    void setUp() {
        webSession = TestWebSessions.newAdminSession("action-test");
        servlet = new ActionServlet();
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    private HttpServletRequest buildRequest(final String uri, final long contentLengthLong, final int contentLength,
            final String contentType, final byte[] body) {
        final HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("session")).thenReturn(webSession);
        final HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getSession()).thenReturn(httpSession);
        lenient().when(req.getSession(true)).thenReturn(httpSession);
        lenient().when(req.getRequestURI()).thenReturn(uri);
        lenient().when(req.getContentLengthLong()).thenReturn(contentLengthLong);
        lenient().when(req.getContentLength()).thenReturn(contentLength);
        lenient().when(req.getContentType()).thenReturn(contentType);
        if (body != null) {
            try {
                lenient().when(req.getInputStream()).thenReturn(new ServletInputStream() {
                    private final ByteArrayInputStream delegate = new ByteArrayInputStream(body);

                    @Override
                    public boolean isFinished() {
                        return delegate.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(final jakarta.servlet.ReadListener readListener) {
                        // no-op
                    }

                    @Override
                    public int read() throws IOException {
                        return delegate.read();
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return req;
    }

    @Nested
    @DisplayName("doPost")
    class DoPostTest {
        @Test
        @DisplayName("negative contentLengthLong returns SC_LENGTH_REQUIRED")
        void lengthRequired() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/actions/cmd", -1, -1, null, null);
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
        }

        @Test
        @DisplayName("negative contentLength returns SC_REQUEST_ENTITY_TOO_LARGE")
        void tooLarge() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/actions/cmd", 1L, -1, null, null);
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        }

        @Test
        @DisplayName("wrong content type returns SC_BAD_REQUEST")
        void wrongContentType() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/actions/cmd", 10L, 10, "text/plain", null);
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("unknown URI returns SC_NOT_IMPLEMENTED")
        void unknownUri() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/actions/unknown", 0L, 0, null, null);
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("valid JSON command processes and returns SC_OK")
        void validCommand() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final String jsonCmd = "{\"cmd\":\"Global.getMemory\"}";
            final byte[] body = jsonCmd.getBytes(StandardCharsets.UTF_8);
            final HttpServletRequest req = buildRequest("/actions/cmd", (long) body.length, body.length, "application/json", body);
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_OK);
        }

        @Test
        @DisplayName("session without authenticated user returns SC_UNAUTHORIZED")
        void unauthenticatedWebSession() throws Exception {
            final WebSession unauth = new WebSession("unauth-action");
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(unauth);
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final String jsonCmd = "{\"cmd\":\"Global.setProperty\",\"params\":{\"x\":\"y\"}}";
            final byte[] body = jsonCmd.getBytes(StandardCharsets.UTF_8);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getRequestURI()).thenReturn("/actions/cmd");
            when(req.getSession(true)).thenReturn(httpSession);
            lenient().when(req.getContentLengthLong()).thenReturn((long) body.length);
            lenient().when(req.getContentLength()).thenReturn(body.length);
            lenient().when(req.getContentType()).thenReturn("application/json");
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(unauth.hasUser()).isFalse();
        }
    }

    @Nested
    @DisplayName("doGet")
    class DoGetTest {
        @Test
        @DisplayName("unknown URI returns SC_NOT_IMPLEMENTED")
        void unknownUri() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getSession()).thenReturn(httpSession);
            when(req.getSession(true)).thenReturn(httpSession);
            when(req.getRequestURI()).thenReturn("/actions/unknown");
            servlet.doGet(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }

        @Test
        @DisplayName("unauthenticated GET returns SC_UNAUTHORIZED")
        void unauthenticatedGet() throws Exception {
            final WebSession unauth = new WebSession("unauth-get");
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(unauth);
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getSession(true)).thenReturn(httpSession);
            when(req.getRequestURI()).thenReturn("/actions/init");
            servlet.doGet(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("encapsulate")
    class EncapsulateTest {
        private static final String CMD_B = "{\"cmd\":\"b\"}";
        private static final String CMD_A = "{\"cmd\":\"a\"}";
        private static final String CMD_TEST = "{\"cmd\":\"test\"}";

        @Test
        @DisplayName("single message returned as-is")
        void singleMessage() {
            final var msgs = new ArrayList<String>();
            msgs.add(CMD_TEST);
            assertThat(servlet.encapsulate(msgs)).isEqualTo(CMD_TEST);
        }

        @Test
        @DisplayName("multiple messages wrapped in Global.multiCMD")
        void multipleMessages() {
            final var msgs = new ArrayList<String>();
            msgs.add(CMD_A);
            msgs.add(CMD_B);
            final String result = servlet.encapsulate(msgs);
            assertThat(result)
                .startsWith("{\"cmd\":\"Global.multiCMD\",\"params\":[")
                .contains(CMD_A)
                .contains(CMD_B);
        }
    }

    @Nested
    @DisplayName("sendResp")
    class SendRespTest {
        @Test
        @DisplayName("sends message with content length, json charset and nosniff")
        void sendMessage() throws Exception {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
                @Override
                public void write(final int b) {
                    out.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(final jakarta.servlet.WriteListener writeListener) {
                    // no-op
                }
            });
            servlet.sendResp(resp, "{\"cmd\":\"test\"}");
            verify(resp).setContentType("application/json;charset=UTF-8");
            verify(resp).setCharacterEncoding("UTF-8");
            verify(resp).setHeader("X-Content-Type-Options", "nosniff");
            verify(resp).setStatus(HttpServletResponse.SC_OK);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("{\"cmd\":\"test\"}");
        }

        @Test
        @DisplayName("null message sends zero content length")
        void sendNullMessage() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            servlet.sendResp(resp, null);
            verify(resp).setContentType("application/json;charset=UTF-8");
            verify(resp).setHeader("X-Content-Type-Options", "nosniff");
            verify(resp).setContentLength(0);
        }
    }
}