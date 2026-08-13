package jrm.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.security.Session;

@DisplayName("Import MAME execution gate")
class ImportMameExecutionTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler progress;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("import-mame-execution-test", "JRomManager", new String[] { "admin" });
        progress = mock(ProgressHandler.class, withSettings().stubOnly());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("DAT files are imported as data and never executed")
    void datFilesAreNotExecuted() throws Exception {
        final File dat = tempDir.resolve("set.dat").toFile();
        Files.writeString(dat.toPath(), "<?xml version=\"1.0\"?><datafile/>");
        dat.setExecutable(true, false);

        final var imprt = new Import(session, dat, false, progress);

        assertThat(imprt.isMame()).isFalse();
        assertThat(imprt.getFile()).isEqualTo(dat);
        assertThat(imprt.getRomsFile()).isNull();
    }

    @Test
    @DisplayName("script files are not started via ProcessBuilder")
    void scriptsAreNotExecuted() throws Exception {
        final Path marker = tempDir.resolve("pwned.txt");
        final File script = tempDir.resolve("mame.sh").toFile();
        Files.writeString(script.toPath(), "#!/bin/sh\necho pwned > \"" + marker.toAbsolutePath() + "\"\n");
        script.setExecutable(true, false);

        final var imprt = new Import(session, script, false, progress);

        assertThat(imprt.isMame()).isFalse();
        assertThat(imprt.getFile()).isNull();
        assertThat(imprt.importMame(script, false, progress)).isNull();
        assertThat(marker).doesNotExist();
    }

    @Test
    @DisplayName("unrelated native binaries are not started")
    void unrelatedBinariesAreNotExecuted() throws Exception {
        final File cmd = tempDir.resolve("cmd.exe").toFile();
        Files.write(cmd.toPath(), new byte[] { 'M', 'Z', 0x00, 0x00 });
        cmd.setExecutable(true, false);

        final var imprt = new Import(session, cmd, false, progress);

        assertThat(imprt.isMame()).isFalse();
        assertThat(imprt.getFile()).isNull();
        assertThat(imprt.importMame(cmd, false, progress)).isNull();
    }
}
