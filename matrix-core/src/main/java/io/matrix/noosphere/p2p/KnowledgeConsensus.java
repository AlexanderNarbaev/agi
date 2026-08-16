package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Consensus mechanism for conflicting knowledge from multiple peers.
 * 
 * Uses weighted voting based on peer trust scores to resolve conflicts
 * when multiple peers provide different versions of the same knowledge.
 */
@ApplicationScoped
public class KnowledgeConsensus {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeConsensus.class);

    @Inject
    TrustManager trustManager;

    /**
     * Resolve conflict between multiple versions of knowledge.
     * Uses weighted voting based on trust scores.
     */
    public FnlPackage resolveConflict(List<FnlPackage> conflicting) {
        if (conflicting.isEmpty()) {
            throw new IllegalArgumentException("No packages to resolve");
        }
        if (conflicting.size() == 1) {
            return conflicting.get(0);
        }

        // Weighted voting
        Map<String, Double> votes = new HashMap<>();
        Map<String, FnlPackage> packageMap = new HashMap<>();

        for (FnlPackage pkg : conflicting) {
            double trust = trustManager.getTrustScore(pkg.authorInstanceId());
            String hash = pkg.snapshotHash();
            votes.merge(hash, trust, Double::sum);
            packageMap.putIfAbsent(hash, pkg);
        }

        // Select highest-voted version
        String winner = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(conflicting.get(0).snapshotHash());

        FnlPackage result = packageMap.getOrDefault(winner, conflicting.get(0));
        log.debug("Conflict resolved: {} votes, winner trust={}", votes.size(), votes.get(winner));
        return result;
    }

    /**
     * Check if packages are conflicting (different content hashes).
     */
    public boolean hasConflict(List<FnlPackage> packages) {
        if (packages.size() <= 1) return false;
        String firstHash = packages.get(0).snapshotHash();
        return packages.stream().anyMatch(p -> !p.snapshotHash().equals(firstHash));
    }

    /**
     * Merge non-conflicting packages.
     */
    public List<FnlPackage> mergeUnique(List<FnlPackage> packages) {
        Map<String, FnlPackage> unique = new LinkedHashMap<>();
        for (FnlPackage pkg : packages) {
            unique.putIfAbsent(pkg.snapshotHash(), pkg);
        }
        return new ArrayList<>(unique.values());
    }
}
