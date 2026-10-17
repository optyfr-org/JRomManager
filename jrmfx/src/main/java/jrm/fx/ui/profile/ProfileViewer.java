package jrm.fx.ui.profile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.fx.ui.JRMScene;
import jrm.fx.ui.MainFrame;
import jrm.fx.ui.misc.Settings;
import jrm.locale.Messages;
import jrm.profile.Profile;
import jrm.security.Session;
import jrm.security.Sessions;
import lombok.Getter;

/**
 * A non-modal stage for viewing and managing profile details.
 * <p>
 * Loads the ProfileViewer FXML layout and provides methods to clear, reload,
 * and reset the profile view. Includes a timer-based reset mechanism for
 * handling profile changes.
 *
 * @since 2.5
 */
public class ProfileViewer extends Stage {
    /** The profile viewer controller. */
    private ProfileViewerController controller;

    /** The current user session. */
    private Session session;

    /**
     * The currently viewed profile.
     * @return the currently viewed profile
     */
    @Getter
    private Profile profile;

    /**
     * Counter for pending reset requests.
     * @return the reset counter
     */
    @Getter
    private static final AtomicInteger resetCounter = new AtomicInteger();

    /** Timer for processing reset requests. */
    private Timer resetTimer = null;

    /**
     * Constructs and shows the profile viewer.
     *
     * @param parent the parent stage
     * @throws IOException        if the FXML cannot be loaded
     * @throws URISyntaxException if the FXML resource URI is invalid
     */
    public ProfileViewer(Stage parent) throws IOException, URISyntaxException {
        super();
        session = Sessions.getSingleSession();
        initOwner(parent);
        initModality(Modality.NONE);
        getIcons().add(parent.getIcons().get(0));
        setOnShowing(_ -> Settings.fromJson(session.getUser().getSettings().getProperty("ProfileViewer.Bounds", null), this));
        setOnCloseRequest(_ -> {
            session.getUser().getSettings().setProperty("ProfileViewer.Bounds", Settings.toJson(this));
            hide();
        });
        final var resource = ProfileViewer.class.getResource("ProfileViewer.fxml");
        if (resource == null)
            throw new IOException("Missing resource: ProfileViewer.fxml");
        final var loader = new FXMLLoader(resource.toURI().toURL(), Messages.getBundle());
        final var root = loader.<JRMScene>load();
        controller = loader.getController();
        setScene(root);
        resetCounter.incrementAndGet();

        resetTimer = new Timer(true);
        resetTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (resetCounter.get() > 0) {
                    Platform.runLater(() -> {
                        if (MainFrame.getProfileViewer() != null) {
                            resetCounter.set(0);
                            MainFrame.getProfileViewer().reset(session.getCurrProfile());
                        }
                    });

                }
            }
        }, 0, 1000);
    }

    /**
     * Clears the profile viewer.
     */
    public void clear() {
        controller.clear();
    }

    /**
     * Reloads the profile viewer.
     */
    public void reload() {
        controller.reload();
    }

    /**
     * Resets the profile viewer with a new profile.
     *
     * @param profile the profile to display
     */
    public void reset(Profile profile) {
        this.profile = profile;
        controller.reset(profile);
    }
}
