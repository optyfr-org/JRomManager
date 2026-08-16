package jrm.server.shared.datasources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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

import jrm.server.shared.TestDataSets;
import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link RemoteFileChooserXMLResponse}.
 */
@DisplayName("RemoteFileChooserXMLResponse")
class RemoteFileChooserXMLResponseTest {

    @Nested
    @DisplayName("directory-only contexts")
    class DirectoryContextsTest {
        @Test
        @DisplayName("tfRomsDest is directory-only with no path matcher")
        void tfRomsDest() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("tfRomsDest");
            assertThat(opts.isDir).isTrue();
            assertThat(opts.pathmatcher).isNull();
        }

        @Test
        @DisplayName("listSrcDir is directory-only")
        void listSrcDir() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("listSrcDir");
            assertThat(opts.isDir).isTrue();
            assertThat(opts.pathmatcher).isNull();
        }
        
        @ParameterizedTest(name = "{0} is directory-only")
        @ValueSource(strings = {"tfSrcDir", "updDat", "updTrnt"})
        void directoryOnlyContexts(String context) {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options(context);
            assertThat(opts.isDir).isTrue();
        }
    }

    @Nested
    @DisplayName("torrent file context")
    class TorrentContextTest {
        @Test
        @DisplayName("addTrnt matches glob:*.torrent and is not directory")
        void addTrnt() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("addTrnt");
            assertThat(opts.isDir).isFalse();
            assertThat(opts.pathmatcher).isEqualTo("glob:*.torrent");
        }
    }

    @Nested
    @DisplayName("DAT file contexts")
    class DatContextsTest {
        private static final String GLOB_XML_DAT = "glob:*.{xml,dat}";

        @Test
        @DisplayName("importDat matches glob:*.{xml,dat}")
        void importDat() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("importDat");
            assertThat(opts.isDir).isFalse();
            assertThat(opts.pathmatcher).isEqualTo(GLOB_XML_DAT);
        }

        @Test
        @DisplayName("addDat matches glob:*.{xml,dat}")
        void addDat() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("addDat");
            assertThat(opts.pathmatcher).isEqualTo(GLOB_XML_DAT);
        }

        @Test
        @DisplayName("tfDstDat matches glob:*.{xml,dat}")
        void tfDstDat() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("tfDstDat");
            assertThat(opts.pathmatcher).isEqualTo(GLOB_XML_DAT);
        }

        @Test
        @DisplayName("exportFixDat matches glob:*.{xml,dat}")
        void exportFixDat() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("exportFixDat");
            assertThat(opts.isDir).isFalse();
            assertThat(opts.pathmatcher).isEqualTo(GLOB_XML_DAT);
        }
    }

    @Nested
    @DisplayName("archive context")
    class ArchiveContextTest {
        @Test
        @DisplayName("addArc matches archive extensions")
        void addArc() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("addArc");
            assertThat(opts.isDir).isFalse();
            assertThat(opts.pathmatcher)
                .contains("zip")
                .contains("7z")
                .contains("rar");
        }
    }

    @Nested
    @DisplayName("settings contexts")
    class SettingsContextTest {
        @Test
        @DisplayName("importSettings matches glob:*.properties")
        void importSettings() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("importSettings");
            assertThat(opts.isDir).isFalse();
            assertThat(opts.pathmatcher).isEqualTo("glob:*.properties");
        }

        @Test
        @DisplayName("exportSettings matches glob:*.properties")
        void exportSettings() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("exportSettings");
            assertThat(opts.pathmatcher).isEqualTo("glob:*.properties");
        }
    }

    @Nested
    @DisplayName("default context")
    class DefaultContextTest {
        @Test
        @DisplayName("unknown context defaults to no filter, not directory")
        void unknownContext() {
            final RemoteFileChooserXMLResponse.Options opts = new RemoteFileChooserXMLResponse.Options("unknown");
            assertThat(opts.isDir).isFalse();
            assertThat(opts.pathmatcher).isNull();
        }
    }

    @Nested
    @DisplayName("case insensitive file finder")
    class CaseInsensitiveFileFinderTest {
        @TempDir
        Path workPath;

        @Test
        @DisplayName("finds an existing file ignoring case")
        void findsExistingFile() throws Exception {
            final Path file = workPath.resolve("FiLe.TxT");
            Files.createFile(file);

            final Optional<Path> result = RemoteFileChooserXMLResponse.CaseInsensitiveFileFinder.findFileIgnoreCase(workPath, "file.txt");

            assertThat(result).isPresent().hasValue(file);
        }

        @Test
        @DisplayName("returns empty when file does not exist")
        void returnsEmptyWhenMissing() {
            final Optional<Path> result = RemoteFileChooserXMLResponse.CaseInsensitiveFileFinder.findFileIgnoreCase(workPath, "missing.txt");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("finds file by full string path ignoring case")
        void findsByStringPath() throws Exception {
            final Path file = workPath.resolve("MiXeD.dat");
            Files.createFile(file);

            final Optional<java.io.File> result = RemoteFileChooserXMLResponse.CaseInsensitiveFileFinder.findFileIgnoreCase(file.toString().toLowerCase());

            assertThat(result).isPresent().hasValue(file.toFile());
        }
    }

    @Nested
    @DisplayName("operations")
    class OperationsTest {

        @TempDir
        Path workPath;

        private WebSession session;

        @BeforeEach
        void setUp() {
            TestWebSessions.setWorkPath(workPath);
            session = TestWebSessions.newAdminSession("remote-chooser-test");
        }

        @AfterEach
        void tearDown() {
            TestWebSessions.resetStaticState();
        }

        @Test
        @DisplayName("fetch lists directory entries")
        void fetch() throws Exception {
            Files.createDirectory(session.getUser().getSettings().getWorkPath().resolve("sub"));
            final String xml = """
                    <request>
                      <operationType>fetch</operationType>
                      <data>
                        <context>listSrcDir</context>
                        <root>%work</root>
                        <parent>%work/sub</parent>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>").contains("Name=\"..\"").contains("isDir=\"true\"");
        }

        @Test
        @DisplayName("add of hmac key name is rejected")
        void addProtectedHmacKeyRejected() throws Exception {
            final String xml = """
                    <request>
                      <operationType>add</operationType>
                      <data>
                        <context>listSrcDir</context>
                        <root>%work</root>
                        <parent>%work</parent>
                        <Name>.cache-hmac</Name>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("Protected cache location");
            assertThat(session.getUser().getSettings().getWorkPath().resolve(".cache-hmac")).doesNotExist();
        }

        @Test
        @DisplayName("add creates a directory")
        void add() throws Exception {
            final String xml = """
                    <request>
                      <operationType>add</operationType>
                      <data>
                        <context>listSrcDir</context>
                        <root>%work</root>
                        <parent>%work</parent>
                        <Name>newfolder</Name>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>").contains("Name=\"newfolder\"");
            assertThat(session.getUser().getSettings().getWorkPath().resolve("newfolder")).exists();
        }

        @Test
        @DisplayName("update renames a directory")
        void update() throws Exception {
            Files.createDirectory(session.getUser().getSettings().getWorkPath().resolve("oldname"));
            final String xml = """
                    <request>
                      <operationType>update</operationType>
                      <data>
                        <context>listSrcDir</context>
                        <root>%work</root>
                        <parent>%work</parent>
                        <Name>newname</Name>
                      </data>
                      <oldValues>
                        <Name>oldname</Name>
                      </oldValues>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>").contains("Name=\"newname\"");
            assertThat(session.getUser().getSettings().getWorkPath().resolve("newname")).exists();
        }

        @Test
        @DisplayName("remove deletes a directory")
        void remove() throws Exception {
            Files.createDirectory(session.getUser().getSettings().getWorkPath().resolve("todelete"));
            final String xml = """
                    <request>
                      <operationType>remove</operationType>
                      <data>
                        <context>listSrcDir</context>
                        <root>%work</root>
                        <parent>%work</parent>
                        <Name>todelete</Name>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>");
            assertThat(session.getUser().getSettings().getWorkPath().resolve("todelete")).doesNotExist();
        }

        @Test
        @DisplayName("custom expand lists provided paths")
        void customExpand() throws Exception {
            final String xml = """
                    <request>
                      <operationType>custom</operationType>
                      <operationId>expand</operationId>
                      <data>
                        <root>%work</root>
                        <parent>%work</parent>
                        <paths>%work</paths>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>").contains("<record");
        }

        @Test
        @DisplayName("custom extract_here extracts a zip file")
        void customExtractHere() throws Exception {
            final Path zip = session.getUser().getSettings().getWorkPath().resolve("archive.zip");
            try (final var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
                zos.putNextEntry(new ZipEntry("nested.txt"));
                zos.write("content".getBytes());
                zos.closeEntry();
            }
            final String xml = """
                    <request>
                      <operationType>custom</operationType>
                      <operationId>extract_here</operationId>
                      <data>
                        <root>%work</root>
                        <Path>%work/archive.zip</Path>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>");
            assertThat(session.getUser().getSettings().getWorkPath().resolve("nested.txt")).exists();
        }

        @Test
        @DisplayName("extract skips zip entries that target cache files")
        void customExtractHereSkipsProtectedCacheEntries() throws Exception {
            final Path zip = session.getUser().getSettings().getWorkPath().resolve("poison.zip");
            try (final var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
                zos.putNextEntry(new ZipEntry("safe.txt"));
                zos.write("ok".getBytes());
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("evil.dat.cache"));
                zos.write("poison".getBytes());
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry(".cache-hmac"));
                zos.write(new byte[32]);
                zos.closeEntry();
            }
            final String xml = """
                    <request>
                      <operationType>custom</operationType>
                      <operationId>extract_here</operationId>
                      <data>
                        <root>%work</root>
                        <Path>%work/poison.zip</Path>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>");
            assertThat(session.getUser().getSettings().getWorkPath().resolve("safe.txt")).exists();
            assertThat(session.getUser().getSettings().getWorkPath().resolve("evil.dat.cache")).doesNotExist();
            assertThat(session.getUser().getSettings().getWorkPath().resolve(".cache-hmac")).doesNotExist();
        }

        @Test
        @DisplayName("extract skips zip entries that escape the destination directory")
        void customExtractHereSkipsZipSlipEntries() throws Exception {
            final Path work = session.getUser().getSettings().getWorkPath();
            final Path zip = work.resolve("slip.zip");
            try (final var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
                zos.putNextEntry(new ZipEntry("safe.txt"));
                zos.write("ok".getBytes());
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("../evil-outside.txt"));
                zos.write("slip".getBytes());
                zos.closeEntry();
            }
            final String xml = """
                    <request>
                      <operationType>custom</operationType>
                      <operationId>extract_here</operationId>
                      <data>
                        <root>%work</root>
                        <Path>%work/slip.zip</Path>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>");
            assertThat(work.resolve("safe.txt")).exists();
            assertThat(work.resolveSibling("evil-outside.txt")).doesNotExist();
            assertThat(work.getParent().resolve("evil-outside.txt")).doesNotExist();
        }

        @Test
        @DisplayName("custom extract_subfolder extracts a zip file into a subfolder")
        void customExtractSubfolder() throws Exception {
            final Path zip = session.getUser().getSettings().getWorkPath().resolve("archive.zip");
            try (final var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
                zos.putNextEntry(new ZipEntry("nested.txt"));
                zos.write("content".getBytes());
                zos.closeEntry();
            }
            final String xml = """
                    <request>
                      <operationType>custom</operationType>
                      <operationId>extract_subfolder</operationId>
                      <data>
                        <root>%work</root>
                        <Path>%work/archive.zip</Path>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>");
            assertThat(session.getUser().getSettings().getWorkPath().resolve("archive/nested.txt")).exists();
        }

        @Test
        @DisplayName("custom expand with addArc context lists archive files recursively")
        void customExpandAddArc() throws Exception {
            final Path sub = session.getUser().getSettings().getWorkPath().resolve("sub");
            Files.createDirectories(sub);
            Files.createFile(sub.resolve("archive.zip"));
            final String xml = """
                    <request>
                      <operationType>custom</operationType>
                      <operationId>expand</operationId>
                      <data>
                        <root>%work</root>
                        <parent>%work</parent>
                        <context>addArc</context>
                        <paths>%work</paths>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("<status>0</status>").contains("archive.zip");
        }

        @Test
        @DisplayName("initial path marks selected entry in fetch response")
        void fetchWithInitialPath() throws Exception {
            Files.createDirectories(session.getUser().getSettings().getWorkPath().resolve("sub"));
            final String xml = """
                    <request>
                      <operationType>fetch</operationType>
                      <data>
                        <context>listSrcDir</context>
                        <root>%work</root>
                        <parent>%work</parent>
                        <initialPath>%work/sub</initialPath>
                      </data>
                    </request>
                    """;
            final String output = TestDataSets.processResponse(new RemoteFileChooserXMLResponse(TestDataSets.xmlRequest(session, xml)));
            assertThat(output).contains("isSelected=\"true\"").contains("Name=\"sub\"");
        }
    }
}