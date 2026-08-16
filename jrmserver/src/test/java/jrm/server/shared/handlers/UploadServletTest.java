package jrm.server.shared.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;
import jrm.server.shared.handlers.UploadServlet.Result;

/**
 * Unit tests for {@link UploadServlet}.
 */
@DisplayName("UploadServlet")
class UploadServletTest {

    private WebSession webSession;
    private UploadServlet servlet;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        TestWebSessions.setWorkPath(tempDir);
        webSession = TestWebSessions.newAdminSession("upload-test");
        servlet = new UploadServlet();
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Nested
    @DisplayName("sanitizeHeader")
    class SanitizeHeaderTest {
        @Test
        @DisplayName("URL-decodes and removes traversal and control chars")
        void sanitizes() throws Exception {
            assertThat(servlet.sanitizeHeader("hello%20world")).isEqualTo("hello world");
            assertThat(servlet.sanitizeHeader("..secret")).isEqualTo("secret");
            assertThat(servlet.sanitizeHeader("a\0b")).isEqualTo("ab");
        }
    }

    @Nested
    @DisplayName("getXFileSize")
    class GetXFileSizeTest {
        @Test
        @DisplayName("parses valid size header")
        void parsesValid() {
            final HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("x-file-size")).thenReturn("12345");
            assertThat(servlet.getXFileSize(req)).isEqualTo(12345L);
        }

        @Test
        @DisplayName("returns -1 for invalid header")
        void returnsInvalid() {
            final HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("x-file-size")).thenReturn("abc");
            assertThat(servlet.getXFileSize(req)).isEqualTo(-1L);
        }

        @Test
        @DisplayName("returns -1 for missing header")
        void returnsMissing() {
            final HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("x-file-size")).thenReturn(null);
            assertThat(servlet.getXFileSize(req)).isEqualTo(-1L);
        }
    }

    @Nested
    @DisplayName("Result JSON serialization")
    class ResultJsonTest {
        @Test
        @DisplayName("Result serializes to JSON with status and extstatus")
        void serializes() {
            final Result result = new Result();
            result.status = 0;
            result.extstatus = "continue...";
            final String json = new com.google.gson.Gson().toJson(result);
            assertThat(json).contains("\"status\":0").contains("\"extstatus\":\"continue...\"");
        }
    }

    @Nested
    @DisplayName("doPost")
    class DoPostTest {
        @Test
        @DisplayName("init=1 with valid writable directory returns status 0")
        void initValidDirectory() throws Exception {
            // %work resolves to basePath/users/admin for multiuser admin session
            Files.createDirectories(Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn("test.txt");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work");
            lenient().when(req.getHeader("x-file-size")).thenReturn("100");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":0");
        }

        @ParameterizedTest(name = "init=1 blocked for parent={2} file={1}")
        @CsvSource({
            "reports, test.txt, %25work/reports",
            "reports/2024, test.txt, %25work/reports/2024",
            "., mame.dat.cache, %25work"
        })
        void initProtectedCacheLocationBlocked(final String relativeDir, final String fileName, final String parent)
                throws Exception {
            Files.createDirectories(webSession.getUser().getSettings().getWorkPath().resolve(relativeDir));
            assertInitBlocked(fileName, parent);
        }

        private void assertInitBlocked(final String fileName, final String parent) throws Exception {
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn(fileName);
            lenient().when(req.getHeader("x-file-parent")).thenReturn(parent);
            lenient().when(req.getHeader("x-file-size")).thenReturn("100");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            assertThat(writer.toString()).contains("\"status\":11").contains("protected cache");
        }

        @Test
        @DisplayName("init=1 with hmac key filename returns status 11")
        void initHmacKeyFilenameBlocked() throws Exception {
            Files.createDirectories(Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn(".cache-hmac");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work");
            lenient().when(req.getHeader("x-file-size")).thenReturn("32");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":11").contains("protected cache");
        }

        @Test
        @DisplayName("init=1 with settings directory returns status 11")
        void initSettingsDirectoryBlocked() throws Exception {
            final var workPath = webSession.getUser().getSettings().getWorkPath();
            Files.createDirectories(workPath.resolve("settings"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn("admin.properties");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work/settings");
            lenient().when(req.getHeader("x-file-size")).thenReturn("100");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":11").contains("protected cache");
        }

        @Test
        @DisplayName("init=1 with DirScan cache directory returns status 11")
        void initDirScanCacheDirectoryBlocked() throws Exception {
            final var workPath = webSession.getUser().getSettings().getWorkPath();
            Files.createDirectories(workPath.resolve("cache"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn("scan.bin");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work/cache");
            lenient().when(req.getHeader("x-file-size")).thenReturn("100");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":11").contains("protected cache");
        }

        @Test
        @DisplayName("init=1 with user directory named reports-backup returns status 0")
        void initSimilarlyNamedUserDirectoryAllowed() throws Exception {
            final var workPath = webSession.getUser().getSettings().getWorkPath();
            Files.createDirectories(workPath.resolve("reports-backup"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn("test.txt");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work/reports-backup");
            lenient().when(req.getHeader("x-file-size")).thenReturn("100");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":0");
        }

        @Test
        @DisplayName("init=1 with nested user directory project/reports/2024 returns status 0")
        void initNestedReportsDirectoryAllowed() throws Exception {
            final var workPath = webSession.getUser().getSettings().getWorkPath();
            Files.createDirectories(workPath.resolve("project").resolve("reports").resolve("2024"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn("1");
            lenient().when(req.getHeader("x-file-name")).thenReturn("test.txt");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work/project/reports/2024");
            lenient().when(req.getHeader("x-file-size")).thenReturn("100");

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":0");
        }

        @Test
        @DisplayName("init missing returns status 10")
        void initMissing() throws Exception {
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getParameter("init")).thenReturn(null);

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPost(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":10");
        }
    }

    @Nested
    @DisplayName("doPut")
    class DoPutTest {
        @Test
        @DisplayName("reports cache directory is rejected with status 11")
        void reportsCacheDirectoryBlocked() throws Exception {
            final var workPath = webSession.getUser().getSettings().getWorkPath();
            Files.createDirectories(workPath.resolve("reports"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getHeader("x-file-name")).thenReturn("test.txt");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work/reports");
            lenient().when(req.getInputStream()).thenReturn(new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return true;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(final jakarta.servlet.ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() throws IOException {
                    return -1;
                }
            });

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPut(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":11").contains("protected cache");
        }

        @Test
        @DisplayName("profile .cache filename is rejected with status 11")
        void profileCacheFilenameBlocked() throws Exception {
            Files.createDirectories(Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin"));
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getHeader("x-file-name")).thenReturn("mame.dat.cache");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work");
            lenient().when(req.getInputStream()).thenReturn(new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return true;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(final jakarta.servlet.ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() throws IOException {
                    return -1;
                }
            });

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPut(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":11").contains("protected cache");
        }

        @Test
        @DisplayName("similarly named user directory is allowed and writes file")
        void similarlyNamedUserDirectoryAllowed() throws Exception {
            final var workPath = webSession.getUser().getSettings().getWorkPath();
            Files.createDirectories(workPath.resolve("reports-backup"));
            final byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
            final HttpSession httpSession = mock(HttpSession.class);
            when(httpSession.getAttribute("session")).thenReturn(webSession);
            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getSession()).thenReturn(httpSession);
            lenient().when(req.getRequestURI()).thenReturn("/upload/");
            lenient().when(req.getHeader("x-file-name")).thenReturn("uploaded.txt");
            lenient().when(req.getHeader("x-file-parent")).thenReturn("%25work/reports-backup");
            lenient().when(req.getHeader("x-file-size")).thenReturn(String.valueOf(content.length));
            lenient().when(req.getInputStream()).thenReturn(new ServletInputStream() {
                private final ByteArrayInputStream delegate = new ByteArrayInputStream(content);

                @Override
                public boolean isFinished() {
                    return delegate.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(final jakarta.servlet.ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() throws IOException {
                    return delegate.read();
                }
            });

            final StringWriter writer = new StringWriter();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(writer));

            servlet.doPut(req, resp);

            final String json = writer.toString();
            assertThat(json).contains("\"status\":3");
            assertThat(Files.readString(workPath.resolve("reports-backup").resolve("uploaded.txt"))).isEqualTo("file content");
        }
    }

    @Nested
    @DisplayName("doUpload")
    class DoUploadTest {
        @Test
        @DisplayName("writes file content and sets status 3 on success")
        void uploadSuccess(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            final Path filepath = tempDir.resolve("uploaded.txt");
            final byte[] content = "file content".getBytes(StandardCharsets.UTF_8);

            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getHeader("x-file-size")).thenReturn(String.valueOf(content.length));
            lenient().when(req.getInputStream()).thenReturn(new ServletInputStream() {
                private final ByteArrayInputStream delegate = new ByteArrayInputStream(content);

                @Override
                public boolean isFinished() {
                    return delegate.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(final jakarta.servlet.ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() throws IOException {
                    return delegate.read();
                }
            });

            final Result result = new Result();
            servlet.doUpload(req, result, "uploaded.txt", filepath);

            assertThat(result.status).isEqualTo(3);
            assertThat(Files.readString(filepath)).isEqualTo("file content");
        }

        @Test
        @DisplayName("size mismatch sets status 21")
        void sizeMismatch(@TempDir final Path tempDir) throws Exception {
            TestWebSessions.setWorkPath(tempDir);
            final Path filepath = tempDir.resolve("mismatch.txt");
            final byte[] content = "short".getBytes(StandardCharsets.UTF_8);

            final HttpServletRequest req = mock(HttpServletRequest.class);
            lenient().when(req.getHeader("x-file-size")).thenReturn("999");
            lenient().when(req.getInputStream()).thenReturn(new ServletInputStream() {
                private final ByteArrayInputStream delegate = new ByteArrayInputStream(content);

                @Override
                public boolean isFinished() {
                    return delegate.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(final jakarta.servlet.ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() throws IOException {
                    return delegate.read();
                }
            });

            final Result result = new Result();
            servlet.doUpload(req, result, "mismatch.txt", filepath);

            assertThat(result.status).isEqualTo(21);
        }
    }
}