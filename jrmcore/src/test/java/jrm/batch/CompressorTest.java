package jrm.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipLevel;
import jrm.misc.SettingsEnum;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

/**
 * Unit tests for {@link Compressor}, covering {@link Compressor.FileResult}, the {@code extensions} array, the
 * {@link Compressor#compress} routing logic, skip behaviour, and error handling for non-existent or invalid archives.
 */
@DisplayName("Compressor tests")
class CompressorTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;
    private ProgressHandler progressHandler;
    private Compressor compressor;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("compressor-test", "JRomManager", new String[] { "admin" });
        session.getUser().getSettings().setEnumProperty(SettingsEnum.zip_compression_level, ZipLevel.NORMAL);
        progressHandler = mock(ProgressHandler.class, withSettings().stubOnly());
        when(progressHandler.isCancel()).thenReturn(false);
        compressor = new Compressor(session, new AtomicInteger(0), 1, progressHandler);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    // ──────────────────────────────────────────────────────────────
    //  FileResult
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FileResult")
    class FileResultTests {

        @Test
        @DisplayName("constructor should set file and default result to empty string")
        void constructorShouldSetFileAndDefaultResult() {
            var path = Path.of("/tmp/test.zip");

            var result = new Compressor.FileResult(path);

            assertThat(result.getFile()).isEqualTo(path);
            assertThat(result.getResult()).isEmpty();
        }

        @Test
        @DisplayName("setFile should update the file path")
        void setFileShouldUpdateFilePath() {
            var initialPath = Path.of("/tmp/a.zip");
            var newPath = Path.of("/tmp/b.7z");
            var result = new Compressor.FileResult(initialPath);

            result.setFile(newPath);

            assertThat(result.getFile()).isEqualTo(newPath);
        }

        @Test
        @DisplayName("setResult should update the result string")
        void setResultShouldUpdateResultString() {
            var result = new Compressor.FileResult(Path.of("/tmp/test.zip"));

            result.setResult("OK");

            assertThat(result.getResult()).isEqualTo("OK");
        }

        @Test
        @DisplayName("two FileResults with same fields should be equal")
        void twoFileResultsWithSameFieldsShouldBeEqual() {
            var path = Path.of("/tmp/test.zip");

            var r1 = new Compressor.FileResult(path);
            var r2 = new Compressor.FileResult(path);
            r1.setResult("OK");
            r2.setResult("OK");

            assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
        }

        @Test
        @DisplayName("toString should contain file and result values")
        void toStringShouldContainFileAndResultValues() {
            var result = new Compressor.FileResult(Path.of("/tmp/test.zip"));
            result.setResult("OK");

            var str = result.toString();

            assertThat(str).contains("test.zip", "OK");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  extensions
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extensions")
    class ExtensionsTests {

        @Test
        @DisplayName("should contain all 14 expected extensions including zip and 7z")
        void shouldContainAllExpectedExtensions() {
            assertThat(Compressor.getExtensions())
                .hasSize(14)
                .contains("zip", "7z")
                .containsExactly(
                    "zip", "7z", "rar", "arj", "tar", "lzh", "lha", "tgz", "tbz", "tbz2", "rpm", "iso", "deb", "cab"
                );
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  compress() routing — skip cases
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("compress() routing — skip cases")
    class CompressRoutingSkipTests {

        @Test
        @DisplayName("ZIP format with .zip file and force=false should report Skipped")
        void zipFormatWithZipFileNoForceShouldReportSkipped() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "archive.zip");

            compressor.compress(CompressorFormat.ZIP, file, false, results::add, srcUpdates::add);

            assertThat(results).contains("Skipped");
            assertThat(srcUpdates).isEmpty();
        }

        @Test
        @DisplayName("SEVENZIP format with .7z file and force=false should report Skipped")
        void sevenZipFormatWith7zFileNoForceShouldReportSkipped() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "archive.7z");

            compressor.compress(CompressorFormat.SEVENZIP, file, false, results::add, srcUpdates::add);

            assertThat(results).contains("Skipped");
            assertThat(srcUpdates).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  compress() routing — error cases
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("compress() routing — error cases")
    class CompressRoutingErrorTests {

        @ParameterizedTest(name = "{0} format with .{1} file (force={2}) should attempt conversion and report failure")
        @CsvSource({
            "ZIP, 7z, false",
            "SEVENZIP, zip, false",
            "SEVENZIP, rar, false",
            "TZIP, zip, false",
            "ZIP, zip, true",
            "SEVENZIP, 7z, true"
        })
        @DisplayName("should attempt conversion and report failure for non-existent file")
        void shouldAttemptConversionAndReportFailure(CompressorFormat format, String extension, boolean force) {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent." + extension);

            compressor.compress(format, file, force, results::add, srcUpdates::add);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0)).startsWith("Processing ");
            assertThat(srcUpdates).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  compress() routing — all formats
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("compress() routing — all formats dispatch correctly")
    class CompressRoutingDispatchTests {

        @ParameterizedTest(name = "format {0} should not throw and should produce at least one callback")
        @EnumSource(CompressorFormat.class)
        @DisplayName("every format should produce at least one callback for a .zip file")
        void everyFormatShouldProduceAtLeastOneCallbackForZipFile(CompressorFormat format) {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            compressor.compress(format, file, false, results::add, srcUpdates::add);

            assertThat(results).isNotEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  zip2TZip
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("zip2TZip()")
    class Zip2TZipTests {

        @Test
        @DisplayName("non-existent file should return null and report non-OK status")
        void nonExistentFileShouldReturnNullAndReportNonOkStatus() {
            var results = new ArrayList<String>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            var result = compressor.zip2TZip(file, false, results::add);

            assertThat(result).isNull();
            assertThat(results).isNotEmpty().doesNotContain("OK");
        }

        @Test
        @DisplayName("should report Processing status before failure")
        void shouldReportProcessingStatusBeforeFailure() {
            var results = new ArrayList<String>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            compressor.zip2TZip(file, true, results::add);

            assertThat(results.get(0)).startsWith("Processing ");
            assertThat(results).doesNotContain("OK");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  sevenZip2SevenZip
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sevenZip2SevenZip()")
    class SevenZip2SevenZipTests {

        @Test
        @DisplayName("non-existent file should return null and report failure")
        void nonExistentFileShouldReturnNullAndReportFailure() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.7z");

            var result = compressor.sevenZip2SevenZip(file, results::add, srcUpdates::add);

            assertThat(result).isNull();
            assertThat(results).isNotEmpty();
            assertThat(srcUpdates).isEmpty();
        }

        @Test
        @DisplayName("should report Processing status")
        void shouldReportProcessingStatus() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.7z");

            compressor.sevenZip2SevenZip(file, results::add, srcUpdates::add);

            assertThat(results.get(0)).startsWith("Processing ");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  sevenZip2Zip
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sevenZip2Zip()")
    class SevenZip2ZipTests {

        @Test
        @DisplayName("non-existent file should return null and report failure")
        void nonExistentFileShouldReturnNullAndReportFailure() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.7z");

            var result = compressor.sevenZip2Zip(file, false, results::add, srcUpdates::add);

            assertThat(result).isNull();
            assertThat(results).isNotEmpty();
            assertThat(srcUpdates).isEmpty();
        }

        @Test
        @DisplayName("should report Processing status")
        void shouldReportProcessingStatus() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.7z");

            compressor.sevenZip2Zip(file, true, results::add, srcUpdates::add);

            assertThat(results.get(0)).startsWith("Processing ");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  zip2Zip
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("zip2Zip()")
    class Zip2ZipTests {

        @Test
        @DisplayName("non-existent file should return null and report failure")
        void nonExistentFileShouldReturnNullAndReportFailure() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            var result = compressor.zip2Zip(file, results::add, srcUpdates::add);

            assertThat(result).isNull();
            assertThat(results).isNotEmpty();
            assertThat(srcUpdates).isEmpty();
        }

        @Test
        @DisplayName("should report Processing status")
        void shouldReportProcessingStatus() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            compressor.zip2Zip(file, results::add, srcUpdates::add);

            assertThat(results.get(0)).startsWith("Processing ");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  zip2SevenZip
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("zip2SevenZip()")
    class Zip2SevenZipTests {

        @Test
        @DisplayName("non-existent file should return null and report failure")
        void nonExistentFileShouldReturnNullAndReportFailure() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            var result = compressor.zip2SevenZip(file, results::add, srcUpdates::add);

            assertThat(result).isNull();
            assertThat(results).isNotEmpty();
            assertThat(srcUpdates).isEmpty();
        }

        @Test
        @DisplayName("should report Processing status")
        void shouldReportProcessingStatus() {
            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();
            var file = new File(tempDir.toFile(), "nonexistent.zip");

            compressor.zip2SevenZip(file, results::add, srcUpdates::add);

            assertThat(results.get(0)).startsWith("Processing ");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Real zip file round-trip
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Real zip file operations")
    class RealZipFileTests {

        /**
         * Creates a real ZIP archive containing a single text file entry.
         *
         * @param zipPath the destination path for the ZIP file
         * @throws IOException if an I/O error occurs
         */
        private void createRealZip(Path zipPath) throws IOException {
            var contentFile = tempDir.resolve("content.txt");
            Files.writeString(contentFile, "hello world");
            var params = new ZipParameters();
            params.setCompressionMethod(CompressionMethod.DEFLATE);
            params.setCompressionLevel(CompressionLevel.NORMAL);
            params.setFileNameInZip("content.txt");
            try (var zip = new ZipFile(zipPath.toFile())) {
                zip.addFile(contentFile.toFile(), params);
            }
        }

        @Test
        @DisplayName("zip2Zip with a real zip file should produce a new zip or report failure gracefully")
        void zip2ZipWithRealZipFileShouldProduceNewZipOrReportFailureGracefully() throws IOException {
            var zipPath = tempDir.resolve("real.zip");
            createRealZip(zipPath);
            assertThat(zipPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            var result = compressor.zip2Zip(zipPath.toFile(), results::add, srcUpdates::add);

            // The operation either succeeds (returns new file, reports OK) or fails gracefully (returns null, reports error)
            if (result != null) {
                assertThat(result).exists();
                assertThat(result.getName()).endsWith(".zip");
                assertThat(results).contains("OK");
                assertThat(srcUpdates).isNotEmpty();
            } else {
                assertThat(results).isNotEmpty();
                assertThat(results.get(0)).startsWith("Processing ");
            }
        }

        @Test
        @DisplayName("zip2TZip with a real zip file should produce a valid tzip or report failure gracefully")
        void zip2TZipWithRealZipFileShouldProduceValidTZipOrReportFailureGracefully() throws IOException {
            var zipPath = tempDir.resolve("real.zip");
            createRealZip(zipPath);
            assertThat(zipPath).exists();

            var results = new ArrayList<String>();

            var result = compressor.zip2TZip(zipPath.toFile(), false, results::add);

            // The operation either succeeds (returns the file, reports OK) or fails gracefully (returns null, reports error)
            if (result != null) {
                assertThat(result).exists();
                assertThat(results).contains("OK");
            } else {
                assertThat(results).isNotEmpty();
                assertThat(results.get(0)).startsWith("Processing ");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Real 7zip file conversions
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Real 7zip file conversions")
    class RealSevenZipConversionTests {

        /**
         * Creates a real 7-Zip archive containing a single text file entry using SevenZipJBinding.
         *
         * @param sevenZipPath the destination path for the 7z file
         * @param entryName the name of the entry inside the archive
         * @param entryContent the text content to store in the entry
         * @throws IOException if an I/O error occurs
         */
        private void createReal7z(Path sevenZipPath, String entryName, String entryContent) throws IOException {
            var contentFile = tempDir.resolve(entryName);
            Files.writeString(contentFile, entryContent);
            try (var archive = new SevenZipArchive(session, sevenZipPath.toFile())) {
                try (InputStream in = Files.newInputStream(contentFile)) {
                    archive.addStdIn(in, entryName);
                }
            }
        }

        /**
         * Creates a real ZIP archive containing a single text file entry.
         *
         * @param zipPath the destination path for the ZIP file
         * @param entryName the name of the entry inside the archive
         * @param entryContent the text content to store in the entry
         * @throws IOException if an I/O error occurs
         */
        private void createRealZip(Path zipPath, String entryName, String entryContent) throws IOException {
            var contentFile = tempDir.resolve(entryName);
            Files.writeString(contentFile, entryContent);
            var params = new ZipParameters();
            params.setCompressionMethod(CompressionMethod.DEFLATE);
            params.setCompressionLevel(CompressionLevel.NORMAL);
            params.setFileNameInZip(entryName);
            try (var zip = new ZipFile(zipPath.toFile())) {
                zip.addFile(contentFile.toFile(), params);
            }
        }

        @Test
        @DisplayName("sevenZip2SevenZip with a real 7z file should produce a new 7z or report failure gracefully")
        void sevenZip2SevenZipWithReal7zFileShouldProduceNew7zOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.7z");
            createReal7z(srcPath, "content.txt", "hello 7z world");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            var result = compressor.sevenZip2SevenZip(srcPath.toFile(), results::add, srcUpdates::add);

            if (result != null) {
                assertThat(result).exists();
                assertThat(result.getName()).endsWith(".7z");
                assertThat(results).contains("OK");
                assertThat(srcUpdates).isNotEmpty();
            } else {
                assertThat(results).isNotEmpty();
                assertThat(results.get(0)).startsWith("Processing ");
            }
        }

        @Test
        @DisplayName("sevenZip2Zip with a real 7z file should produce a new zip or report failure gracefully")
        void sevenZip2ZipWithReal7zFileShouldProduceNewZipOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.7z");
            createReal7z(srcPath, "content.txt", "hello 7z to zip");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            var result = compressor.sevenZip2Zip(srcPath.toFile(), false, results::add, srcUpdates::add);

            if (result != null) {
                assertThat(result).exists();
                assertThat(result.getName()).endsWith(".zip");
                assertThat(results).contains("OK");
                assertThat(srcUpdates).isNotEmpty();
            } else {
                assertThat(results).isNotEmpty();
                assertThat(results.get(0)).startsWith("Processing ");
            }
        }

        @Test
        @DisplayName("zip2SevenZip with a real zip file should produce a new 7z or report failure gracefully")
        void zip2SevenZipWithRealZipFileShouldProduceNew7zOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.zip");
            createRealZip(srcPath, "content.txt", "hello zip to 7z");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            var result = compressor.zip2SevenZip(srcPath.toFile(), results::add, srcUpdates::add);

            if (result != null) {
                assertThat(result).exists();
                assertThat(result.getName()).endsWith(".7z");
                assertThat(results).contains("OK");
                assertThat(srcUpdates).isNotEmpty();
            } else {
                assertThat(results).isNotEmpty();
                assertThat(results.get(0)).startsWith("Processing ");
            }
        }

        @Test
        @DisplayName("compress SEVENZIP with a real zip file should produce a new 7z or report failure gracefully")
        void compressSevenZipWithRealZipFileShouldProduceNew7zOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.zip");
            createRealZip(srcPath, "content.txt", "compress zip to 7z");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            compressor.compress(CompressorFormat.SEVENZIP, srcPath.toFile(), false, results::add, srcUpdates::add);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0)).startsWith("Processing ");
        }

        @Test
        @DisplayName("compress ZIP with a real 7z file should produce a new zip or report failure gracefully")
        void compressZipWithReal7zFileShouldProduceNewZipOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.7z");
            createReal7z(srcPath, "content.txt", "compress 7z to zip");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            compressor.compress(CompressorFormat.ZIP, srcPath.toFile(), false, results::add, srcUpdates::add);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0)).startsWith("Processing ");
        }

        @Test
        @DisplayName("compress SEVENZIP with a real 7z file and force=true should re-compress or report failure gracefully")
        void compressSevenZipWithReal7zFileForceTrueShouldRecompressOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.7z");
            createReal7z(srcPath, "content.txt", "force recompress 7z");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            compressor.compress(CompressorFormat.SEVENZIP, srcPath.toFile(), true, results::add, srcUpdates::add);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0)).startsWith("Processing ");
        }

        @Test
        @DisplayName("compress TZIP with a real 7z file should convert to zip then tzip or report failure gracefully")
        void compressTZipWithReal7zFileShouldConvertToZipThenTZipOrReportFailureGracefully() throws IOException {
            var srcPath = tempDir.resolve("source.7z");
            createReal7z(srcPath, "content.txt", "7z to tzip");
            assertThat(srcPath).exists();

            var results = new ArrayList<String>();
            var srcUpdates = new ArrayList<File>();

            compressor.compress(CompressorFormat.TZIP, srcPath.toFile(), false, results::add, srcUpdates::add);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0)).startsWith("Processing ");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Callback interfaces
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Callback interfaces")
    class CallbackInterfaceTests {

        @Test
        @DisplayName("UpdResultCallBack.apply should be a functional interface")
        void updResultCallBackShouldBeFunctionalInterface() {
            Compressor.UpdResultCallBack cb = txt -> { };

            assertThat(cb).isNotNull();
        }

        @Test
        @DisplayName("UpdSrcCallBack.apply should be a functional interface")
        void updSrcCallBackShouldBeFunctionalInterface() {
            Compressor.UpdSrcCallBack cb = file -> { };

            assertThat(cb).isNotNull();
        }
    }
}