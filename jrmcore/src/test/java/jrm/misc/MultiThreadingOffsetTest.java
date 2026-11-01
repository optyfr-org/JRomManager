package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.aui.progress.ProgressHandler;

@DisplayName("MultiThreading offset lifecycle tests")
class MultiThreadingOffsetTest {

    @Nested
    @DisplayName("MultiThreading")
    class PlatformThreads {

        @Test
        @DisplayName("should recycle offset when calledWith throws")
        void shouldRecycleOffsetWhenCalledWithThrows() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            try (var pool = new MultiThreading<>("offset-throw", progress, 1, _ -> {
                throw new IllegalStateException("boom");
            })) {
                pool.start(Stream.of("entry"));

                assertThat(pool.freeOffsets()).containsExactly(0);
                assertThat(pool.getOffset()).isEqualTo(-1);
            }
        }

        @Test
        @DisplayName("should recycle offset after successful call")
        void shouldRecycleOffsetAfterSuccessfulCall() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            final var seen = new AtomicInteger(-2);
            final var holder = new AtomicReference<MultiThreading<String>>();
            try (final var pool = new MultiThreading<String>("offset-ok", progress, 1, _ -> seen.set(holder.get().getOffset()))) {
                holder.set(pool);

                pool.start(Stream.of("entry"));

                assertThat(seen).hasValue(0);
                assertThat(pool.freeOffsets()).containsExactly(0);
                assertThat(pool.getOffset()).isEqualTo(-1);
            }
        }
    }

    @Nested
    @DisplayName("MultiThreadingVirtual")
    class VirtualThreads {

        @Test
        @DisplayName("should recycle offset when calledWith throws")
        void shouldRecycleOffsetWhenCalledWithThrows() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            try (var pool = new MultiThreadingVirtual<>("voffset-throw", progress, 1, _ -> {
                throw new IllegalStateException("boom");
            })) {
                pool.start(Stream.of("entry"));

                assertThat(pool.freeOffsets()).containsExactly(0);
                assertThat(pool.getOffset()).isEqualTo(-1);
            }
        }

        @Test
        @DisplayName("should recycle offset after successful call")
        void shouldRecycleOffsetAfterSuccessfulCall() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            final var seen = new AtomicInteger(-2);
            final var holder = new AtomicReference<MultiThreadingVirtual<String>>();
            try (final var pool = new MultiThreadingVirtual<String>("voffset-ok", progress, 1, _ -> seen.set(holder.get().getOffset()))) {
                holder.set(pool);

                pool.start(Stream.of("entry"));

                assertThat(seen).hasValue(0);
                assertThat(pool.freeOffsets()).containsExactly(0);
                assertThat(pool.getOffset()).isEqualTo(-1);
            }
        }
    }

    @Nested
    @DisplayName("Idle marking")
    class IdleMarking {

        @Test
        @DisplayName("freed slot is marked idle with its own offset in virtual pool when queue is drained")
        void virtualPoolMarksFreedSlotIdle() {
            final var progress = mock(ProgressHandler.class);
            final var idleMsg = new AtomicReference<String>();
            doAnswer(inv -> {
                idleMsg.set(inv.getArgument(1));
                return null;
            }).when(progress).setProgressAt(anyInt(), anyString());
            try (final var pool = new MultiThreadingVirtual<>("vidle-ok", progress, 1, _ -> {
                // no-op task
            })) {
                pool.start(Stream.of("entry"));

                verify(progress).setProgressAt(eq(0), contains("Idle"));
                assertThat(idleMsg.get()).isNotBlank();
            }
        }

        @Test
        @DisplayName("freed slot keeps its label while tasks are still queued in virtual pool")
        void virtualPoolKeepsLabelWhileQueued() throws Exception {
            final var progress = mock(ProgressHandler.class);
            final var entered = new java.util.concurrent.CountDownLatch(1);
            final var releaseAll = new java.util.concurrent.CountDownLatch(1);
            // Pool of 1, three tasks: the first two free their slots while the queue still holds work,
            // so only the last freed slot may be idle-marked.
            try (final var pool = new MultiThreadingVirtual<>("vidle-queued", progress, 1, _ -> {
                entered.countDown();
                if (!releaseAll.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("tasks never released");
            })) {
                final var starter = new Thread(() -> pool.start(Stream.of("a", "b", "c")));
                starter.start();
                // Wait until the first task is picked up (queue still holds "b" and "c"), then let all tasks run.
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                releaseAll.countDown();
                starter.join(10_000);
                assertThat(starter.isAlive()).isFalse();

                // Only the final drain may idle-mark: at most one write, on slot 0.
                verify(progress, atMostOnce()).setProgressAt(eq(0), anyString());
            }
        }

        @Test
        @DisplayName("freed slot is marked idle with its own offset in platform pool")
        void platformPoolMarksFreedSlotIdle() {
            final var progress = mock(ProgressHandler.class);
            try (var pool = new MultiThreading<>("pidle-ok", progress, 1, _ -> {
                // no-op task
            })) {
                pool.start(Stream.of("entry"));

                assertThat(pool.freeOffsets()).containsExactly(0);
            }
            verify(progress).setProgressAt(eq(0), contains("Idle"));
        }
    }
}
