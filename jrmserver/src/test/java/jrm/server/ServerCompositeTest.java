package jrm.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.time.Duration;

import org.awaitility.Awaitility;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ContentResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Composite (integration) tests for {@link Server} using a Jetty {@link HttpClient}.
 *
 * <p>
 * The server is started on an ephemeral port (port 0) and the actual assigned port is read via
 * {@link Server#getLocalPort()} after startup. The Jetty client targets that port.
 * </p>
 */
@DisplayName("Server composite (Jetty client)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerCompositeTest {

    private HttpClient client;
    private int port;

    @BeforeAll
    void startServer() throws Exception {
        final String rootPath = System.getProperty("JRomManager.rootPath");
        Server.parseArgs(
                "--client=" + Paths.get(rootPath).resolve("WebClient").resolve("war"),
                "--debug");
        Server.setHttpPort(0);
        Server.initialize();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(Server::isStarted);
        port = Server.getLocalPort();
        assertThat(port).isPositive();

        client = new HttpClient();
        client.start();
    }

    @AfterAll
    void stopServer() throws Exception {
        if (client != null)
            client.stop();
        Server.terminate();
    }

    @Nested
    @DisplayName("GET /session")
    class GetSessionTest {
        @Test
        @DisplayName("returns 200 with JSON body containing session, msgs, settings")
        void getSession() throws Exception {
            final var request = client.POST("http://localhost:" + port + "/session");
            request.headers(headers -> headers.add("accept-language", "en"));
            final ContentResponse response = request.send();
            assertThat(response.getStatus()).isEqualTo(200);
            final String body = response.getContentAsString();
            assertThat(body).contains("session").contains("msgs").contains("settings");
        }
    }

    @Nested
    @DisplayName("GET /images/<nonexistent>")
    class GetImageTest {
        @Test
        @DisplayName("returns 404 for missing resource")
        void getMissingImage() throws Exception {
            final ContentResponse response = client.GET("http://localhost:" + port + "/images/nonexistent.png");
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("GET /actions/init")
    class GetActionsInitTest {
        @Test
        @DisplayName("returns 200 or 410 JSON for loopback")
        void getActionsInit() throws Exception {
            final ContentResponse response = client.GET("http://localhost:" + port + "/actions/init");
            assertThat(response.getStatus()).isIn(200, 410);
        }
    }

    @Nested
    @DisplayName("POST /actions/cmd")
    class PostActionsCmdTest {
        @Test
        @DisplayName("loopback can run Global.getMemory")
        void loopbackCommand() throws Exception {
            final var request = client.POST("http://localhost:" + port + "/actions/cmd");
            request.headers(headers -> {
                headers.add("Content-Type", "application/json");
            });
            request.body(new org.eclipse.jetty.client.StringRequestContent("application/json",
                    "{\"cmd\":\"Global.getMemory\"}"));
            final ContentResponse response = request.send();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}