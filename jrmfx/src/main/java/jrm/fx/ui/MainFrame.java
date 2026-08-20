package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.misc.Settings;
import jrm.fx.ui.profile.ProfileViewer;
import jrm.fx.ui.profile.report.ReportFrame;
import jrm.fx.ui.progress.ProgressTask;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.profile.data.ExportMode;
import jrm.profile.data.SoftwareList;
import jrm.profile.manager.Export;
import jrm.profile.manager.Export.ExportType;
import jrm.security.Session;
import jrm.security.Sessions;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * JavaFX application entry point for JRomManager.
 * <p>
 * Owns the primary stage, loads the main FXML layout, manages global singletons
 * (report frame, profile viewer, icon cache) and installs the shutdown hook that
 * persists profile and user settings.
 *
 * @since 2.5
 */
@Accessors(chain = true)
public class MainFrame extends Application {
    /**
     * The main frame controller.
     *
     * @return the main frame controller
     */
    private static @Getter MainFrameController controller;

    /**
     * The report frame singleton.
     *
     * @param reportFrame the report frame
     * @return the report frame
     */
    private static @Getter @Setter ReportFrame reportFrame;

    /**
     * The profile viewer singleton.
     *
     * @param profileViewer the profile viewer
     * @return the profile viewer
     */
    private static @Getter @Setter ProfileViewer profileViewer;

    /**
     * The current user session.
     *
     * @return the current user session
     */
    private static @Getter Session session = Sessions.getSingleSession();

    /**
     * The JavaFX application instance.
     *
     * @return the application instance
     */
    private static @Getter Application application;

    /**
     * The primary scene.
     *
     * @param primaryScene the primary scene
     * @return the primary scene
     */
    private static @Getter @Setter JRMScene primaryScene;

    /**
     * Launches the JavaFX application.
     */
    public static void launch() {
        Application.launch();
    }

    /**
     * Initializes the primary stage, loads the main FXML, and shows the window.
     *
     * @param primaryStage the primary stage provided by the JavaFX runtime
     */
    @Override
    public void start(Stage primaryStage) {
        Log.info("Starting...");
        setApplication(this);

        JRMScene.setSheet(session.getUser().getSettings().getEnumProperty(JRMScene.ScenePrefs.style_sheet, JRMScene.StyleSheet.class));
        final var loading = new Loading();
        Platform.runLater(() -> {
            try {
                primaryStage.setOnCloseRequest(_ -> {
                    session.getUser().getSettings().setProperty("MainFrame.Bounds", Settings.toJson(primaryStage));
                    controller.getSettingsPanelController().scheduler.shutdown();
                });
                primaryStage.getIcons().add(getIcon("/jrm/resicons/rom.png"));
                primaryStage.setTitle(Messages.getString("MainFrame.Title") + " " + getVersion());
                setPrimaryScene(new JRMScene(loadMain()));
                primaryStage.setScene(primaryScene);
                setReportFrame(new ReportFrame(primaryStage));
                Settings.fromJson(session.getUser().getSettings().getProperty("MainFrame.Bounds", null), primaryStage);
                primaryStage.show();
            } catch (final URISyntaxException | IOException e) /* NOSONAR */ {
                Log.err("Error occurred while starting the application", e);
            }
            loading.hide();
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (Sessions.getSingleSession().getCurrProfile() != null)
                Sessions.getSingleSession().getCurrProfile().saveSettings();
            Sessions.getSingleSession().getUser().getSettings().saveSettings();
            Log.info("Shutdown");
        }));
    }

    /**
     * Re-applies the current style sheet to all open scenes (primary, report frame, profile viewer).
     */
    public static void applyCSS() {
        JRMScene.setSheet(session.getUser().getSettings().getEnumProperty(JRMScene.ScenePrefs.style_sheet, JRMScene.StyleSheet.class));
        getPrimaryScene().applySheet();
        if (reportFrame != null && reportFrame.getScene() instanceof JRMScene s)
            s.applySheet();
        if (profileViewer != null && profileViewer.getScene() instanceof JRMScene s)
            s.applySheet();
    }

    /**
     * Loads the main FXML layout and captures the controller.
     *
     * @return the root {@link TabPane}
     * @throws URISyntaxException if the FXML resource cannot be converted to a URI
     * @throws IOException        if the FXML cannot be loaded
     */
    private static synchronized TabPane loadMain() throws URISyntaxException, IOException {
        final var loader = new FXMLLoader(MainFrame.class.getResource("MainFrame.fxml").toURI().toURL(), Messages.getBundle());
        final var root = loader.<TabPane>load();
        controller = loader.getController();
        return root;
    }

    /** Cache of loaded icon images keyed by resource path. */
    private static final HashMap<String, Image> iconsCache = new HashMap<>();
    /** The module providing icon resources. */
    private static Optional<Module> iconsModule = ModuleLayer.boot().findModule("res.icons");

    /**
     * Returns a cached icon image for the given resource path.
     *
     * @param res the icon resource path
     * @return the loaded image, or {@code null} if not found
     */
    public static Image getIcon(String res) {
        synchronized (iconsCache) {
            Image image = iconsCache.get(res);
            if (image == null) {
                iconsModule.ifPresentOrElse(module -> {
                    try (final var in = module.getResourceAsStream(res)) {
                        if (in != null)
                            iconsCache.put(res, new Image(in));
                    } catch (Exception e) {
                        Log.err(e.getMessage(), e);
                    }
                }, () -> {
                    try (final var in = MainFrame.class.getResourceAsStream(res)) {
                        if (in != null)
                            iconsCache.put(res, new Image(in));
                    } catch (Exception e) {
                        Log.err(e.getMessage(), e);
                    }
                });
                image = iconsCache.get(res);
            }
            return image;
        }
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    private String getVersion() {
        String version = ""; //$NON-NLS-1$
        final var pkg = getClass().getPackage();
        if (pkg.getSpecificationVersion() != null) {
            version += pkg.getSpecificationVersion(); // $NON-NLS-1$
            if (pkg.getImplementationVersion() != null)
                version += "." + pkg.getImplementationVersion(); //$NON-NLS-1$
        }
        return version;
    }

    /**
     * Stores the application instance (thread-safe).
     *
     * @param application the application instance
     */
    private static synchronized void setApplication(Application application) {
        MainFrame.application = application;
    }

    /**
     * Opens a file-save dialog and exports the current profile in the background.
     *
     * @param owner      the owner window for the dialog
     * @param session    the current user session
     * @param type       the export format
     * @param modes      the export modes
     * @param selection  the software list selection, or {@code null}
     */
    public static void export(Window owner, final Session session, final ExportType type, final Set<ExportMode> modes, final SoftwareList selection) {
        final var chooser = new FileChooser();
        Optional.ofNullable(session.getUser().getSettings().getProperty("MainFrame.ChooseExeOrDatToExport", (String) null)).map(File::new).ifPresent(chooser::setInitialDirectory);
        chooser.setTitle(Messages.getString("ProfileViewer.ChooseDestinationFile"));
        final var fnef = new FileChooser.ExtensionFilter(Messages.getString("MainFrame.DatFile"), "xml", "dat"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        chooser.getExtensionFilters().add(fnef);
        chooser.setSelectedExtensionFilter(fnef);
        final var chosen = chooser.showSaveDialog(owner);
        if (chosen != null) {
            try {
                Thread.startVirtualThread(new ProgressTask<Void>((Stage) owner) {
                    @Override
                    protected Void call() throws Exception {
                        Export.export(session.getCurrProfile(), chosen, type, modes, selection, this);
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        close();
                    }

                    @Override
                    protected void failed() {
                        close();
                        if (getException() instanceof BreakException)
                            Dialogs.showAlert("Cancelled");
                        else {
                            Optional.ofNullable(getException().getCause()).ifPresentOrElse(cause -> {
                                Log.err(cause.getMessage(), cause);
                                Dialogs.showError(cause);
                            }, () -> {
                                Log.err(getException().getMessage(), getException());
                                Dialogs.showError(getException());
                            });
                        }
                    }
                });
            } catch (IOException | URISyntaxException e) {
                Log.err(e.getMessage(), e);
                Dialogs.showError(e);
            }
        }
    }
}
