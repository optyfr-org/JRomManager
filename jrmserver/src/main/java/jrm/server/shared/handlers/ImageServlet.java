package jrm.server.shared.handlers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.Log;
import jrm.misc.URIUtils;

/**
 * Servlet responsible for serving static image and resource icon files to the web client.
 * <p>
 * This servlet handles GET requests to the {@code /resicons/} endpoint and serves resource files (icons, images, etc.) from either
 * a Java module path ({@code jrt:/}) or a classpath resource location, depending on the deployment mode.
 * </p>
 * <p>
 * Features include:
 * <ul>
 * <li>Automatic detection of module vs. classpath resource location</li>
 * <li>HTTP caching support via {@code Last-Modified} and {@code Cache-Control} headers</li>
 * <li>Conditional GET support via {@code If-Modified-Since} header (304 Not Modified)</li>
 * <li>Path traversal protection to prevent unauthorized file access</li>
 * </ul>
 * <p>
 * Resources are resolved relative to the {@code /jrm/resicons/} base path within the application's module or classpath.
 * </p>
 * 
 * @see URIUtils
 * @see Log
 * 
 * @author JRM Project
 * 
 * @version 1.0
 * 
 * @since 1.0
 */
public class ImageServlet extends HttpServlet {
    private static final Pattern SAFE_RESOURCE_PATH = Pattern.compile("^[a-zA-Z0-9/_\\-.]+$");
    /** Cached base URI for resource resolution (either {@code jrt:/} module path or classpath resource). */
    private static final AtomicReference<URI> uri = new AtomicReference<>();

    /**
     * Returns the base URI for resource icon files, detecting the deployment mode on first call.
     * <p>
     * This method attempts to resolve resources from the Java module path ({@code jrt:/jrm.merged.module/jrm/resicons/}) first. If
     * the module path is not available (non-modular deployment), it falls back to the classpath resource location
     * ({@code /jrm/resicons/}).
     * </p>
     * <p>
     * The result is cached in {@link #uri} for subsequent calls. Concurrent first-time callers may each resolve a candidate; only
     * one value is published via compare-and-set.
     * </p>
     * 
     * @return the base URI pointing to the resource icons directory
     * 
     * @throws URISyntaxException if the URI cannot be constructed
     */
    private static URI getURI() throws URISyntaxException {
        final URI cached = uri.get();
        if (cached != null)
            return cached;
        URI candidate = URI.create("jrt:/jrm.merged.module/jrm/resicons/");
        if (!URIUtils.URIExists(candidate))
            candidate = ImageServlet.class.getResource("/jrm/resicons/").toURI();
        uri.compareAndSet(null, candidate);
        return uri.get();
    }

    /**
     * Checks whether the requested resource has been modified since the client's last request.
     * <p>
     * Compares the {@code If-Modified-Since} request header against the resource's {@code Last-Modified} timestamp (truncated to
     * second precision for HTTP compatibility).
     * </p>
     * 
     * @param req the HTTP servlet request containing the {@code If-Modified-Since} header
     * @param urlconn the URL connection to the resource, providing the last-modified timestamp
     * 
     * @return {@code true} if the resource has been modified or the header is absent/invalid; {@code false} if the resource has not
     *         been modified (304 response should be sent)
     */
    private boolean ifModifiedSince(HttpServletRequest req, URLConnection urlconn) {
        String ifModifiedSince = req.getHeader("if-modified-since");
        try {
            if (ifModifiedSince != null && dateParse(ifModifiedSince).toEpochSecond() == urlconn.getLastModified() / 1000) {
                return false;
            }
        } catch (DateTimeParseException _) {
            // ignore
        }
        return true;
    }

    /**
     * Resolves and validates the requested resource URI against path traversal attacks.
     * <p>
     * This method extracts the resource path from the request URI (stripping the first 8 characters corresponding to the servlet
     * prefix), validates it against disallowed patterns (such as {@code ..}, {@code \\}, {@code :}, null bytes, and double
     * slashes), and resolves it against the base resource URI.
     * </p>
     * <p>
     * After normalization, the resolved URI is verified to still fall within the base URI prefix to prevent path traversal escapes.
     * </p>
     * 
     * @param requestUri the raw request URI from the HTTP request
     * 
     * @return the resolved and validated URI pointing to the requested resource
     * 
     * @throws URISyntaxException if the request URI is null, too short, contains disallowed patterns, or resolves outside the base
     *         URI
     */
    static URI resolveRequestedResourceUri(String requestUri) throws URISyntaxException {
        if (requestUri == null || requestUri.length() <= 8) {
            throw new URISyntaxException(String.valueOf(requestUri), "Invalid request URI");
        }

        final String encodedResourcePath = requestUri.substring(8);
        String resourcePath = URLDecoder.decode(encodedResourcePath, StandardCharsets.UTF_8);
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        if (resourcePath.isEmpty()
                || resourcePath.contains("..")
                || resourcePath.contains("\\")
                || resourcePath.contains(":")
                || resourcePath.contains("\0")
                || resourcePath.startsWith("//")
                || !SAFE_RESOURCE_PATH.matcher(resourcePath).matches()) {
            Log.debug("Disallowed resource path : " + resourcePath);
            throw new URISyntaxException(resourcePath, "Disallowed resource path");
        }

        final URI baseUri = getURI();
        final URI resolved = (baseUri.isOpaque() ? URI.create(baseUri + resourcePath) : baseUri.resolve(resourcePath)).normalize();
        if (!resolved.toString().startsWith(baseUri.toString())) {
            Log.debug(() -> "Resolved path '%s' escapes base URI '%s' for request '%s'".formatted(resolved, baseUri, requestUri));
            throw new URISyntaxException(resourcePath, "Resolved path escapes base URI");
        }
        return resolved;
    }

    /**
     * Handles GET requests to serve resource icon files.
     * <p>
     * This method:
     * <ol>
     * <li>Resolves and validates the requested resource URI</li>
     * <li>Opens a connection to the resource</li>
     * <li>Returns {@code 404 Not Found} if the resource is empty</li>
     * <li>Returns {@code 304 Not Modified} if the client's cached version is current</li>
     * <li>Streams the resource content with appropriate HTTP headers (content type, content length, last-modified,
     * cache-control)</li>
     * </ol>
     * <p>
     * HTTP status codes returned:
     * <ul>
     * <li>{@code 200 OK} - resource successfully streamed</li>
     * <li>{@code 304 Not Modified} - client's cached version is current</li>
     * <li>{@code 404 Not Found} - if the resource is empty, URI is invalid, or I/O error occurs</li>
     * </ul>
     * 
     * @param req the HTTP servlet request specifying the resource to retrieve
     * @param resp the HTTP servlet response for writing the resource content
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            final var geturi = resolveRequestedResourceUri(req.getRequestURI());
            final var url = geturi.toURL();
            final var urlconn = url.openConnection();
            urlconn.setDoInput(true);
            if (urlconn.getContentLength() == 0) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Empty result");
                return;
            }
            if (!ifModifiedSince(req, urlconn)) {
                resp.sendError(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentLengthLong(urlconn.getContentLengthLong());
            resp.setContentType(urlconn.getContentType());
            resp.setDateHeader("Last-Modified", urlconn.getLastModified());
            resp.setHeader("Cache-Control", "max-age=86400");
            IOUtils.copy(urlconn.getInputStream(), resp.getOutputStream());
        } catch (URISyntaxException | IOException e) {
            try {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            } catch (IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
        }
    }

    /**
     * Parses an HTTP date string in RFC 1123 format into a {@link ZonedDateTime} object.
     * <p>
     * The expected format follows RFC 1123 (e.g., {@code "Wed, 21 Oct 2015 07:28:00 GMT"}), parsed using the
     * {@link DateTimeFormatter#RFC_1123_DATE_TIME} formatter, which uses the US locale for day and month names.
     * </p>
     * 
     * @param str the HTTP date string to parse
     * 
     * @return the parsed {@link ZonedDateTime} object
     * 
     * @throws DateTimeParseException if the string cannot be parsed as a valid HTTP date
     */
    private static ZonedDateTime dateParse(final String str) {
        return ZonedDateTime.parse(str, DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
