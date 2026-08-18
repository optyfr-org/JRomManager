package jrm.fullserver.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.fullserver.ServerSettings;
import jrm.server.shared.TestWebSessions;

/**
 * Unit tests for {@link H2}.
 */
@DisplayName("H2 database")
class H2Test {

    private H2 h2;
    

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        ServerSettings settings;
        TestWebSessions.setWorkPath(tempDir);
        settings = new ServerSettings();
        h2 = new H2(settings);
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("resolveName")
    class ResolveNameTest {
        @Test
        @DisplayName("full=true appends .mv.db when not present")
        void appendsMvDb() {
            assertThat(h2.resolveName("testdb", true)).hasToString("testdb.mv.db");
        }

        @Test
        @DisplayName("full=true does not append when .db already present")
        void doesNotAppendWhenDbPresent() {
            assertThat(h2.resolveName("testdb.db", true)).hasToString("testdb.db");
        }

        @Test
        @DisplayName("full=false strips .mv.db suffix when present")
        void stripsDbSuffix() {
            assertThat(h2.resolveName("testdb.mv.db", false)).hasToString("testdb");
        }

        @Test
        @DisplayName("full=false does not over-truncate a bare .db suffix")
        void doesNotStripBareDbSuffix() {
            assertThat(h2.resolveName("testdb.db", false)).hasToString("testdb.db");
        }

        @Test
        @DisplayName("full=false returns name as-is when no .db suffix")
        void noSuffix() {
            assertThat(h2.resolveName("testdb", false)).hasToString("testdb");
        }
    }

    @Nested
    @DisplayName("getDBPath")
    class GetDBPathTest {
        @Test
        @DisplayName("replaces %w placeholder with work path")
        void replacesWorkPlaceholder(@TempDir final Path tempDir) {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            final Path path = h2.getDBPath("%w/mydb", false);
            assertThat(path).isNotNull();
        }

        @Test
        @DisplayName("resolves relative name against work path")
        void resolvesRelative(@TempDir final Path tempDir) {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            final Path path = h2.getDBPath("mydb", false);
            assertThat(path).isNotNull();
        }
    }

    @Nested
    @DisplayName("shouldDropDB")
    class ShouldDropDBTest {
        @Test
        @DisplayName("returns true when DB file does not exist")
        void dbMissing(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            final Path cpsPath = tempDir.resolve("source.access");
            Files.createFile(cpsPath);
            assertThat(h2.shouldDropDB(cpsPath, null)).isTrue();
        }

        @Test
        @DisplayName("returns false when source missing but DB exists")
        void sourceMissingDbExists(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            final Path cpsPath = tempDir.resolve("source.access");
            // Don't create source, but create DB file
            final Path dbPath = tempDir.resolve("source.access.mv.db");
            Files.createFile(dbPath);
            assertThat(h2.shouldDropDB(cpsPath, null)).isFalse();
        }

        @Test
        @DisplayName("returns true when source is newer than DB creation time")
        void sourceNewerThanDb(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            final Path cpsPath = tempDir.resolve("source.access");
            final Path dbPath = tempDir.resolve("source.access.mv.db");
            Files.createFile(dbPath);
            final Instant base = Instant.now();
            Files.getFileAttributeView(dbPath, java.nio.file.attribute.BasicFileAttributeView.class).setTimes(
                    FileTime.from(base),
                    FileTime.from(base),
                    FileTime.from(base));
            Files.createFile(cpsPath);
            Files.setLastModifiedTime(cpsPath, FileTime.from(base.plusSeconds(1)));
            assertThat(h2.shouldDropDB(cpsPath, null)).isTrue();
        }

        @Test
        @DisplayName("returns false when DB is newer than source")
        void dbNewerThanSource(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            final Path cpsPath = tempDir.resolve("source.access");
            final Path dbPath = tempDir.resolve("source.access.mv.db");
            final Instant base = Instant.now();
            Files.createFile(cpsPath);
            Files.setLastModifiedTime(cpsPath, FileTime.from(base));
            Files.createFile(dbPath);
            Files.setLastModifiedTime(dbPath, FileTime.from(base.plusSeconds(1)));
            assertThat(h2.shouldDropDB(cpsPath, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("connectToDB")
    class ConnectToDBTest {
        @Test
        @DisplayName("connects to in-memory H2 database")
        void connectInMemory(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            h2 = new H2(new ServerSettings());
            try (Connection conn = h2.connectToDB("testdb", false, true, false)) {
                assertThat(conn).isNotNull();
                assertThat(conn.isClosed()).isFalse();
            }
        }
    }
}