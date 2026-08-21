package jrm.server.shared.actions;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;

import com.eclipsesource.json.JsonObject;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.batch.TorrentChecker;
import jrm.io.torrent.options.TrntChkMode;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;

/**
 * Handles WebSocket actions for Torrent Checker operations.
 * <p>
 * This class manages the process of verifying that the contents of a directory match the files described by a torrent file. In the
 * retro-gaming context, torrent files are commonly used to distribute large ROM sets (collections of game ROMs for emulators). The
 * torrent checker verifies that each file in the destination directory matches the expected checksums and sizes defined in the
 * torrent metadata, and can optionally clean up unexpected or incorrectly-sized files.
 * </p>
 * <h2>Retro-Gaming Context:</h2>
 * <ul>
 * <li><strong>Torrent File:</strong> A BitTorrent metadata file ({@code .torrent}) containing file names, sizes, and piece hashes
 * for a ROM set distribution</li>
 * <li><strong>Torrent Check:</strong> A verification process that compares files on disk against the torrent's expected file list
 * and checksums</li>
 * <li><strong>SrcDstResult (SDR):</strong> Mapping between source torrent files and destination directories to verify</li>
 * <li><strong>TrntChkMode:</strong> The checking mode that determines how files are verified (e.g., by file-level checksums or by
 * block-level piece hashes)</li>
 * </ul>
 * <h2>WebSocket Protocol:</h2>
 * <p>
 * This class processes incoming JSON messages with the following structure:
 * </p>
 * 
 * <pre>
 * <code class="language-json">
 * {
 *   "cmd": "TrntChk.start",
 *   "params": { ... }
 * }
 * </code>
 * </pre>
 * <p>
 * Response messages are sent back with commands like:
 * </p>
 * <ul>
 * <li>{@code TrntChk.updateResult} - Updates a specific row's verification result</li>
 * <li>{@code TrntChk.clearResults} - Clears all result statuses</li>
 * <li>{@code TrntChk.end} - Signals operation completion</li>
 * </ul>
 * <h3>Thread Safety:</h3>
 * <p>
 * The {@link #start(JsonObject)} method spawns a background worker thread via {@link Worker}. The operation can be cancelled by
 * invoking {@code ProgressHandler.doCancel()}, which causes a {@link BreakException} to be thrown within the worker thread. The
 * {@link ResultColUpdater} callback is invoked from within the worker thread and synchronizes result persistence with WebSocket
 * message sending. All WebSocket message sending is guarded by {@link ActionsMgr#isOpen()} checks.
 * </p>
 * 
 * @see TorrentChecker
 * @see TrntChkMode
 * @see SrcDstResult
 * @see ProgressActions
 * @see ActionsMgr
 * @see WebSession
 * @see Log
 */
public class TrntChkActions {
    /** The WebSocket action manager for sending messages and accessing the session. */
    private final ActionsMgr ws;

    /**
     * Constructs a new TrntChkActions handler.
     *
     * @param ws the WebSocket action manager for communication
     */
    public TrntChkActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Initiates a torrent verification operation.
     * <p>
     * This method runs the operation in a background worker thread. The process:
     * </p>
     * <ol>
     * <li>Loads the checking mode ({@link TrntChkMode}) from user settings</li>
     * <li>Builds an {@link EnumSet} of {@link TorrentChecker.Options} based on user preferences:
     * <ul>
     * <li>{@code REMOVEUNKNOWNFILES} - Delete files not listed in the torrent</li>
     * <li>{@code REMOVEWRONGSIZEDFILES} - Delete files whose size doesn't match the torrent</li>
     * <li>{@code DETECTARCHIVEDFOLDERS} - Detect folders that are actually archived content</li>
     * </ul>
     * </li>
     * <li>Loads the {@link SrcDstResult} mappings (torrent file &rarr; destination directory) from settings</li>
     * <li>Executes the {@link TorrentChecker} with a {@link ResultColUpdater} callback that persists and broadcasts result updates
     * to the client</li>
     * <li>Cleans up resources and notifies the client upon completion</li>
     * </ol>
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>{@link BreakException}: Caught silently — indicates the user cancelled the operation via the progress handler</li>
     * <li>Any other exceptions propagate from {@link TorrentChecker} and are not caught here; the {@code finally} block still
     * ensures cleanup and client notification</li>
     * </ul>
     * <h4>Thread Safety:</h4>
     * <p>
     * This method spawns a worker thread via {@link WebSession#setWorker(Worker)}. The {@link ResultColUpdater} callback is invoked
     * from within the worker thread and synchronizes result persistence with WebSocket message sending.
     * </p>
     *
     * @param jso the incoming JSON message (currently unused, reserved for future parameters)
     */
    public void start(JsonObject jso) {
        (ws.getSession().setWorker(new Worker(this::performTorrentCheck))).start();
    }

    /** Performs the torrent check action in a worker thread.        
     * <p>
     * This method performs the actual torrent check action. It loads the necessary settings, builds the required options,
     * initializes the progress handler, and runs the {@link TorrentChecker} with a result updater.
     * <h4>Steps:</h4>
     * <ol>
     * <li>Loads the checking mode from user settings</li>
     * <li>Builds an {@link EnumSet} of {@link TorrentChecker.Options} based on user preferences</li>
     * <li>Initializes the progress handler</li>
     * <li>Loads the {@link SDRList} of {@link SrcDstResult} mappings from settings</li>
     * <li>Creates and runs a new {@link TorrentChecker} instance with the specified parameters</li>
     * <li>Handles the {@link BreakException} if the user cancels the operation</li>
     * <li>Ensures cleanup and client notification upon completion</li>
     * </ol>
     */
    private void performTorrentCheck() {
        WebSession session = ws.getSession();
        final var mode = TrntChkMode.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_mode));
        final var opts = EnumSet.noneOf(TorrentChecker.Options.class);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_unknown_files, Boolean.class)))
            opts.add(TorrentChecker.Options.REMOVEUNKNOWNFILES);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_wrong_sized_files, Boolean.class)))
            opts.add(TorrentChecker.Options.REMOVEWRONGSIZEDFILES);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_detect_archived_folders, Boolean.class)))
            opts.add(TorrentChecker.Options.DETECTARCHIVEDFOLDERS);

        session.getWorker().setProgress(new ProgressActions(ws));
        try {
            SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr));
            new TorrentChecker<SrcDstResult>(session, session.getWorker().getProgress(), sdrl, mode, createResultColUpdater(session, sdrl), opts);
        } catch (BreakException _) {
            // user cancelled action
        } finally {
            TrntChkActions.this.end();
            session.getWorker().getProgress().close();
            session.getWorker().setProgress(null);
            session.setLastAction(Instant.now());
        }
    }

    /** Creates a ResultColUpdater that updates the result of a specific SDR row and saves the changes to user settings.
     * <p>
     * The ResultColUpdater is used by the TorrentChecker to update the verification result of a specific SDR row and save the changes
     * to the user's settings. It also notifies the client about the updated result.
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class="language-json">
     * {
     *   "cmd": "TrntChk.updateResult",
     *   "params": {
     *     "row": 0,
     *     "result": "Complete" | "Missing files" | "Error description"
     *   }
     * }
     * </code>
     * </pre>
     * @param session The WebSession instance associated with the current user and request.
     * @param sdrl The SDRList of SrcDstResult instances to be checked and updated.
     * @return A ResultColUpdater implementation that updates the verification result of a specific SDR row and notifies the client.
     */
    private ResultColUpdater createResultColUpdater(WebSession session, SDRList<SrcDstResult> sdrl) {
        return new ResultColUpdater() {
            @Override
            public void updateResult(int row, String result) {
                sdrl.get(row).setResult(result);
                session.getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(sdrl));
                session.getUser().getSettings().saveSettings();
                TrntChkActions.this.updateResult(row, result);
            }

            @Override
            public void clearResults() {
                sdrl.forEach(sdr -> sdr.setResult(""));
                session.getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(sdrl));
                session.getUser().getSettings().saveSettings();
                TrntChkActions.this.clearResults();
            }
        };
    }

    /**
     * Sends a result update message for a specific SDR row to the client.
     * <p>
     * This method notifies the client about the verification status of a specific torrent-to-directory mapping entry. The result
     * typically indicates whether the directory contents match the torrent specification (e.g., "Complete", "Missing files", or a
     * detailed error description).
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class="language-json">
     * {
     *   "cmd": "TrntChk.updateResult",
     *   "params": {
     *     "row": 0,
     *     "result": "Complete" | "Missing files" | "Error description"
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>If the WebSocket connection is closed ({@link ActionsMgr#isOpen()} returns {@code false}), the message is silently
     * dropped</li>
     * <li>If the WebSocket message cannot be sent, the {@link IOException} is logged via {@link Log#err(String, Throwable)}</li>
     * </ul>
     *
     * @param row the zero-based index of the SDR entry being updated
     * @param result the verification result message (e.g., "Complete", "Missing files", or an error description)
     */
    void updateResult(int row, String result) {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "TrntChk.updateResult");
                final var params = new JsonObject();
                params.add("row", row);
                params.add("result", result);
                rjso.add("params", params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message to clear all result statuses in the client UI.
     * <p>
     * This is typically called at the beginning of a new torrent check operation or when the user explicitly requests a results
     * reset. The client should clear all previously displayed verification results from its table view.
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class="language-json">
     * {
     *   "cmd": "TrntChk.clearResults"
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>If the WebSocket connection is closed, the message is silently dropped</li>
     * <li>If the WebSocket message cannot be sent, the {@link IOException} is logged via {@link Log#err(String, Throwable)}</li>
     * </ul>
     */
    void clearResults() {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "TrntChk.clearResults");
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message signaling the end of the torrent verification operation.
     * <p>
     * The client uses this signal to re-enable UI controls, stop progress indicators, and finalize any pending UI updates. This
     * method is always called from the {@code finally} block of {@link #start(JsonObject)}, ensuring it is sent regardless of
     * whether the operation completed successfully, was cancelled, or encountered an error.
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class="language-json">
     * {
     *   "cmd": "TrntChk.end"
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>If the WebSocket connection is closed, the message is silently dropped</li>
     * <li>If the WebSocket message cannot be sent, the {@link IOException} is logged via {@link Log#err(String, Throwable)}</li>
     * </ul>
     */
    void end() {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "TrntChk.end");
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

}
