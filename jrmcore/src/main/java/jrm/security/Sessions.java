package jrm.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;

/**
 * Utility registry and manager for active security sessions in JRomManager. This class supports both a single-session mode
 * (typically utilized for standard desktop/standalone executions) and a multi-session registry (used for server/multi-user
 * environments where sessions are mapped via unique IDs).
 *
 * @author Expert Java Code Documentation Developer
 * 
 * @since 1.0
 */
public final @UtilityClass class Sessions {
    /**
     * Flag indicating whether the application is operating in single-session mode.
     *
     * @param singleMode true to enforce single-session mode, false for multi-session mode
     * 
     * @return true if single-session mode is active, false otherwise
     */
    private static @Getter @Setter boolean singleMode = false;

    /**
     * The globally shared single session instance. Used when {@link #singleMode} is enabled.
     *
     * @param singleSession the global session instance to set
     * 
     * @return the active single session instance, or null if not yet initialized
     */
    private static @Getter @Setter Session singleSession = null;

    /**
     * Registry mapping unique session identifier strings to their respective active {@link Session} contexts. Used only when
     * {@link #singleMode} is false.
     */
    private static final Map<String, Session> sessionsMap = new ConcurrentHashMap<>();

    /**
     * Retrieves the single, globally active session, initializing it if necessary. This method can only be invoked when
     * {@link #singleMode} is enabled.
     *
     * @param multiuser {@code true} if multi-user isolation features should be enabled in the session, {@code false} otherwise
     * @param noupdate {@code true} if update checks should be disabled in the session, {@code false} otherwise
     * 
     * @return the globally active session instance
     * 
     * @throws AssertionError if {@link #singleMode} is false
     */
    public static Session getSession(boolean multiuser, boolean noupdate) {
        assert singleMode;
        if (singleSession == null)
            singleSession = new Session(multiuser, noupdate);
        return singleSession;
    }

    /**
     * Retrieves an active session associated with the specified session identifier. This method can only be invoked when
     * {@link #singleMode} is false.
     *
     * @param session the unique session identifier to look up
     * 
     * @return the associated {@link Session} context, or {@code null} if no such session is registered
     * 
     * @throws AssertionError if {@link #singleMode} is true
     */
    public static Session getSession(String session) {
        assert !singleMode;
        return sessionsMap.get(session);
    }

    /**
     * Creates and registers a new session associated with the specified identifier if it does not already exist. This method can
     * only be invoked when {@link #singleMode} is false.
     *
     * @param session the unique session identifier to register
     * 
     * @throws AssertionError if {@link #singleMode} is true
     */
    public static void setSession(String session) {
        assert !singleMode;
        sessionsMap.putIfAbsent(session, new Session(session));
    }
}
