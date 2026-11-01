package jrm.fx.ui.misc;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

import javafx.scene.control.Cell;
import javafx.scene.control.Control;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import jrm.security.PathAbstractor;
import jrm.security.Sessions;

/**
 * Utility class for installing drag-and-drop handlers on JavaFX controls.
 * <p>
 * Provides methods to accept files, directories, or any dragged content with
 * visual feedback (green for accept, red for reject). Supports both single-file
 * and multi-file drop operations.
 *
 * @since 2.5
 */
public class DragNDrop {
    /** The CSS style for accepting drops. */
    private final String styleAccept;
    /** The CSS style for rejecting drops. */
    private final String styleReject;

    /** The control to install handlers on. */
    private final Control control;

    /**
     * Constructs a drag-and-drop handler.
     *
     * @param control the control to install handlers on
     */
    public DragNDrop(Control control) {
        if (control instanceof Cell) {
            styleAccept = "-fx-background-color: #DDFFDD;";
            styleReject = "-fx-background-color: #FFDDDD;";
        } else {
            styleAccept = "-fx-control-inner-background: #DDFFDD;";
            styleReject = "-fx-control-inner-background: #FFDDDD;";
        }
        this.control = control;
    }

    /**
     * Callback interface for single-file drop operations.
     */
    @FunctionalInterface
    public interface SetCallBack {
        /**
         * Invoked when a file is dropped.
         *
         * @param txt the file path as a string
         */
        public void call(String txt);
    }

    /**
     * Callback interface for multi-file drop operations.
     */
    public interface SetFilesCallBack {
        /**
         * Invoked when files are dropped.
         *
         * @param files the list of dropped files
         */
        public void call(List<File> files);
    }

    /**
     * Installs handlers that accept a single regular file.
     *
     * @param cb the callback to invoke
     */
    public void addFile(SetCallBack cb) {
        addFiltered(f -> Files.isRegularFile(f.toPath()), cb);
    }

    /**
     * Installs handlers that accept a new or existing regular file.
     *
     * @param cb the callback to invoke
     */
    public void addNewFile(SetCallBack cb) {
        addFiltered(f -> (!Files.exists(f.toPath())) || Files.isRegularFile(f.toPath()), cb);
    }

    /**
     * Installs handlers that accept any dragged files.
     *
     * @param cb the callback to invoke
     */
    public void addAny(SetFilesCallBack cb) {
        addFiltered(_ -> true, cb);
    }

    /**
     * Installs handlers that accept a single directory.
     *
     * @param cb the callback to invoke
     */
    public void addDir(SetCallBack cb) {
        addFiltered(f -> Files.isDirectory(f.toPath()), cb);
    }

    /**
     * Installs handlers that accept multiple directories.
     *
     * @param cb the callback to invoke
     */
    public void addDirs(SetFilesCallBack cb) {
        addFiltered(f -> Files.isDirectory(f.toPath()), cb);
    }

    /**
     * Installs drag-and-drop handlers that accept every dragged file satisfying {@code filter}
     * and forward the resulting file list to {@code cb}.
     *
     * @param filter the predicate every dropped file must satisfy
     * @param cb     the callback receiving the accepted files
     */
    public void addFiltered(Predicate<File> filter, SetFilesCallBack cb) {
        control.setOnDragOver(event -> handleDragOverFiles(event, filter));
        control.setOnDragExited(_ -> control.setStyle(""));
        control.setOnDragDropped(event -> handleDragDroppedFiles(event, filter, cb));
    }

    /**
     * Installs drag-and-drop handlers that accept a single dragged file satisfying {@code filter}.
     * When the control is a {@link TextField}, live validation of the typed text is also installed.
     *
     * @param filter the predicate the single dropped file must satisfy
     * @param cb     the callback receiving the accepted file path as a string
     */
    public void addFiltered(Predicate<File> filter, SetCallBack cb) {
        if (control instanceof TextField tf) {
            installTextValidator(tf, filter, cb);
        }
        control.setOnDragOver(event -> handleDragOverSingle(event, filter));
        control.setOnDragExited(_ -> control.setStyle(""));
        control.setOnDragDropped(event -> handleDragDroppedSingle(event, filter, cb));
    }

    /**
     * Highlights the control as an accept target when every dragged file satisfies {@code filter}.
     *
     * @param event  the drag-over event
     * @param filter the predicate every dragged file must satisfy
     */
    void handleDragOverFiles(DragEvent event, Predicate<File> filter) {
        if (event.getGestureSource() != control) {
            final var db = event.getDragboard();
            if (db.hasFiles() && db.getFiles().stream().allMatch(filter)) {
                event.acceptTransferModes(TransferMode.COPY);
                control.setStyle(styleAccept);
                return;
            }
        }
        control.setStyle(styleReject);
        event.consume();
    }

    /**
     * Performs the drop for the multi-file variant, delegating list merging when the control is a
     * {@link ListView}.
     *
     * @param event  the drop event
     * @param filter the predicate every dropped file must satisfy
     * @param cb     the callback receiving the accepted files
     */
    @SuppressWarnings("unchecked")
    void handleDragDroppedFiles(DragEvent event, Predicate<File> filter, SetFilesCallBack cb) {
        final var db = event.getDragboard();
        var success = false;
        if (db.hasFiles() && db.getFiles().stream().allMatch(filter)) {
            if (control instanceof ListView) {
                appendFilesToListView(db.getFiles());
                cb.call(((ListView<File>) control).getItems());
            } else {
                cb.call(db.getFiles());
            }
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Appends each file from {@code files} to the bound {@link ListView}, skipping duplicates while
     * preserving insertion order.
     *
     * @param files the files to append
     */
    @SuppressWarnings("unchecked")
    void appendFilesToListView(List<File> files) {
        final var lv = (ListView<File>) control;
        final var set = new LinkedHashSet<>(lv.getItems());
        for (final var f : files) {
            if (!set.contains(f)) {
                lv.getItems().add(f);
            }
        }
    }

    /**
     * Highlights the control as an accept target when exactly one dragged file satisfies
     * {@code filter}.
     *
     * @param event  the drag-over event
     * @param filter the predicate the single dragged file must satisfy
     */
    void handleDragOverSingle(DragEvent event, Predicate<File> filter) {
        if (event.getGestureSource() != control) {
            final var db = event.getDragboard();
            if (db.hasFiles() && db.getFiles().size() == 1 && filter.test(db.getFiles().get(0))) {
                event.acceptTransferModes(TransferMode.COPY);
                control.setStyle(styleAccept);
                return;
            }
        }
        control.setStyle(styleReject);
        event.consume();
    }

    /**
     * Performs the drop for the single-file variant, updating a {@link TextInputControl} when
     * applicable before invoking {@code cb}.
     *
     * @param event  the drop event
     * @param filter the predicate the single dropped file must satisfy
     * @param cb     the callback receiving the accepted file path as a string
     */
    void handleDragDroppedSingle(DragEvent event, Predicate<File> filter, SetCallBack cb) {
        final var db = event.getDragboard();
        var success = false;
        if (db.hasFiles() && db.getFiles().size() == 1 && filter.test(db.getFiles().get(0))) {
            if (control instanceof TextInputControl tic) {
                tic.setText(db.getFiles().get(0).toString());
            }
            cb.call(db.getFiles().get(0).toString());
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Installs a listener that validates {@code tf}'s text against {@code filter} as the user types.
     *
     * @param tf     the text field to validate
     * @param filter the predicate the resolved path must satisfy
     * @param cb     the callback invoked with valid text
     */
    void installTextValidator(TextField tf, Predicate<File> filter, SetCallBack cb) {
        tf.textProperty().addListener((_, _, newValue) -> validateText(tf, newValue, filter, cb));
    }

    /**
     * Validates {@code value} resolved through {@link PathAbstractor} against {@code filter} and
     * updates the field color accordingly, invoking {@code cb} when valid.
     *
     * @param tf     the originating text field
     * @param value  the current text value
     * @param filter the predicate the resolved path must satisfy
     * @param cb     the callback invoked with valid text
     */
    void validateText(TextField tf, String value, Predicate<File> filter, SetCallBack cb) {
        if (value == null || value.isBlank() || tf.isDisabled()) {
            tf.setStyle(null);
        } else if (filter.test(PathAbstractor.getAbsolutePath(Sessions.getSingleSession(), value).toFile())) {
            tf.setStyle("-fx-text-inner-color: green;");
            cb.call(value);
        } else {
            tf.setStyle("-fx-text-inner-color: red;");
        }
    }
}
