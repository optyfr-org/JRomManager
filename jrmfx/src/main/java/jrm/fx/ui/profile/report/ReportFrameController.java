package jrm.fx.ui.profile.report;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.HBox;

/**
 * FXML controller for the full report frame.
 * <p>
 * Embeds a {@link ReportViewController} and a status bar for displaying
 * scan progress messages.
 *
 * @since 2.5
 */
public class ReportFrameController implements Initializable {
    /** The embedded report view controller. */
    @FXML
    ReportViewController viewController;
    /** The status bar container. */
    @FXML
    HBox status;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // no-op
    }

}
