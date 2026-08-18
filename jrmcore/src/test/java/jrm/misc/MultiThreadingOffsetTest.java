package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
            final var pool = new MultiThreading<>("offset-throw", progress, 1, _ -> {
                throw new IllegalStateException("boom");
            });

            pool.start(Stream.of("entry"));

            assertThat(pool.freeOffsets()).containsExactly(0);
            assertThat(pool.getOffset()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should recycle offset after successful call")
        void shouldRecycleOffsetAfterSuccessfulCall() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            final var seen = new AtomicInteger(-2);
            final var holder = new AtomicReference<MultiThreading<String>>();
            final var pool = new MultiThreading<String>("offset-ok", progress, 1, _ -> seen.set(holder.get().getOffset()));
            holder.set(pool);

            pool.start(Stream.of("entry"));

            assertThat(seen).hasValue(0);
            assertThat(pool.freeOffsets()).containsExactly(0);
            assertThat(pool.getOffset()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("MultiThreadingVirtual")
    class VirtualThreads {

        @Test
        @DisplayName("should recycle offset when calledWith throws")
        void shouldRecycleOffsetWhenCalledWithThrows() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            final var pool = new MultiThreadingVirtual<>("voffset-throw", progress, 1, _ -> {
                throw new IllegalStateException("boom");
            });

            pool.start(Stream.of("entry"));

            assertThat(pool.freeOffsets()).containsExactly(0);
            assertThat(pool.getOffset()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should recycle offset after successful call")
        void shouldRecycleOffsetAfterSuccessfulCall() {
            final var progress = mock(ProgressHandler.class, withSettings().stubOnly());
            final var seen = new AtomicInteger(-2);
            final var holder = new AtomicReference<MultiThreadingVirtual<String>>();
            final var pool = new MultiThreadingVirtual<String>("voffset-ok", progress, 1, _ -> seen.set(holder.get().getOffset()));
            holder.set(pool);

            pool.start(Stream.of("entry"));

            assertThat(seen).hasValue(0);
            assertThat(pool.freeOffsets()).containsExactly(0);
            assertThat(pool.getOffset()).isEqualTo(-1);
        }
    }
}
