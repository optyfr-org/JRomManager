package jrm.fx.ui;

import java.io.IOException;
import java.net.URISyntaxException;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.batch.TrntChkReport;
import jrm.fx.ui.misc.Settings;
import jrm.locale.Messages;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * Modal dialog displaying batch torrent check results.
 *
 * @since 2.5
 */
class BatchTorrentResults extends Stage {
    /** The FXML controller. */
    private BatchTorrentResultsController controller;

    /** The current user session. */
    private Session session;

    /**
     * Constructs and shows the batch torrent check results dialog.
     *
     * @param parent  the parent stage
     * @param results the results to display, or {@code null}
     * @throws URISyntaxException if the FXML resource cannot be converted to a URI
     * @throws IOException        if the FXML cannot be loaded
     */
    public BatchTorrentResults(Stage parent, TrntChkReport results) throws URISyntaxException, IOException {
        super();
        session = Sessions.getSingleSession();
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        getIcons().add(parent.getIcons().get(0));
        setOnShowing(_ -> Settings.fromJson(session.getUser().getSettings().getProperty("BatchTorrentResults.Bounds", null), this));
        setOnCloseRequest(_ -> {
            session.getUser().getSettings().setProperty("BatchTorrentResults.Bounds", Settings.toJson(this));
            hide();
        });
        final var resource = BatchTorrentResults.class.getResource("BatchTorrentResults.fxml");
        if (resource == null)
            throw new IOException("Missing resource: BatchTorrentResults.fxml");
        final var loader = new FXMLLoader(resource.toURI().toURL(), Messages.getBundle());
        final var root = loader.<BorderPane>load();
        controller = loader.getController();

        if (results != null)
            controller.setResult(results);

        setScene(new JRMScene(root));
        sizeToScene();
        show();
    }
}
