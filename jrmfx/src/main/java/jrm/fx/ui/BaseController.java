package jrm.fx.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import javafx.fxml.Initializable;
import javafx.scene.control.Control;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.security.Sessions;

/**
 * Abstract base class for FXML controllers providing common file and directory chooser helpers.
 * <p>
 * Subclasses gain access to a shared {@link Session} and convenience methods for opening
 * file/directory dialogs with initial values and extension filters.
 *
 * @since 2.5
 */
abstract class BaseController implements Initializable {
    /** The current user session. */
    protected final Session session = Sessions.getSingleSession();

    /**
     * Callback for single path selection.
     */
    interface Callback {
        /**
         * Invoked when a path is selected.
         *
         * @param path the selected path
         */
        void call(Path path);
    }

    /**
     * Callback for multiple path selection.
     */
    interface CallbackMulti {
        /**
         * Invoked when multiple paths are selected.
         *
         * @param paths the selected paths
         */
        void call(List<Path> paths);
    }

    /**
     * Opens a directory chooser dialog and invokes the callback with the selected path.
     *
     * @param parent       the parent control for dialog positioning
     * @param initialValue the initial directory path, or {@code null}
     * @param defdir       the default directory, or {@code null}
     * @param cb           the callback receiving the selected path
     */
    protected void chooseDir(Control parent, String initialValue, File defdir, Callback cb) {
        final var chooser = initDirectoryChooser(initialValue, defdir);
        final var chosen = chooser.showDialog(parent.getScene().getWindow());
        if (chosen != null)
            cb.call(PathAbstractor.getRelativePath(session, chosen.toPath()));
    }

    /**
     * Opens a file chooser dialog for selecting a single file and invokes the callback.
     *
     * @param parent       the parent control for dialog positioning
     * @param initialValue the initial file path, or {@code null}
     * @param defdir       the default directory, or {@code null}
     * @param filters      the extension filters, or {@code null}
     * @param cb           the callback receiving the selected path
     */
    protected void chooseOpenFile(Control parent, String initialValue, File defdir, Collection<ExtensionFilter> filters, Callback cb) {
        final var chooser = initFileChooser(initialValue, defdir);
        if (filters != null)
            chooser.getExtensionFilters().addAll(filters);
        final var chosen = chooser.showOpenDialog(parent.getScene().getWindow());
        if (chosen != null)
            cb.call(PathAbstractor.getRelativePath(session, chosen.toPath()));
    }

    /**
     * Opens a file chooser dialog for selecting multiple files and invokes the callback.
     *
     * @param parent       the parent control for dialog positioning
     * @param initialValue the initial file path, or {@code null}
     * @param defdir       the default directory, or {@code null}
     * @param filters      the extension filters, or {@code null}
     * @param cb           the callback receiving the selected paths
     */
    protected void chooseOpenFileMulti(Control parent, String initialValue, File defdir, Collection<ExtensionFilter> filters, CallbackMulti cb) {
        final var chooser = initFileChooser(initialValue, defdir);
        if (filters != null)
            chooser.getExtensionFilters().addAll(filters);
        final var chosen = chooser.showOpenMultipleDialog(parent.getScene().getWindow());
        if (chosen != null)
            cb.call(chosen.stream().map(f -> PathAbstractor.getRelativePath(session, f.toPath())).toList());
    }

    /**
     * Opens a file chooser dialog for saving a file and invokes the callback.
     *
     * @param parent       the parent control for dialog positioning
     * @param initialValue the initial file path, or {@code null}
     * @param defdir       the default directory, or {@code null}
     * @param filters      the extension filters, or {@code null}
     * @param cb           the callback receiving the selected path
     */
    protected void chooseSaveFile(Control parent, String initialValue, File defdir, Collection<ExtensionFilter> filters, Callback cb) {
        final var chooser = initFileChooser(initialValue, defdir);
        if (filters != null)
            chooser.getExtensionFilters().addAll(filters);
        final var chosen = chooser.showSaveDialog(parent.getScene().getWindow());
        if (chosen != null)
            cb.call(PathAbstractor.getRelativePath(session, chosen.toPath()));
    }

    /**
     * Initializes a {@link FileChooser} with the given initial value and default directory.
     *
     * @param initialValue the initial file path, or {@code null}
     * @param defdir       the default directory, or {@code null}
     * @return a configured file chooser
     */
    private FileChooser initFileChooser(String initialValue, File defdir) // NOSONAR
    {
        final var chooser = new FileChooser();
        var initialized = false;
        if (initialValue != null && !initialValue.isBlank()) {
            final var initial = PathAbstractor.getAbsolutePath(session, initialValue);
            if (initial != null) {
                if (Files.exists(initial) && Files.isDirectory(initial)) {
                    chooser.setInitialDirectory(initial.toFile());
                    initialized = true;
                } else {
                    final var parent = initial.getParent();
                    if (parent != null && Files.exists(parent) && Files.isDirectory(parent)) {
                        chooser.setInitialDirectory(parent.toFile());
                        initialized = true;
                    }
                    final var fileName = initial.getFileName();
                    if (fileName != null)
                        chooser.setInitialFileName(fileName.toString());
                }
            }
        }
        if (!initialized && defdir != null && defdir.exists())
            chooser.setInitialDirectory(defdir);
        return chooser;
    }

    /**
     * Initializes a {@link DirectoryChooser} with the given initial value and default directory.
     *
     * @param initialValue the initial directory path, or {@code null}
     * @param defdir       the default directory, or {@code null}
     * @return a configured directory chooser
     */
    private DirectoryChooser initDirectoryChooser(String initialValue, File defdir) {
        final var chooser = new DirectoryChooser();
        if (initialValue != null && !initialValue.isBlank()) {
            final var initial = PathAbstractor.getAbsolutePath(session, initialValue);
            if (initial != null && (Files.exists(initial) && Files.isDirectory(initial)))
                chooser.setInitialDirectory(initial.toFile());
            else if (defdir != null && defdir.exists())
                chooser.setInitialDirectory(defdir);
        } else if (defdir != null && defdir.exists())
            chooser.setInitialDirectory(defdir);
        return chooser;
    }

}
