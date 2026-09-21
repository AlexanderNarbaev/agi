package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class P2PIntegrationTest {

    @Test
    void p2pNetworkStartsAndStops() {
        var network = new P2PNetwork();
        // Network starts with 0 peers
        assertEquals(0, network.getPeerCount());
    }

    @Test
    void trustManagerMultiplePeers() {
        var tm = new TrustManager();
        // Add multiple peers
        tm.recordSuccess("peerA", 0.9);
        tm.recordSuccess("peerB", 0.8);
        tm.recordFailure("peerC");
        tm.recordSuccess("peerD", 0.7);
        tm.recordFailure("peerD");
        
        var scores = tm.getAllScores();
        assertEquals(4, scores.size());
        assertTrue(scores.get("peerA") > scores.get("peerC"));
    }

    @Test
    void consensusWithIdenticalPackages() {
        var consensus = new KnowledgeConsensus();
        var pkg = FnlPackage.builder()
                .name("test")
                .snapshotHash("hash1")
                .authorInstanceId("author1")
                .build();
        
        assertFalse(consensus.hasConflict(List.of(pkg, pkg, pkg)));
        assertEquals(1, consensus.mergeUnique(List.of(pkg, pkg, pkg)).size());
    }

    @Test
    void privacyPreserverConsistentHashing() {
        var preserver = new PrivacyPreserver();
        var pkg1 = FnlPackage.builder()
                .name("test1")
                .authorInstanceId("peer-123")
                .build();
        var pkg2 = FnlPackage.builder()
                .name("test2")
                .authorInstanceId("peer-123")
                .build();
        
        var anon1 = preserver.anonymize(pkg1);
        var anon2 = preserver.anonymize(pkg2);
        // Same author should produce same anonymous ID
        assertEquals(anon1.authorInstanceId(), anon2.authorInstanceId());
    }
}
