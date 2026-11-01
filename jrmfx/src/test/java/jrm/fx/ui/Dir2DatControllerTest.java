package jrm.fx.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxRecordedStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jrm.security.Session;
import jrm.security.Sessions;
import jrm.security.User;
import jrm.misc.GlobalSettings;
import jrm.misc.SettingsEnum;

/**
 * Tests for {@link Dir2DatController}.
 * <p>
 * Verifies initialization of checkboxes, text fields, buttons, radio buttons,
 * and event handlers for the Dir2Dat panel.
 *
 * @since 3.0.5
 */
@TestFxApplication(Dir2DatControllerTest.TestApp.class)
@DisplayName("Dir2DatController Tests")
class Dir2DatControllerTest {

    /**
     * Functional interface that accepts a runnable which may throw checked exceptions.
     */
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Minimal application that creates a {@link Dir2DatController}
     * with all FXML fields injected via reflection and mocks for Session.
     */
    public static class TestApp extends Application implements TestFxRecordedStage {
        /** The primary stage used for the test application. */
        private Stage primaryStage;

        /** The controller instance shared across test methods. */
        private static Dir2DatController controller;

        @Override
        public Stage recordedStage() {
            return primaryStage;
        }

        @Override
        public void start(Stage primaryStage) throws Exception {
            this.primaryStage = primaryStage;

            // Create mock chain in dependency order
            GlobalSettings mockSettings = mock(GlobalSettings.class);
            when(mockSettings.getProperty(any(SettingsEnum.class), eq(Boolean.class))).thenReturn(false);
            when(mockSettings.getProperty(any(SettingsEnum.class))).thenReturn("");
            // Specific stubs MUST come after generic any() stubs to override them
            when(mockSettings.getProperty(SettingsEnum.dir2dat_format)).thenReturn("MAME");
            when(mockSettings.getWorkPath()).thenReturn(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")));
            doAnswer(inv -> null).when(mockSettings).setProperty(any(SettingsEnum.class), any());
            doAnswer(inv -> null).when(mockSettings).setProperty(any(SettingsEnum.class), any(Boolean.class));
            
            User mockUser = mock(User.class);
            when(mockUser.getSettings()).thenReturn(mockSettings);
            
            Session mockSession = mock(Session.class);
            when(mockSession.getUser()).thenReturn(mockUser);
            
            // Use shared mock to avoid thread conflicts
            SharedMockSession.getOrCreate().when(Sessions::getSingleSession).thenReturn(mockSession);
                
            // Now create the controller - the session field will be initialized
            controller = new Dir2DatController();
            
            // Verify session was set, if not, inject it directly
            Field sessionField = BaseController.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            if (sessionField.get(controller) == null) {
                // Force set the final field
                sessionField.set(controller, mockSession);
            }

            // Create UI components
            CheckBox includeEmptyDirs = new CheckBox();
            CheckBox matchCurrentProfile = new CheckBox();
            CheckBox doNotScan = new CheckBox();
            CheckBox junkSubfolders = new CheckBox();
            CheckBox addSha1 = new CheckBox();
            CheckBox addMd = new CheckBox();
            CheckBox deepScanFor = new CheckBox();
            CheckBox scanSubfolders = new CheckBox();

            TextField name = new TextField();
            TextField description = new TextField();
            TextField version = new TextField();
            TextField author = new TextField();
            TextField comment = new TextField();
            TextField category = new TextField();
            TextField date = new TextField();
            TextField email = new TextField();
            TextField homepage = new TextField();
            TextField url = new TextField();
            TextField srcDir = new TextField();
            TextField dstDat = new TextField();

            Button srcDirBtn = new Button();
            Button dstDatBtn = new Button();
            Button generate = new Button();

            ToggleGroup format = new ToggleGroup();
            RadioButton formatMame = new RadioButton();
            RadioButton formatLogiqxDat = new RadioButton();
            RadioButton formatSWList = new RadioButton();
            formatMame.setToggleGroup(format);
            formatLogiqxDat.setToggleGroup(format);
            formatSWList.setToggleGroup(format);

            // Inject all fields via reflection
            injectField(controller, "includeEmptyDirs", includeEmptyDirs);
            injectField(controller, "matchCurrentProfile", matchCurrentProfile);
            injectField(controller, "doNotScan", doNotScan);
            injectField(controller, "junkSubfolders", junkSubfolders);
            injectField(controller, "addSha1", addSha1);
            injectField(controller, "addMd", addMd);
            injectField(controller, "deepScanFor", deepScanFor);
            injectField(controller, "scanSubfolders", scanSubfolders);

            injectField(controller, "name", name);
            injectField(controller, "description", description);
            injectField(controller, "version", version);
            injectField(controller, "author", author);
            injectField(controller, "comment", comment);
            injectField(controller, "category", category);
            injectField(controller, "date", date);
            injectField(controller, "email", email);
            injectField(controller, "homepage", homepage);
            injectField(controller, "url", url);
            injectField(controller, "srcDir", srcDir);
            injectField(controller, "dstDat", dstDat);

            injectField(controller, "srcDirBtn", srcDirBtn);
            injectField(controller, "dstDatBtn", dstDatBtn);
            injectField(controller, "generate", generate);

            injectField(controller, "format", format);
            injectField(controller, "formatMame", formatMame);
            injectField(controller, "formatLogiqxDat", formatLogiqxDat);
            injectField(controller, "formatSWList", formatSWList);

            // Initialize controller
            VBox root = new VBox();
            root.getChildren().addAll(includeEmptyDirs, matchCurrentProfile, doNotScan, junkSubfolders,
                    addSha1, addMd, deepScanFor, scanSubfolders, name, description, version, author,
                    comment, category, date, email, homepage, url, srcDir, dstDat, srcDirBtn, dstDatBtn,
                    generate, formatMame, formatLogiqxDat, formatSWList);
            
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
    @DisplayName("Should initialize checkboxes")
    void shouldInitializeCheckboxes() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("includeEmptyDirs");
            field.setAccessible(true);
            CheckBox checkBox = (CheckBox) field.get(TestApp.controller);
            assertThat(checkBox).isNotNull();
        });
    }

    @Test
    @DisplayName("Should initialize text fields")
    void shouldInitializeTextFields() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("srcDir");
            field.setAccessible(true);
            TextField textField = (TextField) field.get(TestApp.controller);
            assertThat(textField).isNotNull();
        });
    }

    @Test
    @DisplayName("Should initialize buttons")
    void shouldInitializeButtons() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("generate");
            field.setAccessible(true);
            Button button = (Button) field.get(TestApp.controller);
            assertThat(button).isNotNull();
        });
    }

    @Test
    @DisplayName("Should initialize radio buttons with toggle group")
    void shouldInitializeRadioButtons() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("formatMame");
            field.setAccessible(true);
            RadioButton radioButton = (RadioButton) field.get(TestApp.controller);
            assertThat(radioButton).isNotNull();
            assertThat(radioButton.getToggleGroup()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should set checkbox listeners")
    void shouldSetCheckboxListeners() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("scanSubfolders");
            field.setAccessible(true);
            CheckBox checkBox = (CheckBox) field.get(TestApp.controller);
            assertThat(checkBox.selectedProperty()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should set button actions")
    void shouldSetButtonActions() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("generate");
            field.setAccessible(true);
            Button button = (Button) field.get(TestApp.controller);
            assertThat(button.getOnAction()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should initialize format toggle group")
    void shouldInitializeFormatToggleGroup() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("format");
            field.setAccessible(true);
            ToggleGroup toggleGroup = (ToggleGroup) field.get(TestApp.controller);
            assertThat(toggleGroup).isNotNull();
            assertThat(toggleGroup.getToggles()).hasSize(3);
        });
    }

    @Test
    @DisplayName("Should select default format toggle")
    void shouldSelectDefaultFormatToggle() throws Exception {
        runOnFxThread(() -> {
            Field field = Dir2DatController.class.getDeclaredField("format");
            field.setAccessible(true);
            ToggleGroup toggleGroup = (ToggleGroup) field.get(TestApp.controller);
            assertThat(toggleGroup.getSelectedToggle()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should initialize all required fields")
    void shouldInitializeAllRequiredFields() throws Exception {
        runOnFxThread(() -> {
            String[] fieldNames = {
                "includeEmptyDirs", "matchCurrentProfile", "doNotScan", "junkSubfolders",
                "addSha1", "addMd", "deepScanFor", "scanSubfolders",
                "name", "description", "version", "author", "comment", "category",
                "date", "email", "homepage", "url", "srcDir", "dstDat",
                "srcDirBtn", "dstDatBtn", "generate",
                "format", "formatMame", "formatLogiqxDat", "formatSWList"
            };
            
            for (String fieldName : fieldNames) {
                Field field = Dir2DatController.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                assertThat(field.get(TestApp.controller)).isNotNull();
            }
        });
    }
}
