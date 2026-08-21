package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.misc.DragNDrop;
import jrm.fx.ui.progress.ProgressTask;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.scan.Dir2Dat;
import jrm.profile.scan.DirScan;
import jrm.profile.scan.DirScan.Options;
import jrm.security.Session;

/**
 * FXML controller for the Dir2Dat panel.
 * <p>
 * Scans a source directory and generates a DAT file containing ROM information. Supports multiple output formats (MAME, Logiqx,
 * Software List), hash computation (MD5, SHA1), and various scanning options (subfolders, deep scan, junk folders).
 *
 * @since 2.5
 */
public class Dir2DatController extends BaseController {
    /** FXML UI component for the include empty directories checkbox. */
    @FXML
    CheckBox includeEmptyDirs;
    /** FXML UI component for the match current profile checkbox. */
    @FXML
    CheckBox matchCurrentProfile;
    /** FXML UI component for the do not scan archives checkbox. */
    @FXML
    CheckBox doNotScan;
    /** FXML UI component for the junk subfolders checkbox. */
    @FXML
    CheckBox junkSubfolders;
    /** FXML UI component for the add SHA1 checkbox. */
    @FXML
    CheckBox addShamd;
    /** FXML UI component for the add MD5 checkbox. */
    @FXML
    CheckBox addMd;
    /** FXML UI component for the deep scan checkbox. */
    @FXML
    CheckBox deepScanFor;
    /** FXML UI component for the scan subfolders checkbox. */
    @FXML
    CheckBox scanSubfolders;
    /** FXML UI component for the name metadata text field. */
    @FXML
    TextField name;
    /** FXML UI component for the description metadata text field. */
    @FXML
    TextField description;
    /** FXML UI component for the version metadata text field. */
    @FXML
    TextField version;
    /** FXML UI component for the author metadata text field. */
    @FXML
    TextField author;
    /** FXML UI component for the comment metadata text field. */
    @FXML
    TextField comment;
    /** FXML UI component for the category metadata text field. */
    @FXML
    TextField category;
    /** FXML UI component for the date metadata text field. */
    @FXML
    TextField date;
    /** FXML UI component for the email metadata text field. */
    @FXML
    TextField email;
    /** FXML UI component for the homepage metadata text field. */
    @FXML
    TextField homepage;
    /** FXML UI component for the URL metadata text field. */
    @FXML
    TextField url;
    /** FXML UI component for the source directory text field. */
    @FXML
    TextField srcDir;
    /** FXML UI component for the destination DAT file text field. */
    @FXML
    TextField dstDat;
    /** FXML UI component for the source directory browse button. */
    @FXML
    Button srcDirBtn;
    /** FXML UI component for the destination DAT file browse button. */
    @FXML
    Button dstDatBtn;
    /** FXML UI component for the generate button. */
    @FXML
    Button generate;
    /** FXML UI component for the format toggle group. */
    @FXML
    ToggleGroup format;
    /** FXML UI component for the MAME format radio button. */
    @FXML
    RadioButton formatMame;
    /** FXML UI component for the Logiqx DAT format radio button. */
    @FXML
    RadioButton formatLogiqxDat;
    /** FXML UI component for the Software List format radio button. */
    @FXML
    RadioButton formatSWList;

    /**
     * Initializes the Dir2Dat panel, setting up UI components and loading user preferences.
     *
     * @param location  the location used to resolve relative paths for the root object, or null if unknown
     * @param resources the resources used to localize the root object, or null if not localized
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scanSubfolders.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_scan_subfolders, Boolean.class));
        scanSubfolders.selectedProperty().addListener((_, _, newValue) -> applyScanSubfoldersSetting(newValue));
        deepScanFor.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_deep_scan, Boolean.class)); // $NON-NLS-1$
        deepScanFor.selectedProperty().addListener((_, _, newValue) -> applyDeepScanSetting(newValue)); // $NON-NLS-1$
        addMd.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_md5, Boolean.class)); // $NON-NLS-1$
        addMd.selectedProperty().addListener((_, _, newValue) -> applyMd5Setting(newValue)); // $NON-NLS-1$
        addShamd.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_sha1, Boolean.class)); // $NON-NLS-1$
        addShamd.selectedProperty().addListener((_, _, newValue) -> applySha1Setting(newValue)); // $NON-NLS-1$
        junkSubfolders.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_junk_folders, Boolean.class)); // $NON-NLS-1$
        junkSubfolders.selectedProperty().addListener((_, _, newValue) -> applyJunkFoldersSetting(newValue)); // $NON-NLS-1$
        doNotScan.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_do_not_scan_archives, Boolean.class)); // $NON-NLS-1$
        doNotScan.selectedProperty().addListener((_, _, newValue) -> applyDoNotScanArchivesSetting(newValue)); // $NON-NLS-1$
        matchCurrentProfile.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_match_profile, Boolean.class)); // $NON-NLS-1$
        matchCurrentProfile.selectedProperty().addListener((_, _, newValue) -> applyMatchProfileSetting(newValue)); // $NON-NLS-1$
        includeEmptyDirs.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_include_empty_dirs, Boolean.class)); // $NON-NLS-1$
        includeEmptyDirs.selectedProperty().addListener((_, _, newValue) -> applyEmptyDirSetting(newValue)); // $NON-NLS-1$
        new DragNDrop(srcDir).addDir(this::saveSourceDirectory);
        srcDir.setText(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_src_dir));
        ImageView srciv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/disk.png"));
        srciv.setPreserveRatio(true);
        srciv.getStyleClass().add("icon");
        srcDirBtn.setGraphic(srciv);
        srcDirBtn.setOnAction(_ -> chooseSourceDirectory());
        new DragNDrop(dstDat).addNewFile(this::storeDestinationFile);
        dstDat.setText(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_dst_file));
        ImageView dstiv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/disk.png"));
        dstiv.setPreserveRatio(true);
        dstiv.getStyleClass().add("icon");
        dstDatBtn.setGraphic(dstiv);
        dstDatBtn.setOnAction(_ -> saveDataFile());
        format.selectToggle(switch (ExportType.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format))) {
            case MAME -> formatMame;
            case DATAFILE -> formatLogiqxDat;
            case SOFTWARELIST -> formatSWList;
            default -> formatMame;
        });
        format.selectedToggleProperty().addListener((_, _, newValue) -> setFormatPreference(newValue));
        generate.setOnAction(_ -> dir2dat());
    }

    /**
     * Sets the export format preference based on the selected toggle.
     *
     * @param newValue the newly selected toggle
     */
    private void setFormatPreference(Toggle newValue) {
        if (newValue == formatMame)
            session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_format, ExportType.MAME.toString());
        else if (newValue == formatLogiqxDat)
            session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_format, ExportType.DATAFILE.toString());
        else if (newValue == formatSWList)
            session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_format, ExportType.SOFTWARELIST.toString());
    }

    /**
     * Opens a file chooser dialog to select the destination DAT file and saves the selected path in user settings.
     */
    private void saveDataFile() {
        final var workdir = session.getUser().getSettings().getWorkPath(); // $NON-NLS-1$
        final var lastdstdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_lastdstdir)).map(File::new).filter(File::exists)
                .orElse(workdir.toFile());
        chooseSaveFile(dstDatBtn, dstDat.getText(), lastdstdir, Collections.singletonList(new FileChooser.ExtensionFilter("Dat file", "*.xml", "*.dat")), this::storeFilePathDetails);
    }

    /**
     * Stores the selected destination file path in user settings and updates the corresponding text field.
     *
     * @param path the selected file path
     */
    private void storeFilePathDetails(Path path) {
        session.getUser().getSettings().setProperty("MainFrame.ChooseDatDst", path.toString()); //$NON-NLS-1$
        dstDat.setText(path.toString());
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_dst_file, dstDat.getText()); // $NON-NLS-1$
        session.getUser().getSettings().setProperty(SettingsEnum.dir2dat_lastdstdir, Optional.ofNullable(path).map(Path::toFile).map(File::getParent).orElse(null));
    }

    /**
     * Stores the destination file path in user settings.
     *
     * @param txt the destination file path as a string
     */
    private void storeDestinationFile(String txt) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_dst_file, txt);
    }

    /**
     * Opens a directory chooser dialog to select the source directory and updates the corresponding text field and user settings.
     */
    private void chooseSourceDirectory() {
        final var workdir = session.getUser().getSettings().getWorkPath(); // $NON-NLS-1$
        final var lastsrcdir = Optional.ofNullable(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_lastsrcdir)).map(File::new).filter(File::exists)
                .orElse(workdir.toFile());
        chooseDir(srcDirBtn, srcDir.getText(), lastsrcdir, this::updateSourceDirectory);
    }

    /**
     * Updates the source directory path in user settings and the corresponding text field.
     *
     * @param path the selected source directory path
     */
    private void updateSourceDirectory(Path path) {
        session.getUser().getSettings().setProperty("MainFrame.ChooseDatSrc", path.toString()); //$NON-NLS-1$
        srcDir.setText(path.toString());
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_src_dir, srcDir.getText()); // $NON-NLS-1$
        session.getUser().getSettings().setProperty(SettingsEnum.dir2dat_lastsrcdir, Optional.ofNullable(path).map(Path::toFile).map(File::getParent).orElse(null));
    }

    /**
     * Saves the source directory path in user settings.
     *
     * @param txt the source directory path as a string
     */
    private void saveSourceDirectory(String txt) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_src_dir, txt);
    }

    /**
     * Applies the empty directory setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyEmptyDirSetting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_include_empty_dirs, newValue);
    }

    /**
     * Applies the match profile setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyMatchProfileSetting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_match_profile, newValue);
    }

    /**
     * Applies the "do not scan archives" setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyDoNotScanArchivesSetting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_do_not_scan_archives, newValue);
    }

    /**
     * Applies the junk folders setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyJunkFoldersSetting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_junk_folders, newValue);
    }

    /**
     * Applies the SHA1 setting.
     *
     * @param newValue the new value for the setting
     */
    private void applySha1Setting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_add_sha1, newValue);
    }

    /**
     * Applies the MD5 setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyMd5Setting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_add_md5, newValue);
    }

    /**
     * Applies the deep scan setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyDeepScanSetting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_deep_scan, newValue);
    }

    /**
     * Applies the scan subfolders setting.
     *
     * @param newValue the new value for the setting
     */
    private void applyScanSubfoldersSetting(Boolean newValue) {
        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_scan_subfolders, newValue);
    }

    /**
     * Starts the Dir2Dat conversion on a virtual thread.
     * <p>
     * Text field values and DAT headers are captured on the JavaFX thread before the background task is started, because JavaFX
     * controls are not thread-safe.
     * </p>
     */
    private void dir2dat() {
        try {
            final String src = srcDir.getText();
            final String dst = dstDat.getText();
            final var headers = initHeaders();
            Thread.startVirtualThread(dir2DatTask(src, dst, headers));
        } catch (IOException | URISyntaxException e) {
            Log.err(e.getMessage(), e);
            Dialogs.showError(e);
        }
    }

    /**
     * Creates the Dir2Dat background task.
     *
     * @param src the captured source directory path from the FX text field
     * @param dst the captured destination DAT file path from the FX text field
     * @param headers the captured DAT metadata headers from the FX text fields
     * @return the progress task
     * @throws IOException if an I/O error occurs
     * @throws URISyntaxException if the FXML resource URI is invalid
     */
    private ProgressTask<Void> dir2DatTask(final String src, final String dst, final HashMap<String, String> headers) throws IOException, URISyntaxException {
        return new ProgressTask<Void>((Stage) dstDat.getScene().getWindow()) {
            @Override
            protected Void call() throws Exception {
                executeDir2Dat(src, dst, headers, this);
                return null;
            }

            @Override
            public void succeeded() {
                close();
            }

            @Override
            protected void failed() {
                handleDir2DatFailure(this);
            }
        };
    }

    private void executeDir2Dat(final String src, final String dst, final HashMap<String, String> headers, final ProgressTask<Void> task) throws IOException {
        if (src == null || src.isEmpty() || dst == null || dst.isEmpty())
            return;
        final File srcdir = new File(src);
        if (!srcdir.isDirectory())
            return;
        final File dstdat = new File(dst);
        final File dstdir = dstdat.getParentFile();
        if ((dstdir == null || dstdir.isDirectory()) && (dstdat.exists() || dstdat.createNewFile())) {
            final var options = initOptions(session);
            final var type = ExportType.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format)); // $NON-NLS-1$
            new Dir2Dat(session, srcdir, dstdat, task, options, type, headers);
        }
    }

    private static void handleDir2DatFailure(final ProgressTask<Void> task) {
        if (task.getException() instanceof BreakException)
            Dialogs.showAlert("Cancelled");
        else {
            task.close();
            Optional.ofNullable(task.getException().getCause()).ifPresentOrElse(cause -> {
                Log.err(cause.getMessage(), cause);
                Dialogs.showError(cause);
            }, () -> {
                Log.err(task.getException().getMessage(), task.getException());
                Dialogs.showError(task.getException());
            });
        }
    }

    /**
     * Initializes the directory scan options from settings.
     *
     * @param session the security session
     * @return the scan options
     */
    private EnumSet<DirScan.Options> initOptions(final Session session) {
        EnumSet<DirScan.Options> options = EnumSet.of(Options.USE_PARALLELISM, Options.MD5_DISKS, Options.SHA1_DISKS);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_scan_subfolders, Boolean.class))) // $NON-NLS-1$
            options.add(Options.RECURSE);
        if (Boolean.FALSE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_deep_scan, Boolean.class))) // $NON-NLS-1$
            options.add(Options.IS_DEST);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_md5, Boolean.class))) // $NON-NLS-1$
            options.add(Options.NEED_MD5);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_sha1, Boolean.class))) // $NON-NLS-1$
            options.add(Options.NEED_SHA1);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_junk_folders, Boolean.class))) // $NON-NLS-1$
            options.add(Options.JUNK_SUBFOLDERS);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_do_not_scan_archives, Boolean.class))) // $NON-NLS-1$
            options.add(Options.ARCHIVES_AND_CHD_AS_ROMS);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_match_profile, Boolean.class))) // $NON-NLS-1$
            options.add(Options.MATCH_PROFILE);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_include_empty_dirs, Boolean.class))) // $NON-NLS-1$
            options.add(Options.EMPTY_DIRS);
        return options;
    }

    /**
     * Initializes the DAT file headers from the form fields.
     *
     * @return the header map
     */
    private HashMap<String, String> initHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("name", name.getText()); //$NON-NLS-1$
        headers.put("description", description.getText()); //$NON-NLS-1$
        headers.put("version", version.getText()); //$NON-NLS-1$
        headers.put("author", author.getText()); //$NON-NLS-1$
        headers.put("comment", comment.getText()); //$NON-NLS-1$
        headers.put("category", category.getText()); //$NON-NLS-1$
        headers.put("date", date.getText()); //$NON-NLS-1$
        headers.put("email", email.getText()); //$NON-NLS-1$
        headers.put("homepage", homepage.getText()); //$NON-NLS-1$
        headers.put("url", url.getText()); //$NON-NLS-1$
        return headers;
    }
}
