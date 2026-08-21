package jrm.batch;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.progress.ProgressNarchiveCallBack;
import jrm.aui.progress.ProgressTZipCallBack;
import jrm.aui.status.StatusRendererFactory;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipArchive;
import jrm.compressors.ZipArchive.CustomVisitor;
import jrm.compressors.ZipLevel;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.security.Session;
import jtrrntzip.SimpleTorrentZipOptions;
import jtrrntzip.TorrentZip;
import jtrrntzip.TrrntZipStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

/**
 * Handles archive compression operations, including conversion between formats like 7z and zip, as well as TorrentZip validation.
 */
public class Compressor implements StatusRendererFactory {
    /**
     * Status message constants used for progress updates during compression operations. These constants represent common status
     * messages such as "OK", "Failed", "Crunching", "Extracting", "Creating", and "Processing", which are used to indicate the current state of
     * the compression process in progress updates.
     */
    private static final String OK = "OK";
    private static final String FAILED = "Failed";
    private static final String CRUNCHING = "Crunching ";
    private static final String EXTRACTING = "extracting ";
    private static final String CREATING = "creating ";
    private static final String PROCESSING = "Processing ";

    /**
     * The Session object representing the current user session, used for authentication and access control during compression
     * operations. This session is typically passed to archive handling classes to ensure that operations are performed with the
     * appropriate permissions and context.
     */
    private final Session session;
    /**
     * An AtomicInteger used to track the count of processed files during batch compression operations. This counter is typically
     * incremented as each file is processed, allowing for accurate progress tracking and updates in the user interface.
     */
    private final AtomicInteger cnt;
    /**
     * The total number of files to be processed during batch compression operations. This value is used in conjunction with the cnt
     * AtomicInteger to calculate and display progress updates, providing users with feedback on the overall progress of the
     * compression tasks.
     */
    private final int total;
    /**
     * The ProgressHandler instance used for updating the progress of compression operations. This handler is typically used to send
     * progress updates to the user interface, allowing users to see the current status of ongoing compression tasks and providing
     * feedback on the completion of each file being processed.
     */
    private final ProgressHandler progress;

    /**
     * An array of supported file extensions for compression operations. This array lists the file extensions that are recognized
     * and can be processed by the compressor, such as "zip", "7z", "rar", "tar", etc. The compressor may use this array to
     * determine which files are eligible for compression or conversion based on their extensions.
     * 
     * @return the array of supported file extensions for compression operations
     */
    protected static final @Getter String[] extensions = { "zip", "7z", "rar", "arj", "tar", "lzh", "lha", "tgz", "tbz", "tbz2", "rpm", "iso", "deb", "cab" };

    /**
     * A data class representing the result of a file compression operation. This class contains a Path object representing the file
     * that was processed and a String representing the result of the operation (e.g., "OK", "Failed", or an error message). The
     * FileResult class is used to encapsulate the outcome of a compression task for a specific file, allowing for easy tracking and
     * reporting of results in batch operations.
     */
    public static @Data class FileResult {
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        private final Object lock = new Object();

        /**
         * The Path object representing the file that was processed during the compression operation. This path typically points to
         * the file that was compressed or converted, allowing for easy reference to the file in subsequent operations or for
         * reporting purposes.
         * 
         * @param file the Path object representing the file that was processed during the compression operation
         * 
         * @return the Path object representing the file that was processed during the compression operation
         */
        private Path file;
        /**
         * A String representing the result of the compression operation for the associated file. This string typically indicates
         * whether the operation was successful ("OK"), failed ("Failed"), or contains an error message if an issue occurred during
         * processing. The result field allows for easy tracking and reporting of the outcome of compression tasks for each file in
         * batch operations.
         * 
         * @param result a String representing the result of the compression operation (e.g., "OK", "Failed", or an error message)
         * 
         * @return a String representing the result of the compression operation for the associated file
         */
        private String result = "";

        /**
         * Constructs a new FileResult instance with the specified file path. This constructor initializes the file field with the
         * provided Path object and leaves the result field as an empty string, allowing for later assignment of the operation
         * result after processing the file.
         * 
         * @param file the Path object representing the file that was processed during the compression operation
         */
        public FileResult(Path file) {
            this.file = file;
        }

        /**
         * Updates the result text under this instance's lock and returns the stored value.
         *
         * @param txt the result text to store
         * @return the stored result
         */
        public String applyResult(String txt) {
            synchronized (lock) {
                setResult(txt);
                return getResult();
            }
        }
    }

    /**
     * Constructs a new Compressor instance with the specified session, count, total, and progress handler. This constructor
     * initializes the compressor with the necessary context for performing compression operations, including the user session for
     * authentication, the count and total for tracking progress in batch operations, and the progress handler for sending updates
     * to the user interface.
     * 
     * @param session the Session object representing the current user session, used for authentication and access control during
     *        compression operations
     * @param cnt an AtomicInteger used to track the count of processed files during batch compression operations
     * @param total the total number of files to be processed during batch compression operations
     * @param progress the ProgressHandler instance used for updating the progress of compression operations
     */
    public Compressor(Session session, AtomicInteger cnt, int total, ProgressHandler progress) {
        this.session = session;
        this.cnt = cnt;
        this.total = total;
        this.progress = progress;
    }

    /**
     * Calculates the total size of the files contained within the specified path. This method uses a file tree walk to traverse the
     * directory structure starting from the given path, summing the sizes of all files encountered. It handles any IOExceptions
     * that may occur during the file tree walk and returns the total size in bytes as a long value.
     * 
     * @param path the Path object representing the directory or file for which to calculate the total size
     * 
     * @return the total size of the files contained within the specified path, in bytes
     */
    @SuppressWarnings("unused")
    private static long size(Path path) {
        final var size = new AtomicLong(0);
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException _) {
            throw new AssertionError("walkFileTree will not throw IOException if the FileVisitor does not");
        }
        return size.get();
    }

    /**
     * Callback interface used to report status updates or operation results during compression, format conversion, or validation
     * tasks.
     */
    public interface UpdResultCallBack {
        /**
         * Applies the result update with the provided text.
         * 
         * @param txt the result update text
         */
        void apply(String txt);
    }

    /**
     * A callback interface for updating the source file after a successful compression operation.
     * <p>
     * This interface defines a single method, apply, which takes a File object as a parameter. The apply method is intended to be
     * implemented by classes that need to perform actions on the source file (e.g., moving, deleting, or renaming) after the
     * compression operation has completed successfully. By using this callback mechanism, the compressor can notify other
     * components of the application about the updated source file, allowing for further processing or cleanup as needed after
     * compression tasks.
     */
    public interface UpdSrcCallBack {
        /**
         * Applies the source file update with the provided File object.
         * 
         * @param file the File object representing the updated source file after a successful compression operation
         */
        void apply(File file);
    }

    /**
     * Functional interface for the actual archive conversion logic, used by {@link #convertArchive} as a strategy callback.
     */
    @FunctionalInterface
    interface ArchiveConverter {
        /**
         * Performs the archive conversion from the source file to the temporary file.
         *
         * @param file the source archive file
         * @param tmpfile the temporary path where the new archive is written
         * @param newfile the final destination file, used for progress reporting
         * 
         * @return {@code true} if the conversion succeeded; {@code false} otherwise
         * 
         * @throws IOException if an I/O error occurs during conversion
         */
        boolean convert(File file, Path tmpfile, File newfile) throws IOException;
    }

    /**
     * Template method that encapsulates the common archive conversion workflow: create a temporary file, invoke the supplied
     * conversion strategy, and finalize by replacing the original file.
     * <p>
     * This eliminates duplicated boilerplate across the various {@code *2*} public methods.
     *
     * @param file the source archive file to convert
     * @param extension the target file extension (without the dot), e.g. {@code "7z"} or {@code "zip"}
     * @param cb the callback to report status updates or operation results
     * @param scb the callback to update the reference to the newly created source file
     * @param converter the strategy that performs the actual conversion
     * 
     * @return the new {@link File} if the conversion is successful; {@code null} otherwise
     */
    private File convertArchive(final File file, final String extension, final UpdResultCallBack cb, final UpdSrcCallBack scb, final ArchiveConverter converter) {
        try {
            cb.apply(PROCESSING + file.getName());
            var tmpfile = IOUtils.createTempFile("JRM", "." + extension);
            Files.delete(tmpfile);
            var newfile = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + "." + extension);
            if (converter.convert(file, tmpfile, newfile) && Files.exists(tmpfile)) {
                return finalizeTmpFile(file, cb, scb, tmpfile, newfile);
            }
        } catch (IOException _) {
            cb.apply(FAILED);
        } finally {
            progress.setProgress("", null, null, "");
        }
        return null;
    }

    /**
     * Creates and configures {@link ZipParameters} based on the user's configured ZIP compression level.
     *
     * @return a new {@link ZipParameters} instance with the compression method and level set according to user settings
     */
    private ZipParameters configureZipParameters() {
        var zipp = new ZipParameters();
        var level = ZipLevel.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.zip_compression_level));
        switch (level) {
            case STORE -> zipp.setCompressionMethod(CompressionMethod.STORE);
            case FASTEST -> zipp.setCompressionLevel(CompressionLevel.FASTEST);
            case FAST -> zipp.setCompressionLevel(CompressionLevel.FAST);
            case NORMAL -> zipp.setCompressionLevel(CompressionLevel.NORMAL);
            case MAXIMUM -> zipp.setCompressionLevel(CompressionLevel.MAXIMUM);
            case ULTRA -> zipp.setCompressionLevel(CompressionLevel.ULTRA);
            default -> zipp.setCompressionLevel(CompressionLevel.NORMAL);
        }
        return zipp;
    }

    /**
     * Converts a 7-Zip archive to another 7-Zip archive format. This method extracts the contents of the original 7-Zip archive,
     * creates a new 7-Zip archive with the extracted contents, and replaces the original file with the new archive if the operation
     * is successful. It uses temporary files to manage the intermediate steps of extraction and creation, and provides progress
     * updates through the provided callback interfaces. If any step of the process fails, it reports the failure through the result
     * callback and ensures that temporary files are cleaned up appropriately.
     * 
     * @param file the File object representing the original 7-Zip archive to be converted
     * @param cb the UpdResultCallBack instance used for reporting status updates or operation results during the conversion process
     * @param scb the UpdSrcCallBack instance used for updating the source file after a successful conversion operation
     * 
     * @return a File object representing the new 7-Zip archive if the conversion is successful, or null if it fails
     */
    public File sevenZip2SevenZip(final File file, final UpdResultCallBack cb, final UpdSrcCallBack scb) {
        return convertArchive(file, "7z", cb, scb, (f, t, n) -> sevenZip2SevenZip(f, cb, t, n));
    }

    /**
     * Finalizes the compression or format conversion process by replacing the original source file with the newly created temporary
     * file.
     * <p>
     * This method attempts to delete the original file, moves the temporary file to its final destination, and notifies the caller
     * of the success or failure of the operation via the provided callbacks.
     *
     * @param file the original file being processed and replaced
     * @param cb the callback to report the final operation status (e.g., "OK" or "Failed")
     * @param scb the callback to update the reference to the newly created source file
     * @param tmpfile the path to the temporary file containing the processed archive
     * @param newfile the final destination file for the new archive
     * 
     * @return the final destination {@link File} if successful; {@code null} otherwise
     * 
     * @throws IOException if an I/O error occurs during the file move operation
     */
    private File finalizeTmpFile(final File file, final UpdResultCallBack cb, final UpdSrcCallBack scb, final Path tmpfile, final File newfile) throws IOException {
        if (FileUtils.deleteQuietly(file)) {
            FileUtils.moveFile(tmpfile.toFile(), newfile);
            scb.apply(newfile);
            cb.apply(OK);
            return newfile;
        } else
            cb.apply(FAILED);
        return null;
    }

    /**
     * Converts a 7-Zip archive to another 7-Zip archive format. This method extracts the contents of the original 7-Zip archive,
     * creates a new 7-Zip archive with the extracted contents, and replaces the original file with the new archive if the operation
     * is successful. It uses temporary files to manage the intermediate steps of extraction and creation, and provides progress
     * updates through the provided callback interfaces. If any step of the process fails, it reports the failure through the result
     * callback and ensures that temporary files are cleaned up appropriately.
     * 
     * @param file the File object representing the original 7-Zip archive to be converted
     * @param cb the UpdResultCallBack instance used for reporting status updates or operation results during the conversion process
     * @param tmpfile the Path object representing the temporary file used for creating the new 7-Zip archive
     * @param newfile the File object representing the final destination for the new 7-Zip archive
     * 
     * @return true if the conversion is successful; false otherwise
     * 
     * @throws IOException if an I/O error occurs during extraction or creation of archives
     */
    private boolean sevenZip2SevenZip(final File file, final UpdResultCallBack cb, final Path tmpfile, final File newfile) throws IOException {
        try (final var archive = new SevenZipArchive(session, file, true, new ProgressNarchiveCallBack(progress))) {
            progress.setProgress(toDocument(EXTRACTING + toItalicBlack(escape(file.getName()))), cnt.get(), total);
            if (archive.extract() == 0) {
                try (final var newarchive = new SevenZipArchive(session, tmpfile.toFile(), new ProgressNarchiveCallBack(progress))) {
                    final var basedir = archive.getTempDir().toPath();
                    Files.walkFileTree(basedir, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                            newarchive.addStdIn(Files.newInputStream(file), basedir.relativize(file).toString());
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    progress.setProgress(toDocument(CRUNCHING + toItalicBlack(escape(newfile.getName()))), cnt.get(), total);
                }
            } else {
                Files.deleteIfExists(tmpfile);
                cb.apply("extract failed");
                return false;
            }
        } catch (Exception _) {
            Files.deleteIfExists(tmpfile);
            cb.apply("7z creation failed");
            return false;
        }
        return true;
    }

    /**
     * Converts a 7-Zip archive to a ZIP archive format. This method extracts the contents of the original 7-Zip archive, creates a
     * new ZIP archive with the extracted contents, and replaces the original file with the new archive if the operation is
     * successful. It uses temporary files to manage the intermediate steps of extraction and creation, and provides progress
     * updates through the provided callback interfaces. If any step of the process fails, it reports the failure through the result
     * callback and ensures that temporary files are cleaned up appropriately.
     * 
     * @param file the File object representing the original 7-Zip archive to be converted
     * @param tzip a boolean flag indicating whether to use TorrentZip for creating the ZIP archive (true) or standard ZIP creation
     *        (false)
     * @param cb the UpdResultCallBack instance used for reporting status updates or operation results during the conversion process
     * @param scb the UpdSrcCallBack instance used for updating the source file after a successful conversion operation
     * 
     * @return a File object representing the new ZIP archive if the conversion is successful, or null if it fails
     */
    public File sevenZip2Zip(final File file, final boolean tzip, final UpdResultCallBack cb, final UpdSrcCallBack scb) {
        return convertArchive(file, "zip", cb, scb, (f, t, n) -> sevenZip2Zip(f, tzip, cb, t, n));
    }

    /**
     * Converts a 7-Zip archive to a ZIP archive by extracting its contents and re-compressing them.
     * <p>
     * This helper method extracts the source 7-Zip archive to a temporary directory, configures ZIP parameters based on user
     * settings, and compresses the extracted files into the specified temporary ZIP path.
     *
     * @param file the source 7-Zip archive file to be converted
     * @param tzip a flag indicating whether to apply Torrentzip constraints (reserved for future use)
     * @param cb the callback used to report status or conversion errors
     * @param tmpfile the path to the temporary file where the ZIP archive is constructed
     * @param newfile the final target ZIP file, used for progress reporting
     * 
     * @return {@code true} if the archive was successfully extracted and compressed; {@code false} otherwise
     * 
     * @throws IOException if an I/O error occurs during extraction or ZIP creation
     */
    private boolean sevenZip2Zip(final File file, final boolean tzip /* NOSONAR */, final UpdResultCallBack cb, final Path tmpfile, final File newfile) throws IOException {
        try (final var archive = new SevenZipArchive(session, file, false, new ProgressNarchiveCallBack(progress))) {
            progress.setProgress(toDocument(EXTRACTING + toItalicBlack(escape(file.getName()))), cnt.get(), total);
            if (archive.extract() == 0) {
                final File basedir = archive.getTempDir();

                final var zipp = configureZipParameters();
                FileUtils.forceMkdirParent(tmpfile.toFile());
                progress.setProgress(toDocument(CREATING + toItalicBlack(escape(newfile.getName()))), cnt.get(), total);
                try (final var dstzipf = new ZipFile(tmpfile.toFile())) {
                    Files.walkFileTree(basedir.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            final var zippf = new ZipParameters(zipp);
                            zippf.setFileNameInZip(basedir.toPath().relativize(file).toString());
                            dstzipf.addFile(file.toFile(), zippf);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }

            } else {
                Files.deleteIfExists(tmpfile);
                cb.apply("extract failed");
                return false;
            }
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            Files.deleteIfExists(tmpfile);
            cb.apply("zip creation failed");
            return false;
        }
        return true;
    }

    /**
     * Re-compresses a ZIP archive to apply the user's configured ZIP compression level.
     * <p>
     * This method extracts the entries of the original ZIP file and adds them to a new temporary ZIP archive using the configured
     * {@link ZipLevel}. If successful, it replaces the original file with the newly generated archive.
     *
     * @param file the source ZIP archive to be re-compressed
     * @param cb the callback used to report status or compression errors
     * @param scb the callback used to update the reference to the newly created source file
     * 
     * @return the updated {@link File} representing the new ZIP archive if successful; {@code null} otherwise
     */
    public File zip2Zip(final File file, final UpdResultCallBack cb, final UpdSrcCallBack scb) {
        return convertArchive(file, "zip", cb, scb, (f, t, n) -> zip2ZipHelper(f, cb, t, n));
    }

    /**
     * Helper method that extracts entries from the source ZIP archive and re-compresses them into a new temporary ZIP file
     * using the user-configured compression level.
     *
     * @param file the source ZIP archive to be re-compressed
     * @param cb the callback used to report status or compression errors
     * @param tmpfile the path to the temporary file where the new ZIP archive is constructed
     * @param newfile the final target ZIP file, used for progress reporting
     * 
     * @return {@code true} if the re-compression succeeded; {@code false} otherwise
     * 
     * @throws IOException if an I/O error occurs during extraction or ZIP creation
     */
    private boolean zip2ZipHelper(final File file, final UpdResultCallBack cb, final Path tmpfile, final File newfile) throws IOException {
        try (final var srcarchive = new ZipArchive(session, file, true, new ProgressNarchiveCallBack(progress)); final var dstzipf = new ZipFile(tmpfile.toFile())) {
            final var zipp = configureZipParameters();
            progress.setProgress(toDocument(CRUNCHING + toItalicBlack(escape(newfile.getName()))), cnt.get(), total);
            srcarchive.extractCustom(new CustomVisitor() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                    final var zippf = new ZipParameters(zipp);
                    zippf.setFileNameInZip(getSourcePath().relativize(file).toString());
                    dstzipf.addStream(Files.newInputStream(file), zippf);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            Files.deleteIfExists(tmpfile);
            cb.apply("zip creation failed");
            return false;
        }
        return true;
    }

    /**
     * Converts a ZIP archive to a 7-Zip archive format.
     * <p>
     * This method extracts the entries of the original ZIP file and adds them to a new temporary 7-Zip archive. If successful, it
     * replaces the original ZIP file with the newly generated 7-Zip archive.
     *
     * @param file the source ZIP archive to be converted
     * @param cb the callback used to report status or conversion errors
     * @param scb the callback used to update the reference to the newly created source file
     * 
     * @return the updated {@link File} representing the new 7-Zip archive if successful; {@code null} otherwise
     */
    public File zip2SevenZip(final File file, final UpdResultCallBack cb, final UpdSrcCallBack scb) {
        return convertArchive(file, "7z", cb, scb, (f, t, n) -> zip2SevenZip(f, cb, t, n));
    }

    /**
     * Helper method to convert a ZIP archive to a 7-Zip archive by extracting its contents and writing them to a temporary 7-Zip
     * file.
     *
     * @param file the source ZIP archive to be converted
     * @param cb the callback to report status or conversion errors
     * @param tmpfile the path to the temporary 7-Zip file to be created
     * @param newfile the final destination file, used for progress feedback
     * 
     * @return {@code true} if the archive was successfully extracted and converted; {@code false} otherwise
     * 
     * @throws IOException if an I/O error occurs during extraction or 7-Zip creation
     */
    private boolean zip2SevenZip(final File file, final UpdResultCallBack cb, final Path tmpfile, final File newfile) throws IOException {
        try (final var archive = new SevenZipArchive(session, tmpfile.toFile(), null)) {
            progress.setProgress(toDocument(EXTRACTING + toItalicBlack(escape(file.getName()))), cnt.get(), total);
            try (final var srcarchive = new ZipArchive(session, file, true, new ProgressNarchiveCallBack(progress));) {
                srcarchive.extractCustom(new CustomVisitor() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                        archive.addStdIn(Files.newInputStream(file), getSourcePath().relativize(file).toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            progress.setProgress(toDocument(CRUNCHING + toItalicBlack(escape(newfile.getName()))), cnt.get(), total);
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            Files.deleteIfExists(tmpfile);
            cb.apply("7z creation failed");
            return false;
        }
        return true;
    }

    /**
     * Processes a ZIP file to convert or validate it as a TorrentZip archive.
     * <p>
     * This method checks if the file is a valid TorrentZip. If not, it attempts to re-align and re-compress it according to the
     * TorrentZip specification.
     *
     * @param file the ZIP file to process
     * @param force {@code true} to force re-processing even if the archive already appears to be TorrentZip-compatible
     * @param cb the callback to report status updates or operation results
     * 
     * @return the processed TorrentZip {@link File} if successful; {@code null} otherwise
     */
    public File zip2TZip(final File file, final boolean force, final UpdResultCallBack cb) {
        try {
            progress.setProgress(toDocument("TorrentZipping " + toItalicBlack(escape(file.getName()))), cnt.get(), total);
            cb.apply(PROCESSING + file.getName());
            final Set<TrrntZipStatus> status = new TorrentZip(new ProgressTZipCallBack(progress), new SimpleTorrentZipOptions(force, false)).process(file);
            if (status.contains(TrrntZipStatus.VALIDTRRNTZIP)) {
                cb.apply(OK);
                return file;
            }
            cb.apply(status.toString());
        } catch (IOException _) {
            cb.apply(FAILED);
        } finally {
            progress.setProgress("", null, null, "");
        }
        return null;
    }

    /**
     * Compresses or converts the specified file to the target compression format.
     * <p>
     * This method routes the file to the appropriate specialized compression logic (ZIP, 7-Zip, or TorrentZip) based on the
     * requested format.
     *
     * @param format the target {@link CompressorFormat} (e.g., SEVENZIP, ZIP, or TZIP)
     * @param file the source file to be compressed or converted
     * @param force {@code true} to force re-compression or conversion even if the archive already matches the target format
     * @param cb the callback to report status updates or operation results
     * @param scb the callback to update the reference to the newly created source file
     */
    public void compress(final CompressorFormat format, final File file, final boolean force, final UpdResultCallBack cb, final UpdSrcCallBack scb) {
        switch (format) {
            case SEVENZIP -> compressToSevenZip(file, force, cb, scb);
            case ZIP -> compressToZip(file, force, cb, scb);
            case TZIP -> compressToTZip(file, force, cb, scb);
        }
    }

    /**
     * Compresses or converts the specified file to a TZip archive format.
     * <p>
     * This helper method determines the current format of the file based on its extension and routes it to the appropriate
     * conversion logic (e.g., ZIP to TZip, or 7-Zip to ZIP then TZip).
     *
     * @param file the source file to be compressed or converted
     * @param force {@code true} to force re-compression if the file is already a ZIP archive; {@code false} to skip it
     * @param cb the callback to report status updates or operation results
     * @param scb the callback to update the reference to the newly created source file
     * 
     * @throws IllegalArgumentException if an invalid argument is provided during conversion
     */
    private void compressToTZip(final File file, final boolean force, final UpdResultCallBack cb, final UpdSrcCallBack scb) throws IllegalArgumentException {
        if ("zip".equals(FilenameUtils.getExtension(file.getName())))
            zip2TZip(file, force, cb);
        else
            Optional.ofNullable(sevenZip2Zip(file, true, cb, scb)).filter(File::exists).ifPresent(f -> zip2TZip(f, force, cb));
    }

    /**
     * Compresses or converts the specified file to a ZIP archive format.
     * <p>
     * This helper method determines the current format of the file based on its extension and routes it to the appropriate
     * conversion logic (e.g., 7-Zip to ZIP, or ZIP to ZIP if forced).
     *
     * @param file the source file to be compressed or converted
     * @param force {@code true} to force re-compression if the file is already a ZIP archive; {@code false} to skip it
     * @param cb the callback to report status updates or operation results
     * @param scb the callback to update the reference to the newly created source file
     * 
     * @throws IllegalArgumentException if an invalid argument is provided during conversion
     */
    private void compressToZip(final File file, final boolean force, final UpdResultCallBack cb, final UpdSrcCallBack scb) throws IllegalArgumentException {
        if ("zip".equals(FilenameUtils.getExtension(file.getName()))) {
            if (force)
                zip2Zip(file, cb, scb);
            else
                cb.apply("Skipped");
        } else
            sevenZip2Zip(file, false, cb, scb);
    }

    /**
     * Compresses or converts the specified file to a 7-Zip archive format.
     * <p>
     * This helper method determines the current format of the file based on its extension and routes it to the appropriate
     * conversion logic (e.g., ZIP to 7-Zip, or 7-Zip to 7-Zip if forced).
     *
     * @param file the source file to be compressed or converted
     * @param force {@code true} to force re-compression if the file is already a 7-Zip archive; {@code false} to skip it
     * @param cb the callback to report status updates or operation results
     * @param scb the callback to update the reference to the newly created source file
     * 
     * @throws IllegalArgumentException if an invalid argument is provided during conversion
     */
    private void compressToSevenZip(final File file, final boolean force, final UpdResultCallBack cb, final UpdSrcCallBack scb) throws IllegalArgumentException {
        switch (FilenameUtils.getExtension(file.getName())) {
            case "zip" -> zip2SevenZip(file, cb, scb);
            case "7z" -> {
                if (force)
                    sevenZip2SevenZip(file, cb, scb);
                else
                    cb.apply("Skipped");
            }
            default -> sevenZip2SevenZip(file, cb, scb);
        }
    }
}
