package io.matrix.federated;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class FederatedProtocolIntegrationTest {

    @Test
    void protocolRoundtrip() {
        var protocol = new FederatedProtocol();
        var aggregator = new SecureAggregator();
        var privacy = new PrivacyMechanism();
        
        // Create updates
        var u1 = new LocalUpdate("node1", new boolean[]{true, false, true, false}, 10, 0.1);
        var u2 = new LocalUpdate("node2", new boolean[]{false, true, false, true}, 20, 0.2);
        var u3 = new LocalUpdate("node3", new boolean[]{true, true, false, false}, 30, 0.15);
        
        // Apply privacy
        var private1 = privacy.addNoise(u1);
        var private2 = privacy.addNoise(u2);
        var private3 = privacy.addNoise(u3);
        
        // Aggregate
        var aggregated = aggregator.aggregate(List.of(private1, private2, private3));
        assertNotNull(aggregated);
        assertEquals(4, aggregated.length);
        
        // Run federated round
        var round = protocol.runRound(List.of(u1, u2, u3));
        assertNotNull(round);
        assertEquals(3, round.participantCount());
    }

    @Test
    void compressionCodecRoundtrip() {
        var codec = new CompressionCodec();
        boolean[] data = {true, false, true, true, false, false, false, true, true, true, false};
        byte[] compressed = codec.compress(data);
        boolean[] decompressed = codec.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void privacyMechanismEpsilonBoundary() {
        var privacy = new PrivacyMechanism();
        privacy.setEpsilon(0.005);
        assertEquals(0.01, privacy.getEpsilon(), 0.001);
        privacy.setEpsilon(150.0);
        assertEquals(100.0, privacy.getEpsilon(), 0.001);
    }

    @Test
    void secureAggregatorRemovesOutliers() {
        var aggregator = new SecureAggregator();
        var u1 = new LocalUpdate("n1", new boolean[]{true, false}, 10, 0.1);
        var u2 = new LocalUpdate("n2", new boolean[]{true, false}, 10, 0.1);
        var outlier = new LocalUpdate("n3", new boolean[]{false, true}, 10, 10.0); // High loss
        
        var result = aggregator.aggregate(List.of(u1, u2, outlier));
        assertEquals(2, result.length);
    }
}
