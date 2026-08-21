package jrm.server.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Worker}.
 */
@DisplayName("Worker")
class WorkerTest {

    @Nested
    @DisplayName("start / isAlive")
    class StartIsAliveTest {
        @Test
        @DisplayName("isAlive is false before start")
        void notAliveBeforeStart() {
            final Worker worker = new Worker(() -> {
            });
            assertThat(worker.isAlive()).isFalse();
        }

        @Test
        @DisplayName("isAlive is true after start while task is running")
        void aliveWhileRunning() throws InterruptedException {
            final CountDownLatch started = new CountDownLatch(1);
            final CountDownLatch finish = new CountDownLatch(1);
            final Worker worker = new Worker(() -> {
                started.countDown();
                try {
                    finish.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });
            worker.start();
            started.await();
            assertThat(worker.isAlive()).isTrue();
            finish.countDown();
            await().atMost(Duration.ofSeconds(2)).until(() -> !worker.isAlive());
            assertThat(worker.isAlive()).isFalse();
        }

        @Test
        @DisplayName("isAlive becomes false after task completes")
        void notAliveAfterComplete() throws InterruptedException {
            final CountDownLatch done = new CountDownLatch(1);
            final Worker worker = new Worker(done::countDown);
            worker.start();
            done.await();
            await().atMost(Duration.ofSeconds(2)).until(() -> !worker.isAlive());
            assertThat(worker.isAlive()).isFalse();
        }
    }

    @Nested
    @DisplayName("progress field")
    class ProgressTest {
        @Test
        @DisplayName("progress is null by default and settable")
        void progressSettable() {
            final Worker worker = new Worker(() -> {
            });
            assertThat(worker.getProgress()).isNull();
            worker.setProgress(null);
            assertThat(worker.getProgress()).isNull();
        }
    }
}