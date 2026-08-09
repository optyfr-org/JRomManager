package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

import jrm.misc.Log;

/**
 * Handles WebSocket actions for global application settings and system operations.
 * <p>
 * This class manages global configuration properties and system-level operations that affect the entire application, regardless of
 * which profile or ROM set is currently active. It provides functionality for updating user preferences, monitoring memory usage,
 * and triggering garbage collection.
 * </p>
 * <h2>Retro-Gaming Context:</h2>
 * <p>
 * While this class doesn't directly interact with ROM files, it manages the global settings that control how the ROM manager
 * behaves across all profiles and operations. These settings might include UI preferences, default paths, network configuration,
 * and other application-wide parameters.
 * </p>
 * <h2>WebSocket Protocol:</h2>
 * <p>
 * This class processes incoming JSON messages with the following structure:
 * </p>
 * 
 * <pre>
 * <code class="language-json">
 * {
 *   "cmd": "Global.setProperty" | "Global.setMemory" | "Global.gc",
 *   "params": { ... }
 * }
 * </code>
 * </pre>
 * <p>
 * Response messages are sent back with commands like:
 * </p>
 * <ul>
 * <li>{@code Global.updateProperty} - Confirms property updates</li>
 * <li>{@code Global.setMemory} - Reports current memory usage</li>
 * <li>{@code Global.warn} - Sends warning messages to the client</li>
 * </ul>
 * <h3>Thread Safety:</h3>
 * <p>
 * Methods in this class are not synchronized. However, property updates are persisted to disk immediately, and WebSocket messages
 * are sent atomically through the ActionsMgr.
 * </p>
 */
public class GlobalActions {
    /** JSON key for the parameters object in WebSocket messages. */
    private static final String PARAMS = "params";

    /** Format string for displaying memory sizes in Mebibytes (MiB). */
    private static final String FF_MI_B = "%.2f MiB";

    /** The WebSocket action manager for sending messages and accessing the session. */
    private final ActionsMgr ws;

    /**
     * Constructs a new GlobalActions handler.
     *
     * @param ws the WebSocket action manager for communication
     */
    public GlobalActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Updates global user settings based on incoming JSON properties.
     * <p>
     * This method processes a JSON object containing key-value pairs representing setting names and their new values. It supports
     * multiple value types:
     * </p>
     * <ul>
     * <li>Boolean values - stored as boolean properties</li>
     * <li>String values - stored as string properties</li>
     * <li>Other types (numbers, objects, arrays) - converted to string representation</li>
     * </ul>
     * <p>
     * After updating all properties, the settings are persisted to disk and a confirmation message is sent back to the client.
     * </p>
     * <h4>Security:</h4>
     * <p>
     * File path properties (e.g., {@code dir2dat.dst_file}, {@code dir2dat.src_dir}) are validated to ensure they remain within
     * the user's workspace directory. Paths that attempt directory traversal or escape the workspace are rejected.
     * </p>
     * <h4>Incoming JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "params": {
     *     "setting.name.boolean": true,
     *     "setting.name.string": "value",
     *     "setting.name.number": 42
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
     *   "cmd": "Global.updateProperty",
     *   "params": {
     *     "setting.name.boolean": true,
     *     "setting.name.string": "value",
     *     "setting.name.number": 42
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>If settings cannot be saved to disk, the error is logged via {@link Log#err(String, Throwable)}</li>
     * <li>If the WebSocket is closed, no confirmation message is sent</li>
     * <li>If a file path property fails validation, it is rejected and logged</li>
     * </ul>
     *
     * @param jso the incoming JSON message containing property updates
     */
    public void setProperty(JsonObject jso) {
        JsonObject pjso = jso.get(PARAMS).asObject();
        for (Member m : pjso) {
            JsonValue value = m.getValue();
            String propertyName = m.getName();
            
            // Validate file path properties to prevent directory traversal
            if (isFilePathProperty(propertyName)) {
                String stringValue = value.isString() ? value.asString() : value.toString();
                if (!isPathWithinWorkspace(stringValue)) {
                    Log.err("Rejected property '" + propertyName + "': path escapes workspace: " + stringValue);
                    continue;
                }
            }
            
            if (value.isBoolean())
                ws.getSession().getUser().getSettings().setProperty(propertyName, value.asBoolean());
            else if (value.isString())
                ws.getSession().getUser().getSettings().setProperty(propertyName, value.asString());
            else
                ws.getSession().getUser().getSettings().setProperty(propertyName, value.toString());
        }
        try {
            if (ws.isOpen()) {
                ws.getSession().getUser().getSettings().saveSettings();
                final var rjso = new JsonObject();
                rjso.add("cmd", "Global.updateProperty");
                rjso.add(PARAMS, pjso);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Determines if a property name represents a file path that should be validated.
     * 
     * @param propertyName the property name to check
     * @return true if the property represents a file path that requires validation
     */
    private boolean isFilePathProperty(String propertyName) {
        return propertyName != null && (
            propertyName.equals("dir2dat.dst_file") ||
            propertyName.equals("dir2dat.src_dir") ||
            propertyName.equals("dir2dat.lastdstdir") ||
            propertyName.equals("dir2dat.lastsrcdir")
        );
    }

    /**
     * Validates that a file path remains within the user's workspace directory.
     * <p>
     * This method canonicalizes the provided path and ensures it is a descendant of the user's work path,
     * preventing directory traversal attacks.
     * </p>
     * 
     * @param pathString the file path to validate
     * @return true if the path is within the workspace or is null/empty, false if it escapes the workspace
     */
    private boolean isPathWithinWorkspace(String pathString) {
        if (pathString == null || pathString.trim().isEmpty()) {
            return true; // Allow null or empty paths
        }
        
        try {
            Path workPath = ws.getSession().getUser().getSettings().getWorkPath().toRealPath();
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
     * Retrieves and sends current JVM memory usage statistics to the client.
     * <p>
     * This method collects memory information from the Java Runtime and formats it as a localized message string. The memory
     * statistics include:
     * </p>
     * <ul>
     * <li>Total memory - Memory currently allocated to the JVM</li>
     * <li>Used memory - Memory currently in use (total - free)</li>
     * <li>Free memory - Memory available for use within the allocated heap</li>
     * <li>Max memory - Maximum memory the JVM can allocate</li>
     * </ul>
     * <p>
     * All values are displayed in Mebibytes (MiB) for readability.
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Global.setMemory",
     *   "params": {
     *     "msg": "Total: 512.00 MiB, Used: 256.00 MiB, Free: 256.00 MiB, Max: 1024.00 MiB"
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>If the WebSocket message cannot be sent, the error is logged via {@link Log#err(String, Throwable)}</li>
     * <li>If the WebSocket is closed, no message is sent</li>
     * </ul>
     *
     * @param jso the incoming JSON message (currently unused)
     */
    public void setMemory(JsonObject jso) {
        try {
            if (ws.isOpen()) {
                final var rt = Runtime.getRuntime();
                final var msg = (String.format(ws.getSession().getMsgs().getString("MainFrame.MemoryUsage"), String.format(FF_MI_B, rt.totalMemory() / 1048576.0), //$NON-NLS-1$
                        String.format(FF_MI_B, (rt.totalMemory() - rt.freeMemory()) / 1048576.0), String.format(FF_MI_B, rt.freeMemory() / 1048576.0),
                        String.format(FF_MI_B, rt.maxMemory() / 1048576.0))); // $NON-NLS-1$
                                                                              // //$NON-NLS-2$
                                                                              // //$NON-NLS-3$
                                                                              // //$NON-NLS-4$
                final var rjso = new JsonObject();
                rjso.add("cmd", "Global.setMemory");
                final var params = new JsonObject();
                params.add("msg", msg);
                rjso.add(PARAMS, params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Triggers garbage collection and reports updated memory usage.
     * <p>
     * This method explicitly requests the JVM to perform garbage collection to reclaim unused memory, then immediately calls
     * {@link #setMemory(JsonObject)} to report the updated memory statistics.
     * </p>
     * <h4>Note:</h4>
     * <p>
     * The call to {@code System.gc()} is a suggestion to the JVM and may not result in immediate garbage collection. The actual
     * behavior depends on the JVM implementation and garbage collector configuration.
     * </p>
     * <h4>Error Handling:</h4>
     * <p>
     * Any errors from the subsequent {@link #setMemory(JsonObject)} call are handled by that method.
     * </p>
     *
     * @param jso the incoming JSON message (passed to setMemory)
     */
    public void gc(JsonObject jso) {
        System.gc(); // NOSONAR
        setMemory(jso);
    }

    /**
     * Sends a warning message to the client for display in the UI.
     * <p>
     * This method is used throughout the application to notify users about non-critical issues, validation errors, or important
     * information that doesn't prevent operation but requires user attention.
     * </p>
     * <h4>Response JSON Structure:</h4>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *   "cmd": "Global.warn",
     *   "params": {
     *     "msg": "Warning message text"
     *   }
     * }
     * </code>
     * </pre>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     * <li>If the WebSocket message cannot be sent, the error is logged via {@link Log#err(String, Throwable)}</li>
     * <li>If the WebSocket is closed, no message is sent</li>
     * </ul>
     *
     * @param msg the warning message to display to the user
     */
    public void warn(String msg) {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "Global.warn");
                final var params = new JsonObject();
                params.add("msg", msg);
                rjso.add(PARAMS, params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }
}
