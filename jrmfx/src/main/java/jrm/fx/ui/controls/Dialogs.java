package jrm.fx.ui.controls;

import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jrm.fx.ui.JRMScene;
import jrm.fx.ui.MainFrame;
import jrm.misc.Log;
import lombok.experimental.UtilityClass;

/**
 * Utility class for displaying modal dialogs (error, warning, confirmation).
 * <p>
 * All dialogs are styled with the application icon and the current {@link JRMScene} style sheet.
 *
 * @since 2.5
 */
public @UtilityClass class Dialogs {
    /** The application icon resource path. */
    private static final String ICO = "/jrm/resicons/rom.png";

    /**
     * Shows an error dialog with the exception type, message, and cause chain (no stack frames).
     *
     * @param e the exception to display
     */
    public static void showError(Throwable e) {
        final var alert = new Alert(AlertType.ERROR);
        configureErrorAlert(alert, e);
        alert.showAndWait();
    }

    /**
     * Configures an error alert with exception details.
     * Package-private for testing.
     *
     * @param alert the alert to configure
     * @param e the exception to display
     */
    static void configureErrorAlert(Alert alert, Throwable e) {
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(MainFrame.getIcon(ICO));
        JRMScene.applySheet(alert.getDialogPane().getScene());
        alert.setTitle("Error");
        alert.setHeaderText(e.getMessage());

        final var dialogPaneContent = new VBox();

        final var label = new Label("Details:");

        final var details = formatException(e);
        TextArea textArea = new TextArea();
        textArea.setText(details);
        textArea.setEditable(false);

        dialogPaneContent.getChildren().addAll(label, textArea);

        alert.getDialogPane().setContent(dialogPaneContent);
    }

    /**
     * Shows a warning dialog with the given message.
     *
     * @param message the message to display
     */
    public static void showAlert(String message) {
        Alert alert = new Alert(AlertType.WARNING);
        configureWarningAlert(alert, message);
        alert.showAndWait();
    }

    /**
     * Configures a warning alert with the given message.
     * Package-private for testing.
     *
     * @param alert the alert to configure
     * @param message the message to display
     */
    static void configureWarningAlert(Alert alert, String message) {
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(MainFrame.getIcon(ICO));
        JRMScene.applySheet(alert.getDialogPane().getScene());
        alert.setTitle("Warning");

        // Header Text: null
        alert.setHeaderText(null);
        alert.setContentText(message);
    }

    /**
     * Shows a confirmation dialog with the given title and message.
     *
     * @param title   the dialog title
     * @param message the message to display
     * @param buttons the button types to show, or {@code null} for defaults
     * @return the selected button type
     */
    public static Optional<ButtonType> showConfirmation(String title, String message, ButtonType... buttons) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        configureConfirmationAlert(alert, title, message, buttons);
        return alert.showAndWait();
    }

    /**
     * Configures a confirmation alert with title, message, and buttons.
     * Package-private for testing.
     *
     * @param alert   the alert to configure
     * @param title   the dialog title
     * @param message the message to display
     * @param buttons the button types to show, or {@code null} for defaults
     */
    static void configureConfirmationAlert(Alert alert, String title, String message, ButtonType... buttons) {
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(MainFrame.getIcon(ICO));
        JRMScene.applySheet(alert.getDialogPane().getScene());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (buttons != null && buttons.length > 0) {
            alert.getButtonTypes().clear();
            alert.getButtonTypes().addAll(buttons);
        }
    }

    /**
     * Shows a confirmation dialog with the given title and custom node content.
     *
     * @param title   the dialog title
     * @param message the custom node to display
     * @param buttons the button types to show, or {@code null} for defaults
     * @return the selected button type
     */
    public static Optional<ButtonType> showConfirmation(String title, Node message, ButtonType... buttons) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        configureNodeConfirmationAlert(alert, title, message, buttons);
        return alert.showAndWait();
    }

    /**
     * Configures a confirmation alert with custom node content.
     * Package-private for testing.
     *
     * @param alert   the alert to configure
     * @param title   the dialog title
     * @param message the custom node to display
     * @param buttons the button types to show, or {@code null} for defaults
     */
    static void configureNodeConfirmationAlert(Alert alert, String title, Node message, ButtonType... buttons) {
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(MainFrame.getIcon(ICO));
        JRMScene.applySheet(alert.getDialogPane().getScene());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(message);
        if (buttons != null && buttons.length > 0) {
            alert.getButtonTypes().clear();
            alert.getButtonTypes().addAll(buttons);
        }
    }

    /**
     * Formats an exception for display: type, message, and cause chain without stack frames.
     *
     * @param e the exception
     * @return the formatted exception summary
     */
    private static String formatException(Throwable e) {
        return Log.formatThrowable(e);
    }
}
