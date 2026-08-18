package jrm.misc;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
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
import java.util.concurrent.atomic.AtomicLong;
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

    /**
     * Tracks the historic maximum count of concurrently active executing tasks.
     */
    private final AtomicLong maxActive = new AtomicLong();

    /**
     * Atomic counter logging the cumulative quantity of tasks completed.
     */
    private final AtomicLong count = new AtomicLong();

    /**
     * Registry mapping active virtual thread identifiers to their respective physical UI reporting offsets.
     */
    private final HashMap<Long, Integer> activeThreads = new HashMap<>();

    /**
     * Pool of released offset indexes recycled for new worker tasks.
     */
    private final Deque<Integer> freeOffsets = new ArrayDeque<>();

    /**
     * Semaphore to enforce concurrency limits on virtual thread executions.
     */
    private final Semaphore semaphore;

    /**
     * The executable logic applied to each stream element.
     */
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
            Log.debug(() -> name + "-%d : %d vthreads for %d tasks in %s".formatted(poolNumber.get(), maxActive.get(), count.get(),
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
        synchronized (activeThreads) {
            final var id = Thread.currentThread().threadId();
            final var offset = activeThreads.get(id);
            if (offset == null)
                return -1;
            return offset;
        }
    }

    @Override
    public int[] freeOffsets() {
        synchronized (activeThreads) {
            return freeOffsets.stream().mapToInt(i -> i).toArray();
        }
    }

    /**
     * Callable task wrapper used to process elements on virtual threads, reserving and recycling physical reporting offsets.
     */
    @RequiredArgsConstructor
    private class CallableWith implements Callable<Void> {
        /**
         * The target user payload unit to process.
         */
        private final K entry;

        /**
         * Reserves a reporting offset for the executing virtual thread.
         * 
         * @return the virtual thread identifier
         */
        private long allocOffset() {
            synchronized (activeThreads) {
                final var id = Thread.currentThread().threadId();
                final var offset = freeOffsets.poll();
                activeThreads.put(id, offset == null ? activeThreads.size() : offset);
                final var currentCount = activeThreads.size();
                if (maxActive.get() < currentCount)
                    maxActive.set(currentCount);
                return id;
            }
        }

        /**
         * Releases and recycles the reporting offset assigned to a completed virtual thread task.
         * 
         * @param id the unique thread identifier
         */
        private void freeOffset(long id) {
            synchronized (activeThreads) {
                count.incrementAndGet();
                freeOffsets.add(activeThreads.remove(id));
            }
        }

        /**
         * Standard task callable execution point that acquires the concurrency semaphore and invokes the user action block.
         * 
         * @return {@code null} on successful execution
         * 
         * @throws Exception if an error occurs during execution
         */
        @Override
        public Void call() throws Exception {
            semaphore.acquire();
            try {
                final var id = allocOffset();
                try {
                    calledWith.call(entry);
                } finally {
                    freeOffset(id);
                }
            } finally {
                semaphore.release();
            }
            return null;
        }
    }

}
