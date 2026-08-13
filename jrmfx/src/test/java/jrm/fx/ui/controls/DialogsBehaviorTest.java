package jrm.fx.ui.controls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxRecordedStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavior tests for {@link Dialogs} alert configuration methods.
 * <p>
 * Tests the package-private configuration methods that set up alert properties
 * without calling showAndWait().
 *
 * @since 3.0.5
 */
@TestFxApplication(DialogsBehaviorTest.TestApp.class)
@DisplayName("Dialogs Behavior Tests")
class DialogsBehaviorTest {

    /**
     * Test application for JavaFX component tests.
     */
    public static class TestApp extends Application implements TestFxRecordedStage {
        private Stage primaryStage;

        @Override
        public void start(Stage primaryStage) {
            this.primaryStage = primaryStage;
            primaryStage.setScene(new Scene(new StackPane(), 400, 300));
            primaryStage.show();
        }

        @Override
        public Stage recordedStage() {
            return primaryStage;
        }
    }

    /**
     * Verifies that {@link Dialogs#configureErrorAlert} sets the title,
     * header text, and a VBox content containing the exception type and message (no stack frames).
     */
    @Test
    @DisplayName("Should configure error alert with exception details")
    void shouldConfigureErrorAlertWithExceptionDetails() {
        // Arrange
        Exception exception = new RuntimeException("Test error message");
        AtomicReference<String> titleRef = new AtomicReference<>();
        AtomicReference<String> headerRef = new AtomicReference<>();
        AtomicReference<VBox> vboxRef = new AtomicReference<>();
        AtomicReference<String> labelTextRef = new AtomicReference<>();
        AtomicReference<String> textAreaTextRef = new AtomicReference<>();

        // Act - Alert must be created on FX application thread
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            Dialogs.configureErrorAlert(alert, exception);

            // Capture data for assertion on test thread
            titleRef.set(alert.getTitle());
            headerRef.set(alert.getHeaderText());
            Object content = alert.getDialogPane().getContent();
            if (content instanceof VBox) {
                VBox vbox = (VBox) content;
                vboxRef.set(vbox);
                if (vbox.getChildren().size() >= 2) {
                    if (vbox.getChildren().get(0) instanceof Label label) {
                        labelTextRef.set(label.getText());
                    }
                    if (vbox.getChildren().get(1) instanceof TextArea textArea) {
                        textAreaTextRef.set(textArea.getText());
                    }
                }
            }
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure alert", e);
        }

        // Assert - on test thread
        assertThat(titleRef.get())
                .as("Error alert title should be 'Error'")
                .isEqualTo("Error");
        assertThat(headerRef.get())
                .as("Error alert header should contain exception message")
                .isEqualTo("Test error message");
        assertThat(vboxRef.get())
                .as("Error alert content should be a VBox with 2 children")
                .isNotNull()
                .satisfies(vbox -> assertThat(vbox.getChildren()).hasSize(2));
        assertThat(labelTextRef.get())
                .as("Label should say 'Details:'")
                .isEqualTo("Details:");
        assertThat(textAreaTextRef.get())
                .as("TextArea should contain exception type and message without stack frames")
                .isNotNull()
                .contains("RuntimeException")
                .contains("Test error message")
                .doesNotContain("\tat ");
    }

    /**
     * Verifies that {@link Dialogs#configureWarningAlert} sets the title to {@code "Warning"},
     * a null header, and the provided message as content text.
     */
    @Test
    @DisplayName("Should configure warning alert with message")
    void shouldConfigureWarningAlertWithMessage() {
        // Arrange
        String message = "This is a warning message";
        AtomicReference<String> titleRef = new AtomicReference<>();
        AtomicReference<String> headerRef = new AtomicReference<>();
        AtomicReference<String> contentRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.WARNING);
            Dialogs.configureWarningAlert(alert, message);
            titleRef.set(alert.getTitle());
            headerRef.set(alert.getHeaderText());
            contentRef.set(alert.getContentText());
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure warning alert", e);
        }

        // Assert
        assertThat(titleRef.get())
                .as("Warning alert title should be 'Warning'")
                .isEqualTo("Warning");
        assertThat(headerRef.get())
                .as("Warning alert header should be null")
                .isNull();
        assertThat(contentRef.get())
                .as("Warning alert content should match message")
                .isEqualTo(message);
    }

    /**
     * Verifies that {@link Dialogs#configureConfirmationAlert} sets both the title and the body message.
     */
    @Test
    @DisplayName("Should configure confirmation alert with title and message")
    void shouldConfigureConfirmationAlertWithTitleAndMessage() {
        // Arrange
        String title = "Confirm Action";
        String message = "Are you sure?";
        AtomicReference<String> titleRef = new AtomicReference<>();
        AtomicReference<String> headerRef = new AtomicReference<>();
        AtomicReference<String> contentRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Dialogs.configureConfirmationAlert(alert, title, message);
            titleRef.set(alert.getTitle());
            headerRef.set(alert.getHeaderText());
            contentRef.set(alert.getContentText());
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure confirmation alert", e);
        }

        // Assert
        assertThat(titleRef.get())
                .as("Confirmation alert title should match")
                .isEqualTo(title);
        assertThat(headerRef.get())
                .as("Confirmation alert header should be null")
                .isNull();
        assertThat(contentRef.get())
                .as("Confirmation alert content should match message")
                .isEqualTo(message);
    }

    /**
     * Verifies that {@link Dialogs#configureConfirmationAlert} replaces default buttons with
     * the provided custom button types.
     */
    @Test
    @DisplayName("Should configure confirmation alert with custom buttons")
    void shouldConfigureConfirmationAlertWithCustomButtons() {
        // Arrange
        String title = "Custom Buttons";
        String message = "Choose an option";
        ButtonType customButton1 = new ButtonType("Option 1");
        ButtonType customButton2 = new ButtonType("Option 2");
        AtomicReference<List<ButtonType>> buttonsRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Dialogs.configureConfirmationAlert(alert, title, message, customButton1, customButton2);
            buttonsRef.set(new ArrayList<>(alert.getButtonTypes()));
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure confirmation alert with custom buttons", e);
        }

        // Assert
        assertThat(buttonsRef.get())
                .as("Button types should contain exactly custom buttons")
                .containsExactly(customButton1, customButton2);
        assertThat(buttonsRef.get())
                .as("Button types should not contain default buttons")
                .doesNotContain(ButtonType.OK, ButtonType.CANCEL);
    }

    /**
     * Verifies that passing {@code null} for the buttons vararg falls back to the default
     * confirmation buttons (e.g., {@link ButtonType#OK}).
     */
    @Test
    @DisplayName("Should configure confirmation alert with null buttons (use defaults)")
    void shouldConfigureConfirmationAlertWithNullButtons() {
        // Arrange
        String title = "Default Buttons";
        String message = "Use default buttons";
        AtomicReference<List<ButtonType>> buttonsRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Dialogs.configureConfirmationAlert(alert, title, message, (ButtonType[]) null);
            buttonsRef.set(new ArrayList<>(alert.getButtonTypes()));
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure confirmation alert with null buttons", e);
        }

        // Assert
        assertThat(buttonsRef.get())
                .as("Button types should contain default buttons when null passed")
                .contains(ButtonType.OK);
    }

    /**
     * Verifies that passing an empty button array falls back to the default
     * confirmation buttons (e.g., {@link ButtonType#OK}).
     */
    @Test
    @DisplayName("Should configure confirmation alert with empty buttons array (use defaults)")
    void shouldConfigureConfirmationAlertWithEmptyButtonsArray() {
        // Arrange
        String title = "Empty Buttons";
        String message = "Empty array should use defaults";
        ButtonType[] emptyButtons = new ButtonType[0];
        AtomicReference<List<ButtonType>> buttonsRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Dialogs.configureConfirmationAlert(alert, title, message, emptyButtons);
            buttonsRef.set(new ArrayList<>(alert.getButtonTypes()));
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure confirmation alert with empty buttons", e);
        }

        // Assert
        assertThat(buttonsRef.get())
                .as("Button types should contain default buttons when empty array passed")
                .contains(ButtonType.OK);
    }

    /**
     * Verifies that {@link Dialogs#configureNodeConfirmationAlert} sets the custom
     * content node in the alert's dialog pane.
     */
    @Test
    @DisplayName("Should configure node confirmation alert with custom content")
    void shouldConfigureNodeConfirmationAlertWithCustomContent() {
        // Arrange
        String title = "Custom Content";
        AtomicReference<String> titleRef = new AtomicReference<>();
        AtomicReference<String> headerRef = new AtomicReference<>();
        AtomicReference<Object> contentRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Label customNode = new Label("Custom content node");
            Dialogs.configureNodeConfirmationAlert(alert, title, customNode);
            titleRef.set(alert.getTitle());
            headerRef.set(alert.getHeaderText());
            contentRef.set(alert.getDialogPane().getContent());
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure node confirmation alert", e);
        }

        // Assert
        assertThat(titleRef.get())
                .as("Node confirmation alert title should match")
                .isEqualTo(title);
        assertThat(headerRef.get())
                .as("Node confirmation alert header should be null")
                .isNull();
        assertThat(contentRef.get())
                .as("Node confirmation alert content should be the custom node")
                .isInstanceOf(Label.class)
                .extracting(obj -> ((Label) obj).getText())
                .isEqualTo("Custom content node");
    }

    /**
     * Verifies that {@link Dialogs#configureNodeConfirmationAlert} uses the provided
     * custom button types instead of the defaults.
     */
    @Test
    @DisplayName("Should configure node confirmation alert with custom buttons")
    void shouldConfigureNodeConfirmationAlertWithCustomButtons() {
        // Arrange
        String title = "Node Custom Buttons";
        ButtonType yesButton = ButtonType.YES;
        ButtonType noButton = ButtonType.NO;
        AtomicReference<List<ButtonType>> buttonsRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Label customNode = new Label("Content");
            Dialogs.configureNodeConfirmationAlert(alert, title, customNode, yesButton, noButton);
            buttonsRef.set(new ArrayList<>(alert.getButtonTypes()));
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure node confirmation alert with custom buttons", e);
        }

        // Assert
        assertThat(buttonsRef.get())
                .as("Node confirmation should contain custom buttons")
                .containsExactly(yesButton, noButton);
    }

    /**
     * Verifies that passing {@code null} for the buttons vararg in a node confirmation alert
     * falls back to the default button set.
     */
    @Test
    @DisplayName("Should configure node confirmation alert with null buttons (use defaults)")
    void shouldConfigureNodeConfirmationAlertWithNullButtons() {
        // Arrange
        String title = "Node Default Buttons";
        AtomicReference<List<ButtonType>> buttonsRef = new AtomicReference<>();

        // Act
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            Label customNode = new Label("Content");
            Dialogs.configureNodeConfirmationAlert(alert, title, customNode, (ButtonType[]) null);
            buttonsRef.set(new ArrayList<>(alert.getButtonTypes()));
            future.complete(null);
        });
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Failed to configure node confirmation alert with null buttons", e);
        }

        // Assert
        assertThat(buttonsRef.get())
                .as("Node confirmation should use default buttons when null passed")
                .contains(ButtonType.OK);
    }
}
