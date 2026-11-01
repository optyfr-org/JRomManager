package jrm.fx.ui;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.batch.DirUpdaterResults;
import jrm.fx.ui.misc.Settings;
import jrm.locale.Messages;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * Modal dialog displaying batch directory update results.
 * <p>
 * This dialog is modal and will block until the user closes it. The results are
 * passed to the FXML controller, which displays them in a table view. The dialog
 * can be resized and moved by the user, and its position and size are saved in the
 * user's settings. If no results are provided, the dialog will display an empty
 * table view.
 * 
 * @see jrm.batch.DirUpdaterResults
 * 
 * @since 2.5
 */
class BatchDirUpdaterResultsStage extends Stage {
    /** The FXML controller. */
    private final BatchDirUpd8rResultsController controller;

    /** The current user session. */
    private final Session session;

    /**
     * Constructs and shows the batch directory update results dialog.
     *
     * @param parent the parent stage
     * @param results the results to display, or {@code null}
     * 
     * @throws URISyntaxException if the FXML resource cannot be converted to a URI
     * @throws IOException if the FXML cannot be loaded
     */
    public BatchDirUpdaterResultsStage(Stage parent, DirUpdaterResults results) throws URISyntaxException, IOException {
        this(requireOwner(parent), requireFxmlUrl(), results);
    }

    private BatchDirUpdaterResultsStage(Stage parent, URL fxmlUrl, DirUpdaterResults results) throws URISyntaxException, IOException {
        super();
        session = Sessions.getSingleSession();
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        if (!parent.getIcons().isEmpty())
            getIcons().add(parent.getIcons().get(0));
        setOnShowing(_ -> Settings.fromJson(session.getUser().getSettings().getProperty("BatchDirUpd8rResults.Bounds", null), this));
        setOnCloseRequest(_ -> {
            session.getUser().getSettings().setProperty("BatchDirUpd8rResults.Bounds", Settings.toJson(this));
            hide();
        });
        final var loader = new FXMLLoader(fxmlUrl.toURI().toURL(), Messages.getBundle());
        final var root = loader.<BorderPane>load();
        controller = loader.getController();
        if (results != null && results.getResults() != null)
            controller.getResultList().getItems().setAll(results.getResults());
        setScene(new JRMScene(root));
        sizeToScene();
        show();
    }

    private static Stage requireOwner(final Stage parent) {
        return Objects.requireNonNull(parent, "parent");
    }

    private static URL requireFxmlUrl() throws IOException {
        final var resource = BatchDirUpdaterResultsStage.class.getResource("BatchDirUpd8rResults.fxml");
        if (resource == null)
            throw new IOException("Missing resource: BatchDirUpd8rResults.fxml");
        return resource;
    }
}
