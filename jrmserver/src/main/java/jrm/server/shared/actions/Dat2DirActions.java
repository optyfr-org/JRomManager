package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.batch.DirUpdater;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;
import jrm.server.shared.actions.ActionsMgr.SingleCmd;
import jrm.server.shared.actions.ActionsMgr.UpdateResult;

/**
 * Handles WebSocket actions for DAT-to-Directory synchronization operations.
 * <p>
 * This class manages the process of updating ROM directories to match DAT file specifications. A DAT file (also known as a ROM
 * database file) contains metadata about ROM sets for retro-gaming emulators, including checksums, file sizes, and expected
 * directory structures. The DAT-to-Directory operation ensures that the user's ROM directories conform to the specifications
 * defined in DAT files.
 * </p>
 * <h2>Retro-Gaming Context:</h2>
 * <ul>
 * <li><strong>DAT File:</strong> A metadata file (typically in Logiqx XML format) describing expected ROM sets</li>
 * <li><strong>ROM Set:</strong> A collection of game ROMs for a specific system or arcade board</li>
 * <li><strong>Source Directories (srcdirs):</strong> Directories containing DAT files that define the expected structure</li>
 * <li><strong>SrcDstResult (SDR):</strong> Mapping between source DAT files and destination directories to update</li>
 * </ul>
 * <h2>WebSocket Protocol:</h2>
 * <p>
 * This class processes incoming JSON messages with the following structure:
 * </p>
 * 
 * <pre>
 * <code class='language-json'>
 * {
 *   "cmd": "Dat2Dir.start" | "Dat2Dir.settings",
 *   "params": { ... }
 * }
 * </code>
 * </pre>
 * <p>
 * Response messages are sent back with commands like:
 * </p>
 * <ul>
 * <li>{@code Dat2Dir.updateResult} - Updates a specific row's result status</li>
 * <li>{@code Dat2Dir.clearResults} - Clears all result statuses</li>
 * <li>{@code Dat2Dir.end} - Signals operation completion</li>
 * <li>{@code Dat2Dir.showSettings} - Displays profile settings for selected DATs</li>
 * </ul>
 * <h2>Thread Safety:</h2>
 * <p>
 * The {@link #start(JsonObject)} method spawns a background worker thread via {@link Worker}. The operation can be cancelled by
 * invoking {@code ProgressHandler.doCancel()}, which causes a {@link BreakException} to be thrown within the worker thread. The
 * {@link ResultColUpdater} callback is invoked from within the worker thread and synchronizes result persistence with WebSocket
 * message sending. All WebSocket message sending is guarded by {@link ActionsMgr#isOpen()} checks.
 * </p>
 * 
 * @see DirUpdater
 * @see SrcDstResult
 * @see ProgressActions
 * @see ActionsMgr
 * @see WebSession
 * @see Log
 */
public class Dat2DirActions {
    /** JSON key for the parameters object in WebSocket messages. */
    private static final String PARAMS = "params";

    /** The WebSocket action manager for sending messages and accessing the session. */
    private final ActionsMgr ws;

    /** Gson instance configured to exclude transient fields during JSON serialization. */
    private final Gson gson = new GsonBuilder().excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT).create();

    /**
     * Constructs a new Dat2DirActions handler.
     *
     * @param ws the WebSocket action manager for communication
     */
    public Dat2DirActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Initiates a DAT-to-Directory synchronization operation.
     * <p>
     * This method runs the operation in a background worker thread. The process:
     * </p>
     * <ol>
     * <li>Loads the configured source directories containing DAT files</li>
     * <li>Loads the SrcDstResult mappings from user settings</li>
     * <li>Validates that all DAT files have assigned profiles/presets</li>
     * <li>Executes the DirUpdater to synchronize directories with DAT specifications</li>
     * <li>Reports progress and results via WebSocket messages, persisting each result update to settings</li>
     * <li>Cleans up resources (resets profile/scan state, closes progress handler) and notifies the client upon completion</li>
     * </ol>
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>{@link BreakException}: Caught silently - indicates user cancelled the operation</li>
     * <li>Missing source directories: Warns user via {@code Global.warn}</li>
     * <li>Unassigned DAT profiles: Warns user via {@code Global.warn}</li>
     * </ul>
     * <h4>Thread Safety:</h4>
     * <p>
     * This method spawns a worker thread. The operation can be cancelled by setting {@code ProgressHandler.doCancel()} which throws
     * {@link BreakException}.
     * </p>
     *
     * @param jso the incoming JSON message (currently unused, reserved for future parameters)
     */
    public void start(JsonObject jso) {
        (ws.getSession().setWorker(new Worker(this::processDat2Dir))).start();
    }

    /**
     * Processes the DAT-to-Directory synchronization operation.
     * <p>
     * The operation involves loading source directories and SrcDstResult mappings, validating DAT profiles, and executing the
     * synchronization process. Progress and results are reported via WebSocket messages. The operation can be cancelled by setting
     * {@code ProgressHandler.doCancel()}.
     * </p>
     * <h4>Steps:</h4>
     * <ol>
     * <li>Load source directories from user settings</li>
     * <li>Load SrcDstResult mappings from user settings</li>
     * <li>Check for missing DAT profiles and warn user if necessary</li>
     * <li>Execute the DirUpdater to synchronize directories</li>
     * <li>Report progress and results via WebSocket messages</li>
     * <li>Clean up resources and notify client upon completion</li>
     * </ol>
     * <h4>Thread Safety:</h4>
     * <p>
     * This method is designed to run in a background worker thread, allowing the main application thread to remain responsive. The
     * operation can be cancelled by setting {@code ProgressHandler.doCancel()} which throws {@link BreakException}.
     * </p>
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>{@link BreakException}: Caught silently - indicates user cancelled the operation</li>
     * <li>Missing source directories: Warns user via {@code Global.warn}</li>
     * <li>Unassigned DAT profiles: Warns user via {@code Global.warn}</li>
     * </ul>
     * 
     * @see DirUpdater
     * @see ProgressHandler
     * @see BreakException
     */
    private void processDat2Dir() {
        WebSession session = ws.getSession();
        boolean dryrun = session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_dry_run, Boolean.class);
        session.getWorker().setProgress(new ProgressActions(ws));
        try {
            String[] srcdirs = StringUtils.split(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_srcdirs), '|');
            if (srcdirs.length > 0) {
                SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr));
                if (countMissingProfiles(session, sdrl) > 0)
                    new GlobalActions(ws).warn(ws.getSession().getMsgs().getString("MainFrame.AllDatsPresetsAssigned")); //$NON-NLS-1$
                else {
                    new DirUpdater(session, sdrl, session.getWorker().getProgress(), toSrcDirFileList(session, srcdirs), createResultColUpdater(session, sdrl), dryrun);
                }
            } else
                new GlobalActions(ws).warn(ws.getSession().getMsgs().getString("MainFrame.AtLeastOneSrcDir"));
        } catch (BreakException _) {
            // user cancelled action
        } finally {
            Dat2DirActions.this.end();
            session.setCurrProfile(null);
            session.setCurrScan(null);
            session.getWorker().getProgress().close();
            session.getWorker().setProgress(null);
            session.setLastAction(Instant.now());
        }
    }

    /**
     * Counts the number of missing DAT profiles for the given source-destination results. A profile is considered missing if the
     * corresponding settings file does not exist.
     * 
     * @param session The current web session containing user settings and context.
     * @param sdrl List of source-destination results to check for missing profiles.
     * 
     * @return Count of missing DAT profiles.
     */
    private long countMissingProfiles(WebSession session, SDRList<SrcDstResult> sdrl) {
        return sdrl.stream()
                .filter(sdr -> !session.getUser().getSettings().getProfileSettingsFile(PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile()).exists())
                .count();
    }

    /**
     * Converts an array of source directory paths to a list of File objects, using the provided session for path resolution.
     * 
     * @param session The current web session.
     * @param srcdirs Array of source directory paths.
     * 
     * @return List of File objects representing the source directories.
     */
    private List<File> toSrcDirFileList(WebSession session, String[] srcdirs) {
        return Stream.of(srcdirs).map(s -> PathAbstractor.getAbsolutePath(session, s).toFile()).toList();
    }

    /**
     * Creates a ResultColUpdater object that can be used to update the result column in the DAT2Dir results table. This updater
     * is responsible for updating the session's settings and triggering UI updates.
     * 
     * @param session The current web session.
     * @param sdrl List of source-destination results to update.
     * 
     * @return A new ResultColUpdater object.
     */
    private ResultColUpdater createResultColUpdater(WebSession session, SDRList<SrcDstResult> sdrl) {
        return new ResultColUpdater() {
            @Override
            public void updateResult(int row, String result) {
                synchronized (session.getUser().getSettings()) {
                    sdrl.get(row).setResult(result);
                    session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(sdrl));
                    session.getUser().getSettings().saveSettings();
                }
                Dat2DirActions.this.updateResult(row, result);
            }

            @Override
            public void clearResults() {
                synchronized (session.getUser().getSettings()) {
                    sdrl.forEach(sdr -> sdr.setResult(""));
                    session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(sdrl));
                    session.getUser().getSettings().saveSettings();
                }
                Dat2DirActions.this.clearResults();
            }
        };
    }

    /**
     * Retrieves and sends profile settings for selected DAT files.
     * <p>
     * This method loads the profile settings associated with the specified DAT source file and sends them to the client for
     * display.
     * </p>
     * <h4>Incoming JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "params": {
     *     "srcs": ["path/to/dat1.dat", "path/to/dat2.dat"]
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Dat2Dir.showSettings",
     *   "params": {
     *     "settings": { ... profile settings object ... },
     *     "srcs": ["path/to/dat1.dat"]
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <p>
     * If the profile settings cannot be loaded (e.g., file not found, parse error), the error is logged via
     * {@link Log#err(String, Throwable)} and no response is sent.
     * </p>
     *
     * @param jso the incoming JSON message containing the source file paths
     */
    public void settings(JsonObject jso) {
        JsonArray srcs = jso.get(PARAMS).asObject().get("srcs").asArray();
        if (srcs != null && srcs.size() > 0) {
            final var src = srcs.get(0).asString();
            try {
                final var session = ws.getSession();
                final var settings = ws.getSession().getUser().getSettings().loadProfileSettings(PathAbstractor.getAbsolutePath(session, src).toFile(), null);
                if (ws.isOpen()) {
                    final var msg = new JsonObject();
                    msg.add("cmd", "Dat2Dir.showSettings");
                    final var params = new JsonObject();
                    params.add("settings", settings.asJSO());
                    params.add("srcs", srcs);
                    msg.add(PARAMS, params);
                    ws.send(msg.toString());
                }
            } catch (IOException e) {
                Log.err(e.getMessage(), e);
            }
        }
    }

    /**
     * Sends a result update message for a specific SDR row.
     * <p>
     * This method notifies the client about the synchronization status of a specific DAT-to-Directory mapping entry.
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Dat2Dir.updateResult",
     *   "params": {
     *     "row": 0,
     *     "result": "OK" | "Updated" | "Error message"
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <p>
     * If the WebSocket message cannot be sent, the error is logged via {@link Log#err(String, Throwable)}.
     * </p>
     *
     * @param row the zero-based index of the SDR entry being updated
     * @param result the result message (e.g., "OK", "Updated", or an error description)
     */
    void updateResult(int row, String result) {
        try {
            if (ws.isOpen()) {
                ws.send(gson.toJson(new UpdateResult("Dat2Dir.updateResult", new UpdateResult.Params(row, result))));
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message to clear all result statuses in the client UI.
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Dat2Dir.clearResults"
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <p>
     * If the WebSocket message cannot be sent, the error is logged via {@link Log#err(String, Throwable)}.
     * </p>
     */
    void clearResults() {
        try {
            if (ws.isOpen()) {
                ws.send(gson.toJson(new SingleCmd("Dat2Dir.clearResults")));
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends a message signaling the end of the DAT-to-Directory operation.
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Dat2Dir.end"
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <p>
     * If the WebSocket message cannot be sent, the error is logged via {@link Log#err(String, Throwable)}.
     * </p>
     */
    void end() {
        try {
            if (ws.isOpen()) {
                ws.send(gson.toJson(new SingleCmd("Dat2Dir.end")));
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

}
