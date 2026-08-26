package jrm.batch;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.progress.ProgressHandler;
import jrm.aui.progress.SynchronizedProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.batch.TrntChkReport.Child;
import jrm.batch.TrntChkReport.Status;
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
    final AtomicInteger processing = new AtomicInteger();
    /** Atomic integer to track the current piece being processed */
    final AtomicInteger current = new AtomicInteger();
    /** the active user session */
    final Session session;
    /** the set of options to control the behavior of the torrent checker */
    final Set<Options> options;
    /** the mode of checking to be performed (e.g., filename, file size, SHA1) */
    final TrntChkMode mode;

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
        final ProgressHandler safeProgress = new SynchronizedProgressHandler(progress);
        final var updateLock = new Object();
        final ResultColUpdater safeUpdater = new ResultColUpdater() {
            @Override
            public void updateResult(int row, String result) {
                synchronized (updateLock) {
                    updater.updateResult(row, result);
                }
            }

            @Override
            public void clearResults() {
                synchronized (updateLock) {
                    updater.clearResults();
                }
            }
        };
        final Map<T, Integer> rowMap = new HashMap<>();
        for (int i = 0; i < sdrl.size(); i++)
            rowMap.put(sdrl.get(i), i);
        sdrl.stream().filter(AbstractSrcDstResult::isSelected).forEach(sdr -> safeUpdater.updateResult(rowMap.get(sdr), ""));
        final var use_parallelism = session.getUser().getSettings().getProperty(SettingsEnum.use_parallelism, Boolean.class);
        final var nThreads = Boolean.TRUE.equals(use_parallelism) ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;
        try (final var mt = new MultiThreadingVirtual<T>("torrent-checker", safeProgress, nThreads, sdr -> {
            if (safeProgress.isCancel())
                return;
            try {
                final int row = rowMap.get(sdr);
                safeUpdater.updateResult(row, "In progress...");
                final String result = check(safeProgress, sdr);
                safeUpdater.updateResult(row, result);
                safeProgress.setProgress(null, -1, null, "");
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
        new TorrentArchiveExtractor<T>(this).detectArchives(sdr, tfiles, options.contains(Options.DETECTARCHIVEDFOLDERS));
        if (mode != TrntChkMode.SHA1)
            result = checkFiles(progress, sdr, src, dst, report, tfiles);
        else
            result = new TorrentPieceHasher<T>(this).checkBlocks(progress, sdr, src, dst, report, torrent, tfiles);
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
            return formatCompleteResult(removedFiles);
        }
        if (mode == TrntChkMode.FILENAME) {
            return String.format(session.getMsgs().getString("TorrentChecker.ResultFileName"), data.ok * 100.0 / data.total, data.missingFiles, removedFiles); //$NON-NLS-1$
        }
        return String.format(session.getMsgs().getString("TorrentChecker.ResultFileSize"), data.ok * 100.0 / data.total, humanReadableByteCount(data.missingBytes, false), //$NON-NLS-1$
                data.wrongSizedFiles, removedFiles);
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
        final Path destRoot = dst.toPath().toAbsolutePath().normalize();
        final Path file;
        try {
            file = resolveTorrentEntry(destRoot, tfile.getFileDirs());
        } catch (IOException e) {
            final Child node = report.add(String.join("/", tfile.getFileDirs()));
            recordMissingFile(data, node, tfile);
            progress.setProgress(toDocument(toPurple(src.getAbsolutePath())), -1, null, e.getMessage());
            progress.setProgress2(current + "/" + processing, current.get(), processing.get()); //$NON-NLS-1$
            return;
        }
        data.paths.add(file);
        final Child node = report.add(destRoot.relativize(file).toString());
        progress.setProgress(toDocument(toPurple(src.getAbsolutePath())), -1, null, file.toString());
        progress.setProgress2(current + "/" + processing, current.get(), processing.get()); //$NON-NLS-1$
        if (Files.exists(file)) {
            checkExistingFile(data, file, node, tfile);
        } else {
            recordMissingFile(data, node, tfile);
        }
    }

    private void recordMissingFile(final CheckFilesData data, final Child node, final TorrentFile tfile) {
        if (mode == TrntChkMode.FILENAME) {
            data.missingFiles++;
        } else {
            data.missingBytes += node.getData().setLength(tfile.getFileLength()).getLength();
        }
        node.setStatus(Status.MISSING);
    }

    private void checkExistingFile(final CheckFilesData data, final Path file, final Child node, final TorrentFile tfile) throws IOException {
        if (mode == TrntChkMode.FILENAME || Files.size(file) == node.getData().setLength(tfile.getFileLength()).getLength()) {
            data.ok++;
            node.setStatus(Status.OK);
        } else {
            if (options.contains(Options.REMOVEWRONGSIZEDFILES)) {
                Files.delete(file);
            }
            data.wrongSizedFiles++;
            data.missingBytes += node.getData().setLength(tfile.getFileLength()).getLength();
            node.setStatus(Status.SIZE);
        }
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










    String formatCompleteResult(final int removedFiles) {
        final String complete = session.getMsgs().getString(TORRENT_CHECKER_RESULT_COMPLETE);
        return toDocument(removedFiles > 0 ? toBoldBlue(complete) : toBoldGreen(complete));
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
    int removeUnknownFiles(final TrntChkReport report, final Set<Path> paths, final T sdr, final boolean remove) throws IOException {
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

    static Path resolveTorrentEntry(final Path destDir, final List<String> components) throws IOException {
        if (components == null || components.isEmpty()) {
            throw new IOException("Torrent path is empty");
        }
        final Path root = destDir.toAbsolutePath().normalize();
        Path resolved = root;
        for (final String component : components) {
            if (component == null || component.isEmpty() || ".".equals(component) || "..".equals(component)) {
                throw new IOException("Torrent path component rejected: " + component);
            }
            if (component.indexOf('/') >= 0 || component.indexOf('\\') >= 0) {
                throw new IOException("Torrent path component contains separator: " + component);
            }
            final Path segment = Paths.get(component);
            if (segment.isAbsolute()) {
                throw new IOException("Torrent path component is absolute: " + component);
            }
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Torrent path escapes destination directory: " + String.join("/", components));
        }
        return resolved;
    }
}
