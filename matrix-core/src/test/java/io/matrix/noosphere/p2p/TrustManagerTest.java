package io.matrix.noosphere.p2p;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrustManagerTest {

    private TrustManager trustManager;

    @BeforeEach
    void setUp() {
        trustManager = new TrustManager();
    }

    @Test
    void newPeerHasNeutralTrust() {
        assertEquals(0.5, trustManager.getTrustScore("unknown"), 0.001);
    }

    @Test
    void successfulInteractionsIncreaseTrust() {
        trustManager.recordSuccess("peer1", 0.9);
        trustManager.recordSuccess("peer1", 0.8);
        assertTrue(trustManager.getTrustScore("peer1") > 0.7);
    }

    @Test
    void failedInteractionsDecreaseTrust() {
        trustManager.recordSuccess("peer2", 1.0);
        trustManager.recordFailure("peer2");
        trustManager.recordFailure("peer2");
        trustManager.recordFailure("peer2");
        assertTrue(trustManager.getTrustScore("peer2") < 0.5);
    }

    @Test
    void trustScoreBoundedZeroToOne() {
        for (int i = 0; i < 100; i++) {
            trustManager.recordFailure("peer3");
        }
        double score = trustManager.getTrustScore("peer3");
        assertTrue(score >= 0.0 && score <= 1.0, "Score out of bounds: " + score);
    }

    @Test
    void multiplePeersIndependent() {
        trustManager.recordSuccess("peerA", 1.0);
        trustManager.recordFailure("peerB");
        assertTrue(trustManager.getTrustScore("peerA") > 0.5);
        assertTrue(trustManager.getTrustScore("peerB") < 0.5);
    }

    @Test
    void getAllScoresReturnsMap() {
        trustManager.recordSuccess("peer1", 0.7);
        trustManager.recordSuccess("peer2", 0.9);
        var scores = trustManager.getAllScores();
        assertEquals(2, scores.size());
        assertTrue(scores.containsKey("peer1"));
        assertTrue(scores.containsKey("peer2"));
    }
}
