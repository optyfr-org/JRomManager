package jrm.server.shared.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link DownloadServlet}.
 */
@DisplayName("DownloadServlet")
class DownloadServletTest {

    private WebSession webSession;
    private DownloadServlet servlet;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        TestWebSessions.setWorkPath(tempDir);
        webSession = TestWebSessions.newAdminSession("download-test");
        servlet = new DownloadServlet();
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    private HttpServletRequest buildRequest(final String uri, final String pathParam) {
        final HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("session")).thenReturn(webSession);
        final HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getSession()).thenReturn(httpSession);
        lenient().when(req.getRequestURI()).thenReturn(uri);
        lenient().when(req.getParameter("path")).thenReturn(pathParam);
        return req;
    }

    @Nested
    @DisplayName("doPost with null path")
    class NullPathTest {
        @Test
        @DisplayName("null path parameter returns SC_BAD_REQUEST")
        void nullPath() {
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/download/", null);
            servlet.doPost(req, resp);
            verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("doPost with regular file")
    class RegularFileTest {
        @Test
        @DisplayName("streams file with Content-Disposition and SC_OK")
        void downloadsFile() throws Exception {
            // %work resolves to basePath/users/admin for multiuser admin session
            final Path workDir = Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin");
            Files.createDirectories(workDir);
            final Path file = Files.createFile(workDir.resolve("test.txt"));
            Files.writeString(file, "hello");

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public void write(final int b) throws IOException {
                    out.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(final jakarta.servlet.WriteListener writeListener) {
                    // no-op
                }
            });

            final HttpServletRequest req = buildRequest("/download/", "%work/test.txt");
            servlet.doPost(req, resp);

            verify(resp).setStatus(HttpServletResponse.SC_OK);
            assertThat(out.toByteArray()).isEqualTo("hello".getBytes());
        }

        @Test
        @DisplayName("hmac key file download is forbidden")
        void hmacKeyDownloadForbidden() throws Exception {
            final Path workDir = Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin");
            Files.createDirectories(workDir.resolve("settings"));
            Files.write(workDir.resolve("settings").resolve(".cache-hmac"), new byte[32]);

            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/download/", "%work/settings/.cache-hmac");
            servlet.doPost(req, resp);

            verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        @DisplayName("profile cache download is forbidden")
        void cacheFileDownloadForbidden() throws Exception {
            final Path workDir = Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin");
            Files.createDirectories(workDir);
            Files.writeString(workDir.resolve("mame.dat.cache"), "poison");

            final HttpServletResponse resp = mock(HttpServletResponse.class);
            final HttpServletRequest req = buildRequest("/download/", "%work/mame.dat.cache");
            servlet.doPost(req, resp);

            verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("doPost with directory")
    class DirectoryTest {
        @Test
        @DisplayName("streams ZIP of directory with SC_OK")
        void downloadsDirectory() throws Exception {
            // %work resolves to basePath/users/admin for multiuser admin session
            final Path workDir = Path.of(System.getProperty("jrommanager.dir")).resolve("users").resolve("admin");
            Files.createDirectories(workDir);
            final Path dir = Files.createDirectory(workDir.resolve("mydir"));
            Files.writeString(dir.resolve("a.txt"), "aaa");

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public void write(final int b) throws IOException {
                    out.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(final jakarta.servlet.WriteListener writeListener) {
                    // no-op
                }
            });

            final HttpServletRequest req = buildRequest("/download/", "%work/mydir");
            servlet.doPost(req, resp);

            verify(resp).setContentType("application/zip");
            verify(resp).setStatus(HttpServletResponse.SC_OK);
            assertThat(out.toByteArray()).isNotEmpty();
        }
    }
}