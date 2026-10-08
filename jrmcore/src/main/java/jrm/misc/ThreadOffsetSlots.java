package jrm.misc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ThreadOffsetSlots implements OffsetProvider {
	private final AtomicLong maxActive = new AtomicLong();
	private final AtomicLong count = new AtomicLong();
	private final HashMap<Long, Integer> activeThreads = new HashMap<>();
	private final Deque<Integer> freeOffsets = new ArrayDeque<>();

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

	long allocOffset() {
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
	 * Releases the slot held by the given thread id and reports which offset was freed.
	 *
	 * @param id the thread id previously returned by {@link #allocOffset()}
	 *
	 * @return the freed offset index, or {@code -1} when the id held no slot
	 */
	int freeOffset(long id) {
		synchronized (activeThreads) {
			count.incrementAndGet();
			final var offset = activeThreads.remove(id);
			if (offset == null)
				return -1;
			freeOffsets.add(offset);
			return offset;
		}
	}



	long getMaxActive() {
		return maxActive.get();
	}

	long getCount() {
		return count.get();
	}
}
