package io.matrix.api;

import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Configurator for AgentWebSocket that allows custom handshake logic.
 *
 * <p>Used to inject authentication checks during the WebSocket upgrade handshake.
 */
public class AgentWebSocketConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // In production, validate against allowed origins
        // For now, accept all origins (restrict in production deployment)
        return originHeaderValue != null && !originHeaderValue.isBlank();
    }
}
