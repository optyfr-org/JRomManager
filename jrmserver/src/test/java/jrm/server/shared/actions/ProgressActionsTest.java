package jrm.server.shared.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link ProgressActions}.
 */
@DisplayName("ProgressActions")
class ProgressActionsTest {

    
    private ActionsMgr mgr;
    private final List<String> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        WebSession webSession;
        webSession = TestWebSessions.newAdminSession("progress-actions-test");
        sentMessages.clear();
        mgr = mock(ActionsMgr.class);
        when(mgr.getSession()).thenReturn(webSession);
        when(mgr.isOpen()).thenReturn(true);
        try {
            doAnswer(inv -> {
                sentMessages.add(inv.getArgument(0));
                return null;
            }).when(mgr).send(anyString());
            doAnswer(inv -> {
                sentMessages.add(inv.getArgument(0));
                return null;
            }).when(mgr).sendOptional(anyString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Test
    @DisplayName("constructor sends Progress open message")
    void constructorSendsOpen() {
        new ProgressActions(mgr);
        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.get(0)).contains("\"Progress\"");
    }

    @Nested
    @DisplayName("setInfos")
    class SetInfosTest {
        @Test
        @DisplayName("sets thread count and sends Progress.setInfos")
        void setInfos() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(4, true);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.setInfos");
            assertThat(sentMessages.get(0)).contains("\"threadCnt\":4");
            assertThat(sentMessages.get(0)).contains("\"multipleSubInfos\":true");
        }

        @Test
        @DisplayName("threadCnt <= 0 defaults to available processors")
        void setInfosDefaultsToProcessors() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(0, false);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.setInfos");
            assertThat(sentMessages.get(0)).contains("\"threadCnt\":" + Runtime.getRuntime().availableProcessors());
        }

        @Test
        @DisplayName("multipleSubInfos null allocates empty subinfos array")
        void setInfosNullSubInfos() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(2, null);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.setInfos");
            assertThat(sentMessages.get(0)).contains("threadCnt");
        }
    }

    @Nested
    @DisplayName("clearInfos")
    class ClearInfosTest {
        @Test
        @DisplayName("sends Progress.clearInfos")
        void clearInfos() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(2, true);
            sentMessages.clear();
            actions.clearInfos();
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.clearInfos");
        }
    }

    @Nested
    @DisplayName("setProgress")
    class SetProgressTest {
        @Test
        @DisplayName("positive val shows visible determinate progress bar")
        void setProgressVisible() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(1, false);
            sentMessages.clear();
            actions.setProgress("Scanning", 50, 100, "sub");
            assertThat(sentMessages).isNotEmpty();
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg)
                .contains("Progress.setFullProgress")
                .contains("\"visibility\":true")
                .contains("\"val\":50")
                .contains("\"max\":100");
        }

        @Test
        @DisplayName("negative val hides progress bar")
        void setProgressHide() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(1, false);
            sentMessages.clear();
            actions.setProgress("msg", 50, 100, null);
            sentMessages.clear();
            actions.setProgress(null, -1, null, null);
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg).contains("\"visibility\":false");
        }

        @Test
        @DisplayName("val 0 enters indeterminate mode")
        void setProgressIndeterminate() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(1, false);
            sentMessages.clear();
            actions.setProgress("Starting", 0, null, null);
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg).contains("\"indeterminate\":true");
        }

        @Test
        @DisplayName("rapid sub-percent updates are throttled to one send")
        void rapidUpdatesAreThrottled() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(1, false);
            sentMessages.clear();
            // Large max: 50 updates stay at integer 0% (no force), like per-chunk
            // bulk-stream reads. First sends, the rest fall in the throttle window.
            for (int i = 1; i <= 50; i++)
                actions.setProgress(null, i, 100_000, null);
            assertThat(sentMessages).hasSize(1);
        }

        @Test
        @DisplayName("integer percentage changes are never throttled")
        void percentageChangesAreNeverThrottled() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(1, false);
            sentMessages.clear();
            actions.setProgress(null, 1, 100, null);
            actions.setProgress(null, 2, 100, null);
            assertThat(sentMessages).hasSize(2);
        }

        @Test
        @DisplayName("completion update is always sent even inside throttle window")
        void completionIsNeverThrottled() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(1, false);
            sentMessages.clear();
            actions.setProgress(null, 1, 100, null);
            sentMessages.clear();
            actions.setProgress(null, 100, 100, null);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("\"val\":100");
        }

        @Test
        @DisplayName("recycled slot clears subinfo but keeps info label visible")
        void recycledSlotKeepsInfoLabel() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setInfos(2, true);
            final var currentOffset = new java.util.concurrent.atomic.AtomicInteger(0);
            final var free = new int[][] { {} };
            actions.setOffsetProvider(new jrm.misc.OffsetProvider() {
                @Override
                public int getOffset() {
                    return currentOffset.get();
                }

                @Override
                public int[] freeOffsets() {
                    return free[0];
                }
            });
            // Thread 0 finishes container A, thread 1 still scans container B.
            currentOffset.set(0);
            actions.setProgress("Scanned a.zip", null, null, "sub-a");
            currentOffset.set(1);
            actions.setProgress("Scanning b.zip", null, null, "sub-b");
            // Slot 0 is recycled; next push from thread 1 must keep slot 0's info but drop its subinfo.
            free[0] = new int[] { 0 };
            sentMessages.clear();
            actions.setProgress("Scanning b.zip", 1, 100, null);
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg).contains("Scanned a.zip");
            assertThat(msg).doesNotContain("sub-a");
        }
    }

    @Nested
    @DisplayName("setProgress2 / setProgress3")
    class SetProgress23Test {
        @Test
        @DisplayName("setProgress2 shows secondary bar")
        void setProgress2() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setProgress2("Sub", 10, 20);
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg).contains("\"visibility\":true");
        }

        @Test
        @DisplayName("setProgress2 with null msg and val hides bar")
        void setProgress2Hide() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setProgress2("Sub", 10, 20);
            sentMessages.clear();
            actions.setProgress2(null, null, null);
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg).contains("\"visibility\":false");
        }

        @Test
        @DisplayName("setProgress3 shows tertiary bar")
        void setProgress3() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.setProgress3("Tertiary", 5, 10);
            final var msg = sentMessages.get(sentMessages.size() - 1);
            assertThat(msg).contains("\"visibility\":true");
        }
    }

    @Nested
    @DisplayName("canCancel")
    class CanCancelTest {
        @Test
        @DisplayName("canCancel(false) sends Progress.canCancel with false")
        void canCancelFalse() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.canCancel(false);
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.canCancel");
            assertThat(sentMessages.get(0)).contains("\"canCancel\":false");
            assertThat(actions.canCancel()).isFalse();
        }

        @Test
        @DisplayName("canCancel(true) sends Progress.canCancel with true")
        void canCancelTrue() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.canCancel(true);
            assertThat(actions.canCancel()).isTrue();
        }
    }

    @Nested
    @DisplayName("cancel / doCancel")
    class CancelTest {
        @Test
        @DisplayName("doCancel sets cancel flag")
        void doCancelSetsFlag() {
            final ProgressActions actions = new ProgressActions(mgr);
            assertThat(actions.isCancel()).isFalse();
            actions.doCancel();
            assertThat(actions.isCancel()).isTrue();
        }
    }

    @Nested
    @DisplayName("close")
    class CloseTest {
        @Test
        @DisplayName("close sends Progress.close with errors")
        void closeSendsErrors() {
            final ProgressActions actions = new ProgressActions(mgr);
            actions.addError("err1");
            actions.addError("err2");
            sentMessages.clear();
            actions.close();
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.close");
            assertThat(sentMessages.get(0)).contains("err1");
            assertThat(sentMessages.get(0)).contains("err2");
        }

        @Test
        @DisplayName("close with no errors sends null errors array")
        void closeNoErrors() {
            final ProgressActions actions = new ProgressActions(mgr);
            sentMessages.clear();
            actions.close();
            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Progress.close");
        }
    }

    @Nested
    @DisplayName("getCurrent")
    class GetCurrentTest {
        @Test
        @DisplayName("getCurrent returns pb1 val")
        void getCurrent() {
            final ProgressActions actions = new ProgressActions(mgr);
            actions.setInfos(1, false);
            actions.setProgress(null, 42, 100, null);
            assertThat(actions.getCurrent()).isEqualTo(42);
        }

        @Test
        @DisplayName("getCurrent2 returns pb2 val")
        void getCurrent2() {
            final ProgressActions actions = new ProgressActions(mgr);
            actions.setProgress2("msg", 7, 10);
            assertThat(actions.getCurrent2()).isEqualTo(7);
        }

        @Test
        @DisplayName("getCurrent3 returns pb3 val")
        void getCurrent3() {
            final ProgressActions actions = new ProgressActions(mgr);
            actions.setProgress3("msg", 3, 10);
            assertThat(actions.getCurrent3()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("reload")
    class ReloadTest {
        @Test
        @DisplayName("reload re-sends open, setInfos, and full progress")
        void reloadResendsState() {
            final ProgressActions actions = new ProgressActions(mgr);
            actions.setInfos(2, true);
            sentMessages.clear();
            actions.reload(mgr);
            assertThat(sentMessages).hasSizeGreaterThanOrEqualTo(2);
            assertThat(sentMessages.get(0)).contains("\"Progress\"");
        }
    }
}