package jrm.profile.fix.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.data.Container;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Entry;
import jrm.security.Session;

@DisplayName("AddEntry profile name path traversal")
class AddEntryPathTraversalTest {

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler progress;

    @BeforeEach
    void setUp() {
        final ResourceBundle msgs = mock(ResourceBundle.class, withSettings().stubOnly());
        when(msgs.getString(org.mockito.ArgumentMatchers.anyString())).thenReturn("%s");
        session = mock(Session.class, withSettings().stubOnly());
        when(session.getMsgs()).thenReturn(msgs);
        progress = mock(ProgressHandler.class, withSettings().stubOnly());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../evil-outside.txt",
        "..\\evil-outside.txt",
        "../../evil-outside.txt",
        "subdir/../../../evil-outside.txt",
        // Disk.getName() always appends .chd; profile <disk name> can still traverse
        "../otherdir/victim.chd",
        "..\\otherdir\\victim.chd",
        "../../otherdir/victim.chd",
        "subdir/../../../otherdir/victim.chd",
        "/tmp/abs-victim.chd",
        "C:/Windows/Temp/abs-victim.chd"
    })
    @DisplayName("doAction(Path) must not write outside target from profile entity name")
    void pathActionMustRejectTraversalNames(String maliciousName) throws Exception {
        final Path target = Files.createDirectories(tempDir.resolve("roms"));
        final Path outside = tempDir.resolve("evil-outside.txt");
        final Path outsideChd = tempDir.resolve("otherdir").resolve("victim.chd");
        assertThat(outside).doesNotExist();
        assertThat(outsideChd).doesNotExist();

        final Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        final Path sourceFile = sourceDir.resolve("good.bin");
        Files.writeString(sourceFile, "payload", StandardCharsets.UTF_8);

        final EntityBase entity = mock(EntityBase.class);
        when(entity.getName()).thenReturn(maliciousName);

        final var container = mock(Container.class);
        when(container.getType()).thenReturn(Container.Type.DIR);
        when(container.getFile()).thenReturn(sourceDir.toFile());

        final Entry entry = new Entry("good.bin", "good.bin");
        setEntryParent(entry, container);

        final var action = new AddEntry(entity, entry);
        final boolean ok = action.doAction(session, target, progress, 1, 1);

        assertThat(ok).isFalse();
        assertThat(outside).doesNotExist();
        assertThat(tempDir.resolve("evil-outside.txt")).doesNotExist();
        assertThat(outsideChd).doesNotExist();
        assertThat(tempDir.resolve("victim.chd")).doesNotExist();
    }

    @Test
    @DisplayName("doAction(Path) accepts safe relative entity names")
    void pathActionAcceptsSafeNames() throws Exception {
        final Path target = Files.createDirectories(tempDir.resolve("roms"));
        final Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        final Path sourceFile = sourceDir.resolve("good.bin");
        Files.writeString(sourceFile, "payload", StandardCharsets.UTF_8);

        final EntityBase entity = mock(EntityBase.class);
        when(entity.getName()).thenReturn("subdir/good.bin");

        final var container = mock(Container.class);
        when(container.getType()).thenReturn(Container.Type.DIR);
        when(container.getFile()).thenReturn(sourceDir.toFile());

        final Entry entry = new Entry("good.bin", "good.bin");
        setEntryParent(entry, container);

        final var action = new AddEntry(entity, entry);
        final boolean ok = action.doAction(session, target, progress, 1, 1);

        assertThat(ok).isTrue();
        assertThat(target.resolve("subdir").resolve("good.bin")).exists().hasContent("payload");
    }

    @Test
    @DisplayName("doAction(Path) accepts safe disk .chd names under target")
    void pathActionAcceptsSafeDiskChdNames() throws Exception {
        final Path target = Files.createDirectories(tempDir.resolve("chds").resolve("machine"));
        final Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(sourceDir.resolve("source.chd"), "chd-payload", StandardCharsets.UTF_8);

        final EntityBase entity = mock(EntityBase.class);
        when(entity.getName()).thenReturn("drive.chd");

        final var container = mock(Container.class);
        when(container.getType()).thenReturn(Container.Type.DIR);
        when(container.getFile()).thenReturn(sourceDir.toFile());

        final Entry entry = new Entry("source.chd", "source.chd");
        setEntryParent(entry, container);

        final var action = new AddEntry(entity, entry);
        final boolean ok = action.doAction(session, target, progress, 1, 1);

        assertThat(ok).isTrue();
        assertThat(target.resolve("drive.chd")).exists().hasContent("chd-payload");
    }

    private static void setEntryParent(Entry entry, Container container) throws Exception {
        final var field = Entry.class.getDeclaredField("parent");
        field.setAccessible(true);
        field.set(entry, container);
    }
}
