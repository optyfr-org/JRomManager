package jrm.server.shared.lpr;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import jrm.misc.Log;
import jrm.server.shared.WebSession;
import jrm.server.shared.actions.ActionsMgr;
import lombok.Getter;

/**
 * Manages long polling request sessions and processes client actions.
 * <p>
 * This manager associates web sessions with their corresponding request managers, processes incoming JSON action messages, and
 * handles session lifecycle events such as setting, unsetting, and persisting user and profile settings.
 * </p>
 * 
 * @see ActionsMgr
 * @see WebSession
 */
public class LongPollingReqMgr implements ActionsMgr {

    /**
     * A global registry storing active long polling request managers indexed by their web session IDs.
     * <p>
     * <strong>Concurrency Note:</strong> This map is implemented as a {@link ConcurrentHashMap} so that registration,
     * unregistration, and iteration from different threads do not require external synchronization.
     * </p>
     */
    @Getter
    private static final Map<String, LongPollingReqMgr> cmds = new ConcurrentHashMap<>();

    /**
     * The web session associated with this long polling request manager. Used to retrieve session configurations, user profiles,
     * and queue long-polling messages.
     */
    private WebSession session;

    /**
     * Constructs a new {@code LongPollingReqMgr} for the specified web session and registers it into the global active command
     * registry.
     * 
     * @param session the {@link WebSession} to associate with this manager, must not be null
     * 
     * @throws NullPointerException if the session is null
     */
    public LongPollingReqMgr(WebSession session) {
        setSession(session);
    }

    /**
     * Processes an incoming client message in JSON format. Parses the raw message into a JSON object and routes the actions through
     * the {@link #processActions(ActionsMgr, JsonObject)} pipeline.
     * 
     * @param msg the JSON message string received from the client
     */
    public void process(String msg) {
        Log.debug("processing client message");
        processActions(this, Json.parse(msg).asObject());
    }

    /**
     * Sets and registers the active web session for this manager. Associates the session ID with this manager instance in the
     * global command map {@link #cmds}.
     * 
     * @param session the {@link WebSession} to set, must not be null
     * 
     * @throws NullPointerException if the session is null
     */
    @Override
    public void setSession(WebSession session) {
        if (session == null)
            throw new NullPointerException("Session not found");
        cmds.put(session.getSessionId(), this);
        this.session = session;
    }

    /**
     * Unregisters the session, persists its active profile and user settings, and removes the session ID association from the
     * global command map.
     * 
     * @param session the {@link WebSession} to unset
     */
    @Override
    public void unsetSession(WebSession session) {
        saveSettings();
        cmds.remove(session.getSessionId());
    }

    /**
     * Appends a message to the long-polling queue to be sent to the client.
     * 
     * @param msg the message string to send
     * 
     * @throws IOException if an I/O error occurs while queuing the message
     */
    @Override
    public void send(String msg) throws IOException {
        session.getLprMsg().add(msg);
    }

    /**
     * Appends a message to the long-polling queue only if the queue is currently empty.
     * 
     * @param msg the message string to queue optionally
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void sendOptional(String msg) throws IOException {
        if (session.getLprMsg().isEmpty())
            session.getLprMsg().add(msg);
    }

    /**
     * Checks whether the request manager session is active. For long polling, this is always considered open and active.
     * 
     * @return {@code true} indicating the long polling session is open
     */
    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * Retrieves the web session associated with this manager.
     * 
     * @return the associated {@link WebSession} instance, or {@code null} if it has been unset
     */
    @Override
    public WebSession getSession() {
        return session;
    }

    /**
     * Persists the current profile and user settings to persistent storage. Resets the active session reference to {@code null}
     * after saving to prevent memory leaks.
     */
    private void saveSettings() {
        if (session != null) {
            if (session.getCurrProfile() != null)
                session.getCurrProfile().saveSettings();
            if (session.hasUser())
                session.getUser().getSettings().saveSettings();
            session = null;
        }
    }

    /**
     * Global utility to save and persist settings across all registered active long polling sessions. Iterates through the global
     * registry and invokes setting serialization.
     */
    public static void saveAllSettings() {
        cmds.forEach((_, socket) -> socket.saveSettings());
    }

}
