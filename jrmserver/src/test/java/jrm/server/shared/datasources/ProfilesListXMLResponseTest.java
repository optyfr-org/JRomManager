package jrm.server.shared.datasources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.security.PathAbstractor;
import jrm.server.shared.TestDataSets;
import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link ProfilesListXMLResponse}.
 */
@DisplayName("ProfilesListXMLResponse")
class ProfilesListXMLResponseTest {

    @TempDir
    Path workPath;

    private WebSession session;
    private Path xmlfiles;

    @BeforeEach
    void setUp() throws Exception {
        TestWebSessions.setWorkPath(workPath);
        session = TestWebSessions.newAdminSession("profiles-list-test");
        xmlfiles = session.getUser().getSettings().getWorkPath().resolve("xmlfiles");
        Files.createDirectories(xmlfiles);
        Files.copy(TestDataSets.resolveResource("dats/MAME 0.288 Software List ROMs (merged)/a5200.xml").toPath(), xmlfiles.resolve("a5200.xml"), StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Test
    @DisplayName("fetch lists profiles in the xmlfiles directory")
    void fetchProfiles() throws Exception {
        final String xml = """
                <request>
                  <operationType>fetch</operationType>
                </request>
                """;
        final String output = TestDataSets.processResponse(new ProfilesListXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output).contains("<status>0</status>").contains("a5200.xml").contains("<record");
    }

    @Test
    @DisplayName("DropCache custom operation deletes the cache file")
    void dropCache() throws Exception {
        Files.createFile(xmlfiles.resolve("a5200.xml.cache"));
        final String xml = """
                <request>
                  <operationType>custom</operationType>
                  <operationId>DropCache</operationId>
                  <data>
                    <File>a5200.xml</File>
                  </data>
                </request>
                """;
        final String output = TestDataSets.processResponse(new ProfilesListXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output).contains("<status>0</status>");
        assertThat(xmlfiles.resolve("a5200.xml.cache")).doesNotExist();
    }

    @Test
    @DisplayName("add copies Src into xmlfiles under a plain File name")
    void addCopiesIntoWorkspace() throws Exception {
        final Path src = workPath.resolve("src-copy.xml");
        Files.copy(xmlfiles.resolve("a5200.xml"), src, StandardCopyOption.REPLACE_EXISTING);
        final String srcRel = PathAbstractor.getRelativePath(session, src).toString().replace('\\', '/');
        final String xml = """
                <request>
                  <operationType>add</operationType>
                  <data>
                    <Src>%s</Src>
                    <File>copied.xml</File>
                  </data>
                </request>
                """.formatted(srcRel);
        final String output = TestDataSets.processResponse(new ProfilesListXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output).contains("<status>0</status>").contains("copied.xml");
        assertThat(xmlfiles.resolve("copied.xml")).exists();
    }

    @Test
    @DisplayName("add rejects absolute File destinations")
    void addRejectsAbsoluteFile(@TempDir Path outside) throws Exception {
        final Path victim = outside.resolve("victim.xml");
        final Path src = workPath.resolve("src-abs.xml");
        Files.copy(xmlfiles.resolve("a5200.xml"), src, StandardCopyOption.REPLACE_EXISTING);
        final String srcRel = PathAbstractor.getRelativePath(session, src).toString().replace('\\', '/');
        final String xml = """
                <request>
                  <operationType>add</operationType>
                  <data>
                    <Src>%s</Src>
                    <File>%s</File>
                  </data>
                </request>
                """.formatted(srcRel, victim.toAbsolutePath());
        final String output = TestDataSets.processResponse(new ProfilesListXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output).contains("<status>-1</status>").contains("path traversal");
        assertThat(victim).doesNotExist();
    }

    @Test
    @DisplayName("add rejects relative traversal in File")
    void addRejectsRelativeTraversal() throws Exception {
        final Path src = workPath.resolve("src-trav.xml");
        Files.copy(xmlfiles.resolve("a5200.xml"), src, StandardCopyOption.REPLACE_EXISTING);
        final Path escaped = workPath.resolve("escaped.xml");
        final String srcRel = PathAbstractor.getRelativePath(session, src).toString().replace('\\', '/');
        final String xml = """
                <request>
                  <operationType>add</operationType>
                  <data>
                    <Src>%s</Src>
                    <File>../escaped.xml</File>
                  </data>
                </request>
                """.formatted(srcRel);
        final String output = TestDataSets.processResponse(new ProfilesListXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output).contains("<status>-1</status>").contains("path traversal");
        assertThat(escaped).doesNotExist();
    }

    @Test
    @DisplayName("resolveContainedFile rejects absolute, multi-segment, and empty names")
    void resolveContainedFileRejectsUnsafeNames() {
        final Path base = xmlfiles.toAbsolutePath().normalize();
        assertThat(ProfilesListXMLResponse.resolveContainedFile(base, "ok.xml")).isEqualTo(base.resolve("ok.xml"));
        assertThatThrownBy(() -> ProfilesListXMLResponse.resolveContainedFile(base, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ProfilesListXMLResponse.resolveContainedFile(base, "")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ProfilesListXMLResponse.resolveContainedFile(base, "..")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ProfilesListXMLResponse.resolveContainedFile(base, "../x.xml")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ProfilesListXMLResponse.resolveContainedFile(base, "a/b.xml")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ProfilesListXMLResponse.resolveContainedFile(base, base.resolve("x.xml").toString()))
                .isInstanceOf(SecurityException.class);
    }
}
