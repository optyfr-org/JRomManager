package jrm.server.shared.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.JsonObject;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link GlobalActions}.
 */
@DisplayName("GlobalActions")
class GlobalActionsTest {

    private WebSession webSession;
    private ActionsMgr mgr;
    private final List<String> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        webSession = TestWebSessions.newAdminSession("global-actions-test");
        sentMessages.clear();
        mgr = mock(ActionsMgr.class);
        when(mgr.getSession()).thenReturn(webSession);
        when(mgr.isOpen()).thenReturn(true);
        try {
            doAnswer(inv -> {
                sentMessages.add(inv.getArgument(0));
                return null;
            }).when(mgr).send(anyString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("setProperty")
    class SetPropertyTest {
        @Test
        @DisplayName("sets boolean and string properties and sends Global.updateProperty")
        void setProperties() {
            final JsonObject params = new JsonObject();
            params.add("bool.prop", true);
            params.add("string.prop", "value");
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Global.setProperty");
            jso.add("params", params);

            new GlobalActions(mgr).setProperty(jso);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Global.updateProperty");
            assertThat(sentMessages.get(0)).contains("bool.prop");
            assertThat(sentMessages.get(0)).contains("string.prop");
        }

        @Test
        @DisplayName("rejects setProperty when session has no authenticated user")
        void rejectsUnauthenticated() {
            webSession = new WebSession("global-unauth");
            when(mgr.getSession()).thenReturn(webSession);
            final JsonObject params = new JsonObject();
            params.add("evil.prop", "pwned");
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Global.setProperty");
            jso.add("params", params);

            new GlobalActions(mgr).setProperty(jso);

            assertThat(sentMessages).isEmpty();
            assertThat(webSession.hasUser()).isFalse();
        }
    }

    @Nested
    @DisplayName("setMemory")
    class SetMemoryTest {
        @Test
        @DisplayName("sends Global.setMemory with formatted memory stats")
        void setMemory() {
            final ResourceBundle msgs = webSession.getMsgs();
            assertThat(msgs).isNotNull();

            final JsonObject jso = new JsonObject();
            new GlobalActions(mgr).setMemory(jso);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Global.setMemory");
            assertThat(sentMessages.get(0)).contains("MiB");
        }
    }

    @Nested
    @DisplayName("gc")
    class GcTest {
        @Test
        @DisplayName("calls System.gc then sends Global.setMemory")
        void gc() {
            final JsonObject jso = new JsonObject();
            new GlobalActions(mgr).gc(jso);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Global.setMemory");
        }
    }

    @Nested
    @DisplayName("warn")
    class WarnTest {
        @Test
        @DisplayName("sends Global.warn with message")
        void warn() {
            new GlobalActions(mgr).warn("be careful");

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Global.warn");
            assertThat(sentMessages.get(0)).contains("be careful");
        }
    }
}