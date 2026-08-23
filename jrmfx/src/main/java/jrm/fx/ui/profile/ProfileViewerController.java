package jrm.fx.ui.profile;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Predicate;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import jrm.fx.ui.JRMScene;
import jrm.fx.ui.MainFrame;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.controls.HashCellFactory;
import jrm.fx.ui.controls.SizeCellFactory;
import jrm.fx.ui.controls.TooltipStringCellFactory;
import jrm.fx.ui.profile.EntityDumpStatusCellFactory;
import jrm.fx.ui.profile.EntityNameCellFactory;
import jrm.fx.ui.profile.EntityStatusCellFactory;
import jrm.fx.ui.profile.AnywareCloneCellFactory;
import jrm.fx.ui.profile.AnywareStatusCellFactory;
import jrm.fx.ui.profile.MachineNameCellFactory;
import jrm.fx.ui.profile.MameLauncher;
import jrm.fx.ui.profile.SampleOfCellFactory;
import jrm.fx.ui.profile.WareListDescCellFactory;
import jrm.fx.ui.profile.WareListHaveCellFactory;
import jrm.fx.ui.profile.WareListNameCellFactory;
import jrm.fx.ui.profile.filter.Keywords;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.Entity.Status;
import jrm.profile.data.EntityBase;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.ExportMode;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;
import jrm.profile.data.Rom;
import jrm.profile.data.Sample;
import jrm.profile.data.Samples;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;
import jrm.profile.manager.Export.ExportType;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * FXML controller for the profile viewer window.
 * <p>
 * Displays profile contents in three linked tables: software/machine lists,
 * individual entries within a selected list, and entity details (ROMs, disks, samples).
 * Supports filtering by status (unknown, missing, partial, complete), keyword search,
 * and context menu actions for copying hashes, searching the web, and exporting.
 *
 * @since 2.5
 */
public class ProfileViewerController implements Initializable {
    private static final String FX_FONT_FAMILY_MONOSPACED = "-fx-font-family: monospaced;";

    private static final String MONOSPACED = "monospaced";

    private static final String D_OF_D_FMT = "%d/%d";

    /** The software/machine list table. */
    @FXML
    private TableView<AnywareList<? extends Anyware>> tableWL;
    /** The software/machine list name column. */
    @FXML
    private TableColumn<AnywareList<? extends Anyware>, AnywareList<? extends Anyware>> tableWLName;
    /** The software/machine list description column. */
    @FXML
    private TableColumn<AnywareList<? extends Anyware>, String> tableWLDesc;
    /** The software/machine list have-count column. */
    @FXML
    private TableColumn<AnywareList<? extends Anyware>, String> tableWLHave;
    /** Toggle to show unknown software/machine lists. */
    @FXML
    private ToggleButton toggleWLUnknown;
    /** Toggle to show missing software/machine lists. */
    @FXML
    private ToggleButton toggleWLMissing;
    /** Toggle to show partial software/machine lists. */
    @FXML
    private ToggleButton toggleWLPartial;
    /** Toggle to show complete software/machine lists. */
    @FXML
    private ToggleButton toggleWLComplete;
    /** The entries table. */
    @FXML
    private TableView<Anyware> tableW;
    /** The machine status column. */
    private final TableColumn<Anyware, Anyware> tableWMStatus = new TableColumn<>(Messages.getString("MachineListRenderer.Status"));
    /** The machine name column. */
    private final TableColumn<Anyware, Machine> tableWMName = new TableColumn<>(Messages.getString("MachineListRenderer.Name"));
    /** The machine description column. */
    private final TableColumn<Anyware, String> tableWMDescription = new TableColumn<>(Messages.getString("MachineListRenderer.Description"));
    /** The machine have-count column. */
    private final TableColumn<Anyware, String> tableWMHave = new TableColumn<>(Messages.getString("MachineListRenderer.Have"));
    /** The machine clone-of column. */
    private final TableColumn<Anyware, Object> tableWMCloneOf = new TableColumn<>(Messages.getString("MachineListRenderer.CloneOf"));
    /** The machine ROM-of column. */
    private final TableColumn<Anyware, Object> tableWMRomOf = new TableColumn<>(Messages.getString("MachineListRenderer.RomOf"));
    /** The machine sample-of column. */
    private final TableColumn<Anyware, Object> tableWMSampleOf = new TableColumn<>(Messages.getString("MachineListRenderer.SampleOf"));
    /** The machine selected checkbox column. */
    private final TableColumn<Anyware, CheckBox> tableWMSelected = new TableColumn<>(Messages.getString("MachineListRenderer.Selected"));
    /** The software status column. */
    private final TableColumn<Anyware, Anyware> tableWSStatus = new TableColumn<>(Messages.getString("SoftwareListRenderer.Status"));
    /** The software name column. */
    private final TableColumn<Anyware, String> tableWSName = new TableColumn<>(Messages.getString("SoftwareListRenderer.Name"));
    /** The software description column. */
    private final TableColumn<Anyware, String> tableWSDescription = new TableColumn<>(Messages.getString("SoftwareListRenderer.Description"));
    /** The software have-count column. */
    private final TableColumn<Anyware, String> tableWSHave = new TableColumn<>(Messages.getString("SoftwareListRenderer.Have"));
    /** The software clone-of column. */
    private final TableColumn<Anyware, Object> tableWSCloneOf = new TableColumn<>(Messages.getString("SoftwareListRenderer.CloneOf"));
    /** The software selected checkbox column. */
    private final TableColumn<Anyware, CheckBox> tableWSSelected = new TableColumn<>(Messages.getString("SoftwareListRenderer.Selected"));
    /** Toggle to show unknown entries. */
    @FXML
    private ToggleButton toggleWUnknown;
    /** Toggle to show missing entries. */
    @FXML
    private ToggleButton toggleWMissing;
    /** Toggle to show partial entries. */
    @FXML
    private ToggleButton toggleWPartial;
    /** Toggle to show complete entries. */
    @FXML
    private ToggleButton toggleWComplete;
    /** The keyword search text field. */
    @FXML
    private TextField search;
    /** The entity details table. */
    @FXML
    private TableView<EntityBase> tableEntity;
    /** The entity status column. */
    @FXML
    private TableColumn<EntityBase, EntityBase> tableEntityStatus;
    /** The entity name column. */
    @FXML
    private TableColumn<EntityBase, EntityBase> tableEntityName;
    /** The entity size column. */
    @FXML
    private TableColumn<EntityBase, Long> tableEntitySize;
    /** The entity CRC column. */
    @FXML
    private TableColumn<EntityBase, String> tableEntityCRC;
    /** The entity MD5 column. */
    @FXML
    private TableColumn<EntityBase, String> tableEntityMD5;
    /** The entity SHA-1 column. */
    @FXML
    private TableColumn<EntityBase, String> tableEntitySHA1;
    /** The entity merge name column. */
    @FXML
    private TableColumn<EntityBase, String> tableEntityMergeName;
    /** The entity dump status column. */
    @FXML
    private TableColumn<EntityBase, Entity.Status> tableEntityDumpStatus;
    /** Toggle to show unknown entities. */
    @FXML
    private ToggleButton toggleEntityUnknown;
    /** Toggle to show KO entities. */
    @FXML
    private ToggleButton toggleEntityKO;
    /** Toggle to show OK entities. */
    @FXML
    private ToggleButton toggleEntityOK;

    /** The software/machine list context menu. */
    @FXML
    private ContextMenu menuWL;
    /** Menu item: export filtered as Logiqx DAT. */
    @FXML
    private MenuItem mntmFilteredAsLogiqxDat;
    /** Menu item: export filtered as MAME DAT. */
    @FXML
    private MenuItem mntmFilteredAsMameDat;
    /** Menu item: export filtered as software lists. */
    @FXML
    private MenuItem mntmFilteredAsSoftwareLists;
    /** Menu item: export all as Logiqx DAT. */
    @FXML
    private MenuItem mntmAllAsLogiqxDat;
    /** Menu item: export all as MAME DAT. */
    @FXML
    private MenuItem mntmAllAsMameDat;
    /** Menu item: export all as software lists. */
    @FXML
    private MenuItem mntmAllAsSoftwareLists;
    /** Menu item: export selected filtered as software lists. */
    @FXML
    private MenuItem mntmSelectedFilteredAsSoftwareLists;
    /** Menu item: export selected as software lists. */
    @FXML
    private MenuItem mntmSelectedAsSoftwareLists;
    /** The entry context menu. */
    @FXML
    private ContextMenu menuW;
    /** Menu item: select by keywords. */
    @FXML
    private MenuItem mntmSelectByKeywords;
    /** Menu item: select all. */
    @FXML
    private MenuItem mntmSelectAll;
    /** Menu item: select none. */
    @FXML
    private MenuItem mntmSelectNone;
    /** Menu item: invert selection. */
    @FXML
    private MenuItem mntmSelectInvert;
    /** The entity context menu. */
    @FXML
    private ContextMenu menuEntity;
    /** Menu item: copy CRC. */
    @FXML
    private MenuItem mntmCopyCrc;
    /** Menu item: copy SHA-1. */
    @FXML
    private MenuItem mntmCopySha1;
    /** Menu item: copy name. */
    @FXML
    private MenuItem mntmCopyName;
    /** Menu item: search web. */
    @FXML
    private MenuItem mntmSearchWeb;

    /** Icon for complete software/machine list. */
    private static final Image diskMultipleGreen = MainFrame.getIcon("/jrm/resicons/disk_multiple_green.png"); //$NON-NLS-1$
    /** Icon for partial software/machine list. */
    private static final Image diskMultipleOrange = MainFrame.getIcon("/jrm/resicons/disk_multiple_orange.png"); //$NON-NLS-1$
    /** Icon for missing software/machine list. */
    private static final Image diskMultipleRed = MainFrame.getIcon("/jrm/resicons/disk_multiple_red.png"); //$NON-NLS-1$
    /** Icon for unknown software/machine list. */
    private static final Image diskMultipleGray = MainFrame.getIcon("/jrm/resicons/disk_multiple_gray.png"); //$NON-NLS-1$
    /** Icon for complete status. */
    private static final Image folderClosedGreen = MainFrame.getIcon("/jrm/resicons/folder_closed_green.png"); //$NON-NLS-1$
    /** Icon for partial status. */
    private static final Image folderClosedOrange = MainFrame.getIcon("/jrm/resicons/folder_closed_orange.png"); //$NON-NLS-1$
    /** Icon for missing status. */
    private static final Image folderClosedRed = MainFrame.getIcon("/jrm/resicons/folder_closed_red.png"); //$NON-NLS-1$
    /** Icon for unknown status. */
    private static final Image folderClosedGray = MainFrame.getIcon("/jrm/resicons/folder_closed_gray.png"); //$NON-NLS-1$
    /** Green bullet icon for OK entity status. */
    private static final Image bulletGreen = MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png"); //$NON-NLS-1$
    /** Red bullet icon for KO entity status. */
    private static final Image bulletRed = MainFrame.getIcon("/jrm/resicons/icons/bullet_red.png"); //$NON-NLS-1$
    /** Black bullet icon for unknown entity status. */
    private static final Image bulletBlack = MainFrame.getIcon("/jrm/resicons/icons/bullet_black.png"); //$NON-NLS-1$

    /** Cache of have-count strings keyed by software/machine list name. */
    private final Map<String, String> haveCache = new HashMap<>();

    /** The current user session. */
    private final Session session = Sessions.getSingleSession();

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        initTableWL();
        wareRelations = new ProfileViewerWareRelations(tableWL);
        initTableW();
        initTableE();
    }

    /**
     * Initializes the entity details table with its columns, toggle buttons, and context menu.
     */
    private void initTableE() {
        initTableEStatus();
        initTableEName();
        initTableESize();
        initTableECRC();
        initTableEMD5();
        initTableESHA1();
        initTableEMergeName();
        initTableEDumpStatus();
        initTableEToggles();
        initTableEMenu();
    }

    /**
     * Configures the entity status column with a graphical cell factory and value factory.
     */
    private void initTableEStatus() {
        tableEntityStatus.setCellFactory(_ -> new EntityStatusCellFactory());
        tableEntityStatus.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue()));
    }

    /**
     * Configures the entity name column with cell factory and value factory.
     */
    private void initTableEName() {
        tableEntityName.setCellFactory(_ -> new EntityNameCellFactory());
        tableEntityName.setCellValueFactory(tableEntityStatus.getCellValueFactory());
    }

    /**
     * Configures the entity size column with width, cell factory, and value factory.
     */
    private void initTableESize() {
        tableEntitySize.setMinWidth(getWidth(12));
        tableEntitySize.setPrefWidth(tableEntitySize.getMinWidth());
        tableEntitySize.setMaxWidth(tableEntitySize.getMinWidth() * 2);
        tableEntitySize.setCellFactory(_ -> new SizeCellFactory<>());
        tableEntitySize.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue() instanceof final Rom r ? r.getSize() : null));
    }

    /**
     * Configures the entity CRC column with monospaced font width and cell factory.
     */
    private void initTableECRC() {
        tableEntityCRC.setMinWidth(getWidth(10, MONOSPACED));
        tableEntityCRC.setPrefWidth(tableEntityCRC.getMinWidth());
        tableEntityCRC.setMaxWidth(tableEntityCRC.getMinWidth() * 2);
        tableEntityCRC.setCellFactory(_ -> new HashCellFactory<>());
        tableEntityCRC.setCellValueFactory(p -> new ReadOnlyStringWrapper( p.getValue() instanceof final Rom r ? r.getCrc() : (p.getValue() instanceof final Disk d ? d.getCrc() : null) ));
    }

    /**
     * Configures the entity MD5 column with monospaced font width and cell factory.
     */
    private void initTableEMD5() {
        tableEntityMD5.setMinWidth(getWidth(34, MONOSPACED));
        tableEntityMD5.setPrefWidth(tableEntityMD5.getMinWidth());
        tableEntityMD5.setMaxWidth(tableEntityMD5.getMinWidth() * 2);
        tableEntityMD5.setCellFactory(_ -> new HashCellFactory<>());
        tableEntityMD5.setCellValueFactory(p -> new ReadOnlyStringWrapper( p.getValue() instanceof final Rom r ? r.getMd5() : null ));
    }

    /**
     * Configures the entity SHA-1 column with monospaced font width and cell factory.
     */
    private void initTableESHA1() {
        tableEntitySHA1.setMinWidth(getWidth(42, MONOSPACED));
        tableEntitySHA1.setPrefWidth(tableEntitySHA1.getMinWidth());
        tableEntitySHA1.setMaxWidth(tableEntitySHA1.getMinWidth() * 2);
        tableEntitySHA1.setCellFactory(_ -> new HashCellFactory<>());
        tableEntitySHA1.setCellValueFactory(p -> new ReadOnlyStringWrapper( p.getValue() instanceof final Disk d ? d.getSha1() : null ));
    }

    /**
     * Configures the entity merge name column with value factory.
     */
    private void initTableEMergeName() {
        tableEntityMergeName.setCellValueFactory(p -> new ReadOnlyStringWrapper( p.getValue() instanceof final Rom r ? r.getMerge() : (p.getValue() instanceof final Disk d ? d.getMerge() : null) ));
    }

    /**
     * Configures the entity dump status column with cell factory and value factory.
     */
    private void initTableEDumpStatus() {
        tableEntityDumpStatus.setCellFactory(_ -> new EntityDumpStatusCellFactory());
        tableEntityDumpStatus.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>( p.getValue() instanceof final Rom r ? r.getDumpStatus() : (p.getValue() instanceof final Disk d ? d.getDumpStatus() : null) ));
    }

    /**
     * Initializes the entity table toggle buttons with bullet icons.
     */
    private void initTableEToggles() {
        final ImageView ibb = new ImageView(bulletBlack);
        ibb.setPreserveRatio(true);
        ibb.getStyleClass().add("icon");
        toggleEntityUnknown.setGraphic(ibb);
        final ImageView ibr = new ImageView(bulletRed);
        ibr.setPreserveRatio(true);
        ibr.getStyleClass().add("icon");
        toggleEntityKO.setGraphic(ibr);
        final ImageView ibg = new ImageView(bulletGreen);
        ibg.setPreserveRatio(true);
        ibg.getStyleClass().add("icon");
        toggleEntityOK.setGraphic(ibg);
    }

    /**
     * Initializes the entity table context menu to update item states when shown.
     */
    private void initTableEMenu() {
        menuEntity.setOnShowing(_ -> updateEMenuItemStates());
    }

    /**
     * Enables or disables context menu items based on whether an entity is selected.
     */
    private void updateEMenuItemStates() {
        final boolean has_selected_entity = tableEntity.getSelectionModel().getSelectedItem() != null;
        mntmCopyCrc.setDisable(!has_selected_entity);
        mntmCopySha1.setDisable(!has_selected_entity);
        mntmCopyName.setDisable(!has_selected_entity);
        mntmSearchWeb.setDisable(!has_selected_entity);
    }

    /**
     * Initializes the entries table with its columns, toggle buttons, and selection listeners.
     */
    private void initTableW() {
        tableW.setFixedCellSize(-1);
        initTableWMStatus();
        initTableWMName();
        initTableWMDescription();
        initTableWMHave();
        initTableWMCloneOf();
        initTableWMRomOf();
        initTableWMSampleOf();
        initTableWMSelected();
        initTableWSColumns();
        initToggleButtons();
        tableW.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> reloadE(newValue));
        search.textProperty().addListener((_, _, newValue) -> filteredData.setPredicate(searchPredicate(newValue)));
    }

    /**
     * Configures the machine status column with cell factory and value factory.
     */
    private void initTableWMStatus() {
        tableWMStatus.setResizable(false);
        tableWMStatus.setSortable(false);
        tableWMStatus.setPrefWidth(24);
        tableWMStatus.setCellFactory(_ -> new AnywareStatusCellFactory());
        tableWMStatus.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue()));
    }

    /**
     * Configures the machine name column with width, cell factory, and value factory.
     */
    private void initTableWMName() {
        tableWMName.setMinWidth(50);
        tableWMName.setPrefWidth(100);
        tableWMName.setMaxWidth(200);
        tableWMName.setCellFactory(_ -> {
            final var c = new MachineNameCellFactory();
            c.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleMachineDoubleClick);
            return c;
        });
        tableWMName.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>((Machine) p.getValue()));
        tableWMName.setSortable(true);
    }

    
    /**
     * Handles double-click on a machine cell to launch MAME if the machine is complete.
     *
     * @param event the mouse event
     */
    private void handleMachineDoubleClick(final MouseEvent event) {
        if (event.getClickCount() > 1 && (event.getSource() instanceof final TableCell<?, ?> c
                && (c.getUserData() instanceof final Machine ware))) {
            if (ware.getStatus() == AnywareStatus.COMPLETE) {
                if (session.getCurrProfile() != null) {
                    final var profile = session.getCurrProfile();
                    MameLauncher.launch(ware, profile);
                } else
                    Dialogs.showAlert(Messages.getString("ProfileViewer.NoProfile"));
            } else
                Dialogs.showAlert(String.format(Messages.getString("ProfileViewer.CantLaunchIncompleteSet"), ware.getStatus()));
        }
    }

    /**
     * Configures the machine description column with width, cell factory, and value factory.
     */
    private void initTableWMDescription() {
        tableWMDescription.setMinWidth(100);
        tableWMDescription.setPrefWidth(200);
        tableWMDescription.setMaxWidth(600);
        tableWMDescription.setCellFactory(_ -> new TooltipStringCellFactory<>());
        tableWMDescription.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getDescription().toString()));
        tableWMDescription.setSortable(true);
    }

    /**
     * Configures the have column with cell factory and value factory.
     */
    private void initTableWMHave() {
        tableWMHave.setResizable(true);
        tableWMHave.setSortable(false);
        tableWMHave.setPrefWidth(45);
        tableWMHave.setMaxWidth(90);
        tableWMHave.setCellFactory(_ -> new TooltipStringCellFactory<>());
        tableWMHave.setCellValueFactory(p -> {
            if (p.getValue() instanceof final Machine m) return new ReadOnlyStringWrapper(String.format(D_OF_D_FMT, m.countHave(), m.countAll()));
            return new ReadOnlyStringWrapper(null);
        });
    }

    
    /**
     * Configures the clone-of column with cell factory and value factory.
     */
    private void initTableWMCloneOf() {
        tableWMCloneOf.setSortable(false);
        tableWMCloneOf.setMinWidth(50);
        tableWMCloneOf.setPrefWidth(100);
        tableWMCloneOf.setMaxWidth(200);
        tableWMCloneOf.setCellFactory(_ -> {
            final var c = new AnywareCloneCellFactory();
            c.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleCloneOfDoubleClick);
            return c;
        });
        tableWMCloneOf.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(wareRelations.getCloneOfValue(p.getValue())));
    }

    /**
     * Handles double-click on a clone-of cell to select and scroll to the referenced entry.
     *
     * @param event the mouse event
     */
    private void handleCloneOfDoubleClick(final MouseEvent event) {
        if (event.getClickCount() > 1 && event.getSource() instanceof final TableCell<?, ?> c && (c.getUserData() instanceof final Anyware ware)) {
            final var sm = tableW.getSelectionModel();
            sm.clearSelection();
            sm.select(ware);
            tableW.scrollTo(ware);
        }
    }

    /**
     * Configures the ROM-of column reusing the clone-of cell factory.
     */
    private void initTableWMRomOf() {
        tableWMRomOf.setSortable(false);
        tableWMRomOf.setMinWidth(50);
        tableWMRomOf.setPrefWidth(100);
        tableWMRomOf.setMaxWidth(200);
        tableWMRomOf.setCellFactory(tableWMCloneOf.getCellFactory());
        tableWMRomOf.setCellValueFactory(p -> {
            if (p.getValue() instanceof final Machine m) {
                final AnywareList<? extends Anyware> ml = tableWL.getSelectionModel().getSelectedItem();
                return new ReadOnlyObjectWrapper<>(Optional.ofNullable(m.getRomof()).filter(romof -> !romof.equals(m.getCloneof())).map(romof -> ml.containsName(romof) ? ml.getByName(romof) : romof).orElse(null));
            }
            return new ReadOnlyObjectWrapper<>(null);
        });
    }

    /**
     * Configures the sample-of column with cell factory and value factory.
     */
    private void initTableWMSampleOf() {
        tableWMSampleOf.setSortable(false);
        tableWMSampleOf.setMinWidth(50);
        tableWMSampleOf.setPrefWidth(100);
        tableWMSampleOf.setMaxWidth(200);
        tableWMSampleOf.setCellFactory(_ -> new SampleOfCellFactory());
        tableWMSampleOf.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(wareRelations.getSampleOfValue(p.getValue())));
    }

    /**
     * Configures the machine selected column with checkbox cell factory.
     */
    private void initTableWMSelected() {
        tableWMSelected.setResizable(true);
        tableWMSelected.setSortable(false);
        tableWMSelected.setPrefWidth(30);
        tableWMSelected.setMaxWidth(60);
        tableWMSelected.setCellValueFactory(this::createWMSelectedCell);
    }

    /**
     * Creates a checkbox cell for the selected column, bound to the entry's selected state.
     *
     * @param p the cell data features
     * @return an observable containing the checkbox
     */
    private ObservableValue<CheckBox> createWMSelectedCell(final CellDataFeatures<Anyware, CheckBox> p) {
        final var aw = p.getValue();
        final var checkBox = new CheckBox();
        checkBox.selectedProperty().setValue(aw.isSelected());
        checkBox.selectedProperty().addListener((_, _, newVal) -> aw.setSelected(newVal));
        return new SimpleObjectProperty<>(checkBox);
    }

    /**
     * Configures the software table columns reusing cell factories from the machine columns.
     */
    private void initTableWSColumns() {
        tableWSStatus.setResizable(false);
        tableWSStatus.setSortable(false);
        tableWSStatus.setPrefWidth(20);
        tableWSStatus.setCellFactory(tableWMStatus.getCellFactory());
        tableWSStatus.setCellValueFactory(tableWMStatus.getCellValueFactory());
        tableWSName.setSortable(true);
        tableWSName.setMinWidth(50);
        tableWSName.setPrefWidth(100);
        tableWSName.setCellFactory(tableWMDescription.getCellFactory());
        tableWSName.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getBaseName()));
        tableWSDescription.setSortable(true);
        tableWSDescription.setMinWidth(200);
        tableWSDescription.setPrefWidth(400);
        tableWSDescription.setCellFactory(tableWMDescription.getCellFactory());
        tableWSDescription.setCellValueFactory(tableWMDescription.getCellValueFactory());
        tableWSHave.setResizable(false);
        tableWSHave.setSortable(false);
        tableWSHave.setPrefWidth(45);
        tableWSHave.setCellFactory(tableWMHave.getCellFactory());
        tableWSHave.setCellValueFactory(p -> new ReadOnlyStringWrapper( p.getValue() instanceof final Software s ? String.format(D_OF_D_FMT, s.countHave(), s.countAll()) : null ));
        tableWSCloneOf.setSortable(false);
        tableWSCloneOf.setMinWidth(50);
        tableWSCloneOf.setPrefWidth(100);
        tableWSCloneOf.setCellFactory(tableWMCloneOf.getCellFactory());
        tableWSCloneOf.setCellValueFactory(p -> {
            final AnywareList<? extends Anyware> sl = tableWL.getSelectionModel().getSelectedItem();
            return new ReadOnlyObjectWrapper<>(p.getValue().getCloneof() != null ? sl.getByName(p.getValue().getCloneof()) : null);
        });
        tableWSSelected.setResizable(false);
        tableWSSelected.setSortable(false);
        tableWSSelected.setPrefWidth(30);
        tableWSSelected.setCellValueFactory(tableWMSelected.getCellValueFactory());
    }

    
    /**
     * Initializes the entry toggle buttons with folder icons.
     */
    private void initToggleButtons() {
        final ImageView ifcgray = new ImageView(folderClosedGray);
        ifcgray.setPreserveRatio(true);
        ifcgray.getStyleClass().add("icon");
        toggleWUnknown.setGraphic(ifcgray);
        final ImageView ifcred = new ImageView(folderClosedRed);
        ifcred.setPreserveRatio(true);
        ifcred.getStyleClass().add("icon");
        toggleWMissing.setGraphic(ifcred);
        final ImageView ifcorange = new ImageView(folderClosedOrange);
        ifcorange.setPreserveRatio(true);
        ifcorange.getStyleClass().add("icon");
        toggleWPartial.setGraphic(ifcorange);
        final ImageView ifcgreen = new ImageView(folderClosedGreen);
        ifcgreen.setPreserveRatio(true);
        ifcgreen.getStyleClass().add("icon");
        toggleWComplete.setGraphic(ifcgreen);
    }

    /**
     * Creates a search predicate that filters entries by name or description.
     *
     * @param newValue the search text
     * @return a predicate matching entries whose name or description contains the search text
     */
    private Predicate<? super Anyware> searchPredicate(final String newValue) {
        return t -> {
            if (newValue == null || newValue.isEmpty())
                return true;
            final var lcase = newValue.toLowerCase();
            return t.getBaseName().toLowerCase().contains(lcase) || t.getDescription().toString().toLowerCase().contains(lcase);
        };
    }

    /**
     * Initializes the software/machine list table with columns, toggle buttons, and selection listeners.
     */
    private void initTableWL() {
        tableWL.setFixedCellSize(-1);
        tableWLName.setCellFactory(_ -> new WareListNameCellFactory());
        tableWLName.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue()));
        tableWLName.setSortable(true);
        tableWLDesc.setCellFactory(_ -> new WareListDescCellFactory());
        tableWLDesc.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(getWLDescription(p.getValue())));
        tableWLHave.setCellFactory(_ -> new WareListHaveCellFactory());
        tableWLHave.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(getWLHave(p.getValue())));
        tableWL.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> reloadW(newValue));
        final ImageView idmgray = new ImageView(diskMultipleGray);
        idmgray.setPreserveRatio(true);
        idmgray.getStyleClass().add("icon");
        toggleWLUnknown.setGraphic(idmgray);
        final ImageView idmred = new ImageView(diskMultipleRed);
        idmred.setPreserveRatio(true);
        idmred.getStyleClass().add("icon");
        toggleWLMissing.setGraphic(idmred);
        final ImageView idmorange = new ImageView(diskMultipleOrange);
        idmorange.setPreserveRatio(true);
        idmorange.getStyleClass().add("icon");
        toggleWLPartial.setGraphic(idmorange);
        final ImageView idmgreen = new ImageView(diskMultipleGreen);
        idmgreen.setPreserveRatio(true);
        idmgreen.getStyleClass().add("icon");
        toggleWLComplete.setGraphic(idmgreen);
        menuWL.setOnShowing(_ -> refreshMenuItemAvailability());
    }

    /**
     * Refreshes the availability state of export menu items.
     */
    private void refreshMenuItemAvailability() {
        final boolean has_machines = session.getCurrProfile().getMachineListList().getList().stream().mapToInt(ml -> ml.getList().size()).sum() > 0;
        final boolean has_filtered_machines = session.getCurrProfile().getMachineListList().getFilteredStream().mapToInt(m -> (int) m.countAll()).sum() > 0;
        final boolean has_selected_swlist = tableWL.getSelectionModel().getSelectedItems().size() == 1 && tableWL.getSelectionModel().getSelectedItem() instanceof SoftwareList;
        mntmAllAsMameDat.setDisable(!has_machines);
        mntmAllAsLogiqxDat.setDisable(!has_machines);
        mntmAllAsSoftwareLists.setDisable(session.getCurrProfile().getMachineListList().getSoftwareListList().isEmpty());
        mntmFilteredAsMameDat.setDisable(!has_filtered_machines);
        mntmFilteredAsLogiqxDat.setDisable(!has_filtered_machines);
        mntmFilteredAsSoftwareLists.setDisable(session.getCurrProfile().getMachineListList().getSoftwareListList().getFilteredStream().count() == 0);
        mntmSelectedAsSoftwareLists.setDisable(!has_selected_swlist);
        mntmSelectedFilteredAsSoftwareLists.setDisable(!has_selected_swlist);
    }

    /**
     * Reloads the entity details table with the entities of the selected entry.
     *
     * @param newValue the selected entry, or {@code null} to clear
     */
    private void reloadE(final Anyware newValue) {
        final var list = FXCollections.<EntityBase>observableArrayList();
        if (newValue != null) {
            newValue.resetCache();
            for (final var e : newValue.getEntities())
                list.add(e);
        }
        tableEntity.setItems(list);
    }

    /** The filtered list backing the entries table. */
    private FilteredList<Anyware> filteredData;

    private ProfileViewerWareRelations wareRelations;

    /**
     * Reloads the entries table with data from the selected software/machine list,
     * choosing the appropriate column set depending on the list type.
     */
    private void reloadW(final AnywareList<? extends Anyware> newValue) {
        tableW.getColumns().clear();
        final var list = FXCollections.<Anyware>observableArrayList();
        if (newValue != null) {
            newValue.resetCache();
            if (newValue instanceof final MachineList ml) {
                tableW.getColumns().add(tableWMStatus);
                tableW.getColumns().add(tableWMName);
                tableW.getColumns().add(tableWMDescription);
                tableW.getColumns().add(tableWMHave);
                tableW.getColumns().add(tableWMCloneOf);
                tableW.getColumns().add(tableWMRomOf);
                tableW.getColumns().add(tableWMSampleOf);
                tableW.getColumns().add(tableWMSelected);
                for (final var w : ml.getFilteredList())
                    list.add(w);
            } else if (newValue instanceof final SoftwareList sl) {
                tableW.getColumns().add(tableWSStatus);
                tableW.getColumns().add(tableWSName);
                tableW.getColumns().add(tableWSDescription);
                tableW.getColumns().add(tableWSHave);
                tableW.getColumns().add(tableWSCloneOf);
                tableW.getColumns().add(tableWSSelected);
                for (final var w : sl.getFilteredList())
                    list.add(w);
            }
        }
        filteredData = new FilteredList<>(list, searchPredicate(search.getText()));
        tableW.setItems(filteredData);
        tableW.getSelectionModel().select(0);
    }

    /**
     * Applies the software/machine list status filter.
     *
     * @param e the action event
     */
    @FXML
    public void diskMultipleFilter(final ActionEvent e) {
        setFilterWL(toggleWLUnknown.isSelected(), toggleWLMissing.isSelected(), toggleWLPartial.isSelected(), toggleWLComplete.isSelected());
    }

    /**
     * Applies the entry status filter.
     *
     * @param e the action event
     */
    @FXML
    public void folderFilter(final ActionEvent e) {
        setFilterW(toggleWUnknown.isSelected(), toggleWMissing.isSelected(), toggleWPartial.isSelected(), toggleWComplete.isSelected());
    }

    /**
     * Applies the entity status filter.
     *
     * @param e the action event
     */
    @FXML
    public void bulletFilter(final ActionEvent e) {
        setFilterE(toggleEntityUnknown.isSelected(), toggleEntityKO.isSelected(), toggleEntityOK.isSelected());
    }

    /**
     * Sets the filter for software/machine lists based on toggle states.
     *
     * @param unknown  whether to include unknown status
     * @param missing  whether to include missing status
     * @param partial  whether to include partial status
     * @param complete whether to include complete status
     */
    private void setFilterWL(final boolean unknown, final boolean missing, final boolean partial, final boolean complete) {
        final EnumSet<AnywareStatus> filter = EnumSet.noneOf(AnywareStatus.class);
        if (unknown)
            filter.add(AnywareStatus.UNKNOWN);
        if (missing)
            filter.add(AnywareStatus.MISSING);
        if (partial)
            filter.add(AnywareStatus.PARTIAL);
        if (complete)
            filter.add(AnywareStatus.COMPLETE);
        session.getCurrProfile().setFilterListLists(filter);
        reset(session.getCurrProfile());
    }

    /**
     * Sets the filter for entries based on toggle states.
     *
     * @param unknown  whether to include unknown status
     * @param missing  whether to include missing status
     * @param partial  whether to include partial status
     * @param complete whether to include complete status
     */
    private void setFilterW(final boolean unknown, final boolean missing, final boolean partial, final boolean complete) {
        final EnumSet<AnywareStatus> filter = EnumSet.noneOf(AnywareStatus.class);
        if (unknown)
            filter.add(AnywareStatus.UNKNOWN);
        if (missing)
            filter.add(AnywareStatus.MISSING);
        if (partial)
            filter.add(AnywareStatus.PARTIAL);
        if (complete)
            filter.add(AnywareStatus.COMPLETE);
        session.getCurrProfile().setFilterList(filter);
        final var item = tableWL.getSelectionModel().getSelectedItem();
        if (item != null)
            reloadW(item);
    }

    /**
     * Sets the filter for entity statuses based on toggle states.
     *
     * @param unknown  whether to include entities with unknown status
     * @param missing  whether to include entities with KO status
     * @param complete whether to include entities with OK status
     */
    private void setFilterE(final boolean unknown, final boolean missing, final boolean complete) {
        final EnumSet<EntityStatus> filter = EnumSet.noneOf(EntityStatus.class);
        if (unknown)
            filter.add(EntityStatus.UNKNOWN);
        if (missing)
            filter.add(EntityStatus.KO);
        if (complete)
            filter.add(EntityStatus.OK);
        session.getCurrProfile().setFilterEntities(filter);
        final var item = tableW.getSelectionModel().getSelectedItem();
        if (item != null)
            reloadE(item);
    }

    /**
     * Returns the folder icon corresponding to the given status.
     *
     * @param status the entry status
     * @return the matching icon
     */
    static Image getStatusIcon(final AnywareStatus status) {
        return switch (status) {
            case COMPLETE -> folderClosedGreen;
            case PARTIAL -> folderClosedOrange;
            case MISSING -> folderClosedRed;
            case UNKNOWN -> folderClosedGray;
            default -> folderClosedGray;
        };
    }

    /**
     * Computes or returns cached have-count string for a ware list (e.g. "12/34").
     */
    private String getWLHave(final AnywareList<? extends Anyware> list) {
        return haveCache.computeIfAbsent(list.getName(), _ -> {
            final long[] ht = { 0, 0 };
            list.getFilteredStream().forEach(t -> {
                if (t.getStatus() == AnywareStatus.COMPLETE)
                    ht[0]++;
                ht[1]++;
            });
            return String.format(D_OF_D_FMT, ht[0], ht[1]);
        });
    }

    /**
     * Returns the description for a ware list (software list desc or "All Machines").
     */
    private static String getWLDescription(final AnywareList<? extends Anyware> list) {
        if (list instanceof final SoftwareList sl)
            return sl.getDescription().toString();
        return Messages.getString("MachineListList.AllMachines");
    }

    /**
     * Returns the pixel width for the given number of digits.
     *
     * @param digits the number of digits
     * @return the calculated width
     */
    private double getWidth(final int digits) {
        return getWidth(digits, null);
    }

    /**
     * Returns the pixel width for the given number of digits using the specified font.
     *
     * @param digits the number of digits
     * @param font   the font family name, or {@code null} for default
     * @return the calculated width
     */
    private double getWidth(final int digits, final String font) {
        final var text = new Text(String.format("%%0%dd".formatted(digits), 0));
        @SuppressWarnings("unused")
        final var scn = new JRMScene(new Group(text));
        text.getStyleClass().add("table-view");
        if (font != null)
            text.styleProperty().bind(new SimpleStringProperty("-fx-font-family: %s;".formatted(font)));
        text.applyCss();
        return text.getBoundsInLocal().getWidth();
    }

    /**
     * Clears all table items and the have cache.
     */
    public void clear() {
        tableEntity.setItems(FXCollections.observableArrayList());
        tableW.setItems(FXCollections.observableArrayList());
        tableWL.setItems(FXCollections.observableArrayList());
        haveCache.clear();
    }

    /**
     * Refreshes all tables and clears the have cache.
     */
    public void reload() {
        tableWL.refresh();
        haveCache.clear();
        tableW.refresh();
        tableEntity.refresh();
    }

    /**
     * Resets the profile viewer with data from the given profile, preserving the current selection if possible.
     *
     * @param profile the profile to display
     */
    public void reset(final Profile profile) {
        final var selected = tableWL.getSelectionModel().getSelectedItem();
        clear();
        final var wl = FXCollections.<AnywareList<? extends Anyware>>observableArrayList();
        profile.getMachineListList().resetCache();
        for (final var w : profile.getMachineListList().getFilteredList())
            wl.add(w);
        profile.getMachineListList().getSoftwareListList().resetCache();
        for (final var w : profile.getMachineListList().getSoftwareListList().getFilteredList())
            wl.add(w);
        tableWL.setItems(wl);
        if (selected != null) {
            final int index = tableWL.getItems().indexOf(selected);
            if (index >= 0)
                tableWL.getSelectionModel().select(index);
        } else
            tableWL.getSelectionModel().select(0);
        tableWL.refresh();
    }

    /**
     * Extends {@link jrm.profile.filter.Keywords} to show the keyword filter dialog
     * and refresh the entries table when filters change.
     */
    private class KW extends jrm.profile.filter.Keywords {

        @Override
        protected void showFilter(final String[] keywords, final KFCallBack callback) {
            try {
                new Keywords((ProfileViewer) tableWL.getScene().getWindow(), keywords, tableWL.getSelectionModel().getSelectedItem(), callback);
            } catch (URISyntaxException | IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
        }

        @Override
        protected void updateList() {
            tableW.refresh();
        }

    }

    /**
     * Opens the keyword filter dialog for the selected software/machine list.
     *
     * @param e the action event
     */
    @FXML
    private void selectByKeywords(final ActionEvent e) {
        final var lst = tableWL.getSelectionModel().getSelectedItem();
        new KW().filter(lst);
    }

    /**
     * Deselects all entries.
     *
     * @param e the action event
     */
    @FXML
    public void selectNone(final ActionEvent e) {
        tableW.getItems().forEach(ware -> ware.setSelected(false));
        tableW.refresh();

    }

    /**
     * Selects all entries.
     *
     * @param e the action event
     */
    @FXML
    public void selectAll(final ActionEvent e) {
        tableW.getItems().forEach(ware -> ware.setSelected(true));
        tableW.refresh();
    }

    /**
     * Inverts the selection of all entries.
     *
     * @param e the action event
     */
    @FXML
    public void selectInvert(final ActionEvent e) {
        tableW.getItems().forEach(ware -> ware.setSelected(!ware.isSelected()));
        tableW.refresh();
    }

    /**
     * Copies the CRC of the selected entity to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    public void copyCrc(final javafx.event.ActionEvent e) {
        if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
            final var content = new ClipboardContent();
            content.putString(entity.getCrc());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Copies the SHA-1 of the selected entity to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    public void copySha1(final javafx.event.ActionEvent e) {
        if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
            final var content = new ClipboardContent();
            content.putString(entity.getSha1());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Copies the name of the selected entity to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    public void copyName(final javafx.event.ActionEvent e) {
        if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
            final var content = new ClipboardContent();
            content.putString(entity.getName());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Opens a web search for the selected entity's name and hash.
     *
     * @param e the action event
     */
    @FXML
    public void searchWeb(final javafx.event.ActionEvent e) {
        if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
            try {
                final var name = entity.getName();
                final var crc = entity.getCrc();
                final var sha1 = entity.getSha1();
                final var hash = Optional.ofNullable(Optional.ofNullable(crc).orElse(sha1)).map(h -> '+' + h).orElse("");
                MainFrame.getApplication().getHostServices()
                        .showDocument(new URI("https://www.google.com/search?q=" + URLEncoder.encode('"' + name + '"', "UTF-8") + hash).toString());
            } catch (IOException | URISyntaxException e1) {
                Log.err(e1.getMessage(), e1);
            }
        }
    }

    /**
     * Exports filtered entries as Logiqx DAT.
     *
     * @param e the action event
     */
    @FXML
    public void exportFilteredAsLogiqxDat(final ActionEvent e) {
        export(ExportType.DATAFILE, EnumSet.of(ExportMode.FILTERED), null);
    }

    /**
     * Exports filtered entries as MAME DAT.
     *
     * @param e the action event
     */
    @FXML
    public void exportFilteredAsMameDat(final ActionEvent e) {
        export(ExportType.MAME, EnumSet.of(ExportMode.FILTERED), null);
    }

    /**
     * Exports filtered entries as software lists.
     *
     * @param e the action event
     */
    @FXML
    public void exportFilteredAsSoftwareLists(final ActionEvent e) {
        export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), null);
    }

    /**
     * Exports all entries as Logiqx DAT.
     *
     * @param e the action event
     */
    @FXML
    public void exportAllAsLogiqxDat(final ActionEvent e) {
        export(ExportType.DATAFILE, EnumSet.of(ExportMode.ALL), null);
    }

    /**
     * Exports all entries as MAME DAT.
     *
     * @param e the action event
     */
    @FXML
    public void exportAllAsMameDat(final ActionEvent e) {
        export(ExportType.MAME, EnumSet.of(ExportMode.ALL), null);
    }

    /**
     * Exports all entries as software lists.
     *
     * @param e the action event
     */
    @FXML
    public void exportAllAsSoftwareLists(final ActionEvent e) {
        export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), null);
    }

    /**
     * Exports the selected software list as filtered software lists.
     *
     * @param e the action event
     */
    @FXML
    public void exportSelectedFilteredAsSoftwareLists(final ActionEvent e) {
        if (tableWL.getSelectionModel().getSelectedItem() instanceof final SoftwareList sl)
            export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), sl);
    }

    /**
     * Exports the selected software list entirely.
     *
     * @param e the action event
     */
    @FXML
    public void exportSelectedAsSoftwareLists(final ActionEvent e) {
        if (tableWL.getSelectionModel().getSelectedItem() instanceof final SoftwareList sl)
            export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), sl);
    }

    /**
     * Delegates export to the main frame export method.
     *
     * @param type      the export format type
     * @param modes     the export modes
     * @param selection the selected software list, or {@code null}
     */
    private void export(final ExportType type, final Set<ExportMode> modes, final SoftwareList selection) {
        MainFrame.export(tableWL.getScene().getWindow(), session, type, modes, selection);
    }

}
