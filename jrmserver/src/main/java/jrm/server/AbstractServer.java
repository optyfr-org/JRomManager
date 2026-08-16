package jrm.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Scanner;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jetty.ee9.servlet.DefaultServlet;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.Resources;

import jrm.fullserver.FullServer;
import jrm.misc.Log;
import jrm.server.shared.WebSession;

/**
 * Abstract base class for all server implementations in the JRomManager web server framework. This class implements the Apache
 * Commons {@link org.apache.commons.daemon.Daemon Daemon} interface and provides shared infrastructure used by both
 * {@link jrm.fullserver.FullServer FullServer} and the administrative server variant.
 * <p>
 * Common functionality includes:
 * </p>
 * <ul>
 * <li>Static resource resolution from the filesystem, classpath, or Java module (JRT) runtime image.</li>
 * <li>Jetty {@link Server} lifecycle management (start, stop, join).</li>
 * <li>GZIP compression handler factory for HTTP response optimization.</li>
 * <li>Graceful shutdown via a JVM shutdown hook and an interactive console stop command (in debug/Windows modes).</li>
 * <li>Working directory and log path resolution.</li>
 * </ul>
 * <p>
 * Subclasses are expected to implement the concrete server initialization logic (servlet registration, connector configuration,
 * etc.) and invoke the shared helpers provided here.
 * </p>
 *
 * @since 1.0
 * 
 * @see jrm.fullserver.FullServer
 */
public abstract class AbstractServer implements Daemon {

    /** Servlet init-parameter name controlling precompressed resource serving. */
    private static final String PRECOMPRESSED = "precompressed";

    /** Servlet init-parameter name enabling HTTP byte-range request support. */
    private static final String ACCEPT_RANGES = "acceptRanges";

    /** Servlet init-parameter name controlling directory listing permission. */
    private static final String DIR_ALLOWED = "dirAllowed";

    /** Reusable string constant {@code "true"} for servlet init-parameters. */
    private static final String TRUE = "true";

    /** Reusable string constant {@code "false"} for servlet init-parameters. */
    private static final String FALSE = "false";

    /**
     * Optional filesystem path to the client-side web resources (HTML, CSS, JavaScript). When {@code null}, the server falls back
     * to embedded classpath or module resources.
     */
    protected static String clientPath;

    /**
     * Shared Jetty {@link Server} instance used by all server variants to manage the HTTP server lifecycle. A {@code null} value
     * indicates that the server has not been initialized or has been terminated.
     */
    protected static Server jettyServer = null;

    /**
     * Debug-mode flag. When {@code true}, the server waits for an interactive console "stop" command instead of blocking on
     * {@link Server#join() join}, facilitating easier shutdown during development and testing.
     */
    protected static boolean debug;

    /**
     * Protected no-argument constructor. Prevents direct instantiation of this abstract class; subclasses must provide their own
     * constructors.
     */
    protected AbstractServer() { /* No implementation needed for abstract class */
    }

    /**
     * Creates and configures a {@link ServletHolder} for serving static content via Jetty's {@link DefaultServlet}. The holder is
     * pre-configured to:
     * <ul>
     * <li>Disable directory listing ({@code dirAllowed=false}).</li>
     * <li>Enable HTTP byte-range requests ({@code acceptRanges=true}).</li>
     * <li>Serve precompressed variants when available ({@code precompressed=true}).</li>
     * </ul>
     *
     * @return a fully configured {@link ServletHolder} ready for registration in a servlet context
     */
    protected static ServletHolder holderStatic() {
        final var holderStatic = new ServletHolder("static", DefaultServlet.class);
        holderStatic.setInitParameter(DIR_ALLOWED, FALSE);
        holderStatic.setInitParameter(ACCEPT_RANGES, TRUE);
        holderStatic.setInitParameter(PRECOMPRESSED, TRUE);
        return holderStatic;
    }

    /**
     * Creates a {@link GzipHandler} configured for HTTP response compression. The handler is set up to compress responses for
     * {@code GET} and {@code POST} methods and includes common web content MIME types (HTML, plain text, XML, CSS, JavaScript, and
     * JSON). Both the inflate buffer size and the minimum response size threshold for compression are set to 2048 bytes.
     *
     * @return a configured {@link GzipHandler} ready to wrap a servlet context handler
     */
    @SuppressWarnings("removal")
    protected static GzipHandler gzipHandler() {
        final var gzipHandler = new GzipHandler();
        gzipHandler.setIncludedMethods("POST", "GET");
        gzipHandler.setIncludedMimeTypes("text/html", "text/plain", "text/xml", "text/css", "application/javascript", "text/javascript", "application/json");
        gzipHandler.setInflateBufferSize(2048);
        gzipHandler.setMinGzipSize(2048);
        return gzipHandler;
    }

    /**
     * Resolves the working directory path for the server. The path is determined by checking the {@code jrommanager.dir} system
     * property first; if not set, it falls back to the {@code user.dir} system property (the JVM's current working directory).
     *
     * @return a {@link Path} representing the server's working directory
     */
    protected static Path getWorkPath() {
        String base = System.getProperty("jrommanager.dir");
        if (base == null)
            base = System.getProperty("user.dir");
        return Paths.get(base);
    }

    /**
     * Resolves and creates (if necessary) the logs directory under the server's working path. The directory is created recursively
     * using {@link Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[]) Files.createDirectories} if it does not
     * already exist.
     *
     * @return the absolute path to the logs directory as a {@link String}
     *
     * @throws IOException if the logs directory cannot be created
     */
    protected static String getLogPath() throws IOException {
        final var path = getWorkPath().resolve("logs");
        Files.createDirectories(path);
        return path.toString();
    }

    /**
     * Registers a JVM shutdown hook for graceful server termination and then blocks the calling thread until a stop signal is
     * received.
     * <p>
     * The shutdown hook invokes {@link #terminate()} to close all active {@link WebSession WebSession} instances and stop the Jetty
     * server.
     * </p>
     * <p>
     * The blocking behavior depends on the runtime environment:
     * </p>
     * <ul>
     * <li><b>Debug mode or Windows:</b> Reads from {@code System.in} in a loop, waiting for the user to type "stop"
     * (case-insensitive), then calls {@link System#exit(int)}.</li>
     * <li><b>Production (non-Windows):</b> Calls {@link Server#join() jettyServer.join()} to block until the server thread
     * completes.</li>
     * </ul>
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws JettyException if an unexpected error occurs during the wait loop (wraps the original exception)
     */
    protected static void waitStop() throws InterruptedException, JettyException {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (isStarted()) {
                    try {
                        terminate();
                        Log.info("Server stopped.");
                    } catch (Exception _) {
                        // ignore
                    }
                }
            }));
            if (debug || SystemUtils.IS_OS_WINDOWS) {
                try (final var sc = new Scanner(System.in)) {
                    // wait until receive stop command from keyboard
                    System.out.println("Enter 'stop' to halt: "); // NOSONAR
                    while (!sc.nextLine().equalsIgnoreCase("stop"))
                        Thread.sleep(1000);
                    System.exit(0);
                }
            } else if (isStarted())
                jettyServer.join();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new JettyException(e.getMessage(), e);
        }
    }

    /**
     * Terminates the server by closing all active web sessions and stopping the Jetty {@link Server} instance. This method is
     * {@code synchronized} to prevent concurrent termination attempts.
     * <p>
     * The method performs the following steps:
     * </p>
     * <ol>
     * <li>Calls {@link WebSession#closeAll()} to close every active session and set the global termination flag.</li>
     * <li>If the Jetty server is not {@code null} and is currently started, invokes {@link Server#stop()} to shut it down
     * gracefully.</li>
     * <li>Sets the {@link #jettyServer} reference to {@code null} to indicate that the server has been fully terminated.</li>
     * </ol>
     *
     * @throws Exception if an error occurs while stopping the Jetty server
     */
    public static synchronized void terminate() throws Exception {
        WebSession.closeAll();
        if (jettyServer != null) {
            if (jettyServer.isStarted())
                jettyServer.stop();
            jettyServer = null;
        }
    }

    /**
     * Checks whether the Jetty server has been initialized and is currently running. A server is considered started when the
     * {@link #jettyServer} reference is not {@code null} and {@link Server#isStarted() jettyServer.isStarted()} returns
     * {@code true}.
     *
     * @return {@code true} if the server has been initialized and is in a started state; {@code false} otherwise
     */
    public static final synchronized boolean isStarted() {
        return jettyServer != null && jettyServer.isStarted();

    }

    /**
     * Checks whether the Jetty server is currently stopped. A server is considered stopped when the {@link #jettyServer} reference
     * is {@code null}, indicating that it has either never been initialized or has been fully terminated via {@link #terminate()}.
     *
     * @return {@code true} if the server is stopped (i.e., {@code jettyServer} is {@code null}); {@code false} otherwise
     */
    public static final synchronized boolean isStopped() {
        return jettyServer == null;

    }

    /**
     * Custom exception class for wrapping Jetty-related errors that occur during server lifecycle operations (startup, shutdown,
     * join). Extends {@link Exception} and provides standard message and cause constructors.
     */
    protected static class JettyException extends Exception {
        /**
         * Constructs a new {@code JettyException} with no detail message or cause.
         */
        public JettyException() {
            super();
        }

        /**
         * Constructs a new {@code JettyException} with the specified detail message.
         *
         * @param message the detail message describing the error
         */
        public JettyException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@code JettyException} with the specified detail message and underlying cause.
         *
         * @param message the detail message describing the error
         * @param cause the underlying cause of this exception
         */
        public JettyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Resolves the client web-resource path by searching multiple locations in priority order:
     * <ol>
     * <li>The explicit {@code path} argument (filesystem or classpath).</li>
     * <li>The Java module runtime image at {@code jrt:/jrm.merged.module/webclient/}.</li>
     * <li>The classpath resource {@code /webclient/} relative to {@link FullServer}.</li>
     * </ol>
     * <p>
     * Each candidate is validated via {@link Resources#exists(Resource)} before being returned. If none of the candidates resolve
     * to an existing resource, a {@link FileNotFoundException} is thrown.
     * </p>
     *
     * @param resourceFactory the {@link ResourceFactory} used to create {@link Resource} instances from paths, URIs, or classpath
     *        entries
     * @param path an optional explicit path to the client resources; may be {@code null} to rely on embedded defaults
     *
     * @return a {@link Resource} pointing to the resolved client web-resource directory
     *
     * @throws IOException if an I/O error occurs while resolving the resource
     * @throws URISyntaxException if the resource URI cannot be parsed
     * @throws FileNotFoundException if no valid client resource path can be found in any of the searched locations
     */
    protected static Resource getClientPath(ResourceFactory resourceFactory, String path) throws IOException, URISyntaxException {
        Resource resource;

        if (path != null) {
            resource = resourceFactory.newResource(path);
            if (Resources.exists(resource))
                return resource;
            resource = resourceFactory.newClassLoaderResource(path, true);
            if (Resources.exists(resource))
                return resource;
        }

        resource = resourceFactory.newResource("jrt:/jrm.merged.module/webclient/");
        if (Resources.exists(resource))
            return resource;
        URL url = FullServer.class.getResource("/webclient/");
        if (url != null) {
            resource = resourceFactory.newResource(URIUtil.correctURI(url.toURI()));
            if (Resources.exists(resource))
                return resource;
        }
        throw new FileNotFoundException("Unable to find webclient path");
    }

    /**
     * Resolves the TLS/SSL certificate file path by searching multiple locations in priority order:
     * <ol>
     * <li>The explicit {@code path} argument (filesystem or classpath).</li>
     * <li>The Java module runtime image at {@code jrt:/jrm.merged.module/certs/localhost.pfx}.</li>
     * <li>The classpath resource {@code /certs/localhost.pfx} relative to {@link FullServer}.</li>
     * </ol>
     * <p>
     * Each candidate is validated via {@link Resources#exists(Resource)} before being returned. If none of the candidates resolve
     * to an existing resource, a {@link FileNotFoundException} is thrown.
     * </p>
     *
     * @param path an optional explicit path to the certificate file; may be {@code null} to rely on embedded defaults
     *
     * @return a {@link Resource} pointing to the resolved certificate file
     *
     * @throws URISyntaxException if the resource URI cannot be parsed
     * @throws IOException if an I/O error occurs while resolving the resource
     * @throws FileNotFoundException if no valid certificate path can be found in any of the searched locations
     */
    protected static Resource getCertsPath(String path) throws URISyntaxException, IOException {
        Resource resource;
        final var resourceFactory = ResourceFactory.root();
        if (path != null) {
            resource = resourceFactory.newResource(path);
            if (Resources.exists(resource))
                return resource;
            resource = resourceFactory.newClassLoaderResource(path, true);
            if (Resources.exists(resource))
                return resource;
        }
        resource = resourceFactory.newResource("jrt:/jrm.merged.module/certs/localhost.pfx");
        if (Resources.exists(resource))
            return resource;
        URL url = FullServer.class.getResource("/certs/localhost.pfx");
        if (url != null) {
            resource = resourceFactory.newResource(URIUtil.correctURI(url.toURI()));
            if (Resources.exists(resource))
                return resource;
        }
        throw new FileNotFoundException("Unable to find webclient path");
    }

    /**
     * Converts a string path representation into a {@link Path} object, handling multiple URI schemes including {@code jrt:},
     * {@code file:}, and {@code jar:}.
     * <p>
     * If the path string begins with one of the recognized URI scheme prefixes, it is parsed as a {@link URI} and converted to a
     * {@link Path} via {@link Path#of(URI)}. If the underlying filesystem does not yet exist (e.g., a JAR or JRT filesystem), a new
     * {@link java.nio.file.FileSystem FileSystem} is created on-the-fly using {@link FileSystems#newFileSystem(URI, java.util.Map)
     * FileSystems.newFileSystem}.
     * </p>
     * <p>
     * Plain filesystem paths (without a URI scheme prefix) are resolved via {@link Paths#get(String, String...) Paths.get}.
     * </p>
     *
     * @param path the string representation of the path to convert; may be a URI string (e.g., {@code "jrt:/..."},
     *        {@code "jar:file:/..."}) or a plain filesystem path
     *
     * @return a {@link Path} corresponding to the given string, or {@code null} if the path cannot be resolved (e.g., the
     *         filesystem cannot be created)
     */
    protected static Path getPath(String path) {
        try {
            return path.startsWith("jrt:") || path.startsWith("file:") || path.startsWith("jar:") ? Path.of(URI.create(path)) : Paths.get(path);
        } catch (FileSystemNotFoundException _) {
            final var uri = URI.create(path);
            try {
                FileSystems.newFileSystem(uri, Collections.emptyMap());
                return Path.of(uri);
            } catch (IOException _) {
                return null;
            }
        }
    }

}
