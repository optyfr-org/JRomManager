package jrm.fx.ui;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ListView.EditEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.util.Callback;
import jrm.fx.ui.controls.DescriptorCellFactory;
import jrm.locale.Messages;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.scan.options.Descriptor;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.HashCollisionOptions;
import jrm.profile.scan.options.MergeOptions;
import lombok.Getter;

/**
 * FXML controller for the scanner panel settings dialog.
 * <p>
 * Manages profile-specific scan settings including hash options, parallelism,
 * create mode, merge options, collision handling, and exclude patterns.
 * Changes are persisted to the profile settings.
 *
 * @since 2.5
 */
public class ScannerPanelSettingsController implements Initializable {

    /** Whether to compute SHA-1/MD5 hashes. */
    @FXML
    private CheckBox needSHA1Chkbx;
    /** Whether to use parallel processing. */
    @FXML
    private CheckBox useParallelismChkbx;
    /** Whether to create missing sets. */
    @FXML
    private CheckBox createMissingSetsChkbx;
    /** Whether to create only complete sets. */
    @FXML
    private CheckBox createOnlyCompleteChkbx;
    /** Whether to ignore unneeded containers. */
    @FXML
    private CheckBox ignoreUnneededContainersChkbx;
    /** Whether to ignore unneeded entries. */
    @FXML
    private CheckBox ignoreUnneededEntriesChkbx;
    /** Whether to ignore unknown containers. */
    @FXML
    private CheckBox ignoreUnknownContainersChkbx;
    /** Whether to use implicit merge. */
    @FXML
    private CheckBox useImplicitMergeChkbx;
    /** Whether to ignore merge-named ROMs. */
    @FXML
    private CheckBox ignoreMergeNameRomsChkbx;
    /** Whether to ignore merge-named disks. */
    @FXML
    private CheckBox ignoreMergeNameDisksChkbx;
    /** Whether to exclude games. */
    @FXML
    private CheckBox excludeGamesChkbx;
    /** Whether to exclude machines. */
    @FXML
    private CheckBox excludeMachinesChkbx;
    /** Whether to enable backup. */
    @FXML
    private CheckBox backupChkbx;
    /** Whether zero-entry ROMs matter. */
    @FXML
    private CheckBox zeroEntryMattersChkbx;
    /** The compression descriptor combo box. */
    @FXML
    private ComboBox<Descriptor> compressionCbx;
    /** The merge mode descriptor combo box. */
    @FXML
    private ComboBox<Descriptor> mergeModeCbx;
    /** The collision mode descriptor combo box. */
    @FXML
    private ComboBox<Descriptor> collisionModeCbx;
    /** The destination exclude glob patterns list. */
    @FXML
    private ListView<String> dstExcludeGlob;

    /** The settings pane container. */
    @FXML
    private Pane settingsPane;

    /** The settings pane context menu (Pleasuredome MAME presets and other actions). */
    @FXML
    private ContextMenu catVerMenu;

    /**
     * Initializes the controller, wiring up all UI bindings, context menus,
     * and icons after FXML injection is complete.
     *
     * @param location the FXML location or {@code null}
     * @param resources the resource bundle or {@code null}
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        final Callback<ListView<Descriptor>, ListCell<Descriptor>> cellFactory = _ -> new DescriptorCellFactory();
        needSHA1Chkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.need_sha1_or_md5, newValue));
        useParallelismChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.use_parallelism, newValue));
        createMissingSetsChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.create_mode, newValue));
        createOnlyCompleteChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.createfull_mode, newValue));
        ignoreUnneededContainersChkbx.selectedProperty()
                .addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.ignore_unneeded_containers, newValue));
        ignoreUnneededEntriesChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.ignore_unneeded_entries, newValue));
        ignoreUnknownContainersChkbx.selectedProperty()
                .addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.ignore_unknown_containers, newValue));
        useImplicitMergeChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.implicit_merge, newValue));
        ignoreMergeNameRomsChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.ignore_merge_name_roms, newValue));
        ignoreMergeNameDisksChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.ignore_merge_name_disks, newValue));
        excludeGamesChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.exclude_games, newValue));
        excludeMachinesChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.exclude_machines, newValue));
        backupChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.backup, newValue));
        zeroEntryMattersChkbx.selectedProperty().addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.zero_entry_matters, newValue));
        compressionCbx.setItems(FXCollections.observableArrayList(FormatOptions.values()));
        compressionCbx.setCellFactory(cellFactory);
        compressionCbx.setButtonCell(cellFactory.call(null));
        compressionCbx.getSelectionModel().selectedItemProperty()
                .addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.format, newValue.toString()));
        mergeModeCbx.setItems(FXCollections.observableArrayList(MergeOptions.values()));
        mergeModeCbx.setCellFactory(cellFactory);
        mergeModeCbx.setButtonCell(cellFactory.call(null));
        mergeModeCbx.getSelectionModel().selectedItemProperty()
                .addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.merge_mode, newValue.toString()));
        collisionModeCbx.setItems(FXCollections.observableArrayList(HashCollisionOptions.values()));
        collisionModeCbx.setCellFactory(cellFactory);
        collisionModeCbx.setButtonCell(cellFactory.call(null));
        collisionModeCbx.getSelectionModel().selectedItemProperty()
                .addListener((_, _, newValue) -> settings.setProperty(ProfileSettingsEnum.hash_collision_mode, newValue.toString()));
        final var pdMameMenuItemMerged = new MenuItem(Messages.getString("MainFrame.mntmPleasuredome.text"));
        pdMameMenuItemMerged.setOnAction(_ -> pdMameMergedPreset());
        final var pdMameMenuItemNonMerged = new MenuItem(Messages.getString("MainFrame.mntmPdMameNon.text"));
        pdMameMenuItemNonMerged.setOnAction(_ -> pdMameNonMergedPreset());
        final var pdMameMenuItemSplit = new MenuItem(Messages.getString("MainFrame.mntmPdMameSplit.text"));
        pdMameMenuItemSplit.setOnAction(_ -> pdMameSplitPreset());
        catVerMenu = new ContextMenu(new Menu(Messages.getString("MainFrame.mnPresets.text"), null, pdMameMenuItemMerged, pdMameMenuItemNonMerged, pdMameMenuItemSplit));
        settingsPane.setOnContextMenuRequested(event -> {
            catVerMenu.show(settingsPane, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        settingsPane.setOnMousePressed(_ -> catVerMenu.hide());
        ImageView addiv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/add.png"));
        addiv.setPreserveRatio(true);
        addiv.getStyleClass().add("icon");
        addDstExcludeGlobMenu.setGraphic(addiv);
        ImageView deliv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/delete.png"));
        deliv.setPreserveRatio(true);
        deliv.getStyleClass().add("icon");
        deleteDstExcludeGlobMenu.setGraphic(deliv);
    }

    /**
     * The profile settings.
     *
     * @return the profile settings
     */
    private @Getter ProfileSettings settings;
    
    /** The destination exclude glob patterns context menu. */
    @FXML
    ContextMenu dstExcludeGlobMenu;
    /** The menu item to add a destination exclude glob pattern. */
    @FXML
    MenuItem addDstExcludeGlobMenu;
    /** The menu item to delete a destination exclude glob pattern. */
    @FXML
    MenuItem deleteDstExcludeGlobMenu;

    /**
     * Initializes the profile settings and binds them to the UI controls.
     *
     * @param settings the profile settings to bind
     */
    void initProfileSettings(final ProfileSettings settings) {
        this.settings = settings;
        needSHA1Chkbx.setSelected(settings.getProperty(ProfileSettingsEnum.need_sha1_or_md5, Boolean.class));
        useParallelismChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.use_parallelism, Boolean.class));
        createMissingSetsChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.create_mode, Boolean.class));
        createOnlyCompleteChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.createfull_mode, Boolean.class));
        ignoreUnneededContainersChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.ignore_unneeded_containers, Boolean.class));
        ignoreUnneededEntriesChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.ignore_unneeded_entries, Boolean.class));
        ignoreUnknownContainersChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.ignore_unknown_containers, Boolean.class));
        useImplicitMergeChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.implicit_merge, Boolean.class));
        ignoreMergeNameRomsChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.ignore_merge_name_roms, Boolean.class));
        ignoreMergeNameDisksChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.ignore_merge_name_disks, Boolean.class));
        excludeGamesChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.exclude_games, Boolean.class));
        excludeMachinesChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.exclude_machines, Boolean.class));
        backupChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.backup, Boolean.class));
        zeroEntryMattersChkbx.setSelected(settings.getProperty(ProfileSettingsEnum.zero_entry_matters, Boolean.class));
        compressionCbx.getSelectionModel().select(FormatOptions.valueOf(settings.getProperty(ProfileSettingsEnum.format)));
        mergeModeCbx.getSelectionModel().select(MergeOptions.valueOf(settings.getProperty(ProfileSettingsEnum.merge_mode)));
        collisionModeCbx.getSelectionModel().select(HashCollisionOptions.valueOf(settings.getProperty(ProfileSettingsEnum.hash_collision_mode)));
        dstExcludeGlob.setCellFactory(TextFieldListCell.forListView());
        loadGlob();
    }

    /**
     * Applies the Pleasuredome MAME merged preset to the UI controls.
     */
    @FXML
    private void pdMameMergedPreset() {
        createMissingSetsChkbx.setSelected(true);
        createOnlyCompleteChkbx.setSelected(false);
        ignoreUnneededContainersChkbx.setSelected(false);
        ignoreUnneededEntriesChkbx.setSelected(false);
        ignoreUnknownContainersChkbx.setSelected(true);
        useImplicitMergeChkbx.setSelected(true);
        ignoreMergeNameDisksChkbx.setSelected(true); // Don't remove _ReadMe_.txt
        ignoreMergeNameRomsChkbx.setSelected(false);
        zeroEntryMattersChkbx.setSelected(false);
        compressionCbx.getSelectionModel().select(FormatOptions.TZIP);
        mergeModeCbx.getSelectionModel().select(MergeOptions.MERGE);
        collisionModeCbx.getSelectionModel().select(HashCollisionOptions.HALFDUMB);
    }

    /**
     * Applies the Pleasuredome MAME non-merged preset to the UI controls.
     */
    @FXML
    private void pdMameNonMergedPreset() {
        createMissingSetsChkbx.setSelected(true);
        createOnlyCompleteChkbx.setSelected(false);
        ignoreUnneededContainersChkbx.setSelected(false);
        ignoreUnneededEntriesChkbx.setSelected(false);
        ignoreUnknownContainersChkbx.setSelected(true); // Don't remove _ReadMe_.txt
        useImplicitMergeChkbx.setSelected(true);
        ignoreMergeNameDisksChkbx.setSelected(true);
        ignoreMergeNameRomsChkbx.setSelected(false);
        zeroEntryMattersChkbx.setSelected(false);
        compressionCbx.getSelectionModel().select(FormatOptions.TZIP);
        mergeModeCbx.getSelectionModel().select(MergeOptions.SUPERFULLNOMERGE);
    }

    /**
     * Applies the Pleasuredome MAME split preset to the UI controls.
     * Unlike the merged preset, the collision mode is left unchanged.
     */
    @FXML
    private void pdMameSplitPreset() {
        createMissingSetsChkbx.setSelected(true);
        createOnlyCompleteChkbx.setSelected(false);
        ignoreUnneededContainersChkbx.setSelected(false);
        ignoreUnneededEntriesChkbx.setSelected(false);
        ignoreUnknownContainersChkbx.setSelected(true); // Don't remove _ReadMe_.txt
        useImplicitMergeChkbx.setSelected(true);
        ignoreMergeNameDisksChkbx.setSelected(true);
        ignoreMergeNameRomsChkbx.setSelected(false);
        compressionCbx.getSelectionModel().select(FormatOptions.TZIP);
        mergeModeCbx.getSelectionModel().select(MergeOptions.SPLIT);
        zeroEntryMattersChkbx.setSelected(false);
    }

    /**
     * Adds a new empty glob pattern to the destination exclude list and starts editing it.
     */
    @FXML
    private void addGlob() {
        dstExcludeGlob.getItems().add("");
        dstExcludeGlob.edit(dstExcludeGlob.getItems().size() - 1);
    }

    /**
     * Deletes the selected glob pattern from the destination exclude list and saves the changes.
     */
    @FXML
    private void delGlob() {
        if (!dstExcludeGlob.getSelectionModel().isEmpty()) {
            dstExcludeGlob.getItems().remove(dstExcludeGlob.getSelectionModel().getSelectedIndex());
            saveGlob();
        }
    }

    /**
     * Commits a glob pattern edit, removing the entry if blank or updating it otherwise.
     * Persists the changes after applying.
     *
     * @param e the edit event containing the old and new values
     */
    @FXML
    private void commitGlob(EditEvent<String> e) {
        if (e.getNewValue().isBlank())
            dstExcludeGlob.getItems().remove(e.getIndex());
        else
            dstExcludeGlob.getItems().set(e.getIndex(), e.getNewValue());
        saveGlob();
    }

    /**
     * Saves the current glob patterns to the profile settings as a pipe-delimited string.
     */
    private void saveGlob() {
        settings.setProperty(ProfileSettingsEnum.exclusion_glob_list, String.join("|", dstExcludeGlob.getItems()));
    }

    /**
     * Loads the glob patterns from the profile settings into the destination exclude list.
     */
    private void loadGlob() {
        dstExcludeGlob.getItems()
                .setAll(Stream.of(StringUtils.split(settings.getProperty(ProfileSettingsEnum.exclusion_glob_list.toString(), "|"), "|")).filter(s -> !s.isEmpty()).toList());
    }
}
