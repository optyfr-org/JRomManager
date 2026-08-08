package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jrm.profile.report.Report;
import jtrrntzip.TrrntZipStatus;

/**
 * Regression tests for {@link DeserializationFilter}.
 * <p>
 * These tests verify that the allowlist used by the filter covers the full serialized object graphs produced by the
 * application's persisted cache and report formats. Future allowlist changes must not break loading of existing data.
 * </p>
 */
@DisplayName("DeserializationFilter tests")
class DeserializationFilterTest {

    /**
     * Serializable holder that exercises the main classes found in persisted object graphs:
     * {@link File}, {@link Instant}, {@link Map.Entry} arrays via {@link HashMap}, and {@link TrrntZipStatus}.
     */
    private static final class PersistedGraph implements Serializable {
        private static final long serialVersionUID = 1L;

        private final File file;
        private final Instant instant;
        private final Map<String, Long> map;
        private final TrrntZipStatus status;
        private final Report.Stats stats;

        PersistedGraph(File file, Instant instant, Map<String, Long> map, TrrntZipStatus status, Report.Stats stats) {
            this.file = file;
            this.instant = instant;
            this.map = map;
            this.status = status;
            this.stats = stats;
        }
    }

    /**
     * Serializes the given object to a byte array.
     *
     * @param object the object to serialize
     * @return the serialized bytes
     * @throws IOException if serialization fails
     */
    private static byte[] serialize(Object object) throws IOException {
        try (final var baos = new ByteArrayOutputStream();
                final var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            return baos.toByteArray();
        }
    }

    /**
     * Deserializes the given bytes using {@link DeserializationFilter#createFilter()}.
     *
     * @param bytes the serialized bytes
     * @return the deserialized object
     * @throws IOException if deserialization fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    @SuppressWarnings("unchecked")
    private static <T> T deserializeWithFilter(byte[] bytes) throws IOException, ClassNotFoundException {
        try (final var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(DeserializationFilter.createFilter());
            return (T) ois.readObject();
        }
    }

    /**
     * Verifies that the filter allows the JDK and third-party types present in the application's persisted object graphs.
     *
     * @throws Exception if the round-trip fails
     */
    @Test
    @DisplayName("should allow persisted object graph with File, Instant, HashMap, and TrrntZipStatus")
    void shouldAllowPersistedObjectGraph() throws Exception {
        final var map = new HashMap<String, Long>();
        map.put("one", 1L);
        map.put("two", 2L);

        final var original = new PersistedGraph(
                new File("test.dat"),
                Instant.parse("2024-01-01T00:00:00Z"),
                map,
                TrrntZipStatus.VALIDTRRNTZIP,
                new Report.Stats());

        final var bytes = serialize(original);
        final PersistedGraph restored = deserializeWithFilter(bytes);

        assertThat(restored).isNotNull();
        assertThat(restored.file).isEqualTo(original.file);
        assertThat(restored.instant).isEqualTo(original.instant);
        assertThat(restored.map).containsExactlyInAnyOrderEntriesOf(original.map);
        assertThat(restored.status).isEqualTo(original.status);
        assertThat(restored.stats).usingRecursiveComparison().isEqualTo(original.stats);
    }

    /**
     * Verifies that the filter still rejects classes that are not part of the application's persisted object graphs.
     *
     * @throws Exception if the test setup fails
     */
    @Test
    @DisplayName("should reject classes outside the allowlist")
    void shouldRejectClassesOutsideAllowlist() throws Exception {
        final var original = java.net.URI.create("http://example.com/"); // java.net.URI is serializable but not in the allowlist //$NON-NLS-1$
        final var bytes = serialize(original);

        assertThatThrownBy(() -> deserializeWithFilter(bytes))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("REJECTED"); //$NON-NLS-1$
    }

    /**
     * Verifies that the custom depth limit overload accepts deeper graphs than the default limit.
     *
     * @throws Exception if the round-trip fails
     */
    @Test
    @DisplayName("should allow custom depth limit to exceed the default")
    void shouldAllowCustomDepthLimit() throws Exception {
        // Build a chain deeper than the default limit of 100.
        DeepNode<Integer> original = null;
        for (int i = 150; i >= 1; i--) {
            original = new DeepNode<>(i, original);
        }
        final var bytes = serialize(original);

        try (final var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(DeserializationFilter.createFilter(200));
            @SuppressWarnings("unchecked")
            final DeepNode<Integer> restored = (DeepNode<Integer>) ois.readObject();
            assertThat(restored).usingRecursiveComparison().isEqualTo(original);
        }
    }

    /**
     * Serializable recursive node used to exercise the depth limit.
     */
    private static final class DeepNode<T> implements Serializable {
        private static final long serialVersionUID = 1L;
        private final T value;
        private final DeepNode<T> child;

        DeepNode(T value, DeepNode<T> child) {
            this.value = value;
            this.child = child;
        }
    }
}
