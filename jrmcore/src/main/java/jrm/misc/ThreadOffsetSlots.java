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

	void freeOffset(long id) {
		synchronized (activeThreads) {
			count.incrementAndGet();
			freeOffsets.add(activeThreads.remove(id));
		}
	}

	long getMaxActive() {
		return maxActive.get();
	}

	long getCount() {
		return count.get();
	}
}
