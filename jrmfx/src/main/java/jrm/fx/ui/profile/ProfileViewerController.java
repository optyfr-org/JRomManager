package jrm.fx.ui.profile;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableValueBase;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContentDisplay;
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
import javafx.scene.text.TextAlignment;
import jrm.fx.ui.JRMScene;
import jrm.fx.ui.MainFrame;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.profile.filter.Keywords;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
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
import jrm.profile.manager.ProfileNFOMame;
import jrm.profile.manager.ProfileNFOMame.MameStatus;
import jrm.security.Session;
import jrm.security.Sessions;
import lombok.val;

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
        tableEntityStatus.setCellFactory(_ -> createEntityStatusCell());
        tableEntityStatus.setCellValueFactory(this::createEntityStatusValue);
    }

    /**
     * Creates an observable value that returns the entity for the status column.
     *
     * @param p the cell data features
     * @return an observable value returning the entity
     */
    private ObservableValueBase<EntityBase> createEntityStatusValue(final CellDataFeatures<EntityBase, EntityBase> p) {
        return new ObservableValueBase<EntityBase>() {
            @Override
            public EntityBase getValue() {
                return p.getValue();
            }
        };
    }

    /**
     * Creates a table cell that renders an entity's status as a colored bullet icon.
     *
     * @return the status table cell
     */
    private TableCell<EntityBase, EntityBase> createEntityStatusCell() {
        return new TableCell<EntityBase, EntityBase>() {
            @Override
            protected void updateItem(final EntityBase item, final boolean empty) {
                if (item == null || empty) {
                    setText("");
                    setGraphic(null);
                } else {
                    final var i = new ImageView(switch (item.getStatus()) {
                        case KO -> bulletRed;
                        case OK -> bulletGreen;
                        case UNKNOWN -> bulletBlack;
                        default -> bulletBlack;
                    });
                    i.setPreserveRatio(true);
                    i.getStyleClass().add("icon");
                    setGraphic(i);
                }
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
            }
        };
    }

    /**
     * Configures the entity name column with cell factory and value factory.
     */
    private void initTableEName() {
        tableEntityName.setCellFactory(_ -> entityNameCellFactory());
        tableEntityName.setCellValueFactory(tableEntityStatus.getCellValueFactory());
    }

    /**
     * Creates a table cell that renders the entity name with a type icon.
     *
     * @return the entity name table cell
     */
    private TableCell<EntityBase, EntityBase> entityNameCellFactory() {
        return new TableCell<EntityBase, EntityBase>() {
            final Image romSmall = MainFrame.getIcon("/jrm/resicons/rom_small.png"); //$NON-NLS-1$
            final Image drive = MainFrame.getIcon("/jrm/resicons/icons/drive.png"); //$NON-NLS-1$
            final Image sound = MainFrame.getIcon("/jrm/resicons/icons/sound.png"); //$NON-NLS-1$

            @Override
            protected void updateItem(final EntityBase item, final boolean empty) {
                if (item == null || empty) {
                    setText("");
                    setGraphic(null);
                } else {
                    setText(item.getBaseName());
                    final var i = switch (item) {
                        case final Rom _ -> new ImageView(romSmall);
                        case final Disk _ -> new ImageView(drive);
                        case final Sample _ -> new ImageView(sound);
                        default -> null;
                    };
                    if (i != null) {
                        i.setPreserveRatio(true);
                        i.getStyleClass().add("icon");
                        setGraphic(i);
                    }
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        };
    }

    /**
     * Configures the entity size column with width, cell factory, and value factory.
     */
    private void initTableESize() {
        tableEntitySize.setMinWidth(getWidth(12));
        tableEntitySize.setPrefWidth(tableEntitySize.getMinWidth());
        tableEntitySize.setMaxWidth(tableEntitySize.getMinWidth() * 2);
        tableEntitySize.setCellFactory(_ -> createEntitySizeCell());
        tableEntitySize.setCellValueFactory(this::createEntitySizeValue);
    }

    /**
     * Creates an observable value that returns the ROM size for the entity size column.
     *
     * @param p the cell data features
     * @return an observable value returning the ROM size, or {@code null} if not a ROM
     */
    private ObservableValueBase<Long> createEntitySizeValue(final CellDataFeatures<EntityBase, Long> p) {
        return new ObservableValueBase<Long>() {
            @Override
            public Long getValue() {
                if (p.getValue() instanceof final Rom r)
                    return r.getSize();
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders an entity's size.
     *
     * @return the size table cell
     */
    private TableCell<EntityBase, Long> createEntitySizeCell() {
        return new TableCell<EntityBase, Long>() {
            @Override
            protected void updateItem(final Long item, final boolean empty) {
                if (item == null || empty)
                    setText("");
                else
                    setText(item.toString());
                setTextAlignment(TextAlignment.RIGHT);
                setAlignment(Pos.CENTER_RIGHT);
                setGraphic(null);
            }
        };
    }

    /**
     * Configures the entity CRC column with monospaced font width and cell factory.
     */
    private void initTableECRC() {
        tableEntityCRC.setMinWidth(getWidth(10, MONOSPACED));
        tableEntityCRC.setPrefWidth(tableEntityCRC.getMinWidth());
        tableEntityCRC.setMaxWidth(tableEntityCRC.getMinWidth() * 2);
        tableEntityCRC.setCellFactory(_ -> createEntityCRCCell());
        tableEntityCRC.setCellValueFactory(this::createEntityCRCValue);
    }

    /**
     * Creates an observable value that returns the CRC for the entity CRC column.
     *
     * @param p the cell data features
     * @return an observable value returning the CRC, or {@code null} if not a ROM or disk
     */
    private ObservableValueBase<String> createEntityCRCValue(final CellDataFeatures<EntityBase, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                if (p.getValue() instanceof final Rom r)
                    return r.getCrc();
                else if (p.getValue() instanceof final Disk d)
                    return d.getCrc();
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders an entity's CRC with monospaced font.
     *
     * @return the CRC table cell
     */
    private TableCell<EntityBase, String> createEntityCRCCell() {
        return new TableCell<EntityBase, String>() {
            @Override
            protected void updateItem(final String item, final boolean empty) {
                if (item == null || empty)
                    setText("");
                else
                    setText(item);
                styleProperty().bind(new SimpleStringProperty(FX_FONT_FAMILY_MONOSPACED));
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(null);
            }
        };
    }

    /**
     * Configures the entity MD5 column with monospaced font width and cell factory.
     */
    private void initTableEMD5() {
        tableEntityMD5.setMinWidth(getWidth(34, MONOSPACED));
        tableEntityMD5.setPrefWidth(tableEntityMD5.getMinWidth());
        tableEntityMD5.setMaxWidth(tableEntityMD5.getMinWidth() * 2);
        tableEntityMD5.setCellFactory(_ -> createEntityMD5Cell());
        tableEntityMD5.setCellValueFactory(this::createEntityMD5Value);
    }

    /**
     * Creates an observable value that returns the MD5 for the entity MD5 column.
     *
     * @param p the cell data features
     * @return an observable value returning the MD5, or {@code null} if not a ROM or disk
     */
    private ObservableValueBase<String> createEntityMD5Value(final CellDataFeatures<EntityBase, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                if (p.getValue() instanceof final Rom r)
                    return r.getMd5();
                else if (p.getValue() instanceof final Disk d)
                    return d.getMd5();
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders an entity's MD5 with monospaced font.
     *
     * @return the MD5 table cell
     */
    private TableCell<EntityBase, String> createEntityMD5Cell() {
        return new TableCell<EntityBase, String>() {
            @Override
            protected void updateItem(final String item, final boolean empty) {
                if (item == null || empty)
                    setText("");
                else
                    setText(item);
                styleProperty().bind(new SimpleStringProperty(FX_FONT_FAMILY_MONOSPACED));
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(null);
            }
        };
    }

    /**
     * Configures the entity SHA-1 column with monospaced font width and cell factory.
     */
    private void initTableESHA1() {
        tableEntitySHA1.setMinWidth(getWidth(42, MONOSPACED));
        tableEntitySHA1.setPrefWidth(tableEntitySHA1.getMinWidth());
        tableEntitySHA1.setMaxWidth(tableEntitySHA1.getMinWidth() * 2);
        tableEntitySHA1.setCellFactory(_ -> createEntitySHA1Cell());
        tableEntitySHA1.setCellValueFactory(this::createEntitySHA1Value);
    }

    /**
     * Creates an observable value that returns the SHA-1 for the entity SHA-1 column.
     *
     * @param p the cell data features
     * @return an observable value returning the SHA-1, or {@code null} if not a ROM or disk
     */
    private ObservableValueBase<String> createEntitySHA1Value(final CellDataFeatures<EntityBase, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                if (p.getValue() instanceof final Rom r)
                    return r.getSha1();
                else if (p.getValue() instanceof final Disk d)
                    return d.getSha1();
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders an entity's SHA-1 with monospaced font.
     *
     * @return the SHA-1 table cell
     */
    private TableCell<EntityBase, String> createEntitySHA1Cell() {
        return new TableCell<EntityBase, String>() {
            @Override
            protected void updateItem(final String item, final boolean empty) {
                if (item == null || empty)
                    setText("");
                else
                    setText(item);
                styleProperty().bind(new SimpleStringProperty(FX_FONT_FAMILY_MONOSPACED));
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(null);
            }
        };
    }

    /**
     * Configures the entity merge name column with value factory.
     */
    private void initTableEMergeName() {
        tableEntityMergeName.setCellValueFactory(this::getEntityMergeName);
    }

    /**
     * Creates an observable value that returns the merge name for the entity merge name column.
     *
     * @param p the cell data features
     * @return an observable value returning the merge name, or {@code null} if not a ROM or disk
     */
    private ObservableValueBase<String> getEntityMergeName(final CellDataFeatures<EntityBase, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                if (p.getValue() instanceof final Rom r)
                    return r.getMerge();
                else if (p.getValue() instanceof final Disk d)
                    return d.getMerge();
                return null;
            }
        };
    }

    /**
     * Configures the entity dump status column with cell factory and value factory.
     */
    private void initTableEDumpStatus() {
        tableEntityDumpStatus.setCellFactory(_ -> createEntityDumpStatusCell());
        tableEntityDumpStatus.setCellValueFactory(this::getEntityDumpStatusValue);
    }

    /**
     * Creates an observable value that returns the dump status for the entity dump status column.
     *
     * @param p the cell data features
     * @return an observable value returning the dump status, or {@code null} if not a ROM or disk
     */
    private ObservableValueBase<Status> getEntityDumpStatusValue(final CellDataFeatures<EntityBase, Status> p) {
        return new ObservableValueBase<Entity.Status>() {
            @Override
            public Entity.Status getValue() {
                if (p.getValue() instanceof final Rom r)
                    return r.getDumpStatus();
                else if (p.getValue() instanceof final Disk d)
                    return d.getDumpStatus();
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders an entity's dump status as an icon.
     *
     * @return the dump status table cell
     */
    private TableCell<EntityBase, Status> createEntityDumpStatusCell() {
        return new TableCell<EntityBase, Entity.Status>() {
            private static final Image verified = MainFrame.getIcon("/jrm/resicons/icons/star.png"); //$NON-NLS-1$
            private static final Image good = MainFrame.getIcon("/jrm/resicons/icons/tick.png"); //$NON-NLS-1$
            private static final Image baddump = MainFrame.getIcon("/jrm/resicons/icons/delete.png"); //$NON-NLS-1$
            private static final Image nodump = MainFrame.getIcon("/jrm/resicons/icons/error.png"); //$NON-NLS-1$

            @Override
            protected void updateItem(final Entity.Status item, final boolean empty) {
                if (item == null || empty) {
                    setText("");
                    setGraphic(null);
                } else {
                    final ImageView i = new ImageView(switch (item) {
                        case baddump -> baddump;
                        case good -> good;
                        case nodump -> nodump;
                        case verified -> verified;
                        default -> null;
                    });
                    i.setPreserveRatio(true);
                    i.getStyleClass().add("icon");
                    setGraphic(i);
                }
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
            }
        };
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
     * Builds MAME command-line arguments for a machine entry.
     *
     * @param ware    the machine to build arguments for
     * @param profile the current profile
     * @param mame    the MAME configuration
     * @param args    the argument list to populate
     */
    private void getMameArgsMachine(final Anyware ware, final Profile profile, final ProfileNFOMame mame, final ArrayList<String> args) {
        final List<String> rompaths = new ArrayList<>(Collections.singletonList(profile.getProperty(ProfileSettingsEnum.roms_dest_dir))); // $NON-NLS-1$
                                                                                                                                          // //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.disks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        args.add(mame.getFile().getAbsolutePath());
        args.add(ware.getBaseName());
        args.add("-homepath");
        args.add(mame.getFile().getParent());
        args.add("-rompath");
        args.add(rompaths.stream().collect(Collectors.joining(";")));
    }

    /**
     * Builds MAME command-line arguments for a software entry, prompting the user
     * to select a compatible machine if needed.
     *
     * @param ware    the software to build arguments for
     * @param profile the current profile
     * @param mame    the MAME configuration
     * @param args    the argument list to populate
     * @throws HeadlessException if running in a headless environment
     */
    private void getMameArgsSofware(final Anyware ware, final Profile profile, final ProfileNFOMame mame, final ArrayList<String> args) throws HeadlessException {
        final List<String> rompaths = new ArrayList<>(Collections.singletonList(profile.getProperty(ProfileSettingsEnum.roms_dest_dir))); // $NON-NLS-1$
                                                                                                                                          // //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.disks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        Log.debug(() -> ((Software) ware).getSl().getBaseName() + ", " + ((Software) ware).getCompatibility()); //$NON-NLS-1$
        final var machines = new ChoiceDialog<Machine>(null,
                profile.getMachineListList().getSortedMachines(((Software) ware).getSl().getBaseName(), ((Software) ware).getCompatibility()));
        final var machine = machines.showAndWait();
        machine.ifPresent(m -> {
            final var device = new StringBuilder(); // $NON-NLS-1$
            for (final var dev : m.getDevices()) {
                if (Objects.equals(((Software) ware).getParts().get(0).getIntrface(), dev.getIntrface()) && dev.getInstance() != null) {
                    device.append("-").append(dev.getInstance().getName()); //$NON-NLS-1$
                    break;
                }
            }
            Log.debug(() -> "-> " + m.getBaseName() + " " + device + " " + ware.getBaseName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            args.add(mame.getFile().getAbsolutePath());
            args.add(m.getBaseName());
            args.add(device.toString());
            args.add(ware.getBaseName());
            args.add("-homepath");
            args.add(mame.getFile().getParent());
            args.add("-rompath");
            args.add(rompaths.stream().collect(Collectors.joining(";")));
        });
    }

    /**
     * Launches MAME for the given entry, building arguments and starting the process.
     *
     * @param ware    the machine or software to launch
     * @param profile the current profile
     * @throws HeadlessException if running in a headless environment
     */
    private void launchMame(final Anyware ware, final Profile profile) throws HeadlessException {
        final ProfileNFOMame mame = profile.getNfo().getMame();
        
        // Validate MAME executable before launching to prevent arbitrary program execution
        if (mame.getFile() == null) {
            Dialogs.showError("MAME executable is not configured for this profile.");
            return;
        }
        
        if (!mame.getFile().exists() || !mame.getFile().canExecute()) {
            Dialogs.showError("MAME executable does not exist or is not executable: " + mame.getFile().getAbsolutePath());
            return;
        }
        
        // Additional security check: verify the executable name contains "mame"
        final String fileName = mame.getFile().getName().toLowerCase();
        if (!fileName.contains("mame")) {
            Dialogs.showError("The configured executable does not appear to be a MAME binary: " + mame.getFile().getName());
            return;
        }
        
        final var args = new ArrayList<String>();
        if (ware instanceof Software) {
            getMameArgsSofware(ware, profile, mame, args);
        } else {
            getMameArgsMachine(ware, profile, mame, args);
        }
        if (!args.isEmpty()) {
            final ProcessBuilder pb = new ProcessBuilder(args).directory(mame.getFile().getParentFile()).redirectErrorStream(true)
                    .redirectOutput(new File(mame.getFile().getParentFile(), "JRomManager.log")); //$NON-NLS-1$
            try {
                pb.start().waitFor();
            } catch (final IOException e1) {
                Dialogs.showError(e1);
            } catch (final InterruptedException e1) {
                Dialogs.showError(e1);
                Thread.currentThread().interrupt();
            }
        }
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
        tableWMStatus.setCellFactory(_ -> createWMStatusCell());
        tableWMStatus.setCellValueFactory(this::getWMStatusValue);
    }

    /**
     * Creates an observable value that returns the {@link Anyware} for the status column.
     *
     * @param p the cell data features
     * @return an observable value returning the entry
     */
    private ObservableValueBase<Anyware> getWMStatusValue(final CellDataFeatures<Anyware, Anyware> p) {
        return new ObservableValueBase<Anyware>() {
            @Override
            public Anyware getValue() {
                return p.getValue();
            }
        };
    }

    /**
     * Creates a table cell that renders an entry's status as a colored icon.
     *
     * @return the status table cell
     */
    private TableCell<Anyware, Anyware> createWMStatusCell() {
        return new TableCell<Anyware, Anyware>() {
            @Override
            protected void updateItem(final Anyware item, final boolean empty) {
                if (item == null || empty)
                    setGraphic(null);
                else {
                    final ImageView i = new ImageView(getStatusIcon(item.getStatus()));
                    setGraphic(i);
                    i.setPreserveRatio(true);
                    i.getStyleClass().add("icon");
                }
                setAlignment(Pos.CENTER);
                setText("");
            }
        };
    }

    /**
     * Configures the machine name column with width, cell factory, and value factory.
     */
    private void initTableWMName() {
        tableWMName.setMinWidth(50);
        tableWMName.setPrefWidth(100);
        tableWMName.setMaxWidth(200);
        tableWMName.setCellFactory(_ -> createWMNameCell());
        tableWMName.setCellValueFactory(this::getWMNameValue);
        tableWMName.setSortable(true);
    }

    /**
     * Creates an observable value that returns the {@link Machine} for the name column.
     *
     * @param p the cell data features
     * @return an observable value returning the machine, or {@code null} if not a machine
     */
    private ObservableValueBase<Machine> getWMNameValue(final CellDataFeatures<Anyware, Machine> p) {
        return new ObservableValueBase<Machine>() {
            @Override
            public Machine getValue() {
                if (p.getValue() instanceof final Machine m)
                    return m;
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders a machine name with a type icon and double-click handling.
     *
     * @return the machine name table cell
     */
    private TableCell<Anyware, Machine> createWMNameCell() {
        final var cell = new TableCell<Anyware, Machine>() {
            private static final Image applicationOSXTerminal = MainFrame.getIcon("/jrm/resicons/icons/application_osx_terminal.png"); //$NON-NLS-1$
            private static final Image computer = MainFrame.getIcon("/jrm/resicons/icons/computer.png"); //$NON-NLS-1$
            private static final Image wrench = MainFrame.getIcon("/jrm/resicons/icons/wrench.png"); //$NON-NLS-1$
            private static final Image joystick = MainFrame.getIcon("/jrm/resicons/icons/joystick.png"); //$NON-NLS-1$

            @Override
            protected void updateItem(final Machine item, final boolean empty) {
                if (empty) {
                    setText("");
                    setGraphic(null);
                } else {
                    setText(item.getBaseName());
                    setUserData(item);
                    setTooltip(new Tooltip(item.getName()));
                    final ImageView i;
                    if (item.isIsbios())
                        i = new ImageView(applicationOSXTerminal);
                    else if (item.isIsdevice())
                        i = new ImageView(computer);
                    else if (item.isIsmechanical())
                        i = new ImageView(wrench);
                    else
                        i = new ImageView(joystick);
                    i.setPreserveRatio(true);
                    i.getStyleClass().add("icon");
                    setGraphic(i);
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        };
        cell.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleMachineDoubleClick);
        return cell;
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
                    if (profile.getNfo().getMame().getStatus() == MameStatus.UPTODATE)
                        launchMame(ware, profile);
                    else
                        Dialogs.showAlert(
                                String.format(Messages.getString("ProfileViewer.MameNotAvailableOrObsolete"),
                                        profile.getNfo().getMame().getStatus()));
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
        tableWMDescription.setCellFactory(_ -> createWMDescriptionCell());
        tableWMDescription.setCellValueFactory(this::getWMDescriptionValue);
        tableWMDescription.setSortable(true);
    }

    /**
     * Creates an observable value that returns the description for the machine description column.
     *
     * @param p the cell data features
     * @return an observable value returning the description text
     */
    private ObservableValueBase<String> getWMDescriptionValue(final CellDataFeatures<Anyware, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                return p.getValue().getDescription().toString();
            }
        };
    }

    /**
     * Creates a table cell that renders a description with tooltip.
     *
     * @return the description table cell
     */
    private TableCell<Anyware, String> createWMDescriptionCell() {
        return new TableCell<Anyware, String>() {
            @Override
            protected void updateItem(final String item, final boolean empty) {
                if (empty)
                    setText("");
                else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(null);
            }
        };
    }

    /**
     * Configures the have column with cell factory and value factory.
     */
    private void initTableWMHave() {
        tableWMHave.setResizable(true);
        tableWMHave.setSortable(false);
        tableWMHave.setPrefWidth(45);
        tableWMHave.setMaxWidth(90);
        tableWMHave.setCellFactory(_ -> createWMHaveCell());
        tableWMHave.setCellValueFactory(this::getWMHaveValue);
    }

    /**
     * Creates an observable value that returns the have-count string for the machine have column.
     *
     * @param p the cell data features
     * @return an observable value returning the have-count formatted string
     */
    private ObservableValueBase<String> getWMHaveValue(final CellDataFeatures<Anyware, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                if (p.getValue() instanceof final Machine machine)
                    return String.format(D_OF_D_FMT, machine.countHave(), machine.countAll());
                return null;
            }
        };
    }

    /**
     * Creates a table cell that renders the have-count centered.
     *
     * @return the have table cell
     */
    private TableCell<Anyware, String> createWMHaveCell() {
        return new TableCell<Anyware, String>() {
            @Override
            protected void updateItem(final String item, final boolean empty) {
                if (empty)
                    setText("");
                else
                    setText(item);
                setTextAlignment(TextAlignment.CENTER);
                setAlignment(Pos.CENTER);
                setGraphic(null);
            }
        };
    }

    /**
     * Configures the clone-of column with cell factory and value factory.
     */
    private void initTableWMCloneOf() {
        tableWMCloneOf.setSortable(false);
        tableWMCloneOf.setMinWidth(50);
        tableWMCloneOf.setPrefWidth(100);
        tableWMCloneOf.setMaxWidth(200);
        tableWMCloneOf.setCellFactory(_ -> createWMCloneOfCell());
        tableWMCloneOf.setCellValueFactory(this::getWMCloneOfValue);
    }

    /**
     * Creates an observable value that returns the clone-of relationship for the column.
     *
     * @param p the cell data features
     * @return an observable value returning the clone-of entry or name
     */
    private ObservableValueBase<Object> getWMCloneOfValue(final CellDataFeatures<Anyware, Object> p) {
        return new ObservableValueBase<Object>() {
            @Override
            public Object getValue() {
                return getCloneOfValue(p);
            }
        };
    }

    /**
     * Creates a table cell for the clone-of column with double-click navigation.
     *
     * @return the clone-of table cell
     */
    private TableCell<Anyware, Object> createWMCloneOfCell() {
        final var cell = new TableCell<Anyware, Object>() {
            @Override
            protected void updateItem(final Object item, final boolean empty) {
                updateCloneOfCell(this, item, empty);
            }
        };
        cell.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleCloneOfDoubleClick);
        return cell;
    }

    /**
     * Updates the clone-of cell content with an icon and name.
     *
     * @param cell  the cell to update
     * @param item  the cell value
     * @param empty whether the cell is empty
     */
    private void updateCloneOfCell(final TableCell<Anyware, Object> cell, final Object item, final boolean empty) {
        if (item == null || empty) {
            cell.setText("");
            cell.setGraphic(null);
        } else if (item instanceof final Anyware aw) {
            final ImageView i = new ImageView(getStatusIcon(aw.getStatus()));
            i.setPreserveRatio(true);
            i.getStyleClass().add("icon");
            cell.setGraphic(i);
            cell.setUserData(aw);
            cell.setText(aw.getBaseName());
        } else {
            final ImageView i = new ImageView(folderClosedGray);
            i.setPreserveRatio(true);
            i.getStyleClass().add("icon");
            cell.setGraphic(i);
            cell.setText(item.toString());
        }
        cell.setAlignment(Pos.CENTER_LEFT);
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
     * Resolves the clone-of value for a cell data row.
     *
     * @param p the cell data features
     * @return the resolved clone-of entry, name, or {@code null}
     */
    private Object getCloneOfValue(final CellDataFeatures<Anyware, Object> p) {
        final AnywareList<? extends Anyware> machineList = tableWL.getSelectionModel().getSelectedItem();
        return Optional.ofNullable(p.getValue().getCloneof()).map(cloneof -> machineList.containsName(cloneof) ? machineList.getByName(cloneof) : cloneof).orElse(null);
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
        tableWMRomOf.setCellValueFactory(this::getWMRomOfValue);
    }

    /**
     * Creates an observable value that returns the ROM-of relationship for the column.
     *
     * @param p the cell data features
     * @return an observable value returning the ROM-of entry or name
     */
    private ObservableValueBase<Object> getWMRomOfValue(final CellDataFeatures<Anyware, Object> p) {
        return new ObservableValueBase<Object>() {
            @Override
            public Object getValue() {
                if (p.getValue() instanceof final Machine m) {
                    final AnywareList<? extends Anyware> machineList = tableWL.getSelectionModel().getSelectedItem();
                    return Optional.ofNullable(m.getRomof()).filter(romof -> !romof.equals(m.getCloneof()))
                            .map(romof -> machineList.containsName(romof) ? machineList.getByName(romof) : romof).orElse(null);
                }
                return null;
            }
        };
    }

    /**
     * Configures the sample-of column with cell factory and value factory.
     */
    private void initTableWMSampleOf() {
        tableWMSampleOf.setSortable(false);
        tableWMSampleOf.setMinWidth(50);
        tableWMSampleOf.setPrefWidth(100);
        tableWMSampleOf.setMaxWidth(200);
        tableWMSampleOf.setCellFactory(_ -> createWMSampleOfCell());
        tableWMSampleOf.setCellValueFactory(this::getWMSampleOfValue);
    }

    /**
     * Creates an observable value that returns the sample-of relationship for the column.
     *
     * @param p the cell data features
     * @return an observable value returning the sample-of entry or name
     */
    private ObservableValueBase<Object> getWMSampleOfValue(final CellDataFeatures<Anyware, Object> p) {
        return new ObservableValueBase<Object>() {
            @Override
            public Object getValue() {
                return getSampleOfValue(p.getValue());
            }
        };
    }

    /**
     * Creates a table cell for the sample-of column.
     *
     * @return the sample-of table cell
     */
    private TableCell<Anyware, Object> createWMSampleOfCell() {
        return new TableCell<Anyware, Object>() {
            @Override
            protected void updateItem(final Object item, final boolean empty) {
                updateSampleOfCell(this, item, empty);
            }
        };
    }

    /**
     * Updates the sample-of cell content with an icon and name.
     *
     * @param cell  the cell to update
     * @param item  the cell value
     * @param empty whether the cell is empty
     */
    private void updateSampleOfCell(final TableCell<Anyware, Object> cell, final Object item, final boolean empty) {
        if (item == null || empty) {
            cell.setText("");
            cell.setGraphic(null);
        } else if (item instanceof final Samples s) {
            final ImageView i = new ImageView(getStatusIcon(s.getStatus()));
            i.setPreserveRatio(true);
            i.getStyleClass().add("icon");
            cell.setGraphic(i);
            cell.setText(s.getBaseName());
        } else {
            final ImageView i = new ImageView(folderClosedGray);
            i.setPreserveRatio(true);
            i.getStyleClass().add("icon");
            cell.setGraphic(i);
            cell.setText(item.toString());
        }
        cell.setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * Resolves the sample-of value for a given entry.
     *
     * @param value the entry to check
     * @return the resolved sample-of entry, name, or {@code null}
     */
    private Object getSampleOfValue(final Object value) {
        if (!(value instanceof final Machine m)) {
            return null;
        }
        final AnywareList<? extends Anyware> awList = tableWL.getSelectionModel().getSelectedItem();
        if (!(awList instanceof final MachineList machineList)) {
            return null;
        }
        return Optional.ofNullable(m.getSampleof())
                .map(sampleof -> machineList.samplesets.containsName(sampleof) ? machineList.samplesets.getByName(sampleof) : sampleof)
                .orElse(null);
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
        tableWSName.setCellValueFactory(this::getWSNameValue);
        tableWSDescription.setSortable(true);
        tableWSDescription.setMinWidth(200);
        tableWSDescription.setPrefWidth(400);
        tableWSDescription.setCellFactory(tableWMDescription.getCellFactory());
        tableWSDescription.setCellValueFactory(tableWMDescription.getCellValueFactory());
        tableWSHave.setResizable(false);
        tableWSHave.setSortable(false);
        tableWSHave.setPrefWidth(45);
        tableWSHave.setCellFactory(tableWMHave.getCellFactory());
        tableWSHave.setCellValueFactory(this::getWSHaveValue);
        tableWSCloneOf.setSortable(false);
        tableWSCloneOf.setMinWidth(50);
        tableWSCloneOf.setPrefWidth(100);
        tableWSCloneOf.setCellFactory(tableWMCloneOf.getCellFactory());
        tableWSCloneOf.setCellValueFactory(this::getWSCloneOfValue);
        tableWSSelected.setResizable(false);
        tableWSSelected.setSortable(false);
        tableWSSelected.setPrefWidth(30);
        tableWSSelected.setCellValueFactory(tableWMSelected.getCellValueFactory());
    }

    /**
     * Creates an observable value that returns the clone-of value for a software entry.
     *
     * @param p the cell data features
     * @return an observable value returning the clone-of entry or {@code null}
     */
    private ObservableValueBase<Object> getWSCloneOfValue(final CellDataFeatures<Anyware, Object> p) {
        return new ObservableValueBase<Object>() {
            @Override
            public Object getValue() {
                final AnywareList<? extends Anyware> softwareList = tableWL.getSelectionModel().getSelectedItem();
                return p.getValue().getCloneof() != null ? softwareList.getByName(p.getValue().getCloneof()) : null;
            }
        };
    }

    /**
     * Creates an observable value that returns the have-count string for a software entry.
     *
     * @param p the cell data features
     * @return an observable value returning the have-count formatted string
     */
    private ObservableValueBase<String> getWSHaveValue(final CellDataFeatures<Anyware, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                if (p.getValue() instanceof final Software software)
                    return String.format(D_OF_D_FMT, software.countHave(), software.countAll());
                return null;
            }
        };
    }

    /**
     * Creates an observable value that returns the base name for a software entry.
     *
     * @param p the cell data features
     * @return an observable value returning the base name
     */
    private ObservableValueBase<String> getWSNameValue(final CellDataFeatures<Anyware, String> p) {
        return new ObservableValueBase<String>() {
            @Override
            public String getValue() {
                return p.getValue().getBaseName();
            }
        };
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
        tableWLName.setCellFactory(_ -> new TableCellWLName());
        tableWLName.setCellValueFactory(ValueWLName::new);
        tableWLName.setSortable(true);
        tableWLDesc.setCellFactory(_ -> new TableCellWLDesc());
        tableWLDesc.setCellValueFactory(ValueWLDesc::new);
        tableWLHave.setCellFactory(_ -> new TableCellWLHave());
        tableWLHave.setCellValueFactory(ValueWLHave::new);
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
    private static Image getStatusIcon(final AnywareStatus status) {
        return switch (status) {
            case COMPLETE -> folderClosedGreen;
            case PARTIAL -> folderClosedOrange;
            case MISSING -> folderClosedRed;
            case UNKNOWN -> folderClosedGray;
            default -> folderClosedGray;
        };
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
                val name = entity.getName();
                val crc = entity.getCrc();
                val sha1 = entity.getSha1();
                val hash = Optional.ofNullable(Optional.ofNullable(crc).orElse(sha1)).map(h -> '+' + h).orElse("");
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
     * Observable value that computes the have-count string for a software/machine list,
     * caching the result for performance.
     */
    private final class ValueWLHave extends ObservableValueBase<String> {
        private final CellDataFeatures<AnywareList<? extends Anyware>, String> p;

        private ValueWLHave(final CellDataFeatures<AnywareList<? extends Anyware>, String> p) {
            this.p = p;
        }

        @Override
        public String getValue() {
            return haveCache.computeIfAbsent(p.getValue().getName(), _ -> {
                final long[] ht = { 0, 0 };
                p.getValue().getFilteredStream().forEach(t -> {
                    if (t.getStatus() == AnywareStatus.COMPLETE)
                        ht[0]++;
                    ht[1]++;
                });
                return String.format(D_OF_D_FMT, ht[0], ht[1]);
            });
        }
    }

    /**
     * Table cell that renders the have-count string centered.
     */
    private static final class TableCellWLHave extends TableCell<AnywareList<? extends Anyware>, String> {
        @Override
        protected void updateItem(final String item, final boolean empty) {
            if (empty)
                setText("");
            else
                setText(item);
            setTextAlignment(TextAlignment.CENTER);
            setAlignment(Pos.CENTER);
            setGraphic(null);
        }
    }

    /**
     * Observable value that returns the description for a software/machine list.
     */
    private static final class ValueWLDesc extends ObservableValueBase<String> {
        private final CellDataFeatures<AnywareList<? extends Anyware>, String> p;

        private ValueWLDesc(final CellDataFeatures<AnywareList<? extends Anyware>, String> p) {
            this.p = p;
        }

        @Override
        public String getValue() {
            if (p.getValue() instanceof final SoftwareList sl)
                return sl.getDescription().toString();
            return Messages.getString("MachineListList.AllMachines");
        }
    }

    /**
     * Table cell that renders the description with a tooltip.
     */
    private static final class TableCellWLDesc extends TableCell<AnywareList<? extends Anyware>, String> {
        @Override
        protected void updateItem(final String item, final boolean empty) {
            if (empty)
                setText("");
            else
                setText(item);
            setTooltip(new Tooltip(getText()));
            setAlignment(Pos.CENTER_LEFT);
            setGraphic(null);
        }
    }

    /**
     * Observable value that returns the name for the software/machine list column.
     */
    private static final class ValueWLName extends ObservableValueBase<AnywareList<? extends Anyware>> {
        private final CellDataFeatures<AnywareList<? extends Anyware>, AnywareList<? extends Anyware>> p;

        private ValueWLName(final CellDataFeatures<AnywareList<? extends Anyware>, AnywareList<? extends Anyware>> p) {
            this.p = p;
        }

        @Override
        public AnywareList<? extends Anyware> getValue() {
            return p.getValue();
        }
    }

    /**
     * Table cell that renders the software/machine list name with a status icon.
     */
    private static final class TableCellWLName extends TableCell<AnywareList<? extends Anyware>, AnywareList<? extends Anyware>> {
        @Override
        protected void updateItem(final AnywareList<? extends Anyware> item, final boolean empty) {
            if (empty) {
                setText("");
                setGraphic(null);
            } else {
                final var i = new ImageView(switch (item.getStatus()) {
                    case COMPLETE -> diskMultipleGreen;
                    case PARTIAL -> diskMultipleOrange;
                    case MISSING -> diskMultipleRed;
                    case UNKNOWN -> diskMultipleGray;
                    default -> diskMultipleGray;

                });
                i.setPreserveRatio(true);
                i.getStyleClass().add("icon");
                setGraphic(i);
                if (item instanceof final SoftwareList sl)
                    setText(sl.getName());
                else if (item instanceof MachineList)
                    setText(Messages.getString("MachineListListRenderer.*"));
            }
            setTooltip(new Tooltip(getText()));
            setAlignment(Pos.CENTER_LEFT);
        }
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
