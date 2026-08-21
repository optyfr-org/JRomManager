package jrm.server.shared.actions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.time.DurationFormatUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.progress.ProgressInputStream;
import jrm.misc.Log;
import jrm.misc.OffsetProvider;
import jrm.server.shared.actions.ProgressActions.SetFullProgress.Data.PB;

/**
 * WebSocket-based implementation of the {@link ProgressHandler} interface for broadcasting progress updates to web clients.
 * <p>
 * This class translates progress tracking operations into JSON WebSocket messages that are sent to connected clients. It maintains
 * the state of three progress bars (primary, secondary, tertiary), thread information, and error messages, serializing these into
 * JSON objects and transmitting them via the WebSocket connection.
 * </p>
 * <p>
 * <b>WebSocket Protocol:</b>
 * </p>
 * <p>
 * This class sends the following outgoing WebSocket messages:
 * </p>
 * <ul>
 * <li><b>Progress</b> - Initial progress dialog open notification</li>
 * <li><b>Progress.setFullProgress</b> - Complete progress state including all progress bars, thread info, and messages</li>
 * <li><b>Progress.setInfos</b> - Thread count and sub-info configuration</li>
 * <li><b>Progress.extendInfos</b> - Thread count extension notification</li>
 * <li><b>Progress.clearInfos</b> - Clear all progress information</li>
 * <li><b>Progress.canCancel</b> - Cancellation capability flag</li>
 * <li><b>Progress.close</b> - Progress dialog close with error list</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> This class uses synchronized blocks to protect access to shared state (thread info arrays, offset provider)
 * when multiple worker threads update progress concurrently. WebSocket message sending is not synchronized as the underlying
 * WebSocket connection handles concurrent writes.
 * </p>
 * <p>
 * <b>Performance Optimization:</b> Progress updates are sent conditionally to reduce WebSocket traffic:
 * </p>
 * <ul>
 * <li>Full state is sent when progress bars become visible or reach completion</li>
 * <li>Optional sending is used for intermediate updates to avoid blocking if the client is slow</li>
 * <li>Percentage changes are only sent when the integer percentage value changes</li>
 * </ul>
 *
 * @author optyfr
 * 
 * @see ProgressHandler
 * @see ActionsMgr
 */
public class ProgressActions implements ProgressHandler {
    /** Time format pattern for displaying duration as hours:minutes:seconds. */
    private static final String HH_MM_SS = "HH:mm:ss";

    /** Format string for displaying elapsed time vs total time (e.g., "00:05:23 / 00:10:45"). */
    private static final String S_OF_S = "%s / %s";

    /** Default time display when no progress has been made ("--:--:-- / --:--:--"). */
    private static final String HH_MM_SS_OF_HH_MM_SS_NONE = "--:--:-- / --:--:--";

    /** The {@link ActionsMgr} instance used for sending WebSocket messages to the client. */
    private ActionsMgr ws;

    /** List of error messages accumulated during the operation, sent to client on close. Thread-safe for concurrent adds. */
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    /** Flag indicating whether the operation has been cancelled by the user. */
    private final AtomicBoolean cancel = new AtomicBoolean(false);

    /** Flag indicating whether the operation supports cancellation. Visible across threads. */
    private volatile boolean canCancel = true;

    /** Gson instance for JSON serialization, configured to exclude transient fields. */
    private Gson gson;

    /**
     * Data transfer object for the {@code "Progress.setFullProgress"} WebSocket message.
     * <p>
     * This message contains the complete progress state including all three progress bars, thread information, and info/sub-info
     * arrays. It is serialized to JSON and sent to the client whenever progress state changes.
     * </p>
     */
    static final class SetFullProgress {
        /**
         * Nested data structure containing all progress state information.
         */
        static final class Data {
            /**
             * Progress bar state container for a single progress bar.
             * <p>
             * Each progress bar has visibility, paint, indeterminate, range, value, percentage, message, and time-left display
             * fields.
             * </p>
             */
            static final class PB {
                /** Whether the progress bar is visible on the client UI. */
                boolean visibility = false;

                /** Whether to paint the percentage string on the progress bar. */
                boolean stringPainted = false;

                /** Whether the progress bar is in indeterminate mode (unknown progress). */
                boolean indeterminate = false;

                /** Maximum value of the progress bar range. */
                int max = 100;

                /** Current value of the progress bar. */
                int val = 0;

                /** Current percentage completion (0.0 to 100.0). */
                double perc = 0;

                /** Optional message to display on or near the progress bar. */
                String msg = null;

                /** Formatted time remaining and total time string (e.g., "00:05:23 / 00:10:45"). */
                String timeleft;

                /** Timestamp when progress tracking started (used for time-left calculation). */
                transient long startTime = 0; // NOSONAR
            }

            /** Current number of active worker threads. */
            int threadCnt = 1;

            /** Whether each thread has its own sub-info display, or all threads share one. */
            Boolean multipleSubInfos = false;

            /** Array of info messages, one per thread (or fewer if threads exceed array size). */
            String[] infos = { null };

            /** Array of sub-info messages, one per thread if multipleSubInfos is true, otherwise single element. */
            String[] subinfos = { null };

            /** Primary progress bar state. */
            final PB pb1 = new PB();

            /** Secondary progress bar state. */
            final PB pb2 = new PB();

            /** Tertiary progress bar state. */
            final PB pb3 = new PB();
        }

        /** WebSocket command name for this message type. */
        static final String cmd = "Progress.setFullProgress"; // NOSONAR

        /** Progress state data to send to the client. */
        final Data params;

        /**
         * Constructs a new setFullProgress message with the specified data.
         *
         * @param data the progress state data to include in the message
         */
        SetFullProgress(Data data) {
            this.params = data;
        }
    }

    /**
     * Data transfer object for the {@code "Progress.setInfos"} WebSocket message.
     * <p>
     * This message notifies the client of the thread count and sub-info configuration, allowing the client to allocate the
     * appropriate number of info display slots.
     * </p>
     */
    static final class SetInfos {
        /** WebSocket command name for this message type. */
        static final String cmd = "Progress.setInfos"; // NOSONAR

        /** Thread configuration data to send to the client. */
        final Data params;

        /**
         * Nested data structure containing thread configuration information.
         */
        static final class Data {
            /** Current number of active worker threads. */
            int threadCnt = 1;

            /** Whether each thread has its own sub-info display. */
            Boolean multipleSubInfos = false;

            /**
             * Constructs a new thread configuration with the specified parameters.
             *
             * @param threadCnt the number of active worker threads
             * @param multipleSubInfos whether each thread has its own sub-info display
             */
            Data(int threadCnt, Boolean multipleSubInfos) {
                this.threadCnt = threadCnt;
                this.multipleSubInfos = multipleSubInfos;
            }
        }

        /**
         * Constructs a new setInfos message with the specified data.
         *
         * @param data the thread configuration data to include in the message
         */
        SetInfos(Data data) {
            this.params = data;
        }

    }

    /**
     * Data transfer object for the {@code "Progress.extendInfos"} WebSocket message.
     * <p>
     * This message notifies the client that the thread count has increased and the info/sub-info arrays have been extended to
     * accommodate more threads.
     * </p>
     */
    static final class ExtendInfos {
        /** WebSocket command name for this message type. */
        static final String cmd = "Progress.extendInfos"; // NOSONAR

        /** Thread extension data to send to the client. */
        final Data params;

        /**
         * Nested data structure containing thread extension information.
         */
        static final class Data {
            /** New number of active worker threads (increased from previous count). */
            int threadCnt = 1;

            /** Whether each thread has its own sub-info display. */
            Boolean multipleSubInfos = false;

            /**
             * Constructs a new thread extension configuration with the specified parameters.
             *
             * @param threadCnt the new number of active worker threads
             * @param multipleSubInfos whether each thread has its own sub-info display
             */
            Data(int threadCnt, Boolean multipleSubInfos) {
                this.threadCnt = threadCnt;
                this.multipleSubInfos = multipleSubInfos;
            }
        }

        /**
         * Constructs a new extendInfos message with the specified data.
         *
         * @param data the thread extension data to include in the message
         */
        ExtendInfos(Data data) {
            this.params = data;
        }

    }

    /**
     * Data transfer object for the {@code "Progress.canCancel"} WebSocket message.
     * <p>
     * This message notifies the client whether the current operation supports cancellation. When canCancel is false, the client
     * should disable or hide the cancel button.
     * </p>
     */
    static final class CanCancel {
        /** WebSocket command name for this message type. */
        static final String cmd = "Progress.canCancel"; // NOSONAR

        /** Cancellation capability data to send to the client. */
        final Data params;

        /**
         * Nested data structure containing cancellation capability information.
         */
        static final class Data {
            /** Whether the operation can be cancelled. */
            final boolean canCancel;

            /**
             * Constructs a new cancellation capability configuration.
             *
             * @param canCancel whether the operation can be cancelled
             */
            Data(boolean canCancel) {
                this.canCancel = canCancel;
            }
        }

        /**
         * Constructs a new canCancel message with the specified capability flag.
         *
         * @param canCancel whether the operation can be cancelled
         */
        CanCancel(boolean canCancel) {
            this.params = new Data(canCancel);
        }

    }

    /**
     * Data transfer object for the {@code "Progress.clearInfos"} WebSocket message.
     * <p>
     * This message notifies the client to clear all info and sub-info displays. It contains no data payload, only the command name.
     * </p>
     */
    static final class ClearInfos {
        /**
         * Private constructor to prevent instantiation. This class is used only for its static command name constant.
         */
        private ClearInfos() {

        }

        /** WebSocket command name for this message type. */
        static final String cmd = "Progress.clearInfos"; // NOSONAR
    }

    /**
     * Data transfer object for the {@code "Progress"} WebSocket message.
     * <p>
     * This message notifies the client to open the progress dialog. It contains no data payload, only the command name.
     * </p>
     */
    static final class Open {
        /**
         * Private constructor to prevent instantiation. This class is used only for its static command name constant.
         */
        private Open() {

        }

        /** WebSocket command name for this message type (opens progress dialog). */
        static final String cmd = "Progress"; // NOSONAR
    }

    /**
     * Data transfer object for the {@code "Progress.close"} WebSocket message.
     * <p>
     * This message notifies the client to close the progress dialog and displays any accumulated error messages. If the errors list
     * is empty, the client may close silently without showing an error dialog.
     * </p>
     */
    static final class Close {
        /** WebSocket command name for this message type. */
        static final String cmd = "Progress.close"; // NOSONAR

        /** Error data to send to the client. */
        final Data params;

        /**
         * Nested data structure containing error information.
         */
        static final class Data {
            /** Array of error messages to display, or null if no errors occurred. */
            String[] errors = null;

            /**
             * Constructs a new error data structure from the specified error list.
             *
             * @param errors the list of error messages to convert to an array
             */
            public Data(List<String> errors) {
                this.errors = errors.toArray(String[]::new);
            }
        }

        /**
         * Constructs a new close message with the specified error list.
         *
         * @param errors the list of error messages to include in the close notification
         */
        public Close(List<String> errors) {
            this.params = new Data(errors);
        }
    }

    /** Shared progress state data object that is serialized and sent to clients. */
    private final SetFullProgress.Data data = new SetFullProgress.Data();

    /**
     * Constructs a new {@code ProgressActions} instance and sends the progress dialog open message.
     * <p>
     * This constructor initializes the Gson serializer with transient field exclusion (to avoid serializing the startTime field in
     * PB) and immediately sends a {@code "Progress"} message to open the progress dialog on the client.
     * </p>
     *
     * @param ws the {@link ActionsMgr} instance used for sending WebSocket messages
     */
    public ProgressActions(ActionsMgr ws) {
        this.ws = ws;
        this.gson = new GsonBuilder().excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT).create();
        sendOpen();
    }

    /**
     * Sends the progress dialog open message to the client.
     * <p>
     * This method sends a {@code "Progress"} command to notify the client to open the progress dialog. If the WebSocket connection
     * is not open, the message is silently ignored.
     * </p>
     */
    private void sendOpen() {
        try {
            if (ws.isOpen())
                ws.send(gson.toJson(new Open())); // NOSONAR
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Reloads the progress handler with a new WebSocket connection.
     * <p>
     * This method is called when the WebSocket connection is re-established after a disconnect. It updates the internal WebSocket
     * reference and re-sends the current progress state to synchronize the client UI with the server state.
     * </p>
     * <p>
     * The reload sequence sends:
     * </p>
     * <ol>
     * <li>Progress dialog open message</li>
     * <li>Thread info configuration</li>
     * <li>Full progress state (forced send to ensure client receives it)</li>
     * </ol>
     *
     * @param ws the new {@link ActionsMgr} instance for the re-established connection
     */
    public void reload(ActionsMgr ws) {
        this.ws = ws;
        sendOpen();
        sendSetInfos();
        sendSetProgress(0, true);
    }

    /**
     * Cleans up thread info arrays by clearing slots associated with recycled thread offsets.
     * <p>
     * This method is called before sending progress updates to ensure that info slots for threads that have completed and recycled
     * their offsets are cleared. It uses the {@link OffsetProvider} to determine which offsets are available (recycled) and clears
     * the corresponding entries in the infos and subinfos arrays.
     * </p>
     * <p>
     * This cleanup prevents stale thread messages from remaining visible after threads complete.
     * </p>
     */
    private synchronized void cleanup() {
        if (offsetProvider != null) {
            for (final var offset : offsetProvider.freeOffsets()) {
                if (offset < data.infos.length) {
                    data.infos[offset] = "";
                    if (data.infos.length == data.subinfos.length)
                        data.subinfos[offset] = "";
                }
            }
        }
    }

    /**
     * Sends a progress state update message to the client.
     * <p>
     * This method decides whether to send a full progress state or use optional sending based on the progress bar state and the
     * force flag. The decision logic is:
     * </p>
     * <ul>
     * <li>If pb == 1, cleanup thread info arrays first</li>
     * <li>If force is true, send full state unconditionally</li>
     * <li>If no progress bars are visible, send full state</li>
     * <li>If the specified progress bar just reached completion (val == max), send full state</li>
     * <li>Otherwise, use optional sending (may be skipped if client is slow)</li>
     * </ul>
     * <p>
     * After sending, the pb1 message is cleared to avoid resending the same message.
     * </p>
     *
     * @param pb the progress bar number (1, 2, or 3) that triggered the update
     * @param force if true, forces a full state send regardless of progress bar state
     */
    private void sendSetProgress(final int pb, final boolean force) {
        try {
            if (pb == 1)
                cleanup();
            if (force)
                ws.send(gson.toJson(new SetFullProgress(data)));
            else if (!data.pb1.visibility && !data.pb2.visibility && !data.pb3.visibility)
                ws.send(gson.toJson(new SetFullProgress(data)));
            else if (pb == 1 && data.pb1.visibility && !data.pb1.indeterminate && data.pb1.val > 0 && data.pb1.max == data.pb1.val)
                ws.send(gson.toJson(new SetFullProgress(data)));
            else if (pb == 2 && data.pb2.visibility && !data.pb2.indeterminate && data.pb2.val > 0 && data.pb2.max == data.pb2.val)
                ws.send(gson.toJson(new SetFullProgress(data)));
            else if (pb == 3 && data.pb3.visibility && !data.pb3.indeterminate && data.pb3.val > 0 && data.pb3.max == data.pb3.val)
                ws.send(gson.toJson(new SetFullProgress(data)));
            else
                ws.sendOptional(gson.toJson(new SetFullProgress(data)));
            data.pb1.msg = null;
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sets the thread information for progress tracking.
     * <p>
     * This method configures the number of worker threads and whether each thread has its own sub-info display. It allocates the
     * info and sub-info arrays to the appropriate size and sends a {@code "Progress.setInfos"} message to the client.
     * </p>
     * <p>
     * If threadCnt is less than or equal to 0, it defaults to the number of available processors.
     * </p>
     *
     * @param threadCnt the number of worker threads (or &lt;= 0 to use available processors)
     * @param multipleSubInfos if true, each thread gets its own sub-info display; if false, all threads share one; if null, no
     *        sub-infos are allocated
     */
    @Override
    public synchronized void setInfos(int threadCnt, Boolean multipleSubInfos) {
        this.data.threadCnt = threadCnt <= 0 ? Runtime.getRuntime().availableProcessors() : threadCnt;
        this.data.multipleSubInfos = multipleSubInfos;
        this.data.infos = new String[this.data.threadCnt];
        if (multipleSubInfos == null)
            this.data.subinfos = new String[0];
        else
            this.data.subinfos = new String[multipleSubInfos.booleanValue() ? this.data.threadCnt : 1];
        sendSetInfos();
    }

    /**
     * Sends the thread info configuration message to the client.
     * <p>
     * This method sends a {@code "Progress.setInfos"} command with the current thread count and multipleSubInfos configuration. If
     * the WebSocket connection is not open, the message is silently ignored.
     * </p>
     */
    private void sendSetInfos() {
        try {
            if (ws.isOpen()) {
                ws.send(gson.toJson(new SetInfos(new SetInfos.Data(data.threadCnt, data.multipleSubInfos))));
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Extends the thread info arrays to accommodate additional threads.
     * <p>
     * This method is called when the offset provider returns an offset that exceeds the current thread count, indicating that more
     * threads are active than initially allocated. It extends the infos and subinfos arrays using
     * {@link Arrays#copyOf(Object[], int)} and sends a {@code "Progress.extendInfos"} message to notify the client.
     * </p>
     *
     * @param threadCnt the new thread count (must be greater than the current count)
     */
    private synchronized void extendInfos(int threadCnt) {
        this.data.threadCnt = threadCnt;
        this.data.infos = Arrays.copyOf(this.data.infos, threadCnt);
        if (Boolean.TRUE.equals(this.data.multipleSubInfos))
            this.data.subinfos = Arrays.copyOf(this.data.subinfos, threadCnt);
        sendExtendInfos();
    }

    /**
     * Sends the thread extension notification message to the client.
     * <p>
     * This method sends a {@code "Progress.extendInfos"} command with the new thread count and multipleSubInfos configuration. If
     * the WebSocket connection is not open, the message is silently ignored.
     * </p>
     */
    private void sendExtendInfos() {
        try {
            if (ws.isOpen()) {
                ws.send(gson.toJson(new ExtendInfos(new ExtendInfos.Data(data.threadCnt, data.multipleSubInfos))));
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Clears all info and sub-info messages.
     * <p>
     * This method sets all entries in the infos and subinfos arrays to null and clears the pb2 message, then sends a
     * {@code "Progress.clearInfos"} message to notify the client to clear all displays.
     * </p>
     */
    @Override
    public synchronized void clearInfos() {
        for (var i = 0; i < data.infos.length; i++)
            data.infos[i] = null;
        for (var i = 0; i < data.subinfos.length; i++)
            data.subinfos[i] = null;
        data.pb2.msg = null;
        sendClearInfos();
    }

    /**
     * Sends the clear info notification message to the client.
     * <p>
     * This method sends a {@code "Progress.clearInfos"} command with no data payload. If the WebSocket connection is not open, the
     * message is silently ignored.
     * </p>
     */
    private void sendClearInfos() {
        try {
            if (ws.isOpen())
                ws.send(gson.toJson(new ClearInfos())); // NOSONAR
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Updates the primary progress bar state.
     * <p>
     * This method updates the primary progress bar (pb1) with the provided message, value, maximum, and sub-message. It handles
     * several special cases:
     * </p>
     * <ul>
     * <li>If val is null, the progress bar state is unchanged (only message/submsg updated)</li>
     * <li>If val is negative, the progress bar is hidden</li>
     * <li>If val is 0, the progress bar enters indeterminate mode</li>
     * <li>If val is positive, the progress bar shows determinate progress with percentage</li>
     * </ul>
     * <p>
     * The message is placed in the info array at the thread's offset. The sub-message is placed in the sub-info array at the
     * thread's offset (if multipleSubInfos is true) or at index 0.
     * </p>
     *
     * @param msg the message to display (or null to keep current message)
     * @param val the current progress value (0 = indeterminate, negative = hide, positive = progress)
     * @param max the maximum progress value (or null to keep current max)
     * @param submsg the sub-message to display (or null to keep current sub-message)
     */
    @Override
    public synchronized void setProgress(String msg, Integer val, Integer max, String submsg) {
        int offset = getOffset();
        if (msg != null)
            data.infos[offset] = msg;
        var force = false;
        if (val != null) {
            if (val < 0 && data.pb1.visibility) {
                data.pb1.visibility = false;
            } else if (val >= 0 && !data.pb1.visibility) {
                data.pb1.visibility = true;
            }
            data.pb1.stringPainted = val != 0;
            data.pb1.indeterminate = val == 0;
            force = computeProgress(data.pb1, val, max, force);
            showDuration(data.pb1, val);
        }
        if (submsg != null || (val != null && val == -1)) {
            if (data.subinfos.length == 1)
                data.subinfos[0] = submsg;
            else if (data.subinfos.length > 1)
                data.subinfos[offset] = submsg;
        }
        sendSetProgress(1, force);
    }

    /**
     * Updates the secondary progress bar state.
     * <p>
     * This method updates the secondary progress bar (pb2) with the provided message, value, and maximum. If both msg and val are
     * non-null, the progress bar becomes visible. If both are null and the bar is visible, it becomes hidden.
     * </p>
     *
     * @param msg the message to display on the progress bar (or null with val to hide)
     * @param val the current progress value (0 = indeterminate, positive = progress, or null with msg to hide)
     * @param max the maximum progress value (or null to keep current max)
     */
    @Override
    public synchronized void setProgress2(String msg, Integer val, Integer max) {
        var force = false;
        if (msg != null && val != null) {
            if (!data.pb2.visibility)
                data.pb2.visibility = true;
            data.pb2.stringPainted = true/* msg != null || val > 0 */;
            data.pb2.msg = msg;
            data.pb2.indeterminate = val == 0 && msg == null;
            force = computeProgress(data.pb2, val, max, force);
            showDuration(data.pb2, val);
        } else if (data.pb2.visibility)
            data.pb2.visibility = false;
        sendSetProgress(2, force);
    }

    /**
     * Updates the tertiary progress bar state.
     * <p>
     * This method updates the tertiary progress bar (pb3) with the provided message, value, and maximum. If both msg and val are
     * non-null, the progress bar becomes visible. If both are null and the bar is visible, it becomes hidden.
     * </p>
     *
     * @param msg the message to display on the progress bar (or null with val to hide)
     * @param val the current progress value (0 = indeterminate, positive = progress, or null with msg to hide)
     * @param max the maximum progress value (or null to keep current max)
     */
    @Override
    public synchronized void setProgress3(String msg, Integer val, Integer max) {
        var force = false;
        if (msg != null && val != null) {
            if (!data.pb3.visibility)
                data.pb3.visibility = true;
            data.pb3.stringPainted = true/* msg != null || val > 0 */;
            data.pb3.msg = msg;
            data.pb3.indeterminate = val == 0 && msg == null;
            force = computeProgress(data.pb3, val, max, force);
            showDuration(data.pb3, val);
        } else if (data.pb3.visibility)
            data.pb3.visibility = false;
        sendSetProgress(3, force);
    }

    /**
     * Computes the progress percentage and determines if a forced update is needed.
     * <p>
     * This helper method updates the progress bar's value, max, percentage, and start time. It returns true if the integer
     * percentage changed (requiring a forced update to ensure the client sees the change), or false if only a minor update
     * occurred.
     * </p>
     *
     * @param pb the progress bar state to update
     * @param val the current progress value
     * @param max the maximum progress value (or null to keep current)
     * @param force the current force flag (may be overridden to true)
     * 
     * @return true if the integer percentage changed and a forced update is needed
     */
    private boolean computeProgress(final PB pb, final Integer val, final Integer max, boolean force) {
        if (max != null)
            pb.max = max;
        if (val >= 0)
            pb.val = val;
        if (val == 0)
            pb.startTime = System.currentTimeMillis();
        if (pb.val >= 0 && pb.max > 0) {
            final var perc = pb.val * 100.0f / pb.max;
            force = (int) pb.perc != (int) perc;
            pb.perc = perc;
        }
        return force;
    }

    /**
     * Gets the thread offset for the current thread.
     * <p>
     * This method uses the {@link OffsetProvider} to determine the logical offset for the calling thread in the info/sub-info
     * arrays. If no offset provider is set, it returns 0. If the offset exceeds the current array size, it calls
     * {@link #extendInfos(int)} to grow the arrays.
     * </p>
     *
     * @return the thread offset (0 if no provider, or the provider's offset)
     */
    private synchronized int getOffset() {
        if (offsetProvider != null) {
            int offset = offsetProvider.getOffset();
            if (offset < 0)
                return 0;
            if (offset >= data.threadCnt)
                extendInfos(offset + 1);
            return offset;
        }
        return 0;
    }

    /**
     * Updates the time-left display for the specified progress bar.
     * <p>
     * This method calculates the elapsed time and estimated time remaining based on the progress rate. It formats these as
     * "HH:mm:ss / HH:mm:ss" and stores them in the progress bar's timeleft field. If the progress bar has no message, it sets the
     * message to the current percentage.
     * </p>
     *
     * @param pb the progress bar state to update
     * @param val the current progress value
     */
    private void showDuration(PB pb, int val) {
        if (val > 0) {
            if (pb.msg == null)
                pb.msg = String.format("%.02f%%", pb.perc);
            final String left = DurationFormatUtils.formatDuration((System.currentTimeMillis() - pb.startTime) * (pb.max - val) / val, HH_MM_SS); // $NON-NLS-1$
            final String total = DurationFormatUtils.formatDuration((System.currentTimeMillis() - pb.startTime) * pb.max / val, HH_MM_SS); // $NON-NLS-1$
            pb.timeleft = String.format(S_OF_S, left, total); // $NON-NLS-1$
        } else
            pb.timeleft = HH_MM_SS_OF_HH_MM_SS_NONE; // $NON-NLS-1$
    }

    /**
     * Gets the current value of the primary progress bar.
     *
     * @return the current progress value for pb1
     */
    @Override
    public synchronized int getCurrent() {
        return data.pb1.val;
    }

    /**
     * Gets the current value of the secondary progress bar.
     *
     * @return the current progress value for pb2
     */
    @Override
    public synchronized int getCurrent2() {
        return data.pb2.val;
    }

    /**
     * Gets the current value of the tertiary progress bar.
     *
     * @return the current progress value for pb3
     */
    @Override
    public synchronized int getCurrent3() {
        return data.pb3.val;
    }

    /**
     * Checks if the operation has been cancelled by the user.
     *
     * @return true if the user has requested cancellation, false otherwise
     */
    @Override
    public boolean isCancel() {
        return cancel.get();
    }

    /**
     * Marks the operation as cancelled.
     * <p>
     * This method is called when the user clicks the cancel button on the client UI. It sets the cancel flag to true, which can be
     * checked by the operation via {@link #isCancel()}.
     * </p>
     */
    @Override
    public void doCancel() {
        cancel.set(true);
    }

    /**
     * Wraps an input stream with progress tracking.
     * <p>
     * This method returns a {@link ProgressInputStream} that automatically updates the primary progress bar as bytes are read from
     * the stream. The progress is calculated based on the number of bytes read vs the total expected length.
     * </p>
     *
     * @param in the input stream to wrap with progress tracking
     * @param len the total expected length of the stream in bytes
     * 
     * @return a new {@link ProgressInputStream} that tracks read progress
     */
    @Override
    public InputStream getInputStream(InputStream in, Integer len) {
        return new ProgressInputStream(in, len, this);
    }

    /**
     * Closes the progress handler and sends the close message to the client.
     * <p>
     * This method sends a {@code "Progress.close"} message with the accumulated error list. If the error list is empty, the client
     * closes the progress dialog silently. If errors are present, the client displays them to the user.
     * </p>
     */
    @Override
    public void close() {
        try {
            if (ws.isOpen()) {
                final List<String> errorsSnapshot;
                synchronized (errors) {
                    errorsSnapshot = new ArrayList<>(errors);
                }
                ws.send(gson.toJson(new Close(errorsSnapshot)));
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Checks if the operation supports cancellation.
     *
     * @return true if the operation can be cancelled, false otherwise
     */
    public boolean canCancel() {
        return canCancel;
    }

    /**
     * Sets whether the operation supports cancellation.
     * <p>
     * This method updates the canCancel flag and sends a {@code "Progress.canCancel"} message to the client, which enables or
     * disables the cancel button accordingly.
     * </p>
     *
     * @param canCancel true to enable cancellation, false to disable it
     */
    public void canCancel(boolean canCancel) {
        this.canCancel = canCancel;
        sendCanCancel();
    }

    /**
     * Sends the cancellation capability message to the client.
     * <p>
     * This method sends a {@code "Progress.canCancel"} command with the current canCancel flag. If the WebSocket connection is not
     * open, the message is silently ignored.
     * </p>
     */
    private void sendCanCancel() {
        try {
            if (ws.isOpen())
                ws.send(gson.toJson(new CanCancel(canCancel)));
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Adds an error message to the error list.
     * <p>
     * Error messages are accumulated during the operation and sent to the client when {@link #close()} is called. The client
     * displays these errors in a dialog if any are present.
     * </p>
     *
     * @param error the error message to add
     */
    @Override
    public void addError(String error) {
        errors.add(error);
    }

    /**
     * Sets progress handler options.
     * <p>
     * This implementation does not support any options and silently ignores the call. The {@link ProgressHandler} interface defines
     * this method for implementations that support features like lazy updates.
     * </p>
     *
     * @param first the first option (ignored)
     * @param rest additional options (ignored)
     */
    @Override
    public void setOptions(Option first, Option... rest) {
        // do nothing

    }

    /** Provider for thread offset information, used to map threads to info array slots. */
    private OffsetProvider offsetProvider = null;

    /**
     * Sets the offset provider for thread-to-slot mapping.
     * <p>
     * The offset provider is used in multi-threaded operations to determine which slot in the info/sub-info arrays should be used
     * for each thread's progress messages. This allows multiple threads to update progress concurrently without overwriting each
     * other's messages.
     * </p>
     *
     * @param offsetProvider the offset provider to use, or null to disable thread-specific offsets
     */
    @Override
    public void setOffsetProvider(OffsetProvider offsetProvider) {
        this.offsetProvider = offsetProvider;

    }

}
