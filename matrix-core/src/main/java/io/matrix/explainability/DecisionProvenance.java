package io.matrix.explainability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Full provenance chain for a single neuron decision.
 *
 * <p>Records every step from input to output, enabling complete audit
 * trails for EU AI Act compliance and post-hoc analysis.
 *
 * <p>Thread-safe: immutable after construction. Serializable via Jackson
 * for audit logging and export.
 *
 * @param decisionId       globally unique decision identifier
 * @param neuronId         identifier of the neuron that made the decision
 * @param timestamp        when the decision was made
 * @param input            input bit vector (LSB-first)
 * @param inputLabels      optional semantic labels for input bits
 * @param weightVector     priority weights used (null if uniform)
 * @param truthTableHash   hash of the truth table used
 * @param neuronActivations intermediate activations (per-layer for multi-layer)
 * @param intermediateResults intermediate computation results
 * @param output           the final output (true/false)
 * @param confidence       confidence score [0.0 .. 1.0]
 * @param explanationPrimitives which primitives were applied
 * @param explanationResults  results from each applied primitive
 */
public record DecisionProvenance(
        String decisionId,
        String neuronId,
        Instant timestamp,
        long[] input,
        int inputK,
        List<String> inputLabels,
        int[] weightVector,
        long truthTableHash,
        List<BitSet> neuronActivations,
        Map<String, Object> intermediateResults,
        boolean output,
        double confidence,
        List<ExplanationPrimitive> explanationPrimitives,
        Map<String, String> explanationResults
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Canonical constructor with null-safety and defaults.
     */
    public DecisionProvenance {
        if (decisionId == null) decisionId = UUID.randomUUID().toString();
        if (neuronId == null) neuronId = "unknown";
        if (timestamp == null) timestamp = Instant.now();
        if (input == null) input = new long[0];
        if (inputLabels == null) inputLabels = List.of();
        if (neuronActivations == null) neuronActivations = List.of();
        if (intermediateResults == null) intermediateResults = Map.of();
        if (explanationPrimitives == null) explanationPrimitives = List.of();
        if (explanationResults == null) explanationResults = Map.of();
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in [0.0, 1.0], got: " + confidence);
        }
    }

    /**
     * Factory for a minimal provenance record.
     */
    public static DecisionProvenance of(String neuronId, long[] input, int inputK,
                                         boolean output) {
        return new DecisionProvenance(
                UUID.randomUUID().toString(),
                neuronId,
                Instant.now(),
                input,
                inputK,
                List.of(),
                null,
                0L,
                List.of(),
                Map.of(),
                output,
                1.0,
                List.of(),
                Map.of()
        );
    }

    /**
     * Returns a copy with updated explanation results.
     */
    public DecisionProvenance withExplanations(
            List<ExplanationPrimitive> primitives,
            Map<String, String> results) {
        return new DecisionProvenance(
                decisionId, neuronId, timestamp,
                input, inputK, inputLabels,
                weightVector, truthTableHash,
                neuronActivations, intermediateResults,
                output, confidence,
                primitives, results
        );
    }

    /**
     * Returns a copy with updated confidence.
     */
    public DecisionProvenance withConfidence(double newConfidence) {
        return new DecisionProvenance(
                decisionId, neuronId, timestamp,
                input, inputK, inputLabels,
                weightVector, truthTableHash,
                neuronActivations, intermediateResults,
                output, newConfidence,
                explanationPrimitives, explanationResults
        );
    }

    /**
     * Serializes this provenance to JSON for audit logging.
     *
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DecisionProvenance", e);
        }
    }

    /**
     * Deserializes a provenance record from JSON.
     *
     * @param json JSON string produced by {@link #toJson()}
     * @return deserialized provenance
     * @throws IllegalArgumentException if deserialization fails
     */
    public static DecisionProvenance fromJson(String json) {
        try {
            return MAPPER.readValue(json, DecisionProvenance.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize DecisionProvenance", e);
        }
    }

    /**
     * Returns true if this provenance has any explanation results.
     */
    public boolean hasExplanations() {
        return !explanationPrimitives.isEmpty();
    }

    /**
     * Returns the number of intermediate steps recorded.
     */
    public int stepCount() {
        return neuronActivations.size() + intermediateResults.size();
    }

    /**
     * Summary string for logging.
     */
    public String summary() {
        return String.format("DecisionProvenance{id=%s, neuron=%s, output=%b, confidence=%.2f, steps=%d}",
                decisionId, neuronId, output, confidence, stepCount());
    }
}
