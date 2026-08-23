package jrm.fx.ui.profile.report;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import jrm.fx.ui.MainFrame;
import jrm.fx.ui.controls.Dialogs;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.data.Entity;
import jrm.profile.data.ExportMode;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.report.ContainerTZip;
import jrm.profile.report.ContainerUnknown;
import jrm.profile.report.ContainerUnneeded;
import jrm.profile.report.EntryAdd;
import jrm.profile.report.EntryMissing;
import jrm.profile.report.EntryMissingDuplicate;
import jrm.profile.report.EntryOK;
import jrm.profile.report.EntryUnneeded;
import jrm.profile.report.EntryWrongHash;
import jrm.profile.report.EntryWrongName;
import jrm.profile.report.FilterOptions;
import jrm.profile.report.Note;
import jrm.profile.report.Report;
import jrm.profile.report.RomSuspiciousCRC;
import jrm.profile.report.SubjectSet;

/**
 * FXML controller for the report tree view.
 * <p>
 * Displays scan results in a hierarchical tree with context menu actions for
 * viewing details, copying hashes, searching the web, downloading ROMs, and
 * exporting profiles. Supports filtering by entry type (OK, missing, wrong, etc.).
 *
 * @since 2.5
 */
public class ReportViewController implements Initializable {
    /** The report tree view. */
    @FXML
    protected TreeView<Object> treeview;
    /** The context menu. */
    @FXML
    private ContextMenu menu;
    /** The open all nodes menu item. */
    @FXML
    private MenuItem openAllNodes;
    /** The close all nodes menu item. */
    @FXML
    private MenuItem closeAllNodes;
    /** The show OK entries check menu item. */
    @FXML
    private CheckMenuItem showok;
    /** The hide missing entries check menu item. */
    @FXML
    private CheckMenuItem hidemissing;
    /** The detail menu item. */
    @FXML
    private MenuItem detail;
    /** The copy CRC menu item. */
    @FXML
    private MenuItem copyCrc;
    /** The copy SHA-1 menu item. */
    @FXML
    private MenuItem copySha1;
    /** The copy name menu item. */
    @FXML
    private MenuItem copyName;
    /** The search web menu item. */
    @FXML
    private MenuItem searchWeb;
    /** The download button. */
    @FXML
    private Button download;
    /** The copy-report button. */
    @FXML
    private Button copyReport;
    /** The create-fixDAT button. */
    @FXML
    private Button createFixDat;
    /** The export-as menu button. */
    @FXML
    private MenuButton exportAs;
    /** The copyable summary and missing/partial title list. */
    @FXML
    private TextArea summary;

    /** The active filter options. */
    private static final Set<FilterOptions> filterOptions = new HashSet<>();

    /** The report being displayed. */
    private Report report;

    /**
     * Initializes the controller: sets up menu item icons and configures
     * context menu item enable/disable based on selection state.
     *
     * @param location  the FXML location
     * @param resources the resource bundle
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        openAllNodes.setGraphic(new ImageView(MainFrame.getIcon("/jrm/resicons/folder_open.png")));
        closeAllNodes.setGraphic(new ImageView(MainFrame.getIcon("/jrm/resicons/folder_closed.png")));
        showok.setGraphic(new ImageView(MainFrame.getIcon("/jrm/resicons/folder_closed_green.png")));
        hidemissing.setGraphic(new ImageView(MainFrame.getIcon("/jrm/resicons/folder_closed_red.png")));
        menu.setOnShowing(_ -> {
            final var item = treeview.getSelectionModel().getSelectedItem();
            final var disabled = !(item != null && item.getValue() instanceof Note);
            detail.setDisable(disabled);
            copyCrc.setDisable(disabled);
            copySha1.setDisable(disabled);
            copyName.setDisable(disabled);
            searchWeb.setDisable(disabled);
        });
    }

    /**
     * Sets the report to display and rebuilds the tree.
     *
     * @param report the report
     */
    public void setReport(Report report) {
        this.report = report;
        build();
    }

    /**
     * Builds the report tree from the current report and filter options.
     */
    private void build() {
        final var root = new TreeItem<Object>(report);
        if (report != null) {
            report.stream(filterOptions).forEachOrdered(s -> {
                final var sitem = new TreeItem<Object>(s);
                s.stream(filterOptions).forEach(n -> sitem.getChildren().add(new TreeItem<>(n)));
                root.getChildren().add(sitem);
            });
            download.setDisable(!Optional.ofNullable(report.getReportFile()).map(File::exists).orElse(false));
            exportAs.setDisable(report.getProfile() == null);
            if (createFixDat != null)
                createFixDat.setDisable(report.getProfile() == null);
            if (copyReport != null)
                copyReport.setDisable(false);
            if (summary != null)
                summary.setText(report.getSummaryText());
        } else {
            download.setDisable(true);
            exportAs.setDisable(true);
            if (createFixDat != null)
                createFixDat.setDisable(true);
            if (copyReport != null)
                copyReport.setDisable(true);
            if (summary != null)
                summary.clear();
        }
        treeview.setShowRoot(false);
        treeview.setCellFactory(_ -> new ReportTreeCell());
        treeview.setRoot(root);
    }

    /**
     * Shows the detail dialog for the selected note.
     *
     * @param e the action event
     */
    @FXML
    private void detail(javafx.event.ActionEvent e) {
        if (treeview.getSelectionModel().getSelectedItem() != null && treeview.getSelectionModel().getSelectedItem().getValue() instanceof Note note)
            detail(note);
    }

    /**
     * Shows a detail dialog for a note.
     *
     * @param note the note to display
     */
    private static void detail(Note note) {
        final var node = new TextArea(note.getDetail());
        node.setEditable(false);
        Dialogs.showConfirmation("Detail", node, ButtonType.OK);
    }

    /**
     * Copies the CRC of the selected note to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    private void copyCrc(javafx.event.ActionEvent e) {
        if (treeview.getSelectionModel().getSelectedItem() != null && treeview.getSelectionModel().getSelectedItem().getValue() instanceof Note note) {
            final var content = new ClipboardContent();
            content.putString(note.getCrc());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Copies the SHA-1 hash of the selected note to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    private void copySha1(javafx.event.ActionEvent e) {
        if (treeview.getSelectionModel().getSelectedItem() != null && treeview.getSelectionModel().getSelectedItem().getValue() instanceof Note note) {
            final var content = new ClipboardContent();
            content.putString(note.getSha1());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Copies the name of the selected note to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    private void copyName(javafx.event.ActionEvent e) {
        if (treeview.getSelectionModel().getSelectedItem() != null && treeview.getSelectionModel().getSelectedItem().getValue() instanceof Note note) {
            final var content = new ClipboardContent();
            content.putString(note.getName());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Searches the web for the selected note using Google.
     *
     * @param e the action event
     */
    @FXML
    private void searchWeb(javafx.event.ActionEvent e) {
        if (treeview.getSelectionModel().getSelectedItem() != null && treeview.getSelectionModel().getSelectedItem().getValue() instanceof Note note) {
            try {
                final var name = note.getName();
                final var crc = note.getCrc();
                final var sha1 = note.getSha1();
                final var hash = Optional.ofNullable(Optional.ofNullable(crc).orElse(sha1)).map(h -> '+' + h).orElse("");
                MainFrame.getApplication().getHostServices()
                        .showDocument(new URI("https://www.google.com/search?q=" + URLEncoder.encode('"' + name + '"', "UTF-8") + hash).toString());
            } catch (IOException | URISyntaxException e1) {
                Log.err(e1.getMessage(), e1);
            }
        }
    }

    /**
     * Toggles the display of OK entries in the report.
     *
     * @param e the action event
     */
    @FXML
    private void showok(javafx.event.ActionEvent e) {
        if (showok.isSelected())
            filterOptions.add(FilterOptions.SHOWOK);
        else
            filterOptions.remove(FilterOptions.SHOWOK);
        build();
    }

    /**
     * Toggles the display of missing entries in the report.
     *
     * @param e the action event
     */
    @FXML
    private void hidemissing(javafx.event.ActionEvent e) {
        if (hidemissing.isSelected())
            filterOptions.add(FilterOptions.HIDEMISSING);
        else
            filterOptions.remove(FilterOptions.HIDEMISSING);
        build();
    }

    /**
     * Expands all nodes in the tree view.
     *
     * @param e the action event
     */
    @FXML
    private void openAllNodes(javafx.event.ActionEvent e) {
        final var root = treeview.getRoot();
        treeview.setRoot(null);
        for (TreeItem<?> child : root.getChildren())
            if (!child.isLeaf())
                child.setExpanded(true);
        treeview.setRoot(root);
    }

    /**
     * Collapses all nodes in the tree view.
     *
     * @param e the action event
     */
    @FXML
    private void closeAllNodes(javafx.event.ActionEvent e) {
        final var root = treeview.getRoot();
        treeview.setRoot(null);
        for (TreeItem<?> child : root.getChildren())
            if (!child.isLeaf())
                child.setExpanded(false);
        treeview.setRoot(root);
    }

    /**
     * Opens the report file in the default desktop application.
     *
     * @param e the action event
     */
    @FXML
    private void download(javafx.event.ActionEvent e) {
        if (report == null)
            return;
        try {
            Desktop.getDesktop().open(report.getReportFile());
        } catch (IOException e1) {
            Log.err(e1.getMessage(), e1);
        }
    }

    /**
     * Copies the full report text (summary, missing/partial titles, and details) to the clipboard.
     *
     * @param e the action event
     */
    @FXML
    private void copyReport(javafx.event.ActionEvent e) {
        if (report == null)
            return;
        final var content = new ClipboardContent();
        content.putString(report.toCopyableText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Exports a fixDAT containing missing and partial titles.
     *
     * @param e the action event
     */
    @FXML
    private void createFixDat(javafx.event.ActionEvent e) {
        if (report == null || report.getProfile() == null)
            return;
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), Report.resolveFixDatType(report.getProfile()),
                EnumSet.of(ExportMode.MISSING), null);
    }

    /**
     * Exports filtered entries as a Logiqx DAT file.
     *
     * @param e the action event
     */
    @FXML
    private void exportFilteredAsLogiqxDat(javafx.event.ActionEvent e) {
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), ExportType.DATAFILE, EnumSet.of(ExportMode.FILTERED), null);
    }

    /**
     * Exports filtered entries as a MAME DAT file.
     *
     * @param e the action event
     */
    @FXML
    private void exportFilteredAsMameDat(javafx.event.ActionEvent e) {
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), ExportType.MAME, EnumSet.of(ExportMode.FILTERED), null);
    }

    /**
     * Exports filtered entries as software lists.
     *
     * @param e the action event
     */
    @FXML
    private void exportFilteredAsSoftwareLists(javafx.event.ActionEvent e) {
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), null);
    }

    /**
     * Exports all entries as a Logiqx DAT file.
     *
     * @param e the action event
     */
    @FXML
    private void exportAllAsLogiqxDat(javafx.event.ActionEvent e) {
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), ExportType.DATAFILE, EnumSet.of(ExportMode.ALL), null);
    }

    /**
     * Exports all entries as a MAME DAT file.
     *
     * @param e the action event
     */
    @FXML
    private void exportAllAsMameDat(javafx.event.ActionEvent e) {
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), ExportType.MAME, EnumSet.of(ExportMode.ALL), null);
    }

    /**
     * Exports all entries as software lists.
     *
     * @param e the action event
     */
    @FXML
    private void exportAllAsSoftwareLists(javafx.event.ActionEvent e) {
        MainFrame.export(treeview.getScene().getWindow(), report.getProfile().getSession(), ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), null);
    }

    /**
     * Custom tree cell renderer for report entries.
     * <p>
     * Renders each report item type with appropriate icons, colors, and localized
     * text. Handles SubjectSet, suspicious CRC containers, container types, and
     * entry types (add, missing, OK, unneeded, wrong hash, wrong name).
     */
    private static final class ReportTreeCell extends TreeCell<Object> {
        /** Bold font weight style constant. */
        private static final String FX_FONT_WEIGHT_BOLD = "-fx-font-weight: bold;";

        /** Regex pattern to split localized template strings. */
        private static final Pattern regex = Pattern.compile("%s");

        /** Localized template parts for "X missing" SubjectSet text. */
        private static final String[] missing = regex.split(Messages.getString("SubjectSet.Missing"));
        /** Localized template parts for "X unneeded" SubjectSet text. */
        private static final String[] unneeded = regex.split(Messages.getString("SubjectSet.Unneeded"));
        /** Localized template parts for "X found, need fixes" SubjectSet text. */
        private static final String[] foundneedfixes = regex.split(Messages.getString("SubjectSet.FoundNeedFixes"));
        /** Localized template parts for "X found, incomplete" SubjectSet text. */
        private static final String[] foundincomplete = regex.split(Messages.getString("SubjectSet.FoundIncomplete"));
        /** Localized template parts for "X found" SubjectSet text. */
        private static final String[] found = regex.split(Messages.getString("SubjectSet.Found"));
        /** Localized template parts for "X missing, totally created" SubjectSet text. */
        private static final String[] missingtotallycreated = regex.split(Messages.getString("SubjectSet.MissingTotallyCreated"));
        /** Localized template parts for "X missing, partially created" SubjectSet text. */
        private static final String[] missingpartiallycreated = regex.split(Messages.getString("SubjectSet.MissingPartiallyCreated"));
        /** Localized template parts for unknown SubjectSet status text. */
        private static final String[] unknown = regex.split(Messages.getString("SubjectSet.Unknown"));
        /** Localized template parts for entry-add text. */
        private static final String[] eadd = regex.split(Messages.getString("EntryAddAdd"));
        /** Localized template parts for entry-missing text. */
        private static final String[] emissing = regex.split(Messages.getString("EntryMissing.Missing"));
        /** Localized template parts for entry-missing-duplicate text. */
        private static final String[] emissingdup = regex.split(Messages.getString("EntryMissingDuplicate.MissingDuplicate"));
        /** Localized template parts for entry-OK text. */
        private static final String[] eok = regex.split(Messages.getString("EntryOK.OK"));
        /** Localized template parts for entry-unneeded text. */
        private static final String[] eunneeded = regex.split(Messages.getString("EntryUnneeded.Unneeded"));
        /** Localized template parts for entry-wrong-hash text. */
        private static final String[] ewronghash = regex.split(Messages.getString("EntryWrongHash.Wrong"));
        /** Localized template parts for entry-wrong-name text. */
        private static final String[] ewrongname = regex.split(Messages.getString("EntryWrongName.Wrong"));

        /**
         * Constructs a tree cell with double-click to show note details.
         */
        public ReportTreeCell() {
            setOnMouseClicked(e -> {
                final var ti = getTreeItem();
                if (ti == null || e.getClickCount() < 2)
                    return;
                if (ti.getValue() instanceof Note note)
                    detail(note);
            });
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setGraphic(null);
                setText(null);
            } else {
                switch (item) {
                    case SubjectSet s -> updateSubjectSet(s);
                    case RomSuspiciousCRC s -> updateRomSuspiciousCRC(s);
                    case ContainerTZip s -> updateContainerTZip(s);
                    case ContainerUnknown s -> updateContainerUnknown(s);
                    case ContainerUnneeded s -> updateContainerUnneeded(s);
                    case EntryAdd s -> updateEntryAdd(s);
                    case EntryMissing s -> updateEntryMissing(s);
                    case EntryMissingDuplicate s -> updateEntryMissingDuplicate(s);
                    case EntryOK s -> updateEntryOK(s);
                    case EntryUnneeded s -> updateEntryUnneeded(s);
                    case EntryWrongHash s -> updateEntryWrongHash(s);
                    case EntryWrongName s -> updateEntryWrongName(s);
                    default -> {
                        setGraphic(null);
                        setText(item.toString());
                    }
                }
            }
            setAlignment(Pos.CENTER_LEFT);
        }

        /**
         * Renders an unneeded container with an exclamation icon.
         *
         * @param s the unneeded container
         */
        private void updateContainerUnneeded(ContainerUnneeded s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/exclamation.png"));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(s.toString()));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders an unknown container with an error icon.
         *
         * @param s the unknown container
         */
        private void updateContainerUnknown(ContainerUnknown s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/error.png"));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(s.toString()));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a TZip container with a compress icon.
         *
         * @param s the TZip container
         */
        private void updateContainerTZip(ContainerTZip s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/compress.png"));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(s.toString()));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a suspicious CRC ROM with an information icon.
         *
         * @param s the suspicious CRC ROM
         */
        private void updateRomSuspiciousCRC(RomSuspiciousCRC s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/information.png"));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(s.toString()));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a wrong-name entry with a pink bullet icon.
         *
         * @param s the wrong-name entry
         */
        private void updateEntryWrongName(EntryWrongName s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_pink.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var en = new Text(s.getEntry().getName());
            en.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            final var enn = new Text(s.getEntity().getNormalizedName());
            enn.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(ewrongname[0]), n, new Text(ewrongname[1]), en, new Text(ewrongname[2]), enn, new Text(ewrongname[3]));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a wrong-hash entry with an orange bullet icon.
         *
         * @param s the wrong-hash entry
         */
        private void updateEntryWrongHash(EntryWrongHash s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_orange.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var ef = new Text(s.getEntry().getRelFile());
            ef.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            final String hashname;
            final String ehash;
            final String hash;
            if (s.getEntry().getMd5() == null && s.getEntry().getSha1() == null) {
                hashname = "CRC";
                ehash = s.getEntry().getCrc();
                hash = s.getCrc();
            } else if (s.getEntry().getSha1() == null) {
                hashname = "MD5";
                ehash = s.getEntry().getMd5();
                hash = s.getMd5();
            } else {
                hashname = "SHA-1";
                ehash = s.getEntry().getSha1();
                hash = s.getSha1();
            }
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(ewronghash[0]), n, new Text(ewronghash[1]), ef, new Text(ewronghash[2]), new Text(hashname), new Text(ewronghash[3]), new Text(ehash),
                    new Text(ewronghash[4]), new Text(hash), new Text(ewronghash[5]));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders an unneeded entry with a black bullet icon.
         *
         * @param s the unneeded entry
         */
        private void updateEntryUnneeded(EntryUnneeded s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_black.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var ef = new Text(s.getEntry().getRelFile());
            ef.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            final String hash;
            if (s.getEntry().getSha1() != null)
                hash = s.getEntry().getSha1();
            else if (s.getEntry().getMd5() != null)
                hash = s.getEntry().getMd5();
            else
                hash = s.getEntry().getCrc();
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(eunneeded[0]), n, new Text(eunneeded[1]), ef, new Text(eunneeded[2]), new Text(hash), new Text(eunneeded[3]));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders an OK entry with a green bullet icon.
         *
         * @param s the OK entry
         */
        private void updateEntryOK(EntryOK s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var en = new Text(s.getEntity().getNormalizedName());
            en.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(eok[0]), n, new Text(eok[1]), en, new Text(eok[2]));
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a missing-duplicate entry with a purple bullet icon.
         *
         * @param s the missing-duplicate entry
         */
        private void updateEntryMissingDuplicate(EntryMissingDuplicate s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_purple.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var ef = new Text(s.getEntry().getRelFile());
            ef.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            final var en = new Text(s.getEntity().getName());
            en.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(emissingdup[0]), n, new Text(emissingdup[1]), ef, new Text(emissingdup[2]), en);
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a missing entry with a red bullet icon, including hash if available.
         *
         * @param s the missing entry
         */
        private void updateEntryMissing(EntryMissing s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_red.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var en = new Text(s.getEntity().getName());
            en.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            if (s.getEntity() instanceof Entity e) {
                final String hash;
                if (e.getSha1() != null)
                    hash = e.getSha1();
                else if (e.getMd5() != null)
                    hash = e.getMd5();
                else
                    hash = e.getCrc();
                i.getStyleClass().add("icon");
                HBox hBox = new HBox(i, new Text(emissing[0]), n, new Text(emissing[1]), en, new Text(emissing[2] + " (" + hash + ")"));
                hBox.setAlignment(Pos.CENTER_LEFT);
                setGraphic(hBox);
            } else {
                i.getStyleClass().add("icon");
                HBox hBox = new HBox(i, new Text(emissing[0]), n, new Text(emissing[1]), en, new Text(emissing[2]));
                hBox.setAlignment(Pos.CENTER_LEFT);
                setGraphic(hBox);
            }
            setText(null);
        }

        /**
         * Renders an add entry with a blue bullet icon, showing parent path and file.
         *
         * @param s the add entry
         */
        private void updateEntryAdd(EntryAdd s) {
            final var i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_blue.png"));
            final var n = new Text(s.getParent().getWare().getFullName());
            n.setFill(Color.BLUE);
            final var en = new Text(s.getEntity().getNormalizedName());
            en.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            final var ep = new Text(s.getEntry().getParent().getRelFile().toString());
            ep.styleProperty().bind(new SimpleStringProperty("-fx-font-style: italic;"));
            final var ef = new Text(s.getEntry().getRelFile());
            ef.styleProperty().bind(new SimpleStringProperty(FX_FONT_WEIGHT_BOLD));
            i.getStyleClass().add("icon");
            HBox hBox = new HBox(i, new Text(eadd[0]), n, new Text(eadd[1]), en, new Text(eadd[2]), ep, new Text(eadd[3]), ef);
            hBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Renders a SubjectSet node with the appropriate icon and localized status text.
         *
         * @param s the SubjectSet
         */
        private void updateSubjectSet(SubjectSet s) {
            final String[] t = switch (s.getStatus()) {
                case MISSING -> missing;
                case UNNEEDED -> unneeded;
                case FOUND -> {
                    if (s.hasNotes())
                        yield s.isFixable() ? foundneedfixes : foundincomplete;
                    yield found;
                }
                case CREATE, CREATEFULL -> s.isFixable() ? missingtotallycreated : missingpartiallycreated;
                default -> unknown;
            };
            final ImageView i;
            if (s.getNotes().isEmpty() && SubjectSet.Status.FOUND.equals(s.getStatus()))
                i = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png"));
            else
                i = new ImageView(MainFrame.getIcon(getFolderIcon(s, false)));
            i.getStyleClass().add("icon");
            final var n = new Text(s.getWare().getFullName());
            n.setFill(Color.BLUE);
            final var d = new Text(s.getWare().getDescription().toString());
            d.setFill(Color.PURPLE);
            final var hBox = new HBox(i, new Text(t[0]), n, new Text(t[1]), d, new Text(t[2]));
            hBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setMargin(i, new Insets(0, 2, 0, 0));
            setGraphic(hBox);
            setText(null);
        }

        /**
         * Returns the folder icon path for a SubjectSet, based on its status.
         *
         * @param value    the SubjectSet value
         * @param expanded whether the node is expanded
         * @return the icon resource path
         */
        private String getFolderIcon(Object value, final boolean expanded) {
            String icon = "/jrm/resicons/folder"; //$NON-NLS-1$
            icon += expanded ? "_open" : "_closed";
            if (value instanceof SubjectSet s) {
                icon += switch (s.getStatus()) {
                    case FOUND -> {
                        if (!s.hasNotes())
                            yield "_green";
                        yield s.isFixable() ? "_purple" : "_orange";
                    }
                    case CREATE, CREATEFULL -> s.isFixable() ? "_blue" : "_orange";
                    case MISSING -> "_red"; //$NON-NLS-1$
                    case UNNEEDED -> "_gray"; //$NON-NLS-1$
                    default -> "";
                };
            }
            icon += ".png"; //$NON-NLS-1$
            return icon;
        }
    }
}
