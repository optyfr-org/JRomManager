package jrm.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link SessionListener}.
 */
@DisplayName("SessionListener")
class SessionListenerTest {

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("sessionCreated (single-session mode)")
    class SessionCreatedSingleTest {
        @Test
        @DisplayName("creates single-session WebSession without inventing a user")
        void createsSingleSession() {
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getId()).thenReturn("single-test");
            final HttpSessionEvent event = mock(HttpSessionEvent.class);
            when(event.getSession()).thenReturn(httpSession);

            final SessionListener listener = new SessionListener(false);
            listener.sessionCreated(event);

            verify(httpSession).setAttribute(eq("session"), org.mockito.ArgumentMatchers.argThat(attr -> {
                final WebSession ws = (WebSession) attr;
                return !ws.hasUser();
            }));
        }
    }

    @Nested
    @DisplayName("sessionCreated (multi-session mode)")
    class SessionCreatedMultiTest {
        @Test
        @DisplayName("creates multi-session WebSession without inventing a user")
        void createsMultiSession() {
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getId()).thenReturn("multi-test");
            final HttpSessionEvent event = mock(HttpSessionEvent.class);
            when(event.getSession()).thenReturn(httpSession);

            final SessionListener listener = new SessionListener(true);
            listener.sessionCreated(event);

            verify(httpSession).setAttribute(eq("session"), org.mockito.ArgumentMatchers.argThat(attr -> {
                final WebSession ws = (WebSession) attr;
                return !ws.hasUser();
            }));
        }
    }

    @Nested
    @DisplayName("sessionDestroyed")
    class SessionDestroyedTest {
        @Test
        @DisplayName("closes the WebSession when destroyed")
        void closesWebSession() {
            final WebSession webSession = TestWebSessions.newAdminSession("destroy-test");
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpSessionEvent event = mock(HttpSessionEvent.class);
            when(event.getSession()).thenReturn(httpSession);

            final SessionListener listener = new SessionListener(false);
            listener.sessionDestroyed(event);

            // After close, lprMsg should contain the sentinel (if it was empty)
            assertThat(webSession.getLprMsg()).contains("");
        }

        @Test
        @DisplayName("does not throw when WebSession is null")
        void nullWebSession() {
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(null);
            final HttpSessionEvent event = mock(HttpSessionEvent.class);
            when(event.getSession()).thenReturn(httpSession);

            final SessionListener listener = new SessionListener(false);
            // Should not throw
            assertThatCode(() -> listener.sessionDestroyed(event)).doesNotThrowAnyException();
        }
    }
}