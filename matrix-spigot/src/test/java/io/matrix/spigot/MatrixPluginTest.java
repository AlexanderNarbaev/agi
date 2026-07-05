package io.matrix.spigot;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the MATRIX Spigot plugin.
 *
 * <p>Since v2.2.0, the plugin communicates with matrix-core via HTTP/WebSocket.
 * Unit tests verify client behavior. Integration tests auto-skip when matrix-core
 * is not running.
 */
class MatrixPluginTest {

    @BeforeAll
    static void checkMatrixCore() {
        // Integration tests need matrix-core running on localhost:9091
    }

    @Test
    void matrixCoreClientShouldNotBeConnectedInitially() {
        MatrixCoreClient client = new MatrixCoreClient("http://localhost:9091");
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void matrixCoreClientShouldAcceptCustomUrl() {
        MatrixCoreClient client = new MatrixCoreClient("http://matrix:9091");
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void shouldConnectToMatrixCore() {
        boolean matrixUp = isPortOpen("localhost", 9091);
        assumeTrue(matrixUp,
                "Skipping — matrix-core not running. Start: ./scripts/matrix-full.sh start");
        MatrixCoreClient client = new MatrixCoreClient("http://localhost:9091");
        client.connect(new MatrixCoreClient.ActionCallback() {
            @Override public void onAction(String action) {}
            @Override public void onStatus(String status) {}
            @Override public void onError(String error) {}
        });
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void shouldTrainAndReceiveResults() {
        boolean matrixUp = isPortOpen("localhost", 9091);
        assumeTrue(matrixUp,
                "Skipping — matrix-core not running. Start: ./scripts/matrix-full.sh start");
        MatrixCoreClient client = new MatrixCoreClient("http://localhost:9091");
        client.connect(new MatrixCoreClient.ActionCallback() {
            @Override public void onAction(String action) {}
            @Override public void onStatus(String status) {}
            @Override public void onError(String error) {}
        });
        var result = client.train(5, 10, 4);
        assertThat(result).isNotNull();
    }

    private static boolean isPortOpen(String host, int port) {
        try (var s = new java.net.Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
