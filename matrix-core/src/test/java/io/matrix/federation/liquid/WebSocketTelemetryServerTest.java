package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W586 — Tests for WebSocket Telemetry Server.
 */
class WebSocketTelemetryServerTest {

    @Test
    void testServerStartStop() throws Exception {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();
        Map<String, KineticModulator> mods = new HashMap<>();

        // Use random port to avoid conflicts
        int port = 18000 + (int) (Math.random() * 1000);
        WebSocketTelemetryServer server = new WebSocketTelemetryServer(
                port, telemetry, homeostat, mods);

        server.start();
        assertTrue(server.isRunning());
        assertEquals(port, server.getPort());

        server.stop();
        Thread.sleep(100); // Wait for port release
        assertFalse(server.isRunning());
    }
}
