package jrm.compressors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jrm.compressors.sevenzipjbinding.ExtractorCallback;
import jrm.security.Session;

/**
 * Security tests for {@link NArchive} implementations to verify path traversal protection.
 * Tests ensure that malicious entry names cannot escape the temporary directory.
 */
@DisplayName("NArchive path traversal protection")
class NArchivePathTraversalTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("path-traversal-test", "JRomManager", new String[] { "admin" });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Nested
    @DisplayName("addStdIn path traversal protection")
    class AddStdInTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "../etc/passwd",
            "..\\etc\\passwd",
            "../../etc/passwd",
            "subdir/../../etc/passwd",
            "subdir/../../../etc/passwd"
        })
        @DisplayName("should reject relative path traversal attempts")
        void shouldRejectRelativePathTraversal(String maliciousEntry) throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream maliciousContent = new ByteArrayInputStream("malicious content".getBytes());
                
                assertThatThrownBy(() -> archive.addStdIn(maliciousContent, maliciousEntry))
                    .isInstanceOf(IOException.class)
                    .hasMessageMatching(".*(escapes base directory|escapes temporary directory).*");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "/etc/passwd",
            "/tmp/evil",
            "\\\\etc\\passwd"
        })
        @DisplayName("should reject absolute path attempts")
        void shouldRejectAbsolutePaths(String maliciousEntry) throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream maliciousContent = new ByteArrayInputStream("malicious content".getBytes());
                
                assertThatThrownBy(() -> archive.addStdIn(maliciousContent, maliciousEntry))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("absolute");
            }
        }

        @Test
        @DisplayName("should reject Windows drive letter paths")
        void shouldRejectWindowsDriveLetterPaths() throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream maliciousContent = new ByteArrayInputStream("malicious content".getBytes());
                
                assertThatThrownBy(() -> archive.addStdIn(maliciousContent, "C:\\Windows\\System32\\evil.dll"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("absolute");
            }
        }

        @Test
        @DisplayName("should reject null byte in entry path")
        void shouldRejectNullByte() throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream maliciousContent = new ByteArrayInputStream("malicious content".getBytes());
                
                assertThatThrownBy(() -> archive.addStdIn(maliciousContent, "file\0.txt"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("null byte");
            }
        }

        @Test
        @DisplayName("should reject null entry path")
        void shouldRejectNullEntry() throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream maliciousContent = new ByteArrayInputStream("malicious content".getBytes());
                
                assertThatThrownBy(() -> archive.addStdIn(maliciousContent, null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("null or empty");
            }
        }

        @Test
        @DisplayName("should reject empty entry path")
        void shouldRejectEmptyEntry() throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream maliciousContent = new ByteArrayInputStream("malicious content".getBytes());
                
                assertThatThrownBy(() -> archive.addStdIn(maliciousContent, ""))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("null or empty");
            }
        }

        @Test
        @DisplayName("should accept valid relative paths")
        void shouldAcceptValidRelativePaths() throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream content = new ByteArrayInputStream("valid content".getBytes());
                
                int result = archive.addStdIn(content, "subdir/file.txt");
                
                assertThat(result).isZero();
            }
        }

        @Test
        @DisplayName("should accept simple filenames")
        void shouldAcceptSimpleFilenames() throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream content = new ByteArrayInputStream("valid content".getBytes());
                
                int result = archive.addStdIn(content, "file.txt");
                
                assertThat(result).isZero();
            }
        }
    }

    @Nested
    @DisplayName("add path traversal protection")
    class AddTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "../etc/passwd",
            "../../etc/passwd",
            "subdir/../../../etc/passwd"
        })
        @DisplayName("should reject path traversal attempts in add method")
        void shouldRejectPathTraversal(String maliciousEntry) throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            Path sourceFile = tempDir.resolve("source.txt");
            Files.writeString(sourceFile, "test content");
            
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                assertThatThrownBy(() -> archive.add(sourceFile.toFile(), maliciousEntry))
                    .isInstanceOf(IOException.class)
                    .hasMessageMatching(".*(escapes base directory|escapes temporary directory).*");
            }
        }
    }

    @Nested
    @DisplayName("extract path traversal protection")
    class ExtractTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "../etc/passwd",
            "../../etc/passwd"
        })
        @DisplayName("should reject path traversal attempts in extract method")
        void shouldRejectPathTraversal(String maliciousEntry) throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            // Create a valid archive first
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream content = new ByteArrayInputStream("test".getBytes());
                archive.addStdIn(content, "valid.txt");
            }
            
            // Try to extract with malicious path
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile(), true, null)) {
                assertThatThrownBy(() -> archive.extract(maliciousEntry))
                    .isInstanceOf(IOException.class)
                    .hasMessageMatching(".*(escapes base directory|escapes temporary directory).*");
            }
        }
    }

    @Nested
    @DisplayName("extractStdOut path traversal protection")
    class ExtractStdOutTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "../etc/passwd",
            "/etc/passwd"
        })
        @DisplayName("should reject path traversal attempts in extractStdOut method")
        void shouldRejectPathTraversal(String maliciousEntry) throws Exception {
            Path archivePath = tempDir.resolve("test.7z");
            
            // Create a valid archive first
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile())) {
                InputStream content = new ByteArrayInputStream("test".getBytes());
                archive.addStdIn(content, "valid.txt");
            }
            
            // Try to extract with malicious path
            try (SevenZipArchive archive = new SevenZipArchive(session, archivePath.toFile(), true, null)) {
                assertThatThrownBy(() -> archive.extractStdOut(maliciousEntry))
                    .isInstanceOf(IOException.class)
                    .hasMessageMatching(".*(escapes base directory|escapes temporary directory|absolute).*");
            }
        }
    }

    @Nested
    @DisplayName("resolveContainedFile Zip Slip protection")
    class ResolveContainedFileTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "../evil-outside.txt",
            "../../evil-outside.txt",
            "subdir/../../evil-outside.txt",
            "/etc/passwd",
            "C:/Windows/System32/evil.dll",
            "file\0.txt"
        })
        @DisplayName("should reject traversal, absolute, and NUL entry paths")
        void shouldRejectUnsafeEntryPaths(String maliciousEntry) {
            final var baseDir = tempDir.resolve("extract-root").toFile();
            assertThatThrownBy(() -> ExtractorCallback.resolveContainedFile(baseDir, maliciousEntry))
                .isInstanceOf(IOException.class)
                .hasMessageMatching(".*(escapes base directory|escapes temporary directory|absolute|null byte).*");
        }

        @Test
        @DisplayName("should accept safe relative entry paths")
        void shouldAcceptSafeRelativePaths() throws IOException {
            final var baseDir = Files.createDirectories(tempDir.resolve("extract-root"));
            final var resolved = ExtractorCallback.resolveContainedFile(baseDir.toFile(), "subdir/safe.txt");
            assertThat(resolved.toPath().normalize().toString().replace('\\', '/'))
                .startsWith(baseDir.normalize().toString().replace('\\', '/'));
            assertThat(resolved).hasName("safe.txt");
        }
    }

    @Nested
    @DisplayName("full-archive extract Zip Slip protection")
    class FullArchiveExtractTests {

        @Test
        @DisplayName("extract() must not create files outside extraction root from archive entry paths")
        void fullExtractMustRejectZipSlipEntries() throws Exception {
            final var archivePath = tempDir.resolve("zipslip.zip");
            final var outsideTarget = tempDir.resolve("evil-outside.txt");
            assertThat(outsideTarget).doesNotExist();

            try (var zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
                zos.putNextEntry(new ZipEntry("../../evil-outside.txt"));
                zos.write("pwned".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("safe.txt"));
                zos.write("ok".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            try (ZipArchive archive = new ZipArchive(session, archivePath.toFile(), true, null)) {
                try {
                    archive.extract();
                } catch (IOException | RuntimeException expected) {
                    assertThat(expected.getMessage() == null ? "" : expected.getMessage()
                            + (expected.getCause() != null && expected.getCause().getMessage() != null
                                    ? expected.getCause().getMessage() : ""))
                            .matches("(?s).*(escapes base directory|escapes temporary directory|absolute|null byte|SevenZip).*");
                }
            }

            assertThat(outsideTarget).doesNotExist();
            assertThat(tempDir.resolve("evil-outside.txt")).doesNotExist();
        }
    }
}
