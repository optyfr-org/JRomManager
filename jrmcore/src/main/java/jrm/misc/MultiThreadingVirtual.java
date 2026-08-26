package jrm.misc;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.DurationFormatUtils;

import jrm.aui.progress.ProgressHandler;
import lombok.RequiredArgsConstructor;

/**
 * An executor service that utilizes lightweight virtual threads to run tasks concurrently.
 * <p>
 * This class implements {@link ExecutorService} and {@link OffsetProvider}, allowing the progress handler to track active thread
 * slots using physical UI reporting offsets. It uses a semaphore to limit concurrency to prevent overwhelming system resources.
 * </p>
 * 
 * @param <K> the type of task element processed by this executor
 * 
 * @author optyfr
 */
public class MultiThreadingVirtual<K> implements ExecutorService, OffsetProvider {
    /**
     * Underlying Java virtual thread per task executor service.
     */
    private final ExecutorService service;

    /**
     * Name of the virtual thread group or pool category.
     */
    private final String name;

    /**
     * Maximum number of concurrent tasks allowed to execute simultaneously (managed by semaphore).
     */
    private final int threadLimit;

    private final ThreadOffsetSlots slots = new ThreadOffsetSlots();

    private final Semaphore semaphore;

    private final CalledWith<K> calledWith;

    /**
     * Pool counter incremented sequentially to ensure unique thread naming.
     */
    private static final AtomicInteger poolNumber = new AtomicInteger(1);

    /**
     * Constructs a new {@code MultiThreadingVirtual} executor service.
     * 
     * @param name the base name used for naming the virtual threads
     * @param progress progress tracker coupled with this executor's offset provider
     * @param nThreads concurrency limits (negative value triggers a multiple of processor counts, zero defaults to 256, positive
     *        value sets explicit limit)
     * @param calledWith the executable logic applied to each stream element
     */
    public MultiThreadingVirtual(final String name, final ProgressHandler progress, final int nThreads, final CalledWith<K> calledWith) {
        this.calledWith = calledWith;
        this.name = name;
        this.threadLimit = getThreadLimit(nThreads);
        this.semaphore = new Semaphore(threadLimit);
        this.service = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(name + "-" + poolNumber.getAndIncrement() + "-thread-", 1).factory());
        progress.setOffsetProvider(this);
    }

    /**
     * Resolves the absolute concurrency limit of virtual threads from a given requested pool size.
     * 
     * @param nThreads requested threads limit input
     * 
     * @return resolved positive integer representing the max concurrency level
     */
    private int getThreadLimit(final int nThreads) {
        if (nThreads < 0)
            return Runtime.getRuntime().availableProcessors() * (-nThreads + 1);
        if (nThreads == 0)
            return 256;
        return nThreads;
    }

    @Override
    public void execute(Runnable command) {
        service.execute(command);
    }

    @Override
    public void shutdown() {
        service.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return service.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return service.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return service.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return service.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return service.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return service.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return service.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return service.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return service.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return service.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return service.invokeAny(tasks, timeout, unit);
    }

    /**
     * Starts processing all elements from a stream according to the task and blocks until all tasks terminate or timeout.
     * 
     * @param stream the stream of objects to process
     */
    public void start(final Stream<K> stream) {
        try {
            Objects.requireNonNull(calledWith);
            final var start = System.currentTimeMillis();
            stream.forEach(entry -> submit(new CallableWith(entry))); // submit all entries from stream using a task
            shutdown(); // does not accept submission after stream as been consumed
            awaitTermination(1, TimeUnit.DAYS); // wait max for 1 day for all tasks to terminate
            Log.debug(() -> name + "-%d : %d vthreads for %d tasks in %s".formatted(poolNumber.get(), slots.getMaxActive(), slots.getCount(),
                    DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - start)));
        } catch (InterruptedException e) {
            Log.err(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (NullPointerException e) {
            Log.err(e.getMessage(), e);
        }
    }

    @Override
    public int getOffset() {
        return slots.getOffset();
    }

    @Override
    public int[] freeOffsets() {
        return slots.freeOffsets();
    }

    @RequiredArgsConstructor
    private class CallableWith implements Callable<Void> {
        private final K entry;

        @Override
        public Void call() throws Exception {
            semaphore.acquire();
            try {
                final var id = slots.allocOffset();
                try {
                    calledWith.call(entry);
                } finally {
                    slots.freeOffset(id);
                }
            } finally {
                semaphore.release();
            }
            return null;
        }
    }

}
