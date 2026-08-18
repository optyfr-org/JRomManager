package jrm.fullserver.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

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
 * Unit tests for {@link SessionServlet} (fullserver variant).
 */
@DisplayName("SessionServlet (fullserver)")
class SessionServletTest {

    /** The web session to use for the tests. */
    private WebSession webSession;

    @BeforeEach
    void setUp() {
        webSession = TestWebSessions.newAdminSession("fullserver-servlet-test");
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    /** Builds a mock HttpServletRequest with an admin user session. */
    private HttpServletRequest buildRequest() {
        final HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("session")).thenReturn(webSession);
        when(httpSession.getId()).thenReturn("fullserver-servlet-test");
        final HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getSession()).thenReturn(httpSession);
        lenient().when(req.getHeader("accept-language")).thenReturn("en");
        return req;
    }

    /** Tests that doPost sets the authenticated and admin fields to true for an admin user and returns the expected JSON string.     */  
    @Nested
    @DisplayName("doPost admin user")
    class DoPostAdminTest {
        @Test
        @DisplayName("adds authenticated=true and admin=true for admin user")
        void doPostAdmin() throws Exception {
            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));
            final HttpServletRequest req = buildRequest();

            final SessionServlet servlet = new SessionServlet();
            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"authenticated\":true").contains("\"admin\":true");
        }
    }

    /**
     * Tests that doPost sets the admin field to false for a non-admin user and returns the expected JSON string.
     */
    @Nested
    @DisplayName("doPost non-admin user")
    class DoPostNonAdminTest {
        @Test
        @DisplayName("adds admin=false for non-admin user")
        void doPostNonAdmin() throws Exception {
            webSession = TestWebSessions.newSession("non-admin-test", "user", new String[] { "user" });
            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));
            final HttpServletRequest req = buildRequest();

            final SessionServlet servlet = new SessionServlet();
            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"admin\":false");
        }
    }

    @Nested
    @DisplayName("doPost unauthenticated")
    class DoPostUnauthenticatedTest {
        @Test
        @DisplayName("returns SC_UNAUTHORIZED when session has no user")
        void doPostNoUser() throws Exception {
            webSession = new WebSession("fullserver-unauth");
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest();

            final SessionServlet servlet = new SessionServlet();
            servlet.doPost(req, resp);

            verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(webSession.hasUser()).isFalse();
        }

        @Test
        @DisplayName("returns SC_UNAUTHORIZED when session attribute is missing")
        void doPostMissingSession() throws Exception {
            webSession = null;
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest();

            final SessionServlet servlet = new SessionServlet();
            servlet.doPost(req, resp);

            verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /**
     * Tests that doPost sets the response status to SC_INTERNAL_SERVER_ERROR when an exception occurs while writing the response.
     */
    @Nested
    @DisplayName("doPost exception")
    class DoPostExceptionTest {
        @Test
        @DisplayName("sets SC_INTERNAL_SERVER_ERROR on exception")
        void doPostException() throws Exception {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenThrow(new RuntimeException("boom"));
            final HttpServletRequest req = buildRequest();

            final SessionServlet servlet = new SessionServlet();
            servlet.doPost(req, resp);

            verify(resp).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}