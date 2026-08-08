package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
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
 * Each test serializes then deserializes a real cache/report object graph under the same filter applied in production, ensuring that
 * future allowlist changes do not break persisted data formats.
 */
@DisplayName("DeserializationFilter regression tests")
class DeserializationFilterTest {

    /**
     * Serializes then deserializes the supplied object using {@link DeserializationFilter#createFilter()}.
     *
     * @param <T> the object type
     * @param object the object to round-trip
     * @return the deserialized object
     * @throws Exception if serialization or deserialization fails
     */
    private static <T> T roundTrip(final T object) throws Exception {
        final var baos = new ByteArrayOutputStream();
        try (final var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        final var bytes = baos.toByteArray();
        try (final var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(DeserializationFilter.createFilter());
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
    @DisplayName("Report should round-trip under the filter")
    void reportShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var report = new Report();
        setField(report, "reportFile", tempDir.resolve("report.rpt").toFile());

        final var archive = new Archive(tempDir.resolve("roms.zip").toFile(), new File("roms.zip"), (AnywareBase) null);
        archive.setLastTZipStatus(EnumSet.of(TrrntZipStatus.VALIDTRRNTZIP));
        report.getSubjects().add(new ContainerUnknown(archive));

        final Report loaded = roundTrip(report);

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
    @DisplayName("DirUpdaterResults should round-trip under the filter")
    void dirUpdaterResultsShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var results = new DirUpdaterResults();
        final var datFile = tempDir.resolve("source.dat").toFile();
        results.setDat(datFile);
        final var stats = new Report.Stats();
        stats.incSetFound();
        stats.incSetFoundOk();
        results.add(tempDir.resolve("a.dat").toFile(), stats);
        results.add(tempDir.resolve("b.dat").toFile(), new Report.Stats());

        final DirUpdaterResults loaded = roundTrip(results);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getDat()).isEqualTo(datFile);
        assertThat(loaded.getResults()).hasSize(2);
        assertThat(loaded.getResults().get(0).getDat()).isEqualTo(tempDir.resolve("a.dat").toFile());
        assertThat(loaded.getResults().get(0).getStats().getSetFound()).isEqualTo(1);
        assertThat(loaded.getResults().get(0).getStats().getSetFoundOk()).isEqualTo(1);
        assertThat(loaded.getResults().get(1).getDat()).isEqualTo(tempDir.resolve("b.dat").toFile());
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

        @SuppressWarnings("unchecked")
        final Map<String, Container> loaded = roundTrip(cache);

        assertThat(loaded).isNotNull().hasSize(2);
        assertThat(loaded.get("dir").getFile()).isEqualTo(tempDir.resolve("dir").toFile());
        assertThat(loaded.get("zip").getFile()).isEqualTo(tempDir.resolve("archive.zip").toFile());
        assertThat(loaded.get("zip").getLastTZipStatus()).contains(TrrntZipStatus.VALIDTRRNTZIP);
    }

    @Test
    @DisplayName("TrntChkReport should round-trip under the filter")
    void trntChkReportShouldRoundTripUnderTheFilter(@TempDir final Path tempDir) throws Exception {
        final var report = new TrntChkReport(tempDir.resolve("test.torrent").toFile());
        final var root = addTrntChkReportChild(report, "root");
        setTrntChkReportChildStatus(root, TrntChkReport.Status.OK);
        final var parent = addTrntChkReportChild(report, "parent");
        final Method setLength = parent.getClass().getDeclaredMethod("getData");
        setLength.setAccessible(true);
        final Object parentData = setLength.invoke(parent);
        parentData.getClass().getDeclaredMethod("setLength", Long.class).invoke(parentData, 123L);
        final var child = addTrntChkReportChild(parent, "child");
        setTrntChkReportChildStatus(child, TrntChkReport.Status.MISSING);

        final TrntChkReport loaded = roundTrip(report);

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
