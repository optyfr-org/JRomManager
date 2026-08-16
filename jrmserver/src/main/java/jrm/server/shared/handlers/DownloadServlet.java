package jrm.server.shared.handlers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.Log;
import jrm.security.CachePathGuard;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;
import lombok.val;

/**
 * Servlet responsible for handling file and directory download requests.
 * <p>
 * This servlet processes POST requests to the {@code /download/} endpoint and provides two types of download functionality:
 * <ul>
 * <li><b>Single file download</b> - Streams the requested file directly to the client with appropriate content type, size, and
 * caching headers</li>
 * <li><b>Directory download</b> - Packages the requested directory and all its contents into a ZIP archive and streams it to the
 * client</li>
 * </ul>
 * <p>
 * The servlet uses {@link PathAbstractor} to resolve abstract path references (such as {@code %shared}, {@code %work},
 * {@code %presets}) to actual file system paths, ensuring secure access control based on the user's session permissions.
 * <p>
 * File paths are specified via the {@code path} request parameter and are resolved relative to the session's accessible
 * directories.
 * 
 * @see PathAbstractor
 * @see WebSession
 * 
 * @author JRM Project
 * 
 * @version 1.0
 * 
 * @since 1.0
 */
public class DownloadServlet extends HttpServlet {
    /**
     * Handles POST requests for file and directory downloads.
     * <p>
     * Routes requests to the {@link #download(HttpServletRequest, HttpServletResponse)} method if the request URI matches
     * {@code /download/}. All other POST requests are delegated to the parent servlet implementation.
     * <p>
     * HTTP status codes returned:
     * <ul>
     * <li>{@code 400 Bad Request} - if the path parameter is missing</li>
     * <li>{@code 500 Internal Server Error} - on I/O errors during download</li>
     * </ul>
     * 
     * @param req the HTTP servlet request containing the download path parameter
     * @param resp the HTTP servlet response for streaming the file or ZIP archive
     */
    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
        try {
            if ("/download/".equals(req.getRequestURI())) {
                download(req, resp);
            } else
                super.doPost(req, resp);
        } catch (IOException | ServletException e) {
            Log.err(e.getMessage(), e);
        }

    }

    /**
     * Processes the download request and streams the file or directory to the client.
     * <p>
     * This method performs the following operations:
     * <ol>
     * <li>Retrieves the {@link WebSession} from the HTTP session</li>
     * <li>Creates a {@link PathAbstractor} to resolve the requested path</li>
     * <li>Determines if the path refers to a file or directory</li>
     * <li>For files: streams the file with appropriate HTTP headers including:
     * <ul>
     * <li>Content-Disposition with UTF-8 encoded filename</li>
     * <li>Content-Type based on file probe</li>
     * <li>Content-Length matching file size</li>
     * <li>Last-Modified timestamp</li>
     * <li>Cache-Control with 24-hour max-age</li>
     * </ul>
     * </li>
     * <li>For directories: creates a ZIP archive containing all files and subdirectories, streaming it with {@code application/zip}
     * content type. The ZIP archive uses UTF-8 encoding for entry names to support international characters.</li>
     * </ol>
     * <p>
     * <b>Exception handling:</b> {@link SecurityException} from {@link PathAbstractor#getAbsolutePath(String)} propagates to the
     * caller, while {@link IOException} is caught internally and converted to an HTTP 500 error response.
     * 
     * @param req the HTTP servlet request containing the {@code path} parameter
     * @param resp the HTTP servlet response for writing the download stream
     * 
     * @throws SecurityException if the requested path is not accessible to the current user
     */
    private void download(final HttpServletRequest req, final HttpServletResponse resp) {
        try {
            val ws = (WebSession) req.getSession().getAttribute("session");
            val pathAbstractor = new PathAbstractor(ws);
            val path = req.getParameter("path");
            if (path != null) {
                val file = pathAbstractor.getAbsolutePath(path);
                if (CachePathGuard.isProtectedFile(ws, file)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                if (Files.isRegularFile(file))
                    streamFile(resp, file);
                else
                    streamZippedDirectory(resp, file);
            } else
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch (final IOException e) {
            try {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (final IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
        }
    }

    /**
     * Streams a directory and its contents as a ZIP archive to the client.
     * <p>
     * This method creates a ZIP output stream from the response's output stream and walks the file tree starting at the
     * specified directory path, adding each file as a ZIP entry. The ZIP archive uses UTF-8 encoding for entry names.
     * </p>
     *
     * @param resp the HTTP servlet response for writing the ZIP stream
     * @param file the directory path to zip and stream
     * @throws IOException if an I/O error occurs during streaming
     */
    private void streamZippedDirectory(final HttpServletResponse resp, final Path file) throws IOException {
        val dlfilename = file.getFileName().toString() + ".zip";
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(dlfilename, "UTF-8") + "; filename=\"" + dlfilename + "\"");
        resp.setHeader("Content-Transfer-Encoding", "binary");
        resp.setContentType("application/zip");
        resp.setStatus(HttpServletResponse.SC_OK);
        final var zos = new ZipOutputStream(resp.getOutputStream(), StandardCharsets.UTF_8);
        Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path f, final BasicFileAttributes attrs) throws IOException {
                zos.putNextEntry(new ZipEntry(file.relativize(f).toString()));
                Files.copy(f, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
        zos.finish();
    }

    /**
     * Streams a single file to the client with appropriate HTTP headers.
     *
     * @param resp the HTTP servlet response for streaming the file
     * @param file the path to the file to stream
     * @throws IOException if an I/O error occurs during streaming
     */
    private void streamFile(final HttpServletResponse resp, final Path file) throws IOException {
        val dlfilename = file.getFileName().toString();
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(dlfilename, "UTF-8") + "; filename=\"" + dlfilename + "\"");
        resp.setHeader("Content-Transfer-Encoding", "binary");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentLengthLong(Files.size(file));
        resp.setContentType(Files.probeContentType(file));
        resp.setDateHeader("Last-Modified", Files.getLastModifiedTime(file).toMillis());
        resp.setHeader("Cache-Control", "max-age=86400");
        Files.copy(file, resp.getOutputStream());
    }
}
