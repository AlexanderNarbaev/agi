package io.matrix.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardedHybridRagTest {

    private BooleanIndex denseIndex;
    private HybridBooleanRag rag;

    @BeforeEach
    void setup() {
        denseIndex = BooleanIndex.builder().dimensions(64).build();
        denseIndex.add("NeuralNetwork",     new long[]{0xABCDEF0123456789L});
        denseIndex.add("Backpropagation",   new long[]{0xFEDCBA9876543210L});
        denseIndex.add("concept:abstract",  new long[]{0b11000011L});
        denseIndex.add("concept:vision",    new long[]{0b11110000L});

        rag = HybridBooleanRag.builder()
                .index(denseIndex)
                .topK(3)
                .build();
    }

    // ═══ guardedQuery — matching terms → generation allowed ═══

    @Test
    void shouldAllowGenerationWhenTechnicalTermsMatchContext() {
        // Query vector that matches "NeuralNetwork" (exact match via Hamming distance 0)
        long[] query = {0xABCDEF0123456789L};
        String textQuery = "Explain NeuralNetwork architecture";

        HybridBooleanRag.GuardedRagResult result = rag.guardedQuery(query, textQuery);

        assertThat(result).isNotNull();
        assertThat(result.ragResult()).isNotNull();
        assertThat(result.guardResult()).isNotNull();
        assertThat(result.generationAllowed()).isTrue();
        assertThat(result.guardResult().allowed()).isTrue();
        assertThat(result.guardResult().matchedTerms())
                .contains("NeuralNetwork");
        assertThat(result.guardResult().missingTerms()).isEmpty();
    }

    @Test
    void shouldAllowGenerationWhenMultipleTermsAllMatch() {
        // Use an index where two entries have very similar vectors (distance 1)
        // so a single query returns both in top-K results.
        // Note: terms must have internal uppercase transitions to be recognized
        // as CamelCase (sentence-initial capitals like "Backpropagation" are excluded).
        var idx = BooleanIndex.builder().dimensions(64).build();
        idx.add("neuralNet",     new long[]{0xAAAA0000BBBB0001L});
        idx.add("vectorDB",      new long[]{0xAAAA0000BBBB0000L});
        idx.add("concept:vision",    new long[]{0xFFFFFFFF00000000L});

        var r = HybridBooleanRag.builder()
                .index(idx).topK(3)
                .adaptiveContext(false)
                .build();

        long[] query = {0xAAAA0000BBBB0001L};
        String textQuery = "Explain neuralNet and vectorDB usage";

        HybridBooleanRag.GuardedRagResult result = r.guardedQuery(query, textQuery);

        assertThat(result.generationAllowed()).isTrue();
        assertThat(result.guardResult().allowed()).isTrue();
        assertThat(result.guardResult().matchedTerms())
                .contains("neuralNet", "vectorDB");
    }

    // ═══ guardedQuery — missing terms → generation blocked ═══

    @Test
    void shouldBlockGenerationWhenTechnicalTermMissingFromContext() {
        long[] query = {0xABCDEF0123456789L};
        String textQuery = "Explain TransformerModel attention mechanism";

        HybridBooleanRag.GuardedRagResult result = rag.guardedQuery(query, textQuery);

        assertThat(result.generationAllowed()).isFalse();
        assertThat(result.guardResult().allowed()).isFalse();
        assertThat(result.guardResult().missingTerms())
                .contains("TransformerModel");
        assertThat(result.guardResult().matchedTerms()).isEmpty();
    }

    @Test
    void shouldBlockGenerationWhenQuotedTermsMissing() {
        denseIndex.add("api_v1_users", new long[]{0xAAAA0000BBBB0000L});
        var r = HybridBooleanRag.builder()
                .index(denseIndex).topK(3).build();

        long[] query = {0xAAAA0000BBBB0000L};
        String textQuery = "Call \"getUserProfile\" endpoint /api/v2/items";

        HybridBooleanRag.GuardedRagResult result = r.guardedQuery(query, textQuery);

        // "getUserProfile" and "/api/v2/items" should be missing
        assertThat(result.generationAllowed()).isFalse();
        assertThat(result.guardResult().missingTerms())
                .contains("getUserProfile", "/api/v2/items")
                .doesNotContain("/api/v1/users"); // not in query
    }

    // ═══ guardedQuery — empty query → guard returns allowed ═══

    @Test
    void shouldAllowGenerationForEmptyTextQuery() {
        long[] query = {0xABCDEF0123456789L};
        String textQuery = "";

        HybridBooleanRag.GuardedRagResult result = rag.guardedQuery(query, textQuery);

        assertThat(result.generationAllowed()).isTrue();
        assertThat(result.guardResult().allowed()).isTrue();
        assertThat(result.guardResult().matchedTerms()).isEmpty();
        assertThat(result.guardResult().missingTerms()).isEmpty();
        assertThat(result.guardResult().confidence()).isEqualTo(1.0);
    }

    // ═══ guardedQuery — non-technical query → guard returns allowed ═══

    @Test
    void shouldAllowGenerationForNonTechnicalQuery() {
        long[] query = {0xABCDEF0123456789L};
        String textQuery = "explain the concept in simple terms";

        HybridBooleanRag.GuardedRagResult result = rag.guardedQuery(query, textQuery);

        assertThat(result.generationAllowed()).isTrue();
        assertThat(result.guardResult().allowed()).isTrue();
        assertThat(result.guardResult().matchedTerms()).isEmpty();
        assertThat(result.guardResult().missingTerms()).isEmpty();
    }

    @Test
    void shouldAllowGenerationForSentenceCaseQuery() {
        long[] query = {0xABCDEF0123456789L};
        // "Use" and "The" should not be treated as technical terms
        // (sentence-initial capitals are excluded)
        String textQuery = "Use the system for retrieval";

        HybridBooleanRag.GuardedRagResult result = rag.guardedQuery(query, textQuery);

        assertThat(result.generationAllowed()).isTrue();
        assertThat(result.guardResult().allowed()).isTrue();
    }

    // ═══ contextChunks — coverage ═══

    @Test
    void shouldReturnContextChunksFromBothStrongAndBorderline() {
        long[] query = {0xABCDEF0123456789L};

        HybridBooleanRag.HybridRagResult result = rag.query(query);

        var chunks = result.contextChunks();
        assertThat(chunks).isNotNull();
        assertThat(chunks).isNotEmpty();
        // "NeuralNetwork" should be in strong matches (exact hamming distance 0)
        assertThat(chunks).contains("NeuralNetwork");
    }

    // ═══ GuardedRagResult record — field access ═══

    @Test
    void shouldGuardResultProvideAllFields() {
        long[] query = {0xABCDEF0123456789L};
        String textQuery = "NeuralNetwork classifier";

        HybridBooleanRag.GuardedRagResult result = rag.guardedQuery(query, textQuery);

        assertThat(result.ragResult()).isNotNull();
        assertThat(result.ragResult().originalQuery()).isEqualTo(query);
        assertThat(result.guardResult()).isNotNull();
        assertThat(result.guardResult().confidence()).isGreaterThan(0.0);
        assertThat(result.generationAllowed()).isTrue();
    }
}
