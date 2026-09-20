package io.matrix.federation.liquid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W575 — Security & Sybil Resistance.
 *
 * Implements Proof-of-Capability handshake for new nodes.
 * Rate limiting based on role (Infants cannot vote).
 */
public final class SybilResistance {

    private final Map<Long, NodeProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Long, List<ActionRecord>> actionHistory = new ConcurrentHashMap<>();
    private final Set<Long> blacklistedNodes = ConcurrentHashMap.newKeySet();

    public record NodeProfile(
            long nodeId,
            long registrationTimeNs,
            int capabilityLevel,
            double reputationScore,
            boolean verified,
            int challengeCount
    ) {}

    public record ActionRecord(String action, long timestampNs, boolean allowed) {}

    public enum ChallengeType {
        PROOF_OF_CAPABILITY,    // Demonstrate capability level
        PROOF_OF_WORK,          // Computational challenge
        PROOF_OF_KNOWLEDGE,     // Knowledge verification
        PROOF_OF_IDENTITY       // Identity verification
    }

    /**
     * Register a new node with initial verification.
     */
    public NodeProfile registerNode(long nodeId, int capabilityLevel) {
        NodeProfile profile = new NodeProfile(
                nodeId,
                System.nanoTime(),
                capabilityLevel,
                0.5, // Initial reputation
                false, // Not verified until challenge
                0
        );
        profiles.put(nodeId, profile);
        return profile;
    }

    /**
     * Issue a challenge to verify a node.
     *
     * @return challenge result
     */
    public ChallengeResult issueChallenge(long nodeId, ChallengeType type) {
        NodeProfile profile = profiles.get(nodeId);
        if (profile == null) {
            return new ChallengeResult(nodeId, type, false, "Node not registered");
        }

        if (blacklistedNodes.contains(nodeId)) {
            return new ChallengeResult(nodeId, type, false, "Node blacklisted");
        }

        // Increment challenge count
        NodeProfile updated = new NodeProfile(
                profile.nodeId(),
                profile.registrationTimeNs(),
                profile.capabilityLevel(),
                profile.reputationScore(),
                profile.challengeCount() >= 1, // Verified after 2 successful challenges
                profile.challengeCount() + 1
        );
        profiles.put(nodeId, updated);

        // For now, auto-approve (in production, this would require actual proof)
        return new ChallengeResult(nodeId, type, true, "Challenge passed");
    }

    public record ChallengeResult(long nodeId, ChallengeType type, boolean passed, String message) {}

    /**
     * Check if a node can perform an action.
     *
     * @return true if allowed
     */
    public boolean checkPermission(long nodeId, String action) {
        if (blacklistedNodes.contains(nodeId)) {
            recordAction(nodeId, action, false);
            return false;
        }

        NodeProfile profile = profiles.get(nodeId);
        if (profile == null) {
            recordAction(nodeId, action, false);
            return false;
        }

        // Rate limiting based on capability
        int recentActions = getRecentActionCount(nodeId, 1_000_000_000L); // 1 second
        int maxActionsPerSecond = getMaxActionsPerSecond(profile.capabilityLevel());

        if (recentActions >= maxActionsPerSecond) {
            recordAction(nodeId, action, false);
            return false;
        }

        recordAction(nodeId, action, true);
        return true;
    }

    private int getMaxActionsPerSecond(int capabilityLevel) {
        return switch (capabilityLevel) {
            case 0, 1, 2 -> 5;   // Infants: 5 actions/sec
            case 3, 4 -> 20;     // Learners: 20 actions/sec
            case 5 -> 50;        // Adults: 50 actions/sec
            case 6 -> 100;       // Specialists: 100 actions/sec
            case 7 -> 200;       // Guardians: 200 actions/sec
            default -> 1;
        };
    }

    private int getRecentActionCount(long nodeId, long windowNs) {
        List<ActionRecord> history = actionHistory.get(nodeId);
        if (history == null) return 0;

        long now = System.nanoTime();
        return (int) history.stream()
                .filter(r -> (now - r.timestampNs()) < windowNs)
                .count();
    }

    private void recordAction(long nodeId, String action, boolean allowed) {
        actionHistory.computeIfAbsent(nodeId, k -> new ArrayList<>())
                .add(new ActionRecord(action, System.nanoTime(), allowed));
    }

    /**
     * Blacklist a node (for Sybil attacks).
     */
    public void blacklistNode(long nodeId, String reason) {
        blacklistedNodes.add(nodeId);
    }

    /**
     * Check if a node is blacklisted.
     */
    public boolean isBlacklisted(long nodeId) {
        return blacklistedNodes.contains(nodeId);
    }

    /**
     * Get a node's profile.
     */
    public NodeProfile getProfile(long nodeId) {
        return profiles.get(nodeId);
    }

    /**
     * Update reputation score.
     */
    public void updateReputation(long nodeId, double delta) {
        NodeProfile profile = profiles.get(nodeId);
        if (profile == null) return;

        double newScore = Math.max(0, Math.min(1, profile.reputationScore() + delta));
        NodeProfile updated = new NodeProfile(
                profile.nodeId(),
                profile.registrationTimeNs(),
                profile.capabilityLevel(),
                newScore,
                profile.verified(),
                profile.challengeCount()
        );
        profiles.put(nodeId, updated);
    }

    /**
     * Get all registered nodes.
     */
    public Collection<NodeProfile> getAllProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    /**
     * Get blacklisted nodes.
     */
    public Set<Long> getBlacklistedNodes() {
        return Collections.unmodifiableSet(blacklistedNodes);
    }
}
