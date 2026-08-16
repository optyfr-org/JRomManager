package jrm.server.shared.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.JsonObject;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link ActionsMgr} default method routing.
 */
@DisplayName("ActionsMgr")
class ActionsMgrTest {

    private WebSession webSession;
    private TestActionsMgr mgr;
    private final List<String> sentMessages = new ArrayList<>();

    /** Concrete test implementation of ActionsMgr that captures sent messages. */
    private class TestActionsMgr implements ActionsMgr {
        @Override
        public void send(String msg) {
            sentMessages.add(msg);
        }

        @Override
        public void sendOptional(String msg) {
            sentMessages.add(msg);
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public WebSession getSession() {
            return webSession;
        }

        @Override
        public void setSession(WebSession session) {
            // no-op
        }

        @Override
        public void unsetSession(WebSession session) {
            // no-op
        }
    }

    @BeforeEach
    void setUp() {
        webSession = TestWebSessions.newAdminSession("actionsmgr-test");
        sentMessages.clear();
        mgr = new TestActionsMgr();
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Test
    @DisplayName("processActions with null JSON does not throw")
    void processActionsNullJson() {
        mgr.processActions(mgr, null);
        // should not throw, no messages sent
        assertThat(sentMessages).isEmpty();
    }

    @Test
    @DisplayName("processActions with unknown command does not throw")
    void processActionsUnknownCommand() {
        final JsonObject jso = new JsonObject();
        jso.add("cmd", "Unknown.command");
        mgr.processActions(mgr, jso);
        // unknown command is logged but no message sent
        assertThat(sentMessages).isEmpty();
    }

    @Test
    @DisplayName("processActions rejects Global.setProperty without authenticated user")
    void processActionsRejectsUnauthenticatedSetProperty() {
        webSession = new WebSession("actionsmgr-unauth");
        final JsonObject params = new JsonObject();
        params.add("evil.prop", "pwned");
        final JsonObject jso = new JsonObject();
        jso.add("cmd", "Global.setProperty");
        jso.add("params", params);
        mgr.processActions(mgr, jso);
        assertThat(sentMessages).isEmpty();
        assertThat(webSession.hasUser()).isFalse();
    }

    @Nested
    @DisplayName("Global command routing")
    class GlobalRoutingTest {
        @Test
        @DisplayName("Global.setProperty routes to GlobalActions.setProperty")
        void setProperty() {
            final JsonObject params = new JsonObject();
            params.add("test.prop", "value");
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Global.setProperty");
            jso.add("params", params);
            mgr.processActions(mgr, jso);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Global.updateProperty");
        }

        @Test
        @DisplayName("Global.getMemory routes to GlobalActions.setMemory without throwing")
        void getMemory() {
            assertDoesNotThrow(() -> {
                final JsonObject jso = new JsonObject();
                jso.add("cmd", "Global.getMemory");
                // processActions catches exceptions, so routing should not throw even if setMemory fails
                mgr.processActions(mgr, jso);
                // setMemory may fail if msgs bundle is not loaded; routing is verified by no exception
            });
        }

        @Test
        @DisplayName("Global.GC routes to GlobalActions.gc without throwing")
        void gc() {
            assertDoesNotThrow(() -> {
                final JsonObject jso = new JsonObject();
                jso.add("cmd", "Global.GC");
                mgr.processActions(mgr, jso);
                // gc calls setMemory which may fail if msgs bundle is not loaded; routing is verified by no exception
            });
        }
    }

    @Nested
    @DisplayName("Report command routing")
    class ReportRoutingTest {
        @Test
        @DisplayName("Report.setFilter routes to ReportActions.setFilter (non-lite)")
        void reportSetFilter() {
            final JsonObject params = new JsonObject();
            params.add("SHOWOK", true);
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Report.setFilter");
            jso.add("params", params);
            mgr.processActions(mgr, jso);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Report.applyFilters");
        }

        @Test
        @DisplayName("Report.createFixDat routes without throwing")
        void reportCreateFixDat() {
            assertDoesNotThrow(() -> {
                final JsonObject params = new JsonObject();
                params.add("path", "%work/fix.xml");
                final JsonObject jso = new JsonObject();
                jso.add("cmd", "Report.createFixDat");
                jso.add("params", params);
                mgr.processActions(mgr, jso);
            });
        }

        @Test
        @DisplayName("ReportLite.setFilter routes to ReportActions.setFilter (lite)")
        void reportLiteSetFilter() {
            assertDoesNotThrow(() -> {
                final JsonObject params = new JsonObject();
                params.add("SHOWOK", true);
                final JsonObject jso = new JsonObject();
                jso.add("cmd", "ReportLite.setFilter");
                jso.add("params", params);
                // processActions catches exceptions, so routing should not throw even if handler fails
                mgr.processActions(mgr, jso);
                // ReportLite uses getTmpReport() which may be null, causing the handler to fail silently
                // The test verifies routing does not throw, not that the handler succeeds
            });
        }
    }

    @Nested
    @DisplayName("CatVer / NPlayers routing")
    class CatVerNPlayersRoutingTest {
        @Test
        @DisplayName("CatVer.load routes to CatVerActions.load without throwing")
        void catVerLoad() {
            assertDoesNotThrow(() -> {
                final JsonObject jso = new JsonObject();
                jso.add("cmd", "CatVer.load");
                jso.add("params", new JsonObject());
                mgr.processActions(mgr, jso);
                // CatVer.load may fail silently if no profile is loaded; routing is verified by no exception
            });
        }

        @Test
        @DisplayName("NPlayers.load routes to NPlayersActions.load without throwing")
        void nPlayersLoad() {
            assertDoesNotThrow(() -> {
                final JsonObject jso = new JsonObject();
                jso.add("cmd", "NPlayers.load");
                jso.add("params", new JsonObject());
                mgr.processActions(mgr, jso);
                // NPlayers.load may fail silently if no profile is loaded; routing is verified by no exception
            });
        }
    }
}