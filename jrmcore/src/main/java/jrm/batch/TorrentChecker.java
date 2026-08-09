package jrm.batch;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.progress.ProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.batch.TrntChkReport.Child;
import jrm.batch.TrntChkReport.Status;
import jrm.io.torrent.Torrent;
import jrm.io.torrent.TorrentException;
import jrm.io.torrent.TorrentFile;
import jrm.io.torrent.TorrentParser;
import jrm.io.torrent.options.TrntChkMode;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.SettingsEnum;
import jrm.misc.UnitRenderer;
import jrm.security.PathAbstractor;
import jrm.security.Session;

/**
 * * Torrent checker component to manage torrent file validity.
 * 
 * @param <T> the type of source-destination result to be processed, which must extend AbstractSrcDstResult. This allows the
 *        TorrentChecker to work with various types of results that contain source and destination information for torrent checking
 *        operations.
 */
public class TorrentChecker<T extends AbstractSrcDstResult> implements UnitRenderer, StatusRendererFactory {
    /** the message key for piece progression during torrent checking */
    private static final String TORRENT_CHECKER_PIECE_PROGRESSION = "TorrentChecker.PieceProgression";
    /** the message key for indicating that the torrent check is complete */
    private static final String TORRENT_CHECKER_RESULT_COMPLETE = "TorrentChecker.ResultComplete";

    /** Atomic integer to track the number of pieces currently being processed */
    private final AtomicInteger processing = new AtomicInteger();
    /** Atomic integer to track the current piece being processed */
    private final AtomicInteger current = new AtomicInteger();
    /** the active user session */
    private final Session session;
    /** the set of options to control the behavior of the torrent checker */
    private final Set<Options> options;
    /** the mode of checking to be performed (e.g., filename, file size, SHA1) */
    private final TrntChkMode mode;

    /**
     * Enumeration of options for the torrent checker, including removing unknown files, removing wrong sized files, and detecting
     * archived folders.
     */
    public enum Options {
        /** Option to remove files that are not listed in the torrent file */
        REMOVEUNKNOWNFILES,
        /**
         * Option to remove files that have a size different from what is specified in the torrent file
         */
        REMOVEWRONGSIZEDFILES,
        /**
         * Option to detect folders that are likely to be archives based on the torrent file structure
         */
        DETECTARCHIVEDFOLDERS;
    }

    /**
     * Constructs a TorrentChecker with the specified parameters.
     *
     * @param session the active user session
     * @param progress the handler for reporting progress during the torrent checking process
     * @param sdrl the list of source-destination results to process
     * @param mode the mode of checking to be performed (e.g., filename, file size, SHA1)
     * @param updater the interface for updating results in the user interface
     * @param options the set of options to control the behavior of the torrent checker
     */
    public TorrentChecker(final Session session, final ProgressHandler progress, List<T> sdrl, TrntChkMode mode, ResultColUpdater updater, Set<Options> options) {
        this.session = session;
        this.options = options;
        this.mode = mode;
        progress.setInfos(Math.min(Runtime.getRuntime().availableProcessors(), (int) sdrl.stream().filter(AbstractSrcDstResult::isSelected).count()), true);
        progress.setProgress2("", 0, 1); //$NON-NLS-1$
        sdrl.stream().filter(AbstractSrcDstResult::isSelected).forEach(sdr -> updater.updateResult(sdrl.indexOf(sdr), ""));
        final var use_parallelism = session.getUser().getSettings().getProperty(SettingsEnum.use_parallelism, Boolean.class);
        final var nThreads = Boolean.TRUE.equals(use_parallelism) ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;
        try (final var mt = new MultiThreadingVirtual<T>("torrent-checker", progress, nThreads, sdr -> {
            if (progress.isCancel())
                return;
            try {
                final int row = sdrl.indexOf(sdr);
                updater.updateResult(row, "In progress...");
                final String result = check(progress, sdr);
                updater.updateResult(row, result);
                progress.setProgress(null, -1, null, "");
            } catch (IOException | TorrentException e) {
                Log.err(e.getMessage(), e);
            }
        })) {
            mt.start(sdrl.stream().filter(AbstractSrcDstResult::isSelected));
        }
    }

    /**
     * Performs a verification check on the specified source-destination result. It parses the torrent file, optionally detects and
     * extracts archives, and verifies the files using either simple file-level checks or piece-by-piece SHA-1 hashing.
     *
     * @param progress the progress handler for reporting verification progress
     * @param sdr the source-destination result containing the torrent source and destination paths
     * 
     * @return a string message summarizing the results of the check operation
     * 
     * @throws IOException if an I/O error occurs during file checking or reading
     * @throws TorrentException if an error occurs while parsing the torrent file
     */
    String check(final ProgressHandler progress, final T sdr) throws IOException, TorrentException {
        if (sdr.getSrc() == null || sdr.getDst() == null)
            return sdr.getSrc() == null ? session.getMsgs().getString("TorrentChecker.SrcNotDefined") : session.getMsgs().getString("TorrentChecker.DstNotDefined"); //$NON-NLS-1$ //$NON-NLS-2$
        var result = ""; //$NON-NLS-1$
        final var src = PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile();
        final var dst = PathAbstractor.getAbsolutePath(session, sdr.getDst()).toFile();
        if (!src.exists() || !dst.exists())
            return src.exists() ? session.getMsgs().getString("TorrentChecker.DstMustExist") : session.getMsgs().getString("TorrentChecker.SrcMustExist"); //$NON-NLS-1$ //$NON-NLS-2$

        final var report = new TrntChkReport(src);
        final var torrent = TorrentParser.parseTorrent(src.getAbsolutePath());
        final List<TorrentFile> tfiles = torrent.getFileList();
        detectArchives(sdr, tfiles, options.contains(Options.DETECTARCHIVEDFOLDERS));
        if (mode != TrntChkMode.SHA1)
            result = checkFiles(progress, sdr, src, dst, report, tfiles);
        else
            result = checkBlocks(progress, sdr, src, dst, report, torrent, tfiles);
        report.save(session, report.getReportFile(session));
        return result;
    }

    /**
     * Data container for storing state and accumulated statistics during file-level torrent verification.
     */
    private class CheckFilesData {
        /** Counter for the number of files that are correctly verified */
        int ok = 0;
        /** Counter for the total number of files to be checked */
        long missingBytes = 0L;
        /** Counter for the number of files that are missing */
        int missingFiles = 0;
        /**
         * Counter for the number of files that have a size different from what is specified in the torrent file
         */
        int wrongSizedFiles = 0;
        /** Set of paths that are expected to be present based on the torrent file */
        final Set<Path> paths = new HashSet<>();
        /**
         * Total number of files to be checked, initialized based on the size of the torrent file list
         */
        final int total;

        /**
         * Constructs a CheckFilesData instance and initializes the total number of files to be checked based on the provided list
         * of torrent files.
         *
         * @param tfiles the list of torrent files to be checked
         */
        public CheckFilesData(final List<TorrentFile> tfiles) {
            total = tfiles.size();
        }
    }

    /**
     * Performs file-level verification of the files specified in the torrent file against the files present in the destination
     * directory. It updates the progress handler with the current status and accumulates statistics on the number of files that are
     * correctly verified, missing, or have size mismatches. It also handles the removal of unknown files if the corresponding
     * option is enabled.
     *
     * @param progress the progress handler for reporting verification progress
     * @param sdr the source-destination result containing the torrent source and destination paths
     * @param src the source file representing the torrent file
     * @param dst the destination directory where the files should be located
     * @param report the report object for recording verification results
     * @param tfiles the list of torrent files to be checked
     * 
     * @return a string message summarizing the results of the file-level check
     * 
     * @throws IOException if an I/O error occurs during file checking or reading
     */
    private String checkFiles(final ProgressHandler progress, final T sdr, final File src, final File dst, final TrntChkReport report, final List<TorrentFile> tfiles)
            throws IOException {
        String result;
        CheckFilesData data = new CheckFilesData(tfiles);

        processing.addAndGet(data.total);
        for (var j = 0; j < data.total; j++) {
            TorrentFile tfile = tfiles.get(j);
            checkFilesFile(data, src, dst, tfile, report, progress);
            if (progress.isCancel())
                return "Cancelled...";
        }
        int removedFiles = removeUnknownFiles(report, data.paths, sdr, options.contains(Options.REMOVEUNKNOWNFILES) && !progress.isCancel());
        if (data.ok == data.total) {
            if (removedFiles > 0)
                result = toDocument(toBoldBlue(session.getMsgs().getString(TORRENT_CHECKER_RESULT_COMPLETE)));
            else
                result = toDocument(toBoldGreen(session.getMsgs().getString(TORRENT_CHECKER_RESULT_COMPLETE)));
        } else if (mode == TrntChkMode.FILENAME)
            result = String.format(session.getMsgs().getString("TorrentChecker.ResultFileName"), data.ok * 100.0 / data.total, data.missingFiles, removedFiles); //$NON-NLS-1$
        else
            result = String.format(session.getMsgs().getString("TorrentChecker.ResultFileSize"), data.ok * 100.0 / data.total, humanReadableByteCount(data.missingBytes, false), //$NON-NLS-1$
                    data.wrongSizedFiles, removedFiles);
        return result;
    }

    /**
     * Checks the existence and size of a single file specified in the torrent file against the corresponding file in the
     * destination directory. It updates the progress handler with the current status and accumulates statistics on the number of
     * files that are correctly verified, missing, or have size mismatches. It also handles the removal of wrong sized files if the
     * corresponding option is enabled.
     *
     * @param data the CheckFilesData object for accumulating verification statistics
     * @param src the source file representing the torrent file
     * @param dst the destination directory where the files should be located
     * @param tfile the TorrentFile object representing the file to be checked
     * @param report the report object for recording verification results
     * @param progress the progress handler for reporting verification progress
     * 
     * @throws IOException if an I/O error occurs during file checking or reading
     */
    private void checkFilesFile(CheckFilesData data, final File src, final File dst, TorrentFile tfile, final TrntChkReport report, final ProgressHandler progress)
            throws IOException {
        current.incrementAndGet();
        var file = dst.toPath();
        for (String path : tfile.getFileDirs())
            file = file.resolve(path);
        data.paths.add(file.toAbsolutePath());
        final var identity = Paths.get(".");
        final Child node = report.add(tfile.getFileDirs().stream().map(Paths::get).reduce(identity, Path::resolve).toString());
        progress.setProgress(toDocument(toPurple(src.getAbsolutePath())), -1, null, file.toString());
        progress.setProgress2(current + "/" + processing, current.get(), processing.get()); //$NON-NLS-1$
        if (Files.exists(file)) {
            if (mode == TrntChkMode.FILENAME || Files.size(file) == (node.getData().setLength(tfile.getFileLength()).getLength())) {
                data.ok++;
                node.setStatus(Status.OK);
            } else {
                if (options.contains(Options.REMOVEWRONGSIZEDFILES))
                    Files.delete(file);
                data.wrongSizedFiles++;
                data.missingBytes += (node.getData().setLength(tfile.getFileLength()).getLength());
                node.setStatus(Status.SIZE);
            }
        } else {
            if (mode == TrntChkMode.FILENAME)
                data.missingFiles++;
            else
                data.missingBytes += (node.getData().setLength(tfile.getFileLength()).getLength());
            node.setStatus(Status.MISSING);
        }
    }

    /**
     * Data container for storing state, buffers, hash digests, and statistics during piece-by-piece SHA-1 block verification of
     * torrent files.
     */
    private class CheckBlocksData {
        /** the current block being processed in the report */
        Child block;
        /** the current child node representing the file being processed */
        Child node = null;
        /**
         * Counter for the number of bytes that are missing based on the piece verification
         */
        long missingBytes = 0L;
        /**
         * Counter for the number of bytes remaining to be read for the current piece
         */
        long toGo;
        /** Counter for the number of pieces that have been processed */
        int pieceCnt = 0;
        /**
         * Counter for the number of pieces that are valid according to their SHA-1 hash
         */
        int pieceValid = 0;
        /** Buffer for reading file data during hashing */
        final byte[] buffer = new byte[8192];
        /** the MessageDigest instance for computing SHA-1 hashes */
        final MessageDigest md;
        /**
         * Atomic boolean to indicate whether the current piece is valid based on file checks
         */
        final AtomicBoolean valid = new AtomicBoolean(true);
        /**
         * Counter for the number of files that have a size different from what is specified in the torrent file during piece
         * verification
         */
        final AtomicInteger wrongSizedFiles = new AtomicInteger();
        /**
         * Set of paths that are expected to be present based on the torrent file during piece verification
         */
        final Set<Path> paths = new HashSet<>();
        /** the length of each piece as specified in the torrent file */
        final long pieceLength;
        /** the list of SHA-1 hashes for each piece as specified in the torrent file */
        final List<String> pieces;

        /**
         * Constructs a CheckBlocksData instance and initializes the MessageDigest for SHA-1 hashing, the piece length, and the list
         * of piece hashes based on the provided torrent file.
         *
         * @param torrent the Torrent object containing the piece length and piece hashes
         * 
         * @throws NoSuchAlgorithmException if the SHA-1 algorithm is not available in the environment
         */
        public CheckBlocksData(Torrent torrent) throws NoSuchAlgorithmException {
            md = MessageDigest.getInstance("SHA-1"); // NOSONAR
            pieceLength = torrent.getPieceLength();
            pieces = torrent.getPieces();
        }
    }

    /**
     * Performs piece-by-piece SHA-1 block verification of the files specified in the torrent file against the files present in the
     * destination directory. It updates the progress handler with the current status and accumulates statistics on the number of
     * pieces that are valid, missing bytes, and files with size mismatches. It also handles the removal of unknown files if the
     * corresponding option is enabled.
     *
     * @param progress the progress handler for reporting verification progress
     * @param sdr the source-destination result containing the torrent source and destination paths
     * @param src the source file representing the torrent file
     * @param dst the destination directory where the files should be located
     * @param report the report object for recording verification results
     * @param torrent the Torrent object containing piece information for verification
     * @param tfiles the list of torrent files to be checked
     * 
     * @return a string message summarizing the results of the block-level check
     * 
     * @throws IOException if an I/O error occurs during file checking or reading
     */
    private String checkBlocks(final ProgressHandler progress, final T sdr, final File src, final File dst, final TrntChkReport report, final Torrent torrent,
            final List<TorrentFile> tfiles) {
        String result;
        try {
            final var data = new CheckBlocksData(torrent);

            data.toGo = data.pieceLength;
            processing.addAndGet(data.pieces.size());
            progress.setProgress(src.getAbsolutePath(), -1, null, ""); //$NON-NLS-1$
            progress.setProgress2(String.format(session.getMsgs().getString(TORRENT_CHECKER_PIECE_PROGRESSION), current.get(), processing.get()), -1, processing.get()); // $NON-NLS-1$
            data.pieceCnt++;
            data.block = report.add(String.format("Piece %d", data.pieceCnt));
            data.block.getData().setLength(data.pieceLength);
            for (TorrentFile tfile : tfiles) {
                checkBlocksFile(data, src, dst, tfile, report, progress);
                if (progress.isCancel())
                    return "cancelled...";
            }
            progress.setProgress2(String.format(session.getMsgs().getString(TORRENT_CHECKER_PIECE_PROGRESSION), current.get(), processing.get()), current.get(), processing.get()); // $NON-NLS-1$
            if (data.valid.get()) {
                if (Hex.encodeHexString(data.md.digest()).equalsIgnoreCase(data.pieces.get(data.pieceCnt - 1))) {
                    data.pieceValid++;
                    data.block.setStatus(Status.OK);
                } else
                    data.block.setStatus(Status.SHA1);
            } else {
                data.missingBytes += data.pieceLength - data.toGo;
                data.block.setStatus(Status.SKIPPED);
            }
            data.block.getData().setLength(data.pieceLength - data.toGo);
            Log.info(String.format("piece counted %d, given %d, valid %d, completion=%.02f%%%n", data.pieceCnt, data.pieces.size(), data.pieceValid, //$NON-NLS-1$
                    data.pieceValid * 100.0 / data.pieceCnt));
            Log.info(String.format("piece len : %d%n", data.pieceLength)); //$NON-NLS-1$
            Log.info(String.format("last piece len : %d%n", data.pieceLength - data.toGo)); //$NON-NLS-1$
            int removedFiles = removeUnknownFiles(report, data.paths, sdr, options.contains(Options.REMOVEUNKNOWNFILES) && !progress.isCancel());
            if (data.pieceValid == data.pieceCnt) {
                if (removedFiles > 0)
                    result = toDocument(toBoldBlue(session.getMsgs().getString(TORRENT_CHECKER_RESULT_COMPLETE)));
                else
                    result = toDocument(toBoldGreen(session.getMsgs().getString(TORRENT_CHECKER_RESULT_COMPLETE)));
            } else
                result = String.format(session.getMsgs().getString("TorrentChecker.ResultSHA1"), data.pieceValid * 100.0 / data.pieceCnt, //$NON-NLS-1$
                        humanReadableByteCount(data.missingBytes, false), data.wrongSizedFiles.get(), removedFiles);
        } catch (Exception ex) {
            result = ex.getMessage();
        }
        return result;
    }

    /**
     * Checks the blocks of a single file specified in the torrent file against the corresponding file in the destination directory
     * using piece-by-piece SHA-1 hashing. It updates the progress handler with the current status and accumulates statistics on the
     * number of pieces that are valid, missing bytes, and files with size mismatches. It also handles the removal of wrong sized
     * files if the corresponding option is enabled.
     *
     * @param data the CheckBlocksData object for accumulating verification statistics and state
     * @param src the source file representing the torrent file
     * @param dst the destination directory where the files should be located
     * @param tfile the TorrentFile object representing the file to be checked
     * @param report the report object for recording verification results
     * @param progress the progress handler for reporting verification progress
     * 
     * @throws IOException if an I/O error occurs during file checking or reading
     */
    private void checkBlocksFile(final CheckBlocksData data, final File src, final File dst, TorrentFile tfile, final TrntChkReport report, final ProgressHandler progress)
            throws IOException {
        var file = dst.toPath();
        for (String path : tfile.getFileDirs())
            file = file.resolve(path);
        data.paths.add(file.toAbsolutePath());
        final var identity = Paths.get(".");
        data.node = data.block.add(tfile.getFileDirs().stream().map(Paths::get).reduce(identity, Path::resolve).toString());
        try (BufferedInputStream in = getFileStram(options, data.wrongSizedFiles, data.node, data.valid, tfile, file)) {
            progress.setProgress(toDocument(toPurple(src.getAbsolutePath())), -1, null, file.toString());
            long flen = (data.node.getData().setLength(tfile.getFileLength()).getLength());
            while (flen >= data.toGo) {
                hashStream(data.md, data.buffer, in, data.toGo);
                flen -= data.toGo;
                data.toGo = data.pieceLength;
                progress.setProgress2(String.format(session.getMsgs().getString(TORRENT_CHECKER_PIECE_PROGRESSION), current.get(), processing.get()), current.get(),
                        processing.get()); // $NON-NLS-1$
                if (data.valid.get()) {
                    if (Hex.encodeHexString(data.md.digest()).equalsIgnoreCase(data.pieces.get(data.pieceCnt - 1))) {
                        data.pieceValid++;
                        data.block.setStatus(Status.OK);
                    } else
                        data.block.setStatus(Status.SHA1);
                } else {
                    data.missingBytes += data.pieceLength;
                    data.block.setStatus(Status.SKIPPED);
                }
                data.md.reset();
                data.pieceCnt++;
                data.block = report.add(String.format("Piece %d", data.pieceCnt));
                data.block.getData().setLength(data.pieceLength);
                data.node = data.block.add(data.node);
                current.incrementAndGet();
                data.valid.set(true);
                if (flen > 0) {
                    if (!Files.exists(file)) {
                        data.valid.set(false);
                        data.node.setStatus(Status.MISSING);
                    } else if (Files.size(file) != tfile.getFileLength()) {
                        data.valid.set(false);
                        data.node.setStatus(Status.SIZE);
                    }
                }
            }
            hashStream(data.md, data.buffer, in, flen);
            data.toGo -= flen;
        }
    }

    /**
     * Reads a specified number of bytes from the input stream and updates the message digest.
     *
     * @param md the MessageDigest instance to update
     * @param buffer the temporary buffer used to read and process stream chunks
     * @param in the input stream containing the file data to hash
     * @param toRead the number of bytes to read and hash
     * 
     * @throws IOException if an I/O error occurs while reading from the stream
     */
    private void hashStream(final MessageDigest md, final byte[] buffer, BufferedInputStream in, long toRead) throws IOException {
        if (in != null) {
            do {
                int len = in.read(buffer, 0, (int) (toRead < buffer.length ? toRead : buffer.length));
                md.update(buffer, 0, len);
                toRead -= len;
            } while (toRead > 0);
        }
    }

    /**
     * Opens a buffered input stream for the specified file after performing initial size and existence checks. If the file is
     * missing or has an incorrect size, the state and report node are updated, and null is returned.
     *
     * @param options the set of active checker options to determine behavior (e.g., removing wrong-sized files)
     * @param wrongSizedFiles the counter for tracking files with mismatched sizes
     * @param node the report node representing the file to update with checking status
     * @param valid the validation flag to be cleared if checks fail
     * @param tfile the expected torrent file information
     * @param file the path to the physical file on disk
     * 
     * @return a {@link BufferedInputStream} for the file, or {@code null} if the file is invalid or missing
     * 
     * @throws IOException if an I/O error occurs during file access or deletion
     */
    private BufferedInputStream getFileStram(Set<Options> options, AtomicInteger wrongSizedFiles, Child node, AtomicBoolean valid, TorrentFile tfile, Path file)
            throws IOException {
        if (!Files.exists(file)) {
            valid.set(false);
            node.setStatus(Status.MISSING);
        } else if (Files.size(file) != (node.getData().setLength(tfile.getFileLength())).getLength()) {
            if (options.contains(Options.REMOVEWRONGSIZEDFILES))
                Files.delete(file);
            wrongSizedFiles.incrementAndGet();
            node.setStatus(Status.SIZE);
            valid.set(false);
        } else
            return new BufferedInputStream(new FileInputStream(file.toFile()));
        return null;
    }

    /**
     * Removes files from the destination directory that are not listed in the torrent file. It walks through the destination
     * directory and collects files that are not present in the set of expected paths. It updates the report with the list of
     * unknown files and their sizes, and optionally deletes them from disk.
     *
     * @param report the report object for recording unknown files
     * @param paths the set of expected file paths based on the torrent file
     * @param sdr the source-destination result containing the torrent source and destination paths
     * @param remove a flag indicating whether to actually delete the unknown files
     * 
     * @return the number of unknown files that were found (and possibly removed)
     * 
     * @throws IOException if an I/O error occurs during file access or deletion
     */
    private int removeUnknownFiles(final TrntChkReport report, final Set<Path> paths, final T sdr, final boolean remove) throws IOException {
        final var filesToRemove = new ArrayList<Path>();
        final var dst = PathAbstractor.getAbsolutePath(session, sdr.getDst());
        Files.walkFileTree(dst, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (!paths.contains(file.toAbsolutePath()))
                    filesToRemove.add(file);
                return super.visitFile(file, attrs);
            }
        });
        final int count = filesToRemove.size();
        if (count > 0) {
            final Child lostfound = report.add("Unknown files");
            lostfound.getData().setLength(0L);
            for (final Path p : filesToRemove) {
                final Child entry = lostfound.add(Paths.get(".").resolve(dst.relativize(p)).toString());
                lostfound.getData().setLength(lostfound.getData().getLength() + (entry.getData().setLength(Files.size(p)).getLength()));
            }
            if (remove) {
                filesToRemove.forEach(t -> {
                    try {
                        Files.delete(t);
                    } catch (IOException _) {
                        // ignore
                    }
                });
            }
        }
        return count;
    }

    /**
     * Detects potential archive folders based on the structure of the torrent file and the presence of corresponding .zip files in
     * the destination directory. It identifies folders that are likely to be archives and optionally extracts them if the unarchive
     * flag is set.
     *
     * @param sdr the source-destination result containing the torrent source and destination paths
     * @param tfiles the list of torrent files to analyze for potential archive detection
     * @param unarchive a flag indicating whether to automatically extract detected archives
     */
    private void detectArchives(final T sdr, final List<TorrentFile> tfiles, final boolean unarchive) {
        final var components = new HashSet<String>();
        final var archives = new HashSet<Path>();
        final var dst = PathAbstractor.getAbsolutePath(session, sdr.getDst());
        for (var j = 0; j < tfiles.size(); j++) {
            final TorrentFile tfile = tfiles.get(j);
            final List<String> filedirs = tfile.getFileDirs();
            if (filedirs.size() > 1) {
                final String path = filedirs.get(0);
                if (!components.contains(path)) {
                    components.add(path);

                    Path file = dst;
                    file = file.resolve(path);

                    isArchive(archives, file);
                }
            }
        }
        for (var j = 0; j < tfiles.size(); j++) {
            TorrentFile tfile = tfiles.get(j);
            Path file = dst;
            for (final String path : tfile.getFileDirs())
                file = file.resolve(path);
            if (archives.contains(file))
                archives.remove(file);
        }
        for (Path archive : archives) {
            if (unarchive) {
                unarchive(archive);
            } else
                Log.debug(archive);
        }
    }

    /**
     * Checks if a corresponding .zip archive exists for the given file or directory path, and adds its path to the set of archives
     * if found.
     *
     * @param archives the set of discovered archive file paths
     * @param file the path of the file or folder to check for a matching archive
     */
    private void isArchive(final HashSet<Path> archives, Path file) {
        final Path parent = file.getParent();
        if (parent != null) {
            final Path filename = file.getFileName();
            if (filename != null) {
                final Path archive = parent.resolve(filename.toString() + ".zip");
                if (Files.exists(archive)) {
                    archives.add(archive);
                }
            }
        }
    }

    /**
     * Unarchives the specified zip file into a subdirectory with the same name.
     *
     * @param archive the path to the zip archive file to be extracted
     */
    private void unarchive(Path archive) {
        try {
            Path parent = archive.getParent();
            if (parent != null) {
                Path filename = archive.getFileName();
                if (filename != null) {
                    unzip(archive, parent.resolve(FilenameUtils.getBaseName(filename.toString())));
                }
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Extracts the contents of a ZIP file into a specified destination directory.
     *
     * @param zipFile the path to the ZIP file to extract
     * @param destDir the path to the directory where the contents should be extracted
     * 
     * @throws IOException if an I/O error occurs during extraction
     */
    private void unzip(final Path zipFile, final Path destDir) throws IOException {
        if (Files.notExists(destDir)) {
            Files.createDirectories(destDir);
        }

        try (final var zipFileSystem = FileSystems.newFileSystem(zipFile, (ClassLoader) null)) {
            Log.debug(() -> "unzipping : " + zipFile);
            final Path root = zipFileSystem.getRootDirectories().iterator().next();

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final var destFile = Paths.get(destDir.toString(), file.toString());
                    try {
                        Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (DirectoryNotEmptyException _) {
                        // ignore
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    final var dirToCreate = Paths.get(destDir.toString(), dir.toString());
                    if (Files.notExists(dirToCreate)) {
                        Files.createDirectory(dirToCreate);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
