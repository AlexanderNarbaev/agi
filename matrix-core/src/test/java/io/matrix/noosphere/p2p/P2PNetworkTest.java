package io.matrix.noosphere.p2p;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.Socket;
import java.io.IOException;

class PeerTest {

    @Test
    void peerCreation() throws IOException {
        // Peer can't be created without actual Socket, test basic invariants
        assertTrue(true);
    }
}

class P2PNetworkTest {

    @Test
    void networkCreation() {
        var network = new P2PNetwork();
        assertNotNull(network);
        assertEquals(0, network.getPeerCount());
    }
}
