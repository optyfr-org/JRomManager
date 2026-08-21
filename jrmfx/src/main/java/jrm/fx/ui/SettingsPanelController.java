package jrm.fx.ui;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;
import jrm.compressors.SevenZipOptions;
import jrm.compressors.ZipLevel;
import jrm.compressors.ZipTempThreshold;
import jrm.fx.ui.JRMScene.StyleSheet;
import jrm.fx.ui.misc.DragNDrop;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * FXML controller for the settings panel.
 * <p>
 * Manages application-wide settings including threading, style sheet, backup destination, compressor options (ZIP level, 7z
 * options), and debug log level.
 *
 * @since 2.5
 */
public class SettingsPanelController extends BaseController {
    /** The general settings pane. */
    @FXML
    TitledPane paneGeneral;
    /** The threading choice box. */
    @FXML
    ChoiceBox<ThreadCnt> cbThreading;
    /** The style sheet choice box. */
    @FXML
    ChoiceBox<JRMScene.StyleSheet> cbStyleSheet;
    /** The backup destination enabled checkbox. */
    @FXML
    CheckBox ckbBackupDst;
    /** The backup destination text field. */
    @FXML
    TextField tfBackupDst;
    /** The backup destination browse button. */
    @FXML
    Button btBackupDst;

    /** The compressors settings pane. */
    @FXML
    TitledPane paneCompressors;
    /** The ZIP temp threshold choice box. */
    @FXML
    ChoiceBox<ZipTempThreshold> cbZipTempThreshold;
    /** The ZIP compression level choice box. */
    @FXML
    ChoiceBox<ZipLevel> cbZipLevel;
    /** The 7z options choice box. */
    @FXML
    ChoiceBox<SevenZipOptions> cb7zArgs;
    /** The 7z thread count spinner. */
    @FXML
    Spinner<Integer> tf7zThreads;
    /** The 7z solid archive checkbox. */
    @FXML
    CheckBox ckb7ZSolid;

    /** The debug settings pane. */
    @FXML
    TitledPane paneDebug;
    /** The debug level choice box. */
    @FXML
    ChoiceBox<Level> cbDbgLevel;
    /** The garbage collection button. */
    @FXML
    Button gc;
    /** The status text field. */
    @FXML
    TextField status;

    /** Valid debug log levels selectable in the UI. */
    private static final Level[] levels = new Level[] { Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST, Level.ALL };

    /** The scheduler for periodic memory monitoring. */
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Initializes the settings panel, setting up general settings, compressors, and debug sections.
     *
     * @param location  the location used to resolve relative paths for the root object, or {@code null}
     * @param resources the resources used to localize the root object, or {@code null}
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initGeneral();
        initCompressors();
        initDebug();
    }

    /**
     * Initializes the general settings section.
     */
    private void initGeneral() {
        ImageView generaliv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/cog.png"));
        generaliv.setPreserveRatio(true);
        generaliv.getStyleClass().add("icon");
        paneGeneral.setGraphic(generaliv);
        cbThreading.getItems().setAll(ThreadCnt.build());
        cbThreading.getSelectionModel().select(new ThreadCnt(session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class)));
        cbThreading.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> configureThreadCount(newValue));
        ckbBackupDst.selectedProperty().addListener((_, _, newValue) -> configureBackupDestinationAvailability(newValue));
        ckbBackupDst.setSelected(session.getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class));
        ImageView backupdstiv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/disk.png"));
        backupdstiv.setPreserveRatio(true);
        backupdstiv.getStyleClass().add("icon");
        btBackupDst.setGraphic(backupdstiv);
        tfBackupDst.setText(session.getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir)); // $NON-NLS-1$
        new DragNDrop(btBackupDst).addDir(this::configureBackupDirectory);
        btBackupDst.setOnAction(_ -> chooseDir(btBackupDst, tfBackupDst.getText(), null, this::configureBackupPath));
        cbStyleSheet.getItems().setAll(JRMScene.StyleSheet.values());
        cbStyleSheet.getSelectionModel().select(session.getUser().getSettings().getEnumProperty(JRMScene.ScenePrefs.style_sheet, JRMScene.StyleSheet.class));
        cbStyleSheet.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> changeStyleSheet(newValue));
    }

    /**
     * Sets the backup destination path and updates the text field.
     *
     * @param path the chosen directory path
     */
    private void configureBackupPath(Path path) {
        session.getUser().getSettings().setProperty(ProfileSettingsEnum.backup_dest_dir, path.toString());
        tfBackupDst.setText(path.toString());
    }

    /**
     * Sets the backup directory from a drag-and-drop path.
     *
     * @param txt the directory path from drag-and-drop
     */
    private void configureBackupDirectory(String txt) {
        session.getUser().getSettings().setProperty(ProfileSettingsEnum.backup_dest_dir, txt);
    }

    /**
     * Enables or disables the backup destination controls.
     *
     * @param newValue whether backup destination is enabled
     */
    private void configureBackupDestinationAvailability(Boolean newValue) {
        tfBackupDst.setDisable(!newValue);
        btBackupDst.setDisable(!newValue);
        session.getUser().getSettings().setProperty(ProfileSettingsEnum.backup_dest_dir_enabled, newValue); // $NON-NLS-1$
    }

    /**
     * Configures the thread count setting.
     *
     * @param newValue the new thread count
     */
    private void configureThreadCount(ThreadCnt newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.thread_count, newValue.getCnt());
    }

    /**
     * Changes the application style sheet.
     *
     * @param newValue the new style sheet
     */
    private void changeStyleSheet(StyleSheet newValue) {
        session.getUser().getSettings().setEnumProperty(JRMScene.ScenePrefs.style_sheet, newValue);
        MainFrame.applyCSS();
    }

    /**
     * Initializes the compressor settings section.
     */
    private void initCompressors() {
        ImageView compressoriv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/compress.png"));
        compressoriv.setPreserveRatio(true);
        compressoriv.getStyleClass().add("icon");
        paneCompressors.setGraphic(compressoriv);
        cbZipTempThreshold.getItems().setAll(ZipTempThreshold.values());
        cbZipTempThreshold.setConverter(zipTempThresholdStringConverter());
        cbZipTempThreshold.getSelectionModel().select(session.getUser().getSettings().getEnumProperty(SettingsEnum.zip_temp_threshold, ZipTempThreshold.class));
        cbZipTempThreshold.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> configureZipTempThreshold(newValue));
        cbZipLevel.getItems().setAll(ZipLevel.values());
        cbZipLevel.setConverter(zipLevelStringConverter());
        cbZipLevel.getSelectionModel().select(session.getUser().getSettings().getEnumProperty(SettingsEnum.zip_compression_level, ZipLevel.class));
        cbZipLevel.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> configureZipCompressionLevel(newValue));

        cb7zArgs.getItems().setAll(SevenZipOptions.values());
        cb7zArgs.setConverter(sevenZipOptionsStringConverter());
        cb7zArgs.getSelectionModel().select(session.getUser().getSettings().getEnumProperty(SettingsEnum.sevenzip_level, SevenZipOptions.class));
        tf7zThreads.setValueFactory(get7zThreadsSpinner());
        tf7zThreads.getValueFactory().setValue(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_threads, Integer.class));
        tf7zThreads.valueProperty().addListener((_, _, newValue) -> configureSevenZipThreads(newValue));
        ckb7ZSolid.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_solid, Boolean.class));
        ckb7ZSolid.selectedProperty().addListener((_, _, newValue) -> configureSevenZipSolidOption(newValue));
    }

    /**
     * Applies the 7z solid archive setting.
     *
     * @param newValue whether solid archives are enabled
     */
    private void configureSevenZipSolidOption(Boolean newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_solid, newValue);
    }

    /**
     * Applies the 7z thread count setting.
     *
     * @param newValue the new thread count
     */
    private void configureSevenZipThreads(Integer newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_threads, newValue);
    }

    /**
     * Creates a spinner value factory for 7z thread count with custom increment and
     * decrement behavior that allows a minimum value of -1.
     *
     * @return a configured {@link SpinnerValueFactory} for thread counts
     */
    private SpinnerValueFactory<Integer> get7zThreadsSpinner() {
        return new SpinnerValueFactory<Integer>() {
            @Override
            public void decrement(int steps) {
                setValue(Math.max(-1, getValue() - steps));
            }

            @Override
            public void increment(int steps) {
                setValue(getValue() + steps);
            }
        };
    }

    /**
     * Creates a string converter that shows the display name of
     * {@link SevenZipOptions} values.
     *
     * @return a string converter for 7z options
     */
    private StringConverter<SevenZipOptions> sevenZipOptionsStringConverter() {
        return new StringConverter<>() {

            @Override
            public String toString(SevenZipOptions object) {
                return object.getName();
            }

            @Override
            public SevenZipOptions fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Applies the ZIP compression level setting.
     *
     * @param newValue the new compression level
     */
    private void configureZipCompressionLevel(ZipLevel newValue) {
        session.getUser().getSettings().setEnumProperty(SettingsEnum.zip_compression_level, newValue);
    }

    /**
     * Creates a string converter that shows the display name of
     * {@link ZipLevel} values.
     *
     * @return a string converter for ZIP levels
     */
    private StringConverter<ZipLevel> zipLevelStringConverter() {
        return new StringConverter<>() {

            @Override
            public String toString(ZipLevel object) {
                return object.getName();
            }

            @Override
            public ZipLevel fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Applies the ZIP temporary threshold setting.
     *
     * @param newValue the new threshold
     */
    private void configureZipTempThreshold(ZipTempThreshold newValue) {
        session.getUser().getSettings().setEnumProperty(SettingsEnum.zip_temp_threshold, newValue);
    }

    /**
     * Creates a string converter that displays the description of
     * {@link ZipTempThreshold} values.
     *
     * @return a string converter for ZIP temp thresholds
     */
    private StringConverter<ZipTempThreshold> zipTempThresholdStringConverter() {
        return new StringConverter<>() {

            @Override
            public String toString(ZipTempThreshold object) {
                return object.getDesc();
            }

            @Override
            public ZipTempThreshold fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Initializes the debug pane with log level selector and memory monitor.
     */
    private void initDebug() {
        ImageView debugiv = new ImageView(MainFrame.getIcon("/jrm/resicons/icons/bug.png"));
        debugiv.setPreserveRatio(true);
        debugiv.getStyleClass().add("icon");
        paneDebug.setGraphic(debugiv);
        cbDbgLevel.getItems().setAll(levels);
        cbDbgLevel.getSelectionModel().select(Level.parse(session.getUser().getSettings().getProperty(SettingsEnum.debug_level)));
        cbDbgLevel.getSelectionModel().selectedItemProperty().addListener((ChangeListener<Level>) (_, _, newValue) -> changeDebugLevel(newValue));
        gc.setOnAction(_ -> performGarbageCollection());
        scheduler.scheduleAtFixedRate(this::updateMemory, 0, 20, TimeUnit.SECONDS);
    }

    /**
     * Triggers garbage collection and updates the memory status display.
     */
    private void performGarbageCollection() {
        System.gc(); // NOSONAR
        updateMemory();
    }

    /**
     * Changes the debug log level and updates the application logger.
     *
     * @param newValue the new log level
     */
    private void changeDebugLevel(Level newValue) {
        session.getUser().getSettings().setProperty(SettingsEnum.debug_level, newValue.toString());
        Log.setLevel(newValue);
    }

    /** Memory format pattern for MiB display. */
    private static final String XX_MIB = "%.2f MiB";

    /**
     * Updates the memory usage status display with total, used, free, and maximum JVM heap metrics.
     */
    void updateMemory() {
        final Runtime rt = Runtime.getRuntime();
        status.setText(String.format(Messages.getString("MainFrame.MemoryUsage"), String.format(XX_MIB, rt.totalMemory() / 1048576.0), //$NON-NLS-1$
                String.format(XX_MIB, (rt.totalMemory() - rt.freeMemory()) / 1048576.0), String.format(XX_MIB, rt.freeMemory() / 1048576.0),
                String.format(XX_MIB, rt.maxMemory() / 1048576.0))); // $NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    /**
     * Represents a thread count option selectable in the threading choice box.
     */
    @RequiredArgsConstructor
    private static class ThreadCnt {
        /**
         * The thread count.
         *
         * @return the thread count
         */
        final @Getter int cnt;
        /**
         * The thread name.
         *
         * @return the thread name
         */
        final @Getter String name;

        /**
         * Constructs a thread count option with a numeric value and no display name.
         *
         * @param cnt the thread count
         */
        public ThreadCnt(int cnt) {
            this.cnt = cnt;
            this.name = null;
        }

        /**
         * @return the display name if set, otherwise the numeric count
         */
        @Override
        public String toString() {
            return name != null ? name : Integer.toString(cnt);
        }

        /**
         * @return a hash code derived from the thread count, consistent with {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Integer.hashCode(cnt);
        }

        /**
         * @param obj the object to compare
         * @return {@code true} if the other object is a {@code ThreadCnt} with the same thread count
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (obj instanceof ThreadCnt tc)
                return this.cnt == tc.cnt;
            return super.equals(obj);
        }

        /**
         * Builds the available thread count options including "Adaptive", "Max available", and numeric counts
         * from 1 to available processors.
         *
         * @return the array of thread count options
         */
        static ThreadCnt[] build() {
            ArrayList<ThreadCnt> list = new ArrayList<>();
            list.add(new ThreadCnt(-1, "Adaptive"));
            list.add(new ThreadCnt(0, "Max available"));
            for (var i = 1; i <= Runtime.getRuntime().availableProcessors(); i++)
                list.add(new ThreadCnt(i));
            return list.toArray(ThreadCnt[]::new);
        }
    }
}
