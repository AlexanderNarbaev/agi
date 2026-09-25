package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W575 — Tests for Sybil Resistance.
 */
class SybilResistanceTest {

    @Test
    void testRegisterNode() {
        SybilResistance sybil = new SybilResistance();
        SybilResistance.NodeProfile profile = sybil.registerNode(1, 5);

        assertEquals(1, profile.nodeId());
        assertEquals(5, profile.capabilityLevel());
        assertFalse(profile.verified());
    }

    @Test
    void testChallengeVerification() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 5);

        // First challenge
        SybilResistance.ChallengeResult r1 = sybil.issueChallenge(1, SybilResistance.ChallengeType.PROOF_OF_CAPABILITY);
        assertTrue(r1.passed());

        // Second challenge → verified
        SybilResistance.ChallengeResult r2 = sybil.issueChallenge(1, SybilResistance.ChallengeType.PROOF_OF_CAPABILITY);
        assertTrue(r2.passed());

        SybilResistance.NodeProfile profile = sybil.getProfile(1);
        assertTrue(profile.verified());
    }

    @Test
    void testBlacklistedNode() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 5);
        sybil.blacklistNode(1, "Sybil attack");

        assertTrue(sybil.isBlacklisted(1));

        // Cannot issue challenge
        SybilResistance.ChallengeResult r = sybil.issueChallenge(1, SybilResistance.ChallengeType.PROOF_OF_CAPABILITY);
        assertFalse(r.passed());
    }

    @Test
    void testRateLimiting() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 0); // Infant: 5 actions/sec

        // Should allow up to 5 actions
        for (int i = 0; i < 5; i++) {
            assertTrue(sybil.checkPermission(1, "test"));
        }

        // 6th action should be rate limited
        assertFalse(sybil.checkPermission(1, "test"));
    }

    @Test
    void testHigherCapabilityHigherRate() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 7); // Guardian: 200 actions/sec

        // Should allow many actions
        for (int i = 0; i < 100; i++) {
            assertTrue(sybil.checkPermission(1, "test"));
        }
    }

    @Test
    void testReputationUpdate() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 5);

        sybil.updateReputation(1, 0.3);
        SybilResistance.NodeProfile profile = sybil.getProfile(1);
        assertEquals(0.8, profile.reputationScore(), 0.001);

        // Cannot go above 1.0
        sybil.updateReputation(1, 0.5);
        profile = sybil.getProfile(1);
        assertEquals(1.0, profile.reputationScore(), 0.001);
    }

    @Test
    void testUnregisteredNodePermission() {
        SybilResistance sybil = new SybilResistance();
        assertFalse(sybil.checkPermission(999, "test"));
    }
}
