package jrm.profile.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.profile.Profile;
import jrm.profile.data.Machine;
import jrm.profile.data.Rom;
import jrm.security.Session;

/**
 * Reproduces the dat2dir batch report viewer flow: a populated session report is saved
 * via {@link Report#save(Session)} (keyed on the profile file) and reloaded via
 * {@link Report#load(Session, File)} with the DAT file, as done by
 * {@code BatchDirUpd8rResultsController.showReport}.
 */
@DisplayName("Report batch save/load pairing")
class ReportBatchLoadTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("report-batch-load-test", "JRomManager", new String[] { "admin" });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    private static Machine machine(String name, File datFile) {
        final Profile profile = mock(Profile.class);
        when(profile.getSettings()).thenReturn(null);
        final Machine machine = new Machine(profile);
        machine.setName(name);
        machine.description.append(name);
        return machine;
    }

    private static Profile realProfile() throws Exception {
        final var ctor = Profile.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    @Test
    @DisplayName("save then load with dat file should restore populated report")
    void saveThenLoadShouldRestorePopulatedReport() {
        final var datFile = tempDir.resolve("test.dat").toFile();
        final Machine m = machine("someset", datFile);
        final var report = session.getReport();
        final var ss = new SubjectSet(m);
        ss.setMissing();
        ss.add(new EntryMissing(new Rom(m)));
        report.add(ss);

        // batch flow: session report profile bound to dat, then saved
        report.save(session, ReportIntf.getReportFile(session, datFile));

        final Report loaded = Report.load(session, datFile);

        assertThat(loaded).isNotNull();
        assertThat(loaded).hasSize(1);
    }

    @Test
    @DisplayName("save via session report default path then load should restore populated report")
    void saveViaDefaultPathThenLoadShouldRestorePopulatedReport() throws Exception {
        final var datFile = tempDir.resolve("test2.dat").toFile();
        Files.writeString(datFile.toPath(), "<datafile></datafile>");
        // batch flow in DirUpdater: session report gets profile bound to dat, then save()
        final var nfoCtor = jrm.profile.manager.ProfileNFO.class.getDeclaredConstructor(File.class);
        nfoCtor.setAccessible(true);
        final var nfo = nfoCtor.newInstance(datFile);
        final var profile = mock(Profile.class);
        when(profile.getNfo()).thenReturn(nfo);
        session.getReport().setProfile(profile);
        final Machine m = machine("someset", datFile);
        final var ss = new SubjectSet(m);
        ss.setMissing();
        ss.add(new EntryMissing(new Rom(m)));
        session.getReport().add(ss);

        session.getReport().save(session);

        final Report loaded = Report.load(session, datFile);

        assertThat(loaded).isNotNull();
        assertThat(loaded).hasSize(1);
    }

    @Test
    @DisplayName("save/load with ware bound to real profile graph should restore report")
    void saveLoadWithRealProfileShouldRestoreReport() throws Exception {
        final var datFile = tempDir.resolve("real.dat").toFile();
        final Profile profile = realProfile();
        final var m = new Machine(profile);
        m.setName("realsystem");
        m.description.append("realsystem");
        final var rom = new Rom(m);
        rom.setName("realrom");
        m.getRoms().add(rom);
        final var report = new Report();
        final var ss = new SubjectSet(m);
        ss.setMissing();
        ss.add(new EntryMissing(new Rom(m)));
        report.add(ss);

        final var target = jrm.profile.report.ReportIntf.getReportFile(session, datFile);
        report.save(session, target);

        assertThat(target).exists();
        final Report loaded = Report.load(session, datFile);

        assertThat(loaded).isNotNull();
        assertThat(loaded).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Timeout(300)
    @DisplayName("e2e: real scan report save then viewer load should restore populated report")
    void e2eScanReportSaveThenViewerLoad() throws Exception {
        final var datFile = new File("src/test/resources/dats/MAME 0.288 ROMs (merged).xml");
        org.assertj.core.api.Assertions.assertThat(datFile).exists();
        final var handler = mock(jrm.aui.progress.ProgressHandler.class,
                org.mockito.Mockito.withSettings().stubOnly());
        when(handler.isCancel()).thenReturn(false);
        when(handler.getInputStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        final var profile = Profile.load(session, datFile, handler);
        org.assertj.core.api.Assertions.assertThat(profile).isNotNull();
        // minimal scan-ready settings: roms dst + src dirs under temp
        final var romsDst = Files.createDirectories(tempDir.resolve("roms")).toString();
        profile.getSettings().setProperty(jrm.misc.ProfileSettingsEnum.roms_dest_dir, romsDst);
        profile.getSettings().setProperty(jrm.misc.ProfileSettingsEnum.src_dir, tempDir.toString());
        session.getReport().setProfile(profile);
        new jrm.profile.scan.Scan(profile, handler);

        // batch flow: snapshot stats + save session report
        final var stats = new Report.Stats(session.getReport().getStats());
        session.getReport().save(session);

        // viewer flow
        final Report loaded = Report.load(session, datFile);

        assertThat(stats.getSetMissing()).isPositive();
        assertThat(loaded).as("viewer Report.load must return the saved scan report").isNotNull();
        assertThat(loaded).as("viewer report must contain subjects").isNotEmpty();
    }
}
