package io.matrix.noosphere.p2p;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trust/reputation manager for P2P peers.
 * 
 * Tracks peer reliability based on successful/failed interactions
 * and knowledge quality.
 */
@ApplicationScoped
public class TrustManager {

    private final Map<String, PeerReputation> reputations = new ConcurrentHashMap<>();

    /**
     * Get trust score for a peer (0.0 to 1.0).
     */
    public double getTrustScore(String peerId) {
        PeerReputation rep = reputations.get(peerId);
        if (rep == null) return 0.5; // Default neutral
        return rep.calculateScore();
    }

    /**
     * Record a successful interaction.
     */
    public void recordSuccess(String peerId, double quality) {
        reputations.computeIfAbsent(peerId, k -> new PeerReputation())
                   .recordSuccess(quality);
    }

    /**
     * Record a failed interaction.
     */
    public void recordFailure(String peerId) {
        reputations.computeIfAbsent(peerId, k -> new PeerReputation())
                   .recordFailure();
    }

    /**
     * Get all reputations.
     */
    public Map<String, Double> getAllScores() {
        Map<String, Double> scores = new ConcurrentHashMap<>();
        reputations.forEach((id, rep) -> scores.put(id, rep.calculateScore()));
        return scores;
    }

    /**
     * Peer reputation tracker.
     */
    public static class PeerReputation {
        private int successful = 0;
        private int failed = 0;
        private double qualitySum = 0.0;

        public synchronized void recordSuccess(double quality) {
            successful++;
            qualitySum += Math.max(0, Math.min(1, quality));
        }

        public synchronized void recordFailure() {
            failed++;
        }

        public synchronized double calculateScore() {
            if (successful + failed == 0) return 0.5;
            double successRate = (double) successful / (successful + failed);
            double avgQuality = successful > 0 ? qualitySum / successful : 0.5;
            return (successRate * 0.7) + (avgQuality * 0.3);
        }

        public synchronized int getTotalInteractions() {
            return successful + failed;
        }
    }
}
