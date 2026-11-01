package jrm.server.shared.actions;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.security.PathAbstractor;

/**
 * Action handler for managing CatVer metadata integrations and session requests.
 * <p>
 * This class processes incoming JSON action commands related to loading "CatVer.ini" files, which map ROM sets to their category
 * and version classifications. It updates the active user profile's CatVer filter configuration, reloads the metadata, and
 * dispatches load responses back to the client via WebSocket messages.
 * </p>
 * <p>
 * <b>Thread Safety:</b> This class is not thread-safe. All operations should be performed on the WebSocket message handling thread.
 * The underlying {@link ActionsMgr} and {@link Profile} instances are shared across the session and should not be accessed
 * concurrently from multiple threads.
 * </p>
 */
public class CatVerActions {

    /**
     * The {@link ActionsMgr} instance used for managing session interactions and WebSocket communications.
     */
    private final ActionsMgr ws;

    /**
     * Constructs a new {@code CatVerActions} instance with the specified actions manager.
     *
     * @param ws the {@link ActionsMgr} instance to use for managing session interactions and communications, must not be null
     */
    public CatVerActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Loads the CatVer.ini file path from the provided JSON object and updates the current profile settings accordingly.
     * <p>
     * This method extracts the "path" parameter from the "params" object within the provided JSON. It then updates the current
     * profile's {@link ProfileSettingsEnum#filter_catver_ini} property with this path, reloads the CatVer settings for the profile,
     * and saves the updated profile settings. Finally, it calls the {@link #loaded(Profile)} method to notify the client that the
     * CatVer settings have been loaded.
     * </p>
     *
     * @param jso the JSON object containing the parameters for loading the CatVer.ini file, expected to have a structure like:
     * 
     *        <pre>
     * <code class='language-json'>
     * {
     *     "params": {
     *          "path": "relative/or/absolute/path/to/CatVer.ini"
     *     }
     * }
     * </code>
     *        </pre>
     */
    public void load(JsonObject jso) {
        JsonValue jsonValue = jso.get("params").asObject().get("path");
        ws.getSession().getCurrProfile().setProperty(ProfileSettingsEnum.filter_catver_ini, jsonValue != null && !jsonValue.isNull() ? jsonValue.asString() : null); // $NON-NLS-1$
        ws.getSession().getCurrProfile().loadCatVer(null);
        ws.getSession().getCurrProfile().saveSettings();
        loaded(ws.getSession().getCurrProfile());
    }

    /**
     * Notifies the client that the CatVer settings have been loaded for the specified profile.
     * <p>
     * This method sends a WebSocket message with the command {@code "CatVer.loaded"} and includes the relative abstract path to the
     * CatVer.ini file in the parameters. If the profile does not have a CatVer.ini file associated with it, the path parameter will
     * be set to {@code null}. The path is converted to a relative abstract path using
     * {@link PathAbstractor#getRelativePath(jrm.security.Session, java.nio.file.Path)} for security and portability.
     * </p>
     * <p>
     * Example outgoing WebSocket message:
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "CatVer.loaded",
     *     "params": {
     *         "path": "%work/profiles/CatVer.ini"
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param profile the profile for which the CatVer settings have been loaded, must not be null
     */
    public void loaded(final Profile profile) {
        try {
            if (ws.isOpen()) {
                final var msg = new JsonObject();
                msg.add("cmd", "CatVer.loaded");
                final var params = new JsonObject();
                params.add("path", profile.getCatver() != null ? PathAbstractor.getRelativePath(ws.getSession(), profile.getCatver().file.toPath()).toString() : null);
                msg.add("params", params);
                ws.send(msg.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }
}
