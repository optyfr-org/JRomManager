package jrm.misc;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import jrm.aui.progress.ProgressHandler;
import lombok.RequiredArgsConstructor;

/**
 * Custom thread pool executor offering concurrent execution of tasks with an optional adaptive load algorithm. The adaptive
 * algorithm attempts to adjust the number of active threads dynamically based on CPU consumption patterns or system load averages.
 * <p>
 * Under adaptive mode, the pool calculates thread CPU utilization over a given interval, scaling down when threads remain idle and
 * scaling up when active threads approach full saturation.
 * </p>
 * <p>
 * For system-wide load average adaptation: $$usage = \frac{load}{poolSize}$$
 * </p>
 * <p>
 * For JMX-based thread CPU time adaptation: $$load = \frac{\sum_{i=1}^{n} \left(cpuTime_{end}^{(i)} - cpuTime_{start}^{(i)}\right)
 * \times 10^{-6}}{elapsed}$$ $$usage = \frac{load}{poolSize}$$ where $cpuTime$ is in nanoseconds, $elapsed$ is in milliseconds, and
 * $n$ is the active pool size.
 * </p>
 * 
 * @param <T> the type of task element processed by this executor
 * 
 * @author optyfr
 */
public final class MultiThreading<T> extends ThreadPoolExecutor implements OffsetProvider {
    /**
     * Thread management bean used to inspect CPU execution metrics per thread.
     */
    private static final ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();

    /**
     * Operating system monitoring bean utilized to query system-wide CPU loads.
     */
    private static final OperatingSystemMXBean osmxb = ManagementFactory.getOperatingSystemMXBean();

    /**
     * Initial number of threads requested for pool allocation.
     */
    private final int nStartThreads;

    /**
     * Flag indicating whether adaptive thread scaling is enabled.
     */
    private final boolean adaptive;

    /**
     * Interval duration (in milliseconds) between successive adaptive load evaluations.
     */
    private final long interval;

    /**
     * System timestamp tracking when the last adaptive evaluation was completed.
     */
    private long time = System.currentTimeMillis();

    /**
     * Tracks the historic maximum count of concurrently active executing threads.
     */
    private final AtomicLong maxActive = new AtomicLong();

    /**
     * Atomic counter logging the cumulative quantity of tasks completed.
     */
    private final AtomicLong count = new AtomicLong();

    /**
     * Registry mapping active thread identifiers to their respective physical UI reporting offsets.
     */
    private final HashMap<Long, Integer> activeThreads = new HashMap<>();

    /**
     * Pool of released offset indexes recycled for new worker threads.
     */
    private final Deque<Integer> freeOffsets = new ArrayDeque<>();

    /**
     * The action callback or runnable wrapper used to process each stream element.
     */
    private final CalledWith<T> calledWith;

    /**
     * Registry logging the starting JMX thread CPU time for each active thread.
     */
    private final HashMap<Thread, Long> startCPUTimeByThread = new HashMap<>();

    /**
     * Constructs a new {@code MultiThreading} executor.
     * 
     * @param name the base name used to identify the thread factory group
     * @param progress progress tracker coupled with this executor's offset provider
     * @param nThreads the thread limit (negative values trigger adaptive scaling, zero requests standard system CPU cores capacity)
     * @param cw the executable logic applied to each stream element
     */
    public MultiThreading(final String name, final ProgressHandler progress, final int nThreads, final CalledWith<T> cw) {
        super(getNStartThreads(nThreads), getNStartThreads(nThreads), 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        this.nStartThreads = getNStartThreads(nThreads);
        this.adaptive = isAdaptive(nThreads);
        this.interval = 60_000; // check interval for adaptive mode (expressed in milliseconds)
        this.calledWith = cw;
        setThreadFactory(new DefaultThreadFactory(name));
        progress.setOffsetProvider(this);
    }

    /**
     * Factory generator creating custom, group-tagged worker threads.
     */
    private static class DefaultThreadFactory implements ThreadFactory {
        /**
         * Pool counter incremented sequentially to ensure unique group naming.
         */
        private static final AtomicInteger poolNumber = new AtomicInteger(1);

        /**
         * Thread group associated with all threads managed by this factory.
         */
        private final ThreadGroup group; // NOSONAR

        /**
         * Thread counter tracking thread creations within this factory group.
         */
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        /**
         * Common text prefix prefixed to all individual thread names.
         */
        private final String namePrefix;

        /**
         * Constructs a new thread factory for a given logical category name.
         * 
         * @param name the descriptive identifier of the thread pool
         */
        DefaultThreadFactory(String name) {
            group = new ThreadGroup(name + "-" + poolNumber.getAndIncrement());
            namePrefix = group.getName() + "-thread-";
        }

        /**
         * Allocates and configures a new thread inside this factory's thread group.
         * 
         * @param r the target runnable task
         * 
         * @return the configured, non-daemon thread
         */
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * Translates raw thread counts into valid starting pool size values.
     * 
     * @param nThreads raw thread target requested
     * 
     * @return positive starting pool capacity
     */
    private static int getNStartThreads(final int nThreads) {
        if (nThreads <= 0)
            return Runtime.getRuntime().availableProcessors();
        return nThreads;
    }

    /**
     * Determines if a given thread count activates adaptive profiling mode.
     * 
     * @param nThreads target thread count
     * 
     * @return {@code true} if negative, triggering adaptive scaling; {@code false} otherwise
     */
    private static boolean isAdaptive(final int nThreads) {
        return nThreads < 0;
    }

    /**
     * Sequentially submits each element of a data stream into the executor pool and blocks until all queued threads terminate or
     * timeout.
     * 
     * @param stream the source stream of data units to process
     */
    public void start(final Stream<T> stream) {
        try {
            Objects.requireNonNull(calledWith);
            stream.forEach(entry -> submit(new CallableWith(entry))); // submit all entries from stream using a task
            shutdown(); // does not accept submission after stream as been consumed
            awaitTermination(1, TimeUnit.DAYS); // wait max for 1 day for all tasks to terminate
        } catch (InterruptedException e) {
            Log.err(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (NullPointerException e) {
            Log.err(e.getMessage(), e);
        }
    }

    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        super.beforeExecute(t, r);
        if (adaptive) {
            synchronized (this) {
                startCPUTimeByThread.computeIfAbsent(t, _ -> tmxb.getCurrentThreadCpuTime());
            }
        }
    }

    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        super.afterExecute(r, t);
        if (!adaptive)
            return;
        final var elapsed = System.currentTimeMillis() - time;
        if (elapsed <= interval)
            return;
        adaptive(elapsed);
    }

    /**
     * Performs dynamic adaptive pool adjustment by querying either platform load averages or aggregating precise JMX-computed
     * thread execution times.
     * 
     * @param elapsed the exact duration in milliseconds since the last evaluation
     */
    private synchronized void adaptive(final long elapsed) {
        double load = osmxb.getSystemLoadAverage();
        if (load < 0) // sys load is not computable
            adaptiveNoLoad(elapsed);
        else
            adaptiveLoad(load);
        time = System.currentTimeMillis();
        startCPUTimeByThread.entrySet().removeIf(e -> !e.getKey().isAlive()); // cleanup dead thread from list
        startCPUTimeByThread.forEach((k, _) -> startCPUTimeByThread.put(k, tmxb.getThreadCpuTime(k.threadId()))); // reset
                                                                                                                  // startCPUTime
    }

    /**
     * Adjusts the active pool boundaries dynamically based on system load averages.
     * 
     * @param load the current system-wide load factor
     */
    private void adaptiveLoad(double load) {
        // Compute usage ratio (number between 0.0 and 1.0)
        double usage = load / getPoolSize();
        Log.info(String.format("sys load avg is %.03f so usage ratio is %.03f with %d active threads%n", load, usage, getPoolSize()));
        if (getMaximumPoolSize() == getPoolSize()) // make sure that used threads is not different than current maximum (as a
                                                   // result of a former thread reduction not yet taken into account or because we
                                                   // are near the end)
        {
            if (load > getMaximumPoolSize() + 1) {
                final var newThreadCnt = Math.max(1, getMaximumPoolSize() - 1); // sub 1 thread
                if (newThreadCnt < getMaximumPoolSize()) {
                    Log.info(String.format("setting down to %d from %d threads...%n", newThreadCnt, getMaximumPoolSize()));
                    setCorePoolSize(newThreadCnt); // core pool must be lowered first or we will get illegalArgumentException
                    setMaximumPoolSize(newThreadCnt);
                }
            } else if (load < getMaximumPoolSize() - 1) {
                final var newThreadCnt = Math.min(nStartThreads, getMaximumPoolSize() + 1); // add 1 thread
                if (newThreadCnt > getMaximumPoolSize()) {
                    Log.info(String.format("setting up to %d from %d threads...%n", newThreadCnt, getMaximumPoolSize()));
                    setMaximumPoolSize(newThreadCnt);
                    setCorePoolSize(newThreadCnt); // core pool must be augmented last or we will get illegalArgumentException
                }
            }
        } else
            Log.info(String.format("pools size of %d <> max pool size of %d...%n", getPoolSize(), getMaximumPoolSize()));
    }

    /**
     * Fallback adaptation logic for systems where system load averages are not queryable. Computes execution metrics based on
     * accumulated JMX thread CPU consumption times.
     * 
     * @param elapsed the exact measurement duration in milliseconds
     */
    private void adaptiveNoLoad(final long elapsed) {
        double load;
        // Compute cumulated CPUTime of all alive threads (number between 0.0 and n with
        // n the count of active threads)
        load = (startCPUTimeByThread.entrySet().stream().filter(e -> e.getKey().isAlive()).mapToLong(e -> tmxb.getThreadCpuTime(e.getKey().threadId()) - e.getValue()).sum()
                / 1_000_000.0 /*
                               * ns to ms
                               */) / elapsed;
        // Compute usage ratio (number between 0.0 and 1.0)
        double usage = load / getPoolSize();
        Log.info(String.format("cpu load is %.03f so usage ratio is %.03f with %d active threads%n", load, usage, getPoolSize()));
        if (getMaximumPoolSize() == getPoolSize()) // make sure that used threads is not different than current maximum (as a
                                                   // result of a former thread reduction not yet taken into account or because we
                                                   // are near the end)
        {
            final var threshold = 1.0 / getMaximumPoolSize();
            if (usage < 1.0 - threshold) // there was at least 1 unused thread during the last sample
            {
                final var newThreadCnt = Math.max(1, (int) Math.ceil(load)); // down to the rounded up used thread (equiv. to the
                                                                             // load)
                if (newThreadCnt < getMaximumPoolSize()) {
                    Log.info(() -> String.format("setting down to %d from %d threads...%n", newThreadCnt, getMaximumPoolSize()));
                    setCorePoolSize(newThreadCnt); // core pool must be lowered first or we will get illegalArgumentException
                    setMaximumPoolSize(newThreadCnt);
                }
            } else if (usage > 1.0 - 0.5 * threshold) // there was less than half of a thread unused
            {
                final var newThreadCnt = Math.min(nStartThreads, getMaximumPoolSize() + 1); // add 1 thread
                if (newThreadCnt > getMaximumPoolSize()) {
                    Log.info(String.format("setting up to %d from %d threads...%n", newThreadCnt, getMaximumPoolSize()));
                    setMaximumPoolSize(newThreadCnt);
                    setCorePoolSize(newThreadCnt); // core pool must be augmented last or we will get illegalArgumentException
                }
            }
        } else
            Log.info(String.format("pools size of %d <> max pool size of %d...%n", getPoolSize(), getMaximumPoolSize()));
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
     * Internal task wrapper converting user payloads into callable execution units that handle reporting offset reservation and
     * recycling.
     */
    @RequiredArgsConstructor
    private class CallableWith implements Callable<Void> {
        /**
         * The target user payload unit to process.
         */
        private final T entry;

        /**
         * Reserves a reporting offset for the executing thread.
         * 
         * @return the thread ID of the active thread
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
         * Releases and recycles the reporting offset assigned to a completed thread.
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
         * Standard task callable execution point that wraps user execution block.
         * 
         * @return {@code null} on successful execution
         * 
         * @throws Exception if an error occurs during execution
         */
        @Override
        public Void call() throws Exception {
            final var id = allocOffset();
            try {
                calledWith.call(entry);
            } finally {
                freeOffset(id);
            }
            return null;
        }
    }

}
