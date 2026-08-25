package jrm.fx.ui.progress;

import java.io.IOException;
import java.net.URISyntaxException;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.fx.ui.JRMScene;
import jrm.locale.Messages;
import lombok.Getter;

/**
 * A modal progress dialog backed by a {@link ProgressTask}.
 * <p>
 * Loads the Progress FXML layout, wires it to a {@link ProgressController},
 * and displays the dialog as a window-modal stage.
 *
 * @since 2.5
 */
public class Progress extends Stage {
    /**
     * The progress controller.
     *
     * @return the progress controller
     */
    private @Getter ProgressController controller;

    /**
     * Constructs and shows a progress dialog.
     *
     * @param parent the parent stage
     * @param task   the progress task to track
     * @throws IOException        if the FXML cannot be loaded
     * @throws URISyntaxException if the FXML resource URI is invalid
     */
    public Progress(Stage parent, ProgressTask<?> task) throws IOException, URISyntaxException {
        super();
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        getIcons().add(parent.getIcons().get(0));
        setOnCloseRequest(_ -> hide());
        final var loader = new FXMLLoader(getClass().getResource("Progress.fxml").toURI().toURL(), Messages.getBundle());
        final var root = loader.<GridPane>load();
        controller = loader.getController();
        controller.setTask(task);
        setScene(new JRMScene(root));
        sizeToScene();
        show();
    }
}
