package jrm.profile.fix.actions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.Archive;
import jrm.profile.data.Entry;
import jrm.security.Session;

@DisplayName("BackupEntry unsupported destinations")
class BackupEntryUnsupportedTargetTest {

    @Test
    @DisplayName("doAction(Archive) is not a supported backup target")
    void archiveTargetIsUnsupported() {
        final var action = new BackupEntry(new Entry("rom.bin", "rom.bin"));
        final Session session = mock(Session.class, withSettings().stubOnly());
        final Archive archive = mock(Archive.class, withSettings().stubOnly());
        final ProgressHandler handler = mock(ProgressHandler.class, withSettings().stubOnly());
        assertThatThrownBy(() -> action.doAction(session, archive, handler, 1, 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("doAction(Path) is not a supported backup target")
    void pathTargetIsUnsupported() {
        final var action = new BackupEntry(new Entry("rom.bin", "rom.bin"));
        final Session session = mock(Session.class, withSettings().stubOnly());
        final ProgressHandler handler = mock(ProgressHandler.class, withSettings().stubOnly());
        final Path target = Path.of("backup");
        assertThatThrownBy(() -> action.doAction(session, target, handler, 1, 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
