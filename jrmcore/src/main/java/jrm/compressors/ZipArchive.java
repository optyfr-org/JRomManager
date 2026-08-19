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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import jrm.aui.progress.ProgressNarchiveCallBack;
import jrm.misc.Log;
import jrm.security.Session;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

/**
 * The external Zip archive class.<br>
 * If possible, wrap over {@link NArchive} via {@link ZipNArchive} to use SevenZipJBinding.<br>
 * Otherwise will try to use external zip executable if available...<br>
 * If command line is used, the archive will be extracted to temporary directory upon first write operation, then entirely recreated
 * from temporary directory upon archive's {@link #close()} operation
 * 
 * @author optyfr
 */
public class ZipArchive implements Archive {
    /**
     * Progress callback for tracking the progress of archive operations. This callback can be used to update progress indicators in
     * user interfaces or logs during long-running archive operations.
     */
    private ProgressNarchiveCallBack cb;
    /**
     * The File object representing the archive file being managed by this instance. This is used to access the archive file for
     * reading, writing, and other operations as needed during the lifecycle of the ZipArchive instance.
     */
    private File archive;

    /**
     * The native Zip archive instance used for performing archive operations. This instance is typically initialized using the
     * SevenZipJBinding library and provides methods for extracting, adding, deleting, renaming, and duplicating entries within the
     * archive. It serves as the underlying implementation for managing the Zip archive and executing the corresponding operations
     * based on the methods defined in the Archive interface.
     */
    private ZipNArchive nativeZip = null;

    /**
     * Constructs a new ZipArchive instance with the specified session and archive file. This constructor initializes the archive in
     * read-write mode by default and does not provide a progress callback.
     * 
     * @param session the Session object representing the current user session, used for authentication and access control during
     *        archive operations
     * @param archive the File object representing the archive file to be opened and managed by this instance
     * 
     * @throws IOException if an I/O error occurs while accessing the archive file
     */
    public ZipArchive(final Session session, final File archive) throws IOException {
        this(session, archive, false, null);
    }

    /**
     * Constructs a new ZipArchive instance with the specified session, archive file, and progress callback. This constructor
     * initializes the archive in read-write mode by default and provides a callback for tracking progress during archive
     * operations.
     * 
     * @param session the Session object representing the current user session, used for authentication and access control during
     *        archive operations
     * @param archive the File object representing the archive file to be opened and managed by this instance
     * @param cb a ProgressNarchiveCallBack instance used for tracking progress during archive operations, allowing for updates on
     *        the status of ongoing tasks
     * 
     * @throws IOException if an I/O error occurs while accessing the archive file
     */
    public ZipArchive(final Session session, final File archive, ProgressNarchiveCallBack cb) throws IOException {
        this(session, archive, false, cb);
    }

    /**
     * Constructs a new ZipArchive instance with the specified session, archive file, read-only flag, and progress callback. This
     * constructor allows for specifying whether the archive should be opened in read-only mode and provides a callback for tracking
     * progress during archive operations.
     * 
     * @param session the Session object representing the current user session, used for authentication and access control during
     *        archive operations
     * @param archive the File object representing the archive file to be opened and managed by this instance
     * @param readonly a boolean flag indicating whether the archive should be opened in read-only mode (true) or read-write mode
     *        (false)
     * @param cb a ProgressNarchiveCallBack instance used for tracking progress during archive operations, allowing for updates on
     *        the status of ongoing tasks
     * 
     * @throws IOException if an I/O error occurs while accessing the archive file
     */
    public ZipArchive(final Session session, final File archive, final boolean readonly, ProgressNarchiveCallBack cb) throws IOException {
        this.cb = cb;
        this.archive = archive;
        try {
            nativeZip = new ZipNArchive(session, archive, readonly, cb);
        } catch (final SevenZipNativeInitializationException _) {
            throw new IOException("not supported on that platform"); //$NON-NLS-1$
        }
    }

    /**
     * Closes the ZipArchive instance and releases any resources associated with it. This method should be called when the archive
     * operations are completed to ensure that all resources are properly released and any temporary files are cleaned up. If an I/O
     * error occurs during the closing process, it will be thrown as an IOException.
     * 
     * @throws IOException if an I/O error occurs while closing the archive or releasing resources
     */
    @Override
    public void close() throws IOException {
        nativeZip.close();
    }

    /**
     * Retrieves the temporary directory for intermediate file storage during archive modifications. This method delegates the call
     * to the underlying native Zip archive instance, allowing it to manage the temporary directory as needed for its operations.
     * The returned File instance represents the temporary directory that can be used for storing intermediate files during archive
     * modifications, and should be cleaned up when the archive is closed.
     * 
     * @return the File instance representing the temporary directory for intermediate file storage
     * 
     * @throws IOException if an error occurs while retrieving or creating the temporary directory
     */
    @Override
    public File getTempDir() throws IOException {
        return nativeZip.getTempDir();
    }

    /**
     * A custom file visitor class that extends SimpleFileVisitor to provide custom behavior during file tree traversal. This class
     * is used in the extractCustom method to perform specific actions when visiting directories and files within the archive. It
     * delegates the actual visit operations to a CustomVisitor instance, allowing for flexible handling of file and directory
     * visits based on the logic defined in the CustomVisitor implementation. The CustomVisitorCB class also keeps track of the
     * number of files visited and updates the progress callback accordingly if it is provided.
     */
    private final class CustomVisitorCB extends SimpleFileVisitor<Path> {
        /**
         * A reference to the CustomVisitor instance that defines the custom behavior for visiting directories and files during the
         * file tree traversal. This allows the CustomVisitorCB to delegate the visit operations to the CustomVisitor, enabling
         * flexible handling of file and directory visits based on the logic defined in the CustomVisitor implementation.
         */
        private final CustomVisitor sfv;
        /**
         * A counter to keep track of the number of files visited during the file tree traversal. This counter is incremented each
         * time a file is visited, and it is used to update the progress callback with the number of completed files if a progress
         * callback is provided.
         */
        long cnt = 0;

        /**
         * Constructs a new CustomVisitorCB instance with the specified CustomVisitor. This constructor initializes the
         * CustomVisitorCB with the provided CustomVisitor, allowing it to delegate the visit operations to the CustomVisitor during
         * the file tree traversal.
         * 
         * @param sfv the CustomVisitor instance that defines the custom behavior for visiting directories and files during the file
         *        tree traversal
         */
        private CustomVisitorCB(CustomVisitor sfv) {
            this.sfv = sfv;
        }

        /**
         * Overrides the preVisitDirectory method to delegate the directory visit operation to the CustomVisitor instance. This
         * method is called before visiting a directory during the file tree traversal, and it allows the CustomVisitor to perform
         * any necessary actions or checks before proceeding with the visit. The return value of this method determines whether the
         * file tree traversal should continue, skip the subtree, or terminate based on the logic defined in the CustomVisitor
         * implementation.
         * 
         * @param dir the Path object representing the directory being visited
         * @param attrs the BasicFileAttributes of the directory being visited
         * 
         * @return a FileVisitResult indicating whether to continue, skip, or terminate the file tree traversal based on the logic
         *         defined in the CustomVisitor implementation
         * 
         * @throws IOException if an I/O error occurs while visiting the directory
         */
        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            return sfv.preVisitDirectory(dir, attrs);
        }

        /**
         * Overrides the postVisitDirectory method to delegate the directory visit operation to the CustomVisitor instance. This
         * method is called after visiting a directory during the file tree traversal, and it allows the CustomVisitor to perform
         * any necessary actions or checks after completing the visit. The return value of this method determines whether the file
         * tree traversal should continue, skip the subtree, or terminate based on the logic defined in the CustomVisitor
         * implementation.
         * 
         * @param dir the Path object representing the directory that was visited
         * @param exc an IOException that occurred during the visit, or null if no exception occurred
         * 
         * @return a FileVisitResult indicating whether to continue, skip, or terminate the file tree traversal based on the logic
         *         defined in the CustomVisitor implementation
         * 
         * @throws IOException if an I/O error occurs while visiting the directory
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return sfv.postVisitDirectory(dir, exc);
        }

        /**
         * Overrides the visitFile method to delegate the file visit operation to the CustomVisitor instance. This method is called
         * when visiting a file during the file tree traversal, and it allows the CustomVisitor to perform any necessary actions or
         * checks when visiting a file. After delegating the visit operation to the CustomVisitor, it increments the file counter
         * and updates the progress callback with the number of completed files if a progress callback is provided. The return value
         * of this method determines whether the file tree traversal should continue, skip the subtree, or terminate based on the
         * logic defined in the CustomVisitor implementation.
         * 
         * @param file the Path object representing the file being visited
         * @param attrs the BasicFileAttributes of the file being visited
         * 
         * @return a FileVisitResult indicating whether to continue, skip, or terminate the file tree traversal based on the logic
         *         defined in the CustomVisitor implementation
         * 
         * @throws IOException if an I/O error occurs while visiting the file
         */
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            final FileVisitResult result = sfv.visitFile(file, attrs);
            if (cb != null)
                cb.setCompleted(++cnt);
            return result;
        }
    }

    /**
     * A custom visitor class that extends SimpleFileVisitor to provide custom behavior during file tree traversal. This class is
     * used in the extractCustom method to perform specific actions when visiting directories and files within the archive. It
     * allows for flexible handling of file and directory visits based on the logic defined in the CustomVisitor implementation, and
     * it also keeps a reference to the source path and file system being traversed. The CustomVisitor can be extended to implement
     * specific logic for handling files and directories during the extraction process, while the CustomVisitorCB class manages the
     * traversal and progress tracking.
     */
    public static class CustomVisitor extends SimpleFileVisitor<Path> {
        /**
         * A reference to the source path being traversed during the file tree traversal. This path represents the root of the file
         * tree being visited, and it can be used by the CustomVisitor to perform operations relative to the source path when
         * visiting directories and files. The sourcePath is typically set before starting the file tree traversal and can be
         * accessed by the CustomVisitor implementation to determine the context of the visit operations.
         */
        private Path sourcePath = null;
        /**
         * A reference to the file system being traversed during the file tree traversal. This file system represents the underlying
         * file system of the source path, and it can be used by the CustomVisitor to perform operations on files and directories
         * within that file system. The fs is typically set before starting the file tree traversal and can be accessed by the
         * CustomVisitor implementation to interact with the files and directories being visited.
         */
        private FileSystem fs = null;

        /**
         * Constructs a new CustomVisitor instance. This constructor initializes the CustomVisitor without setting the source path
         * or file system, allowing them to be set later before starting the file tree traversal. The CustomVisitor can be extended
         * to implement specific logic for handling files and directories during the extraction process, while the source path and
         * file system can be set as needed based on the context of the traversal.
         */
        public CustomVisitor() { /* do nothing */
        }

        /**
         * Constructs a new CustomVisitor instance with the specified source path. This constructor initializes the CustomVisitor
         * with the provided source path, allowing it to perform operations relative to that path when visiting directories and
         * files during the file tree traversal. The file system can be set separately if needed, or it can be derived from the
         * source path when starting the traversal. The CustomVisitor can be extended to implement specific logic for handling files
         * and directories during the extraction process, while the source path provides context for the visit operations.
         * 
         * @param sourcePath the Path object representing the source path being traversed during the file tree traversal
         */
        public CustomVisitor(Path sourcePath) {
            setSourcePath(sourcePath);
        }

        /**
         * Retrieves the source path being traversed during the file tree traversal. This method returns the Path object
         * representing the root of the file tree being visited, which can be used by the CustomVisitor implementation to perform
         * operations relative to that path when visiting directories and files. The source path is typically set before starting
         * the file tree traversal and provides context for the visit operations performed by the CustomVisitor.
         * 
         * @return the Path object representing the source path being traversed during the file tree traversal
         */
        public Path getSourcePath() {
            return sourcePath;
        }

        /**
         * Sets the source path being traversed during the file tree traversal. This method allows for setting the Path object
         * representing the root of the file tree being visited, which can be used by the CustomVisitor implementation to perform
         * operations relative to that path when visiting directories and files. The source path should be set before starting the
         * file tree traversal to provide context for the visit operations performed by the CustomVisitor.
         * 
         * @param sourcePath the Path object representing the source path to be set for the file tree traversal
         */
        private void setSourcePath(Path sourcePath) {
            this.sourcePath = sourcePath;
        }

        /**
         * Sets the file system being traversed during the file tree traversal. This method allows for setting the FileSystem object
         * representing the underlying file system of the source path, which can be used by the CustomVisitor implementation to
         * perform operations on files and directories within that file system. The file system should be set before starting the
         * file tree traversal to provide context for the visit operations performed by the CustomVisitor.
         * 
         * @param fs the FileSystem object representing the file system to be set for the file tree traversal
         */
        private void setFileSystem(FileSystem fs) {
            this.fs = fs;
        }

        /**
         * Retrieves the file system being traversed during the file tree traversal. This method returns the FileSystem object
         * representing the underlying file system of the source path, which can be used by the CustomVisitor implementation to
         * perform operations on files and directories within that file system. The file system is typically set before starting the
         * file tree traversal and provides context for the visit operations performed by the CustomVisitor.
         * 
         * @return the FileSystem object representing the file system being traversed during the file tree traversal
         */
        public FileSystem getFileSystem() {
            return fs;
        }
    }

    /**
     * Extracts the contents of the archive using a custom visitor for handling file and directory visits during the extraction
     * process. This method creates a new file system for the archive, sets up the CustomVisitor with the source path and file
     * system, and then walks the file tree using a CustomVisitorCB to manage the traversal and progress tracking. The CustomVisitor
     * allows for flexible handling of file and directory visits based on the logic defined in its implementation, while the
     * CustomVisitorCB manages the traversal and updates the progress callback as files are visited. If an I/O error occurs during
     * this process, it will be logged and a value of -1 will be returned to indicate failure. If the extraction is successful, a
     * value of 0 will be returned.
     * 
     * @param sfv the CustomVisitor instance used for handling file and directory visits during the extraction process
     * 
     * @return 0 if the extraction is successful, or -1 if an I/O error occurs during the extraction process
     */
    public int extractCustom(CustomVisitor sfv) {
        try (final var srcfs = FileSystems.newFileSystem(archive.toPath(), (ClassLoader) null);) {
            sfv.setFileSystem(srcfs);
            sfv.setSourcePath(srcfs.getPath("/"));
            if (cb != null)
                try (final var stream = Files.walk(sfv.getSourcePath())) {
                    cb.setTotal(stream.filter(Files::isRegularFile).count());
                }
            Files.walkFileTree(sfv.getSourcePath(), new CustomVisitorCB(sfv));
            return 0;
        } catch (IOException ex) {
            Log.err(ex.getMessage(), ex);
        }
        return -1;
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#extract()
     */
    @Override
    public int extract() throws IOException {
        return nativeZip.extract();
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#extract(java.lang.String)
     */
    @Override
    public File extract(final String entry) throws IOException {
        return nativeZip.extract(entry);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#extractStdOut(java.lang.String)
     */
    @Override
    public InputStream extractStdOut(final String entry) throws IOException {
        return nativeZip.extractStdOut(entry);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#add(java.lang.String)
     */
    @Override
    public int add(final String entry) throws IOException {
        return nativeZip.add(entry);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#add(java.io.File, java.lang.String)
     */
    @Override
    public int add(final File baseDir, final String entry) throws IOException {
        return nativeZip.add(baseDir, entry);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#addStdIn(java.io.InputStream, java.lang.String)
     */
    @Override
    public int addStdIn(final InputStream src, final String entry) throws IOException {
        return nativeZip.addStdIn(src, entry);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#delete(java.lang.String)
     */
    @Override
    public int delete(final String entry) throws IOException {
        return nativeZip.delete(entry);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#rename(java.lang.String, java.lang.String)
     */
    @Override
    public int rename(final String entry, final String newname) throws IOException {
        return nativeZip.rename(entry, newname);
    }

    /**
     * (non-Javadoc)
     * 
     * @see jrm.compressors.Archive#duplicate(java.lang.String, java.lang.String)
     */
    @Override
    public int duplicate(final String entry, final String newname) throws IOException {
        return nativeZip.duplicate(entry, newname);
    }
}
