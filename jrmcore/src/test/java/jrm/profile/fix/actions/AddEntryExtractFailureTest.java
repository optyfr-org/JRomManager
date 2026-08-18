package jrm.profile.fix.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.SevenZipArchive;
import jrm.profile.data.Container;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Entry;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

@DisplayName("AddEntry extract failure must not report success")
class AddEntryExtractFailureTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler progress;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = spy(new Session("add-entry-extract-test", "JRomManager", new String[] { "admin" }));
        final ResourceBundle msgs = mock(ResourceBundle.class, withSettings().stubOnly());
        when(msgs.getString(org.mockito.ArgumentMatchers.anyString())).thenReturn("%s");
        when(session.getMsgs()).thenReturn(msgs);
        progress = mock(ProgressHandler.class, withSettings().stubOnly());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("doAction(Path) fails when 7z extract returns null")
    void pathActionFailsWhenExtractYieldsNoFile() throws Exception {
        final Path archive = tempDir.resolve("src.7z");
        createReal7z(archive, "present.bin", "payload");

        final Path target = Files.createDirectories(tempDir.resolve("roms"));
        final Path dest = target.resolve("missing.bin");

        final boolean ok = newAddEntry("missing.bin", archive).doAction(session, target, progress, 1, 1);

        assertThat(ok).isFalse();
        assertThat(dest).doesNotExist();
    }

    @Test
    @DisplayName("doAction(ZipFile) fails when 7z extract returns null")
    void zipActionFailsWhenExtractYieldsNoFile() throws Exception {
        final Path archive = tempDir.resolve("src.7z");
        createReal7z(archive, "present.bin", "payload");

        final Path destZip = tempDir.resolve("dest.zip");
        try (ZipFile zipf = new ZipFile(destZip.toFile())) {
            final boolean ok = newAddEntry("missing.bin", archive).doAction(session, zipf, new ZipParameters(), progress, 1, 1);
            assertThat(ok).isFalse();
        }
        assertThat(destZip).doesNotExist();
    }

    @Test
    @DisplayName("doAction(Path) copies extracted 7z entry to disk")
    void pathActionCopiesExtractedSevenZipEntry() throws Exception {
        final Path archive = tempDir.resolve("src.7z");
        createReal7z(archive, "good.bin", "payload");

        final Path target = Files.createDirectories(tempDir.resolve("roms"));
        final boolean ok = newAddEntry("good.bin", archive).doAction(session, target, progress, 1, 1);

        assertThat(ok).isTrue();
        assertThat(target.resolve("good.bin")).exists().hasContent("payload");
    }

    @Test
    @DisplayName("doAction(ZipFile) adds extracted 7z entry to zip")
    void zipActionAddsExtractedSevenZipEntry() throws Exception {
        final Path archive = tempDir.resolve("src.7z");
        createReal7z(archive, "good.bin", "payload");

        final Path destZip = tempDir.resolve("dest.zip");
        try (ZipFile zipf = new ZipFile(destZip.toFile())) {
            final boolean ok = newAddEntry("good.bin", archive).doAction(session, zipf, new ZipParameters(), progress, 1, 1);
            assertThat(ok).isTrue();
        }
        try (ZipFile zipf = new ZipFile(destZip.toFile())) {
            assertThat(zipf.getFileHeader("good.bin")).isNotNull();
        }
    }

    private AddEntry newAddEntry(String name, Path archive) throws Exception {
        final EntityBase entity = mock(EntityBase.class);
        when(entity.getName()).thenReturn(name);

        final var container = mock(Container.class);
        when(container.getType()).thenReturn(Container.Type.SEVENZIP);
        when(container.getFile()).thenReturn(archive.toFile());

        final Entry entry = new Entry(name, name);
        final var field = Entry.class.getDeclaredField("parent");
        field.setAccessible(true);
        field.set(entry, container);
        return new AddEntry(entity, entry);
    }

    private void createReal7z(Path sevenZipPath, String entryName, String entryContent) throws IOException {
        final var contentFile = tempDir.resolve(entryName);
        Files.writeString(contentFile, entryContent, StandardCharsets.UTF_8);
        try (var archive = new SevenZipArchive(session, sevenZipPath.toFile());
                InputStream in = Files.newInputStream(contentFile)) {
            archive.addStdIn(in, entryName);
        }
    }
}
