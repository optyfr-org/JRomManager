package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableValueBase;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.batch.Compressor;
import jrm.batch.CompressorFormat;
import jrm.batch.DirUpdater;
import jrm.batch.DirUpdaterResults;
import jrm.batch.TorrentChecker;
import jrm.batch.TorrentChecker.Options;
import jrm.batch.TrntChkReport;
import jrm.fx.ui.controls.ButtonCellFactory;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.controls.DropCell;
import jrm.fx.ui.controls.NodeCellFactory;
import jrm.fx.ui.misc.DragNDrop;
import jrm.fx.ui.misc.FileResult;
import jrm.fx.ui.misc.SrcDstResult;
import jrm.fx.ui.progress.ProgressTask;
import jrm.io.torrent.options.TrntChkMode;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.ProfileSettings;
import jrm.misc.SettingsEnum;
import jrm.security.PathAbstractor;

/**
 * FXML controller for the batch tools panel.
 * <p>
 * Provides three batch operations: DAT to directory extraction, torrent checking, and archive compression. Each operation runs in a
 * background task with progress reporting and supports drag-and-drop file input.
 *
 * @since 2.5
 */
public class BatchToolsPanelController extends BaseController {
    /** The path to the bullet/go icon resource. */
    private static final String ICON_BULLET_GO = "/jrm/resicons/icons/bullet_go.png";
    /** The localized string for the cancelled status. */
    private static final String CANCELLED = "Cancelled";

    /** FXML-injected tab for the DAT to directory extraction operation. */
    @FXML
    private Tab panelBatchToolsDat2Dir;
    /** FXML-injected tab for the torrent checking operation. */
    @FXML
    private Tab panelBatchToolsDir2Torrent;
    /** FXML-injected tab for the DAT to torrent operation (not yet implemented). */
    @FXML
    private Tab panelBatchToolsDat2Torrent;

    /** FXML-injected button to clear the DAT-to-directory source list. */
    @FXML
    private Button btnBatchToolsDat2DirClear;
    /** FXML-injected button to clear the torrent check list. */
    @FXML
    private Button btnBatchToolsTrntChkClear;    
    /** FXML-injected button to start the DAT-to-directory batch operation. */
    @FXML
    private Button btnBatchToolsDat2DirStart;
    /** FXML-injected button to start the DAT-to-torrent batch operation. */
    @FXML
    private Button btnBatchToolsDat2TorrentStart;
    /** FXML-injected button to clear the DAT-to-torrent list. */
    @FXML
    private Button btnBatchToolsDat2TorrentClear;
    /** FXML-injected checkbox for the DAT-to-torrent dry-run mode. */
    @FXML
    private CheckBox cbBatchToolsDat2TorrentDryRun;
    /** FXML-injected choice box for the DAT-to-torrent check mode. */
    @FXML
    private ChoiceBox<TrntChkMode> cbbxBatchToolsDat2TorrentMode;
    /** FXML-injected table view for the DAT-to-torrent source files. */
    @FXML
    private TableView<File> tvBatchToolsDat2TorrentSrc;
    /** FXML-injected column for the DAT-to-torrent source file path. */
    @FXML
    private TableColumn<File, File> tvBatchToolsDat2TorrentSrcCol;
    /** FXML-injected context menu for the DAT-to-torrent source list. */
    @FXML
    private ContextMenu popupMenuSrcDat2Torrent;
    /** FXML-injected menu item to add a DAT-to-torrent source directory. */
    @FXML
    private MenuItem mnDat2TorrentAddSrcDir;
    /** FXML-injected menu item to delete a DAT-to-torrent source directory. */
    @FXML
    private MenuItem mnDat2TorrentDelSrcDir;
    /** FXML-injected table view for the DAT-to-torrent destination results. */
    @FXML
    private TableView<SrcDstResult> tvBatchToolsDat2TorrentDst;
    /** FXML-injected column for the DAT-to-torrent destination DAT path. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsDat2TorrentDstDatsCol;
    /** FXML-injected column for the DAT-to-torrent selection checkbox. */
    @FXML
    private TableColumn<SrcDstResult, Boolean> tvBatchToolsDat2Torrent;
    /** FXML-injected tab for the archive compression operation. */
    @FXML
    private Tab panelBatchToolsCompressor;
    /** FXML-injected button to start the directory-to-DAT batch operation. */
    @FXML
    private Button btnBatchToolsDir2DatStart;
    /** FXML-injected button to start the torrent check operation. */
    @FXML
    private Button btnBatchToolsTrntChkStart;
    /** FXML-injected button to start the compression operation. */
    @FXML
    private Button btnBatchToolsCompressorStart;
    /** FXML-injected button to clear the compressor list. */
    @FXML
    private Button btnBatchToolsCompressorClear;
    /** FXML-injected choice box for the torrent check mode. */
    @FXML
    private ChoiceBox<TrntChkMode> cbbxBatchToolsTrntChk;
    /** FXML-injected choice box for the compressor output format. */
    @FXML
    private ChoiceBox<CompressorFormat> cbbxBatchToolsCompressorFormat;
    /** FXML-injected checkbox for the DAT-to-directory dry-run mode. */
    @FXML
    private CheckBox cbBatchToolsDat2DirDryRun;
    /** FXML-injected checkbox for detecting archived folders during torrent check. */
    @FXML
    private CheckBox cbBatchToolsTrntChkDetectArchivedFolder;
    /** FXML-injected checkbox for removing unknown files during torrent check. */
    @FXML
    private CheckBox cbBatchToolsTrntChkRemoveUnknownFiles;
    /** FXML-injected checkbox for removing wrong-sized files during torrent check. */
    @FXML
    private CheckBox cbBatchToolsTrntChkRemoveWrongSizedFiles;
    /** FXML-injected checkbox for forcing recompression even if the target format matches. */
    @FXML
    private CheckBox cbBatchToolsCompressorForce;
    /** FXML-injected table view for the DAT-to-directory source directories. */
    @FXML
    private TableView<File> tvBatchToolsDat2DirSrc;
    /** FXML-injected column for the DAT-to-directory source directory path. */
    @FXML
    private TableColumn<File, File> tvBatchToolsDat2DirSrcCol;
    /** FXML-injected context menu for the DAT-to-directory source list. */
    @FXML
    private ContextMenu popupMenuSrc;
    /** FXML-injected menu item to add a DAT-to-directory source directory. */
    @FXML
    private MenuItem mnDat2DirAddSrcDir;
    /** FXML-injected menu item to delete a DAT-to-directory source directory. */
    @FXML
    private MenuItem mnDat2DirDelSrcDir;
    /** FXML-injected table view for the DAT-to-directory destination results. */
    @FXML
    private TableView<SrcDstResult> tvBatchToolsDat2DirDst;
    /** FXML-injected column for the DAT-to-directory destination DAT path. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsDat2DirDstDatsCol;
    /** FXML-injected column for the DAT-to-directory destination directory path. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsDat2DirDstDirsCol;
    /** FXML-injected column for the DAT-to-directory result status text. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsDat2DirDstResultCol;
    /** FXML-injected column for the DAT-to-directory detail button. */
    @FXML
    private TableColumn<SrcDstResult, SrcDstResult> tvBatchToolsDat2DirDstDetailsCol;
    /** FXML-injected column for the DAT-to-directory selection checkbox. */
    @FXML
    private TableColumn<SrcDstResult, Boolean> tvBatchToolsDat2DirDstSelCol;
    /** FXML-injected context menu for the DAT-to-directory destination list. */
    @FXML
    private ContextMenu popupMenuDst;
    /** FXML-injected menu item to delete a DAT-to-directory destination DAT entry. */
    @FXML
    private MenuItem mnDat2DirDelDstDat;
    /** FXML-injected menu for the DAT-to-directory destination presets submenu. */
    @FXML
    private Menu mntmDat2DirDstPresets;
    /** FXML-injected table view for the torrent check source entries. */
    @FXML
    private TableView<SrcDstResult> tvBatchToolsTorrent;
    /** FXML-injected column for the torrent check source file path. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsTorrentFilesCol;
    /** FXML-injected column for the torrent check destination directory path. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsTorrentDstDirsCol;
    /** FXML-injected column for the torrent check result status text. */
    @FXML
    private TableColumn<SrcDstResult, String> tvBatchToolsTorrentResultCol;
    /** FXML-injected column for the torrent check detail button. */
    @FXML
    private TableColumn<SrcDstResult, SrcDstResult> tvBatchToolsTorrentDetailsCol;
    /** FXML-injected column for the torrent check selection checkbox. */
    @FXML
    private TableColumn<SrcDstResult, Boolean> tvBatchToolsTorrentSelCol;
    /** FXML-injected context menu for the torrent check list. */
    @FXML
    private ContextMenu popupMenuTorrent;
    /** FXML-injected menu item to delete a torrent entry. */
    @FXML
    private MenuItem mnDelTorrent;

    /** FXML-injected table view for the compressor file list. */
    @FXML
    private TableView<FileResult> tvBatchToolsCompressor;
    /** FXML-injected column for the compressor source file path. */
    @FXML
    private TableColumn<FileResult, Path> tvBatchToolsCompressorFileCol;
    /** FXML-injected column for the compressor status text. */
    @FXML
    private TableColumn<FileResult, String> tvBatchToolsCompressorStatusCol;

    /**
     * Initializes the controller after the FXML fields have been injected.
     *
     * @param location the location used to resolve relative paths for the root object, or {@code null} if the location is not known
     * @param resources the resources used to localize the root object, or {@code null} if the root object was not localized
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initDat2Dir();
        initTorrent();
        initCompressor();
    }

    /**
     * Initializes the compressor panel.
     */
    private void initCompressor() {
        ImageView compressoriv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/compress.png"));
        compressoriv.setPreserveRatio(true);
        compressoriv.getStyleClass().add("icon");
        panelBatchToolsCompressor.setGraphic(compressoriv);

        new DragNDrop(tvBatchToolsCompressor).addAny(this::addFilesToCompressorList);
        tvBatchToolsCompressorFileCol.setCellFactory(_ -> createBatchToolsCompressorFileCell());
        tvBatchToolsCompressorFileCol.setCellValueFactory(param -> param.getValue().fileProperty());
        tvBatchToolsCompressorStatusCol.setCellFactory(_ -> createBatchToolsCompressorStatusCell());
        tvBatchToolsCompressorStatusCol.setCellValueFactory(param -> param.getValue().resultProperty());

        cbbxBatchToolsCompressorFormat.setItems(FXCollections.observableArrayList(CompressorFormat.values()));
        cbbxBatchToolsCompressorFormat.getSelectionModel().select(CompressorFormat.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.compressor_format))); // $NON-NLS-1$
        cbbxBatchToolsCompressorFormat.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> configureCompressorFormat(newValue));

        cbBatchToolsCompressorForce.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.compressor_force, Boolean.class));
        cbBatchToolsCompressorForce.selectedProperty().addListener((_, _, newValue) -> configureCompressorForce(newValue));

        ImageView compressorcleariv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bin.png"));
        compressorcleariv.setPreserveRatio(true);
        compressorcleariv.getStyleClass().add("icon");
        btnBatchToolsCompressorClear.setGraphic(compressorcleariv);

        ImageView compressorgoiv = new ImageView(MainFrame.getIcon(ICON_BULLET_GO));
        compressorgoiv.setPreserveRatio(true);
        compressorgoiv.getStyleClass().add("icon");
        btnBatchToolsCompressorStart.setGraphic(compressorgoiv);
        btnBatchToolsCompressorStart.setOnAction(_ -> startCompressionProcess());
    }

    /**
     * Starts the compression process in a background thread.
     */
    private void startCompressionProcess() {
        try {
            final var thread = new Thread(startCompression());
            thread.setDaemon(true);
            thread.start();
        } catch (IOException | URISyntaxException e1) /* NOSONAR */ {
            Log.err(e1.getMessage(), e1);
        }
    }

    /**
     * Configures the compressor force setting.
     *
     * @param newValue the new value for the compressor force setting
     */
    private void configureCompressorForce(Boolean newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.compressor_force, newValue);
    }

    /**
     * Configures the compressor format setting.
     *
     * @param newValue the new value for the compressor format setting
     */
    private void configureCompressorFormat(CompressorFormat newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.compressor_format, newValue.toString());
    }

    /**
     * Creates a custom table cell for displaying the compressor status.
     *
     * @return the custom table cell
     */
    private TableCell<FileResult, String> createBatchToolsCompressorStatusCell() {
        return new TableCell<FileResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText("");
                else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    /**
     * Creates a custom table cell for displaying the compressor file path.
     *
     * @return the custom table cell
     */
    private TableCell<FileResult, Path> createBatchToolsCompressorFileCell() {
        return new TableCell<FileResult, Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText("");
                else {
                    setText(item.toString());
                    setTooltip(new Tooltip(getText()));
                }
            }
        };
    }

    /**
     * Starts the compression task in a background thread.
     *
     * @return the progress task
     * 
     * @throws IOException if an I/O error occurs
     * @throws URISyntaxException if the FXML resource URI is invalid
     */
    private ProgressTask<Void> startCompression() throws IOException, URISyntaxException {
        return new ProgressTask<Void>((Stage) btnBatchToolsCompressorStart.getScene().getWindow()) {

            @Override
            protected Void call() throws Exception {
                final var cnt = new AtomicInteger();
                final var compressor = new Compressor(session, cnt, tvBatchToolsCompressor.getItems().size(), this);
                final var useParallelism = session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.compressor_parallelism, Boolean.class);
                final var nThreads = Boolean.TRUE.equals(useParallelism) ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;

                setInfos(nThreads <= 0 ? Runtime.getRuntime().availableProcessors() : nThreads, true);
                tvBatchToolsCompressor.getItems().forEach(fr -> fr.setResult(""));

                try (final var mt = new MultiThreadingVirtual<FileResult>("compressor", this, nThreads, fr -> {
                    if (isCancel())
                        return;
                    compress(cnt, compressor, fr);
                })) {
                    mt.start(tvBatchToolsCompressor.getItems().stream());
                }
                return null;
            }

            @Override
            public void succeeded() {
                close();
            }

            @Override
            protected void failed() {
                close();
                if (getException() instanceof BreakException || isCancelled())
                    Dialogs.showAlert(CANCELLED);
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
        };
    }

    /**
     * Compresses a single file using the selected format.
     *
     * @param cnt the compression counter
     * @param compressor the compressor instance
     * @param fr the file result to compress
     */
    private void compress(final AtomicInteger cnt, final Compressor compressor, FileResult fr) {
        var file = fr.getFile().toFile();
        cnt.incrementAndGet();
        Compressor.UpdResultCallBack cb = fr::setResult;
        Compressor.UpdSrcCallBack scb = src -> fr.setFile(src.toPath());
        switch (cbbxBatchToolsCompressorFormat.getSelectionModel().getSelectedItem()) {
            case SEVENZIP -> toSevenZip(compressor, file, cb, scb);
            case ZIP -> toZip(compressor, file, cb, scb);
            case TZIP -> toTZip(compressor, file, cb, scb);
        }
    }

    /**
     * Converts a file to 7-Zip format.
     *
     * @param compressor the compressor instance
     * @param file the file to convert
     * @param cb the result callback
     * @param scb the source callback
     */
    private void toSevenZip(final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb) {
        switch (FilenameUtils.getExtension(file.getName())) {
            case "zip" -> compressor.zip2SevenZip(file, cb, scb);
            case "7z" -> {
                if (cbBatchToolsCompressorForce.isSelected())
                    compressor.sevenZip2SevenZip(file, cb, scb);
                else
                    cb.apply("Skipped");
            }
            default -> compressor.sevenZip2SevenZip(file, cb, scb);
        }
    }

    /**
     * Converts a file to Zip format.
     *
     * @param compressor the compressor instance
     * @param file the file to convert
     * @param cb the result callback
     * @param scb the source callback
     */
    private void toZip(final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb) {
        if ("zip".equals(FilenameUtils.getExtension(file.getName()))) {
            if (cbBatchToolsCompressorForce.isSelected())
                compressor.zip2Zip(file, cb, scb);
            else
                cb.apply("Skipped");
        } else
            compressor.sevenZip2Zip(file, false, cb, scb);
    }

    /**
     * Converts a file to TZip format.
     *
     * @param compressor the compressor instance
     * @param file the file to convert
     * @param cb the result callback
     * @param scb the source callback
     */
    private void toTZip(final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb) {
        if ("zip".equals(FilenameUtils.getExtension(file.getName())))
            compressor.zip2TZip(file, cbBatchToolsCompressorForce.isSelected(), cb);
        else {
            file = compressor.sevenZip2Zip(file, true, cb, scb);
            if (file != null && file.exists())
                compressor.zip2TZip(file, cbBatchToolsCompressorForce.isSelected(), cb);
        }
    }

    /**
     * Adds files to the compressor list.
     *
     * @param files the list of files to add
     */
    private void addFilesToCompressorList(List<File> files) {
        final var extensions = new String[] { "zip", "7z", "rar", "arj", "tar", "lzh", "lha", "tgz", "tbz", "tbz2", "rpm", "iso", "deb", "cab" };
        final var set = new LinkedHashSet<>(tvBatchToolsCompressor.getItems());
        final var ffiles = files.stream().flatMap(f -> {
            try {
                return f.isDirectory()
                        ? Files.find(f.toPath(), Integer.MAX_VALUE, (p, attr) -> attr.isRegularFile() && FilenameUtils.isExtension(p.getFileName().toString(), extensions))
                        : Stream.of(f.toPath());
            } catch (IOException _) {
                return Stream.empty();
            }
        }).map(FileResult::new).toList();
        set.addAll(ffiles);
        tvBatchToolsCompressor.setItems(FXCollections.observableArrayList(set));
    }

    /**
     * Initializes the torrent check panel.
     */
    private void initTorrent() {
        ImageView torrentiv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/drive_web.png"));
        torrentiv.setPreserveRatio(true);
        torrentiv.getStyleClass().add("icon");
        panelBatchToolsDir2Torrent.setGraphic(torrentiv);

        initTorrentList();

        cbbxBatchToolsTrntChk.setItems(FXCollections.observableArrayList(TrntChkMode.values()));
        cbbxBatchToolsTrntChk.getSelectionModel().select(TrntChkMode.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_mode)));
        cbbxBatchToolsTrntChk.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> handleTrntChkModeChange(newValue));

        cbBatchToolsTrntChkDetectArchivedFolder.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_detect_archived_folders, Boolean.class));
        cbBatchToolsTrntChkDetectArchivedFolder.selectedProperty().addListener((_, _, newValue) -> handleDetectArchivedFolderChange(newValue));

        cbBatchToolsTrntChkRemoveWrongSizedFiles.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_wrong_sized_files, Boolean.class));
        cbBatchToolsTrntChkRemoveWrongSizedFiles.selectedProperty().addListener((_, _, newValue) -> handleRemoveWrongSizedFilesChange(newValue));

        cbBatchToolsTrntChkRemoveUnknownFiles.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_unknown_files, Boolean.class));
        cbBatchToolsTrntChkRemoveUnknownFiles.selectedProperty().addListener((_, _, newValue) -> handleRemoveUnknownFilesChange(newValue));

        btnBatchToolsTrntChkStart.setOnAction(_ -> startTorrent());
        ImageView torrentgoiv = new ImageView(MainFrame.getIcon(ICON_BULLET_GO));
        torrentgoiv.setPreserveRatio(true);
        torrentgoiv.getStyleClass().add("icon");
        btnBatchToolsTrntChkStart.setGraphic(torrentgoiv);
    }

    /**
     * Handles changes to the "Remove Unknown Files" checkbox.
     *
     * @param newValue the new value of the checkbox
     */
    private void handleRemoveUnknownFilesChange(Boolean newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_remove_unknown_files, newValue);
    }

    /**
     * Handles changes to the "Remove Wrong Sized Files" checkbox.
     *
     * @param newValue the new value of the checkbox
     */
    private void handleRemoveWrongSizedFilesChange(Boolean newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_remove_wrong_sized_files, newValue);
    }

    /**
     * Handles changes to the "Detect Archived Folders" checkbox.
     *
     * @param newValue the new value of the checkbox
     */
    private void handleDetectArchivedFolderChange(Boolean newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_detect_archived_folders, newValue);
    }

    /**
     * Handles changes to the torrent check mode selection.
     *
     * @param newValue the new value of the torrent check mode
     */
    private void handleTrntChkModeChange(TrntChkMode newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_mode, newValue.toString());
        cbBatchToolsTrntChkRemoveWrongSizedFiles.setDisable(newValue == TrntChkMode.FILENAME);
    }

    /**
     * Initializes the torrent list.
     */
    private void initTorrentList() {
        tvBatchToolsTorrent.getItems().setAll(SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr)));
        tvBatchToolsTorrentFilesCol.setCellFactory(_ -> createTorrentDropCell());
        tvBatchToolsTorrentFilesCol.setCellValueFactory(param -> param.getValue().srcProperty());
        tvBatchToolsTorrentDstDirsCol.setCellFactory(_ -> createTorrentDstDropCell());
        tvBatchToolsTorrentDstDirsCol.setCellValueFactory(param -> param.getValue().dstProperty());
        tvBatchToolsTorrentResultCol.setCellFactory(_ -> new NodeCellFactory<>());
        tvBatchToolsTorrentResultCol.setCellValueFactory(param -> param.getValue().resultProperty());
        tvBatchToolsTorrentSelCol.setCellFactory(CheckBoxTableCell.forTableColumn(this::createSelectionObservable));
        tvBatchToolsTorrentDetailsCol.setCellFactory(_ -> createTorrentDetailsCellFactory());
        popupMenuTorrent.setOnShowing(_ -> mnDelTorrent.setDisable(tvBatchToolsTorrent.getSelectionModel().isEmpty()));
    }

    /**
     * Creates a button cell factory for the "Detail" button in the torrent list.
     *
     * @return the button cell factory
     */
    private ButtonCellFactory<SrcDstResult, SrcDstResult> createTorrentDetailsCellFactory() {
        return new ButtonCellFactory<>("Detail", cell -> {
            final AbstractSrcDstResult sdr = tvBatchToolsTorrent.getItems().get(cell.getIndex());
            final var results = TrntChkReport.load(session, PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile());
            try {
                new BatchTorrentResults((Stage) tvBatchToolsTorrent.getScene().getWindow(), results);
            } catch (URISyntaxException | IOException e1) /* NOSONAR */ {
                Log.err(e1.getMessage(), e1);
            }
        });
    }

    /**
     * Creates an observable value for the selection state of a torrent list item.
     *
     * @param param the index of the item in the torrent list
     * 
     * @return the observable value representing the selection state
     */
    private ObservableValue<Boolean> createSelectionObservable(Integer param) {
        final var sdr = tvBatchToolsTorrent.getItems().get(param);
        BooleanProperty observable = new SimpleBooleanProperty(sdr.isSelected());
        observable.addListener((_, _, isNowSelected) -> handleSelectionChange(sdr, isNowSelected));
        return observable;
    }

    /**
     * Handles changes to the selection state of a torrent list item.
     *
     * @param sdr the selected/deselected item
     * @param isNowSelected the new selection state
     */
    private void handleSelectionChange(final SrcDstResult sdr, Boolean isNowSelected) {
        sdr.setSelected(isNowSelected);
        saveTorrentDst();
    }

    /**
     * Creates a drop cell for the torrent destination column.
     *
     * @return the drop cell
     */
    private DropCell createTorrentDstDropCell() {
        return new DropCell(tvBatchToolsTorrent, this::handleTorrentDstDrop, File::isDirectory);
    }

    /**
     * Handles dropped destination directories by updating the destination paths in the given list
     * of {@code SrcDstResult} objects and saving the updated list.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of dropped directories
     */
    private void handleTorrentDstDrop(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setDst(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        saveTorrentDst();
    }

    /**
     * Creates a drop cell for the torrent source column.
     *
     * @return the drop cell
     */
    private DropCell createTorrentDropCell() {
        return new DropCell(tvBatchToolsTorrent, this::handleDroppedTorrentFiles, this::isTorrentFile);
    }

    /**
     * Checks if a file is a valid torrent file.
     *
     * @param file the file to check
     * 
     * @return {@code true} if the file is a {@code .torrent} file, {@code false} otherwise
     */
    boolean isTorrentFile(File file) {
        if (Files.isRegularFile(file.toPath()))
            return file.getName().endsWith(".torrent");
        return false;
    }

    /**
     * Handles dropped torrent files by updating the source paths in the given list of {@code SrcDstResult} objects.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of dropped torrent files
     */
    private void handleDroppedTorrentFiles(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setSrc(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        saveTorrentDst();
    }

    /**
     * Initializes the directory to DAT conversion panel.
     */
    private void initDat2Dir() {
        ImageView dat2diriv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/application_cascade.png"));
        dat2diriv.setPreserveRatio(true);
        dat2diriv.getStyleClass().add("icon");
        panelBatchToolsDat2Dir.setGraphic(dat2diriv);
        initDat2DirSrc();
        initDat2DirDst();
        cbBatchToolsDat2DirDryRun.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_dry_run, Boolean.class));
        cbBatchToolsDat2DirDryRun.selectedProperty().addListener((_, _, newValue) -> handleDat2DirDryRunChange(newValue));
        ImageView dat2dirgoiv = new ImageView(MainFrame.getIcon(ICON_BULLET_GO));
        dat2dirgoiv.setPreserveRatio(true);
        dat2dirgoiv.getStyleClass().add("icon");
        btnBatchToolsDir2DatStart.setGraphic(dat2dirgoiv);
        btnBatchToolsDir2DatStart.setOnAction(_ -> startDir2Dat());
    }

    /**
     * Handles changes to the "Dry Run" checkbox for the DAT-to-directory operation.
     *
     * @param newValue the new value of the checkbox
     */
    private void handleDat2DirDryRunChange(Boolean newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_dry_run, newValue);
    }

    /**
     * Checks if a file is a valid DAT/XML file or directory containing DAT/XML files.
     *
     * @param file the file or directory to check
     * 
     * @return {@code true} if the file is a {@code .xml}/{@code .dat} file or a directory containing at least one such file,
     *         {@code false} otherwise
     */
    boolean isValidDatFile(File file) {
        if (Files.isRegularFile(file.toPath()))
            return file.getName().endsWith(".xml") || file.getName().endsWith(".dat");
        if (file.isDirectory())
            try (var stream = Files.list(file.toPath())) {
                return stream.map(Path::toFile).anyMatch(f -> f.getName().endsWith(".xml") || f.getName().endsWith(".dat"));
            } catch (IOException _) {
                // do nothing
            }
        return false;
    }

    /**
     * Initializes the destination table for the directory to DAT conversion.
     */
    private void initDat2DirDst() {
        tvBatchToolsDat2DirDst.setFixedCellSize(Region.USE_COMPUTED_SIZE);
        tvBatchToolsDat2DirDst.getItems().setAll(SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr)));
        tvBatchToolsDat2DirDstDatsCol.setCellFactory(_ -> createDat2DirDstDatsDropCell());
        tvBatchToolsDat2DirDstDatsCol.setCellValueFactory(param -> param.getValue().srcProperty());
        tvBatchToolsDat2DirDstDirsCol.setCellFactory(_ -> createDat2DirDstDirsDropCell());
        tvBatchToolsDat2DirDstDirsCol.setCellValueFactory(param -> param.getValue().dstProperty());
        tvBatchToolsDat2DirDstResultCol.setCellFactory(_ -> createDat2DirDstResultCell());
        tvBatchToolsDat2DirDstResultCol.setCellValueFactory(param -> param.getValue().resultProperty());
        tvBatchToolsDat2DirDstDetailsCol.setCellFactory(_ -> getDat2DirDetailButtonCellFactory());
        tvBatchToolsDat2DirDstSelCol.setCellFactory(CheckBoxTableCell.forTableColumn(this::createDat2DirDstSelectedObservable));
        popupMenuDst.setOnShowing(_ -> updateDstMenuItemsState());
    }

    /**
     * Updates the state of the destination menu items based on the selection in the destination table.
     */
    private void updateDstMenuItemsState() {
        mntmDat2DirDstPresets.setDisable(tvBatchToolsDat2DirDst.getSelectionModel().isEmpty());
        mnDat2DirDelDstDat.setDisable(tvBatchToolsDat2DirDst.getSelectionModel().isEmpty());
    }

    /**
     * Creates an observable value for the selection state of a destination table item.
     *
     * @param param the index of the item in the destination table
     * 
     * @return the observable value representing the selection state
     */
    private ObservableValue<Boolean> createDat2DirDstSelectedObservable(Integer param) {
        final var sdr = tvBatchToolsDat2DirDst.getItems().get(param);
        BooleanProperty observable = new SimpleBooleanProperty(sdr.isSelected());
        observable.addListener((_, _, isNowSelected) -> onDat2DirDstSelectionChanged(sdr, isNowSelected));
        return observable;
    }

    /**
     * Handles changes to the selection state of a destination table item.
     *
     * @param sdr the selected/deselected item
     * @param isNowSelected the new selection state
     */
    private void onDat2DirDstSelectionChanged(final SrcDstResult sdr, Boolean isNowSelected) {
        sdr.setSelected(isNowSelected);
        saveDat2DirDst();
    }

    /**
     * Creates a cell for displaying the result of a DAT to directory conversion.
     *
     * @return the cell for displaying the result
     */
    private TableCell<SrcDstResult, String> createDat2DirDstResultCell() {
        return new TableCell<SrcDstResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText("");
                else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    /**
     * Creates a drop cell for handling directory drops in the DAT to directory destination table.
     *
     * @return the drop cell for handling directory drops
     */
    private DropCell createDat2DirDstDirsDropCell() {
        return new DropCell(tvBatchToolsDat2DirDst, this::handleDat2DirDstDrop, File::isDirectory);
    }

    /**
     * Handles dropped directories by updating the destination paths in the given list of {@code SrcDstResult} objects.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of dropped directories
     */
    private void handleDat2DirDstDrop(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setDst(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        saveDat2DirDst();
    }

    /**
     * Creates a drop cell for handling DAT file drops in the DAT to directory destination table.
     *
     * @return the drop cell for handling DAT file drops
     */
    private DropCell createDat2DirDstDatsDropCell() {
        return new DropCell(tvBatchToolsDat2DirDst, this::handleDroppedDat2DirDstFiles, this::isValidDatFile);
    }

    /**
     * Handles dropped DAT files by updating the source paths in the given list of {@code SrcDstResult} objects.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of dropped DAT files
     */
    private void handleDroppedDat2DirDstFiles(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setSrc(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        saveDat2DirDst();
    }

    /**
     * Returns a button cell factory for the "Detail" button in the DAT to directory destination table.
     *
     * @return the button cell factory
     */
    private ButtonCellFactory<SrcDstResult, SrcDstResult> getDat2DirDetailButtonCellFactory() {
        return new ButtonCellFactory<>("Detail", cell -> {
            final AbstractSrcDstResult sdr = tvBatchToolsDat2DirDst.getItems().get(cell.getIndex());
            final var results = DirUpdaterResults.load(session, new File(sdr.getSrc()));
            try {
                new BatchDirUpd8rResults((Stage) tvBatchToolsDat2DirDst.getScene().getWindow(), results);
            } catch (URISyntaxException | IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
        });
    }

    /**
     * Initializes the source directory list for the directory to DAT conversion.
     */
    private void initDat2DirSrc() {
        new DragNDrop(tvBatchToolsDat2DirSrc).addDirs(this::handleDat2DirSrcDrop);
        tvBatchToolsDat2DirSrc.setFixedCellSize(Region.USE_COMPUTED_SIZE);
        tvBatchToolsDat2DirSrc.getItems().setAll(
                Stream.of(StringUtils.split(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_srcdirs), '|')).filter(s -> !s.isBlank()).map(File::new).toList());
        tvBatchToolsDat2DirSrcCol.setCellFactory(_ -> createFileDisplayCell());
        tvBatchToolsDat2DirSrcCol.setCellValueFactory(this::createFileCellValueFactory);
        popupMenuSrc.setOnShowing(_ -> mnDat2DirDelSrcDir.setDisable(tvBatchToolsDat2DirSrc.getSelectionModel().isEmpty()));
        mnDat2DirDelSrcDir.setOnAction(_ -> handleDat2DirDelSrcDir());
        final var lastsrcdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_lastsrcdir)).map(File::new).filter(File::exists).orElse(null);
        mnDat2DirAddSrcDir.setOnAction(_ -> chooseDir(tvBatchToolsDat2DirSrc, null, lastsrcdir, this::onDat2DirSrcDirChosen));
    }

    /**
     * Handles the event when a source directory is chosen for the directory to DAT conversion.
     *
     * @param dir the chosen source directory
     */
    private void onDat2DirSrcDirChosen(Path dir) {
        tvBatchToolsDat2DirSrc.getItems().add(dir.toFile());
        session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_lastsrcdir, dir.toFile().getParent());
        saveDat2DirSrc();
    }

    /**
     * Handles the deletion of selected source directories from the directory to DAT conversion source list.
     */
    private void handleDat2DirDelSrcDir() {
        tvBatchToolsDat2DirSrc.getItems().removeAll(tvBatchToolsDat2DirSrc.getSelectionModel().getSelectedItems());
        saveDat2DirSrc();
    }

    /**
     * Creates an observable value for a file in the source directory list.
     *
     * @param param the cell data features containing the file
     * 
     * @return the observable value representing the file
     */
    private ObservableValueBase<File> createFileCellValueFactory(CellDataFeatures<File, File> param) {
        return new ObservableValueBase<>() {
            @Override
            public File getValue() {
                return param.getValue();
            }
        };
    }

    /**
     * Creates a table cell for displaying a file in the source directory list.
     *
     * @return the table cell for displaying a file
     */
    private TableCell<File, File> createFileDisplayCell() {
        return new TableCell<File, File>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : item.toString());
            }
        };
    }

    /**
     * Handles the event when directories are dropped onto the source directory list for the directory to DAT conversion.
     *
     * @param dirs the list of dropped directories
     */
    private void handleDat2DirSrcDrop(List<File> dirs) {
        tvBatchToolsDat2DirSrc.getItems().addAll(dirs);
        saveDat2DirSrc();
    }

    /**
     * Starts the directory to DAT conversion task in a background thread.
     */
    private void startDir2Dat() {
        if (!tvBatchToolsDat2DirSrc.getItems().isEmpty()) {
            final List<SrcDstResult> sdrl = tvBatchToolsDat2DirDst.getItems();
            if (sdrl.stream().filter(sdr -> !session.getUser().getSettings().getProfileSettingsFile(PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile()).exists())
                    .count() > 0)
                Dialogs.showAlert(Messages.getString("MainFrame.AllDatsPresetsAssigned"));
            else {
                try {
                    Thread.startVirtualThread(buildDir2DatTask(sdrl, createResultColUpdater()));
                } catch (URISyntaxException | IOException ex) {
                    Log.err(ex.getMessage(), ex);
                }
            }
        } else
            Dialogs.showAlert(Messages.getString("MainFrame.AtLeastOneSrcDir"));
    }

    /**
     * Creates a result column updater for the DAT-to-directory batch operation.
     *
     * @return a new {@link ResultColUpdater} that updates the DAT-to-directory destination table rows
     */
    private ResultColUpdater createResultColUpdater() {
        return new ResultColUpdater() {
            @Override
            public void updateResult(int row, String result) {
                tvBatchToolsDat2DirDst.getItems().get(row).setResult(result);
            }

            @Override
            public void clearResults() {
                for (final AbstractSrcDstResult item : tvBatchToolsDat2DirDst.getItems())
                    item.setResult("");
            }
        };
    }

    /**
     * Starts the torrent check task in a background thread.
     */
    private void startTorrent() {
        if (!tvBatchToolsTorrent.getItems().isEmpty()) {
            final List<SrcDstResult> sdrl = tvBatchToolsTorrent.getItems();

            final TrntChkMode mode = cbbxBatchToolsTrntChk.getSelectionModel().getSelectedItem();
            final var opts = EnumSet.noneOf(TorrentChecker.Options.class);
            if (cbBatchToolsTrntChkRemoveUnknownFiles.isSelected())
                opts.add(TorrentChecker.Options.REMOVEUNKNOWNFILES);
            if (cbBatchToolsTrntChkRemoveWrongSizedFiles.isSelected())
                opts.add(TorrentChecker.Options.REMOVEWRONGSIZEDFILES);
            if (cbBatchToolsTrntChkDetectArchivedFolder.isSelected())
                opts.add(TorrentChecker.Options.DETECTARCHIVEDFOLDERS);

            try {
                Thread.startVirtualThread(buildTorrentTask(sdrl, mode, createTorrentResultUpdater(), opts));
            } catch (URISyntaxException | IOException ex) {
                Log.err(ex.getMessage(), ex);
                Dialogs.showError(ex);
            }
        } else
            Dialogs.showAlert(Messages.getString("MainFrame.AtLeastOneSrcDir"));
    }

    /**
     * Creates a result column updater for the torrent check task.
     *
     * @return the result column updater
     */
    private ResultColUpdater createTorrentResultUpdater() {
        return new ResultColUpdater() {
            @Override
            public void updateResult(int row, String result) {
                tvBatchToolsTorrent.getItems().get(row).setResult(result);
            }

            @Override
            public void clearResults() {
                for (final AbstractSrcDstResult item : tvBatchToolsTorrent.getItems())
                    item.setResult("");
            }
        };
    }

    /**
     * Builds a directory to DAT conversion task.
     * 
     * @param sdrl the list of source-destination results
     * @param updater the result column updater
     * 
     * @return the built progress task
     * 
     * @throws IOException if an I/O error occurs
     * @throws URISyntaxException if the URI syntax is invalid
     */
    private ProgressTask<DirUpdater> buildDir2DatTask(final List<SrcDstResult> sdrl, ResultColUpdater updater) throws IOException, URISyntaxException {
        return new ProgressTask<DirUpdater>((Stage) tvBatchToolsDat2DirDst.getScene().getWindow()) {

            @Override
            protected DirUpdater call() throws Exception {
                final var srclist = tvBatchToolsDat2DirSrc.getItems().stream().map(f -> PathAbstractor.getAbsolutePath(session, f.toString()).toFile()).toList();
                return new DirUpdater(session, sdrl, this, srclist, updater, cbBatchToolsDat2DirDryRun.isSelected());
            }

            @Override
            public void succeeded() {
                close();
                saveDat2DirDst();
                session.setCurrProfile(null);
                session.setCurrScan(null);
                session.getReport().setProfile(session.getCurrProfile());
                if (MainFrame.getProfileViewer() != null) {
                    MainFrame.getProfileViewer().hide();
                    MainFrame.setProfileViewer(null);
                }
                if (MainFrame.getReportFrame() != null)
                    MainFrame.getReportFrame().hide();
                MainFrame.getController().getTabPane().getTabs().get(1).setDisable(true);
            }

            @Override
            protected void failed() {
                close();
                if (getException() instanceof BreakException || isCancelled())
                    Dialogs.showAlert(CANCELLED);
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

        };
    }

    /**
     * Builds a torrent check task.
     * 
     * @param sdrl the list of source-destination results
     * @param mode the torrent check mode
     * @param updater the result column updater
     * @param opts the options
     * 
     * @return the built progress task
     * 
     * @throws IOException if an I/O error occurs
     * @throws URISyntaxException if the URI syntax is invalid
     */
    private ProgressTask<TorrentChecker<SrcDstResult>> buildTorrentTask(final List<SrcDstResult> sdrl, TrntChkMode mode, ResultColUpdater updater, EnumSet<Options> opts)
            throws IOException, URISyntaxException {
        return new ProgressTask<TorrentChecker<SrcDstResult>>((Stage) tvBatchToolsDat2DirDst.getScene().getWindow()) {

            @Override
            protected TorrentChecker<SrcDstResult> call() throws Exception {
                return new TorrentChecker<>(session, this, sdrl, mode, updater, opts);
            }

            @Override
            public void succeeded() {
                close();
            }

            @Override
            protected void failed() {
                close();
                if (getException() instanceof BreakException || isCancelled())
                    Dialogs.showAlert(CANCELLED);
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

        };
    }

    /**
     * Saves the DAT to directory destination items.
     */
    private void saveDat2DirDst() {
        session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(tvBatchToolsDat2DirDst.getItems()));
    }

    /**
     * Saves the torrent destination directories.
     */
    private void saveTorrentDst() {
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(tvBatchToolsTorrent.getItems()));
    }

    /**
     * Saves the source directories for the DAT to directory conversion.
     */
    private void saveDat2DirSrc() {
        session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_srcdirs, String.join("|", tvBatchToolsDat2DirSrc.getItems().stream().map(File::getAbsolutePath).toList()));
    }

    /**
     * Handles the "Custom Presets" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onCustomPresets(ActionEvent e) {
        try {
            if (!tvBatchToolsDat2DirDst.getSelectionModel().isEmpty())
                new CustomPresets((Stage) tvBatchToolsDat2DirDst.getScene().getWindow());
        } catch (IOException | URISyntaxException e1) {
            Log.err(e1.getMessage(), e1);
        }
    }

    /**
     * Handles the "Delete Dat2DirDst" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onDelDat2DirDst(ActionEvent e) {
        tvBatchToolsDat2DirDst.getItems().removeAll(tvBatchToolsDat2DirDst.getSelectionModel().getSelectedItems());
        saveDat2DirDst();
    }

    /**
     * Handles the "Add Dat2DirDst DAT File" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onAddDat2DirDstDat(ActionEvent e) {
        final var lastdstdatdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_lastdstdatdir)).map(File::new).filter(File::exists)
                .filter(File::isDirectory).orElse(null);
        chooseOpenFileMulti(tvBatchToolsDat2DirDst, null, lastdstdatdir, Arrays.asList(new FileChooser.ExtensionFilter("DAT files", "*.dat", "*.xml")), paths -> DropCell
                .process(tvBatchToolsDat2DirDst, tvBatchToolsDat2DirDst.getSelectionModel().getSelectedIndex(), paths.stream().map(Path::toFile).toList(),
                        this::processDat2DirDstFiles));
    }

    /**
     * Processes the dropped DAT files and updates the source paths in the given list of {@code SrcDstResult} objects.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of dropped DAT files
     */
    private void processDat2DirDstFiles(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setSrc(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_lastdstdatdir, files.stream().map(File::getParent).findFirst().orElse(null));
        saveDat2DirDst();
    }

    /**
     * Handles the "Add Dat2DirDst DAT Directory" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onAddDat2DirDstDatDir(ActionEvent e) {
        final var lastdstdatdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_lastdstdatdir)).map(File::new).filter(File::exists)
                .filter(File::isDirectory).orElse(null);
        chooseDir(tvBatchToolsDat2DirDst, null, lastdstdatdir,
                path -> DropCell.process(tvBatchToolsDat2DirDst, tvBatchToolsDat2DirDst.getSelectionModel().getSelectedIndex(), Arrays.asList(path.toFile()),
                        this::processDat2DirDstFiles));
    }

    /**
     * Handles the "Add Dat2DirDst Directory" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onAddDat2DirDstDir(ActionEvent e) {
        final var lastdstdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_lastdstdir)).map(File::new).filter(File::exists)
                .filter(File::isDirectory).orElse(null);
        chooseDir(tvBatchToolsDat2DirDst, null, lastdstdir,
                path -> DropCell.process(tvBatchToolsDat2DirDst, tvBatchToolsDat2DirDst.getSelectionModel().getSelectedIndex(), Arrays.asList(path.toFile()),
                        this::processDat2DirDst));
    }

    /**
     * Processes the dropped directories for the DAT to directory destination table.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of dropped directories
     */
    private void processDat2DirDst(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setDst(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_lastdstdir, files.stream().map(File::getParent).findFirst().orElse(null));
        saveDat2DirDst();
    }

    /**
     * Handles the "TZIP Presets" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onTZIPPresets(ActionEvent e) {
        for (final AbstractSrcDstResult sdr : tvBatchToolsDat2DirDst.getSelectionModel().getSelectedItems())
            ProfileSettings.TZIP(session, PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile());
    }

    /**
     * Handles the "DIR Presets" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onDIRPresets(ActionEvent e) {
        for (final AbstractSrcDstResult sdr : tvBatchToolsDat2DirDst.getSelectionModel().getSelectedItems())
            ProfileSettings.DIR(session, PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile());
    }

    /**
     * Handles the "Add Torrent" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onAddTorrent(ActionEvent e) {
        final var lasttrntdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_lasttrntdir)).map(File::new).filter(File::exists)
                .filter(File::isDirectory).orElse(null);
        chooseOpenFileMulti(tvBatchToolsTorrent, null, lasttrntdir, Arrays.asList(new FileChooser.ExtensionFilter("Torrent files", "*.torrent")), paths -> DropCell
                .process(tvBatchToolsTorrent, tvBatchToolsTorrent.getSelectionModel().getSelectedIndex(), paths.stream().map(Path::toFile).toList(), this::handleAddedTorrents));
    }

    /**
     * Handles the added torrent files by updating the source paths in the given list of {@code SrcDstResult} objects.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of added torrent files
     */
    private void handleAddedTorrents(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setSrc(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_lasttrntdir, files.stream().map(File::getParent).findFirst().orElse(null));
        saveTorrentDst();
    }

    /**
     * Handles the "Add Torrent Destination Directory" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onAddTorrentDstDir(ActionEvent e) {
        final var lastdstdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_lastdstdir)).map(File::new).filter(File::exists)
                .filter(File::isDirectory).orElse(null);
        chooseDir(tvBatchToolsTorrent, null, lastdstdir,
                path -> DropCell.process(tvBatchToolsTorrent, tvBatchToolsTorrent.getSelectionModel().getSelectedIndex(), Arrays.asList(path.toFile()),
                        this::handleTorrentDstSelection));
    }

    /**
     * Handles the selection of a torrent destination directory by updating the destination paths in the given list of
     * {@code SrcDstResult} objects.
     *
     * @param sdrlist the list of {@code SrcDstResult} objects to update
     * @param files the list of selected torrent destination directories
     */
    private void handleTorrentDstSelection(List<SrcDstResult> sdrlist, List<File> files) {
        for (int i = 0; i < files.size(); i++)
            sdrlist.get(i).setDst(PathAbstractor.getRelativePath(session, files.get(i).toPath()).toString());
        session.getUser().getSettings().setProperty(SettingsEnum.trntchk_lastdstdir, files.stream().map(File::getParent).findFirst().orElse(null));
        saveTorrentDst();
    }

    /**
     * Handles the "Delete Torrent" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onDelTorrent(ActionEvent e) {
        tvBatchToolsTorrent.getItems().removeAll(tvBatchToolsTorrent.getSelectionModel().getSelectedItems());
        saveTorrentDst();
    }

    /**
     * Handles the "Add Archive" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onAddArchive(ActionEvent e) {
        final var lastdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.compressor_lastdir)).map(File::new).filter(File::exists)
                .filter(File::isDirectory).orElse(null);
        chooseOpenFileMulti(tvBatchToolsCompressor, null, lastdir, Arrays.asList(new FileChooser.ExtensionFilter("Archive files", "*.zip", "*.7z", "*.rar", "*.arj", "*.tar",
                "*.lzh", "*.lha", "*.tgz", "*.tbz", "*.tbz2", "*.rpm", "*.iso", "*.deb", "*.cab")), this::processSelectedArchives);
    }

    /**
     * Processes the selected archive files by creating {@code FileResult} objects and adding them to the compressor table.
     *
     * @param paths the list of selected archive file paths
     */
    private void processSelectedArchives(List<Path> paths) {
        paths.stream().map(FileResult::new).forEachOrdered(tvBatchToolsCompressor.getItems()::add);
        session.getUser().getSettings().setProperty(SettingsEnum.compressor_lastdir, paths.stream().map(Path::toFile).map(File::getParent).findFirst().orElse(null));
    }

    /**
     * Handles the "Delete Archive" button action.
     * 
     * @param e the action event
     */
    @FXML
    void onDelArchive(ActionEvent e) {
        tvBatchToolsCompressor.getItems().removeAll(tvBatchToolsCompressor.getSelectionModel().getSelectedItems());
    }

    /**
     * Modal dialog for editing custom profile settings (compression, filtering, etc.) on selected
     * DAT-to-directory entries.
     * <p>
     * Loads the {@code ScannerPanelSettings.fxml} layout, initializes a {@link ScannerPanelSettingsController},
     * and saves the modified settings back for each selected entry on OK.
     */
    public class CustomPresets extends Stage {
        /**
         * The controller for the scanner panel settings loaded from the FXML layout.
         */
        ScannerPanelSettingsController controller;

        /**
         * Initializes a new instance of the CustomPresets class.
         * 
         * @param parent the parent stage
         * 
         * @throws IOException if an I/O error occurs
         * @throws URISyntaxException if the URI syntax is invalid
         */
        public CustomPresets(Stage parent) throws IOException, URISyntaxException {
            super();
            initOwner(parent);
            initModality(Modality.WINDOW_MODAL);
            getIcons().add(parent.getIcons().get(0));
            setOnShowing(_ -> {
            });
            setOnCloseRequest(_ -> hide());
            final var loader = new FXMLLoader(getClass().getResource("ScannerPanelSettings.fxml").toURI().toURL(), Messages.getBundle());
            final var settings = loader.<ScrollPane>load();
            controller = loader.getController();
            final var root = new BorderPane(settings);
            final var cancel = new Button("Cancel");
            cancel.setOnAction(_ -> close());
            final var ok = new Button("OK");
            ok.setOnAction(_ -> save());
            final var bar = new HBox(cancel, ok);
            bar.setPadding(new Insets(5));
            bar.setSpacing(5);
            bar.setAlignment(Pos.CENTER_RIGHT);
            root.setBottom(bar);
            setScene(new Scene(root, 600, 400));
            sizeToScene();

            AbstractSrcDstResult entry = tvBatchToolsDat2DirDst.getSelectionModel().getSelectedItems().get(0);
            controller.initProfileSettings(session.getUser().getSettings().loadProfileSettings(PathAbstractor.getAbsolutePath(session, entry.getSrc()).toFile(), null));

            show();
        }

        /**
         * Saves the profile settings.
         */
        private void save() {
            for (final AbstractSrcDstResult sdr : tvBatchToolsDat2DirDst.getSelectionModel().getSelectedItems())
                session.getUser().getSettings().saveProfileSettings(PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile(), controller.getSettings());
            close();
        }
    }
}
