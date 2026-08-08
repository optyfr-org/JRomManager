package jrm.server.shared.handlers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.Log;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;
import lombok.val;

/**
 * Servlet handler for file upload operations in the JRM web server.
 * <p>
 * This servlet manages file uploads through HTTP POST and PUT requests to the "/upload/" endpoint. It performs validation checks
 * including path writability, destination directory existence, available disk space, and file size verification before accepting
 * uploads.
 * </p>
 * <p>
 * The upload process is split into two phases:
 * <ul>
 * <li>POST request: Validates the upload parameters and checks preconditions</li>
 * <li>PUT request: Performs the actual file transfer and writes to disk</li>
 * </ul>
 * <p>
 * <strong>Custom HTTP Headers Protocol:</strong><br>
 * Clients must send the following custom headers with upload requests:
 * <ul>
 * <li>{@code x-file-name}: URL-encoded filename of the file being uploaded</li>
 * <li>{@code x-file-parent}: URL-encoded parent directory path where the file should be saved</li>
 * <li>{@code x-file-size}: Expected file size in bytes (optional, used for validation)</li>
 * </ul>
 * All responses are returned as JSON objects containing {@code status} and {@code extstatus} fields.
 * 
 * @author JRM Project
 * 
 * @version 1.0
 * 
 * @since 1.0
 * 
 * @see WebSession
 * @see PathAbstractor
 */
@SuppressWarnings("serial")
public class UploadServlet extends HttpServlet {

    /**
     * Character encoding used for URL decoding of HTTP headers.
     * <p>
     * UTF-8 encoding ensures proper handling of international characters in filenames and file paths transmitted via custom HTTP
     * headers (x-file-name, x-file-parent).
     * </p>
     */
    private static final String UTF_8 = "UTF-8";

    /**
     * Error message for invalid or malicious paths.
     * <p>
     * Used when path validation fails due to traversal attempts, forgery, or malformed input.
     * </p>
     */
    private static final String INVALID_PATH = "Invalid path";

    /**
     * Data structure representing the result of an upload operation.
     * <p>
     * This package-private class encapsulates the status code and extended status message that are serialized to JSON via
     * {@link Gson} and returned to the client as an HTTP response. The status field indicates the outcome of the operation, while
     * extstatus provides additional details or error messages.
     * </p>
     */
    static class Result {
        /**
         * Status code indicating the outcome of the upload operation.
         * <p>
         * Status codes:
         * <ul>
         * <li>-1: Uninitialized/default state</li>
         * <li>0: Success, continue with upload</li>
         * <li>3: Upload completed successfully</li>
         * <li>6: Error - destination is not an existing directory</li>
         * <li>7: Error - insufficient disk space</li>
         * <li>8: Error - invalid path (InvalidPathException)</li>
         * <li>9: Error - invalid filename (IOException)</li>
         * <li>10: Error - initialization parameter missing or invalid</li>
         * <li>11: Error - target location is read-only</li>
         * <li>20: Error - I/O exception during upload</li>
         * <li>21: Error - file size mismatch</li>
         * </ul>
         */
        int status = -1;

        /**
         * Extended status message providing additional details about the operation result.
         * <p>
         * Contains human-readable messages describing success, continuation status, or detailed error information for
         * troubleshooting.
         * </p>
         */
        String extstatus = "";
    }

    /**
     * Handles HTTP POST requests for upload initialization and validation.
     * <p>
     * This method processes POST requests to the "/upload/" endpoint. It validates the upload parameters and performs pre-upload
     * checks including:
     * <ul>
     * <li>Verifies the "init" parameter is present and set to "1"</li>
     * <li>Validates the target directory is writable</li>
     * <li>Confirms the destination directory exists</li>
     * <li>Checks available disk space is sufficient for the upload</li>
     * <li>Validates the filename and path are syntactically correct</li>
     * </ul>
     * The method returns a JSON response with status code and message. If the request URI does not match "/upload/", the request is
     * delegated to the parent class implementation via {@link #superPost}.
     * 
     * @param req the HTTP servlet request containing upload parameters and headers
     * @param resp the HTTP servlet response for returning JSON status
     * 
     * @see #checkRequest
     */
    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
        if ("/upload/".equals(req.getRequestURI())) {
            try {
                val ws = (WebSession) req.getSession().getAttribute("session");
                val pathAbstractor = new PathAbstractor(ws);
                val result = new Result();
                val init = req.getParameter("init");
                if (init != null && init.equals("1")) {
                    checkRequest(req, pathAbstractor, result);
                } else {
                    result.status = 10;
                    result.extstatus = "init error";
                }
                resp.setContentType("text/json");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(new Gson().toJson(result));
            } catch (final IOException e) {
                internalError(resp, e);
            }
        } else
            superPost(req, resp);
    }

    /**
     * Delegates to the parent class doPost method with exception handling.
     * <p>
     * This helper method is called when the request URI does not match the "/upload/" endpoint. It wraps the superclass doPost call
     * to handle any ServletException or IOException that may occur. Errors are logged using the {@link Log} utility for debugging
     * and monitoring purposes.
     * 
     * @param req the HTTP servlet request
     * @param resp the HTTP servlet response
     */
    private void superPost(final HttpServletRequest req, final HttpServletResponse resp) {
        try {
            super.doPost(req, resp);
        } catch (ServletException | IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Sends an HTTP 500 Internal Server Error response and logs the exception.
     * <p>
     * This method handles I/O errors that occur during response generation. If sending the error response itself fails, the
     * secondary exception is logged but not propagated to avoid masking the original error.
     * 
     * @param resp the HTTP servlet response
     * @param e the I/O exception that triggered the error response
     */
    private void internalError(final HttpServletResponse resp, final IOException e) {
        try {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final IOException e1) {
            Log.err(e1.getMessage(), e1);
        }
    }

    /**
     * Validates upload request parameters and checks preconditions before file transfer.
     * <p>
     * This method performs comprehensive validation of the upload request:
     * <ul>
     * <li>Extracts and decodes filename and parent directory from HTTP headers</li>
     * <li>Verifies the target directory is writable according to the user's session</li>
     * <li>Blocks uploads to the reports cache directory to prevent cache poisoning attacks</li>
     * <li>Confirms the destination directory exists on the filesystem</li>
     * <li>Checks that sufficient disk space is available for the file</li>
     * <li>Validates the file path is syntactically correct</li>
     * </ul>
     * Results are stored in the provided Result object with appropriate status codes.
     * 
     * @param req the HTTP servlet request containing headers x-file-name, x-file-parent, and x-file-size
     * @param pathAbstractor the path abstractor for resolving and validating file paths
     * @param result the result object to populate with status code and message
     * 
     * @throws SecurityException if path validation fails due to security restrictions
     */
    void checkRequest(final HttpServletRequest req, final PathAbstractor pathAbstractor, final Result result) {
        try {
            result.status = 0;
            result.extstatus = "continue...";
            final String filename = sanitizeHeader(req.getHeader("x-file-name"));
            final String fileparent = sanitizeHeader(req.getHeader("x-file-parent"));
            validateUploadHeaders(filename, fileparent);
            
            // Prevent uploads to the reports cache directory to mitigate cache poisoning attacks
            if (fileparent.startsWith("%work/reports") || fileparent.contains("/reports/") || fileparent.endsWith("/reports")) {
                result.status = 11;
                result.extstatus = "Uploads to reports cache directory are not permitted";
                return;
            }
            
            if (pathAbstractor.isWriteable(fileparent)) {
                final var filesize = getXFileSize(req);
                final var dest = pathAbstractor.getAbsolutePath(fileparent);
                if (!(Files.exists(dest) && Files.isDirectory(dest))) {
                    result.status = 6;
                    result.extstatus = "Error: destination must be an existing directory";
                } else {
                    final var fs = Files.getFileStore(dest);
                    final var free = fs.getUsableSpace();
                    if (free < filesize) {
                        result.status = 7;
                        result.extstatus = "Error: not enough free space";
                    } else {
                        final var filepath = dest.resolve(filename);
                        Files.getLastModifiedTime(filepath);
                    }
                }
            } else {
                result.status = 11;
                result.extstatus = "Is read only";
            }
        } catch (final NoSuchFileException _) { // File does not exist yet, which is acceptable for new uploads
        } catch (InvalidPathException | SecurityException _) { // Invalid path syntax or traversal attempt detected
            result.status = 8;
            result.extstatus = INVALID_PATH;
        } catch (final IOException e) { // I/O error occurred while accessing file metadata
            result.status = 9;
            result.extstatus = e.getMessage();
        }
    }

    /**
     * Extracts the expected file size from the HTTP request headers.
     * <p>
     * Parses the "x-file-size" header to obtain the expected file size in bytes. This value is used for disk space validation and
     * post-upload size verification. If the header is missing or contains invalid data, returns -1 to indicate that size validation
     * should be skipped.
     * 
     * @param req the HTTP servlet request containing the x-file-size header
     * 
     * @return the expected file size in bytes, or -1 if the header is invalid or missing
     */
    long getXFileSize(final HttpServletRequest req) {
        try {
            return Long.parseLong(req.getHeader("x-file-size"));
        } catch (final NumberFormatException _) {
            return -1;
        }
    }

    /**
     * Sanitizes and decodes an HTTP header value to prevent path traversal attacks.
     * <p>
     * This method URL-decodes the header value and removes potentially dangerous characters:
     * <ul>
     * <li>Path traversal sequences (..)</li>
     * <li>Null bytes</li>
     * <li>Control characters</li>
     * </ul>
     * 
     * @param header the raw HTTP header value to sanitize
     * 
     * @return the sanitized and decoded header value
     * 
     * @throws IOException if URL decoding fails
     */
    private void validateUploadHeaders(final String filename, final String fileparent) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new InvalidPathException(String.valueOf(filename), "Invalid filename");
        }
        if (fileparent == null || fileparent.isBlank() || !fileparent.startsWith("%") || fileparent.contains("\0")) {
            throw new InvalidPathException(String.valueOf(fileparent), "Invalid file parent");
        }
    }

    String sanitizeHeader(final String header) throws IOException {
        final String decoded = URLDecoder.decode(header, UTF_8);
        // Remove path traversal attempts and dangerous characters
        return decoded.replace("..", "")
                      .replace("\0", "")
                      .replaceAll("[\\p{Cntrl}]", "");
    }

    /**
     * Validates that a user-provided filename is a single path component.
     *
     * @param filename the candidate filename from request headers
     * @throws SecurityException if the filename is invalid
     */
    void validateFilenameComponent(final String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new SecurityException("Invalid filename");
        }
    }

    /**
     * Handles HTTP PUT requests for actual file upload and disk writing.
     * <p>
     * This method processes PUT requests to the "/upload/" endpoint to perform the actual file transfer. It:
     * <ul>
     * <li>Extracts and decodes filename and parent directory from HTTP headers</li>
     * <li>Validates the target directory is writable</li>
     * <li>Blocks uploads to the reports cache directory to prevent cache poisoning attacks</li>
     * <li>Creates any necessary parent directories for the target file</li>
     * <li>Streams the request body to the target file on disk</li>
     * <li>Verifies the uploaded file size matches the expected size</li>
     * <li>Deletes the file if upload fails or size verification fails</li>
     * </ul>
     * Returns a JSON response with the final upload status. If the request URI does not match "/upload/", the request is delegated
     * to the parent class implementation via {@link #superPost}.
     * 
     * @param req the HTTP servlet request containing the file data and metadata headers
     * @param resp the HTTP servlet response for returning JSON status
     * 
     * @see #doUpload
     */
    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) {
        if ("/upload/".equals(req.getRequestURI())) {
            try {
                val ws = (WebSession) req.getSession().getAttribute("session");
                val pathAbstractor = new PathAbstractor(ws);
                final var result = new Result();
                final String filename = sanitizeHeader(req.getHeader("x-file-name"));
                final String fileparent = sanitizeHeader(req.getHeader("x-file-parent"));
                validateFilenameComponent(filename);
                
                // Prevent uploads to the reports cache directory to mitigate cache poisoning attacks
                if (fileparent.startsWith("%work/reports") || fileparent.contains("/reports/") || fileparent.endsWith("/reports")) {
                    result.status = 11;
                    result.extstatus = "Uploads to reports cache directory are not permitted";
                    resp.getWriter().write(new Gson().toJson(result));
                    return;
                }
                
                if (pathAbstractor.isWriteable(fileparent)) {
                    final var dest = pathAbstractor.getAbsolutePath(fileparent).normalize().toAbsolutePath();
                    final var filepath = dest.resolve(filename).normalize().toAbsolutePath();
                    if (!filepath.startsWith(dest)) {
                        throw new SecurityException("Invalid upload path");
                    }
                    Files.createDirectories(filepath.getParent());
                    doUpload(req, result, filename, filepath);
                    if (result.status != 3) {
                        Log.debug(() -> result.status + " : " + result.extstatus);
                        Files.delete(filepath);
                    }
                } else {
                    result.status = 11;
                    result.extstatus = "Is read only";
                }
                resp.getWriter().write(new Gson().toJson(result));
            } catch (final SecurityException _) { // Path traversal or forgery attempt detected by PathAbstractor
                getErrResult(resp);
            } catch (final IOException e) {
                internalError(resp, e);
            }
        } else
            superPost(req, resp);
    }

    private void getErrResult(final HttpServletResponse resp) {
        final var errResult = new Result();
        errResult.status = 8;
        errResult.extstatus = INVALID_PATH;
        try {
            resp.getWriter().write(new Gson().toJson(errResult));
        } catch (final IOException ioe) {
            internalError(resp, ioe);
        }
    }

    /**
     * Performs the actual file upload by streaming request data to disk.
     * <p>
     * This method handles the core file transfer operation:
     * <ul>
     * <li>Opens the target file for writing with CREATE and TRUNCATE options</li>
     * <li>Streams the HTTP request input stream to the file using buffered I/O</li>
     * <li>Tracks the number of bytes written for size verification</li>
     * <li>Sets success status (3) if upload completes without errors</li>
     * <li>Verifies written size matches expected size from x-file-size header</li>
     * <li>Sets error status (21) if size mismatch is detected</li>
     * </ul>
     * The method uses try-with-resources to ensure proper stream closure and handles I/O exceptions gracefully with appropriate
     * error reporting.
     * 
     * @param req the HTTP servlet request containing the file data stream
     * @param result the result object to populate with upload status
     * @param filename the name of the file being uploaded (for status messages)
     * @param filepath the absolute path where the file should be written
     */
    void doUpload(final HttpServletRequest req, final Result result, final String filename, final Path filepath) {
        final long filesize = getXFileSize(req);
        long size = 0;
        try (final var out = new BufferedOutputStream(Files.newOutputStream(filepath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            size = IOUtils.copy(req.getInputStream(), out);
            result.status = 3;
            result.extstatus = filename + " done";
        } catch (final IOException e) {
            result.status = 20;
            result.extstatus = filename + " : " + e.getMessage();
        } finally {
            if (filesize >= 0 && size != filesize) {
                result.status = 21;
                result.extstatus = "Error: " + filename + " size should be " + filesize + " bytes long but got " + size + " bytes";
            }
        }
    }
}
