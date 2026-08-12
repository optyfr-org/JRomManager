package jrm.compressors.sevenzipjbinding;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import jrm.misc.Log;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;

/**
 * An abstract callback implementation for extracting archive entries using SevenZipJBinding. It manages writing extracted data to
 * temporary files and moving them to their final destination upon completion.
 */
public abstract class ExtractorCallback implements IArchiveExtractCallback {

    /** The base archive instance associated with this callback, used to access archive properties and methods during extraction. */
    private final NArchiveBase nArchive;
    /**
     * The base directory where extracted files should be moved after extraction. This is used to determine the final destination of
     * extracted files based on their paths within the archive.
     */
    private final File baseDir;
    /**
     * A mapping of archive entry indices to temporary files where extracted data is written during the extraction process. This
     * allows for managing the intermediate storage of extracted data before it is moved to its final location.
     */
    private final Map<Integer, File> tmpfiles;
    /**
     * A mapping of archive entry indices to RandomAccessFile instances used for writing extracted data during the extraction
     * process. This allows for managing the output streams associated with each entry being extracted.
     */
    private final Map<Integer, RandomAccessFile> rafs;
    /**
     * A flag indicating whether the current entry being processed should be skipped during extraction. This is typically set based
     * on whether the entry is a folder or if the extraction mode does not require extracting the entry.
     */
    private boolean skipExtraction;
    /**
     * The index of the current archive entry being processed. This is used to identify which entry is being extracted and to manage
     * the corresponding temporary file and output stream.
     */
    private int index;

    /**
     * Constructs a new ExtractorCallback instance with the specified parameters.
     * 
     * @param nArchive the base archive instance associated with this callback
     * @param baseDir the base directory where extracted files should be moved after extraction
     * @param tmpfiles a mapping of archive entry indices to temporary files for writing extracted data
     * @param rafs a mapping of archive entry indices to RandomAccessFile instances for writing extracted data
     */
    protected ExtractorCallback(NArchiveBase nArchive, File baseDir, Map<Integer, File> tmpfiles, Map<Integer, RandomAccessFile> rafs) {
        this.nArchive = nArchive;
        this.baseDir = baseDir;
        this.tmpfiles = tmpfiles;
        this.rafs = rafs;
    }

    /**
     * Prepares the extraction operation for the current entry. In this implementation, no specific preparation is needed, so the
     * method is left empty. This method can be overridden by subclasses if any setup is required before extracting an entry.
     * 
     * @param extractAskMode the mode of extraction being performed (e.g., EXTRACT, TEST, etc.)
     * 
     * @throws SevenZipException if an error occurs during preparation (not applicable in this implementation)
     */
    @Override
    public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
        // do nothing
    }

    /**
     * Sets the result of the extraction operation for the current entry. If the extraction was successful, it moves the temporary
     * file to its final destination based on the entry's path within the archive. If there was an error during extraction, it logs
     * an error message.
     * 
     * @param extractOperationResult the result of the extraction operation for the current entry
     * 
     * @throws SevenZipException if an error occurs while handling the extraction result
     */
    @Override
    public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
        if (skipExtraction)
            return;
        if (extractOperationResult != ExtractOperationResult.OK)
            Log.err("Extraction error");
        else {
            try {
                rafs.get(index).close();
                String path = (String) this.nArchive.getIInArchive().getProperty(index, PropID.PATH);
                File tmpfile = tmpfiles.get(index);
                File dstfile = resolveContainedFile(baseDir, path);
                FileUtils.forceMkdirParent(dstfile);
                if (!dstfile.exists())
                    FileUtils.moveFile(tmpfile, dstfile);
            } catch (IOException e) {
                throw new SevenZipException(e.getMessage(), e);
            }
        }
    }

    /**
     * Resolves an archive entry path against the extraction root and rejects path-traversal entries (Zip Slip).
     *
     * @param baseDir extraction root directory
     * @param entryPath archive-controlled entry path (from {@link PropID#PATH} or a caller-supplied name)
     * @return destination file confined under {@code baseDir}
     * @throws IOException if the entry is absolute, contains NULs, or would escape {@code baseDir}
     */
    public static File resolveContainedFile(final File baseDir, final String entryPath) throws IOException {
        if (baseDir == null) {
            throw new IOException("Extraction directory cannot be null");
        }
        if (entryPath == null || entryPath.isEmpty()) {
            throw new IOException("Entry path cannot be null or empty");
        }

        final var normalizedEntry = entryPath.replace('\\', '/');
        if (normalizedEntry.contains("\0")) {
            throw new IOException("Entry path contains null byte: " + normalizedEntry);
        }

        final var hasWindowsDriveRoot = normalizedEntry.length() > 2
            && Character.isLetter(normalizedEntry.charAt(0))
            && normalizedEntry.charAt(1) == ':'
            && (normalizedEntry.charAt(2) == '/' || normalizedEntry.charAt(2) == '\\');
        if (normalizedEntry.startsWith("/") || normalizedEntry.startsWith("//") || hasWindowsDriveRoot) {
            throw new IOException("Entry path cannot be absolute: " + normalizedEntry);
        }

        final Path basePath = baseDir.toPath().toAbsolutePath().normalize();
        final Path resolved = basePath.resolve(normalizedEntry).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IOException("Entry path escapes temporary directory: " + normalizedEntry);
        }
        return resolved.toFile();
    }

    /**
     * Retrieves the sequential output stream for writing the extracted data of the specified archive entry. If the entry is a
     * folder or if the extraction mode is not EXTRACT, it returns {@code null} to skip extraction.
     * 
     * @param index the index of the archive entry being processed
     * @param extractAskMode the mode of extraction being performed
     * 
     * @return the sequential output stream to write data to, or {@code null} if the entry should be skipped
     * 
     * @throws SevenZipException if an error occurs while retrieving the entry properties
     */
    @Override
    public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
        this.index = index;
        skipExtraction = (Boolean) this.nArchive.getIInArchive().getProperty(index, PropID.IS_FOLDER);
        if (skipExtraction || extractAskMode != ExtractAskMode.EXTRACT)
            return null;
        return new ISequentialOutStream() {
            @Override
            public int write(byte[] data) throws SevenZipException {
                try {
                    rafs.get(index).write(data);
                    return data.length;
                } catch (IOException e) {
                    Log.err(e.getMessage(), e);
                }
                return 0;
            }
        };
    }
}