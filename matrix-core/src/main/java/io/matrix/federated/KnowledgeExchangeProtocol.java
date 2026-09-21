package io.matrix.federated;

import io.matrix.distill.LLMKnowledgeDistiller;
import io.matrix.distill.BitNetEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W761 — Knowledge Exchange Protocol.
 *
 * Enables nodes to share distilled knowledge (NOT raw data):
 * - KNOWLEDGE_SHARE message type for BitNet vectors/rules
 * - KnowledgeCompressor: Minimize payload via run-length encoding
 * - ConflictResolver: Handle contradictory rules from different nodes
 *
 * CONSTITUTION IV: FROZEN filters apply to shared rules.
 * Privacy: No raw user data leaves a node — only distilled vectors/rules.
 */
public final class KnowledgeExchangeProtocol {

    /**
     * Type of message exchanged between nodes.
     */
    public enum MessageType {
        KNOWLEDGE_SHARE,
        RULE_QUERY,
        RULE_RESPONSE,
        CONFLICT_NOTICE
    }

    /**
     * A knowledge share message containing compressed rules.
     */
    public record ShareMessage(
            long senderId,
            MessageType type,
            List<LLMKnowledgeDistiller.DistilledRule> rules,
            byte[] compressedData,
            int originalSize,
            int compressedSize,
            long timestamp
    ) {}

    /**
     * Compressed representation of a list of rules.
     */
    public record CompressedKnowledge(
            byte[] data,
            int originalRuleCount,
            int compressionRatio
    ) {}

    private final Map<Long, List<LLMKnowledgeDistiller.DistilledRule>> nodeKnowledge = new ConcurrentHashMap<>();
    private final List<ShareMessage> messageLog = new ArrayList<>();
    private final BitNetEncoder encoder = new BitNetEncoder();

    /**
     * Register knowledge for a node.
     */
    public void registerKnowledge(long nodeId, List<LLMKnowledgeDistiller.DistilledRule> rules) {
        nodeKnowledge.put(nodeId, new ArrayList<>(rules));
    }

    /**
     * Compress a list of rules using run-length encoding.
     */
    public CompressedKnowledge compress(List<LLMKnowledgeDistiller.DistilledRule> rules) {
        int originalSize = rules.size() * 100; // Estimated bytes per rule

        // Simple run-length encoding simulation
        StringBuilder encoded = new StringBuilder();
        String lastPremise = "";
        int count = 0;

        for (LLMKnowledgeDistiller.DistilledRule rule : rules) {
            String premise = String.join(",", rule.positivePremises());
            if (premise.equals(lastPremise) && count < 255) {
                count++;
            } else {
                if (count > 0) {
                    encoded.append(lastPremise).append(":").append(count).append(";");
                }
                lastPremise = premise;
                count = 1;
            }
        }
        if (count > 0) {
            encoded.append(lastPremise).append(":").append(count).append(";");
        }

        byte[] data = encoded.toString().getBytes();
        int compressionRatio = originalSize / Math.max(data.length, 1);

        return new CompressedKnowledge(data, rules.size(), compressionRatio);
    }

    /**
     * Share knowledge from sender to receiver.
     */
    public ShareMessage share(long senderId, long receiverId,
                               List<LLMKnowledgeDistiller.DistilledRule> rules) {
        CompressedKnowledge compressed = compress(rules);

        ShareMessage message = new ShareMessage(
            senderId, MessageType.KNOWLEDGE_SHARE, rules,
            compressed.data(), compressed.originalRuleCount() * 100,
            compressed.data().length, System.currentTimeMillis()
        );

        // Receiver stores the knowledge
        registerKnowledge(receiverId, rules);
        messageLog.add(message);

        return message;
    }

    /**
     * Resolve conflicts between rules from different nodes.
     */
    public List<LLMKnowledgeDistiller.DistilledRule> resolveConflicts(
            List<LLMKnowledgeDistiller.DistilledRule> node1Rules,
            List<LLMKnowledgeDistiller.DistilledRule> node2Rules) {
        Map<String, Integer> votes = new HashMap<>();

        for (LLMKnowledgeDistiller.DistilledRule rule : node1Rules) {
            votes.merge(rule.conclusion(), 1, Integer::sum);
        }
        for (LLMKnowledgeDistiller.DistilledRule rule : node2Rules) {
            votes.merge(rule.conclusion(), 1, Integer::sum);
        }

        // Keep rules where both nodes agree (2+ votes)
        // Use a Set to dedupe by conclusion
        Set<String> seenConclusions = new HashSet<>();
        List<LLMKnowledgeDistiller.DistilledRule> merged = new ArrayList<>();
        for (LLMKnowledgeDistiller.DistilledRule rule : node1Rules) {
            if (votes.getOrDefault(rule.conclusion(), 0) >= 2 &&
                seenConclusions.add(rule.conclusion())) {
                merged.add(rule);
            }
        }
        for (LLMKnowledgeDistiller.DistilledRule rule : node2Rules) {
            if (votes.getOrDefault(rule.conclusion(), 0) >= 2 &&
                seenConclusions.add(rule.conclusion())) {
                merged.add(rule);
            }
        }

        return merged;
    }

    /**
     * Get knowledge for a node.
     */
    public List<LLMKnowledgeDistiller.DistilledRule> getKnowledge(long nodeId) {
        return nodeKnowledge.getOrDefault(nodeId, List.of());
    }

    /**
     * Get message log.
     */
    public List<ShareMessage> getMessageLog() {
        return new ArrayList<>(messageLog);
    }
}
