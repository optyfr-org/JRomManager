package jrm.fx.ui.profile.filter;

import java.io.IOException;
import java.net.URISyntaxException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.fx.ui.misc.Settings;
import jrm.fx.ui.profile.ProfileViewer;
import jrm.locale.Messages;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.filter.Keywords.KFCallBack;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * A modal dialog for managing keyword filters.
 * <p>
 * Allows users to add, remove, and reorder keywords that filter the profile view.
 * Keywords can be dragged between available and used lists.
 *
 * @since 2.5
 */
public class Keywords extends Stage {

    /** The keywords controller. */
    private KeywordsController controller;
    /** The current user session. */
    private Session session;

    /**
     * Constructs and shows the keywords dialog.
     *
     * @param parent   the parent stage
     * @param keywords the initial keywords
     * @param awlist   the anyware list to filter
     * @param callback the callback to invoke when keywords change
     * @throws URISyntaxException if the FXML resource URI is invalid
     * @throws IOException        if the FXML cannot be loaded
     */
    public Keywords(ProfileViewer parent, String[] keywords, AnywareList<? extends Anyware> awlist, KFCallBack callback) throws URISyntaxException, IOException {
        super();
        session = Sessions.getSingleSession();
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        getIcons().add(parent.getIcons().get(0));
        setOnShowing(_ -> Settings.fromJson(session.getUser().getSettings().getProperty("Keywords.Bounds", null), this));
        setOnCloseRequest(_ -> controller.onClose());
        final var resource = Keywords.class.getResource("Keywords.fxml");
        if (resource == null)
            throw new IOException("Missing resource: Keywords.fxml");
        final var loader = new FXMLLoader(resource.toURI().toURL(), Messages.getBundle());
        final var root = loader.<Scene>load();
        controller = loader.getController();
        controller.initKeywords(keywords);
        controller.callback = callback;
        controller.awlist = awlist;
        setScene(root);
        showAndWait();
    }
}
