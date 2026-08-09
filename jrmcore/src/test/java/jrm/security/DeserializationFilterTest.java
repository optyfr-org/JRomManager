package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.batch.DirUpdaterResults;
import jrm.batch.TrntChkReport;
import jrm.profile.data.AnywareBase;
import jrm.profile.data.Archive;
import jrm.profile.data.Container;
import jrm.profile.data.Directory;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.report.ContainerUnknown;
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
    private static <T> T deserializeWithFilter(byte[] bytes) throws IOException, ClassNotFoundException {
        return deserializeWithFilter(bytes, DeserializationFilter.Mode.DEFAULT);
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserializeWithFilter(byte[] bytes, DeserializationFilter.Mode mode)
            throws IOException, ClassNotFoundException {
        try (final var ois = DeserializationFilter.openObjectInputStream(new ByteArrayInputStream(bytes), mode)) {
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

        try (final var ois = DeserializationFilter.openObjectInputStream(new ByteArrayInputStream(bytes), 200)) {
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
        @SuppressWarnings("unused")
        private final T value;
        @SuppressWarnings("unused")
        private final DeepNode<T> child;

        DeepNode(T value, DeepNode<T> child) {
            this.value = value;
            this.child = child;
        }
    }


    /**
     * Serializes then deserializes the supplied object using {@link DeserializationFilter#createFilter()}.
     *
     * @param <T> the object type
     * @param object the object to round-trip
     * @return the deserialized object
     * @throws Exception if serialization or deserialization fails
     */
    private static <T> T roundTrip(final T object) throws Exception {
        return roundTrip(object, DeserializationFilter.Mode.DEFAULT);
    }

    private static <T> T roundTrip(final T object, final DeserializationFilter.Mode mode) throws Exception {
        final var baos = new ByteArrayOutputStream();
        try (final var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        final var bytes = baos.toByteArray();
        try (final var ois = DeserializationFilter.openObjectInputStream(new ByteArrayInputStream(bytes), mode)) {
            @SuppressWarnings("unchecked")
            final T result = (T) ois.readObject();
            return result;
        }
    }

    /**
     * Sets a private field via reflection for test construction.
     *
     * @param target the object to modify
     * @param fieldName the field name
     * @param value the value to set
     * @throws Exception if reflection fails
     */
    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Invokes the package-private {@code add(String)} method on a {@link TrntChkReport} or one of its child nodes.
     *
     * @param parent the report or child node that owns the package-private method
     * @param title the child title
     * @return the created child
     * @throws Exception if reflection fails
     */
    private static Object addTrntChkReportChild(final Object parent, final String title) throws Exception {
        final Method method = parent.getClass().getDeclaredMethod("add", String.class);
        method.setAccessible(true);
        return method.invoke(parent, title);
    }

    /**
     * Invokes the package-private {@code TrntChkReport.Child.setStatus(Status)} method.
     *
     * @param child the child node
     * @param status the status to set
     * @throws Exception if reflection fails
     */
    private static void setTrntChkReportChildStatus(final Object child, final TrntChkReport.Status status) throws Exception {
        final Method method = child.getClass().getDeclaredMethod("setStatus", TrntChkReport.Status.class);
        method.setAccessible(true);
        method.invoke(child, status);
    }

    @Test
    @DisplayName("ProfileNFO should round-trip under the filter")
    void profileNfoShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var datFile = tempDir.resolve("test.dat").toFile();
        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        final ProfileNFO nfo = constructor.newInstance(datFile);
        nfo.getStats().setVersion("v1");
        nfo.getStats().setHaveSets(42L);
        nfo.getMame().setFileroms(tempDir.resolve("roms.dat").toFile());
        nfo.getMame().setFilesl(tempDir.resolve("sl.dat").toFile());

        final ProfileNFO loaded = roundTrip(nfo);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getFile()).isEqualTo(datFile);
        assertThat(loaded.getName()).isEqualTo("test.dat");
        assertThat(loaded.getStats().getVersion()).isEqualTo("v1");
        assertThat(loaded.getStats().getHaveSets()).isEqualTo(42L);
        assertThat(loaded.getMame().getFileroms()).isEqualTo(tempDir.resolve("roms.dat").toFile());
        assertThat(loaded.getMame().getFilesl()).isEqualTo(tempDir.resolve("sl.dat").toFile());
    }

    @Test
    @DisplayName("Report should round-trip under the report filter")
    void reportShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var report = new Report();
        setField(report, "reportFile", tempDir.resolve("report.rpt").toFile());

        final var archive = new Archive(tempDir.resolve("roms.zip").toFile(), new File("roms.zip"), (AnywareBase) null);
        archive.setLastTZipStatus(EnumSet.of(TrrntZipStatus.VALIDTRRNTZIP));
        report.getSubjects().add(new ContainerUnknown(archive));

        final Report loaded = roundTrip(report, DeserializationFilter.Mode.REPORT);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getReportFile()).isEqualTo(tempDir.resolve("report.rpt").toFile());
        assertThat(loaded).hasSize(1);
        final var subject = loaded.get(0);
        assertThat(subject).isInstanceOf(ContainerUnknown.class);
        final Container container = ((ContainerUnknown) subject).getContainer();
        assertThat(container.getFile()).isEqualTo(tempDir.resolve("roms.zip").toFile());
        assertThat(container.getLastTZipStatus()).contains(TrrntZipStatus.VALIDTRRNTZIP);
    }

    @Test
    @DisplayName("DirUpdaterResults should round-trip under the report filter")
    void dirUpdaterResultsShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var results = new DirUpdaterResults();
        final var datFile = tempDir.resolve("source.dat").toFile();
        results.setDat(datFile);
        final var stats = new Report.Stats();
        stats.incSetFound();
        stats.incSetFoundOk();
        results.add(tempDir.resolve("a.dat").toFile(), stats);
        results.add(tempDir.resolve("b.dat").toFile(), new Report.Stats());

        final DirUpdaterResults loaded = roundTrip(results, DeserializationFilter.Mode.REPORT);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getDat()).isEqualTo(datFile);
        assertThat(loaded.getResults()).hasSize(2);
        assertThat(loaded.getResults().get(0).getDat()).isEqualTo(tempDir.resolve("a.dat").toFile());
        assertThat(loaded.getResults().get(0).getStats().getSetFound()).isEqualTo(1);
        assertThat(loaded.getResults().get(0).getStats().getSetFoundOk()).isEqualTo(1);
        assertThat(loaded.getResults().get(1).getDat()).isEqualTo(tempDir.resolve("b.dat").toFile());
    }

    @Test
    @DisplayName("report mode should reject non-report jrm packages")
    void reportModeShouldRejectNonReportJrmPackages() throws Exception {
        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        final ProfileNFO nfo = constructor.newInstance(new File("test.dat"));
        final var bytes = serialize(nfo);

        assertThatThrownBy(() -> deserializeWithFilter(bytes, DeserializationFilter.Mode.REPORT))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    @DisplayName("DirScan cache Map should round-trip under the filter")
    void dirScanCacheMapShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final Map<String, Container> cache = new HashMap<>();
        final var directory = new Directory(tempDir.resolve("dir").toFile(), new File("dir"), (AnywareBase) null);
        final var archive = new Archive(tempDir.resolve("archive.zip").toFile(), new File("archive.zip"), (AnywareBase) null);
        archive.setLastTZipStatus(EnumSet.of(TrrntZipStatus.VALIDTRRNTZIP));
        cache.put("dir", directory);
        cache.put("zip", archive);

        final Map<String, Container> loaded = roundTrip(cache);

        assertThat(loaded).isNotNull().hasSize(2);
        assertThat(loaded.get("dir").getFile()).isEqualTo(tempDir.resolve("dir").toFile());
        assertThat(loaded.get("zip").getFile()).isEqualTo(tempDir.resolve("archive.zip").toFile());
        assertThat(loaded.get("zip").getLastTZipStatus()).contains(TrrntZipStatus.VALIDTRRNTZIP);
    }

    @Test
    @DisplayName("TrntChkReport should round-trip under the report filter")
    void trntChkReportShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var report = new TrntChkReport(tempDir.resolve("test.torrent").toFile());
        final TrntChkReport.Child root = (TrntChkReport.Child) addTrntChkReportChild(report, "root");
        setTrntChkReportChildStatus(root, TrntChkReport.Status.OK);
        final TrntChkReport.Child parent = (TrntChkReport.Child) addTrntChkReportChild(report, "parent");
        parent.getData().setLength(123L);
        final TrntChkReport.Child child = (TrntChkReport.Child) addTrntChkReportChild(parent, "child");
        setTrntChkReportChildStatus(child, TrntChkReport.Status.MISSING);

        final TrntChkReport loaded = roundTrip(report, DeserializationFilter.Mode.REPORT);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getNodes()).hasSize(2);
        assertThat(loaded.getNodes().get(0).getData().getTitle()).isEqualTo("root");
        assertThat(loaded.getNodes().get(0).getData().getStatus()).isEqualTo(TrntChkReport.Status.OK);
        assertThat(loaded.getNodes().get(1).getData().getTitle()).isEqualTo("parent");
        assertThat(loaded.getNodes().get(1).getData().getLength()).isEqualTo(123L);
        assertThat(loaded.getNodes().get(1).getChildren()).hasSize(1);
        assertThat(loaded.getNodes().get(1).getChildren().get(0).getData().getTitle()).isEqualTo("child");
        assertThat(loaded.getNodes().get(1).getChildren().get(0).getData().getStatus()).isEqualTo(TrntChkReport.Status.MISSING);
    }
}
