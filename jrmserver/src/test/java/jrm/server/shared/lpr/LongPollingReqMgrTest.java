package jrm.server.shared.lpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link LongPollingReqMgr}.
 */
@DisplayName("LongPollingReqMgr")
class LongPollingReqMgrTest {

    private WebSession webSession;
    private LongPollingReqMgr mgr;

    @BeforeEach
    void setUp() {
        webSession = TestWebSessions.newAdminSession("lpr-test");
        mgr = new LongPollingReqMgr(webSession);
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("send / sendOptional")
    class SendTest {
        @Test
        @DisplayName("send adds message to lprMsg queue")
        void sendAddsMessage() throws IOException {
            mgr.send("hello");
            assertThat(webSession.getLongPollingMessages()).contains("hello");
        }

        @Test
        @DisplayName("sendOptional adds only when queue is empty")
        void sendOptionalOnlyWhenEmpty() throws IOException {
            mgr.sendOptional("first");
            assertThat(webSession.getLongPollingMessages()).contains("first");

            mgr.sendOptional("second");
            // Queue already has "first", so "second" should not be added
            assertThat(webSession.getLongPollingMessages()).containsExactly("first");
        }
    }

    @Nested
    @DisplayName("isOpen")
    class IsOpenTest {
        @Test
        @DisplayName("isOpen always returns true")
        void isOpenAlwaysTrue() {
            assertThat(mgr.isOpen()).isTrue();
        }
    }

    @Nested
    @DisplayName("getSession")
    class GetSessionTest {
        @Test
        @DisplayName("returns the associated session")
        void returnsSession() {
            assertThat(mgr.getSession()).isSameAs(webSession);
        }
    }

    @Nested
    @DisplayName("setSession")
    class SetSessionTest {
        @Test
        @DisplayName("null session throws NullPointerException")
        void nullSessionThrows() {
            assertThatThrownBy(() -> mgr.setSession(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("process")
    class ProcessTest {
        @Test
        @DisplayName("processes Global.getMemory command without throwing")
        void processGetMemory() {
            mgr.process("{\"cmd\":\"Global.getMemory\"}");
            // The command should have been processed; lastAction should be updated
            assertThat(webSession.getLastAction()).isNotNull();
        }

        @Test
        @DisplayName("unknown command does not throw")
        void processUnknownCommand() {
            assertThatCode(() -> mgr.process("{\"cmd\":\"Unknown.command\"}")).doesNotThrowAnyException();
            assertThat(webSession.getLastAction()).isNotNull();
        }

        @Test
        @DisplayName("empty JSON does not throw")
        void processEmptyJson() {
            assertThatCode(() -> mgr.process("{}")).doesNotThrowAnyException();
        }
    }
}