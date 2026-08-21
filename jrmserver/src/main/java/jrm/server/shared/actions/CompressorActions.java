package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;

import com.eclipsesource.json.JsonObject;

import jrm.batch.Compressor;
import jrm.batch.Compressor.FileResult;
import jrm.batch.CompressorFormat;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.SettingsEnum;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;

/**
 * Handles actions related to compressing files within the web session. Manages the compression process, including starting the
 * process, handling different compression formats, updating progress, and sending results back to the client.
 */
public class CompressorActions {
    /** The parent {@link ActionsMgr} instance managing this action handler. */
    private final ActionsMgr ws;

    /**
     * Constructs a new {@link CompressorActions} instance.
     *
     * @param ws The {@link ActionsMgr} instance responsible for managing this action handler.
     */
    public CompressorActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Starts the compression process for the files in the cached compressor list. This method initiates a background worker thread
     * to handle the compression asynchronously. It retrieves user settings for format, force option, and threading, sets up
     * progress tracking, clears previous results, and executes the compression tasks in parallel if configured.
     *
     * @param jso A JSON object containing parameters for the start command (currently unused).
     */
    public void start(JsonObject jso) // NOSONAR
    {
        (ws.getSession().setWorker(new Worker(this::processCompressorList))).start();
    }

    /**
     * Processes the cached compressor list to compress files based on user settings. This method retrieves the compression format,
     * force option, and threading configuration from the user settings. It sets up progress tracking, clears previous results, and
     * starts the compression process in parallel if configured.
     */
    private void processCompressorList() {
        final var session = ws.getSession();
        final var format = CompressorFormat.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.compressor_format));
        final var force = session.getUser().getSettings().getProperty(SettingsEnum.compressor_force, Boolean.class);

        final var useParallelism = session.getUser().getSettings().getProperty(SettingsEnum.use_parallelism, Boolean.class);
        final var nThreads = Boolean.TRUE.equals(useParallelism) ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;

        session.getWorker().setProgress(new ProgressActions(ws));
        session.getWorker().getProgress().setInfos(Math.min(Runtime.getRuntime().availableProcessors(), ws.getSession().getCachedCompressorList().size()), true);
        try {
            clearResults();
            startParallelCompression(session, format, force, nThreads);

        } catch (BreakException _) { // user requested to stop the process
            session.getWorker().getProgress().doCancel();
        } finally {
            session.getWorker().getProgress().close();
            CompressorActions.this.end();
        }
    }

    /**
     * Starts parallel compression using the provided session, format, force option, and number of threads. It initializes a
     * list of file results from the cached compressor list, creates an atomic integer for tracking processed files, and sets up
     * a {@link Compressor} instance. The method then starts a multi-threaded virtual environment to process the compression tasks.
     *
     * @param session The current {@link WebSession}.
     * @param format The target {@link CompressorFormat} for compression.
     * @param force Whether to force compression even if the file is already in the target format.
     * @param nThreads The number of threads to use for parallel processing.
     */
    private void startParallelCompression(final WebSession session, final CompressorFormat format, final Boolean force, final int nThreads) {
        List<FileResult> values = new ArrayList<>(session.getCachedCompressorList().values());
        final var cnt = new AtomicInteger();
        final var compressor = new Compressor(session, cnt, session.getCachedCompressorList().size(), session.getWorker().getProgress());
        try (final var mt = new MultiThreadingVirtual<Compressor.FileResult>("compressor", session.getWorker().getProgress(), nThreads,
                fr -> doCompress(session, format, force, cnt, compressor, values, fr))) {
            mt.start(session.getCachedCompressorList().values().stream());
        }
    }

    /**
     * Performs the actual compression logic for a single file result ({@link FileResult}). It determines the current file's index,
     * gets the absolute path, sets up callback functions for updating results and file paths, and delegates to specific format
     * handling methods based on the configured {@link CompressorFormat}.
     *
     * @param session The current {@link WebSession}.
     * @param format The target {@link CompressorFormat} (SEVENZIP, ZIP, TZIP).
     * @param force Whether to force the compression even if the target format already matches.
     * @param cnt An {@link AtomicInteger} to track the number of processed files.
     * @param compressor The {@link Compressor} instance performing the operations.
     * @param values The list of all {@link FileResult}s to determine the index.
     * @param fr The specific {@link FileResult} representing the file to compress.
     */
    private void doCompress(final WebSession session, final CompressorFormat format, final boolean force, final AtomicInteger cnt, final Compressor compressor,
            List<FileResult> values, FileResult fr) {
        if (session.getWorker().getProgress().isCancel())
            return;
        try {
            final int i = values.indexOf(fr);
            var file = PathAbstractor.getAbsolutePath(session, fr.getFile().toString()).toFile();
            Compressor.UpdResultCallBack cb = txt -> updateResult(i, fr.applyResult(txt));
            Compressor.UpdSrcCallBack scb = src -> updateFile(i, fr.applyFile(PathAbstractor.getRelativePath(session, src.toPath())));
            switch (format) {
                case SEVENZIP -> doCompress2SevenZip(force, compressor, file, cb, scb);
                case ZIP -> doCompress2Zip(force, compressor, file, cb, scb);
                case TZIP -> doCompress2TZip(force, compressor, file, cb, scb);
                default -> {
                    /* nothing to do */}
            }
        } catch (BreakException _) { // user requested to stop the process
            session.getWorker().getProgress().doCancel();
        } catch (final Exception e) { // oops! something unexpected happened
            Log.err(e.getMessage(), e);
        } finally {
            cnt.incrementAndGet();
        }
    }

    /**
     * Handles the conversion of a file to the TZIP format. If the source file is already a ZIP, it converts it directly to TZIP.
     * Otherwise, it first converts the source file to ZIP (using 7-Zip internally) and then converts the resulting ZIP to TZIP.
     *
     * @param force Whether to force the conversion even if the target format already matches.
     * @param compressor The {@link Compressor} instance performing the operations.
     * @param file The source {@link File} to be converted.
     * @param cb The callback to update the compression result text.
     * @param scb The callback to update the source file path during intermediate steps.
     * 
     * @throws IllegalArgumentException if the input file is invalid.
     */
    private void doCompress2TZip(final boolean force, final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb)
            throws IllegalArgumentException {
        if ("zip".equals(FilenameUtils.getExtension(file.getName())))
            compressor.zip2TZip(file, force, cb);
        else {
            file = compressor.sevenZip2Zip(file, true, cb, scb);
            if (file != null && file.exists())
                compressor.zip2TZip(file, force, cb);
        }
    }

    /**
     * Handles the conversion of a file to the ZIP format. If the source file is already a ZIP and force is true, it re-compresses
     * the ZIP. If the source file is already a ZIP and force is false, it skips the file. Otherwise, it converts the source file
     * (assumed to be 7z) to ZIP.
     *
     * @param force Whether to force the conversion even if the target format already matches.
     * @param compressor The {@link Compressor} instance performing the operations.
     * @param file The source {@link File} to be converted.
     * @param cb The callback to update the compression result text.
     * @param scb The callback to update the source file path during intermediate steps.
     * 
     * @throws IllegalArgumentException if the input file is invalid.
     */
    private void doCompress2Zip(final boolean force, final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb)
            throws IllegalArgumentException {
        if ("zip".equals(FilenameUtils.getExtension(file.getName()))) {
            if (force)
                compressor.zip2Zip(file, cb, scb);
            else
                cb.apply("Skipped");
        } else
            compressor.sevenZip2Zip(file, false, cb, scb);
    }

    /**
     * Handles the conversion of a file to the SEVENZIP format. If the source file is already a 7z and force is true, it
     * re-compresses the 7z. If the source file is already a 7z and force is false, it skips the file. If the source file is a ZIP,
     * it converts it to 7z. For other formats, it assumes the input is already in a compatible format and re-compresses it.
     *
     * @param force Whether to force the conversion even if the target format already matches.
     * @param compressor The {@link Compressor} instance performing the operations.
     * @param file The source {@link File} to be converted.
     * @param cb The callback to update the compression result text.
     * @param scb The callback to update the source file path during intermediate steps.
     * 
     * @throws IllegalArgumentException if the input file is invalid.
     */
    private void doCompress2SevenZip(final boolean force, final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb)
            throws IllegalArgumentException {
        switch (FilenameUtils.getExtension(file.getName())) {
            case "zip" -> compressor.zip2SevenZip(file, cb, scb);
            case "7z" -> {
                if (force)
                    compressor.sevenZip2SevenZip(file, cb, scb);
                else
                    cb.apply("Skipped");
            }
            default -> compressor.sevenZip2SevenZip(file, cb, scb);
        }
    }

    /**
     * Sends a message to the client to update the displayed file path for a specific row.
     *
     * @param row The index (0-based) of the row in the client-side table/list.
     * @param file The new {@link Path} of the file to display.
     */
    void updateFile(int row, Path file) {
        try {
            if (ws.isOpen()) {
                final var msg = new JsonObject();
                msg.add("cmd", "Compressor.updateFile");
                final var params = new JsonObject();
                params.add("row", row);
                params.add("file", file.toString());
                msg.add("params", params);
                ws.send(msg.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message to the client to update the displayed result text for a specific row.
     *
     * @param row The index (0-based) of the row in the client-side table/list.
     * @param result The new result string to display (e.g., "Success", "Error", "Skipped").
     */
    void updateResult(int row, String result) {
        try {
            if (ws.isOpen()) {
                final var msg = new JsonObject();
                msg.add("cmd", "Compressor.updateResult");
                final var params = new JsonObject();
                params.add("row", row);
                params.add("result", result);
                msg.add("params", params);
                ws.send(msg.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message to the client to clear the results display.
     */
    void clearResults() {
        try {
            if (ws.isOpen()) {
                final var msg = new JsonObject();
                msg.add("cmd", "Compressor.clearResults");
                ws.send(msg.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message to the client indicating the end of the compression process.
     */
    void end() {
        try {
            if (ws.isOpen()) {
                final var msg = new JsonObject();
                msg.add("cmd", "Compressor.end");
                ws.send(msg.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

}
