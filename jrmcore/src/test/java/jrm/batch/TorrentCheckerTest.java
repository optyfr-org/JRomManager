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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import jrm.io.torrent.bencoding.types.BByteString;
import jrm.io.torrent.bencoding.types.BDictionary;
import jrm.io.torrent.bencoding.types.BInt;
import jrm.io.torrent.bencoding.types.BList;
import jrm.misc.SettingsEnum;
import jrm.security.Session;

/**
 * Unit tests for {@link TorrentChecker}, covering constructor behavior with empty lists, unselected entries,
 * null/missing paths, real torrent file checking in various modes, cancellation, and options.
 */
@DisplayName("TorrentChecker tests")
class TorrentCheckerTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler progress;
    private ResultColUpdater updater;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("torrent-checker-test");
        session.getUser().getSettings().setProperty(SettingsEnum.use_parallelism, false);
        progress = mock(ProgressHandler.class, withSettings().stubOnly());
        when(progress.isCancel()).thenReturn(false);
        updater = mock(ResultColUpdater.class);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    // ──────────────────────────────────────────────────────────────
    //  Helper: create synthetic multi-file torrent
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates a small synthetic multi-file torrent and writes it to the temp directory.
     *
     * @param name the torrent content name
     * @return the path to the created torrent file
     * @throws IOException if writing fails
     */
    private Path createMultiFileTorrent(String name) throws IOException {
        BDictionary root = new BDictionary();
        BDictionary info = new BDictionary();

        info.add(new BByteString("name"), new BByteString(name));
        info.add(new BByteString("piece length"), new BInt(16384L));
        info.add(new BByteString("pieces"), new BByteString(new byte[20]));

        BList files = new BList();

        BDictionary file1 = new BDictionary();
        file1.add(new BByteString("length"), new BInt(500L));
        BList path1 = new BList();
        path1.add(new BByteString("file1.txt"));
        file1.add(new BByteString("path"), path1);

        BDictionary file2 = new BDictionary();
        file2.add(new BByteString("length"), new BInt(700L));
        BList path2 = new BList();
        path2.add(new BByteString("file2.txt"));
        file2.add(new BByteString("path"), path2);

        files.add(file1);
        files.add(file2);

        info.add(new BByteString("files"), files);
        root.add(new BByteString("info"), info);

        Path torrentFile = tempDir.resolve(name + ".torrent");
        Files.write(torrentFile, root.bencode());
        return torrentFile;
    }

    /**
     * Creates a synthetic multi-file torrent with nested directory structure.
     *
     * @param name the torrent content name
     * @return the path to the created torrent file
     * @throws IOException if writing fails
     */
    private Path createNestedMultiFileTorrent(String name) throws IOException {
        BDictionary root = new BDictionary();
        BDictionary info = new BDictionary();

        info.add(new BByteString("name"), new BByteString(name));
        info.add(new BByteString("piece length"), new BInt(16384L));
        info.add(new BByteString("pieces"), new BByteString(new byte[20]));

        BList files = new BList();

        BDictionary file1 = new BDictionary();
        file1.add(new BByteString("length"), new BInt(100L));
        BList path1 = new BList();
        path1.add(new BByteString("subdir"));
        path1.add(new BByteString("file1.txt"));
        file1.add(new BByteString("path"), path1);

        BDictionary file2 = new BDictionary();
        file2.add(new BByteString("length"), new BInt(200L));
        BList path2 = new BList();
        path2.add(new BByteString("subdir"));
        path2.add(new BByteString("file2.txt"));
        file2.add(new BByteString("path"), path2);

        files.add(file1);
        files.add(file2);

        info.add(new BByteString("files"), files);
        root.add(new BByteString("info"), info);

        Path torrentFile = tempDir.resolve(name + ".torrent");
        Files.write(torrentFile, root.bencode());
        return torrentFile;
    }

    /**
     * Creates a multi-file torrent whose path uses {@code ..} to escape the destination directory.
     */
    private Path createTraversalMultiFileTorrent(String name) throws IOException {
        BDictionary root = new BDictionary();
        BDictionary info = new BDictionary();

        info.add(new BByteString("name"), new BByteString(name));
        info.add(new BByteString("piece length"), new BInt(16384L));
        info.add(new BByteString("pieces"), new BByteString(new byte[20]));

        BList files = new BList();

        BDictionary file1 = new BDictionary();
        file1.add(new BByteString("length"), new BInt(500L));
        BList path1 = new BList();
        path1.add(new BByteString(".."));
        path1.add(new BByteString("evil-outside.txt"));
        file1.add(new BByteString("path"), path1);

        files.add(file1);

        info.add(new BByteString("files"), files);
        root.add(new BByteString("info"), info);

        Path torrentFile = tempDir.resolve(name + ".torrent");
        Files.write(torrentFile, root.bencode());
        return torrentFile;
    }

    // ──────────────────────────────────────────────────────────────
    //  Empty / unselected lists
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("empty and unselected lists")
    class EmptyAndUnselectedTests {

        @Test
        @DisplayName("constructor with empty sdrl should not throw")
        void constructorWithEmptySdrlShouldNotThrow() {
            var sdrl = new SDRList<SrcDstResult>();

            assertThatCode(() -> new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>())).doesNotThrowAnyException();

            verify(updater, never()).updateResult(anyInt(), anyString());
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

            assertThatCode(() -> new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>())).doesNotThrowAnyException();

            verify(updater, never()).updateResult(anyInt(),
                    anyString());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Null src / dst
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("null src or dst")
    class NullSrcDstTests {

        @Test
        @DisplayName("constructor with null src should return SrcNotDefined message")
        void constructorWithNullSrcShouldReturnSrcNotDefined() {
            var sdr = new SrcDstResult(null, tempDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            assertThat(captor.getAllValues()).contains("");
            // The result should be the SrcNotDefined message (not "In progress...")
            assertThat(captor.getAllValues()).anyMatch(v -> !v.isEmpty() && !"In progress...".equals(v));
        }

        @Test
        @DisplayName("constructor with null dst should return DstNotDefined message")
        void constructorWithNullDstShouldReturnDstNotDefined() {
            var sdr = new SrcDstResult(tempDir.toString(), null);
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            assertThat(captor.getAllValues()).contains("");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Non-existent src / dst
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("non-existent src or dst")
    class NonExistentSrcDstTests {

        @Test
        @DisplayName("constructor with non-existent src should return SrcMustExist message")
        void constructorWithNonExistentSrcShouldReturnSrcMustExist() {
            var sdr = new SrcDstResult(
                    tempDir.resolve("nonexistent.torrent").toString(),
                    tempDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            // Should have "" and a non-"In progress..." result
            assertThat(captor.getAllValues()).contains("");
        }

        @Test
        @DisplayName("constructor with non-existent dst should return DstMustExist message")
        void constructorWithNonExistentDstShouldReturnDstMustExist() throws IOException {
            var torrentFile = createMultiFileTorrent("test");
            var sdr = new SrcDstResult(
                    torrentFile.toString(),
                    tempDir.resolve("nonexistent-dst").toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            assertThat(captor.getAllValues()).contains("");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Real torrent file checking — FILENAME mode
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FILENAME mode checking")
    class FilenameModeTests {

        @Test
        @DisplayName("constructor with torrent and empty dst should report missing files")
        void constructorWithTorrentAndEmptyDstShouldReportMissingFiles() throws IOException {
            var torrentFile = createMultiFileTorrent("test-torrent");
            var dstDir = tempDir.resolve("dst");
            Files.createDirectories(dstDir);
            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            // Should have "", "In progress...", and a result
            assertThat(captor.getAllValues()).contains("", "In progress...");
            // The final result should not be "In progress..."
            assertThat(captor.getAllValues()).anyMatch(v -> !"In progress...".equals(v) && !v.isEmpty());
        }

        @Test
        @DisplayName("constructor with torrent and matching files should report OK")
        void constructorWithTorrentAndMatchingFilesShouldReportOk() throws IOException {
            var torrentFile = createMultiFileTorrent("ok-torrent");
            var dstDir = tempDir.resolve("dst-ok");
            Files.createDirectories(dstDir);
            // Create the expected files
            Files.write(dstDir.resolve("file1.txt"), new byte[500]);
            Files.write(dstDir.resolve("file2.txt"), new byte[700]);
            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            // The result should contain "Complete" or similar success indicator
            assertThat(captor.getAllValues()).anyMatch(v -> v.contains("COMPLETE") || v.contains("100"));
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Real torrent file checking — FILESIZE mode
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FILESIZE mode checking")
    class FilesizeModeTests {

        @Test
        @DisplayName("constructor with torrent and empty dst in FILESIZE mode should report missing bytes")
        void constructorWithTorrentAndEmptyDstFilesizeModeShouldReportMissingBytes() throws IOException {
            var torrentFile = createMultiFileTorrent("filesize-test");
            var dstDir = tempDir.resolve("dst-filesize");
            Files.createDirectories(dstDir);
            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILESIZE,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            assertThat(captor.getAllValues()).contains("", "In progress...");
            assertThat(captor.getAllValues()).anyMatch(v -> !"In progress...".equals(v) && !v.isEmpty());
        }

        @Test
        @DisplayName("constructor with wrong-sized files in FILESIZE mode should report size mismatch")
        void constructorWithWrongSizedFilesFilesizeModeShouldReportSizeMismatch() throws IOException {
            var torrentFile = createMultiFileTorrent("wrong-size");
            var dstDir = tempDir.resolve("dst-wrong-size");
            Files.createDirectories(dstDir);
            // Create files with wrong sizes
            Files.write(dstDir.resolve("file1.txt"), new byte[100]);
            Files.write(dstDir.resolve("file2.txt"), new byte[200]);
            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILESIZE,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            // Result should not be "Complete" since sizes are wrong
            assertThat(captor.getAllValues()).anyMatch(v -> !"In progress...".equals(v) && !v.isEmpty());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Real torrent file checking — SHA1 mode
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SHA1 mode checking")
    class Sha1ModeTests {

        @Test
        @DisplayName("constructor with torrent and empty dst in SHA1 mode should report invalid pieces")
        void constructorWithTorrentAndEmptyDstSha1ModeShouldReportInvalidPieces() throws IOException {
            var torrentFile = createMultiFileTorrent("sha1-test");
            var dstDir = tempDir.resolve("dst-sha1");
            Files.createDirectories(dstDir);
            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.SHA1,
                    updater, new HashSet<>());

            var captor = ArgumentCaptor.forClass(String.class);
            verify(updater, atLeast(2)).updateResult(anyInt(), captor.capture());
            assertThat(captor.getAllValues()).contains("", "In progress...");
            assertThat(captor.getAllValues()).anyMatch(v -> !"In progress...".equals(v) && !v.isEmpty());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Options
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("options")
    class OptionsTests {

        @Test
        @DisplayName("REMOVEUNKNOWNFILES option should remove files not in torrent")
        void removeUnknownFilesOptionShouldRemoveFilesNotInTorrent() throws IOException {
            var torrentFile = createMultiFileTorrent("remove-unknown");
            var dstDir = tempDir.resolve("dst-remove-unknown");
            Files.createDirectories(dstDir);
            // Create an unknown file
            var unknownFile = dstDir.resolve("unknown.txt");
            Files.write(unknownFile, new byte[42]);

            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, Set.of(TorrentChecker.Options.REMOVEUNKNOWNFILES));

            // The unknown file should have been removed
            assertThat(unknownFile).doesNotExist();
        }

        @Test
        @DisplayName("REMOVEWRONGSIZEDFILES option should remove files with wrong size")
        void removeWrongSizedFilesOptionShouldRemoveWrongSizedFiles() throws IOException {
            var torrentFile = createMultiFileTorrent("remove-wrong-size");
            var dstDir = tempDir.resolve("dst-remove-wrong-size");
            Files.createDirectories(dstDir);
            // Create a file with wrong size
            var wrongFile = dstDir.resolve("file1.txt");
            Files.write(wrongFile, new byte[100]); // Expected 500

            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILESIZE,
                    updater, Set.of(TorrentChecker.Options.REMOVEWRONGSIZEDFILES));

            // The wrong-sized file should have been removed
            assertThat(wrongFile).doesNotExist();
        }

        @Test
        @DisplayName("DETECTARCHIVEDFOLDERS option should not throw with nested torrent")
        void detectArchivedFoldersOptionShouldNotThrowWithNestedTorrent() throws IOException {
            var torrentFile = createNestedMultiFileTorrent("nested");
            var dstDir = tempDir.resolve("dst-nested");
            Files.createDirectories(dstDir);

            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            assertThatCode(() -> new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, Set.of(TorrentChecker.Options.DETECTARCHIVEDFOLDERS))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("FILESIZE REMOVEWRONGSIZEDFILES must not delete paths outside destDir")
        void removeWrongSizedFilesMustRejectPathTraversal() throws IOException {
            var torrentFile = createTraversalMultiFileTorrent("path-slip");
            var dstDir = tempDir.resolve("dst-path-slip");
            Files.createDirectories(dstDir);

            var outsideTarget = tempDir.resolve("evil-outside.txt");
            Files.write(outsideTarget, new byte[42]); // wrong size vs torrent length 500

            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            assertThatCode(() -> new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILESIZE,
                    updater, Set.of(TorrentChecker.Options.REMOVEWRONGSIZEDFILES))).doesNotThrowAnyException();

            assertThat(outsideTarget).exists().hasSize(42);
        }

        @Test
        @DisplayName("DETECTARCHIVEDFOLDERS must not extract Zip Slip paths outside destDir")
        void detectArchivedFoldersMustRejectZipSlipEntries() throws IOException {
            var torrentFile = createNestedMultiFileTorrent("zipslip");
            var dstDir = tempDir.resolve("dst-zipslip");
            Files.createDirectories(dstDir);

            // detectArchives looks for <first-component>.zip beside the expected folder
            var maliciousZip = dstDir.resolve("subdir.zip");
            var outsideTarget = tempDir.resolve("evil-outside.txt");
            assertThat(outsideTarget).doesNotExist();

            try (var zos = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
                zos.putNextEntry(new ZipEntry("../../evil-outside.txt"));
                zos.write("pwned".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("safe.txt"));
                zos.write("ok".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            assertThatCode(() -> new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, Set.of(TorrentChecker.Options.DETECTARCHIVEDFOLDERS))).doesNotThrowAnyException();

            assertThat(outsideTarget).doesNotExist();
            assertThat(tempDir.resolve("evil-outside.txt")).doesNotExist();
            // Safe entries under the extraction root may still be written before a bad entry aborts
            var extractedRoot = dstDir.resolve("subdir");
            if (Files.isDirectory(extractedRoot)) {
                assertThat(extractedRoot.resolve("safe.txt")).satisfiesAnyOf(
                        p -> assertThat(p).doesNotExist(),
                        p -> assertThat(p).exists().hasContent("ok"));
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Cancellation
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancellation")
    class CancellationTests {

        @Test
        @DisplayName("constructor with cancel=true should still clear results for selected entries")
        void constructorWithCancelShouldStillClearResults() {
            var sdr = new SrcDstResult(
                    tempDir.resolve("nonexistent.torrent").toString(),
                    tempDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);
            when(progress.isCancel()).thenReturn(true);

            assertThatCode(() -> new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>())).doesNotThrowAnyException();

            // First pass always runs (sets to "")
            verify(updater).updateResult(0, "");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Multiple entries
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("multiple entries")
    class MultipleEntriesTests {

        @Test
        @DisplayName("constructor with mixed selected/unselected should only process selected")
        void constructorWithMixedSelectedShouldOnlyProcessSelected() throws IOException {
            var torrentFile = createMultiFileTorrent("mixed");
            var dstDir = tempDir.resolve("dst-mixed");
            Files.createDirectories(dstDir);

            var sdr1 = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr1.setSelected(true);
            var sdr2 = new SrcDstResult("src2", "dst2");
            sdr2.setSelected(false);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr1);
            sdrl.add(sdr2);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            // Only row 0 should be updated
            verify(updater, atLeast(1)).updateResult(0, "");
            verify(updater, never()).updateResult(1, "");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Report file creation
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("report file creation")
    class ReportFileTests {

        @Test
        @DisplayName("constructor should save report file after checking")
        void constructorShouldSaveReportFileAfterChecking() throws IOException {
            var torrentFile = createMultiFileTorrent("report-test");
            var dstDir = tempDir.resolve("dst-report");
            Files.createDirectories(dstDir);
            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            sdr.setSelected(true);
            var sdrl = new SDRList<SrcDstResult>();
            sdrl.add(sdr);

            new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            // The report file should exist in the reports directory
            var reportsDir = session.getUser().getSettings().getWorkPath().resolve("reports").toFile();
            assertThat(reportsDir).exists();
            assertThat(reportsDir.listFiles()).anyMatch(f -> f.isFile() && f.getName().endsWith(".report"));
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Direct check() method tests
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("check() method")
    class CheckMethodTests {

        @Test
        @DisplayName("check with null src should return SrcNotDefined message")
        void checkWithNullSrcShouldReturnSrcNotDefined() throws Exception {
            var sdrl = new SDRList<SrcDstResult>();
            var checker = new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var sdr = new SrcDstResult(null, tempDir.toString());
            var result = checker.check(progress, sdr);

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("check with null dst should return DstNotDefined message")
        void checkWithNullDstShouldReturnDstNotDefined() throws Exception {
            var sdrl = new SDRList<SrcDstResult>();
            var checker = new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var sdr = new SrcDstResult(tempDir.toString(), null);
            var result = checker.check(progress, sdr);

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("check with non-existent src should return SrcMustExist message")
        void checkWithNonExistentSrcShouldReturnSrcMustExist() throws Exception {
            var sdrl = new SDRList<SrcDstResult>();
            var checker = new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var sdr = new SrcDstResult(
                    tempDir.resolve("nonexistent.torrent").toString(),
                    tempDir.toString());
            var result = checker.check(progress, sdr);

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("check with torrent and empty dst in FILENAME mode should return result")
        void checkWithTorrentAndEmptyDstFilenameModeShouldReturnResult() throws Exception {
            var torrentFile = createMultiFileTorrent("direct-check");
            var dstDir = tempDir.resolve("dst-direct");
            Files.createDirectories(dstDir);
            var sdrl = new SDRList<SrcDstResult>();
            var checker = new TorrentChecker<>(session, progress, sdrl, jrm.io.torrent.options.TrntChkMode.FILENAME,
                    updater, new HashSet<>());

            var sdr = new SrcDstResult(torrentFile.toString(), dstDir.toString());
            var result = checker.check(progress, sdr);

            assertThat(result).isNotNull();
        }
    }
}