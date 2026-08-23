package jrm.compressors.sevenzipjbinding;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;

import jrm.compressors.Archive;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;

/**
 * Base class for archive implementations utilizing SevenZipJBinding. It manages the underlying input archive and stream, queues of
 * pending file modifications, closeable resources, and a temporary directory.
 */
@Accessors(chain = true)
public abstract class NArchiveBase implements Archive, Closeables {
    /**
     * The underlying SevenZipJBinding input archive instance, if applicable. This is used for reading and modifying the archive
     * contents, and is stored here to be properly closed when done.
     * 
     * @param iInArchive the IInArchive instance representing the opened archive, or null if not applicable
     * 
     * @return the IInArchive instance, or null if not applicable
     */
    private @Getter @Setter IInArchive iInArchive = null;
    /**
     * The underlying SevenZipJBinding input stream instance, if applicable. This is used for reading the archive data, and is
     * stored here to be properly closed when done.
     * 
     * @param iInStream the IInStream instance representing the opened archive stream, or null if not applicable
     * 
     * @return the IInStream instance, or null if not applicable
     */
    private @Getter @Setter IInStream iInStream = null;

    /**
     * A list of file paths to be added to the archive. Each entry represents a file that should be included in the modified
     * archive. This list is populated with paths of files that are intended to be added during the archive modification process.
     * 
     * @return the list of file paths to be added to the archive
     */
    private final @Getter List<String> toAdd = new ArrayList<>();
    /**
     * A set of file paths to be deleted from the archive. Each entry represents a file that should be removed in the modified
     * archive. This set is populated with paths of files that are intended to be deleted during the archive modification process.
     * 
     * @return the set of file paths to be deleted from the archive
     */
    private final @Getter HashSet<String> toDelete = new HashSet<>();
    /**
     * A mapping of file paths to be renamed within the archive. Each entry maps an original file path to a new file path,
     * indicating that the file should be renamed during the archive modification process. This map is populated with pairs of
     * original and new file paths that are intended to be renamed in the modified archive.
     * 
     * @return the mapping of file paths to be renamed in the archive
     */
    private final @Getter HashMap<String, String> toRename = new HashMap<>();
    /**
     * A mapping of file paths to be copied within the archive. Each entry maps an original file path to a new file path, indicating
     * that the file should be duplicated during the archive modification process. This map is populated with pairs of original and
     * new file paths that are intended to be copied in the modified archive.
     * 
     * @return the mapping of file paths to be copied in the archive
     */
    private final @Getter HashMap<String, String> toCopy = new HashMap<>();

    /**
     * A list of closeable resources that should be closed when the archive is closed. This includes streams, archives, and any
     * other resources that need to be properly released. This list is populated with instances of Closeable resources that are
     * opened during the archive processing and need to be closed to prevent resource leaks.
     * 
     * @return the list of closeable resources to be closed upon archive closing
     */
    private final @Getter List<Closeable> closeables = new ArrayList<>();

    /**
     * A temporary directory used for intermediate file storage during archive modifications. This directory is created as needed
     * and should be cleared when the archive is closed. This field is initialized to null and is assigned a temporary directory
     * path when the getTempDir() method is called for the first time. The directory is intended to be used for storing temporary
     * files that are needed during the archive modification process, and should be cleaned up when the archive is closed.
     * 
     * @return the temporary directory File instance, or null if it has not been initialized
     */
    private File tempDir = null;

    /**
     * Constructs a new NArchiveBase instance. This constructor initializes the base class and sets up any necessary data structures
     * for managing the archive modifications, such as the lists and maps for tracking files to add, delete, rename, and copy. It
     * also prepares the list of closeable resources that will be managed during the archive processing.
     */
    protected NArchiveBase() {
        super();
    }

    @Override
    public void addCloseables(Closeable closeable) {
        closeables.add(closeable);
    }

    @Override
    public void close() throws IOException {
        for (final var closeable : closeables)
            closeable.close();
        closeables.clear();
    }

    /**
     * Retrieves the temporary directory for intermediate file storage during archive modifications. If the temporary directory has
     * not been initialized, it creates a new temporary directory with a prefix "JRM" and returns it. The directory is intended to
     * be used for storing temporary files needed during the archive modification process, and should be cleared when the archive is
     * closed.
     * 
     * @return the File instance representing the temporary directory
     * 
     * @throws IOException if an error occurs while creating the temporary directory
     */
    public File getTempDir() throws IOException {
        if (tempDir == null)
            tempDir = IOUtils.createTempDirectory("JRM").toFile(); //$NON-NLS-1$
        return tempDir;
    }

    /**
     * Clears the temporary directory used for intermediate file storage during archive modifications. This method attempts to
     * delete the temporary directory and all of its contents, and logs any exceptions that occur during the deletion process. It is
     * intended to be called when the archive is closed to ensure that any temporary files are properly cleaned up.
     */
    protected void clearTempDir() {
        try {
            if (tempDir != null)
                FileUtils.deleteDirectory(tempDir);
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

}
