package jrm.fx.ui.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxRecordedStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jrm.fx.ui.progress.ProgressTask.PData;

/**
 * Tests for {@link ProgressController}.
 * <p>
 * Verifies initialization, progress bar management, info panel creation,
 * and cancellation behavior.
 *
 * @since 3.0.5
 */
@TestFxApplication(ProgressControllerTest.TestApp.class)
@DisplayName("ProgressController Tests")
class ProgressControllerTest {

    /**
     * Minimal application that creates a {@link ProgressController}
     * with all FXML fields injected via reflection and mocks for MainFrame.
     */
    public static class TestApp extends Application implements TestFxRecordedStage {
        /** The primary stage for the test application. */
        private Stage primaryStage;
        /** The shared {@link ProgressController} instance under test. */
        private static ProgressController controller;

        /**
         * Starts the test application by constructing a {@link ProgressController},
         * injecting FXML fields via reflection, and setting up a scene.
         *
         * @param primaryStage the primary stage for this application
         * @throws Exception if field injection fails
         */
        @Override
        public void start(Stage primaryStage) throws Exception {
            this.primaryStage = primaryStage;

            controller = new ProgressController();

            // Create GridPane parents for timeleft labels with row constraints
            GridPane gridPane1 = new GridPane();
            gridPane1.getRowConstraints().add(new RowConstraints());
            gridPane1.getRowConstraints().add(new RowConstraints());
            gridPane1.getRowConstraints().add(new RowConstraints());
            gridPane1.getRowConstraints().add(new RowConstraints());

            GridPane gridPane2 = new GridPane();
            gridPane2.getRowConstraints().add(new RowConstraints());
            gridPane2.getRowConstraints().add(new RowConstraints());
            gridPane2.getRowConstraints().add(new RowConstraints());
            gridPane2.getRowConstraints().add(new RowConstraints());

            GridPane gridPane3 = new GridPane();
            gridPane3.getRowConstraints().add(new RowConstraints());
            gridPane3.getRowConstraints().add(new RowConstraints());
            gridPane3.getRowConstraints().add(new RowConstraints());
            gridPane3.getRowConstraints().add(new RowConstraints());

            // Create UI components
            VBox panel = new VBox();
            ProgressBar progressBar = new ProgressBar();
            Label progressBarLbl = new Label();
            Label lblTimeleft = new Label();
            ProgressBar progressBar2 = new ProgressBar();
            Label progressBarLbl2 = new Label();
            Label lblTimeleft2 = new Label();
            ProgressBar progressBar3 = new ProgressBar();
            Label progressBarLbl3 = new Label();
            Label lblTimeleft3 = new Label();
            Button cancelBtn = new Button("Cancel");

            // Add labels to GridPanes to set parent relationship
            gridPane1.getChildren().add(lblTimeleft);
            gridPane2.getChildren().add(lblTimeleft2);
            gridPane3.getChildren().add(lblTimeleft3);

            // Inject fields via reflection
            injectField(controller, "panel", panel);
            injectField(controller, "progressBar", progressBar);
            injectField(controller, "progressBarLbl", progressBarLbl);
            injectField(controller, "lblTimeleft", lblTimeleft);
            injectField(controller, "progressBar2", progressBar2);
            injectField(controller, "progressBarLbl2", progressBarLbl2);
            injectField(controller, "lblTimeleft2", lblTimeleft2);
            injectField(controller, "progressBar3", progressBar3);
            injectField(controller, "progressBarLbl3", progressBarLbl3);
            injectField(controller, "lblTimeleft3", lblTimeleft3);
            injectField(controller, "cancelBtn", cancelBtn);

            controller.initialize(null, null);

            // Setup scene
            Scene scene = new Scene(panel, 800, 600);
            primaryStage.setScene(scene);
            primaryStage.show();
        }

        /**
         * Injects a value into a declared field of the given target object via reflection.
         *
         * @param target the object whose field is to be injected
         * @param fieldName the name of the declared field
         * @param value the value to set on the field
         * @throws Exception if the field is not found or cannot be accessed
         */
        private static void injectField(Object target, String fieldName, Object value) throws Exception {
            Field field = ProgressController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }

        /**
         * Retrieves the value of a declared field from the controller.
         *
         * @param <T> the expected type of the field value
         * @param fieldName the name of the declared field
         * @return the field value cast to {@code T}
         * @throws RuntimeException wrapping the original exception if reflection fails
         */
        @SuppressWarnings("unchecked")
        public <T> T getField(String fieldName) {
            try {
                Field field = ProgressController.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T) field.get(controller);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get field " + fieldName, e);
            }
        }

        /**
         * Injects a value into a declared field on the shared controller.
         *
         * @param fieldName the name of the declared field
         * @param value the value to set on the field
         * @throws RuntimeException wrapping the original exception if injection fails
         */
        public void injectFieldOnController(String fieldName, Object value) {
            try {
                injectField(controller, fieldName, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject field " + fieldName, e);
            }
        }

        /**
         * Returns the shared controller instance.
         *
         * @return the {@link ProgressController} under test
         */
        public static ProgressController getController() {
            return controller;
        }

        /**
         * Helper method to run operations on the JavaFX Application Thread and wait for completion.
         *
         * @param operation the runnable to execute on the FX thread
         * @throws Exception if the operation throws or the future is interrupted
         */
        public static void runOnFxThread(Runnable operation) throws Exception {
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

        /**
         * Returns the recorded stage for TestFX integration.
         *
         * @return the primary stage
         */
        @Override
        public Stage recordedStage() {
            return primaryStage;
        }

        @Override
        public void stop() {
            // No cleanup needed
        }
    }

    /**
     * Verifies that the cancel button has a graphic icon after initialization.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should initialize with cancel button icon")
    void shouldInitializeWithCancelButtonIcon(TestApp application) throws Exception {
        Button cancelBtn = application.getField("cancelBtn");
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            future.complete(cancelBtn.getGraphic() != null);
        });
        assertThat(future.get(5, TimeUnit.SECONDS)).as("cancel button has icon").isTrue();
    }

    /**
     * Verifies that secondary and tertiary progress bars and labels are hidden after initialization.
     *
     * @param application the test application fixture
     */
    @Test
    @DisplayName("Should initialize with secondary and tertiary progress bars hidden")
    void shouldInitializeWithSecondaryAndTertiaryProgressBarsHidden(TestApp application) {
        ProgressBar progressBar2 = application.getField("progressBar2");
        Label progressBarLbl2 = application.getField("progressBarLbl2");
        Label lblTimeleft2 = application.getField("lblTimeleft2");
        ProgressBar progressBar3 = application.getField("progressBar3");
        Label progressBarLbl3 = application.getField("progressBarLbl3");
        Label lblTimeleft3 = application.getField("lblTimeleft3");

        assertThat(progressBar2.isVisible()).as("progressBar2 visible").isFalse();
        assertThat(progressBarLbl2.isVisible()).as("progressBarLbl2 visible").isFalse();
        assertThat(lblTimeleft2.isVisible()).as("lblTimeleft2 visible").isFalse();
        assertThat(progressBar3.isVisible()).as("progressBar3 visible").isFalse();
        assertThat(progressBarLbl3.isVisible()).as("progressBarLbl3 visible").isFalse();
        assertThat(lblTimeleft3.isVisible()).as("lblTimeleft3 visible").isFalse();
    }

    /**
     * Verifies that {@link ProgressController#setInfos} creates a single info and sub-info pane for one thread.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should set infos for single thread")
    void shouldSetInfosForSingleThread(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(1, false);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        Pane[] lblSubInfo = application.getField("lblSubInfo");

        assertThat(lblInfo).as("lblInfo").hasSize(1);
        assertThat(lblSubInfo).as("lblSubInfo").hasSize(1);
    }

    /**
     * Verifies that {@link ProgressController#setInfos} creates info panes for multiple threads.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should set infos for multiple threads")
    void shouldSetInfosForMultipleThreads(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(4, false);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        Pane[] lblSubInfo = application.getField("lblSubInfo");

        assertThat(lblInfo).as("lblInfo").hasSize(4);
        assertThat(lblSubInfo).as("lblSubInfo").hasSize(1);
    }

    /**
     * Verifies that {@link ProgressController#setInfos} with {@code multipleSubInfos=true}
     * creates matching numbers of info and sub-info panes.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should set infos with multiple sub-infos")
    void shouldSetInfosWithMultipleSubInfos(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(3, true);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        Pane[] lblSubInfo = application.getField("lblSubInfo");

        assertThat(lblInfo).as("lblInfo").hasSize(3);
        assertThat(lblSubInfo).as("lblSubInfo").hasSize(3);
        assertThat(lblSubInfo[0].getParent()).as("sub-info beside info").isSameAs(lblInfo[0].getParent());
        assertThat(lblInfo[0].getParent()).isInstanceOf(HBox.class);
    }

    /**
     * Verifies that {@link ProgressController#extendInfos} increases the info pane count to the requested size.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should extend infos to more threads")
    void shouldExtendInfosToMoreThreads(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(2, false);
            controller.extendInfos(5, false);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        assertThat(lblInfo).as("lblInfo").hasSize(5);
    }

    /**
     * Verifies that {@link ProgressController#extendInfos} does not resize when the requested thread count equals the current count.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should not extend infos if already same thread count")
    void shouldNotExtendInfosIfAlreadySameThreadCount(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(3, false);
        });
        
        Pane[] lblInfoBefore = application.getField("lblInfo");
        int lengthBefore = lblInfoBefore.length;

        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.extendInfos(3, false);
        });
        
        Pane[] lblInfoAfter = application.getField("lblInfo");
        assertThat(lblInfoAfter).as("lblInfo length unchanged").hasSize(lengthBefore);
    }

    /**
     * Verifies that {@link ProgressController#clearInfos} removes children from all info and sub-info panes.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should clear infos")
    void shouldClearInfos(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(2, false);
            controller.clearInfos();
        });

        Pane[] lblInfo = application.getField("lblInfo");
        Pane[] lblSubInfo = application.getField("lblSubInfo");

        assertThat(lblInfo[0].getChildren()).as("lblInfo[0] children").isEmpty();
        assertThat(lblInfo[1].getChildren()).as("lblInfo[1] children").isEmpty();
        assertThat(lblSubInfo[0].getChildren()).as("lblSubInfo[0] children").isEmpty();
    }

    /**
     * Verifies that {@link ProgressController#canCancel} enables and disables the cancel button.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should can cancel")
    void shouldCanCancel(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.canCancel(false);
        });
        Button cancelBtn = application.getField("cancelBtn");
        assertThat(cancelBtn.isDisable()).as("cancelBtn disabled").isTrue();

        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.canCancel(true);
        });
        assertThat(cancelBtn.isDisable()).as("cancelBtn enabled").isFalse();
    }

    /**
     * Verifies that {@link ProgressController#doCancel} disables the cancel button after invoking cancel.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should do cancel")
    void shouldDoCancel(TestApp application) throws Exception {
        ProgressTask<?> task = mock(ProgressTask.class);
        application.injectFieldOnController("task", task);

        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.doCancel();
        });

        Button cancelBtn = application.getField("cancelBtn");
        assertThat(cancelBtn.isDisable()).as("cancelBtn disabled after cancel").isTrue();
    }

    /**
     * Verifies that an odd thread count produces alternating background colors on info panels.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should handle odd thread count for alternating colors")
    void shouldHandleOddThreadCountForAlternatingColors(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(5, false);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        assertThat(lblInfo).as("lblInfo").hasSize(5);
        
        // Verify all panels have backgrounds set
        for (int i = 0; i < lblInfo.length; i++) {
            assertThat(lblInfo[i].getBackground()).as("lblInfo[" + i + "] background").isNotNull();
        }
    }

    /**
     * Verifies that two {@link ProgressController#setFullProgress} calls with the same
     * info strings leave pane children unchanged.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should skip unchanged info node rebuilds")
    void shouldSkipUnchangedInfoNodeRebuilds(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(1, false);
            PData pd = new PData();
            pd.setInfos(new String[] { "same" });
            pd.setSubinfos(new String[] { "sub" });
            controller.setFullProgress(pd);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        Node firstChild = lblInfo[0].getChildren().get(0);
        int childCount = lblInfo[0].getChildren().size();

        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            PData pd = new PData();
            pd.setInfos(new String[] { "same" });
            pd.setSubinfos(new String[] { "sub" });
            controller.setFullProgress(pd);
        });

        assertThat(lblInfo[0].getChildren()).as("child count unchanged").hasSize(childCount);
        assertThat(lblInfo[0].getChildren().get(0)).as("child identity unchanged").isSameAs(firstChild);
    }

    /**
     * Verifies that changing a plain info string updates the existing {@link Label}
     * instead of replacing pane children.
     *
     * @param application the test application fixture
     * @throws Exception if the JavaFX operation fails
     */
    @Test
    @DisplayName("Should update plain info labels in place")
    void shouldUpdatePlainInfoLabelsInPlace(TestApp application) throws Exception {
        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            controller.setInfos(1, false);
            PData pd = new PData();
            pd.setInfos(new String[] { "file-a.bin" });
            pd.setSubinfos(new String[] { "sub" });
            controller.setFullProgress(pd);
        });

        Pane[] lblInfo = application.getField("lblInfo");
        Node firstChild = lblInfo[0].getChildren().get(0);

        TestApp.runOnFxThread(() -> {
            ProgressController controller = TestApp.getController();
            PData pd = new PData();
            pd.setInfos(new String[] { "file-b.bin" });
            pd.setSubinfos(new String[] { "sub" });
            controller.setFullProgress(pd);
        });

        assertThat(lblInfo[0].getChildren().get(0)).as("child identity unchanged").isSameAs(firstChild);
        assertThat(((Label) firstChild).getText()).isEqualTo("file-b.bin");
    }
}
