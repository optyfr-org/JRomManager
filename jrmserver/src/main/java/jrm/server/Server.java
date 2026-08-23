package jrm.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.daemon.DaemonContext;
import org.eclipse.jetty.ee9.servlet.FilterHolder;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnectionLimit;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.DefaultEnvironmentProperties;
import jrm.misc.Log;
import jrm.server.handlers.SessionServlet;
import jrm.server.shared.WebSession;
import jrm.server.shared.handlers.ActionServlet;
import jrm.server.shared.handlers.DataSourceServlet;
import jrm.server.shared.handlers.DownloadServlet;
import jrm.server.shared.handlers.ImageServlet;
import jrm.server.shared.handlers.UploadServlet;

/**
 * Main HTTP server implementation for the JRomManager web application. Extends {@link AbstractServer} and provides the concrete
 * initialization logic for the Jetty-based HTTP server, including servlet registration, filter configuration, session management,
 * and connector setup.
 * <p>
 * This class serves as the primary entry point when running the server either as a standalone Java application (via
 * {@link #main(String[])}) or as an Apache Commons Daemon service (via the {@link org.apache.commons.daemon.Daemon Daemon}
 * interface methods). It supports both single-session and multi-session modes, configurable via command-line arguments or
 * environment properties.
 * </p>
 * <p>
 * The server binds to a configurable host and port, registers servlets for handling data sources, images, sessions, actions,
 * uploads, and downloads, and applies cache-control filters for JavaScript resources. A connection limit is enforced to prevent
 * resource exhaustion under high load.
 * </p>
 *
 * @since 1.0
 * 
 * @see AbstractServer
 * @see SessionListener
 * @see WebSession
 */
public class Server extends AbstractServer {

    /**
     * The default port number for HTTP connections.
     */
    private static final int HTTP_PORT_DEFAULT = 8080;

    /**
     * The port number on which the server will listen for incoming HTTP requests. This is set to a default value of
     * {@value #HTTP_PORT_DEFAULT}, which is a common choice for web servers. The value can be overridden via command-line arguments
     * or environment properties.
     */
    private static int httpPort = HTTP_PORT_DEFAULT;

    /**
     * Default bind address for the simple (unauthenticated) server: loopback only. The simple server has no login; binding all
     * interfaces would expose admin settings and actions on the network. Use {@code -b 0.0.0.0} or {@code JRM_SERVER_BIND} only when
     * the host is otherwise firewalled (e.g. Docker publishes a single port).
     */
    private static final String BIND_DEFAULT = "127.0.0.1";

    /**
     * The IP address or hostname to which the server socket will be bound. Defaults to {@value #BIND_DEFAULT} (loopback). Can be
     * overridden via command-line arguments or environment properties.
     */
    private static String bind = BIND_DEFAULT;

    /**
     * The maximum number of simultaneous connections allowed by the server. Defaults to {@code 50}. If the number of incoming
     * connections exceeds this limit, additional connection attempts may be rejected or queued until existing connections are
     * closed. This setting helps manage server resources and prevent overload under high traffic conditions.
     */
    private static int connLimit = 50;

    /**
     * Map of active web sessions keyed by HTTP session ID. Enables the server to maintain stateful context across requests and
     * track user activity.
     */
    static final Map<String, WebSession> sessions = new HashMap<>();

    /**
     * The environment properties instance used to retrieve configuration values for the server. Obtained via
     * {@link DefaultEnvironmentProperties#getInstance(Class)} using this class as the context.
     */
    private static final DefaultEnvironmentProperties env = DefaultEnvironmentProperties.getInstance(Server.class);

    /**
     * Sets the HTTP port for the server. Intended for test use to bind to an ephemeral port (port 0).
     *
     * @param port the HTTP port to set
     */
    static void setHttpPort(final int port) {
        httpPort = port;
    }

    /**
     * Retrieves the actual local port the HTTP connector is listening on after the server has been started. Returns -1 if the
     * server is not started or no connector is available.
     *
     * @return the local port, or -1 if not available
     */
    static int getLocalPort() {
        if (jettyServer != null && jettyServer.isStarted()) {
            for (final var connector : jettyServer.getConnectors()) {
                if (connector instanceof ServerConnector sc) {
                    return sc.getLocalPort();
                }
            }
        }
        return -1;
    }

    /**
     * Command-line arguments container for server configuration. Uses JCommander annotations to define parameter names, arity,
     * default values, and descriptions. Defaults can be overridden by environment properties via {@link #initFromEnv(Args)}.
     */
    @Parameters(separators = " =")
    public static class Args {
        /**
         * Filesystem path to the client-side web resources (HTML, CSS, JS). When {@code null}, the server falls back to embedded
         * classpath or module resources.
         */
        @Parameter(names = { "-c", "--client", "--clientPath" }, arity = 1, description = "Client path")
        private String clientPath = null;

        /**
         * Working directory path for server data, logs, and temporary files. Supports the {@code %HOMEPATH%} placeholder which is
         * replaced with the value of the {@code user.home} system property at runtime.
         */
        @Parameter(names = { "-w", "--work", "--workpath" }, arity = 1, description = "Working path")
        private String workPath = null;

        /**
         * Debug mode flag. When {@code true}, the server enables verbose logging and waits for an interactive console "stop"
         * command instead of blocking on the Jetty server join.
         */
        @Parameter(names = { "-d", "--debug" }, description = "Activate debug mode")
        private boolean debug = false;

        /**
         * HTTP port number for the server listener. Defaults to {@value Server#HTTP_PORT_DEFAULT}.
         */
        @Parameter(names = { "-p", "--http" }, arity = 1, description = "http port")
        private int httpPort = HTTP_PORT_DEFAULT;

        /**
         * Bind address or hostname for the server socket. Defaults to {@value Server#BIND_DEFAULT}.
         */
        @Parameter(names = { "-b", "--bind" }, arity = 1, description = "bind to address or host")
        private String bind = BIND_DEFAULT;
    }

    /**
     * Initializes the command-line arguments from environment properties.
     * <p>
     * This method checks for specific environment properties related to server configuration (client path, work path, debug mode,
     * HTTP port, and bind address) and updates the corresponding fields in the {@link Args} object if the properties are present.
     * It uses {@link Optional} to handle {@code null} values gracefully, preserving the existing default if no property override is
     * found.
     * </p>
     *
     * @param jArgs the {@link Args} object containing the command-line arguments to be initialized from environment properties
     */
    private static void initFromEnv(Args jArgs) {
        Optional.ofNullable(env.getProperty("jrm.server.clientpath", jArgs.clientPath)).ifPresent(v -> jArgs.clientPath = v);
        Optional.ofNullable(env.getProperty("jrm.server.workpath", jArgs.workPath)).ifPresent(v -> jArgs.workPath = v);
        Optional.ofNullable(env.getProperty("jrm.server.debug", jArgs.debug)).ifPresent(v -> jArgs.debug = v);
        Optional.ofNullable(env.getProperty("jrm.server.http", jArgs.httpPort)).ifPresent(v -> jArgs.httpPort = v);
        Optional.ofNullable(env.getProperty("jrm.server.bind", jArgs.bind)).ifPresent(v -> jArgs.bind = v);
    }

    /**
     * Parses command-line arguments and merges them with configuration values from environment properties to configure the server
     * settings.
     * <p>
     * This method processes input options using JCommander, initializes default system settings (such as file encoding and locale),
     * and configures the system logger. If command-line parameter parsing fails, it logs the error, prints command usage, and
     * terminates the application with exit code {@code 1}.
     * </p>
     *
     * @param args the command-line arguments passed to the application
     *
     * @throws NumberFormatException if a numeric argument cannot be parsed
     * @throws IOException if an error occurs during system logger initialization
     */
    public static void parseArgs(String... args) throws NumberFormatException, IOException {
        final var jArgs = new Args();
        final var cmd = JCommander.newBuilder().addObject(jArgs).build();
        try {
            initFromEnv(jArgs);

            cmd.parse(args);
            debug = jArgs.debug;
            clientPath = jArgs.clientPath;
            bind = jArgs.bind;
            httpPort = jArgs.httpPort;
            Optional.ofNullable(jArgs.workPath).map(s -> s.replace("%HOMEPATH%", System.getProperty("user.home"))).ifPresent(s -> System.setProperty("jrommanager.dir", s));
            Locale.setDefault(Locale.US);
            System.setProperty("file.encoding", "UTF-8");
            Log.init(getLogPath() + "/Server.%g.log", debug, 1024 * 1024, 5);
            if (!debug)
                Optional.ofNullable(env.getProperty("jrm.server.log.level")).ifPresent(v -> Log.setLevel(Level.parse(v)));
        } catch (ParameterException e) {
            Log.err(e.getMessage(), e);
            cmd.usage();
            System.exit(1);
        }
    }

    /**
     * The main entry point of the server application.
     * <p>
     * This method parses command-line arguments, initializes the HTTP server, starts the application, and blocks until a
     * termination or shutdown signal is received.
     * </p>
     *
     * @param args the command-line arguments passed to the application
     */
    public static void main(String[] args) {
        try {
            parseArgs(args);
            initialize();
            waitStop();
        } catch (InterruptedException e) {
            Log.err(e.getMessage(), e);
            System.exit(1);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Initializes the Jetty HTTP server, sets up the servlet context and handlers, configures the HTTP connector, and starts the
     * server.
     * <p>
     * This method performs the following steps:
     * </p>
     * <ol>
     * <li>Checks if the server is already initialized. If so, logs an error and returns without creating a duplicate instance.</li>
     * <li>Creates a new Jetty {@link org.eclipse.jetty.server.Server} instance.</li>
     * <li>Sets up the {@link ServletContextHandler} with session support, configures the base resource for static files, and sets
     * the context path to {@code "/"}.</li>
     * <li>Registers servlets for handling data sources ({@code /datasources/*}), images ({@code /images/*}), sessions
     * ({@code /session}), actions ({@code /actions/*}), uploads ({@code /upload/*}), and downloads ({@code /download/*}).</li>
     * <li>Adds a cache-control filter for JavaScript files: {@code no-store} for {@code *.nocache.js} files, and
     * {@code public, max-age=0, must-revalidate} for all other JS files except {@code *.cache.js}.</li>
     * <li>Registers the static content servlet as the default handler at {@code "/"}.</li>
     * <li>Configures the session handler with a maximum inactive interval of 300 seconds and attaches a
     * {@link SessionListener}.</li>
     * <li>Wraps the servlet context in a {@link GzipHandler} for HTTP response compression.</li>
     * <li>Configures the HTTP connector with the specified port and bind address, and adds it to the server.</li>
     * <li>Adds a {@link NetworkConnectionLimit} to restrict the number of simultaneous connections.</li>
     * <li>Starts the server and logs the configuration details.</li>
     * </ol>
     *
     * @throws InterruptedException if the thread is interrupted while waiting for the server to stop
     * @throws Exception if an error occurs during server initialization or startup
     */
    public static void initialize() throws Exception {
        if (jettyServer == null) {
            jettyServer = new org.eclipse.jetty.server.Server();

            final var context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            final var resourceFactory = ResourceFactory.of(context);
            context.setBaseResource(getClientPath(resourceFactory, clientPath));
            context.setContextPath("/");

            context.addServlet(new ServletHolder("datasources", DataSourceServlet.class), "/datasources/*");
            context.addServlet(new ServletHolder("images", ImageServlet.class), "/images/*");
            context.addServlet(new ServletHolder("session", SessionServlet.class), "/session");
            context.addServlet(new ServletHolder("actions", ActionServlet.class), "/actions/*");
            context.addServlet(new ServletHolder("upload", UploadServlet.class), "/upload/*");
            context.addServlet(new ServletHolder("download", DownloadServlet.class), "/download/*");

            context.addFilter(new FilterHolder((request, response, chain) -> {
                if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
                    if (httpRequest.getRequestURI().endsWith(".nocache.js"))
                        httpResponse.setHeader("cache-control", "no-store");
                    else if (!httpRequest.getRequestURI().endsWith(".cache.js"))
                        httpResponse.setHeader("cache-control", "public, max-age=0, must-revalidate");
                }
                chain.doFilter(request, response);
            }), "*.js", EnumSet.of(DispatcherType.REQUEST));

            // Simple server: never auto-admin at session create; elevate only for loopback peers.
            context.addFilter(new FilterHolder(new LocalAdminFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));

            context.addServlet(holderStatic(), "/");

            context.getSessionHandler().setMaxInactiveInterval(300);
            context.getSessionHandler().addEventListener(new SessionListener(false));

            final var gh = gzipHandler();
            gh.setHandler(context);
            jettyServer.setHandler(gh);
            jettyServer.setStopAtShutdown(true);

            // Create the HTTP connection
            final var httpConnectionFactory = new HttpConnectionFactory();
            final var httpConnector = new ServerConnector(jettyServer, httpConnectionFactory);
            httpConnector.setPort(httpPort);
            httpConnector.setHost(bind);
            httpConnector.setName("HTTP");
            jettyServer.addConnector(httpConnector);

            final var connectionLimit = new NetworkConnectionLimit(connLimit, jettyServer);
            jettyServer.addBean(connectionLimit); // limit simultaneous connections

            jettyServer.start();
            Log.config("Start server");
            for (final var connector : jettyServer.getConnectors())
                Log.config(((ServerConnector) connector).getName() + " with port on " + ((ServerConnector) connector).getPort() + " bind to "
                        + ((ServerConnector) connector).getHost());
            Log.config("clientPath: " + context.getBaseResource());
            Log.config("workPath: " + getWorkPath());
        } else
            Log.err("Already initialized");
    }

    /**
     * Initializes the server when running as an Apache Commons Daemon service. Delegates to {@link #parseArgs(String...)} and
     * {@link #initialize()} using the arguments provided by the daemon context.
     *
     * @param context the {@link DaemonContext} provided by the Apache Commons Daemon framework, containing command-line arguments
     *        and environment information
     *
     * @throws Exception if an error occurs during argument parsing or server initialization
     */
    @Override
    public void init(DaemonContext context) throws Exception {
        parseArgs(context.getArguments());
        initialize();
    }

    /**
     * Starts the server (no-op).
     * <p>
     * This method is a no-op for this server implementation, as the server is started during initialization in
     * {@link #init(DaemonContext)}. It is included to satisfy the requirements of the {@link org.apache.commons.daemon.Daemon
     * Daemon} interface.
     * </p>
     *
     * @throws Exception if an error occurs during the start process (not applicable in this implementation)
     */
    @Override
    public void start() throws Exception {
        // do nothing
    }

    /**
     * Stops the server (no-op).
     * <p>
     * This method is a no-op for this server implementation, as the server is stopped during termination in {@link #destroy()}. It
     * is included to satisfy the requirements of the {@link org.apache.commons.daemon.Daemon Daemon} interface.
     * </p>
     *
     * @throws Exception if an error occurs during the stop process (not applicable in this implementation)
     */
    @Override
    public void stop() throws Exception {
        // do nothing
    }

    /**
     * Destroys the server by delegating to {@link #terminate()}. Any exceptions that occur during the termination process are
     * caught and printed to the standard error stream.
     * <p>
     * This method is called by the Apache Commons Daemon framework when the server is being shut down, ensuring that all resources
     * are released properly.
     * </p>
     */
    @Override
    public void destroy() {
        try {
            terminate();
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Handles commands for running the server as a Windows service. Takes an array of command-line arguments where the first
     * argument is expected to be a command string (e.g., {@code "start"} or {@code "stop"}).
     * <p>
     * If the command is {@code "start"}, the method calls {@link #windowsStart()} to start the server in service mode. For any
     * other command, it calls {@link #windowsStop()} to stop the server. Remaining arguments (after the command) are passed to
     * {@link #parseArgs(String...)} for configuration.
     * </p>
     *
     * @param args an array of command-line arguments where the first element is the service command ({@code "start"} or
     *        {@code "stop"}) and subsequent elements are server configuration arguments
     *
     * @throws Exception if an error occurs while processing the commands or starting/stopping the service
     */
    static void windowsService(String[] args) throws Exception {
        Log.info(() -> "WINDOW SERVICE " + (args.length > 0 ? args[0] : ""));
        var cmd = "start";
        if (args.length > 0)
            cmd = args[0];

        try {
            parseArgs(Arrays.copyOfRange(args, 1, args.length));
            if ("start".equals(cmd))
                windowsStart();
            else
                windowsStop();
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Starts the server execution when running as a Windows service.
     * <p>
     * This method triggers the server initialization via {@link #initialize()} and enters a wait loop conditioned on
     * {@link #isStopped()}, using a {@code synchronized} wait on the {@link Server} class monitor to allow notification from
     * {@link #windowsStop()}.
     * </p>
     *
     * @throws Exception if an error occurs during server initialization or while waiting
     */
    static void windowsStart() throws Exception {
        Log.info("WIN START");
        initialize();
        while (isStopped()) {
            synchronized (Server.class) {
                Server.class.wait(60000); // wait 1 minute and check if stopped
            }
        }
    }

    /**
     * Stops the server execution when running as a Windows service.
     * <p>
     * This method terminates the running Jetty server instance via {@link #terminate()} and notifies all waiting threads on the
     * {@link Server} class monitor to break the polling loop in {@link #windowsStart()}.
     * </p>
     *
     * @throws Exception if an error occurs during server termination
     */
    static void windowsStop() throws Exception {
        Log.info("WIN STOP");
        terminate();
        synchronized (Server.class) {
            // stop the start loop
            Server.class.notifyAll();
        }
    }
}
