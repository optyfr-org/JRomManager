package jrm.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.aui.progress.ProgressHandler;
import jrm.security.Session;

/**
 * Unit tests for {@link DirUpdater}, covering constructor behavior with empty lists, unselected entries,
 * cancellation, non-existent DAT files, and dry-run mode.
 */
@DisplayName("DirUpdater tests")
class DirUpdaterTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler progress;
    private ResultColUpdater resultUpdater;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("dirupdater-test", "JRomManager", new String[] { "admin" });
        progress = mock(ProgressHandler.class, withSettings().stubOnly());
        when(progress.isCancel()).thenReturn(false);
        resultUpdater = mock(ResultColUpdater.class);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    // ──────────────────────────────────────────────────────────────
    //  Empty / unselected lists
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("empty and unselected lists")
    class EmptyAndUnselectedTests {

        @Test
        @DisplayName("constructor with empty sdrl should not throw and not call updateResult")
        void constructorWithEmptySdrlShouldNotThrow() {
            var sdrl = new SDRList<SrcDstResult>();
            var srcdirs = new ArrayList<File>();

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            verify(resultUpdater, never()).updateResult(anyInt(),
                    anyString());
        }

        @Test
        @DisplayName("constructor with all unselected entries should not call updateResult")
        void constructorWithAllUnselectedShouldNotCallUpdateResult() {
            var sdr1 = new SrcDstResult("src1", "dst1");
            sdr1.setSelected(false);
            var sdr2 = new SrcDstResult("src2", "dst2");
            sdr2.setSelected(false);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr1);
            sdrl.add(sdr2);
            var srcdirs = new ArrayList<File>();

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            verify(resultUpdater, never()).updateResult(anyInt(),
                    anyString());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Cancellation
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancellation")
    class CancellationTests {

        @Test
        @DisplayName("constructor with cancel=true should only run first pass of updateResult")
        void constructorWithCancelShouldOnlyRunFirstPass() {
            var sdr = new SrcDstResult("src1", "dst1");
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);
            var srcdirs = new ArrayList<File>();
            when(progress.isCancel()).thenReturn(true);

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            // First pass always runs (sets to ""), second pass is skipped due to cancel
            verify(resultUpdater).updateResult(0, "");
            // Should not reach "In progress..." since cancel is true before second pass
            verify(resultUpdater, never()).updateResult(0, "In progress...");
        }

        @Test
        @DisplayName("constructor with cancel=true should still clear results for all selected entries")
        void constructorWithCancelShouldStillClearResultsForAllSelected() {
            var sdr1 = new SrcDstResult("src1", "dst1");
            sdr1.setSelected(true);
            var sdr2 = new SrcDstResult("src2", "dst2");
            sdr2.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr1);
            sdrl.add(sdr2);
            var srcdirs = new ArrayList<File>();
            when(progress.isCancel()).thenReturn(true);

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            // First pass clears all selected entries
            verify(resultUpdater).updateResult(0, "");
            verify(resultUpdater).updateResult(1, "");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Non-existent DAT file
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("non-existent DAT file")
    class NonExistentDatTests {

        @Test
        @DisplayName("constructor with non-existent DAT file should not throw")
        void constructorWithNonExistentDatShouldNotThrow() {
            var sdr = new SrcDstResult(
                    tempDir.resolve("nonexistent.dat").toString(),
                    tempDir.resolve("dst").toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);
            var srcdirs = new ArrayList<File>();

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            // Should have called updateResult at least for clearing and "In progress..."
            verify(resultUpdater).updateResult(0, "");
            verify(resultUpdater).updateResult(0, "In progress...");
        }

        @Test
        @DisplayName("constructor with non-existent DAT file in dry-run mode should not throw")
        void constructorWithNonExistentDatDryRunShouldNotThrow() {
            var sdr = new SrcDstResult(
                    tempDir.resolve("nonexistent.dat").toString(),
                    tempDir.resolve("dst").toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);
            var srcdirs = new ArrayList<File>();

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, true))
                    .doesNotThrowAnyException();

            verify(resultUpdater).updateResult(0, "");
            verify(resultUpdater).updateResult(0, "In progress...");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Multiple selected entries
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("multiple selected entries")
    class MultipleSelectedTests {

        @Test
        @DisplayName("constructor with multiple selected entries should call updateResult for each")
        void constructorWithMultipleSelectedShouldCallUpdateResultForEach() {
            var sdr1 = new SrcDstResult(
                    tempDir.resolve("dat1.dat").toString(),
                    tempDir.resolve("dst1").toString());
            sdr1.setSelected(true);
            var sdr2 = new SrcDstResult(
                    tempDir.resolve("dat2.dat").toString(),
                    tempDir.resolve("dst2").toString());
            sdr2.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr1);
            sdrl.add(sdr2);
            var srcdirs = new ArrayList<File>();

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            // Both entries should have been cleared and started
            verify(resultUpdater).updateResult(0, "");
            verify(resultUpdater).updateResult(1, "");
            verify(resultUpdater).updateResult(0, "In progress...");
            verify(resultUpdater).updateResult(1, "In progress...");
        }

        @Test
        @DisplayName("constructor with mixed selected/unselected should only process selected")
        void constructorWithMixedSelectedShouldOnlyProcessSelected() {
            var sdr1 = new SrcDstResult(
                    tempDir.resolve("dat1.dat").toString(),
                    tempDir.resolve("dst1").toString());
            sdr1.setSelected(true);
            var sdr2 = new SrcDstResult(
                    tempDir.resolve("dat2.dat").toString(),
                    tempDir.resolve("dst2").toString());
            sdr2.setSelected(false);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr1);
            sdrl.add(sdr2);
            var srcdirs = new ArrayList<File>();

            assertThatCode(() -> new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false))
                    .doesNotThrowAnyException();

            // Only row 0 should be updated
            verify(resultUpdater).updateResult(0, "");
            verify(resultUpdater).updateResult(0, "In progress...");
            verify(resultUpdater, never()).updateResult(1, "");
            verify(resultUpdater, never()).updateResult(1, "In progress...");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Result content verification
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("result content")
    class ResultContentTests {

        @Test
        @DisplayName("constructor should call updateResult with non-empty result after processing")
        void constructorShouldCallUpdateResultWithNonEmptyResult() {
            var sdr = new SrcDstResult(
                    tempDir.resolve("nonexistent.dat").toString(),
                    tempDir.resolve("dst").toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);
            var srcdirs = new ArrayList<File>();

            new DirUpdater(session, sdrl, progress, srcdirs, resultUpdater, false);

            // Capture all updateResult calls
            var captor = ArgumentCaptor.forClass(String.class);
            verify(resultUpdater, atLeast(2)).updateResult(anyInt(), captor.capture());
            // At least "" and "In progress..." were called
            assertThat(captor.getAllValues()).contains("", "In progress...");
        }
    }
}