package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import jrm.fx.ui.controls.DescriptorCellFactory;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.misc.DragNDrop;
import jrm.fx.ui.profile.ProfileViewer;
import jrm.fx.ui.progress.ProgressTask;
import jrm.fx.ui.status.NeutralToNodeFormatter;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Driver;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Machine.DisplayOrientation;
import jrm.profile.data.PropertyStub;
import jrm.profile.data.Software.Supported;
import jrm.profile.data.Source;
import jrm.profile.data.Systm;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.profile.filter.NPlayer;
import jrm.profile.filter.NPlayers;
import jrm.profile.fix.Fix;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.scan.Scan;
import jrm.profile.scan.options.Descriptor;
import jrm.profile.scan.options.ScanAutomation;
import jrm.security.PathAbstractor;
import jrm.security.Session;

/**
 * FXML controller for the scanner panel.
 * <p>
 * Manages ROM scanning operations, profile selection, destination paths for ROMs/disks/software/samples, and scan automation.
 * Implements {@link ProfileLoader} to handle profile loading callbacks. Provides buttons for scanning, reporting, fixing,
 * importing, and exporting profiles.
 *
 * @since 2.5
 */
public class ScannerPanelController extends BaseController implements ProfileLoader {
    /** Icon path for disk destination buttons. */
    private static final String DISK_ICON = "/jrm/resicons/icons/disk.png";

    /** The scanner panel tab. */
    @FXML
    private Tab dirTab;

    /** Profile information button. */
    @FXML
    private Button infosBtn;
    /** Scan button. */
    @FXML
    private Button scanBtn;
    /** Report button. */
    @FXML
    private Button reportBtn;
    /** Fix button. */
    @FXML
    private Button fixBtn;
    /** Import settings button. */
    @FXML
    private Button importBtn;
    /** Export settings button. */
    @FXML
    private Button exportBtn;

    /** ROMs destination browse button. */
    @FXML
    private Button romsDestBtn;
    /** ROMs destination directory text field. */
    @FXML
    private TextField romsDest;
    /** Disks destination enabled checkbox. */
    @FXML
    private CheckBox disksDestCB;
    /** Disks destination browse button. */
    @FXML
    private Button disksDestBtn;
    /** Disks destination directory text field. */
    @FXML
    private TextField disksDest;
    /** Software ROMs destination enabled checkbox. */
    @FXML
    private CheckBox swDestCB;
    /** Software ROMs destination browse button. */
    @FXML
    private Button swDestBtn;
    /** Software ROMs destination directory text field. */
    @FXML
    private TextField swDest;
    /** Software disks destination enabled checkbox. */
    @FXML
    private CheckBox swDisksDestCB;
    /** Software disks destination browse button. */
    @FXML
    private Button swDisksDestBtn;
    /** Software disks destination directory text field. */
    @FXML
    private TextField swDisksDest;
    /** Samples destination enabled checkbox. */
    @FXML
    private CheckBox samplesDestCB;
    /** Samples destination browse button. */
    @FXML
    private Button samplesDestBtn;
    /** Samples destination directory text field. */
    @FXML
    private TextField samplesDest;
    /** Backup destination enabled checkbox. */
    @FXML
    private CheckBox backupDestCB;
    /** Backup destination browse button. */
    @FXML
    private Button backupDestBtn;
    /** Backup destination directory text field. */
    @FXML
    private TextField backupDest;

    /** ROM source directories list view. */
    @FXML
    private ListView<File> srcList;
    /** Source list context menu. */
    @FXML
    private ContextMenu srcListMenu;
    /** Source list &quot;Add&quot; menu item. */
    @FXML
    private MenuItem srcListAddMenuItem;
    /** Source list &quot;Delete&quot; menu item. */
    @FXML
    private MenuItem srcListDelMenuItem;

    /** Settings tab. */
    @FXML
    private Tab settingsTab;

    /** Nested settings controller. */
    @FXML
    private ScannerPanelSettingsController scannerPanelSettingsController;

    /** Filter tab. */
    @FXML
    private Tab filterTab;
    /** Advanced filter tab. */
    @FXML
    private Tab advFilterTab;
    /** Automation tab. */
    @FXML
    private Tab automationTab;

    /** Profile info label container. */
    @FXML
    private HBox profileinfoLbl;

    /** Systems filter check-box list view. */
    @FXML
    private ListView<Systm> systemsFilter;
    /** Systems filter context menu. */
    @FXML
    private ContextMenu systemsFilterMenu;
    /** Systems filter &quot;Select All&quot; menu item. */
    @FXML
    private MenuItem systemsFilterSelectAllMenuItem;
    /** Systems filter &quot;Select All BIOS&quot; menu item. */
    @FXML
    private MenuItem systemsFilterSelectAllBiosMenuItem;
    /** Systems filter &quot;Select All Software Lists&quot; menu item. */
    @FXML
    private MenuItem systemsFilterSelectAllSoftwaresMenuItem;
    /** Systems filter &quot;Unselect All&quot; menu item. */
    @FXML
    private MenuItem systemsFilterUnselectAllMenuItem;
    /** Systems filter &quot;Unselect All BIOS&quot; menu item. */
    @FXML
    private MenuItem systemsFilterUnselectAllBiosMenuItem;
    /** Systems filter &quot;Unselect All Software Lists&quot; menu item. */
    @FXML
    private MenuItem systemsFilterUnselectAllSoftwaresMenuItem;
    /** Systems filter &quot;Invert Selection&quot; menu item. */
    @FXML
    private MenuItem systemsFilterInvertSelectionMenuItem;
    /** Sources filter check-box list view. */
    @FXML
    private ListView<Source> sourcesFilter;
    /** Sources filter context menu. */
    @FXML
    private ContextMenu sourcesFilterMenu;
    /** Sources filter &quot;Select All&quot; menu item. */
    @FXML
    private MenuItem sourcesFilterSelectAllMenuItem;
    /** Sources filter &quot;Unselect All&quot; menu item. */
    @FXML
    private MenuItem sourcesFilterUnselectAllMenuItem;
    /** Sources filter &quot;Invert Selection&quot; menu item. */
    @FXML
    private MenuItem sourcesFilterInvertSelectionMenuItem;

    /** Include clones filter checkbox. */
    @FXML
    private CheckBox chckbxIncludeClones;
    /** Include disks filter checkbox. */
    @FXML
    private CheckBox chckbxIncludeDisks;
    /** Include samples filter checkbox. */
    @FXML
    private CheckBox chckbxIncludeSamples;
    /** Driver status filter combo box. */
    @FXML
    private ComboBox<Driver.StatusType> cbbxDriverStatus;
    /** Cabinet type filter combo box. */
    @FXML
    private ComboBox<CabinetType> cbbxFilterCabinetType;
    /** Display orientation filter combo box. */
    @FXML
    private ComboBox<DisplayOrientation> cbbxFilterDisplayOrientation;
    /** Software minimum supported level filter combo box. */
    @FXML
    private ComboBox<Supported> cbbxSWMinSupportedLvl;
    /** Minimum year filter combo box. */
    @FXML
    private ComboBox<String> cbbxYearMin;
    /** Maximum year filter combo box. */
    @FXML
    private ComboBox<String> cbbxYearMax;

    /** NPlayers file path text field. */
    @FXML
    private TextField tfNPlayers;
    /** NPlayers list view. */
    @FXML
    private ListView<NPlayer> listNPlayers;
    /** CatVer file path text field. */
    @FXML
    private TextField tfCatVer;
    /** CatVer category tree view. */
    @FXML
    private TreeView<PropertyStub> treeCatVer;
    /** Scan automation combo box. */
    @FXML
    private ComboBox<Descriptor> cbAutomation;
    /** NPlayers context menu. */
    @FXML
    private ContextMenu nPlayersMenu;
    /** NPlayers &quot;Select All&quot; menu item. */
    @FXML
    private MenuItem nPlayersMenuItemAll;
    /** NPlayers &quot;Select None&quot; menu item. */
    @FXML
    private MenuItem nPlayersMenuItemNone;
    /** NPlayers &quot;Invert Selection&quot; menu item. */
    @FXML
    private MenuItem nPlayersMenuItemInvert;
    /** NPlayers &quot;Clear&quot; menu item. */
    @FXML
    private MenuItem nPlayersMenuItemClear;
    /** CatVer context menu. */
    @FXML
    private ContextMenu catVerMenu;
    /** CatVer &quot;Select All&quot; menu item. */
    @FXML
    private MenuItem catVerMenuItemSelectAll;
    /** CatVer &quot;Select Mature&quot; menu item. */
    @FXML
    private MenuItem catVerMenuItemSelectMature;
    /** CatVer &quot;Unselect All&quot; menu item. */
    @FXML
    private MenuItem catVerMenuItemUnselectAll;
    /** CatVer &quot;Unselect Mature&quot; menu item. */
    @FXML
    private MenuItem catVerMenuItemUnselectMature;
    /** CatVer &quot;Clear&quot; menu item. */
    @FXML
    private MenuItem catVerMenuItemClear;

    /**
     * Initializes the controller, setting up tabs, destinations, action buttons,
     * source list, drag-and-drop, filters, NPlayers, CatVer, automation, and import/export.
     *
     * @param location  the location used to resolve relative paths for the root object, or {@code null}
     * @param resources the resources used to localize the root object, or {@code null}
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTabIcons();
        initDestinations();
        initActionButtons();
        initSrcList();
        initDestDragNDrop();
        initFilters();
        initNPlayers();
        initCatVer();
        initAutomation();
        initImportExport();
    }

    /**
     * Sets icons for all tab headers: directory, settings, filter, advanced filter, and automation.
     */
    private void initTabIcons() {
        dirTab.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/folder.png"));
        settingsTab.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/cog.png"));
        filterTab.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/arrow_join.png"));
        advFilterTab.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/arrow_in.png"));
        automationTab.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/link.png"));
    }

    /**
     * Initializes destination text fields and buttons with icons and checkbox listeners.
     */
    private void initDestinations() {
        romsDestBtn.setGraphic(IconHelper.createIcon(DISK_ICON));
        disksDestBtn.setGraphic(IconHelper.createIcon(DISK_ICON));
        initDestCheckBox(disksDestCB, disksDest, disksDestBtn, ProfileSettingsEnum.disks_dest_dir_enabled);
        swDestBtn.setGraphic(IconHelper.createIcon(DISK_ICON));
        initDestCheckBox(swDestCB, swDest, swDestBtn, ProfileSettingsEnum.swroms_dest_dir_enabled);
        swDisksDestBtn.setGraphic(IconHelper.createIcon(DISK_ICON));
        initDestCheckBox(swDisksDestCB, swDisksDest, swDisksDestBtn, ProfileSettingsEnum.swdisks_dest_dir_enabled);
        samplesDestBtn.setGraphic(IconHelper.createIcon(DISK_ICON));
        initDestCheckBox(samplesDestCB, samplesDest, samplesDestBtn, ProfileSettingsEnum.samples_dest_dir_enabled);
        backupDestBtn.setGraphic(IconHelper.createIcon(DISK_ICON));
        initDestCheckBox(backupDestCB, backupDest, backupDestBtn, ProfileSettingsEnum.backup_dest_dir_enabled);
    }

    /**
     * Sets icons on the info, scan, report, fix, import, and export action buttons.
     */
    private void initActionButtons() {
        infosBtn.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/information.png"));
        scanBtn.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/magnifier.png"));
        reportBtn.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/report.png"));
        fixBtn.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/tick.png"));
        importBtn.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/table_refresh.png"));
        exportBtn.setGraphic(IconHelper.createIcon("/jrm/resicons/icons/table_save.png"));
    }

    /**
     * Configures the source list view with a custom cell factory and context menu actions.
     */
    private void initSrcList() {
        srcList.setCellFactory(_ -> srcListCellFactory());
        srcListMenu.setOnShowing(_ -> srcListDelMenuItem.setDisable(srcList.getSelectionModel().getSelectedIndex() < 0));
        srcListDelMenuItem.setOnAction(_ -> removeSelectedSrcItems());
        srcListAddMenuItem.setOnAction(_ -> chooseSrc(null, ProfileSettingsEnum.src_dir, "MainFrame.ChooseRomsSource"));
    }

    /**
     * Removes selected items from the source list and persists the change to the profile.
     */
    private void removeSelectedSrcItems() {
        srcList.getItems().removeAll(srcList.getSelectionModel().getSelectedItems());
        saveSrcList();
    }

    /**
     * Creates a cell factory for the source file list view.
     * <p>
     * Displays the file path and enables double-click editing of the source directory.
     *
     * @return a {@link ListCell} factory for {@link File} entries
     */
    private ListCell<File> srcListCellFactory() {
        return new ListCell<File>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                    setOnMouseClicked(null);
                } else {
                    setText(item.toString());
                    setOnMouseClicked(ev -> {
                        if (ev.getClickCount() == 2) {
                            chooseSrc(item, ProfileSettingsEnum.src_dir, "MainFrame.ChooseRomsSource");
                        }
                    });
                }
            }
        };
    }

    /**
     * Installs drag-and-drop directory handlers for destination text fields and the source list.
     */
    private void initDestDragNDrop() {
        new DragNDrop(romsDest).addDir(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.roms_dest_dir, txt));
        new DragNDrop(disksDest).addDir(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.disks_dest_dir, txt));
        new DragNDrop(swDest).addDir(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.swroms_dest_dir, txt));
        new DragNDrop(swDisksDest).addDir(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.swdisks_dest_dir, txt));
        new DragNDrop(samplesDest).addDir(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.samples_dest_dir, txt));
        new DragNDrop(backupDest).addDir(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.backup_dest_dir, txt));
        new DragNDrop(srcList)
                .addDirs(files -> session.getCurrProfile().setProperty(ProfileSettingsEnum.src_dir, String.join("|", files.stream().map(File::getAbsolutePath).toList())));
    }

    /**
     * Initializes the systems and sources filter lists and all filter combo boxes
     * with their data sources and event handlers.
     */
    private void initFilters() {
        systemsFilter.setCellFactory(CheckBoxListCell.forListView(this::systemsFilterCellValue));

        sourcesFilter.setCellFactory(CheckBoxListCell.forListView(this::sourcesFilterCellValue));

        cbbxDriverStatus.setItems(FXCollections.observableArrayList(Driver.StatusType.values()));
        cbbxFilterCabinetType.setItems(FXCollections.observableArrayList(CabinetType.values()));
        cbbxFilterDisplayOrientation.setItems(FXCollections.observableArrayList(DisplayOrientation.values()));
        cbbxSWMinSupportedLvl.setItems(FXCollections.observableArrayList(Supported.values()));
        chckbxIncludeClones.setOnAction(_ -> includeClones());
        chckbxIncludeDisks.setOnAction(_ -> includeDisks());
        chckbxIncludeSamples.setOnAction(_ -> includeSamples());
        cbbxFilterCabinetType.setOnAction(_ -> filterCabinetType());
        cbbxFilterDisplayOrientation.setOnAction(_ -> filterDisplayOrientation());
        cbbxDriverStatus.setOnAction(_ -> driverStatus());
        cbbxSWMinSupportedLvl.setOnAction(_ -> swMinSupportedLevel());
        cbbxYearMin.setOnAction(_ -> yearMin());
        cbbxYearMax.setOnAction(_ -> yearMax());
    }

    /**
     * Handles changes to the maximum year filter, updating the profile setting.
     */
    private void yearMax() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_YearMax, cbbxYearMax.getValue());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the minimum year filter, updating the profile setting.
     */
    private void yearMin() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_YearMin, cbbxYearMin.getValue());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the software minimum supported level filter.
     */
    private void swMinSupportedLevel() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_MinSoftwareSupportedLevel, cbbxSWMinSupportedLvl.getValue().toString());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the driver status filter.
     */
    private void driverStatus() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_DriverStatus, cbbxDriverStatus.getValue().toString());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the display orientation filter.
     */
    private void filterDisplayOrientation() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_DisplayOrientation, cbbxFilterDisplayOrientation.getValue().toString());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the cabinet type filter.
     */
    private void filterCabinetType() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_CabinetType, cbbxFilterCabinetType.getValue().toString());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the include samples filter checkbox.
     */
    private void includeSamples() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_InclSamples, chckbxIncludeSamples.isSelected());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the include disks filter checkbox.
     */
    private void includeDisks() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_InclDisks, chckbxIncludeDisks.isSelected());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Handles changes to the include clones filter checkbox.
     */
    private void includeClones() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_InclClones, chckbxIncludeClones.isSelected());
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Provides an observable boolean value for the sources filter check-box list.
     *
     * @param item the source to bind
     * @return an {@link ObservableValue} reflecting the selection state of the source
     */
    private ObservableValue<Boolean> sourcesFilterCellValue(Source item) {
        BooleanProperty observable = new SimpleBooleanProperty(item.isSelected(session.getCurrProfile()));
        observable.addListener((_, _, isNowSelected) -> {
            item.setSelected(session.getCurrProfile(), isNowSelected);
            ProfileViewer.getResetCounter().incrementAndGet();
        });
        return observable;
    }

    /**
     * Provides an observable boolean value for the systems filter check-box list.
     *
     * @param item the system to bind
     * @return an {@link ObservableValue} reflecting the selection state of the system
     */
    private ObservableValue<Boolean> systemsFilterCellValue(Systm item) {
        BooleanProperty observable = new SimpleBooleanProperty(item.isSelected(session.getCurrProfile()));
        observable.addListener((_, _, isNowSelected) -> {
            item.setSelected(session.getCurrProfile(), isNowSelected);
            ProfileViewer.getResetCounter().incrementAndGet();
        });
        return observable;
    }

    /**
     * Initializes the NPlayers filter view with drag-and-drop file loading and check-box cell factory.
     */
    private void initNPlayers() {
        new DragNDrop(tfNPlayers).addFile(this::selectNPlayersFile);
        listNPlayers.setCellFactory(CheckBoxListCell.forListView(this::nPlayersCellValue));
    }

    /**
     * Provides an observable boolean value for the NPlayers check-box list.
     *
     * @param item the NPlayer entry to bind
     * @return an {@link ObservableValue} reflecting the selection state
     */
    private ObservableValue<Boolean> nPlayersCellValue(NPlayer item) {
        BooleanProperty observable = new SimpleBooleanProperty(item.isSelected(session.getCurrProfile()));
        observable.addListener((_, _, isNowSelected) -> {
            item.setSelected(session.getCurrProfile(), isNowSelected);
            ProfileViewer.getResetCounter().incrementAndGet();
        });
        return observable;
    }

    /**
     * Initializes the CatVer filter tree view with drag-and-drop file loading and check-box tree cell factory.
     */
    private void initCatVer() {
        new DragNDrop(tfCatVer).addFile(this::selectCatVerFile);
        treeCatVer.setCellFactory(CheckBoxTreeCell.forTreeView(this::catVerCellValue, catVerStringConverter()));
    }

    /**
     * Creates a string converter for CatVer tree items.
     *
     * @return a {@link StringConverter} that reads the string representation of tree items
     */
    private StringConverter<TreeItem<PropertyStub>> catVerStringConverter() {
        return new StringConverter<TreeItem<PropertyStub>>() {

            @Override
            public String toString(TreeItem<PropertyStub> object) {
                return object.getValue().toString();
            }

            @Override
            public TreeItem<PropertyStub> fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Provides the selected property of CatVer tree items for the check-box cell factory.
     *
     * @param item the tree item
     * @return the selected property, or {@code null} if the item is not a {@link CheckBoxTreeItem}
     */
    private ObservableValue<Boolean> catVerCellValue(TreeItem<PropertyStub> item) {
        if (item instanceof CheckBoxTreeItem<?> i)
            return i.selectedProperty();
        return null;
    }

    /**
     * Initializes the scan automation combo box with available {@link ScanAutomation} values.
     */
    private void initAutomation() {
        cbAutomation.setItems(FXCollections.observableArrayList(ScanAutomation.values()));
        cbAutomation.setCellFactory(_ -> new DescriptorCellFactory());
        cbAutomation.setButtonCell(cbAutomation.getCellFactory().call(null));
        cbAutomation.setOnAction(_ -> session.getCurrProfile().setProperty(ProfileSettingsEnum.automation_scan, cbAutomation.getValue().toString()));
    }

    /**
     * Initializes the import and export button event handlers.
     */
    private void initImportExport() {
        importBtn.setOnAction(_ -> doImport());

        exportBtn.setOnAction(_ -> doExport());
    }

    /**
     * Opens a save dialog to export the current profile's settings to a properties file.
     */
    private void doExport() {
        final var filters = Arrays.asList(new ExtensionFilter("Properties", "*.properties"));
        final var presets = session.getUser().getSettings().getWorkPath().resolve("presets");
        try {
            Files.createDirectories(presets);
            chooseSaveFile(exportBtn, null, presets.toFile(), filters,
                    file -> session.getCurrProfile().saveSettings(PathAbstractor.getAbsolutePath(session, file.toString()).toFile()));
        } catch (IOException e1) {
            Log.err(e1.getMessage(), e1);
        }
    }

    /**
     * Opens a file picker to import profile settings from a properties file, then reloads filters and UI.
     */
    private void doImport() {
        final var filters = Arrays.asList(new ExtensionFilter("Properties", "*.properties"));
        final var presets = session.getUser().getSettings().getWorkPath().resolve("presets");
        chooseOpenFile(importBtn, null, presets.toFile(), filters, file -> {
            session.getCurrProfile().loadSettings(PathAbstractor.getAbsolutePath(session, file.toString()).toFile());
            session.getCurrProfile().loadCatVer(null);
            session.getCurrProfile().loadNPlayers(null);
            initProfileSettings(session);
        });
    }

    /**
     * Saves the current profile and loads the specified profile, refreshing the UI.
     *
     * @param session the current user session
     * @param profile the profile to load
     */
    @Override
    public void loadProfile(Session session, ProfileNFO profile) {
        if (session.getCurrProfile() != null)
            session.getCurrProfile().saveSettings();

        if (MainFrame.getProfileViewer() != null)
            MainFrame.getProfileViewer().clear();

        ProgressTaskRunner.run((Stage) romsDest.getScene().getWindow(), stage -> loadProfileTask(session, profile, stage));
    }

    /**
     * Creates a background task that loads a {@link Profile} and updates the scanner UI on success.
     *
     * @param session the current user session
     * @param profile the profile descriptor to load
     * @param stage   the parent stage for the progress dialog
     * @return a {@link ProgressTask} that loads the profile
     * @throws IOException        if the profile files cannot be read
     * @throws URISyntaxException if a profile URI is malformed
     */
    private ProgressTask<Profile> loadProfileTask(Session session, ProfileNFO profile, Stage stage) throws IOException, URISyntaxException {
        return new ProgressTask<Profile>(stage) {
            @Override
            protected Profile call() throws Exception {
                return Profile.load(session, profile, this);
            }

            @Override
            protected void succeeded() {
                try {
                    profileLoaded(session, get());
                    this.close();
                } catch (InterruptedException e) // NOSONAR
                {
                    ProgressTaskRunner.handleInterruptedException(this, e);
                } catch (ExecutionException e) {
                    ProgressTaskRunner.handleExecutionException(this, e);
                }
            }

            /**
             * Updates the UI after a profile has been successfully loaded.
             */
            private void profileLoaded(Session session, final Profile profile) {
                session.getReport().setProfile(session.getCurrProfile());
                MainFrame.getReportFrame().setNeedUpdate(true);

                ProfileViewer.getResetCounter().incrementAndGet();

                MainFrame.getController().getScannerPanelTab().setDisable(profile == null);
                scanBtn.setDisable(profile == null);
                fixBtn.setDisable(true);
                if (profile != null && session.getCurrProfile() != null) {
                    profileinfoLbl.getChildren().setAll(NeutralToNodeFormatter.toNodes(session.getCurrProfile().getName()));
                    systemsFilter.setItems(FXCollections.observableList(session.getCurrProfile().getSystems().getSystems()));
                    sourcesFilter.setItems(FXCollections.observableList(session.getCurrProfile().getSources().getSrces()));
                    initProfileSettings(session);
                    MainFrame.getController().getTabPane().getSelectionModel().select(1);
                    MainFrame.getController().getProfilePanelController().refreshList();
                }
            }

            @Override
            protected void failed() {
                ProgressTaskRunner.handleFailedException(this, getException());
            }
        };
    }

    /**
     * FXML handler for the scan button.
     *
     * @param e the action event
     */
    @FXML
    private void scan(ActionEvent e) {
        scan(session, true);
    }

    /**
     * Validates the ROMs destination directory and starts a scan in a background task.
     * If the destination is empty, it opens the directory chooser first.
     *
     * @param session  the current user session
     * @param automate whether scan automation rules should be applied after the scan
     */
    private void scan(final Session session, final boolean automate) {
        String txtdstdir = romsDest.getText();
        if (txtdstdir.isEmpty()) {
            romsDestBtn.fire();
            txtdstdir = romsDest.getText();
        }
        if (txtdstdir.isEmpty())
            return;
        ProgressTaskRunner.run((Stage) romsDest.getScene().getWindow(), stage -> scanProgressTask(session, automate, stage));
    }

    /**
     * Creates a background task that performs a ROM scan on the current profile.
     *
     * @param session  the current user session
     * @param automate whether scan automation rules should be applied after the scan
     * @param stage    the parent stage for the progress dialog
     * @return a {@link ProgressTask} that performs the scan
     * @throws IOException        if profile files cannot be read
     * @throws URISyntaxException if a profile URI is malformed
     */
    private ProgressTask<Scan> scanProgressTask(final Session session, final boolean automate, Stage stage) throws IOException, URISyntaxException {
        return new ProgressTask<Scan>(stage) {
            @Override
            protected Scan call() throws Exception {
                return new Scan(session.getCurrProfile(), this);
            }

            @Override
            protected void succeeded() {
                try {
                    handleScanSuccess(session, automate, get(), this);
                } catch (InterruptedException e) // NOSONAR
                {
                    ProgressTaskRunner.handleInterruptedException(this, e);
                } catch (Exception e) {
                    ProgressTaskRunner.handleExecutionException(this, e);
                }
            }

            @Override
            protected void failed() {
                ProgressTaskRunner.handleFailedException(this, getException());
            }

            /**
             * Completes the scan by updating the UI and optionally running automated fix.
             */
            private void handleScanSuccess(final Session session, final boolean automate, final Scan scan, final ProgressTask<Scan> task) {
                session.setCurrScan(scan);
                updateFixButtonState(session);
                task.close();
                updateProfileViewer();
                handleReportFrame(session);
                if (automate && shouldAutoFix(session)) {
                    fix(session);
                }
            }

        };
    }

    /**
     * Enables or disables the fix button based on whether the current scan has pending actions.
     *
     * @param session the current user session
     */
    private void updateFixButtonState(final Session session) {
        boolean hasActions = session.getCurrScan() != null
                && session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0;
        fixBtn.setDisable(!hasActions);
    }

    /**
     * Shows the report frame if the current automation mode includes a report step.
     *
     * @param session the current user session
     */
    private void handleReportFrame(final Session session) {
        if (MainFrame.getReportFrame() == null)
            return;
        ScanAutomation automation = ScanAutomation.valueOf(
                session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
        if (automation.hasReport())
            MainFrame.getReportFrame().setVisible();
        MainFrame.getReportFrame().setNeedUpdate(true);
    }

    /**
     * Determines whether the current automation mode should trigger automatic fixing.
     *
     * @param session the current user session
     * @return {@code true} if auto-fix should run
     */
    private boolean shouldAutoFix(final Session session) {
        ScanAutomation automation = ScanAutomation.valueOf(
                session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
        return !fixBtn.isDisabled() && automation.hasFix();
    }

    /**
     * FXML handler for the report button. Shows the report frame.
     *
     * @param evt the action event
     */
    @FXML
    private void report(ActionEvent evt) {
        MainFrame.getReportFrame().setVisible();
    }

    /**
     * FXML handler for the fix button.
     *
     * @param e the action event
     */
    @FXML
    private void fix(ActionEvent e) {
        fix(session);
    }

    /**
     * Runs the fix operation on the current scan in a background task.
     *
     * @param session the current user session
     */
    private void fix(final Session session) {
        ProgressTaskRunner.run((Stage) romsDest.getScene().getWindow(), stage -> getFixTask(session, stage));
    }

    /**
     * Creates a background task that applies fix operations to the current scan.
     * Prompts for rescan if profile settings have changed since the last scan.
     *
     * @param session the current user session
     * @param stage   the parent stage for the progress dialog
     * @return a {@link ProgressTask} that performs the fix
     * @throws IOException        if profile files cannot be read
     * @throws URISyntaxException if a profile URI is malformed
     */
    private ProgressTask<Fix> getFixTask(final Session session, Stage stage) throws IOException, URISyntaxException {
        return new ProgressTask<Fix>(stage) {

            @Override
            protected Fix call() throws Exception {
                if (!confirmRescanIfNeeded(session, this))
                    return null;
                return new Fix(session.getCurrProfile(), session.getCurrScan(), this);
            }

            @Override
            protected void succeeded() {
                try {
                    final var fix = get();
                    if (fix != null)
                        fixBtn.setDisable(fix.getActionsRemain() <= 0);
                    close();
                    updateProfileViewer();
                    runScanAutomation(session);
                } catch (InterruptedException e) // NOSONAR
                {
                    ProgressTaskRunner.handleInterruptedException(this, e);
                } catch (Exception e) {
                    ProgressTaskRunner.handleExecutionException(this, e);
                }
            }

            @Override
            protected void failed() {
                ProgressTaskRunner.handleFailedException(this, getException());
            }
        };
    }

    /**
     * Prompts the user to rescan before fixing if profile settings have changed since the last scan.
     *
     * @param session the current user session
     * @param task    the current progress task for progress reporting during rescan
     * @return {@code true} to proceed with the fix, {@code false} to cancel
     * @throws Exception if the rescan encounters an error
     */
    private boolean confirmRescanIfNeeded(final Session session, final ProgressTask<?> task) throws Exception /* NOSONAR */ {
        if (!session.getCurrProfile().hasPropsChanged())
            return true;
        final var answer = Dialogs.showConfirmation(Messages.getString("MainFrame.WarnSettingsChanged"), Messages.getString("MainFrame.RescanBeforeFix"),
                ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        if (answer.isEmpty())
            return true;
        if (answer.get() == ButtonType.CANCEL)
            return false;
        if (answer.get() == ButtonType.YES) {
            session.setCurrScan(new Scan(session.getCurrProfile(), task));
            return session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0;
        }
        return true;
    }

    /**
     * Triggers a reload of the profile viewer if it is open.
     */
    private void updateProfileViewer() {
        if (MainFrame.getProfileViewer() != null)
            MainFrame.getProfileViewer().reload();
    }

    /**
     * Runs the next automation step (re-scan or report) after a fix completes, if configured.
     *
     * @param session the current user session
     */
    private void runScanAutomation(final Session session) {
        ScanAutomation automation = ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
        if (automation.hasScanAgain())
            scan(session, false);
    }

    /**
     * Refreshes all UI controls from the current profile settings: destinations, source list,
     * scanner settings, filters, NPlayers, CatVer, and automation.
     *
     * @param session the current user session
     */
    private void initProfileSettings(Session session) {
        initDestSettings(session);
        initSrcListSettings(session);
        scannerPanelSettingsController.initProfileSettings(session.getCurrProfile().getSettings());
        initFilterSettings(session);
        showNPlayers();
        showCatVer();
        initAutomationSettings(session);
    }

    /**
     * Initializes destination text fields and checkboxes from the current profile settings.
     *
     * @param session the current user session
     */
    void initDestSettings(Session session) {
        romsDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.roms_dest_dir));
        disksDestCB.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class));
        disksDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.disks_dest_dir));
        swDestCB.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class));
        swDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.swroms_dest_dir));
        swDisksDestCB.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class));
        swDisksDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.swdisks_dest_dir));
        samplesDestCB.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.samples_dest_dir_enabled, Boolean.class));
        samplesDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.samples_dest_dir));
        backupDestCB.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class));
        backupDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.backup_dest_dir));
    }

    /**
     * Populates the source file list from the pipe-delimited source directory profile setting.
     *
     * @param session the current user session
     */
    void initSrcListSettings(Session session) {
        srcList.setItems(FXCollections.observableList(Stream.of(StringUtils.split(session.getCurrProfile().getProperty(ProfileSettingsEnum.src_dir), '|'))
                .filter(s -> !s.isEmpty())
                .map(File::new)
                .collect(Collectors.toList()))); // NOSONAR
    }

    /**
     * Initializes filter checkboxes and combo boxes from the current profile settings.
     *
     * @param session the current user session
     */
    void initFilterSettings(Session session) {
        chckbxIncludeClones.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_InclClones, Boolean.class));
        chckbxIncludeDisks.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_InclDisks, Boolean.class));
        chckbxIncludeSamples.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_InclSamples, Boolean.class));
        cbbxDriverStatus.getSelectionModel().select(Driver.StatusType.valueOf(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_DriverStatus)));
        cbbxFilterCabinetType.getSelectionModel().select(CabinetType.valueOf(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_CabinetType)));
        cbbxFilterDisplayOrientation.getSelectionModel().select(DisplayOrientation.valueOf(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_DisplayOrientation)));
        cbbxSWMinSupportedLvl.getSelectionModel().select(Supported.valueOf(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_MinSoftwareSupportedLevel)));
        cbbxYearMin.setItems(FXCollections.observableArrayList(session.getCurrProfile().getYears()).sorted());
        cbbxYearMin.getSelectionModel().select(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_YearMin));
        cbbxYearMax.setItems(FXCollections.observableArrayList(session.getCurrProfile().getYears()).sorted());
        cbbxYearMax.getSelectionModel().select(session.getCurrProfile().getProperty(ProfileSettingsEnum.filter_YearMax));
    }

    /**
     * Selects the current automation mode in the combo box from profile settings.
     *
     * @param session the current user session
     */
    void initAutomationSettings(Session session) {
        cbAutomation.getSelectionModel().select(ScanAutomation.valueOf(session.getCurrProfile().getProperty(ProfileSettingsEnum.automation_scan)));
    }

    /**
     * Loads an NPlayers definitions file from the given path and refreshes the NPlayers list.
     *
     * @param file the absolute path to an NPlayers INI file
     */
    private void selectNPlayersFile(String file) {
        if (Files.isRegularFile(Path.of(file))) {
            session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_nplayers_ini, file);
            session.getCurrProfile().loadNPlayers(null);
            showNPlayers();
        }
    }

    /**
     * Refreshes the NPlayers text field and list view from the current profile data.
     */
    private void showNPlayers() {
        tfNPlayers.setText(session.getCurrProfile().getNplayers() != null ? session.getCurrProfile().getNplayers().file.getAbsolutePath() : null);
        listNPlayers.setItems(Optional.ofNullable(session.getCurrProfile().getNplayers()).map(NPlayers::getListNPlayers).map(FXCollections::observableArrayList).orElse(null));
    }

    /**
     * Loads a CatVer definitions file from the given path and refreshes the CatVer tree.
     *
     * @param file the absolute path to a CatVer INI file
     */
    private void selectCatVerFile(String file) {
        if (Files.isRegularFile(Path.of(file))) {
            session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_catver_ini, file);
            session.getCurrProfile().loadCatVer(null);
            showCatVer();
        }
    }

    /**
     * Rebuilds the CatVer category tree view from the current profile data, wiring selection bindings.
     */
    private void showCatVer() {
        tfCatVer.setText(session.getCurrProfile().getCatver() != null ? session.getCurrProfile().getCatver().file.getAbsolutePath() : null);

        final var root = session.getCurrProfile().getCatver();
        if (root != null) {
            final var rootitem = new CheckBoxTreeItem<PropertyStub>(root);
            rootitem.setExpanded(true);
            session.getCurrProfile().getCatver().forEach(cat -> {
                final var catitem = new CheckBoxTreeItem<PropertyStub>(cat);
                rootitem.getChildren().add(catitem);
                cat.forEach(subcat -> catitem.getChildren().add(new CheckBoxTreeItem<>(subcat)));
            });
            treeCatVer.setRoot(rootitem);

            rootitem.selectedProperty().addListener((_, _, newvalue) -> root.setSelected(newvalue));
            rootitem.getChildren().forEach(catitem -> {
                if (catitem instanceof CheckBoxTreeItem<?> cbti && catitem.getValue() instanceof Category c)
                    cbti.selectedProperty().addListener((_, _, newvalue) -> c.setSelected(newvalue));
                catitem.getChildren().forEach(subcatitem -> {
                    if (subcatitem instanceof CheckBoxTreeItem<?> cbti && subcatitem.getValue() instanceof SubCategory sc) {
                        cbti.selectedProperty().addListener((_, _, newvalue) -> updateSubCategorySelection(sc, newvalue));
                        cbti.setSelected(sc.isSelected());
                    }
                });
            });

        } else
            treeCatVer.setRoot(null);

    }

    /**
     * Updates a sub-category selection state and refreshes the CatVer tree view.
     *
     * @param sc       the sub-category to update
     * @param newvalue the new selection state
     */
    private void updateSubCategorySelection(SubCategory sc, Boolean newvalue) {
        sc.setSelected(newvalue);
        treeCatVer.refresh();
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * FXML handler for choosing the ROMs destination directory.
     *
     * @param e the action event
     */
    @FXML
    private void chooseRomsDest(ActionEvent e) {
        chooseAnyDest(romsDest, ProfileSettingsEnum.roms_dest_dir, "MainFrame.ChooseRomsDestination");
    }

    /**
     * FXML handler for choosing the disks destination directory.
     *
     * @param e the action event
     */
    @FXML
    private void chooseDisksDest(ActionEvent e) {
        chooseAnyDest(disksDest, ProfileSettingsEnum.disks_dest_dir, "MainFrame.ChooseDisksDestination");
    }

    /**
     * FXML handler for choosing the software ROMs destination directory.
     *
     * @param e the action event
     */
    @FXML
    private void chooseSWRomsDest(ActionEvent e) {
        chooseAnyDest(swDest, ProfileSettingsEnum.swroms_dest_dir, "MainFrame.ChooseSWRomsDestination");
    }

    /**
     * FXML handler for choosing the software disks destination directory.
     *
     * @param e the action event
     */
    @FXML
    private void chooseSWDisksDest(ActionEvent e) {
        chooseAnyDest(swDisksDest, ProfileSettingsEnum.swdisks_dest_dir, "MainFrame.ChooseSWDisksDestination");
    }

    /**
     * FXML handler for choosing the samples destination directory.
     *
     * @param e the action event
     */
    @FXML
    private void chooseSamplesDest(ActionEvent e) {
        chooseAnyDest(samplesDest, ProfileSettingsEnum.samples_dest_dir, "MainFrame.ChooseSamplesDestination");
    }

    /**
     * FXML handler for choosing the backup destination directory.
     *
     * @param e the action event
     */
    @FXML
    private void chooseBackupDest(ActionEvent e) {
        chooseAnyDest(backupDest, ProfileSettingsEnum.backup_dest_dir, "MainFrame.ChooseBackupDestination");
    }

    /**
     * Opens a directory chooser for a destination and saves the selected path to the profile.
     *
     * @param tf          the text field to display the chosen path
     * @param ppt         the profile settings key to persist the path under
     * @param defPptName   the default directory settings key
     */
    private void chooseAnyDest(TextField tf, ProfileSettingsEnum ppt, String defPptName) {
        final var workdir = session.getUser().getSettings().getWorkPath().toFile();
        final var defdir = PathAbstractor.getAbsolutePath(session, session.getUser().getSettings().getProperty(defPptName, workdir.getAbsolutePath())).toFile();
        chooseDir(tf, tf.getText(), defdir, dir -> applyChosenDir(tf, ppt, defPptName, dir));
    }

    /**
     * Updates the destination text field and persists the selection to both user and profile settings.
     *
     * @param tf          the text field to update
     * @param ppt         the profile settings key
     * @param defPptName   the default directory settings key for user preferences
     * @param dir         the chosen directory path
     */
    private void applyChosenDir(TextField tf, ProfileSettingsEnum ppt, String defPptName, Path dir) {
        tf.setText(dir.toString());
        session.getUser().getSettings().setProperty(defPptName, tf.getText()); // $NON-NLS-1$
        session.getCurrProfile().setProperty(ppt, tf.getText()); // $NON-NLS-1$
    }

    /**
     * Opens a directory chooser for adding or changing a ROM source directory.
     *
     * @param oldDir     the currently selected directory, or {@code null} to add a new one
     * @param ppt        the profile settings key
     * @param defPptName  the default directory settings key
     */
    private void chooseSrc(File oldDir, ProfileSettingsEnum ppt, String defPptName) /* NOSONAR */ {
        final var workdir = session.getUser().getSettings().getWorkPath().toFile();
        final var defdir = PathAbstractor.getAbsolutePath(session, session.getUser().getSettings().getProperty(defPptName, workdir.getAbsolutePath())).toFile();
        chooseDir(srcList, oldDir != null ? oldDir.toString() : null, defdir, dir -> handleChosenSrcDir(oldDir, defPptName, dir));
    }

    /**
     * Updates the source list after a directory is chosen: replaces an existing entry or adds a new one.
     *
     * @param oldDir      the existing directory being replaced, or {@code null} for new additions
     * @param defPptName   the default directory settings key for user preferences
     * @param dir         the chosen directory path
     */
    private void handleChosenSrcDir(File oldDir, String defPptName, Path dir) {
        var modified = false;
        if (oldDir != null) {
            if (!oldDir.equals(dir.toFile())) {
                final var i = srcList.getItems().indexOf(oldDir);
                srcList.getItems().set(i, dir.toFile());
                modified = true;
            }
        } else {
            if (-1 == srcList.getItems().indexOf(dir.toFile())) {
                srcList.getItems().add(dir.toFile());
                modified = true;
            }
        }
        if (modified) {
            saveSrcList();
            session.getUser().getSettings().setProperty(defPptName, dir.toString()); // $NON-NLS-1$
        }
    }

    /**
     * Persists the current source list to the profile as a pipe-delimited string of absolute paths.
     */
    private void saveSrcList() {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.src_dir, String.join("|", srcList.getItems().stream().map(File::getAbsolutePath).toList()));
    }

    /**
     * FXML handler for the profile info button. Opens the profile viewer, creating it lazily if necessary.
     *
     * @param evt the action event
     */
    @FXML
    private void infos(ActionEvent evt) {
        if (MainFrame.getProfileViewer() == null) {
            try {
                MainFrame.setProfileViewer(new ProfileViewer((Stage) infosBtn.getScene().getWindow()));
            } catch (IOException | URISyntaxException e) {
                Log.err(e.getMessage(), e);
            }
        }
        if (MainFrame.getProfileViewer() != null) {
            MainFrame.getProfileViewer().show();
            MainFrame.applyCSS();
            MainFrame.getProfileViewer().reload();
        }
    }

    /**
     * Selects all systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterSelectAll() {
        FilterSelectionHelper.selectAll(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, systemsFilter);
    }

    /**
     * Selects only BIOS systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterSelectAllBios() {
        FilterSelectionHelper.selectFiltered(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, s -> s.getType() == Systm.Type.BIOS, systemsFilter);
    }

    /**
     * Selects only software list systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterSelectAllSoftwares() {
        FilterSelectionHelper.selectFiltered(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, s -> s.getType() == Systm.Type.SOFTWARELIST, systemsFilter);
    }

    /**
     * Unselects all systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterUnselectAll() {
        FilterSelectionHelper.unselectAll(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, systemsFilter);
    }

    /**
     * Unselects only BIOS systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterUnselectAllBios() {
        FilterSelectionHelper.unselectFiltered(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, s -> s.getType() == Systm.Type.BIOS, systemsFilter);
    }

    /**
     * Unselects only software list systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterUnselectAllSoftwares() {
        FilterSelectionHelper.unselectFiltered(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, s -> s.getType() == Systm.Type.SOFTWARELIST, systemsFilter);
    }

    /**
     * Inverts the selection of all systems in the systems filter and refreshes the list view.
     */
    @FXML
    private void systemsFilterInvertSelection() {
        FilterSelectionHelper.invertSelection(session.getCurrProfile().getSystems(), session.getCurrProfile(),
                Systm::setSelected, Systm::isSelected, systemsFilter);
    }

    /**
     * Selects all sources in the sources filter and refreshes the list view.
     */
    @FXML
    private void sourcesFilterSelectAll() {
        FilterSelectionHelper.selectAll(session.getCurrProfile().getSources(), session.getCurrProfile(),
                Source::setSelected, sourcesFilter);
    }

    /**
     * Unselects all sources in the sources filter and refreshes the list view.
     */
    @FXML
    private void sourcesFilterUnselectAll() {
        FilterSelectionHelper.unselectAll(session.getCurrProfile().getSources(), session.getCurrProfile(),
                Source::setSelected, sourcesFilter);
    }

    /**
     * Inverts the selection of all sources in the sources filter and refreshes the list view.
     */
    @FXML
    private void sourcesFilterInvertSelection() {
        FilterSelectionHelper.invertSelection(session.getCurrProfile().getSources(), session.getCurrProfile(),
                Source::setSelected, Source::isSelected, sourcesFilter);
    }

    /**
     * Selects all NPlayers entries and refreshes the list view.
     */
    @FXML
    void nPlayersListSelectAll() {
        FilterSelectionHelper.selectAll(session.getCurrProfile().getNplayers(), session.getCurrProfile(),
                NPlayer::setSelected, listNPlayers);
    }

    /**
     * Unselects all NPlayers entries and refreshes the list view.
     */
    @FXML
    void nPlayersListSelectNone() {
        FilterSelectionHelper.unselectAll(session.getCurrProfile().getNplayers(), session.getCurrProfile(),
                NPlayer::setSelected, listNPlayers);
    }

    /**
     * Inverts the NPlayers selection and refreshes the list view.
     */
    @FXML
    void nPlayersListSelectInvert() {
        FilterSelectionHelper.invertSelection(session.getCurrProfile().getNplayers(), session.getCurrProfile(),
                NPlayer::setSelected, NPlayer::isSelected, listNPlayers);
    }

    /**
     * Clears the NPlayers definitions file reference and resets the UI.
     */
    @FXML
    void nPlayersListClear() {
        session.getCurrProfile().saveSettings();
        session.getCurrProfile().setNplayers(null);
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_nplayers_ini, null);
        session.getCurrProfile().saveSettings();
        tfNPlayers.setText(null);
        listNPlayers.setItems(null);
    }

    /**
     * Returns a stream of all sub-category check-box tree items in the CatVer tree.
     *
     * @return a stream of {@link CheckBoxTreeItem} for sub-categories
     */
    private Stream<CheckBoxTreeItem<PropertyStub>> streamSubCatItems() {
        return (treeCatVer.getRoot()).getChildren().stream().flatMap(t -> t.getChildren().stream()).map(t -> (CheckBoxTreeItem<PropertyStub>) t);
    }

    /**
     * Selects all CatVer sub-categories.
     */
    @FXML
    void catVerListSelectAll() {
        streamSubCatItems().forEachOrdered(subcat -> subcat.setSelected(true));
        treeCatVer.refresh();
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Unselects all CatVer sub-categories.
     */
    @FXML
    void catVerListUnselectAll() {
        streamSubCatItems().forEachOrdered(subcat -> subcat.setSelected(false));
        treeCatVer.refresh();
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /** Marker string identifying mature categories. */
    private static final String MATURE = "* Mature *";

    /**
     * Returns a stream of CatVer sub-category items that belong to mature categories.
     *
     * @return a filtered stream of mature {@link CheckBoxTreeItem} instances
     */
    private Stream<CheckBoxTreeItem<PropertyStub>> streamMatureItems() {
        return streamSubCatItems().filter(t -> t.getValue() instanceof SubCategory subcat && (subcat.name.endsWith(MATURE) || subcat.getParent().name.endsWith(MATURE)));
    }

    /**
     * Selects only mature CatVer sub-categories.
     */
    @FXML
    void catVerListSelectMature() {
        streamMatureItems().forEachOrdered(subcat -> subcat.setSelected(true));
        treeCatVer.refresh();
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Unselects all mature CatVer sub-categories.
     */
    @FXML
    void catVerListUnselectMature() {
        streamMatureItems().forEachOrdered(subcat -> subcat.setSelected(false));
        treeCatVer.refresh();
        ProfileViewer.getResetCounter().incrementAndGet();
    }

    /**
     * Clears the CatVer definitions file reference and resets the UI.
     */
    @FXML
    void catVerListClear() {
        session.getCurrProfile().saveSettings();
        session.getCurrProfile().setCatver(null);
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_catver_ini, null);
        session.getCurrProfile().saveSettings();
        tfCatVer.setText(null);
        treeCatVer.setRoot(null);
    }

    /**
     * Binds a destination checkbox to enable/disable its associated text field and button,
     * and persists the enabled state to the profile.
     *
     * @param cb          the enable/disable checkbox
     * @param tf          the associated destination text field
     * @param btn         the associated browse button
     * @param enabledProp the profile settings key for the enabled state
     */
    private void initDestCheckBox(CheckBox cb, TextField tf, Button btn, ProfileSettingsEnum enabledProp) {
        cb.selectedProperty().addListener((_, _, newValue) -> {
            tf.setDisable(!newValue);
            btn.setDisable(!newValue);
            session.getCurrProfile().setProperty(enabledProp, newValue);
        });
    }
}
