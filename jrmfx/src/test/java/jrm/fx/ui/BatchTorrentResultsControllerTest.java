package jrm.fx.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxRecordedStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jrm.batch.TrntChkReport;
import jrm.batch.TrntChkReport.Child;
import jrm.security.Session;
import jrm.security.Sessions;
import jrm.security.User;
import jrm.misc.GlobalSettings;

/**
 * Tests for {@link BatchTorrentResultsController}.
 * <p>
 * Verifies initialization, status color mapping, filtering operations,
 * and expand/collapse functionality.
 *
 * @since 3.0.5
 */
@TestFxApplication(BatchTorrentResultsControllerTest.TestApp.class)
@DisplayName("BatchTorrentResultsController Tests")
class BatchTorrentResultsControllerTest {

    /**
     * Functional interface that accepts a runnable which may throw checked exceptions.
     */
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Minimal application that creates a {@link BatchTorrentResultsController}
     * with all FXML fields injected via reflection.
     */
    public static class TestApp extends Application implements TestFxRecordedStage {
        private Stage primaryStage;
        static BatchTorrentResultsController controller;

        @Override
        public Stage recordedStage() {
            return primaryStage;
        }

        @Override
        public void start(Stage primaryStage) throws Exception {
            this.primaryStage = primaryStage;

            // Set up static mock for Sessions using shared instance
            GlobalSettings mockSettings = mock(GlobalSettings.class);
            User mockUser = mock(User.class);
            when(mockUser.getSettings()).thenReturn(mockSettings);
            Session mockSession = mock(Session.class);
            when(mockSession.getUser()).thenReturn(mockUser);
            SharedMockSession.getOrCreate().when(Sessions::getSingleSession).thenReturn(mockSession);

            // Create controller
            controller = new BatchTorrentResultsController();

            // Create UI components
            TreeView<Child> treeview = new TreeView<>();
            ContextMenu menu = new ContextMenu();
            MenuItem openAllNodes = new MenuItem("Open All");
            MenuItem closeAllNodes = new MenuItem("Close All");
            CheckMenuItem showok = new CheckMenuItem("Show OK");
            CheckMenuItem hidemissing = new CheckMenuItem("Hide Missing");

            // Inject fields via reflection
            injectField(controller, "treeview", treeview);
            injectField(controller, "menu", menu);
            injectField(controller, "openAllNodes", openAllNodes);
            injectField(controller, "closeAllNodes", closeAllNodes);
            injectField(controller, "showok", showok);
            injectField(controller, "hidemissing", hidemissing);

            // Initialize controller
            VBox root = new VBox(treeview);
            controller.initialize(null, null);

            Scene scene = new Scene(root, 800, 600);
            primaryStage.setScene(scene);
            primaryStage.show();
        }

        /** Injects a value into a private field of the target object using reflection. */
        private static void injectField(Object target, String fieldName, Object value) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject field: " + fieldName, e);
            }
        }

        /**
         * Helper method to run operations on the JavaFX Application Thread and wait for completion.
         */
        public static void runOnFxThread(ThrowingRunnable operation) throws Exception {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    operation.run();
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            future.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void stop() {
            SharedMockSession.close();
        }
    }

    /**
     * Helper to run code on JavaFX thread and wait for completion.
     */
    private void runOnFxThread(ThrowingRunnable action) throws Exception {
        TestApp.runOnFxThread(action);
    }

    @Test
    @DisplayName("Should initialize controller without errors")
    void shouldInitializeController() throws Exception {
        runOnFxThread(() -> {
            assertThat(TestApp.controller).isNotNull();
        });
    }

    @Test
    @DisplayName("Should return green color for OK status")
    void shouldReturnGreenForOkStatus() throws Exception {
        runOnFxThread(() -> {
            String result = TestApp.controller.statusColor(TrntChkReport.Status.OK);
            assertThat(result).isEqualTo("_green");
        });
    }

    @Test
    @DisplayName("Should return red color for MISSING status")
    void shouldReturnRedForMissingStatus() throws Exception {
        runOnFxThread(() -> {
            String result = TestApp.controller.statusColor(TrntChkReport.Status.MISSING);
            assertThat(result).isEqualTo("_red");
        });
    }

    @Test
    @DisplayName("Should return purple color for SHA1 status")
    void shouldReturnPurpleForSha1Status() throws Exception {
        runOnFxThread(() -> {
            String result = TestApp.controller.statusColor(TrntChkReport.Status.SHA1);
            assertThat(result).isEqualTo("_purple");
        });
    }

    @Test
    @DisplayName("Should return blue color for SIZE status")
    void shouldReturnBlueForSizeStatus() throws Exception {
        runOnFxThread(() -> {
            String result = TestApp.controller.statusColor(TrntChkReport.Status.SIZE);
            assertThat(result).isEqualTo("_blue");
        });
    }

    @Test
    @DisplayName("Should return orange color for SKIPPED status")
    void shouldReturnOrangeForSkippedStatus() throws Exception {
        runOnFxThread(() -> {
            String result = TestApp.controller.statusColor(TrntChkReport.Status.SKIPPED);
            assertThat(result).isEqualTo("_orange");
        });
    }

    @Test
    @DisplayName("Should return gray color for UNKNOWN status")
    void shouldReturnGrayForUnknownStatus() throws Exception {
        runOnFxThread(() -> {
            String result = TestApp.controller.statusColor(TrntChkReport.Status.UNKNOWN);
            assertThat(result).isEqualTo("_gray");
        });
    }

    @Test
    @DisplayName("Should set report and build tree")
    void shouldSetReportAndBuildTree() throws Exception {
        runOnFxThread(() -> {
            TrntChkReport report = mock(TrntChkReport.class);
            List<Child> mockChildren = new ArrayList<>();
            when(report.filter(any())).thenReturn(mockChildren);

            TestApp.controller.setResult(report);

            assertThat(TestApp.controller.getTreeview().getRoot()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should toggle SHOWOK filter option")
    void shouldToggleShowokFilter() throws Exception {
        runOnFxThread(() -> {
            TrntChkReport report = mock(TrntChkReport.class);
            when(report.filter(any())).thenReturn(new ArrayList<>());
            TestApp.controller.setResult(report);

            try {
                Field showokField = BatchTorrentResultsController.class.getDeclaredField("showok");
                showokField.setAccessible(true);
                CheckMenuItem showok = (CheckMenuItem) showokField.get(TestApp.controller);

                showok.setSelected(true);
                Method showokMethod = BatchTorrentResultsController.class.getDeclaredMethod("showok", ActionEvent.class);
                showokMethod.setAccessible(true);
                showokMethod.invoke(TestApp.controller, new ActionEvent());

                showok.setSelected(false);
                showokMethod.invoke(TestApp.controller, new ActionEvent());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertThat(TestApp.controller.getTreeview().getRoot()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should toggle HIDEMISSING filter option")
    void shouldToggleHidemissingFilter() throws Exception {
        runOnFxThread(() -> {
            TrntChkReport report = mock(TrntChkReport.class);
            when(report.filter(any())).thenReturn(new ArrayList<>());
            TestApp.controller.setResult(report);

            try {
                Field hidemissingField = BatchTorrentResultsController.class.getDeclaredField("hidemissing");
                hidemissingField.setAccessible(true);
                CheckMenuItem hidemissing = (CheckMenuItem) hidemissingField.get(TestApp.controller);

                hidemissing.setSelected(true);
                Method hidemissingMethod = BatchTorrentResultsController.class.getDeclaredMethod("hidemissing", ActionEvent.class);
                hidemissingMethod.setAccessible(true);
                hidemissingMethod.invoke(TestApp.controller, new ActionEvent());

                hidemissing.setSelected(false);
                hidemissingMethod.invoke(TestApp.controller, new ActionEvent());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertThat(TestApp.controller.getTreeview().getRoot()).isNotNull();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should expand all non-leaf nodes")
    void shouldExpandAllNonLeafNodes() throws Exception {
        runOnFxThread(() -> {
            try {
                TrntChkReport report = mock(TrntChkReport.class);
                when(report.filter(any())).thenReturn(new ArrayList<>());
                TestApp.controller.setResult(report);

                TreeView<Child> treeview = TestApp.controller.getTreeview();
                TreeItem<Child> root = new TreeItem<>();
                TreeItem<Child> child1 = new TreeItem<>();
                TreeItem<Child> child2 = new TreeItem<>();
                TreeItem<Child> grandchild = new TreeItem<>();

                child1.getChildren().add(grandchild);
                root.getChildren().addAll(child1, child2);
                treeview.setRoot(root);
                child1.setExpanded(false);

                Method openAllNodesMethod = BatchTorrentResultsController.class.getDeclaredMethod("openAllNodes", ActionEvent.class);
                openAllNodesMethod.setAccessible(true);
                openAllNodesMethod.invoke(TestApp.controller, new ActionEvent());

                assertThat(child1.isExpanded()).isTrue();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should collapse all non-leaf nodes")
    void shouldCollapseAllNonLeafNodes() throws Exception {
        runOnFxThread(() -> {
            try {
                TrntChkReport report = mock(TrntChkReport.class);
                when(report.filter(any())).thenReturn(new ArrayList<>());
                TestApp.controller.setResult(report);

                TreeView<Child> treeview = TestApp.controller.getTreeview();
                TreeItem<Child> root = new TreeItem<>();
                TreeItem<Child> child1 = new TreeItem<>();
                TreeItem<Child> child2 = new TreeItem<>();
                TreeItem<Child> grandchild = new TreeItem<>();

                child1.getChildren().add(grandchild);
                root.getChildren().addAll(child1, child2);
                treeview.setRoot(root);
                child1.setExpanded(true);

                Method closeAllNodesMethod = BatchTorrentResultsController.class.getDeclaredMethod("closeAllNodes", ActionEvent.class);
                closeAllNodesMethod.setAccessible(true);
                closeAllNodesMethod.invoke(TestApp.controller, new ActionEvent());

                assertThat(child1.isExpanded()).isFalse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Should build a chain up to the maximum tree depth")
    void shouldBuildTreeUpToMaximumDepth() throws Exception {
        runOnFxThread(() -> {
            final var report = mock(TrntChkReport.class);
            when(report.filter(any())).thenReturn(List.of(nestedChildren(BatchTorrentResultsController.MAX_TREE_DEPTH)));
            TestApp.controller.setResult(report);
            assertThat(treeDepth(TestApp.controller.getTreeview().getRoot())).isEqualTo(BatchTorrentResultsController.MAX_TREE_DEPTH);
        });
    }

    @Test
    @DisplayName("Should stop building past the maximum tree depth")
    void shouldStopBuildingPastMaximumDepth() throws Exception {
        runOnFxThread(() -> {
            final var report = mock(TrntChkReport.class);
            when(report.filter(any())).thenReturn(List.of(nestedChildren(BatchTorrentResultsController.MAX_TREE_DEPTH + 5)));
            TestApp.controller.setResult(report);
            assertThat(treeDepth(TestApp.controller.getTreeview().getRoot())).isEqualTo(BatchTorrentResultsController.MAX_TREE_DEPTH);
        });
    }

    @Test
    @DisplayName("Should ignore cyclic child graphs when building the tree")
    void shouldIgnoreCyclicChildGraphs() throws Exception {
        runOnFxThread(() -> {
            final var first = newReport().new Child();
            final var second = newReport().new Child();
            setChildren(first, List.of(second));
            setChildren(second, List.of(first));
            final var report = mock(TrntChkReport.class);
            when(report.filter(any())).thenReturn(List.of(first));
            TestApp.controller.setResult(report);
            assertThat(treeDepth(TestApp.controller.getTreeview().getRoot())).isEqualTo(2);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should expand a deep tree without overflowing")
    void shouldExpandDeepTreeWithoutOverflowing() throws Exception {
        runOnFxThread(() -> {
            try {
                final var root = deepTreeItems(BatchTorrentResultsController.MAX_TREE_DEPTH + 5);
                TestApp.controller.getTreeview().setRoot(root);
                final var openAllNodesMethod = BatchTorrentResultsController.class.getDeclaredMethod("openAllNodes", ActionEvent.class);
                openAllNodesMethod.setAccessible(true);
                openAllNodesMethod.invoke(TestApp.controller, new ActionEvent());
                assertThat(root.isExpanded()).isTrue();
                assertThat(root.getChildren().get(0).isExpanded()).isTrue();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Should close window on OK action")
    void shouldCloseWindowOnOkAction() throws Exception {
        runOnFxThread(() -> {
            try {
                Stage stage = (Stage) TestApp.controller.getTreeview().getScene().getWindow();
                Method onOkMethod = BatchTorrentResultsController.class.getDeclaredMethod("onOK", ActionEvent.class);
                onOkMethod.setAccessible(true);
                onOkMethod.invoke(TestApp.controller, new ActionEvent());
                assertThat(stage.isShowing()).isFalse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static TrntChkReport newReport() {
        return new TrntChkReport(new java.io.File("dummy"));
    }

    private static Child nestedChildren(final int depth) {
        final var report = newReport();
        Child root = report.new Child();
        Child current = root;
        for (var i = 1; i < depth; i++) {
            final var next = report.new Child();
            setChildren(current, List.of(next));
            current = next;
        }
        return root;
    }

    private static void setChildren(final Child node, final List<Child> children) {
        try {
            final var field = Child.class.getDeclaredField("children");
            field.setAccessible(true);
            field.set(node, children);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int treeDepth(final TreeItem<?> node) {
        var depth = 0;
        var current = node;
        while (current != null && !current.getChildren().isEmpty()) {
            current = current.getChildren().get(0);
            depth++;
        }
        return depth;
    }

    private static TreeItem<Child> deepTreeItems(final int depth) {
        final var root = new TreeItem<Child>();
        var current = root;
        for (var i = 0; i < depth; i++) {
            final var child = new TreeItem<Child>();
            current.getChildren().add(child);
            current = child;
        }
        return root;
    }
}
