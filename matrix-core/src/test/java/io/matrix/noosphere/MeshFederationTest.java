package io.matrix.noosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M4: Mesh Federation tests.
 */
class MeshFederationTest {

    @Test
    void joinAndPeerCount() {
        MeshFederation mesh = new MeshFederation("node1", 3, null, null);
        mesh.join();
        assertEquals(1, mesh.peerCount());

        mesh.addPeer("node2");
        mesh.addPeer("node3");
        assertEquals(3, mesh.peerCount());
    }

    @Test
    void publishAndLocalState() {
        MeshFederation mesh = new MeshFederation("node1", 3, null, null);
        mesh.join();

        FnlPackage pkg = FnlPackage.builder()
                .name("test_fnl")
                .type("classifier")
                .version("1.0.0")
                .authorInstanceId("node1")
                .accuracy(0.95)
                .generation(1)
                .description("Test FNL")
                .tags("test")
                .certified(false)
                .build();

        mesh.publish(pkg);

        assertTrue(mesh.localState().containsKey("test_fnl"));
        assertEquals("test_fnl", mesh.localState().get("test_fnl").name());
    }

    @Test
    void quorumCheck() {
        MeshFederation mesh = new MeshFederation("node1", 3, null, null);
        mesh.join();

        assertFalse(mesh.hasQuorum());

        mesh.addPeer("node2");
        mesh.addPeer("node3");
        assertTrue(mesh.hasQuorum());
    }

    @Test
    void listenerNotification() {
        MeshFederation mesh = new MeshFederation("node1", 3, null, null);

        java.util.List<String> events = new java.util.ArrayList<>();
        mesh.addListener((event, data) -> events.add(event + ":" + data));

        mesh.join();
        mesh.addPeer("node2");

        assertEquals(2, events.size());
        assertEquals("join:node1", events.get(0));
        assertEquals("peer_added:node2", events.get(1));
    }
}
