package jrm.fx.ui.misc;

import java.nio.file.Path;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * A result holder for file operations.
 * <p>
 * Tracks the file path and the result message from processing operations.
 *
 * @since 2.5
 */
public class FileResult {
    /** The file path. */
    private final ObjectProperty<Path> file;
    /** The result message. */
    private final StringProperty result;

    /**
     * Constructs a file result.
     *
     * @param file the file path
     */
    public FileResult(Path file) {
        this.file = new SimpleObjectProperty<>(file);
        this.result = new SimpleStringProperty();
    }

    /**
     * Returns the file property.
     *
     * @return the file property
     */
    public final ObjectProperty<Path> fileProperty() {
        return this.file;
    }

    /**
     * Returns the file path.
     *
     * @return the file path
     */
    public final Path getFile() {
        return this.fileProperty().get();
    }

    /**
     * Sets the file path.
     *
     * @param file the file path
     */
    public final void setFile(final Path file) {
        this.fileProperty().set(file);
    }

    /**
     * Returns the result property.
     *
     * @return the result property
     */
    public final StringProperty resultProperty() {
        return this.result;
    }

    /**
     * Returns the result message.
     *
     * @return the result message
     */
    public final String getResult() {
        return this.resultProperty().get();
    }

    /**
     * Sets the result message.
     *
     * @param result the result message
     */
    public final void setResult(final String result) {
        this.resultProperty().set(result);
    }

}