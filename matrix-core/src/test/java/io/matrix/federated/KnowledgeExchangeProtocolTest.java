package io.matrix.federated;

import io.matrix.distill.LLMKnowledgeDistiller;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeExchangeProtocolTest {

    @Test
    void testCreateProtocol() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        assertNotNull(protocol);
    }

    @Test
    void testRegisterKnowledge() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        List<LLMKnowledgeDistiller.DistilledRule> rules = createSampleRules(5);
        protocol.registerKnowledge(1L, rules);

        assertEquals(5, protocol.getKnowledge(1L).size());
    }

    @Test
    void testCompressRules() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        List<LLMKnowledgeDistiller.DistilledRule> rules = createSampleRules(100);
        KnowledgeExchangeProtocol.CompressedKnowledge compressed = protocol.compress(rules);

        assertEquals(100, compressed.originalRuleCount());
        assertTrue(compressed.compressionRatio() >= 1);
    }

    @Test
    void testShareKnowledge() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        List<LLMKnowledgeDistiller.DistilledRule> rules = createSampleRules(10);

        KnowledgeExchangeProtocol.ShareMessage message =
            protocol.share(1L, 2L, rules);

        assertEquals(1L, message.senderId());
        assertEquals(10, protocol.getKnowledge(2L).size());
        assertEquals(1, protocol.getMessageLog().size());
    }

    @Test
    void testResolveConflicts() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();

        // Node 1: rules A, B, C
        List<LLMKnowledgeDistiller.DistilledRule> node1 = List.of(
            new LLMKnowledgeDistiller.DistilledRule("1", "d", List.of("x"), List.of(), "A", 0.9, "ctx"),
            new LLMKnowledgeDistiller.DistilledRule("2", "d", List.of("y"), List.of(), "B", 0.9, "ctx"),
            new LLMKnowledgeDistiller.DistilledRule("3", "d", List.of("z"), List.of(), "C", 0.9, "ctx")
        );

        // Node 2: rules A, B, D (A,B agree, C conflicts, D new)
        List<LLMKnowledgeDistiller.DistilledRule> node2 = List.of(
            new LLMKnowledgeDistiller.DistilledRule("4", "d", List.of("x"), List.of(), "A", 0.9, "ctx"),
            new LLMKnowledgeDistiller.DistilledRule("5", "d", List.of("y"), List.of(), "B", 0.9, "ctx"),
            new LLMKnowledgeDistiller.DistilledRule("6", "d", List.of("w"), List.of(), "D", 0.9, "ctx")
        );

        List<LLMKnowledgeDistiller.DistilledRule> merged = protocol.resolveConflicts(node1, node2);

        // Only rules A and B should be merged (both nodes agree)
        long aCount = merged.stream().filter(r -> r.conclusion().equals("A")).count();
        long bCount = merged.stream().filter(r -> r.conclusion().equals("B")).count();
        assertEquals(1, aCount, "Rule A should appear once after merge");
        assertEquals(1, bCount, "Rule B should appear once after merge");
    }

    @Test
    void testTwoNodeExchange() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();

        List<LLMKnowledgeDistiller.DistilledRule> rules1 = createSampleRules(100);
        List<LLMKnowledgeDistiller.DistilledRule> rules2 = createSampleRules(50);

        protocol.registerKnowledge(1L, rules1);
        protocol.registerKnowledge(2L, rules2);

        // Exchange
        protocol.share(1L, 2L, rules1);
        protocol.share(2L, 1L, rules2);

        assertEquals(2, protocol.getMessageLog().size());
    }

    @Test
    void testMessageLogIntegrity() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        List<LLMKnowledgeDistiller.DistilledRule> rules = createSampleRules(10);

        for (int i = 0; i < 5; i++) {
            protocol.share(1L, 2L, rules);
        }

        assertEquals(5, protocol.getMessageLog().size());
        for (KnowledgeExchangeProtocol.ShareMessage msg : protocol.getMessageLog()) {
            assertEquals(KnowledgeExchangeProtocol.MessageType.KNOWLEDGE_SHARE, msg.type());
            assertTrue(msg.timestamp() > 0);
        }
    }

    @Test
    void testCompressionEfficiency() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();

        // Highly redundant rules should compress well
        List<LLMKnowledgeDistiller.DistilledRule> rules = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rules.add(new LLMKnowledgeDistiller.DistilledRule("r" + i, "d",
                List.of("same_premise"), List.of(), "conclusion", 0.9, "ctx"));
        }

        KnowledgeExchangeProtocol.CompressedKnowledge compressed = protocol.compress(rules);
        assertTrue(compressed.compressionRatio() >= 1,
            "Redundant rules should compress: " + compressed.compressionRatio());
    }

    @Test
    void testEmptyRulesCompression() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        KnowledgeExchangeProtocol.CompressedKnowledge compressed = protocol.compress(List.of());
        assertEquals(0, compressed.originalRuleCount());
    }

    @Test
    void testMessageTimestamp() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        List<LLMKnowledgeDistiller.DistilledRule> rules = createSampleRules(5);

        long before = System.currentTimeMillis();
        KnowledgeExchangeProtocol.ShareMessage msg = protocol.share(1L, 2L, rules);
        long after = System.currentTimeMillis();

        assertTrue(msg.timestamp() >= before);
        assertTrue(msg.timestamp() <= after);
    }

    @Test
    void testOriginalSizeTracking() {
        KnowledgeExchangeProtocol protocol = new KnowledgeExchangeProtocol();
        List<LLMKnowledgeDistiller.DistilledRule> rules = createSampleRules(10);

        KnowledgeExchangeProtocol.ShareMessage msg = protocol.share(1L, 2L, rules);

        assertTrue(msg.originalSize() > 0);
        assertTrue(msg.compressedSize() >= 0);
    }

    private List<LLMKnowledgeDistiller.DistilledRule> createSampleRules(int count) {
        List<LLMKnowledgeDistiller.DistilledRule> rules = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rules.add(new LLMKnowledgeDistiller.DistilledRule(
                "rule-" + i, "Test rule",
                List.of("p" + i), List.of(), "c" + (i % 5), 0.9, "test"
            ));
        }
        return rules;
    }
}
