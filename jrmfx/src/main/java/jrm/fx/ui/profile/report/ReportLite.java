package jrm.fx.ui.profile.report;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.fx.ui.JRMScene;
import jrm.fx.ui.misc.Settings;
import jrm.locale.Messages;
import jrm.profile.report.Report;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * A non-modal stage for displaying a lightweight report view.
 * <p>
 * Shows a simplified report without status updates, suitable for viewing
 * completed scan results.
 *
 * @since 2.5
 */
public class ReportLite extends Stage {
    /** The report lite controller. */
    private final ReportLiteController controller;

    /** The current user session. */
    private final Session session;

    /**
     * Constructs and shows the lightweight report.
     *
     * @param parent the parent stage
     * @param report the report to display
     * @throws IOException        if the FXML cannot be loaded
     * @throws URISyntaxException if the FXML resource URI is invalid
     */
    public ReportLite(Stage parent, Report report) throws IOException, URISyntaxException {
        this(requireOwner(parent), requireFxmlUrl(), report);
    }

    private ReportLite(Stage parent, URL fxmlUrl, Report report) throws IOException, URISyntaxException {
        super();
        session = Sessions.getSingleSession();
        initOwner(parent);
        initModality(Modality.NONE);
        if (!parent.getIcons().isEmpty())
            getIcons().add(parent.getIcons().get(0));
        setOnShowing(_ -> Settings.fromJson(session.getUser().getSettings().getProperty("ReportLite.Bounds", null), this));
        setOnCloseRequest(_ -> {
            session.getUser().getSettings().setProperty("ReportLite.Bounds", Settings.toJson(this));
            hide();
        });
        final var loader = new FXMLLoader(fxmlUrl.toURI().toURL(), Messages.getBundle());
        final var root = loader.<BorderPane>load();
        controller = loader.getController();
        setScene(new JRMScene(root));
        controller.viewController.setReport(report);
        show();
    }

    private static Stage requireOwner(final Stage parent) {
        return Objects.requireNonNull(parent, "parent");
    }

    private static URL requireFxmlUrl() throws IOException {
        final var resource = ReportLite.class.getResource("ReportLite.fxml");
        if (resource == null)
            throw new IOException("Missing resource: ReportLite.fxml");
        return resource;
    }
}
