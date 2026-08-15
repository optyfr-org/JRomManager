package jrm.fx.ui.profile.report;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.Stage;

/**
 * FXML controller for the lightweight report view.
 * <p>
 * Embeds a {@link ReportViewController} and provides a close button handler.
 *
 * @since 2.5
 */
public class ReportLiteController implements Initializable {
    /** The embedded report view controller. */
    @FXML
    ReportViewController viewController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // no-op
    }

    /**
     * Closes the report window.
     *
     * @param e the action event
     */
    @FXML
    private void onClose(ActionEvent e) {
        ((Stage) viewController.treeview.getScene().getWindow()).close();
    }

}
