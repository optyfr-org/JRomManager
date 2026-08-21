package jrm.server.shared;

import java.io.Closeable;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.AtomicReference;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingDeque;

import jrm.batch.Compressor.FileResult;
import jrm.batch.TrntChkReport;
import jrm.profile.report.Report;
import jrm.security.Session;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a web session for a client connected to the JRomManager server. This class extends {@link Session} to provide
 * web-specific functionality, including long-polling message queues, worker thread management, and caching of profile and
 * compressor lists.
 * <p>
 * Each web session is associated with an HTTP session and maintains state information such as:
 * </p>
 * <ul>
 * <li>A blocking deque for asynchronous message delivery to the client via long-polling</li>
 * <li>A reference to the worker thread performing background operations for this session</li>
 * <li>Temporary report objects for scan results and torrent check operations</li>
 * <li>Cached profile and compressor lists for efficient access during batch operations</li>
 * <li>Timestamp tracking for session expiration and inactivity detection</li>
 * </ul>
 * <p>
 * Web sessions are managed in a global registry ({@link #allSessions}) that allows the server to track all active sessions and
 * perform bulk operations such as graceful shutdown. When a session is closed (either due to client disconnect or server shutdown),
 * it removes itself from this registry and signals any waiting long-polling clients.
 * </p>
 * <p>
 * This class implements both {@link Closeable} and {@link Serializable} to support proper resource cleanup and session persistence
 * across server restarts. Transient fields (worker thread, temporary reports, and caches) are not serialized and must be
 * reinitialized after deserialization.
 * </p>
 *
 * @since 1.0
 * 
 * @see Session
 * @see Worker
 * @see Closeable
 */
public class WebSession extends Session implements Closeable, Serializable {
    /**
     * The serialization version identifier for this class. Used to verify compatibility during deserialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Global registry of all active web sessions, mapped by session ID.
     * <p>
     * This concurrent map allows thread-safe access from multiple threads: the servlet container thread (session
     * creation/destruction), worker threads (message generation), and the shutdown hook (bulk close).
     * </p>
     */
    @Getter(lombok.AccessLevel.PACKAGE)
    private static Map<String, WebSession> allSessions = new ConcurrentHashMap<>();

    /**
     * Global termination flag indicating whether the server is shutting down. When {@code true}, all sessions should stop their
     * work and close themselves gracefully.
     *
     * @return {@code true} if the server is terminating, {@code false} otherwise
     */
    private static @Getter @Setter(lombok.AccessLevel.PACKAGE) boolean terminate = false;

    /**
     * Blocking deque for delivering messages to the client via long-polling.
     * <p>
     * Worker threads add messages to this deque, and the long-polling servlet takes messages from it to send to the client. The
     * blocking nature allows the servlet to efficiently wait for new messages without busy polling. An empty string is used as a
     * sentinel to signal session closure.
     * </p>
     *
     * @return the message deque for client communication
     */
    private @Getter BlockingDeque<String> lprMsg = new LinkedBlockingDeque<>();

    /**
     * The worker thread currently executing operations for this session.
     * <p>
     * This reference is set by the {@code ProgressReporter} when a background operation starts and is used to interrupt the worker
     * if the session is closed prematurely. The field is transient because threads cannot be serialized. Access is synchronized to
     * coordinate lifecycle operations (starting, querying, and clearing) across request-handling and worker threads.
     * </p>
     *
     * @return the active worker thread, or {@code null} if no operation is running
     */
    private transient Worker worker = null;

    /**
     * Returns the worker thread currently executing operations for this session.
     *
     * @return the active worker thread, or {@code null} if no operation is running
     */
    public synchronized Worker getWorker() {
        return worker;
    }

    /**
     * Sets the worker thread for this session. Called when a background operation begins.
     *
     * @param worker the worker thread to associate with this session
     * 
     * @return the worker thread that was set
     */
    public synchronized Worker setWorker(Worker worker) {
        this.worker = worker;
        return this.worker;
    }

    /**
     * Timestamp of the last client action, used for session expiration.
     * <p>
     * Updated whenever the client performs an action (e.g., makes a request). The server can check this value periodically to
     * identify inactive sessions and close them to free resources. Initialized to the current time when the session is created.
     * </p>
     *
     * @param lastAction the timestamp to set as the last action time
     * 
     * @return the timestamp of the last client action
     */
    private @Getter @Setter Instant lastAction = Instant.now();

    /**
     * Temporary scan report being generated for this session.
     * <p>
     * Holds intermediate results during a profile scan operation. Cleared once the report is sent to the client. This field is
     * transient because reports are not persisted across server restarts.
     * </p>
     *
     * @param tmpReport the temporary report to set
     * 
     * @return the temporary scan report, or {@code null} if no scan is active
     */
    private transient @Getter @Setter Report tmpReport = null;

    /**
     * Temporary torrent check report being generated for this session.
     * <p>
     * Holds intermediate results during a torrent verification operation. Cleared once the report is sent to the client. This field
     * is transient because reports are not persisted across server restarts.
     * </p>
     *
     * @param tmpTCReport the temporary torrent check report to set
     * 
     * @return the temporary torrent check report, or {@code null} if no check is active
     */
    private transient @Getter @Setter TrntChkReport tmpTCReport = null;

    /**
     * Cache of profile lists generated for this session, keyed by client- assigned ID.
     * <p>
     * Stores file paths to profile lists that have been generated by worker threads. The client can retrieve these lists using the
     * ID. Cleared when the session closes. This field is transient because file paths may not be valid after server restart.
     * </p>
     * <p>
     * Implemented as a {@link ConcurrentSkipListMap} so that worker thread writes and client reads do not require external
     * synchronization.
     * </p>
     */
    private final transient AtomicReference<ConcurrentSkipListMap<Integer, Path>> cachedProfileList = new AtomicReference<>();

    /**
     * Cache of compressor results for this session, keyed by file identifier.
     * <p>
     * Stores results from batch compression operations. The client can query these results to track progress. Cleared when the
     * session closes. This field is transient because compression state is not persisted.
     * </p>
     * <p>
     * Implemented as a {@link ConcurrentSkipListMap} so that worker thread writes and client reads do not require external
     * synchronization.
     * </p>
     *
     * @return the cached compressor results map
     */
    private transient @Getter ConcurrentSkipListMap<String, FileResult> cachedCompressorList = new ConcurrentSkipListMap<>();

    /**
     * Creates a new web session for single-session mode.
     * <p>
     * Each HTTP session gets its own isolated {@link WebSession} instance. The session is registered in the global
     * {@link #allSessions} map for tracking and shutdown coordination.
     * </p>
     *
     * @param sessionId the unique HTTP session identifier
     */
    public WebSession(String sessionId) {
        super(sessionId);
        allSessions.put(sessionId, this);
    }

    /**
     * Creates a new web session for multi-session mode with user and role information.
     * <p>
     * In multi-session mode, multiple HTTP sessions can share state through a common session context. The session is registered in
     * the global {@link #allSessions} map for tracking and shutdown coordination.
     * </p>
     *
     * @param sessionId the unique HTTP session identifier
     * @param user the username for this session, or {@code null} until login assigns one
     * @param roles the roles assigned to this session; ignored when {@code user} is {@code null}
     */
    public WebSession(String sessionId, String user, String[] roles) {
        super(sessionId, user, roles);
        allSessions.put(sessionId, this);
    }

    /**
     * Closes this web session and releases associated resources.
     * <p>
     * This method performs the following cleanup actions:
     * </p>
     * <ol>
     * <li>If the message deque is empty, adds an empty string sentinel to signal any waiting long-polling clients that the session
     * has closed.</li>
     * <li>Removes this session from the global {@link #allSessions} registry to prevent further access and allow garbage
     * collection.</li>
     * </ol>
     * <p>
     * This method is called automatically when the HTTP session is destroyed by the servlet container or when {@link #closeAll()}
     * is invoked during server shutdown.
     * </p>
     */
    @Override
    public void close() {
        if (lprMsg.isEmpty())
            lprMsg.add("");
        allSessions.remove(getSessionId());
    }

    /**
     * Closes all active web sessions and sets the global termination flag.
     * <p>
     * This method is called during server shutdown to gracefully terminate all sessions. It sets {@link #terminate} to {@code true}
     * to signal worker threads to stop, then iterates through all registered sessions and invokes {@link #close()} on each one.
     * </p>
     * <p>
     * Worker threads should periodically check {@link #isTerminate()} and stop their operations if it returns {@code true}.
     * </p>
     */
    public static void closeAll() {
        terminate = true;
        allSessions.forEach((_, s) -> s.close());
    }

    /**
     * Stores a profile list in the session cache.
     * <p>
     * Called by worker threads after generating a profile list. The client can later retrieve the list using the provided ID.
     * </p>
     *
     * @param id the client-assigned identifier for the profile list
     * @param path the filesystem path where the profile list is stored
     */
    public void putProfileList(Integer id, Path path) {
        cachedProfileList.get().put(id, path);
    }

    /**
     * Removes a profile list from the session cache.
     * <p>
     * Called when a profile list is no longer needed or has been downloaded by the client.
     * </p>
     *
     * @param id the identifier of the profile list to remove
     */
    public void removeProfileList(Integer id) {
        cachedProfileList.get().remove(id);
    }

    /**
     * Initializes a new profile list cache for this session.
     * <p>
     * Called at the start of a profile list generation operation to ensure a clean cache state. Any previously cached lists are
     * discarded.
     * </p>
     */
    public void newProfileList() {
        cachedProfileList.set(new ConcurrentSkipListMap<>());
    }

    /**
     * Returns the highest key in the profile list cache, or 0 if the cache is empty or not initialized.
     * <p>
     * Used to generate sequential IDs for new profile lists.
     * </p>
     *
     * @return the last profile list key, or 0 if no lists are cached
     */
    public Integer getLastProfileListKey() {
        final var list = cachedProfileList.get();
        return list != null && !list.isEmpty() ? list.lastKey() : 0;
    }

    /**
     * Retrieves a profile list from the session cache.
     * <p>
     * Called by the client to obtain the filesystem path for a previously generated profile list.
     * </p>
     *
     * @param id the identifier of the profile list to retrieve
     * 
     * @return the filesystem path to the profile list, or {@code null} if not found
     */
    public Path getProfileList(Integer id) {
        return cachedProfileList.get().get(id);
    }
}
