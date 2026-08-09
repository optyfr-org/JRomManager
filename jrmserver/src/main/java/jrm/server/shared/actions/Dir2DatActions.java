package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;

import com.eclipsesource.json.JsonObject;

import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.scan.Dir2Dat;
import jrm.profile.scan.DirScan;
import jrm.profile.scan.DirScan.Options;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;

/**
 * Handles WebSocket actions for Directory-to-DAT file generation operations.
 * <p>
 * This class manages the process of scanning directories containing ROM files and generating DAT files that describe their
 * contents. A DAT file serves as a catalog or manifest of ROM files, recording metadata such as file names, sizes, checksums (MD5,
 * SHA1), and directory structure. This is useful for documenting ROM collections and sharing them with the retro-gaming community.
 * </p>
 * <h2>Retro-Gaming Context:</h2>
 * <ul>
 * <li><strong>DAT File:</strong> A metadata file describing a collection of ROM files with checksums and structure</li>
 * <li><strong>ROM Files:</strong> Game data files extracted from cartridges, discs, or arcade boards</li>
 * <li><strong>Export Formats:</strong> DAT files can be exported in various formats (Logiqx XML, CMPro, etc.)</li>
 * <li><strong>Scanning Options:</strong> Controls whether to recurse into subdirectories, compute checksums, handle archives,
 * etc.</li>
 * </ul>
 * <h2>WebSocket Protocol:</h2>
 * <p>
 * This class processes incoming JSON messages with the following structure:
 * </p>
 * 
 * <pre>
 * <code class="language-json">
 * {
 *   "cmd": "Dir2Dat.start",
 *   "params": {
 *     "options": {
 *       "dir2dat.scan_subfolders": true,
 *       "dir2dat.deep_scan": false,
 *       "dir2dat.add_md5": false,
 *       "dir2dat.add_sha1": false,
 *       "dir2dat.junk_folders": false,
 *       "dir2dat.do_not_scan_archives": false,
 *       "dir2dat.match_profile": false,
 *       "dir2dat.include_empty_dirs": false
 *     },
 *     "headers": {
 *       "name": "My ROM Collection",
 *       "description": "Collection of NES games",
 *       "version": "1.0",
 *       "author": "Username"
 *     }
 *   }
 * }
 * </code>
 * </pre>
 * <p>
 * Response messages are sent back with commands like:
 * </p>
 * <ul>
 * <li>{@code Dir2Dat.end} - Signals operation completion</li>
 * </ul>
 * <h2>Thread Safety:</h2>
 * <p>
 * The {@link #start(JsonObject)} method spawns a background worker thread via {@link Worker}. The operation can be cancelled by
 * invoking {@code ProgressHandler.doCancel()}, which causes a {@link BreakException} to be thrown within the worker thread. All
 * WebSocket message sending is guarded by {@link ActionsMgr#isOpen()} checks.
 * </p>
 * 
 * @see Dir2Dat
 * @see DirScan.Options
 * @see ExportType
 * @see ActionsMgr
 * @see WebSession
 * @see Log
 */
public class Dir2DatActions {
    /** The WebSocket action manager for sending messages and accessing the session. */
    private final ActionsMgr ws;

    /**
     * Constructs a new Dir2DatActions handler.
     *
     * @param ws the WebSocket action manager for communication
     */
    public Dir2DatActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Initiates a Directory-to-DAT file generation operation.
     * <p>
     * This method runs the operation in a background worker thread. The process:
     * </p>
     * <ol>
     * <li>Loads source directory and destination DAT file paths from user settings</li>
     * <li>Parses scanning options and DAT file headers from the JSON message</li>
     * <li>Creates a Dir2Dat scanner with the specified configuration</li>
     * <li>Scans the directory and generates the DAT file with appropriate metadata</li>
     * <li>Reports progress via WebSocket messages</li>
     * <li>Cleans up resources (resets profile/scan state, closes progress handler) and notifies the client upon completion</li>
     * </ol>
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>{@link BreakException}: Caught silently - indicates user cancelled the operation</li>
     * <li>Missing paths: Operation is skipped if source directory or destination file is null</li>
     * </ul>
     * <h4>Thread Safety:</h4>
     * <p>
     * This method spawns a worker thread. The operation can be cancelled by setting {@code ProgressHandler.doCancel()} which throws
     * {@link BreakException}.
     * </p>
     *
     * @param jso the incoming JSON message containing scanning options, DAT file headers, and export format settings
     */
    public void start(JsonObject jso) {
        (ws.getSession().setWorker(new Worker(() -> executeDir2DatTransformation(jso)))).start();
    }

    /**
     * Executes the directory-to-DAT transformation process.
     * <p>
     * This method performs the core logic of the Dir2Dat operation, including loading settings, creating a scanner instance, and handling
     * progress and cancellation.
     * </p>
     * <h4>Security:</h4>
     * <p>
     * Source and destination paths are validated to ensure they remain within the user's workspace directory before processing.
     * </p>
     * <h4>Thread Safety:</h4>
     * <p>
     * This method is designed to run in a separate thread, allowing for non-blocking execution of the transformation process.
     * </p>
     * 
     * @param jso the JSON message containing transformation parameters and settings
     */
    private void executeDir2DatTransformation(JsonObject jso) {
        WebSession session = ws.getSession();
        session.getWorker().progress = new ProgressActions(ws);
        try {
            String srcdir = session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_src_dir);
            String dstdat = session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_dst_file);
            String format = session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format);
            
            // Validate paths before processing
            if (srcdir != null && !isPathWithinWorkspace(srcdir, session)) {
                Log.err("Dir2Dat operation rejected: source directory escapes workspace: " + srcdir);
                return;
            }
            if (dstdat != null && !isPathWithinWorkspace(dstdat, session)) {
                Log.err("Dir2Dat operation rejected: destination file escapes workspace: " + dstdat);
                return;
            }
            
            JsonObject opts = jso.get("params").asObject().get("options").asObject();
            EnumSet<DirScan.Options> options = getOptions(opts);
            HashMap<String, String> headers = new HashMap<>();
            JsonObject hdrs = jso.get("params").asObject().get("headers").asObject();
            hdrs.forEach(m -> {
                if (!m.getValue().isNull())
                    headers.put(m.getName(), m.getValue().asString());
            });
            if (srcdir != null && dstdat != null)
                new Dir2Dat(ws.getSession(), new File(srcdir), new File(dstdat), session.getWorker().progress, options, ExportType.valueOf(format), headers);
        } catch (BreakException _) {
            // user cancelled action
        } finally {
            Dir2DatActions.this.end();
            session.setCurrProfile(null);
            session.setCurrScan(null);
            session.getWorker().progress.close();
            session.getWorker().progress = null;
            session.setLastAction(Instant.now());
        }
    }

    /**
     * Validates that a file path remains within the user's workspace directory.
     * <p>
     * This method canonicalizes the provided path and ensures it is a descendant of the user's work path,
     * preventing directory traversal attacks.
     * </p>
     * 
     * @param pathString the file path to validate
     * @param session the web session containing the user's workspace configuration
     * @return true if the path is within the workspace, false if it escapes the workspace
     */
    private boolean isPathWithinWorkspace(String pathString, WebSession session) {
        if (pathString == null || pathString.trim().isEmpty()) {
            return false; // Reject null or empty paths at execution time
        }
        
        try {
            Path workPath = session.getUser().getSettings().getWorkPath().toRealPath();
            File file = new File(pathString);
            Path canonicalPath = file.getCanonicalFile().toPath();
            
            // Check if the canonical path starts with the work path
            return canonicalPath.startsWith(workPath);
        } catch (IOException e) {
            // If we can't resolve the path, reject it for safety
            Log.err("Failed to validate path: " + pathString, e);
            return false;
        }
    }

    /**
     * Converts JSON scanning options to an EnumSet of DirScan.Options.
     * <p>
     * This method maps boolean flags from the JSON message to the appropriate scanning option constants. Default options include
     * parallelism and checksum computation for disks (MD5 and SHA1).
     * </p>
     * <h4>Available Options:</h4>
     * <ul>
     * <li>{@code USE_PARALLELISM} - Always enabled, enables multi-threaded scanning</li>
     * <li>{@code MD5_DISKS} - Always enabled, computes MD5 checksums for disk images</li>
     * <li>{@code SHA1_DISKS} - Always enabled, computes SHA1 checksums for disk images</li>
     * <li>{@code RECURSE} - Scan subdirectories recursively</li>
     * <li>{@code IS_DEST} - Shallow scan mode (opposite of deep scan)</li>
     * <li>{@code NEED_MD5} - Compute MD5 checksums for all files</li>
     * <li>{@code NEED_SHA1} - Compute SHA1 checksums for all files</li>
     * <li>{@code JUNK_SUBFOLDERS} - Treat subfolders as junk (flatten structure)</li>
     * <li>{@code ARCHIVES_AND_CHD_AS_ROMS} - Treat archives and CHD files as ROMs</li>
     * <li>{@code MATCH_PROFILE} - Match files against a profile</li>
     * <li>{@code EMPTY_DIRS} - Include empty directories in the DAT file</li>
     * </ul>
     *
     * @param opts the JSON object containing boolean scanning options
     * 
     * @return an EnumSet of enabled scanning options
     */
    private EnumSet<DirScan.Options> getOptions(JsonObject opts) {
        var options = EnumSet.of(Options.USE_PARALLELISM, Options.MD5_DISKS, Options.SHA1_DISKS);
        addIf(opts, options, "dir2dat.scan_subfolders", true, Options.RECURSE); //$NON-NLS-1$
        addUnless(opts, options, "dir2dat.deep_scan", false, Options.IS_DEST); //$NON-NLS-1$
        addIf(opts, options, "dir2dat.add_md5", false, Options.NEED_MD5); //$NON-NLS-1$
        addIf(opts, options, "dir2dat.add_sha1", false, Options.NEED_SHA1); //$NON-NLS-1$
        addIf(opts, options, "dir2dat.junk_folders", false, Options.JUNK_SUBFOLDERS); //$NON-NLS-1$
        addIf(opts, options, "dir2dat.do_not_scan_archives", false, Options.ARCHIVES_AND_CHD_AS_ROMS); //$NON-NLS-1$
        addIf(opts, options, "dir2dat.match_profile", false, Options.MATCH_PROFILE); //$NON-NLS-1$
        addIf(opts, options, "dir2dat.include_empty_dirs", false, Options.EMPTY_DIRS); //$NON-NLS-1$
        return options;
    }

    /**
     * Adds an option to the set if the specified key in the JSON object has a true value.
     * If the key does not exist, it uses the provided default value.
     * 
     * @param opts       the JSON object containing the options
     * @param options    the set of options to add to
     * @param key        the key to check in the JSON object
     * @param defaultValue the default value to use if the key does not exist
     * @param option     the option to add if the condition is met
     */
    private static void addIf(JsonObject opts, EnumSet<Options> options, String key, boolean defaultValue, Options option) {
        if (opts.getBoolean(key, defaultValue))
            options.add(option);
    }

    /**
     * Adds an option to the set if the specified key in the JSON object has a false value.
     * If the key does not exist, it uses the provided default value.
     * 
     * @param opts       the JSON object containing the options
     * @param options    the set of options to add to
     * @param key        the key to check in the JSON object
     * @param defaultValue the default value to use if the key does not exist
     * @param option     the option to add if the condition is met
     */
    private static void addUnless(JsonObject opts, EnumSet<Options> options, String key, boolean defaultValue, Options option) {
        if (!opts.getBoolean(key, defaultValue))
            options.add(option);
    }

    /**
     * Sends a message signaling the end of the Directory-to-DAT operation.
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Dir2Dat.end"
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
                final var msg = new JsonObject();
                msg.add("cmd", "Dir2Dat.end");
                ws.send(msg.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

}
