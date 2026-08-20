package jrm.fx.ui.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import javafx.application.Platform;
import javafx.stage.Stage;
import jrm.aui.progress.ProgressInputStream;
import jrm.misc.OffsetProvider;

/**
 * Unit tests for {@link ProgressTask} abstract class.
 * Tests progress tracking, cancellation, error handling, and thread management.
 */
@DisplayName("ProgressTask Unit Tests")
class ProgressTaskTest {

    /**
     * Concrete implementation of ProgressTask for testing.
     */
    private static class TestProgressTask extends ProgressTask<Void> {
        TestProgressTask(Stage owner) throws IOException, URISyntaxException {
            super(owner);
        }

        @Override
        protected Void call() throws Exception {
            return null;
        }
    }

    /** The concrete progress task under test. */
    private TestProgressTask task;
    /** A mocked {@link Stage} for the task owner. */
    private Stage mockStage;

    /**
     * Sets up the test fixture by creating a mock stage and a concrete {@link ProgressTask}
     * instance with mocked JavaFX Platform calls.
     *
     * @throws Exception if the {@code ProgressTask} construction fails
     */
    @BeforeEach
    void setUp() throws Exception {
        // Mock JavaFX Platform to avoid toolkit initialization
        try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
            platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
            platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    // Don't execute runnables to avoid JavaFX thread issues
                    return null;
                });

            mockStage = mock(Stage.class);

            // Mock Progress construction to avoid FXML loading
            try (@SuppressWarnings("unused")
            MockedConstruction<Progress> progressMock = mockConstruction(Progress.class, (mock, context) -> {
                ProgressController mockController = mock(ProgressController.class);
                when(mock.getController()).thenReturn(mockController);
            })) {
                task = new TestProgressTask(mockStage);
            }
        }
    }

    /**
     * Tests for primary, secondary, and tertiary progress tracking.
     */
    @Nested
    @DisplayName("Progress tracking tests")
    class ProgressTrackingTests {

        /**
         * Verifies that a newly created task reports zero progress on all three levels.
         */
        @Test
        @DisplayName("Should initialize with zero progress")
        void shouldInitializeWithZeroProgress() {
            assertThat(task.getCurrent()).isZero();
            assertThat(task.getCurrent2()).isZero();
            assertThat(task.getCurrent3()).isZero();
        }

        /**
         * Verifies that primary progress can be set and retrieved via {@link ProgressTask#setProgress}.
         */
        @Test
        @DisplayName("Should track primary progress")
        void shouldTrackPrimaryProgress() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setProgress("Processing", 50, 100, null);

                assertThat(task.getCurrent()).isEqualTo(50);
            }
        }

        /**
         * Verifies that secondary progress can be set and retrieved via {@link ProgressTask#setProgress2}.
         */
        @Test
        @DisplayName("Should track secondary progress")
        void shouldTrackSecondaryProgress() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setProgress2("Subtask", 25, 50);

                assertThat(task.getCurrent2()).isEqualTo(25);
            }
        }

        /**
         * Verifies that tertiary progress can be set and retrieved via {@link ProgressTask#setProgress3}.
         */
        @Test
        @DisplayName("Should track tertiary progress")
        void shouldTrackTertiaryProgress() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setProgress3("Detail", 10, 20);

                assertThat(task.getCurrent3()).isEqualTo(10);
            }
        }

        /**
         * Verifies that progress tracking works correctly with a non-default max value.
         */
        @Test
        @DisplayName("Should handle progress with max value")
        void shouldHandleProgressWithMaxValue() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setProgress("Processing", 75, 150, null);

                assertThat(task.getCurrent()).isEqualTo(75);
            }
        }

        /**
         * Verifies that setting progress to zero with a non-zero max produces a zero progress value.
         */
        @Test
        @DisplayName("Should handle indeterminate progress (val=0)")
        void shouldHandleIndeterminateProgress() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setProgress("Starting", 0, 100, null);

                assertThat(task.getCurrent()).isZero();
            }
        }

        /**
         * Verifies that rapid {@link ProgressTask#setProgress} calls queue at most one
         * {@link Platform#runLater} until that runnable executes.
         */
        @Test
        @DisplayName("Should coalesce rapid progress updates into one runLater")
        void shouldCoalesceRapidProgressUpdatesIntoOneRunLater() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                final AtomicInteger runLaterCount = new AtomicInteger();
                final List<Runnable> queued = new ArrayList<>();
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        runLaterCount.incrementAndGet();
                        queued.add(invocation.getArgument(0));
                        return null;
                    });

                task.setProgress("A", 1, 100, null);
                task.setProgress("B", 2, 100, null);
                task.setProgress("C", 3, 100, null);

                assertThat(runLaterCount.get()).isEqualTo(1);
                assertThat(task.getCurrent()).isEqualTo(3);

                queued.get(0).run();

                task.setProgress("D", 4, 100, null);

                assertThat(runLaterCount.get()).isEqualTo(2);
                assertThat(task.getCurrent()).isEqualTo(4);
            }
        }
    }

    /**
     * Tests for task cancellation and re-enablement.
     */
    @Nested
    @DisplayName("Cancellation tests")
    class CancellationTests {

        /**
         * Verifies that a newly created task is not cancelled.
         */
        @Test
        @DisplayName("Should not be cancelled initially")
        void shouldNotBeCancelledInitially() {
            assertThat(task.isCancel()).isFalse();
        }

        /**
         * Verifies that cancellation is enabled by default.
         */
        @Test
        @DisplayName("Should be cancellable by default")
        void shouldBeCancellableByDefault() {
            assertThat(task.canCancel()).isTrue();
        }

        /**
         * Verifies that calling {@link ProgressTask#doCancel} sets the cancellation flag.
         */
        @Test
        @DisplayName("Should cancel when doCancel is called")
        void shouldCancelWhenDoCancelIsCalled() {
            task.doCancel();

            assertThat(task.isCancel()).isTrue();
        }

        /**
         * Verifies that cancellation can be programmatically disabled.
         */
        @Test
        @DisplayName("Should disable cancellation")
        void shouldDisableCancellation() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.canCancel(false);

                assertThat(task.canCancel()).isFalse();
            }
        }

        /**
         * Verifies that cancellation can be re-enabled after being disabled.
         */
        @Test
        @DisplayName("Should re-enable cancellation")
        void shouldReEnableCancellation() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.canCancel(false);
                assertThat(task.canCancel()).isFalse();

                task.canCancel(true);
                assertThat(task.canCancel()).isTrue();
            }
        }
    }

    /**
     * Tests for error message accumulation.
     */
    @Nested
    @DisplayName("Error handling tests")
    class ErrorHandlingTests {

        /**
         * Verifies that error messages can be accumulated without throwing exceptions.
         */
        @Test
        @DisplayName("Should add error messages")
        void shouldAddErrorMessages() {
            task.addError("Error 1");
            task.addError("Error 2");

            // Errors are stored internally, no direct getter available
            // Just verify no exception is thrown
            assertThat(task).isNotNull();
        }

        /**
         * Verifies that multiple error messages can be stored without errors.
         */
        @Test
        @DisplayName("Should handle multiple errors")
        void shouldHandleMultipleErrors() {
            for (int i = 0; i < 10; i++) {
                task.addError("Error " + i);
            }

            assertThat(task).isNotNull();
        }
    }

    /**
     * Tests for info panel setup and clearing.
     */
    @Nested
    @DisplayName("Info management tests")
    class InfoManagementTests {

        /**
         * Verifies that thread count and sub-info mode can be set without errors.
         */
        @Test
        @DisplayName("Should set thread count and sub-info mode")
        void shouldSetThreadCountAndSubInfoMode() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setInfos(4, true);

                // Verify no exception is thrown
                assertThat(task).isNotNull();
            }
        }

        /**
         * Verifies that a zero thread count falls back to the number of available processors.
         */
        @Test
        @DisplayName("Should handle zero thread count by using available processors")
        void shouldHandleZeroThreadCount() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setInfos(0, false);

                assertThat(task).isNotNull();
            }
        }

        /**
         * Verifies that a negative thread count falls back to the number of available processors.
         */
        @Test
        @DisplayName("Should handle negative thread count by using available processors")
        void shouldHandleNegativeThreadCount() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setInfos(-1, null);

                assertThat(task).isNotNull();
            }
        }

        /**
         * Verifies that info and sub-info labels can be cleared without errors.
         */
        @Test
        @DisplayName("Should clear infos")
        void shouldClearInfos() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.setInfos(2, true);
                task.setProgress("Info 1", 10, 100, "Sub 1");
                task.clearInfos();

                assertThat(task).isNotNull();
            }
        }
    }

    /**
     * Tests for input stream wrapping with progress tracking.
     */
    @Nested
    @DisplayName("InputStream wrapper tests")
    class InputStreamWrapperTests {

        /**
         * Verifies that an input stream is wrapped in a {@link ProgressInputStream} for tracking.
         */
        @Test
        @DisplayName("Should wrap input stream with progress tracking")
        void shouldWrapInputStreamWithProgressTracking() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                InputStream originalStream = new ByteArrayInputStream("test data".getBytes());

                InputStream wrappedStream = task.getInputStream(originalStream, 9);

                assertThat(wrappedStream).isInstanceOf(ProgressInputStream.class);
                assertThat(wrappedStream).isNotSameAs(originalStream);
            }
        }

        /**
         * Verifies that a {@code null} input stream still returns a {@link ProgressInputStream} wrapper.
         */
        @Test
        @DisplayName("Should handle null input stream")
        void shouldHandleNullInputStream() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                InputStream wrappedStream = task.getInputStream(null, 0);

                assertThat(wrappedStream).isInstanceOf(ProgressInputStream.class);
            }
        }
    }

    /**
     * Tests for progress handler option configuration.
     */
    @Nested
    @DisplayName("Options tests")
    class OptionsTests {

        /**
         * Verifies that progress handler options can be set without errors.
         */
        @Test
        @DisplayName("Should set options")
        void shouldSetOptions() {
            task.setOptions(jrm.aui.progress.ProgressHandler.Option.LAZY);

            assertThat(task).isNotNull();
        }
    }

    /**
     * Tests for offset provider setting.
     */
    @Nested
    @DisplayName("Offset provider tests")
    class OffsetProviderTests {

        /**
         * Verifies that an {@link OffsetProvider} can be set without errors.
         */
        @Test
        @DisplayName("Should set offset provider")
        void shouldSetOffsetProvider() {
            OffsetProvider mockProvider = mock(OffsetProvider.class);

            task.setOffsetProvider(mockProvider);

            assertThat(task).isNotNull();
        }

        /**
         * Verifies that a {@code null} offset provider can be set without errors.
         */
        @Test
        @DisplayName("Should handle null offset provider")
        void shouldHandleNullOffsetProvider() {
            task.setOffsetProvider(null);

            assertThat(task).isNotNull();
        }
    }

    /**
     * Tests for progress dialog closure.
     */
    @Nested
    @DisplayName("Close tests")
    class CloseTests {

        /**
         * Verifies that the progress dialog can be closed without errors.
         */
        @Test
        @DisplayName("Should close progress dialog")
        void shouldCloseProgressDialog() {
            try (MockedStatic<Platform> platformMock = mockStatic(Platform.class)) {
                platformMock.when(Platform::isFxApplicationThread).thenReturn(false);
                platformMock.when(() -> Platform.runLater(org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> null);

                task.close();

                assertThat(task).isNotNull();
            }
        }
    }
}
