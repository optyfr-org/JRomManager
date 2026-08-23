package jrm.fullserver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.daemon.DaemonContext;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.ee9.nested.ServletConstraint;
import org.eclipse.jetty.ee9.security.ConstraintMapping;
import org.eclipse.jetty.ee9.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee9.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.ee9.servlet.FilterHolder;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.AcceptRateLimit;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.NetworkConnectionLimit;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.fullserver.handlers.FullDataSourceServlet;
import jrm.fullserver.handlers.SessionServlet;
import jrm.fullserver.security.Login;
import jrm.fullserver.security.SSLReload;
import jrm.misc.DefaultEnvironmentProperties;
import jrm.misc.Log;
import jrm.misc.URIUtils;
import jrm.server.AbstractServer;
import jrm.server.SessionListener;
import jrm.server.shared.handlers.ActionServlet;
import jrm.server.shared.handlers.DownloadServlet;
import jrm.server.shared.handlers.ImageServlet;
import jrm.server.shared.handlers.UploadServlet;

/**
 * FullServer is the main class for the jrm full server application. It extends AbstractServer and provides functionality to
 * initialize and start a Jetty server with support for HTTP, HTTPS, and HTTP/2 protocols. The server is configured using
 * command-line arguments and environment properties, allowing for flexible setup of various parameters such as ports, bind address,
 * connection limits, and SSL certificate paths.
 * <p>
 * The class includes methods for parsing command-line arguments, setting up SSL context for HTTPS connections, configuring security
 * handlers for authentication, and managing server connectors for different protocols. It also includes a main method that serves
 * as the entry point for the application, allowing it to be run as a standalone server or as a Windows service.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 */
public class FullServer extends AbstractServer {

    /**
     * The default directory path pointing to the embedded key store certificates within the merged module resources.
     * <p>
     * This variable defines the default location for the key store certificates used for SSL/TLS connections in the server. It
     * points to a specific path within the merged module resources, which is expected to contain the necessary certificate files
     * for secure communication. The key store files are typically in PKCS12 format and should include the certificates required for
     * HTTPS connections. This default path can be overridden by command-line arguments or environment properties to allow for
     * flexible configuration of the server's SSL/TLS settings.
     */
    private static final String KEY_STORE = "jrt:/jrm.merged.module/certs/";

    /**
     * The default path to the key store file, which is expected to be a PKCS12 file named "localhost.pfx" located in the KEY_STORE
     * directory.
     * <p>
     * This variable defines the default location for the key store file used for SSL/TLS connections in the server. The key store
     * file is expected to be in PKCS12 format and should contain the necessary certificates for secure communication. The default
     * file name is "localhost.pfx", and it is located in the directory specified by the KEY_STORE variable. This default path can
     * be overridden by command-line arguments or environment properties to allow for flexible configuration of the server's SSL/TLS
     * settings.
     */
    private static final String KEY_STORE_PATH_DEFAULT = KEY_STORE + "localhost.pfx";

    /**
     * The default path to the key store password file, which is expected to be a text file named "localhost.pw" located in the
     * KEY_STORE directory. This file should contain the password for the key store.
     * <p>
     * Note: The key store password file is expected to be a simple text file containing the password for the key store. It should
     * be named "localhost.pw" and located in the same directory as the key store file specified by KEY_STORE_PATH_DEFAULT. This
     * default path can be overridden by command-line arguments or environment properties to allow for flexible configuration of the
     * server's SSL/TLS settings.
     * 
     * @see #KEY_STORE_PATH_DEFAULT
     */
    private static final String KEY_STORE_PW_PATH_DEFAULT = KEY_STORE + "localhost.pw";

    /**
     * The default bind address (IP address) for the server connectors, representing all network interfaces.
     * <p>
     * This variable defines the default IP address that the server will bind to when starting. The default value is "0.0.0.0",
     * which means that the server will bind to all available network interfaces, allowing it to accept connections from any IP
     * address. This default bind address can be overridden by command-line arguments or environment properties to allow for
     * flexible configuration of the server's network settings, enabling it to bind to a specific IP address if desired.
     */
    private static final String BIND_DEFAULT = "0.0.0.0";

    /**
     * The default port number for HTTP connections.
     * <p>
     * This variable defines the default port number that the server will listen on for cleartext HTTP connections. The default
     * value is set to 8080, which is a common port for HTTP traffic. This default port can be overridden by command-line arguments
     * or environment properties to allow for flexible configuration of the server's HTTP connection settings, enabling it to listen
     * on a different port if needed.
     */
    private static final int HTTP_PORT_DEFAULT = 8080;

    /**
     * The default port number for HTTPS connections.
     * <p>
     * This variable defines the default port number that the server will listen on for secure HTTPS connections. The default value
     * is set to 8443, which is a common port for HTTPS traffic. This default port can be overridden by command-line arguments or
     * environment properties to allow for flexible configuration of the server's secure connection settings, enabling it to listen
     * on a different port if needed.
     * 
     * @see #HTTP_PORT_DEFAULT
     */
    private static final int HTTPS_PORT_DEFAULT = 8443;

    /**
     * The default protocol configuration, represented as a bitmask where bit 1 = HTTP, bit 2 = HTTPS, and bit 3 = HTTP/2 (with bit
     * 2).
     * <p>
     * This variable defines the default protocol configuration for the server, using a bitmask to specify which protocols are
     * supported. The bitmask allows for flexible configuration of supported protocols, enabling the server to be set up to support
     * HTTP, HTTPS, and HTTP/2 as needed. The default value is set to 0xff, which means that all three protocols are supported by
     * default. This default configuration can be overridden by command-line arguments or environment properties to allow for
     * customized protocol support based on the specific requirements of the deployment environment.
     * 
     * @see #HTTP_PORT_DEFAULT
     * @see #HTTPS_PORT_DEFAULT
     */
    private static final int PROTOCOLS_DEFAULT = 0xff;

    /**
     * The default maximum number of simultaneous connections allowed by the server.
     * <p>
     * This variable defines the default connection limit for the server, which limits the total number of concurrent connections
     * that the server can handle at any given time. The default value is set to 50, which helps to prevent resource exhaustion and
     * manage server load by controlling the number of active connections. This default connection limit can be overridden by
     * command-line arguments or environment properties to allow for flexible configuration of the server's connection handling
     * capabilities based on the expected traffic and resource availability.
     */
    private static final int CONNLIMIT_DEFAULT = 50;

    /**
     * The default maximum connection rate per second, calculated as one-tenth of the default connection limit.
     * <p>
     * This variable defines the default rate limit for new connections to the server, which limits the number of new connections
     * that can be accepted per second. The default value is calculated as one-tenth of the default connection limit, which helps to
     * prevent denial-of-service attacks and manage server load by controlling the rate at which new connections are established.
     * This default rate limit can be overridden by command-line arguments or environment properties to allow for flexible
     * configuration of the server's connection handling capabilities based on the expected traffic and resource availability.
     * 
     * @see #CONNLIMIT_DEFAULT
     */
    private static final int RATELIMIT_DEFAULT = CONNLIMIT_DEFAULT / 10;

    /**
     * The default maximum number of server threads, calculated as four times the default connection limit.
     * <p>
     * This variable defines the default maximum number of threads that the server can use to handle incoming requests. The default
     * value is calculated as four times the default connection limit, which helps to manage server resources and ensure that the
     * server can handle a sufficient number of concurrent requests without overwhelming the system. This default maximum thread
     * count can be overridden by command-line arguments or environment properties to allow for flexible configuration of the
     * server's threading capabilities based on the expected traffic and resource availability.
     * 
     * @see #CONNLIMIT_DEFAULT
     */
    private static final int MAXTHREADS_DEFAULT = CONNLIMIT_DEFAULT * 4;

    /**
     * The default minimum number of server threads, calculated as one-fourth of the default connection limit.
     * <p>
     * This variable defines the default minimum number of threads that the server will maintain in its thread pool. The default
     * value is calculated as one-fourth of the default connection limit, which helps to ensure that the server has a baseline
     * number of threads available to handle incoming requests, even during periods of low traffic. This default minimum thread
     * count can be overridden by command-line arguments or environment properties to allow for flexible configuration of the
     * server's threading capabilities based on the expected traffic and resource availability.
     * 
     * @see #CONNLIMIT_DEFAULT
     */
    private static final int MINTHREADS_DEFAULT = CONNLIMIT_DEFAULT / 4;

    /**
     * The default session timeout in seconds, set to 300 seconds (5 minutes).
     * <p>
     * This variable defines the default session inactivity timeout for user sessions on the server. The default value is set to 300
     * seconds (5 minutes), which specifies the amount of time that a session can remain inactive before it is invalidated and
     * removed from the server. This helps to manage server resources and improve security by ensuring that inactive sessions do not
     * persist indefinitely. This default session timeout can be overridden by command-line arguments or environment properties to
     * allow for flexible configuration of the server's session management settings based on the expected user behavior and security
     * requirements of the deployment environment.
     */
    private static final int SESSIONTIMEOUT_DEFAULT = 300;

    /**
     * The resource path to the key store certificate file.
     * <p>
     * This variable holds the resource path to the key store certificate file used for SSL/TLS connections. It is used to configure
     * the SSL context factory with the correct certificate file to establish secure connections. The path can be set through
     * command-line arguments or environment properties, and it defaults to a specific location within the merged module resources
     * if not provided. The key store file is expected to be in PKCS12 format and should contain the necessary certificates for
     * SSL/TLS communication.
     */
    private static Resource keyStorePath;

    /**
     * The path to the key store password file.
     * <p>
     * This variable holds the path to the file that contains the password for the key store used for SSL/TLS connections. It is
     * used to configure the SSL context factory with the correct password to access the key store and establish secure connections.
     * The path can be set through command-line arguments or environment properties, and it defaults to a specific location within
     * the merged module resources if not provided.
     */
    private static String keyStorePWPath;

    /**
     * The protocol configuration bitmask (bit 1 = HTTP, bit 2 = HTTPS, bit 3 = HTTP/2 with bit 2).
     * <p>
     * This variable is used to determine which protocols the server should support based on the configuration provided through
     * command-line arguments or environment properties. The bitmask allows for flexible configuration of supported protocols,
     * enabling the server to be set up to support HTTP, HTTPS, and HTTP/2 as needed. The default value is set to 0xff, which means
     * that all three protocols are supported by default.
     */
    private static int protocols; // bit 1 = HTTP, bit 2 = HTTPS, bit 3 = HTTP2 (with bit 2)

    /**
     * The port number for HTTP connections.
     * <p>
     * This parameter specifies the port number that the server will listen on for cleartext HTTP connections. It is used to
     * configure the HTTP connector for the server, allowing clients to establish non-secure connections. The default value is set
     * to 8080, which is a common port for HTTP traffic. This parameter can be overridden by command-line arguments or environment
     * properties to allow for flexible configuration of the server's HTTP connection settings.
     */
    private static int httpPort;

    /**
     * The port number for HTTPS connections.
     * <p>
     * This parameter specifies the port number that the server will listen on for secure HTTPS connections. It is used to configure
     * the HTTPS connector for the server, allowing clients to establish secure connections using SSL/TLS. The default value is set
     * to 8443, which is a common port for HTTPS traffic. This parameter can be overridden by command-line arguments or environment
     * properties to allow for flexible configuration of the server's secure connection settings.
     */
    private static int httpsPort;

    /**
     * The IP address or host to bind the server to.
     * <p>
     * This parameter specifies the network interface or host that the server will bind to when starting. It can be set to a
     * specific IP address to bind to a particular network interface, or it can be set to "0.0.0.0" to bind to all available
     * interfaces. The default value is set to "0.0.0.0", which allows the server to accept connections on any network interface.
     */
    private static String bind;

    /**
     * The maximum number of simultaneous connections allowed by the server.
     * <p>
     * This parameter is used to configure the ConnectionLimit for the server, which limits the total number of concurrent
     * connections that the server can handle at any given time. It helps to prevent resource exhaustion and manage server load by
     * controlling the number of active connections. The default value is set to 50.
     */
    private static int connLimit = CONNLIMIT_DEFAULT;
    /**
     * The maximum connection rate per second allowed by the server.
     * <p>
     * This parameter is used to configure the AcceptRateLimit for the server, which limits the number of new connections that can
     * be accepted per second. It helps to prevent denial-of-service attacks and manage server load by controlling the rate at which
     * new connections are established. The default value is set to one-tenth of the maximum connection limit.
     */
    private static int rateLimit = RATELIMIT_DEFAULT;
    /**
     * The maximum number of threads in the server's thread pool.
     * <p>
     * This parameter is used to configure the maximum number of threads that the server can use to handle incoming requests. It
     * helps to manage server resources and ensure that the server can handle a sufficient number of concurrent requests without
     * overwhelming the system. The default value is set to four times the maximum connection limit.
     */
    private static int maxThreads = MAXTHREADS_DEFAULT;
    /**
     * The minimum number of threads in the server's thread pool.
     * <p>
     * This parameter is used to configure the minimum number of threads that the server will maintain in its thread pool. It helps
     * to ensure that the server has a baseline number of threads available to handle incoming requests, even during periods of low
     * traffic. The default value is set to one-fourth of the maximum connection limit.
     */
    private static int minThreads = MINTHREADS_DEFAULT;

    /**
     * The session inactivity timeout in seconds.
     * <p>
     * This parameter is used to configure the session timeout for user sessions on the server. It specifies the amount of time (in
     * seconds) that a session can remain inactive before it is invalidated and removed from the server. This helps to manage server
     * resources and improve security by ensuring that inactive sessions do not persist indefinitely. The default value is set to
     * 300 seconds (5 minutes).
     */
    private static int sessionTimeOut = SESSIONTIMEOUT_DEFAULT;

    /**
     * The environment properties instance used to retrieve configuration values for the server.
     * <p>
     * This instance is initialized using the DefaultEnvironmentProperties class, which provides a way to access environment
     * properties and configuration values for the server application. It allows the server to retrieve configuration settings from
     * various sources, such as system properties, environment variables, or configuration files, and use them to initialize the
     * server parameters and settings.
     */
    private static final DefaultEnvironmentProperties env = DefaultEnvironmentProperties.getInstance(FullServer.class);

    /**
     * A nested static class that defines the command-line arguments for the server application. It uses JCommander annotations to
     * specify the argument names, descriptions, and default values. The Args class includes fields for client path, working path,
     * debug mode, certificate file path, HTTPS port, HTTP port, bind address, connection limit, rate limit, maximum threads,
     * minimum threads, and session timeout. This class is used to parse and store the command-line arguments provided when starting
     * the server.
     */
    @Parameters(separators = " =")
    private static class Args {
        /**
         * The client path for the server, which can be specified using the -c, --client, or --clientPath command-line arguments.
         */
        @Parameter(names = { "-c", "--client", "--clientPath" }, arity = 1, description = "Client path")
        private String clientPath = null;

        /**
         * The working path for the server, which can be specified using the -w, --work, or --workpath command-line arguments.
         */
        @Parameter(names = { "-w", "--work", "--workpath" }, arity = 1, description = "Working path")
        private String workPath = null;

        /**
         * A flag to activate debug mode, which can be specified using the -d or --debug command-line arguments.
         */
        @Parameter(names = { "-d", "--debug" }, description = "Activate debug mode")
        private boolean debug = false;

        /**
         * The path to the certificate file for HTTPS connections, which can be specified using the -C or --cert command-line
         * arguments. The default value is defined by KEY_STORE_PATH_DEFAULT.
         */
        @Parameter(names = { "-C", "--cert" }, arity = 1, description = "cert file, default is " + KEY_STORE_PATH_DEFAULT)
        private String cert = KEY_STORE_PATH_DEFAULT;

        /**
         * The port number for HTTPS connections, which can be specified using the -s or --https command-line arguments. The default
         * value is defined by HTTPS_PORT_DEFAULT.
         */
        @Parameter(names = { "-s", "--https" }, arity = 1, description = "https port, default is " + HTTPS_PORT_DEFAULT)
        private int httpsPort = HTTPS_PORT_DEFAULT;

        /**
         * The port number for HTTP connections, which can be specified using the -p or --http command-line arguments. The default
         * value is defined by HTTP_PORT_DEFAULT.
         */
        @Parameter(names = { "-p", "--http" }, arity = 1, description = "http port, default is " + HTTP_PORT_DEFAULT)
        private int httpPort = HTTP_PORT_DEFAULT;

        /**
         * The IP address or host to bind the server to, which can be specified using the -b or --bind command-line arguments. The
         * default value is defined by BIND_DEFAULT.
         */
        @Parameter(names = { "-b", "--bind" }, arity = 1, description = "bind to address or host, default is " + BIND_DEFAULT)
        private String bind = BIND_DEFAULT;

        /**
         * The maximum number of simultaneous connections allowed by the server, which can be specified using the --conn-limit
         * command-line argument. The default value is defined by CONNLIMIT_DEFAULT.
         */
        @Parameter(names = { "--conn-limit" }, arity = 1, description = "max simultaneous connection, default is " + CONNLIMIT_DEFAULT)
        private int connlimit = CONNLIMIT_DEFAULT;

        /**
         * The maximum connection rate per second allowed by the server, which can be specified using the --rate-limit command-line
         * argument. The default value is defined by RATELIMIT_DEFAULT.
         */
        @Parameter(names = { "--rate-limit" }, arity = 1, description = "max connection rate per second, default is " + RATELIMIT_DEFAULT)
        private int ratelimit = RATELIMIT_DEFAULT;

        /**
         * The maximum number of threads in the server's thread pool, which can be specified using the --max-threads command-line
         * argument. The default value is defined by MAXTHREADS_DEFAULT.
         */
        @Parameter(names = { "--max-threads" }, arity = 1, description = "max server threads, default is " + MAXTHREADS_DEFAULT)
        private int maxThreads = MAXTHREADS_DEFAULT;

        /**
         * The minimum number of threads in the server's thread pool, which can be specified using the --min-threads command-line
         * argument. The default value is defined by MINTHREADS_DEFAULT.
         */
        @Parameter(names = { "--min-threads" }, arity = 1, description = "min server threads, default is " + MINTHREADS_DEFAULT)
        private int minThreads = MINTHREADS_DEFAULT;

        /**
         * The session inactivity timeout in seconds, which can be specified using the --session-timeout command-line argument. The
         * default value is defined by SESSIONTIMEOUT_DEFAULT.
         */
        @Parameter(names = { "--session-timeout" }, arity = 1, description = "session timeout, default is " + SESSIONTIMEOUT_DEFAULT)
        private int sessionTimeOut = SESSIONTIMEOUT_DEFAULT;

    }

    /**
     * Initializes the server configuration by parsing command-line arguments and environment properties. It uses JCommander to
     * parse the command-line arguments into an instance of the Args class, and then sets the server configuration parameters based
     * on the parsed arguments and environment properties. The method also initializes logging and sets system properties for file
     * encoding and default locale.
     * 
     * @param args the command-line arguments passed to the server application
     * 
     * @throws IOException if an error occurs while reading configuration files or initializing logging
     * @throws URISyntaxException if an error occurs while parsing URI paths for certificates or client resources
     */
    private static void initFromEnv(Args jArgs) {
        Optional.ofNullable(env.getProperty("jrm.server.clientpath", jArgs.clientPath)).ifPresent(v -> jArgs.clientPath = v);
        Optional.ofNullable(env.getProperty("jrm.server.workpath", jArgs.workPath)).ifPresent(v -> jArgs.workPath = v);
        Optional.ofNullable(env.getProperty("jrm.server.debug", jArgs.debug)).ifPresent(v -> jArgs.debug = v);
        Optional.ofNullable(env.getProperty("jrm.server.cert", jArgs.cert)).ifPresent(v -> jArgs.cert = v);
        Optional.ofNullable(env.getProperty("jrm.server.https", jArgs.httpsPort)).ifPresent(v -> jArgs.httpsPort = v);
        Optional.ofNullable(env.getProperty("jrm.server.http", jArgs.httpPort)).ifPresent(v -> jArgs.httpPort = v);
        Optional.ofNullable(env.getProperty("jrm.server.bind", jArgs.bind)).ifPresent(v -> jArgs.bind = v);
        Optional.ofNullable(env.getProperty("jrm.server.connlimit", jArgs.connlimit)).ifPresent(v -> jArgs.connlimit = v);
        Optional.ofNullable(env.getProperty("jrm.server.ratelimit", jArgs.ratelimit)).ifPresent(v -> jArgs.ratelimit = v);
        Optional.ofNullable(env.getProperty("jrm.server.minthreads", jArgs.minThreads)).ifPresent(v -> jArgs.minThreads = v);
        Optional.ofNullable(env.getProperty("jrm.server.maxthreads", jArgs.maxThreads)).ifPresent(v -> jArgs.maxThreads = v);
        Optional.ofNullable(env.getProperty("jrm.server.sessiontimeout", jArgs.sessionTimeOut)).ifPresent(v -> jArgs.sessionTimeOut = v);
    }

    /**
     * Parses the command-line arguments and merges them with the configuration values from the environment properties to initialize
     * the server settings.
     * <p>
     * This method sets various server parameters such as ports, bind address, SSL certificates, connection limits, and configures
     * the default locale, file encoding, and system logger.
     *
     * @param args the command-line arguments passed to the server application
     * 
     * @throws IOException if an error occurs while verifying certificate files, reading configuration, or initializing logging
     * @throws URISyntaxException if a certificate URI contains syntax errors
     */
    public static void parseArgs(String... args) throws IOException, URISyntaxException {
        final var jArgs = new Args();
        final var cmd = JCommander.newBuilder().addObject(jArgs).build();
        try {
            initFromEnv(jArgs);

            cmd.parse(args);
            debug = jArgs.debug;
            clientPath = jArgs.clientPath;
            bind = jArgs.bind;
            httpPort = jArgs.httpPort;
            httpsPort = jArgs.httpsPort;
            keyStorePath = Optional.ofNullable(getCertsPath(jArgs.cert)).filter(p -> p.exists()).orElse(getCertsPath(null));
            if (Files.exists(getPath(keyStorePath + ".pw")))
                keyStorePWPath = keyStorePath + ".pw";
            else if (keyStorePath != null && keyStorePath.getPath() != null && KEY_STORE_PATH_DEFAULT.equals(keyStorePath.getPath().toString())
                    && Files.exists(getPath(KEY_STORE_PW_PATH_DEFAULT)))
                keyStorePWPath = KEY_STORE_PW_PATH_DEFAULT;
            else
                keyStorePWPath = null;
            Optional.ofNullable(jArgs.workPath).map(s -> s.replace("%HOMEPATH%", System.getProperty("user.home"))).ifPresent(s -> System.setProperty("jrommanager.dir", s));
            protocols = PROTOCOLS_DEFAULT;
            connLimit = jArgs.connlimit;
            rateLimit = jArgs.ratelimit;
            minThreads = jArgs.minThreads;
            maxThreads = jArgs.maxThreads;
            sessionTimeOut = jArgs.sessionTimeOut;

            Locale.setDefault(Locale.US);
            System.setProperty("file.encoding", "UTF-8");
            Log.init(getLogPath() + "/Server.%g.log", false, 1024 * 1024, 5);
        } catch (ParameterException e) {
            Log.err(e.getMessage(), e);
            cmd.usage();
            System.exit(1);
        }
    }

    /**
     * Creates and configures a server connector for secure HTTPS (HTTP/1.1) connections.
     * <p>
     * This method sets up the SSL context factory, initiates daily certificate reloading, and configures the connection factories,
     * port, and host for the connector.
     *
     * @param jettyserver the Jetty server instance to configure the connector for
     * 
     * @return the configured HTTPS {@link ServerConnector}
     * 
     * @throws IOException if an error occurs while preparing the SSL context
     */
    private static ServerConnector httpsConnector(final Server jettyserver) throws IOException {
        // SSL Context Factory for HTTPS and HTTP/2
        final var sslContextFactory = sslContext();

        // Reload certificate every day at midnight (used for certificate renewal)
        SSLReload.getInstance(sslContextFactory).start();

        // Create the HTTPS end point
        final var httpsConfig = httpsConfig();

        // SSL Connection Factory
        final SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());

        // HTTPS Connector
        final var httpsConnector = new ServerConnector(jettyserver, ssl, new HttpConnectionFactory(httpsConfig));
        httpsConnector.setPort(httpsPort);
        httpsConnector.setHost(bind);
        httpsConnector.setName("HTTPS");
        return httpsConnector;
    }

    /**
     * Creates and configures a server connector for secure HTTP/2 connections.
     * <p>
     * This method sets up the SSL context factory and configures ALPN plus HTTP/2 connection factories. ALPN is negotiated
     * by the JDK TLS stack via {@code jetty-alpn-java-server}; HTTP/1.1 remains the fallback when a client does not offer ALPN.
     *
     * @param jettyserver the Jetty server instance to configure the connector for
     * 
     * @return the configured HTTP/2 {@link ServerConnector}
     * 
     * @throws IOException if an error occurs while preparing the SSL context
     */
    private static ServerConnector http2Connector(final Server jettyserver) throws IOException {
        // SSL Context Factory for HTTPS and HTTP/2
        final var sslContextFactory = sslContext();

        // Reload certificate every day at midnight (used for certificate renewal)
        SSLReload.getInstance(sslContextFactory).start();

        // Create the HTTPS end point
        final var httpsConfig = httpsConfig();

        // HTTP/2 Connection Factory
        final var h2 = new HTTP2ServerConnectionFactory(httpsConfig);

        final var alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(HttpVersion.HTTP_1_1.asString());

        // SSL Connection Factory
        final var ssl = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());

        // HTTP/2 Connector
        final var http2Connector = new ServerConnector(jettyserver, ssl, alpn, h2, new HttpConnectionFactory(httpsConfig));
        http2Connector.setPort(httpsPort);
        http2Connector.setHost(bind);
        http2Connector.setName("HTTP2");
        return http2Connector;
    }

    /**
     * Creates and configures the standard {@link HttpConfiguration} for secure connections.
     * <p>
     * This configuration specifies the "https" scheme, sets the configured HTTPS port, and adds a {@link SecureRequestCustomizer}
     * to the HTTP configuration.
     *
     * @return the configured {@link HttpConfiguration} for secure connections
     */
    private static HttpConfiguration httpsConfig() {
        final var httpsConfig = new HttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(httpsPort);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());
        return httpsConfig;
    }

    /**
     * Creates and configures the SSL context factory for secure connections.
     * <p>
     * This method sets up the key store type, path, cipher comparator, and password for the SSL context factory based on the
     * configured key store and password paths.
     *
     * @return the configured {@link SslContextFactory.Server} for secure connections
     * 
     * @throws IOException if an error occurs while reading the key store or password
     */
    private static org.eclipse.jetty.util.ssl.SslContextFactory.Server sslContext() throws IOException {
        var sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStoreType("PKCS12");
        sslContextFactory.setKeyStorePath(keyStorePath.getURI().toString());
        sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
        sslContextFactory.setUseCipherSuitesOrder(true);

        String keyStorePassword = (keyStorePWPath != null && Files.exists(getPath(keyStorePWPath))) ? URIUtils.readString(keyStorePWPath).trim() : "";
        sslContextFactory.setKeyStorePassword(keyStorePassword);
        sslContextFactory.setKeyManagerPassword(keyStorePassword);
        return sslContextFactory;
    }

    /**
     * Creates and configures a server connector for standard HTTP (HTTP/1.1) connections.
     * <p>
     * This connector handles cleartext HTTP requests on the configured port and bind address.
     *
     * @param jettyserver the Jetty server instance to configure the connector for
     * @param config the standard {@link HttpConfiguration} to apply
     * 
     * @return the configured HTTP {@link ServerConnector}
     */
    private static ServerConnector httpConnector(final Server jettyserver, final HttpConfiguration config) {
        final var httpConnectionFactory = new HttpConnectionFactory(config);
        final var httpConnector = new ServerConnector(jettyserver, httpConnectionFactory);
        httpConnector.setPort(httpPort);
        httpConnector.setHost(bind);
        httpConnector.setName("HTTP");
        return httpConnector;
    }

    /**
     * Configures the security handler and access constraints for the given context.
     * <p>
     * This method sets up basic authentication using a custom {@link Login} service, restricting access to users with the "admin"
     * or "user" role.
     *
     * @param context the {@link ServletContextHandler} to secure
     * 
     * @throws IOException if an I/O error occurs during security setup
     * @throws SQLException if a database error occurs while loading credentials
     */
    private static void setSecurity(final ServletContextHandler context) throws IOException, SQLException {
        // Authentification server by login & password
        final var security = new ConstraintSecurityHandler();
        security.setAuthenticator(new BasicAuthenticator());
        security.setLoginService(new Login());

        final var constraint = new ServletConstraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[] { "admin", "user" });
        final var constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");
        security.setConstraintMappings(Collections.singletonList(constraintMapping));
        context.setSecurityHandler(security);
    }

    /**
     * The entry point of the standalone server application.
     * <p>
     * This method parses the command-line arguments, initializes the Jetty server, starts the connectors, and waits for a shutdown
     * signal to terminate.
     *
     * @param args the command-line arguments passed to the server application
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
     * Configures and starts the Jetty server with all required servlets, filters, security parameters, and connectors.
     * <p>
     * This method sets up standard servlets (datasources, images, session, actions, upload, download), applies security
     * constraints, configures cache-control filters, binds active HTTP/HTTPS/HTTP2 connectors depending on user preferences, and
     * enforces rate limits and connection limits on the server before starting.
     *
     * @throws Exception if an error occurs while configuring or starting the server
     */
    public static void initialize() throws Exception {
        if (jettyServer != null) {
            Log.err("Already initialized");
            return;
        }
        jettyServer = new Server(createThreadPool());

        final var context = createContext();

        final var gh = gzipHandler();
        gh.setHandler(context);
        jettyServer.setHandler(gh);
        jettyServer.setStopAtShutdown(true);

        final var config = new HttpConfiguration();
        config.addCustomizer(new SecureRequestCustomizer());
        config.addCustomizer(new ForwardedRequestCustomizer());

        addConnectors(jettyServer, config);

        final var connectionLimit = new NetworkConnectionLimit(connLimit, jettyServer);
        jettyServer.addBean(connectionLimit);
        jettyServer.addBean(new AcceptRateLimit(rateLimit > 0 ? rateLimit : (connLimit / 10), 1, TimeUnit.SECONDS, jettyServer));

        jettyServer.start();
        Log.config("Start server");
        for (final var connector : jettyServer.getConnectors())
            Log.config(((ServerConnector) connector).getName() + " with port on " + ((ServerConnector) connector).getPort() + " binded to "
                    + ((ServerConnector) connector).getHost());
        Log.config("clientPath: " + context.getBaseResource());
        Log.config("workPath: " + getWorkPath());
    }

    /**
     * Creates the thread pool with safe defaults for max and min thread counts.
     * <p>
     * The pool uses platform threads for accepting connections and dispatching, but offloads request handling to virtual threads
     * via {@link QueuedThreadPool#setVirtualThreadsExecutor(java.util.concurrent.Executor)}. Virtual threads are ideal for the
     * I/O-bound servlet work (file uploads/downloads, profile scans, database queries) handled by this server. The
     * {@code synchronized} blocks that could pin virtual threads have been audited and migrated to {@link java.util.concurrent.locks.ReentrantLock}
     * where they occur in the request path (see {@link jrm.fullserver.security.Login#login}).
     *
     * @return a configured {@link QueuedThreadPool}
     */
    private static QueuedThreadPool createThreadPool() {
        final int max = maxThreads > 0 ? maxThreads : (connLimit * 4);
        final int min = minThreads > 0 ? minThreads : (connLimit / 4);
        final var pool = new QueuedThreadPool(max, min);
        pool.setVirtualThreadsExecutor(
            java.util.concurrent.Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("jetty-vt-", 0).factory()));
        return pool;
    }

    /**
     * Creates and configures the servlet context with all servlets, filters, security, and session settings.
     *
     * @return the configured {@link ServletContextHandler}
     * @throws URISyntaxException if an error occurs while resolving the client path URI
     * @throws IOException if an I/O error occurs while setting up the context
     * @throws SQLException if a database error occurs while initializing the context
     * @throws Exception if an error occurs during context setup
     */
    private static ServletContextHandler createContext() throws IOException, URISyntaxException, SQLException  {
        final var context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        final var resourceFactory = ResourceFactory.of(context);
        context.setBaseResource(getClientPath(resourceFactory, clientPath));
        context.setContextPath("/");

        context.addServlet(new ServletHolder("datasources", FullDataSourceServlet.class), "/datasources/*");
        context.addServlet(new ServletHolder("images", ImageServlet.class), "/images/*");
        context.addServlet(new ServletHolder("session", SessionServlet.class), "/session");
        context.addServlet(new ServletHolder("actions", ActionServlet.class), "/actions/*");
        context.addServlet(new ServletHolder("upload", UploadServlet.class), "/upload/*");
        context.addServlet(new ServletHolder("download", DownloadServlet.class), "/download/*");

        context.addFilter(new FilterHolder(FullServer::cacheControlFilter), "*.js", EnumSet.of(DispatcherType.REQUEST));

        context.addServlet(holderStatic(), "/");

        setSecurity(context);

        context.getSessionHandler().setMaxInactiveInterval(sessionTimeOut);
        context.getSessionHandler().addEventListener(new SessionListener(true));

        return context;
    }

    /**
     * Cache-control filter callback for JavaScript resources.
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @param chain    the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    private static void cacheControlFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httprequest && response instanceof HttpServletResponse httpresponse) {
            final var uri = httprequest.getRequestURI();
            if (uri.endsWith(".nocache.js"))
                httpresponse.setHeader("cache-control", "no-store");
            else if (!uri.endsWith(".cache.js"))
                httpresponse.setHeader("cache-control", "public, max-age=0, must-revalidate");
        }
        chain.doFilter(request, response);
    }

    /**
     * Adds HTTP, HTTPS, and/or HTTP/2 connectors based on the protocol bitmask configuration.
     *
     * @param server the Jetty server
     * @param config the HTTP configuration for the HTTP connector
     * @throws IOException if an error occurs while creating SSL contexts
     */
    private static void addConnectors(Server server, HttpConfiguration config) throws IOException {
        if ((protocols & 0x1) == 0x1) {
            server.addConnector(httpConnector(server, config));
        }
        if ((protocols & 0x2) == 0x2 && keyStorePath.exists()) {
            addSecureConnector(server);
        }
    }

    /**
     * Adds either an HTTP/2 or HTTPS connector depending on the protocol bitmask.
     *
     * @param server the Jetty server
     * @throws IOException if an error occurs while creating the SSL context
     */
    private static void addSecureConnector(Server server) throws IOException {
        if ((protocols & 0x4) == 0x4)
            server.addConnector(http2Connector(server));
        else
            server.addConnector(httpsConnector(server));
    }

    /**
     * Initializes the server when running as a daemon or background service.
     * <p>
     * This method parses the command-line arguments provided in the daemon context and triggers the server initialization process.
     *
     * @param context the daemon context containing the application's command-line arguments
     * 
     * @throws Exception if an error occurs during argument parsing or server initialization
     */
    @Override
    public void init(DaemonContext context) throws Exception {
        parseArgs(context.getArguments());
        initialize();
    }

    /**
     * Starts the daemon service.
     * <p>
     * This is a lifecycle method called when the daemon is started. As the Jetty server is already started and managed during
     * initialization, this method has no action.
     *
     * @throws Exception if an error occurs during the startup phase
     */
    @Override
    public void start() throws Exception {
        // do nothing
    }

    /**
     * Stops the daemon service.
     * <p>
     * This is a lifecycle method called when the daemon is stopped. As the server teardown is handled during daemon destruction,
     * this method has no action.
     *
     * @throws Exception if an error occurs during the stop phase
     */
    @Override
    public void stop() throws Exception {
        // do nothing
    }

    /**
     * Destroys the daemon service and releases associated resources.
     * <p>
     * This lifecycle method is called when the daemon is being destroyed. It performs cleanup operations by invoking
     * {@link #terminate()} to stop and shut down the running server and connectors.
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
     * Entry point for managing the application when run as a Windows service.
     * <p>
     * This method parses the service command (e.g., "start" or "stop") and the subsequent command-line arguments, then either
     * starts the service execution loop or triggers a server shutdown depending on the command received.
     *
     * @param args the command-line arguments passed by the Windows service manager, where the first element is typically the
     *        control command ("start" or "stop")
     * 
     * @throws Exception if an error occurs during argument parsing, server startup, or shutdown
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
     * This method triggers the server initialization and enters a polling loop that periodically waits while checking if the server
     * is still running.
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
     * This method terminates the running Jetty server instance and notifies all waiting threads on the {@link Server} class to
     * break the main service execution loop.
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
