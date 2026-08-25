package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;

import javafx.application.Platform;
import javafx.beans.value.ObservableValueBase;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import jrm.fx.ui.controls.DateCellFactory;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.controls.ProfileCellFactory;
import jrm.fx.ui.controls.VersionCellFactory;
import jrm.fx.ui.misc.DragNDrop;
import jrm.fx.ui.profile.manager.DirItem;
import jrm.fx.ui.profile.manager.HaveNTotalCellFactory;
import jrm.fx.ui.progress.ProgressTask;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.profile.manager.Dir;
import jrm.profile.manager.Import;
import jrm.profile.manager.MameExecutable;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.manager.ProfileNFOMame.MameStatus;
import jrm.profile.manager.ProfileNFOStats.HaveNTotal;
import jrm.security.Session;
import jrm.security.Sessions;
import lombok.AllArgsConstructor;
import lombok.Setter;

/**
 * JavaFX controller for the profile management panel.
 * <p>
 * Backs the FXML view that lets the user browse the profile directory tree,
 * import DAT files (optionally from a MAME executable or software list),
 * and perform per-profile operations such as loading, renaming, deleting,
 * dropping the cache, and updating from MAME.
 *
 * @author optyfr
 * @since 2.5
 */
public class ProfilePanelController implements Initializable {
    /** Button used to load the currently selected profile. */
    @FXML
    Button btnLoad;
    /** Button used to import a DAT file. */
    @FXML
    Button btnImportDat;
    /** Button used to import a MAME software list. */
    @FXML
    Button btnImportSL;
    /** Tree view showing the profile directory hierarchy. */
    @FXML
    TreeView<Dir> profilesTree;
    /** Table view listing the profiles contained in the selected directory. */
    @FXML
    TableView<ProfileNFO> profilesList;
    /** Table column displaying the profile name (editable). */
    @FXML
    TableColumn<ProfileNFO, ProfileNFO> profileCol;
    /** Table column displaying the profile version. */
    @FXML
    TableColumn<ProfileNFO, String> profileVersionCol;
    /** Table column displaying the have/total sets count. */
    @FXML
    TableColumn<ProfileNFO, HaveNTotal> profileHaveSetsCol;
    /** Table column displaying the have/total ROMs count. */
    @FXML
    TableColumn<ProfileNFO, HaveNTotal> profileHaveRomsCol;
    /** Table column displaying the have/total disks count. */
    @FXML
    TableColumn<ProfileNFO, HaveNTotal> profileHaveDisksCol;
    /** Table column displaying the profile creation date. */
    @FXML
    TableColumn<ProfileNFO, Instant> profileCreatedCol;
    /** Table column displaying the date of the last profile scan. */
    @FXML
    TableColumn<ProfileNFO, Instant> profileLastScanCol;
    /** Table column displaying the date of the last profile fix. */
    @FXML
    TableColumn<ProfileNFO, Instant> profileLastFixCol;
    /** Menu item for creating a new profile folder. */
    @FXML
    MenuItem createFolderMenu;
    /** Menu item for deleting the selected profile folder. */
    @FXML
    MenuItem deleteFolderMenu;
    /** Menu item for deleting the selected profile. */
    @FXML
    MenuItem deleteProfileMenu;
    /** Menu item for renaming the selected profile. */
    @FXML
    MenuItem renameProfileMenu;
    /** Menu item for dropping the selected profile cache. */
    @FXML
    MenuItem dropCacheMenu;
    /** Menu item for updating the selected profile from MAME. */
    @FXML
    MenuItem updateFromMameMenu;
    /** Context menu shown for profile folder actions. */
    @FXML
    ContextMenu folderMenu;
    /** Context menu shown for profile actions. */
    @FXML
    ContextMenu profileMenu;

    /** The current user session. */
    final Session session = Sessions.getSingleSession();

    /**
     * Callback used to load a selected profile.
     * @param profileLoader the callback used to load a selected profile
     */
    private @Setter ProfileLoader profileLoader;

    /** Null-safe comparator ordering {@code Long} values with {@code null} first. */
    private static Comparator<Long> nullSafeLongComparator = Comparator.nullsFirst(Long::compareTo);

    /** Comparator for {@link HaveNTotal} values based on their have counts. */
    private static Comparator<HaveNTotal> haveNTotalComparator = Comparator
            .comparing(HaveNTotal::getHave, nullSafeLongComparator)
            .thenComparing(HaveNTotal::getHave, nullSafeLongComparator);

    /**
     * Initializes the controller: wires up button and menu icons, configures the
     * profile tree and table, sets up cell factories, value factories, comparators,
     * selection listeners, and drag-and-drop import support.
     *
     * @param location the location used to resolve relative paths for the root object, or {@code null} if unknown
     * @param resources the resources used to localize the root object, or {@code null} if not localized
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ImageView add = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/add.png"));
        add.setPreserveRatio(true);
        add.getStyleClass().add("icon");
        btnLoad.setGraphic(add);
        ImageView go = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/script_go.png"));
        go.setPreserveRatio(true);
        go.getStyleClass().add("icon");
        btnImportDat.setGraphic(go);
        ImageView appgo = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/application_go.png"));
        appgo.setPreserveRatio(true);
        appgo.getStyleClass().add("icon");
        btnImportSL.setGraphic(appgo);
        ImageView fadd = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/folder_add.png"));
        fadd.setPreserveRatio(true);
        fadd.getStyleClass().add("icon");
        createFolderMenu.setGraphic(fadd);
        ImageView fdel = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/folder_delete.png"));
        fdel.setPreserveRatio(true);
        fdel.getStyleClass().add("icon");
        deleteFolderMenu.setGraphic(fdel);
        ImageView sdel = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/script_delete.png"));
        sdel.setPreserveRatio(true);
        sdel.getStyleClass().add("icon");
        deleteProfileMenu.setGraphic(sdel);
        ImageView sedit = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/script_edit.png"));
        sedit.setPreserveRatio(true);
        sedit.getStyleClass().add("icon");
        renameProfileMenu.setGraphic(sedit);
        ImageView bin = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bin.png"));
        bin.setPreserveRatio(true);
        bin.getStyleClass().add("icon");
        dropCacheMenu.setGraphic(bin);
        folderMenu.setOnShowing(_ -> updateFolderMenuState());
        profileMenu.setOnShowing(_ -> updateMenuStates());
        profilesTree.setCellFactory(_ -> createProfileTreeCell());
        profilesTree.setOnEditCommit(this::editCommitProfileDir);
        profilesTree.setRoot(new DirItem(session.getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize().toFile()));
        profilesTree.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> populate(newValue));
        profilesTree.getSelectionModel().select(0);
        profileCol.setEditable(true);
        profileCol.setCellFactory(_ -> new ProfileCellFactory());
        profileCol.setOnEditCommit(this::editCommitProfile);
        profileCol.setCellValueFactory(this::getProfileNFOValue);
        profileVersionCol.setCellFactory(_ -> new VersionCellFactory<>());
        profileVersionCol.setCellValueFactory(this::getProfileVersion);
        profileHaveSetsCol.setCellFactory(_ -> new HaveNTotalCellFactory<>());
        profileHaveSetsCol.setCellValueFactory(this::getProfileHaveSets);
        profileHaveSetsCol.setComparator(haveNTotalComparator);
        profileHaveRomsCol.setCellFactory(_ -> new HaveNTotalCellFactory<>());
        profileHaveRomsCol.setCellValueFactory(this::getProfileHaveRoms);
        profileHaveRomsCol.setComparator(haveNTotalComparator);
        profileHaveDisksCol.setCellFactory(_ -> new HaveNTotalCellFactory<>());
        profileHaveDisksCol.setCellValueFactory(this::getProfileHaveDisks);
        profileHaveDisksCol.setComparator(haveNTotalComparator);
        profileCreatedCol.setCellFactory(_ -> new DateCellFactory());
        profileCreatedCol.setCellValueFactory(this::getProfileCreated);
        profileLastScanCol.setCellFactory(_ -> new DateCellFactory());
        profileLastScanCol.setCellValueFactory(this::getProfileLastScan);
        profileLastFixCol.setCellFactory(_ -> new DateCellFactory());
        profileLastFixCol.setCellValueFactory(this::getProfileLastFix);
        profilesList.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> btnLoad.setDisable(newValue == null));
        profilesList.setRowFactory(_ -> createProfileRow());
        profilesList.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> profilesList.refresh());
        profilesList.setEditable(false);
        profilesList.setFixedCellSize(Region.USE_COMPUTED_SIZE);
        new DragNDrop(profilesList).addAny(files -> importDat(files, true));
    }

    /** Creates a row for the profiles list.
     * @return a row for the profiles list
     */
    private TableRow<ProfileNFO> createProfileRow() {
        final var row = new TableRow<ProfileNFO>();
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                profileLoader.loadProfile(session, row.getItem());
            }
        });
        return row;
    }

    /** Returns an observable value for the profile last fix date.
     * @param param the cell data features
     * @return an observable value for the profile last fix date
     */
    private ObservableValueBase<Instant> getProfileLastFix(CellDataFeatures<ProfileNFO, Instant> param) {
        return new ObservableValueBase<>() {
            @Override
            public Instant getValue() {
                return param.getValue().getStats().getFixed();
            }
        };
    }

    /** Returns an observable value for the profile last scan date.
     * @param param the cell data features
     * @return an observable value for the profile last scan date
     */
    private ObservableValueBase<Instant> getProfileLastScan(CellDataFeatures<ProfileNFO, Instant> param) {
        return new ObservableValueBase<>() {
            @Override
            public Instant getValue() {
                return param.getValue().getStats().getScanned();
            }
        };
    }

    /** Returns an observable value for the profile created date.
     * @param param the cell data features
     * @return an observable value for the profile created date
     */
    private ObservableValueBase<Instant> getProfileCreated(CellDataFeatures<ProfileNFO, Instant> param) {
        return new ObservableValueBase<>() {
            @Override
            public Instant getValue() {
                return param.getValue().getStats().getCreated();
            }
        };
    }

    /** Returns an observable value for the profile have/total disks count.
     * @param param the cell data features
     * @return an observable value for the profile have/total disks count
     */
    private ObservableValueBase<HaveNTotal> getProfileHaveDisks(CellDataFeatures<ProfileNFO, HaveNTotal> param) {
        return new ObservableValueBase<>() {
            @Override
            public HaveNTotal getValue() {
                return param.getValue().getStats().getDisks();
            }
        };
    }

    /** Returns an observable value for the profile have/total ROMs count.
     * @param param the cell data features
     * @return an observable value for the profile have/total ROMs count
     */
    private ObservableValueBase<HaveNTotal> getProfileHaveRoms(CellDataFeatures<ProfileNFO, HaveNTotal> param) {
        return new ObservableValueBase<>() {
            @Override
            public HaveNTotal getValue() {
                return param.getValue().getStats().getRoms();
            }
        };
    }

    /** Returns an observable value for the profile sets.
     * @param param the cell data features
     * @return an observable value for the profile sets
     */
    private ObservableValueBase<HaveNTotal> getProfileHaveSets(CellDataFeatures<ProfileNFO, HaveNTotal> param) {
        return new ObservableValueBase<>() {
            @Override
            public HaveNTotal getValue() {
                return param.getValue().getStats().getSets();
            }
        };
    }

    /** Returns an observable value for the profile version.
     * @param param the cell data features
     * @return an observable value for the profile version
     */
    private ObservableValueBase<String> getProfileVersion(CellDataFeatures<ProfileNFO, String> param) {
        return new ObservableValueBase<>() {
            @Override
            public String getValue() {
                return param.getValue().getStats().getVersion();
            }
        };
    }

    /**
     * Returns an observable value for the profile NFO.
     * @param param the cell data features
     * @return an observable value for the profile NFO
     */
    private ObservableValueBase<ProfileNFO> getProfileNFOValue(CellDataFeatures<ProfileNFO, ProfileNFO> param) {
        return new ObservableValueBase<>() {
            @Override
            public ProfileNFO getValue() {
                return param.getValue();
            }
        };
    }

    /**
     * Creates a tree cell for the profile directory tree view, with a string converter that renames the underlying directory when the cell is edited.
     * @return a new tree cell for the profile directory tree view
     */
    private TextFieldTreeCell<Dir> createProfileTreeCell() {
        return new TextFieldTreeCell<>(new StringConverter<>() {
            private Dir dir;

            @Override
            public String toString(Dir dir) {
                this.dir = dir;
                return dir.toString();
            }

            @Override
            public Dir fromString(String string) {
                return dir.renameTo(dir.getFile().toPath().getParent().resolve(string).toFile());
            }
        });
    }

    /**
     * Updates the state of the profile-related menu items based on the currently selected profile.
     */
    private void updateMenuStates() {
        final var selected = profilesList.getSelectionModel().getSelectedItem();
        deleteProfileMenu.setDisable(selected == null);
        renameProfileMenu.setDisable(selected == null);
        dropCacheMenu.setDisable(selected == null);
        updateFromMameMenu.setDisable(selected == null || !selected.isJRM());
    }

    /**
     * Updates the state of the folder-related menu items based on the currently selected folder.
     */
    private void updateFolderMenuState() {
        final var selected = profilesTree.getSelectionModel().getSelectedItem();
        deleteFolderMenu.setDisable(selected == null);
        createFolderMenu.setDisable(selected == null);
    }

    /**
     * Resizes all profile table columns to fit their content.
     * <p>
     * Uses reflection to invoke the package-private
     * {@code TableColumnHeader.resizeColumnToFitContent} method on the JavaFX skin.
     */
    public void resizeColumns() {
        Platform.runLater(this::resizeColumnsToFitContent);
    }

    /**
     * Resizes all profile table columns to fit their content.
     */
    private void resizeColumnsToFitContent() {
        final var columns = profilesList.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            try {
                final var th = (TableColumnHeader) profilesList.queryAccessibleAttribute(AccessibleAttribute.COLUMN_AT_INDEX, i);
                final var columnToFitMethod = TableColumnHeader.class.getDeclaredMethod("resizeColumnToFitContent", int.class);
                columnToFitMethod.setAccessible(true); // NOSONAR
                columnToFitMethod.invoke(th, -1);
            } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) /* NOSONAR */ {
                Log.err("Failed to resize column " + columns.get(i).getText(), e);
            }

        }
    }

    /**
     * Reloads the directory tree item after a profile directory rename edit.
     *
     * @param e the tree edit event describing the renamed directory
     */
    private void editCommitProfileDir(TreeView.EditEvent<Dir> e) {
        Platform.runLater(() -> refreshProfileItem(e));
    }

    /** Refreshes the profile item in the tree view.
     * 
     * @param e the tree edit event describing the renamed directory
     */
    private void refreshProfileItem(TreeView.EditEvent<Dir> e) {
        if (getTreeViewItem(profilesTree.getRoot(), e.getNewValue()) instanceof DirItem newItem)
            newItem.reload();
    }

    /**
     * Handles a profile rename edit by renaming the profile file along with its
     * companion {@code .properties} and {@code .cache} files, and relocating the
     * in-memory {@link ProfileNFO} (and the current profile, if it matches).
     *
     * @param e the cell edit event carrying the old and new profile names
     */
    private void editCommitProfile(CellEditEvent<ProfileNFO, ProfileNFO> e) {
        final ProfileNFO pnfo = e.getRowValue();
        AtomicInteger err = new AtomicInteger();
        Arrays.asList("", ".properties", ".cache").forEach(ext -> renameProfileFile(e, pnfo, err, ext));
        if (err.get() != 0) {
            Dialogs.showAlert("Can't rename " + e.getOldValue().getName() + " to " + e.getNewValue().getNewName());
            e.getTableView().refresh();
        } else {
            final var newNfoFile = new File(pnfo.getFile().getParentFile(), e.getNewValue().getNewName());
            if (session.getCurrProfile() != null && session.getCurrProfile().getNfo().getFile().equals(pnfo.getFile()))
                session.getCurrProfile().getNfo().relocate(session, newNfoFile);
            pnfo.relocate(session, newNfoFile);
        }
    }

    /** Renames a profile file with the specified extension.
     * 
     * @param e the cell edit event carrying the old and new profile names
     * @param pnfo the profile NFO being renamed
     * @param err an atomic integer used to accumulate error flags for each rename operation
     * @param ext the file extension to rename (e.g., "", ".properties", ".cache")
     */
    private void renameProfileFile(CellEditEvent<ProfileNFO, ProfileNFO> e, final ProfileNFO pnfo, AtomicInteger err, String ext) {
        final var oldfile = new File(pnfo.getFile().getParentFile(), pnfo.getName() + ext);
        final var newfile = new File(pnfo.getFile().getParentFile(), e.getNewValue().getNewName() + ext);
        final var success = !oldfile.equals(newfile) && oldfile.renameTo(newfile);
        err.set((err.get() << 1) | (success ? 0 : 1));
        if (!success)
            Log.warn(() -> "Can't rename " + oldfile.getName() + " to " + newfile.getName());
    }

    /**
     * Populates the profile table with the profiles contained in the given directory.
     *
     * @param newValue the newly selected tree item, or {@code null} to do nothing
     */
    private void populate(TreeItem<Dir> newValue) /* NOSONAR */ {
        if (newValue == null)
            return;
        profilesList.setItems(FXCollections.observableArrayList(ProfileNFO.list(session, newValue.getValue().getFile())));
        resizeColumns();
    }

    /**
     * Loads the currently selected profile.
     *
     * @param e the action event triggering the load
     */
    @FXML
    void actionLoad(ActionEvent e) {
        final var profile = profilesList.getSelectionModel().getSelectedItem();
        if (profile != null)
            profileLoader.loadProfile(session, profile);
    }

    /**
     * Opens a file chooser to import one or more DAT files.
     *
     * @param e the action event triggering the import
     */
    @FXML
    void actionImportDat(ActionEvent e) {
        importDat(false);
    }

    /**
     * Opens a file chooser to import one or more MAME software list files.
     *
     * @param e the action event triggering the import
     */
    @FXML
    void actionImportSL(ActionEvent e) {
        importDat(true);
    }

    /**
     * Opens a file chooser initialized from the user settings and imports the
     * selected DAT files, optionally as software lists. Remembers the chosen
     * directory in the user settings for the next import.
     *
     * @param sl {@code true} to import as a MAME software list, {@code false} otherwise
     */
    private void importDat(final boolean sl) {
        final var workdir = session.getUser().getSettings().getWorkPath().toFile();
        final var chooser = new FileChooser();
        final var filter = new ExtensionFilter(Messages.getString("MainFrame.DatFile"), "*.dat", "*.xml");
        final var filter2 = new ExtensionFilter(Messages.getString("MainFrame.MameExecutable"),
                SystemUtils.IS_OS_WINDOWS ? new String[] { "*mame*.exe", "*mess*.exe" } : new String[] { "*mame*", "*mess*" });
        chooser.getExtensionFilters().addAll(filter, filter2);
        chooser.setSelectedExtensionFilter(filter);
        Optional.ofNullable(session.getUser().getSettings().getProperty("MainFrame.ChooseExeOrDatToImport", workdir.getAbsolutePath())).map(File::new).filter(File::isDirectory)
                .ifPresent(chooser::setInitialDirectory);
        final var files = chooser.showOpenMultipleDialog(profilesList.getScene().getWindow());
        importDat(files, sl);
        if (files != null)
            session.getUser().getSettings().setProperty("MainFrame.ChooseExeOrDatToImport", files.stream().filter(File::exists).map(File::getParent).findFirst().orElse(null));
    }

    /**
     * Background task that searches for DAT files in the selected inputs and imports
     * them into the currently selected profile directory.
     */
    private final class ImportDatTask extends ProgressTask<Void> {
        /** The files selected by the user for import. */
        private final List<File> files;
        /** Whether the import is a MAME software list import. */
        private final boolean sl;
        /** The successfully prepared imports with their base files, to be processed on success. */
        final List<ImportWithBaseFile> imprts = new ArrayList<>();

        /**
         * Constructs a new DAT import task.
         *
         * @param owner the stage owning the progress dialog
         * @param files the files selected for import
         * @param sl {@code true} to import as a MAME software list, {@code false} otherwise
         * @throws IOException if an I/O error occurs while preparing the task
         * @throws URISyntaxException if a URI used by the task is malformed
         */
        private ImportDatTask(Stage owner, List<File> files, boolean sl) throws IOException, URISyntaxException {
            super(owner);
            this.files = files;
            this.sl = sl;
        }

        /**
         * Searches each selected file for DAT/executable files and prepares the
         * corresponding imports.
         *
         * @return always {@code null}
         * @throws Exception if an error occurs while preparing an import
         */
        @Override
        protected Void call() throws Exception {
            for (final var basefile : files) {
                for (final var file : searchDats(basefile)) {
                    setProgress(Messages.getString("MainFrame.ImportingFromMame"), -1); //$NON-NLS-1$
                    imprts.add(new ImportWithBaseFile(new Import(session, file, sl, this), basefile));
                }
            }
            return null;
        }

        /**
         * Called on the JavaFX application thread when the task succeeds: processes
         * each prepared import and refreshes the selected directory in the tree.
         */
        @Override
        protected void succeeded() {
            this.close();
            var rejected = 0;
            for (final var imprt : imprts) {
                if (imprt.imprt.getFile() == null) {
                    rejected++;
                    continue;
                }
                try {
                    importDat(imprt, sl);
                } catch (IOException e) {
                    Log.err(e.getMessage(), e);
                }
            }
            if (rejected > 0)
                Dialogs.showAlert(rejected == 1
                        ? "Could not import 1 selected file. Only DAT/XML files and native MAME/MESS executables are accepted."
                        : "Could not import " + rejected + " selected files. Only DAT/XML files and native MAME/MESS executables are accepted.");

            final var theNode = profilesTree.getSelectionModel().getSelectedItem();
            if (theNode instanceof DirItem d) {
                d.reload();
                populate(d);
            } else
                Log.err(Messages.getString("MainFrame.NodeNotFound")); //$NON-NLS-1$
        }

        /**
         * Called on the JavaFX application thread when the task fails: shows an alert
         * for user cancellations, or an error dialog with the cause otherwise.
         */
        @Override
        protected void failed() {
            if (getException() instanceof BreakException)
                Dialogs.showAlert("Cancelled");
            else {
                this.close();
                Optional.ofNullable(getException().getCause()).ifPresentOrElse(cause -> {
                    Log.err(cause.getMessage(), cause);
                    Dialogs.showError(cause);
                }, () -> {
                    Log.err(getException().getMessage(), getException());
                    Dialogs.showError(getException());
                });
            }
        }

        /**
         * Import a DAT file into the currently selected profile directory, resolving its destination path relative to the given base file. Non-MAME DATs are copied directly (with an overwrite/rename/file-chooser prompt when the target already exists), while MAME DATs prompt the user for a JRM file name before delegating to {@link #importDat(Session, boolean, jrm.profile.manager.Import, File)}.
         * 
         * @param imprt the import with base file holder describing the source import and the base used to compute the destination directory
         * @param sl whether the import is a MAME software list import
         * 
         * @throws IllegalArgumentException if the destination directory or import parameters are not valid
         * @throws IOException if the destination directory cannot be created or the import file cannot be copied
         */
        private void importDat(final ImportWithBaseFile imprt, final boolean sl) throws IllegalArgumentException, IOException {
            final var selDir = profilesTree.getSelectionModel().getSelectedItem().getValue().getFile().toPath();
            final var currDir = selDir.resolve(imprt.basefile.toPath().getParent().relativize(imprt.imprt.getOrgFile().toPath().getParent())).toFile();
            Files.createDirectories(currDir.toPath());
            if (!imprt.imprt.isMame()) {
                var fileRef = new AtomicReference<File>(new File(currDir, imprt.imprt.getFile().getName()));
                int mode = importDatExistsChoose(fileRef);
                if (mode == 3)
                    return;
                if (!fileRef.get().exists() || mode == 0) {
                    try {
                        FileUtils.copyFile(imprt.imprt.getFile(), fileRef.get());
                    } catch (IOException e) {
                        Log.err(e.getMessage(), e);
                    }
                }
            } else {
                final var layout = new VBox();
                layout.setPrefWidth(300);
                final var label = new Label("Choose a name to save JRM file for import of " + imprt.imprt.getOrgFile());
                label.setWrapText(true);
                layout.getChildren().add(label);
                final var nameField = new TextField(imprt.imprt.getFile().getName());
                layout.getChildren().add(nameField);
                final var result = Dialogs.showConfirmation("Choose a name to save JRM file", layout, ButtonType.APPLY);
                final var fileName = result.filter(t -> t == ButtonType.APPLY)
                        .map(_ -> nameField.getText())
                        .filter(t -> !t.isBlank())
                        .map(t -> t.endsWith(".jrm") ? t : (t + ".jrm"))
                        .orElse(imprt.imprt.getFile().getName());
                importDat(session, sl, imprt.imprt, currDir.toPath().resolve(fileName).toFile());
            }
        }

        /**
         * Asks the user how to proceed when the import destination already exists.
         *
         * @param file the destination file reference, updated in place when auto-rename is chosen
         * @return the chosen mode: {@code 0} overwrite, {@code 1} auto-rename, {@code 2} file chooser, {@code 3} cancel
         * @throws IllegalArgumentException inherited from the underlying dialog API
         */
        private int importDatExistsChoose(AtomicReference<File> file) throws IllegalArgumentException {
            int mode = -1;
            if (file.get().exists()) {
                final var overwrite = new ButtonType("Overwrite");
                final var autorename = new ButtonType("Auto Rename");
                final var filechooser = new ButtonType("File Chooser");
                final var options = new ButtonType[] { overwrite, autorename, filechooser, ButtonType.CANCEL };
                final var ret = Dialogs.showConfirmation("File already exists", "File already exists, choose what to do", options);
                if (ret.isEmpty())
                    mode = 3;
                else if (ret.get() == overwrite)
                    mode = 0;
                else if (ret.get() == autorename)
                    mode = 1;
                else if (ret.get() == filechooser)
                    mode = 2;
                else
                    mode = 3;
                if (mode == 1)
                    file.set(autoRenameFile(file.get()));
            }
            return mode;
        }

        /**
         * Computes a non-existing sibling file name by appending an incrementing
         * numeric suffix to the base name.
         *
         * @param file the original file
         * @return a sibling file that does not exist yet
         * @throws IllegalArgumentException never thrown; declared for signature compatibility
         */
        private File autoRenameFile(File file) throws IllegalArgumentException {
            for (var i = 1;; i++) {
                final var testFile = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + '_' + i + '.' + FilenameUtils.getExtension(file.getName()));
                if (!testFile.exists())
                    return testFile;
            }
        }

        /**
         * Searches for DAT files and MAME executables starting from the given file.
         *
         * @param file the file or directory to start the search from
         * @return the accumulated list of matching files
         */
        private List<File> searchDats(File file) {
            return ProfilePanelController.searchDats(file, new ArrayList<>());
        }

        /**
         * Copies the prepared import file to its destination, and for MAME imports also
         * copies the associated ROMs and software-list files and persists the profile.
         *
         * @param session the current user session
         * @param sl {@code true} to import as a MAME software list, {@code false} otherwise
         * @param imprt the prepared import
         * @param file the destination file
         * @return always {@code null}
         */
        private Void importDat(final Session session, final boolean sl, final jrm.profile.manager.Import imprt, final File file) {
            try {
                final var parent = file.getParentFile();
                FileUtils.copyFile(imprt.getFile(), file);
                if (imprt.isMame()) {
                    final var pnfo = ProfileNFO.load(session, file);
                    pnfo.getMame().set(imprt.getOrgFile(), sl);
                    if (imprt.getRomsFile() != null) {
                        FileUtils.copyFileToDirectory(imprt.getRomsFile(), parent);
                        pnfo.getMame().setFileroms(new File(parent, imprt.getRomsFile().getName()));
                        if (imprt.getSlFile() != null) {
                            FileUtils.copyFileToDirectory(imprt.getSlFile(), parent);
                            pnfo.getMame().setFilesl(new File(parent, imprt.getSlFile().getName()));
                        }
                    }
                    pnfo.save(session);
                }
            } catch (final IOException e) {
                Log.err(e.getMessage(), e);
            }
            return null;
        }

    }

    /**
     * Background task that re-imports a profile from its MAME executable and updates
     * the associated ROMs and software-list files.
     */
    private final class UpdateFromMameTask extends ProgressTask<Import> {
        /** The profile to update from MAME. */
        private final ProfileNFO nfo;

        /**
         * Constructs a new update-from-MAME task.
         *
         * @param owner the stage owning the progress dialog
         * @param nfo the profile to update from MAME
         * @throws IOException if an I/O error occurs while preparing the task
         * @throws URISyntaxException if a URI used by the task is malformed
         */
        private UpdateFromMameTask(Stage owner, ProfileNFO nfo) throws IOException, URISyntaxException {
            super(owner);
            this.nfo = nfo;
        }

        /**
         * Builds the MAME import for the profile.
         *
         * @return the prepared import
         * @throws Exception if an error occurs while preparing the import
         */
        @Override
        protected Import call() throws Exception {
            return new Import(session, nfo.getMame().getFile(), nfo.getMame().isSL(), this);
        }

        /**
         * Called on the JavaFX application thread when the task succeeds: applies the
         * updated MAME import to the profile.
         */
        @Override
        protected void succeeded() {
            try {
                this.close();
                updateFromMame(session, nfo, get());
            } catch (InterruptedException e) {
                Log.err(e.getMessage(), e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException | IOException e) {
                Log.err(e.getMessage(), e);
                Dialogs.showError(e);
            }
        }

        /**
         * Called on the JavaFX application thread when the task fails: shows an alert
         * for user cancellations, or an error dialog with the cause otherwise.
         */
        @Override
        protected void failed() {
            if (getException() instanceof BreakException)
                Dialogs.showAlert("Cancelled");
            else {
                this.close();
                Optional.ofNullable(getException().getCause()).ifPresentOrElse(cause -> {
                    Log.err(cause.getMessage(), cause);
                    Dialogs.showError(cause);
                }, () -> {
                    Log.err(getException().getMessage(), getException());
                    Dialogs.showError(getException());
                });
            }
        }

        /**
         * Applies the updated MAME import to the profile, replacing its ROMs and
         * software-list files, resetting the stats, and persisting the profile.
         *
         * @param session the current user session
         * @param nfo the profile to update
         * @param imprt the prepared import
         * @throws IOException if the ROMs or software-list files cannot be copied
         */
        private void updateFromMame(final Session session, final ProfileNFO nfo, Import imprt) throws IOException {
            if (imprt == null || !imprt.canApplyMameUpdate(nfo.getMame().isSL())) {
                Dialogs.showAlert("Could not update from MAME. The executable is missing, is not a native MAME/MESS binary, or did not return listxml data.");
                return;
            }
            nfo.getMame().deleteAlongside(nfo.getFile());
            nfo.getMame().setFileroms(new File(nfo.getFile().getParentFile(), imprt.getRomsFile().getName()));
            Files.copy(imprt.getRomsFile().toPath(), nfo.getMame().getFileroms().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            if (nfo.getMame().isSL()) {
                nfo.getMame().setFilesl(new File(nfo.getFile().getParentFile(), imprt.getSlFile().getName()));
                Files.copy(imprt.getSlFile().toPath(), nfo.getMame().getFilesl().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            }
            nfo.getMame().setUpdated();
            nfo.getStats().reset();
            nfo.save(session);
            profilesList.refresh();
        }
    }

    /**
     * Pairs a prepared import with the base file used to compute its destination directory.
     */
    @AllArgsConstructor
    private static final class ImportWithBaseFile {
        /** The prepared import. */
        Import imprt;
        /** The base file from which the import originated. */
        File basefile;
    }

    /**
     * Starts a background task to import the given files, optionally as software lists.
     *
     * @param files the files to import, or {@code null} to do nothing
     * @param sl {@code true} to import as MAME software lists, {@code false} otherwise
     */
    private void importDat(final List<File> files, final boolean sl) {
        try {
            if (files == null)
                return;
            Thread.startVirtualThread(new ImportDatTask((Stage) profilesList.getScene().getWindow(), files, sl));
        } catch (IOException | URISyntaxException e) {
            Log.err(e.getMessage(), e);
            Dialogs.showError(e);
        }
    }

    /**
     * Recursively searches the tree for the item holding the given value.
     *
     * @param <T> the type of values held by the tree items
     * @param item the tree item to start the search from
     * @param value the value to find
     * @return the matching tree item, or {@code null} if none is found
     */
    public static <T> TreeItem<T> getTreeViewItem(TreeItem<T> item, T value) {
        if (item != null) {
            if (item.getValue().equals(value))
                return item;
            for (TreeItem<T> child : item.getChildren()) {
                TreeItem<T> s = getTreeViewItem(child, value);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Refreshes the profile table view.
     */
    public void refreshList() {
        profilesList.refresh();
    }

    /**
     * Creates a new profile folder under the selected directory and enters edit mode.
     *
     * @param e the action event triggering the creation
     */
    @FXML
    private void createFolder(ActionEvent e) {
        final var selectedItem = profilesTree.getSelectionModel().getSelectedItem();
        if (selectedItem instanceof DirItem d) {
            final var newDir = new Dir(new File(selectedItem.getValue().getFile(), Messages.getString("MainFrame.NewFolder")));
            d.reload();
            final var newItem = getTreeViewItem(d, newDir);
            profilesTree.getSelectionModel().clearSelection();
            profilesTree.getSelectionModel().select(newItem);
            profilesTree.layout();
            profilesTree.edit(newItem);
        }
    }

    /**
     * Deletes the selected folder after confirming when it is not empty.
     *
     * @param e the action event triggering the deletion
     */
    @FXML
    private void deleteFolder(ActionEvent e) {
        final var selectedItem = profilesTree.getSelectionModel().getSelectedItem();
        if (selectedItem instanceof DirItem d) {
            try {
                boolean empty = false;
                try (final var entries = Files.list(d.getValue().getFile().toPath())) {
                    empty = !entries.findFirst().isPresent();
                }
                boolean doit = empty;
                if (!empty)
                    doit = Dialogs.showConfirmation("Dir not empty", "This directory is not empty, delete?", ButtonType.YES, ButtonType.NO).map(t -> t == ButtonType.YES)
                            .orElse(false);
                if (doit) {
                    FileUtils.deleteDirectory(d.getValue().getFile());
                    selectedItem.getParent().getChildren().remove(d);
                }
            } catch (IOException ex) {
                Log.err(ex.getMessage(), ex);
                Dialogs.showAlert(ex.getMessage());
            }
        }
    }

    /**
     * Deletes the selected profile, unless it is the currently loaded profile.
     *
     * @param e the action event triggering the deletion
     */
    @FXML
    void deleteProfile(ActionEvent e) {
        final var nfo = profilesList.getSelectionModel().getSelectedItem();
        if (nfo != null && (session.getCurrProfile() == null || !session.getCurrProfile().getNfo().equals(nfo)) && nfo.delete())
            profilesList.getItems().remove(nfo);
    }

    /**
     * Enters edit mode on the selected profile name column to allow renaming.
     *
     * @param e the action event triggering the rename
     */
    @FXML
    void renameProfile(ActionEvent e) {
        profilesList.setEditable(true);
        profilesList.edit(profilesList.getSelectionModel().getSelectedIndex(), profileCol);
        profilesList.setEditable(false);
    }

    /**
     * Deletes the {@code .cache} file associated with the selected profile.
     *
     * @param e the action event triggering the cache drop
     */
    @FXML
    void dropCache(ActionEvent e) {
        final var nfo = profilesList.getSelectionModel().getSelectedItem();
        if (nfo != null)
            try {
                Files.deleteIfExists(Paths.get(nfo.getFile().getAbsolutePath() + ".cache"));
            } catch (IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
    }

    /**
     * Updates the selected profile from its MAME executable, optionally relocating
     * the MAME file first if it cannot be found.
     *
     * @param e the action event triggering the update
     */
    @FXML
    private void updateFromMame(ActionEvent e) {
        final var nfo = profilesList.getSelectionModel().getSelectedItem();
        if (nfo != null) {
            try {
                final var chooser = new FileChooser();
                chooser.setTitle(Messages.getString("MainFrame.ChooseMameNewLocation"));
                chooser.setInitialDirectory(nfo.getFile().getParentFile());
                chooser.setInitialFileName(nfo.getFile().getName());
                if (nfo.getMame().getStatus() == MameStatus.NEEDUPDATE || (EnumSet.of(MameStatus.NOTFOUND, MameStatus.UNKNOWN).contains(nfo.getMame().getStatus())
                        && updateFromMameRelocate(nfo, chooser.showOpenDialog(profilesList.getScene().getWindow())) == MameStatus.NEEDUPDATE)) {
                    Thread.startVirtualThread(new UpdateFromMameTask((Stage) profilesList.getScene().getWindow(), nfo));
                }
            } catch (IOException | URISyntaxException ex) {
                Log.err(ex.getMessage(), ex);
                Dialogs.showError(ex);
            }
        }
    }

    /**
     * Relocates the MAME executable for the given profile and reports the new status.
     *
     * @param nfo the profile whose MAME executable is being relocated
     * @param mame the newly chosen MAME executable file
     * @return the resulting MAME status, or {@link MameStatus#NOTFOUND} if the file does not exist
     */
    MameStatus updateFromMameRelocate(final ProfileNFO nfo, File mame) {
        if (mame != null && mame.exists())
            return nfo.getMame().relocate(mame);
        return MameStatus.NOTFOUND;
    }

    /**
     * Maximum directory nesting searched from the starting file. Deeper folders are omitted.
     */
    static final int MAX_DAT_SEARCH_DEPTH = 100;

    /**
     * Search for DAT files and MAME executables starting from the given file.
     * Walks iteratively with a depth cap and canonical-path cycle detection.
     *
     * @param file the file or directory to start the search from
     * @param files the list to append matching files to
     * @return the same list reference passed in, with matches appended
     */
    static List<File> searchDats(File file, List<File> files) {
        if (file == null || files == null)
            return files;
        final var pending = new ArrayDeque<PendingDat>();
        pending.add(new PendingDat(file, 0));
        final var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            final var current = pending.removeFirst();
            final File currentFile = current.file();
            if (currentFile == null)
                continue;
            if (currentFile.isFile()) {
                if (FilenameUtils.isExtension(currentFile.getName(), "xml", "dat") || MameExecutable.isLaunchable(currentFile))
                    files.add(currentFile);
                continue;
            }
            if (current.depth() >= MAX_DAT_SEARCH_DEPTH || !currentFile.isDirectory())
                continue;
            final String canonical;
            try {
                canonical = currentFile.getCanonicalPath();
            } catch (final IOException e) {
                Log.warn(e.getMessage());
                continue;
            }
            if (!visited.add(canonical))
                continue;
            try (final var stream = Files.newDirectoryStream(currentFile.toPath())) {
                for (final var path : stream)
                    pending.add(new PendingDat(path.toFile(), current.depth() + 1));
            } catch (IOException e) {
                Log.warn(e.getMessage());
            }
        }
        return files;
    }

    private record PendingDat(File file, int depth) {
    }
}
