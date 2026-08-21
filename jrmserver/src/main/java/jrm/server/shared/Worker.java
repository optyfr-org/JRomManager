package jrm.server.shared;

import jrm.server.shared.actions.ProgressActions;
import lombok.Getter;

/**
 * Worker that runs a background task on a virtual thread and can report progress to the client. The progress actions are set by
 * the task itself (via {@code session.getWorker().progress = ...}) once it starts running.
 * <p>
 * This class composes a {@link Thread} rather than extending it, following the recommendation in <i>Effective Java</i> to prefer
 * composition over inheritance for thread-like abstractions. The underlying thread is a virtual thread, which is well-suited for
 * the I/O-bound work (file scanning, DAT import, torrent checking) that these workers perform.
 */
public class Worker {

    /**
     * The progress actions to report progress to the client. Set by the task when it starts running. Marked {@code volatile}
     * so request-handling threads can safely observe the progress handler assigned by the worker thread.
     *
     * @return the progress actions to report progress to the client
     */
    public @Getter volatile ProgressActions progress = null;

    /**
     * The target to run in the worker thread.
     */
    private final Runnable target;

    /**
     * The underlying virtual thread, created and started by {@link #start()}. Remains {@code null} until {@link #start()} is
     * called.
     */
    private Thread thread = null;

    /**
     * Creates a new worker with the given target. The worker is not started until {@link #start()} is called.
     *
     * @param target the target to run in the worker thread
     */
    public Worker(Runnable target) {
        this.target = target;
    }

    /**
     * Starts the worker on a new virtual thread.
     */
    public void start() {
        thread = Thread.startVirtualThread(target);
    }

    /**
     * Tests whether the worker's thread is alive.
     *
     * @return {@code true} if the worker has been started and its thread is still alive; {@code false} otherwise
     */
    public boolean isAlive() {
        return thread != null && thread.isAlive();
    }

}
