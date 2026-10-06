package jrm.server.shared.ws;

import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;

import jrm.misc.Log;
import jrm.server.AbstractServer;

/**
 * Registers the optional {@code /ws} endpoint on the shared {@link ServletContextHandler} (same context, so session
 * cookies, {@code LocalAdminFilter}, and FullServer Basic-auth apply unchanged). No-op unless
 * {@link AbstractServer#isWebsocketEnabled()}.
 * <p>
 * Must be called before the context starts (Jetty 12 + {@code ServletContextHandler} has no endpoint auto-discovery).
 * The container max text message size covers large pushes (100-message {@code Global.multiCMD} batches from the 20s
 * LPR window); the upgrade request itself passes through the surrounding {@code GzipHandler} unbuffered.
 * </p>
 */
public final class WsEndpointRegistrar {

    /** Max WS text message size (both directions): 16 MiB, covers batched progress pushes. */
    static final long MAX_TEXT_MESSAGE_SIZE = 16L * 1024L * 1024L;

    /** Idle timeout: 10 minutes, above the 300s session timeout so heartbeat/pushes keep it alive first. */
    static final long MAX_IDLE_TIMEOUT_MS = 10L * 60L * 1000L;

    private WsEndpointRegistrar() {
    }

    /**
     * Registers {@link ActionSocket} when the websocket flag is on.
     *
     * @param context the started-or-not servlet context handler
     */
    public static void registerIfEnabled(final ServletContextHandler context) {
        if (!AbstractServer.isWebsocketEnabled())
            return;
        JakartaWebSocketServletContainerInitializer.configure(context, (_, container) -> {
            container.setDefaultMaxTextMessageBufferSize((int) MAX_TEXT_MESSAGE_SIZE);
            container.setDefaultMaxSessionIdleTimeout(MAX_IDLE_TIMEOUT_MS);
            container.addEndpoint(ActionSocket.class);
        });
        Log.config("websocket actions channel enabled at /ws");
    }
}
