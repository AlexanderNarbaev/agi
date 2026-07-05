package io.matrix.spigot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the MATRIX Spigot plugin.
 *
 * <p>Since v2.2.0, the plugin communicates with matrix-core via HTTP/WebSocket
 * instead of running NeuralBrain locally.
 */
class MatrixPluginTest {

    @Test
    void matrixCoreClientShouldCreateWithDefaultUrl() {
        MatrixCoreClient client = new MatrixCoreClient("http://localhost:9091");
        assertThat(client.isConnected()).isFalse();
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
    @Disabled("Requires running matrix-core — run as integration test")
    void shouldConnectToMatrixCore() {
        MatrixCoreClient client = new MatrixCoreClient("http://localhost:9091");
        client.connect(new MatrixCoreClient.ActionCallback() {
            @Override public void onAction(String action) {}
            @Override public void onStatus(String status) {}
            @Override public void onError(String error) {}
        });
        // Integration test: requires matrix-core on localhost:9091
    }

    @Test
    @Disabled("Requires running matrix-core — run as integration test")
    void shouldTrainAndReceiveResults() {
        MatrixCoreClient client = new MatrixCoreClient("http://localhost:9091");
        client.train(10, 20, 8);
    }
}
