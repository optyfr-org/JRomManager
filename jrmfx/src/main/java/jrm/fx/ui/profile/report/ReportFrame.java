package jrm.fx.ui.profile.report;

import java.io.IOException;
import java.net.URISyntaxException;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.aui.progress.StatusHandler;
import jrm.fx.ui.JRMScene;
import jrm.fx.ui.misc.Settings;
import jrm.fx.ui.status.NeutralToNodeFormatter;
import jrm.locale.Messages;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * A non-modal stage for displaying the full scan report.
 * <p>
 * Implements {@link StatusHandler} to receive status updates from the scan process.
 * Persists window state and updates the view when the report changes.
 *
 * @since 2.5
 */
public class ReportFrame extends Stage implements StatusHandler {
    /** The report frame controller. */
    private ReportFrameController controller;

    /** The current user session. */
    private Session session;

    /**
     * Constructs and shows the report frame.
     *
     * @param parent the parent stage
     * @throws IOException        if the FXML cannot be loaded
     * @throws URISyntaxException if the FXML resource URI is invalid
     */
    public ReportFrame(Stage parent) throws IOException, URISyntaxException {
        super();
        session = Sessions.getSingleSession();
        initOwner(parent);
        initModality(Modality.NONE);
        getIcons().add(parent.getIcons().get(0));
        setOnShowing(_ -> Settings.fromJson(session.getUser().getSettings().getProperty("ReportFrame.Bounds", null), this));
        setOnCloseRequest(_ -> {
            session.getUser().getSettings().setProperty("ReportFrame.Bounds", Settings.toJson(this));
            hide();
        });
        final var resource = ReportFrame.class.getResource("ReportFrame.fxml");
        if (resource == null)
            throw new IOException("Missing resource: ReportFrame.fxml");
        final var loader = new FXMLLoader(resource.toURI().toURL(), Messages.getBundle());
        final var root = loader.<BorderPane>load();
        controller = loader.getController();
        setScene(new JRMScene(root));
        session.getReport().setStatusHandler(this);
    }

    /** The current status message. */
    private String status = "";

    /**
     * Sets the status message.
     *
     * @param text the status text
     */
    @Override
    public void setStatus(String text) {
        status = text;
    }

    /** Whether the view needs updating. */
    private boolean needUpdate = false;

    /**
     * Marks the view as needing an update.
     *
     * @param needUpdate {@code true} to trigger an update
     */
    public void setNeedUpdate(boolean needUpdate) {
        this.needUpdate = needUpdate;
        if (isShowing())
            update();
    }

    /**
     * Updates the view if needed.
     */
    private void update() {
        if (needUpdate) {
            controller.viewController.setReport(Sessions.getSingleSession().getReport());
            controller.status.getChildren().setAll(NeutralToNodeFormatter.toNodes(status));
            needUpdate = false;
        }
    }

    /**
     * Shows the report frame and updates the view.
     */
    public void setVisible() {
        update();
        super.show();
    }
}
