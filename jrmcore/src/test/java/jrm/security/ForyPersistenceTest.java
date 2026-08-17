package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.batch.DirUpdaterResults;
import jrm.batch.TrntChkReport;
import jrm.profile.Profile;
import jrm.profile.data.AnywareBase;
import jrm.profile.data.Archive;
import jrm.profile.data.Container;
import jrm.profile.data.Directory;
import jrm.profile.data.Disk;
import jrm.profile.data.Machine;
import jrm.profile.data.Rom;
import jrm.profile.data.Sample;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.report.ContainerUnknown;
import jrm.profile.report.Report;
import jtrrntzip.TrrntZipStatus;

/**
 * Regression tests for Fory registration lists and HMAC-signed round-trips.
 */
@DisplayName("ForyPersistence tests")
class ForyPersistenceTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("fory-persistence-test", "JRomManager", new String[] { "admin" });
    }

    @AfterEach
    void tearDown() {
        CacheIntegrityKey.clearCache();
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("should allow persisted object graph with File, Instant, HashMap, and TrrntZipStatus")
    void shouldAllowPersistedObjectGraph() throws Exception {
        final var map = new HashMap<String, Object>();
        map.put("file", new File("test.dat"));
        map.put("instant", Instant.parse("2024-01-01T00:00:00Z"));
        map.put("one", 1L);
        map.put("status", TrrntZipStatus.VALIDTRRNTZIP);

        final File file = tempDir.resolve("graph.cache").toFile();
        SignedObjectStore.write(session, file, map, SignedObjectStore.Codec.CACHE);
        @SuppressWarnings("unchecked")
        final HashMap<String, Object> restored = (HashMap<String, Object>) SignedObjectStore.read(session, file,
                SignedObjectStore.Codec.CACHE);

        assertThat(restored)
                .isNotNull()
                .containsEntry("file", map.get("file"))
                .containsEntry("instant", map.get("instant"))
                .containsEntry("one", 1L)
                .containsEntry("status", TrrntZipStatus.VALIDTRRNTZIP);
    }

    @Test
    @DisplayName("ProfileNFO should round-trip under CACHE")
    void profileNfoShouldRoundTrip(@TempDir final Path nfoDir) throws Exception {
        final var datFile = nfoDir.resolve("test.dat").toFile();
        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        final ProfileNFO nfo = constructor.newInstance(datFile);
        nfo.getStats().setVersion("v1");
        nfo.getStats().setHaveSets(42L);
        nfo.getMame().setFileroms(nfoDir.resolve("roms.dat").toFile());
        nfo.getMame().setFilesl(nfoDir.resolve("sl.dat").toFile());

        final File file = nfoDir.resolve("test.nfo").toFile();
        SignedObjectStore.write(session, file, nfo, SignedObjectStore.Codec.CACHE);
        final ProfileNFO loaded = (ProfileNFO) SignedObjectStore.read(session, file, SignedObjectStore.Codec.CACHE);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getFile()).isEqualTo(datFile);
        assertThat(loaded.getName()).isEqualTo("test.dat");
        assertThat(loaded.getStats().getVersion()).isEqualTo("v1");
        assertThat(loaded.getStats().getHaveSets()).isEqualTo(42L);
        assertThat(loaded.getMame().getFileroms()).isEqualTo(nfoDir.resolve("roms.dat").toFile());
        assertThat(loaded.getMame().getFilesl()).isEqualTo(nfoDir.resolve("sl.dat").toFile());
    }

    @Test
    @DisplayName("Report should round-trip under REPORT")
    void reportShouldRoundTrip(@TempDir final Path reportDir) throws Exception {
        final var report = new Report();
        setField(report, "reportFile", reportDir.resolve("report.rpt").toFile());

        final var archive = new Archive(reportDir.resolve("roms.zip").toFile(), new File("roms.zip"), (AnywareBase) null);
        archive.setLastTZipStatus(EnumSet.of(TrrntZipStatus.VALIDTRRNTZIP));
        report.getSubjects().add(new ContainerUnknown(archive));

        final File file = reportDir.resolve("report.cache").toFile();
        SignedObjectStore.write(session, file, report, SignedObjectStore.Codec.REPORT);
        final Report loaded = (Report) SignedObjectStore.read(session, file, SignedObjectStore.Codec.REPORT);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getReportFile()).isEqualTo(reportDir.resolve("report.rpt").toFile());
        assertThat(loaded).hasSize(1);
        final var subject = loaded.get(0);
        assertThat(subject).isInstanceOf(ContainerUnknown.class);
        final Container container = ((ContainerUnknown) subject).getContainer();
        assertThat(container.getFile()).isEqualTo(reportDir.resolve("roms.zip").toFile());
        assertThat(container.getLastTZipStatus()).contains(TrrntZipStatus.VALIDTRRNTZIP);
    }

    @Test
    @DisplayName("DirUpdaterResults should round-trip under REPORT")
    void dirUpdaterResultsShouldRoundTrip(@TempDir final Path resultsDir) throws Exception {
        final var results = new DirUpdaterResults();
        final var datFile = resultsDir.resolve("source.dat").toFile();
        results.setDat(datFile);
        final var stats = new Report.Stats();
        stats.incSetFound();
        stats.incSetFoundOk();
        results.add(resultsDir.resolve("a.dat").toFile(), stats);
        results.add(resultsDir.resolve("b.dat").toFile(), new Report.Stats());

        final File file = resultsDir.resolve("results.cache").toFile();
        SignedObjectStore.write(session, file, results, SignedObjectStore.Codec.REPORT);
        final DirUpdaterResults loaded = (DirUpdaterResults) SignedObjectStore.read(session, file,
                SignedObjectStore.Codec.REPORT);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getDat()).isEqualTo(datFile);
        assertThat(loaded.getResults()).hasSize(2);
        assertThat(loaded.getResults().get(0).getDat()).isEqualTo(resultsDir.resolve("a.dat").toFile());
        assertThat(loaded.getResults().get(0).getStats().getSetFound()).isEqualTo(1);
        assertThat(loaded.getResults().get(0).getStats().getSetFoundOk()).isEqualTo(1);
        assertThat(loaded.getResults().get(1).getDat()).isEqualTo(resultsDir.resolve("b.dat").toFile());
    }

    @Test
    @DisplayName("DirScan cache Map should round-trip under CACHE")
    void dirScanCacheMapShouldRoundTrip(@TempDir final Path scanDir) throws Exception {
        final Map<String, Container> cache = new HashMap<>();
        final var directory = new Directory(scanDir.resolve("dir").toFile(), new File("dir"), (AnywareBase) null);
        final var archive = new Archive(scanDir.resolve("archive.zip").toFile(), new File("archive.zip"), (AnywareBase) null);
        archive.setLastTZipStatus(EnumSet.of(TrrntZipStatus.VALIDTRRNTZIP));
        cache.put("dir", directory);
        cache.put("zip", archive);

        final File file = scanDir.resolve("dirscan.cache").toFile();
        SignedObjectStore.write(session, file, cache, SignedObjectStore.Codec.CACHE);
        @SuppressWarnings("unchecked")
        final Map<String, Container> loaded = (Map<String, Container>) SignedObjectStore.read(session, file,
                SignedObjectStore.Codec.CACHE);

        assertThat(loaded).isNotNull().hasSize(2);
        assertThat(loaded.get("dir").getFile()).isEqualTo(scanDir.resolve("dir").toFile());
        assertThat(loaded.get("zip").getFile()).isEqualTo(scanDir.resolve("archive.zip").toFile());
        assertThat(loaded.get("zip").getLastTZipStatus()).contains(TrrntZipStatus.VALIDTRRNTZIP);
    }

    @Test
    @DisplayName("TrntChkReport should round-trip under TRNTCHK")
    void trntChkReportShouldRoundTrip(@TempDir final Path torrentDir) throws Exception {
        final var report = new TrntChkReport(torrentDir.resolve("test.torrent").toFile());
        final TrntChkReport.Child root = (TrntChkReport.Child) addTrntChkReportChild(report, "root");
        setTrntChkReportChildStatus(root, TrntChkReport.Status.OK);
        final TrntChkReport.Child parent = (TrntChkReport.Child) addTrntChkReportChild(report, "parent");
        parent.getData().setLength(123L);
        final TrntChkReport.Child child = (TrntChkReport.Child) addTrntChkReportChild(parent, "child");
        setTrntChkReportChildStatus(child, TrntChkReport.Status.MISSING);

        final File file = torrentDir.resolve("trnt.cache").toFile();
        SignedObjectStore.write(session, file, report, SignedObjectStore.Codec.TRNTCHK);
        final TrntChkReport loaded = (TrntChkReport) SignedObjectStore.read(session, file, SignedObjectStore.Codec.TRNTCHK);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getNodes()).hasSize(2);
        assertThat(loaded.getNodes().get(0).getData().getTitle()).isEqualTo("root");
        assertThat(loaded.getNodes().get(0).getData().getStatus()).isEqualTo(TrntChkReport.Status.OK);
        assertThat(loaded.getNodes().get(1).getData().getTitle()).isEqualTo("parent");
        assertThat(loaded.getNodes().get(1).getData().getLength()).isEqualTo(123L);
        assertThat(loaded.getNodes().get(1).getChildren()).hasSize(1);
        assertThat(loaded.getNodes().get(1).getChildren().get(0).getData().getTitle()).isEqualTo("child");
        assertThat(loaded.getNodes().get(1).getChildren().get(0).getData().getStatus()).isEqualTo(TrntChkReport.Status.MISSING);
        assertThat(loaded.getNodes().get(1).getChildren().get(0).getParent()).isSameAs(loaded.getNodes().get(1));
    }

    @Test
    @DisplayName("Profile Machine+Rom/Disk/Sample should restore parent after CACHE load")
    void profileMachineGraphShouldRestoreParents() throws Exception {
        final Constructor<Profile> constructor = Profile.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Profile profile = constructor.newInstance();
        final Machine machine = new Machine(profile);
        machine.setName("pacman");
        final Rom rom = new Rom(machine);
        rom.setName("pacman.6e");
        machine.getRoms().add(rom);
        final Disk disk = new Disk(machine);
        disk.setName("pacman");
        machine.getDisks().add(disk);
        final Sample sample = new Sample(machine, "die");
        machine.getSamples().add(sample);
        profile.getMachineListList().get(0).add(machine);
        profile.getMachineListList().get(0).putByName(machine);

        final File file = tempDir.resolve("profile.cache").toFile();
        SignedObjectStore.write(session, file, profile, SignedObjectStore.Codec.CACHE);
        final Profile loaded = (Profile) SignedObjectStore.read(session, file, SignedObjectStore.Codec.CACHE);

        assertThat(loaded).isNotNull();
        final Machine loadedMachine = loaded.getMachineListList().get(0).getByName("pacman");
        assertThat(loadedMachine).isNotNull();
        assertThat(loadedMachine.getRoms()).hasSize(1);
        assertThat(loadedMachine.getRoms().iterator().next().getParent()).isSameAs(loadedMachine);
        assertThat(loadedMachine.getDisks()).hasSize(1);
        assertThat(loadedMachine.getDisks().iterator().next().getParent()).isSameAs(loadedMachine);
        assertThat(loadedMachine.getSamples()).hasSize(1);
        assertThat(loadedMachine.getSamples().iterator().next().getParent()).isSameAs(loadedMachine);
    }

    @Test
    @DisplayName("CACHE Fory should reject a REPORT-only type")
    void cacheShouldRejectReportOnlyType() throws Exception {
        final File file = tempDir.resolve("report-as-cache.cache").toFile();
        SignedObjectStore.write(session, file, new Report(), SignedObjectStore.Codec.REPORT);

        assertThatThrownBy(() -> SignedObjectStore.read(session, file, SignedObjectStore.Codec.CACHE))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("REPORT Fory should reject a CACHE-only NFO type")
    void reportShouldRejectCacheOnlyType() throws Exception {
        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        final ProfileNFO nfo = constructor.newInstance(new File("test.dat"));
        final File file = tempDir.resolve("nfo-as-report.cache").toFile();
        SignedObjectStore.write(session, file, nfo, SignedObjectStore.Codec.CACHE);

        assertThatThrownBy(() -> SignedObjectStore.read(session, file, SignedObjectStore.Codec.REPORT))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("same Fory instance should still read a valid payload after a failed deserialize")
    void foryInstanceShouldRecoverAfterFailedDeserialize() throws Exception {
        final var fory = ForyPersistence.get(SignedObjectStore.Codec.CACHE);
        assertThatThrownBy(() -> fory.deserialize(new byte[] { 1, 2, 3, 4 }))
                .isInstanceOf(Exception.class);

        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        final ProfileNFO nfo = constructor.newInstance(new File("recover.dat"));
        final byte[] bytes = fory.serialize(nfo);
        final ProfileNFO loaded = (ProfileNFO) fory.deserialize(bytes);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("recover.dat");
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object addTrntChkReportChild(final Object parent, final String title) throws Exception {
        final Method method = parent.getClass().getDeclaredMethod("add", String.class);
        method.setAccessible(true);
        return method.invoke(parent, title);
    }

    private static void setTrntChkReportChildStatus(final Object child, final TrntChkReport.Status status) throws Exception {
        final Method method = child.getClass().getDeclaredMethod("setStatus", TrntChkReport.Status.class);
        method.setAccessible(true);
        method.invoke(child, status);
    }
}
