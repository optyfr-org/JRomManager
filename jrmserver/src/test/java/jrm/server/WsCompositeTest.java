package jrm.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.ee9.websocket.jakarta.client.JakartaWebSocketClientContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

/**
 * Composite (integration) tests for the optional {@code /ws} actions channel.
 */
@DisplayName("WS composite (Jetty client)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WsCompositeTest {

    private HttpClient http;
    private JakartaWebSocketClientContainer wsClient;
    private int port;
    private String cookie;

    @BeforeAll
    void startServer() throws Exception {
        final String rootPath = System.getProperty("JRomManager.rootPath");
        Server.parseArgs(
                "--client=" + Paths.get(rootPath).resolve("WebClient").resolve("war"),
                "--debug",
                "--websocket");
        Server.setHttpPort(0);
        Server.initialize();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(Server::isStarted);
        port = Server.getLocalPort();
        assertThat(port).isPositive();

        http = new HttpClient();
        http.start();

        // Establish a session (loopback auto-admin) and capture the cookie for the WS upgrade.
        final var request = http.POST("http://localhost:" + port + "/session");
        request.headers(headers -> headers.add("accept-language", "en"));
        final ContentResponse response = request.send();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"websocket\":true");
        cookie = response.getHeaders().get("set-cookie");
        assertThat(cookie).isNotNull();

        wsClient = new JakartaWebSocketClientContainer();
        wsClient.start();
    }

    @AfterAll
    void stopServer() throws Exception {
        if (wsClient != null)
            wsClient.stop();
        if (http != null)
            http.stop();
        AbstractServer.setWebsocketEnabled(false);
        Server.terminate();
    }

    private Session connect(final LinkedBlockingQueue<String> inbox) throws Exception {
        final var config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(final java.util.Map<String, java.util.List<String>> headers) {
                        headers.put("Cookie", java.util.List.of(cookie));
                    }
                })
                .build();
        final var endpoint = new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig cfg) {
                session.addMessageHandler(String.class, (MessageHandler.Whole<String>) inbox::offer);
            }
        };
        return wsClient.connectToServer(endpoint, config, URI.create("ws://localhost:" + port + "/ws"));
    }

    @Nested
    @DisplayName("unauthenticated upgrade")
    class RejectedTest {
        @Test
        @DisplayName("rejected without the session cookie")
        void rejected() throws Exception {
            final var inbox = new LinkedBlockingQueue<String>();
            final var config = ClientEndpointConfig.Builder.create().build();
            final var endpoint = new Endpoint() {
                @Override
                public void onOpen(final Session session, final EndpointConfig cfg) {
                    session.addMessageHandler(String.class, (MessageHandler.Whole<String>) inbox::offer);
                }
            };
            boolean failed = false;
            Session session = null;
            try {
                session = wsClient.connectToServer(endpoint, config, URI.create("ws://localhost:" + port + "/ws"));
                // Give the server a chance to close an unauthorized socket.
                Thread.sleep(1500);
                failed = !session.isOpen();
            } catch (final Exception _) {
                failed = true;
            } finally {
                if (session != null && session.isOpen())
                    session.close();
            }
            assertThat(failed).isTrue();
        }
    }

    @Nested
    @DisplayName("authenticated upgrade")
    class UpgradeTest {
        @Test
        @DisplayName("connects, receives init-seeded push, and round-trips a command")
        void roundTrip() throws Exception {
            final var inbox = new LinkedBlockingQueue<String>();
            final Session session = connect(inbox);
            try {
                assertThat(session.isOpen()).isTrue();
                session.getBasicRemote().sendText("{\"cmd\":\"Global.getMemory\"}");
                final String msg = inbox.poll(10, TimeUnit.SECONDS);
                assertThat(msg).isNotNull().contains("Global.setMemory");
            } finally {
                session.close();
            }
        }

        @Test
        @DisplayName("Global.ping keeps the session without reply")
        void pingNoReply() throws Exception {
            final var inbox = new LinkedBlockingQueue<String>();
            final Session session = connect(inbox);
            try {
                inbox.clear();
                session.getBasicRemote().sendText("{\"cmd\":\"Global.ping\"}");
                assertThat(inbox.poll(2, TimeUnit.SECONDS)).isNull();
            } finally {
                session.close();
            }
        }

        @Test
        @DisplayName("LPR still works when WS enabled")
        void lprFallback() throws Exception {
            final var request = http.POST("http://localhost:" + port + "/actions/cmd");
            request.headers(headers -> {
                headers.add("Content-Type", "application/json");
                headers.add("Cookie", cookie);
            });
            request.body(new org.eclipse.jetty.client.StringRequestContent("application/json", "{\"cmd\":\"Global.getMemory\"}"));
            final ContentResponse response = request.send();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
