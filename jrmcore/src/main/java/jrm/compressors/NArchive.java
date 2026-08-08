/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.compressors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import jrm.aui.progress.ProgressNarchiveCallBack;
import jrm.compressors.sevenzipjbinding.Archive7ZOpenVolumeCallback;
import jrm.compressors.sevenzipjbinding.ArchiveRAROpenVolumeCallback;
import jrm.compressors.sevenzipjbinding.CloseCreateCallback;
import jrm.compressors.sevenzipjbinding.ExtractorCallback;
import jrm.compressors.sevenzipjbinding.NArchiveBase;
import jrm.misc.GlobalSettings;
import jrm.misc.IOUtils;
import jrm.misc.SettingsEnum;
import jrm.security.Session;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IOutCreateArchive;
import net.sf.sevenzipjbinding.IOutCreateCallback;
import net.sf.sevenzipjbinding.IOutFeatureSetLevel;
import net.sf.sevenzipjbinding.IOutFeatureSetMultithreading;
import net.sf.sevenzipjbinding.IOutFeatureSetSolid;
import net.sf.sevenzipjbinding.IOutItemAllFormats;
import net.sf.sevenzipjbinding.IOutUpdateArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream;
import net.sf.sevenzipjbinding.impl.VolumedArchiveInStream;

/**
 * The multiple formats abstract class using SevenZipJBinding as back-end<br>
 * Please note that SevenZipJBinding never modifies "in place" archive : a new temporary archive is always created, and non modified
 * entries may be copied without further re-compression (except if solid archive).<br>
 * This behavior happens even in case of renaming!<br>
 * If the archive does not already exists, then we are in creation mode, and no temporary file will be created.<br>
 * Currently only support 7Z and ZIP.
 * 
 * @author optyfr
 */
abstract class NArchive extends NArchiveBase {
    /**
     * The session associated with this archive, used for accessing user settings and other session-related information during
     * archive operations.
     */
    private Session session;
    /**
     * The file representing the archive being processed. This is used for reading and writing the archive data, and is stored here
     * to manage the archive file during operations.
     */
    private File archive;
    /**
     * A flag indicating whether the archive is in read-only mode. If true, the archive will be treated as read-only, and
     * modifications will not be allowed. This flag is used to determine the behavior of the archive during operations, such as
     * whether to allow adding, deleting, renaming, or copying entries within the archive.
     */
    private final boolean readonly;
    /**
     * A callback instance for reporting progress during archive operations. This is used to provide feedback on the progress of
     * long-running operations such as extraction, creation, or updating of the archive. The callback can be used to update progress
     * bars or other UI elements to inform the user about the status of the ongoing operation.
     */
    private ProgressNarchiveCallBack cb = null;

    /**
     * A static mapping of archive file paths to their corresponding File instances. This map is used to manage and reuse File
     * instances for archives that are being processed, ensuring that multiple instances of the same archive file are not created
     * unnecessarily. The map allows for efficient access to the File instance associated with a given archive path, and it helps to
     * manage the lifecycle of the archive files during operations.
     */
    private static final Map<String, File> archives = new HashMap<>();

    /**
     * Clears the static archive cache, releasing all cached {@link File} references.
     * <p>
     * This should be called after tests or operations that populate the archive cache to prevent memory leaks.
     * </p>
     */
    public static void clearArchives() {
        archives.clear();
    }

    /**
     * The format of the archive being processed. This is determined based on the file extension of the archive and is used to
     * specify the appropriate handling and options for the archive operations. The format is set during the initialization of the
     * NArchive instance and is used to configure the behavior of the SevenZipJBinding library when performing operations on the
     * archive.
     */
    private ArchiveFormat format = ArchiveFormat.SEVEN_ZIP;
    /**
     * The file extension of the archive being processed. This is determined based on the name of the archive file and is used for
     * various purposes, such as determining the format of the archive and managing temporary files during operations. The extension
     * is set during the initialization of the NArchive instance and is used to ensure that the correct handling and options are
     * applied based on the type of archive being processed.
     */
    private String ext = "7z"; //$NON-NLS-1$

    /**
     * Constructor that default to readwrite
     * 
     * @param session the session associated with this archive, used for accessing user settings and other session-related
     *        information during archive operations
     * @param archive {@link File} to archive
     * 
     * @throws IOException
     * @throws SevenZipNativeInitializationException in case of problem to find and initialize sevenzipjbinding native libraries
     */
    protected NArchive(final Session session, final File archive) throws IOException, SevenZipNativeInitializationException {
        this(session, archive, false, null);
    }

    /**
     * Constructor that default to readwrite
     * 
     * @param session the session associated with this archive, used for accessing user settings and other session-related
     *        information during archive operations
     * @param archive {@link File} to archive
     * @param cb {@link ProgressNarchiveCallBack} to show progress
     * 
     * @throws IOException
     * @throws SevenZipNativeInitializationException in case of problem to find and initialize sevenzipjbinding native libraries
     */
    protected NArchive(final Session session, final File archive, final ProgressNarchiveCallBack cb) throws IOException, SevenZipNativeInitializationException {
        this(session, archive, false, cb);
    }

    /**
     * Constructor with optional readonly mode
     * 
     * @param session the session associated with this archive, used for accessing user settings and other session-related
     *        information during archive operations
     * @param archive {@link File} to archive
     * @param readonly if true, will set archive in readonly safe mode
     * @param cb {@link ProgressNarchiveCallBack} to show progress
     * 
     * @throws IOException
     * @throws SevenZipNativeInitializationException in case of problem to find and initialize sevenzipjbinding native libraries
     */
    protected NArchive(final Session session, final File archive, final boolean readonly, final ProgressNarchiveCallBack cb)
            throws IOException, SevenZipNativeInitializationException {
        this.session = session;
        this.cb = cb;
        if (!SevenZip.isInitializedSuccessfully())
            SevenZip.initSevenZipFromPlatformJAR(session.getUser().getSettings().getTmpPath(true).toFile());
        ext = FilenameUtils.getExtension(archive.getName());
        switch (ext.toLowerCase()) {
            case "zip": //$NON-NLS-1$
                format = ArchiveFormat.ZIP;
                break;
            case "rar":
                format = ArchiveFormat.RAR;
                break;
            case "7z": //$NON-NLS-1$
            default:
                format = ArchiveFormat.SEVEN_ZIP;
                break;
        }
        if (archive.exists()) {
            if (format == ArchiveFormat.RAR) // RAR and RAR multipart
            {
                final var archiveOpenVolumeCallback = new ArchiveRAROpenVolumeCallback(this);
                getCloseables().add(setIInArchive(
                        SevenZip.openInArchive(format, setIInStream(archiveOpenVolumeCallback.getStream(archive.getAbsolutePath())).getIInStream(), archiveOpenVolumeCallback))
                        .getIInArchive());
            } else if (format == ArchiveFormat.SEVEN_ZIP && archive.getName().endsWith(".7z.001")) // SevenZip multipart
            {
                getCloseables().add(setIInArchive(SevenZip.openInArchive(format, new VolumedArchiveInStream(archive.getAbsolutePath(), new Archive7ZOpenVolumeCallback(this))))
                        .getIInArchive());
            } else // auto detect
            {
                getCloseables().add(setIInStream(new RandomAccessFileInStream(new RandomAccessFile(archive, "r"))).getIInStream()); //$NON-NLS-1$
                getCloseables().add(setIInArchive(SevenZip.openInArchive(null, getIInStream())).getIInArchive());
                format = getIInArchive().getArchiveFormat();
            }
        }
        this.readonly = readonly;
        if (null == (this.archive = NArchive.archives.get(archive.getAbsolutePath()))) {
            this.archive = archive;
            NArchive.archives.put(archive.getAbsolutePath(), this.archive);
        }
    }

    /**
     * This is where all operations really take place! Almost all is inside {@link IOutCreateCallback} callback, then we are using
     * {@link IOutUpdateArchive} or {@link IOutCreateArchive} in case of creation mode (where archive does not already exist)
     */
    @Override
    public void close() throws IOException {
        if (getToAdd().isEmpty() && getToRename().isEmpty() && getToDelete().isEmpty() && getToCopy().isEmpty()) {
            super.close();
            clearTempDir();
            return;
        }

        final var rafs = new HashMap<Integer, RandomAccessFile>();
        final var tmpfiles = new HashMap<Integer, File>();

        try {
            final var callback = new CloseCreateCallback(this, tmpfiles, rafs) {
                @Override
                public void setTotal(final long total) throws SevenZipException {
                    if (cb != null)
                        cb.setTotal(total);
                }

                @Override
                public void setCompleted(final long complete) throws SevenZipException {
                    if (cb != null)
                        cb.setCompleted(complete);
                }
            };

            if (archive.exists() && getIInArchive() != null) {
                final var tmpfile = Files.createTempFile(archive.getParentFile().toPath(), "JRM", "." + ext); //$NON-NLS-1$ //$NON-NLS-2$
                Files.deleteIfExists(tmpfile);
                try (final var raf = new RandomAccessFile(tmpfile.toFile(), "rw")) //$NON-NLS-1$
                {
                    final IOutUpdateArchive<IOutItemAllFormats> iout = getIInArchive().getConnectedOutArchive();
                    setOptions(iout);

                    final int itemsCount = getIInArchive().getNumberOfItems() - getToDelete().size() + getToAdd().size() + getToCopy().size();
                    iout.updateItems(new RandomAccessFileOutStream(raf), itemsCount, callback);
                }
                super.close();
                if (Files.exists(tmpfile) && Files.size(tmpfile) > 0 && Files.deleteIfExists(archive.toPath()) && !tmpfile.toFile().renameTo(archive))
                    Files.delete(tmpfile);
            } else {
                try (final var iout = SevenZip.openOutArchive(format); RandomAccessFile raf = new RandomAccessFile(archive, "rw")) //$NON-NLS-1$
                {
                    setOptions(iout);

                    final var itemsCount = getToAdd().size() + getToCopy().size();

                    iout.createArchive(new RandomAccessFileOutStream(raf), itemsCount, callback);
                }
                super.close();
            }
        } finally {
            for (final var raf : rafs.values())
                raf.close();
            for (final var tmpfile : tmpfiles.values())
                Files.delete(tmpfile.toPath());
        }
        clearTempDir();
    }

    /**
     * Mapper between SevenZipJBinding options and {@link GlobalSettings}
     * 
     * @param iout the archive feature to map (see code to know what is supported)
     * 
     * @throws SevenZipException
     */
    private void setOptions(final Object iout) throws SevenZipException {
        switch (format) {
            case SEVEN_ZIP:
                if (iout instanceof IOutFeatureSetSolid ss)
                    ss.setSolid(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_solid, Boolean.class)); // $NON-NLS-1$
                if (iout instanceof IOutFeatureSetLevel sl)
                    sl.setLevel(SevenZipOptions.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_level)).getLevel()); // $NON-NLS-1$
                if (iout instanceof IOutFeatureSetMultithreading sm)
                    sm.setThreadCount(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_threads, Integer.class)); // $NON-NLS-1$
                break;
            case ZIP:
                if (iout instanceof IOutFeatureSetLevel sl)
                    sl.setLevel(ZipOptions.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.zip_level)).getLevel()); // $NON-NLS-1$
                if (iout instanceof IOutFeatureSetMultithreading sm)
                    sm.setThreadCount(session.getUser().getSettings().getProperty(SettingsEnum.zip_threads, Integer.class)); // $NON-NLS-1$
                break;
            default:
                break;
        }

    }

    /**
     * Internal method to extract one entry into an arbitrary base directory
     * 
     * @param baseDir the base directory where we should extract file
     * @param entry the entry name of the file (with path)
     * 
     * @return 0 in case of success, -1 otherwise
     * 
     * @throws IOException
     */
    private int extract(final File baseDir, final String entry) throws IOException {
        if (entry != null) {
            final var simpleInArchive = getIInArchive().getSimpleInterface();
            for (final var item : simpleInArchive.getArchiveItems()) {
                if (item.getPath().equals(entry)) {
                    final var file = new File(baseDir, entry);
                    FileUtils.forceMkdirParent(file);
                    try (final var out = new RandomAccessFile(file, "rw")) //$NON-NLS-1$
                    {
                        if (item.extractSlow(new RandomAccessFileOutStream(out)) == ExtractOperationResult.OK)
                            return 0;
                    }
                }
            }
            return -1;
        }
        final var tmpfiles = new HashMap<Integer, File>();
        final var rafs = new HashMap<Integer, RandomAccessFile>();
        final var idx = new int[getIInArchive().getNumberOfItems()];
        for (var i = 0; i < idx.length; i++) {
            idx[i] = i;
            if (!(boolean) getIInArchive().getProperty(i, PropID.IS_FOLDER)) {
                final var file = IOUtils.createTempFile("JRM", null).toFile();
                tmpfiles.put(i, file); // $NON-NLS-1$
                rafs.put(i, new RandomAccessFile(file, "rw")); //$NON-NLS-1$
            } else {
                final var dir = new File(baseDir, (String) getIInArchive().getProperty(i, PropID.PATH));
                FileUtils.forceMkdir(dir);
            }
        }

        getIInArchive().extract(idx, false, new ExtractorCallbackWithProgress(this, baseDir, tmpfiles, rafs));
        return 0;
    }

    /**
     * Extractor callback with progress support.
     */
    private final class ExtractorCallbackWithProgress extends ExtractorCallback {
        /**
         * Constructor for the ExtractorCallbackWithProgress class, which extends the ExtractorCallback to include progress
         * reporting functionality. This constructor initializes the callback with the provided parameters and allows for progress
         * updates to be sent to the associated ProgressNarchiveCallBack instance.
         * 
         * @param nArchive the base archive instance associated with this callback
         * @param baseDir the base directory where extracted files should be moved after extraction
         * @param tmpfiles a mapping of archive entry indices to temporary files for writing extracted data
         * @param rafs a mapping of archive entry indices to RandomAccessFile instances for writing extracted data
         */
        private ExtractorCallbackWithProgress(NArchiveBase nArchive, File baseDir, Map<Integer, File> tmpfiles, Map<Integer, RandomAccessFile> rafs) {
            super(nArchive, baseDir, tmpfiles, rafs);
        }

        /**
         * Overrides the setTotal method to report the total progress of the extraction operation. If a ProgressNarchiveCallBack
         * instance is associated with this callback, it calls the setTotal method on the callback to update the total progress
         * value.
         * 
         * @param total the total progress value to be set for the extraction operation
         * 
         * @throws SevenZipException if an error occurs while setting the total progress (not applicable in this implementation)
         */
        @Override
        public void setTotal(long total) throws SevenZipException {
            if (cb != null)
                cb.setTotal(total);
        }

        /**
         * Overrides the setCompleted method to report the completed progress of the extraction operation. If a
         * ProgressNarchiveCallBack instance is associated with this callback, it calls the setCompleted method on the callback to
         * update the completed progress value.
         * 
         * @param complete the completed progress value to be set for the extraction operation
         * 
         * @throws SevenZipException if an error occurs while setting the completed progress (not applicable in this implementation)
         */
        @Override
        public void setCompleted(long complete) throws SevenZipException {
            if (cb != null)
                cb.setCompleted(complete);
        }
    }

    /**
     * Extracts the entire archive to a temporary directory. This method calls the internal extract method with the temporary
     * directory as the base directory and null as the entry, indicating that all entries should be extracted. It returns 0 in case
     * of success, or -1 if an error occurs during extraction.
     * 
     * @return 0 if the extraction was successful, or -1 if an error occurred
     * 
     * @throws IOException if an error occurs during extraction (e.g., file I/O errors)
     */
    @Override
    public int extract() throws IOException {
        return extract(getTempDir(), null);
    }

    /**
     * Extracts a specific entry from the archive to a temporary directory. This method calls the internal extract method with the
     * temporary directory as the base directory and the specified entry name, indicating that only that entry should be extracted.
     * If the extraction is successful and the resulting file exists, it returns a File instance representing the extracted file. If
     * the extraction fails or the resulting file does not exist, it returns null.
     * 
     * @param entry the name of the entry to be extracted (including path within the archive)
     * 
     * @return a File instance representing the extracted file if successful, or null if extraction failed or the file does not
     *         exist
     * 
     * @throws IOException if an error occurs during extraction (e.g., file I/O errors)
     */
    @Override
    public File extract(final String entry) throws IOException {
        final var validatedEntry = validateAndNormalizeEntry(entry);
        extract(getTempDir(), validatedEntry);
        final var result = new File(getTempDir(), validatedEntry);
        if (result.exists())
            return result;
        return null;
    }

    /**
     * Extracts a specific entry from the archive and returns it as an InputStream. This method calls the internal extract method
     * with the temporary directory as the base directory and the specified entry name, indicating that only that entry should be
     * extracted. If the extraction is successful, it returns an InputStream for reading the extracted file. If the extraction fails
     * or the resulting file does not exist, it throws an IOException.
     * 
     * @param entry the name of the entry to be extracted (including path within the archive)
     * 
     * @return an InputStream for reading the extracted file if successful
     * 
     * @throws IOException if an error occurs during extraction (e.g., file I/O errors) or if the resulting file does not exist
     */
    @Override
    public InputStream extractStdOut(final String entry) throws IOException {
        final var validatedEntry = validateAndNormalizeEntry(entry);
        extract(getTempDir(), validatedEntry);
        return new FileInputStream(new File(getTempDir(), validatedEntry));
    }

    /**
     * Adds a file entry to the archive from a specified base directory. This method checks if the archive is in read-only mode, and
     * if so, it returns -1 to indicate that the operation is not allowed. If the base directory is a file, it copies the file to
     * the temporary directory with the specified entry name. If the base directory is a directory and is not the same as the
     * temporary directory, it copies the specified entry from the base directory to the temporary directory. Finally, it adds the
     * entry name to the list of entries to be added to the archive and returns 0 to indicate success.
     * 
     * @param baseDir the base directory from which to add the file entry (can be a file or a directory)
     * @param entry the name of the entry to be added (including path within the archive)
     * 
     * @return 0 if the entry was successfully added, or -1 if the archive is in read-only mode
     * 
     * @throws IOException if an error occurs during file copying (e.g., file I/O errors)
     */
    @Override
    public int add(final File baseDir, final String entry) throws IOException {
        if (readonly)
            return -1;
        final var validatedEntry = validateAndNormalizeEntry(entry);
        if (baseDir.isFile())
            FileUtils.copyFile(baseDir, new File(getTempDir(), validatedEntry));
        else if (!baseDir.equals(getTempDir()))
            FileUtils.copyFile(new File(baseDir, entry), new File(getTempDir(), validatedEntry));
        getToAdd().add(validatedEntry);
        return 0;
    }

    /**
     * Adds a file entry to the archive using an InputStream as the source. This method checks if the archive is in read-only mode,
     * and if so, it returns -1 to indicate that the operation is not allowed. Otherwise, it validates the entry path to prevent
     * path traversal attacks, copies the input stream to a file in the temporary directory with the specified entry name, adds the
     * entry name to the list of entries to be added to the archive, and returns 0 to indicate success.
     * 
     * @param src the InputStream containing the data for the new entry
     * @param entry the name of the entry to be added (including path within the archive)
     * 
     * @return 0 if the entry was successfully added, or -1 if the archive is in read-only mode
     * 
     * @throws IOException if an error occurs while writing the input stream to a file or if the entry path is invalid
     */
    @Override
    public int addStdIn(final InputStream src, final String entry) throws IOException {
        if (readonly)
            return -1;
        final var validatedEntry = validateAndNormalizeEntry(entry);
        FileUtils.copyInputStreamToFile(src, new File(getTempDir(), validatedEntry));
        getToAdd().add(validatedEntry);
        return 0;
    }

    /**
     * Deletes a file entry from the archive. This method checks if the archive is in read-only mode, and if so, it returns -1 to
     * indicate that the operation is not allowed. Otherwise, it adds the entry (after normalizing it) to the list of entries to be
     * deleted during the archive update. It returns 0 to indicate success.
     * 
     * @param entry the name of the entry to be deleted (including path within the archive)
     * 
     * @return 0 if the deletion was successfully queued, or -1 if the archive is in read-only mode
     * 
     * @throws IOException if an error occurs during deletion
     */
    @Override
    public int delete(final String entry) throws IOException {
        if (readonly)
            return -1;
        getToDelete().add(normalize(entry));
        return 0;
    }

    /**
     * Renames a file entry in the archive to a new name. This method checks if the archive is in read-only mode, and if so, it
     * returns -1 to indicate that the operation is not allowed. Otherwise, it adds the mapping of the original entry to the new
     * name (after normalizing both) to the list of entries to be renamed during the archive update. It returns 0 to indicate
     * success.
     * 
     * @param entry the original name of the entry to be renamed (including path within the archive)
     * @param newname the new name for the entry (including path within the archive)
     * 
     * @return 0 if the renaming was successfully queued, or -1 if the archive is in read-only mode
     * 
     * @throws IOException if an error occurs during renaming
     */
    @Override
    public int rename(final String entry, final String newname) throws IOException {
        if (readonly)
            return -1;
        getToRename().put(normalize(entry), normalize(newname));
        return 0;
    }

    /**
     * Duplicates a file entry in the archive with a new name. This method checks if the archive is in read-only mode, and if so, it
     * returns -1 to indicate that the operation is not allowed. Otherwise, it adds the mapping of the original entry to the new
     * name (after normalizing both) to the list of entries to be copied during the archive update. It returns 0 to indicate
     * success.
     * 
     * @param entry the original name of the entry to be duplicated (including path within the archive)
     * @param newname the name for the duplicated entry (including path within the archive)
     * 
     * @return 0 if the duplication was successfully queued, or -1 if the archive is in read-only mode
     * 
     * @throws IOException if an error occurs during duplication
     */
    @Override
    public int duplicate(final String entry, final String newname) throws IOException {
        if (readonly)
            return -1;
        getToCopy().put(normalize(newname), normalize(entry));
        return 0;
    }

    /**
     * Normalize char separator according platform default separator
     * 
     * @param entry the entry to normalize
     * 
     * @return the normalized entry
     */
    private String normalize(final String entry) {
        if (File.separatorChar == '/')
            return entry.replace('\\', '/');
        return entry.replace('/', '\\');
    }

    /**
     * Validates and normalizes an entry path to prevent path traversal attacks.
     * This method ensures that the entry path:
     * <ul>
     * <li>Does not represent an absolute path</li>
     * <li>Does not escape the temporary directory using ".." segments</li>
     * <li>Does not contain null bytes or other dangerous characters</li>
     * </ul>
     * 
     * @param entry the entry path to validate and normalize
     * 
     * @return the validated and normalized entry path
     * 
     * @throws IOException if the entry path is invalid or represents a path traversal attempt
     */
    private String validateAndNormalizeEntry(final String entry) throws IOException {
        if (entry == null || entry.isEmpty()) {
            throw new IOException("Entry path cannot be null or empty");
        }
        
        // Check for null bytes
        if (entry.contains("\0")) {
            throw new IOException("Entry path contains null byte: " + entry);
        }
        
        // Check for absolute paths (Unix-style or Windows-style)
        if (entry.startsWith("/") || entry.startsWith("\\\\") || 
            (entry.length() > 1 && entry.charAt(1) == ':')) {
            throw new IOException("Entry path cannot be absolute: " + entry);
        }
        
        // Normalize the entry path and resolve it against the temp directory
        final var tempDirPath = getTempDir().toPath().toAbsolutePath().normalize();
        final var entryPath = tempDirPath.resolve(entry).normalize();
        
        // Verify the resolved path is still within the temp directory
        if (!entryPath.startsWith(tempDirPath)) {
            throw new IOException("Entry path escapes temporary directory: " + entry);
        }
        
        // Return the relative path from temp directory
        return tempDirPath.relativize(entryPath).toString();
    }
}
