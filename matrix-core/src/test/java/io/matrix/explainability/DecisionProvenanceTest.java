package io.matrix.explainability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionProvenanceTest {

    @Test
    void shouldCreateMinimalProvenance() {
        DecisionProvenance p = DecisionProvenance.of("neuron-1", new long[]{0b101L}, 3, true);

        assertThat(p.decisionId()).isNotBlank();
        assertThat(p.neuronId()).isEqualTo("neuron-1");
        assertThat(p.input()).containsExactly(0b101L);
        assertThat(p.inputK()).isEqualTo(3);
        assertThat(p.output()).isTrue();
        assertThat(p.confidence()).isEqualTo(1.0);
        assertThat(p.timestamp()).isNotNull();
        assertThat(p.hasExplanations()).isFalse();
    }

    @Test
    void shouldRejectInvalidConfidence() {
        assertThatThrownBy(() ->
                DecisionProvenance.of("n", new long[]{0}, 1, true).withConfidence(-0.1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                DecisionProvenance.of("n", new long[]{0}, 1, true).withConfidence(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSerializeToJson() {
        DecisionProvenance p = DecisionProvenance.of("neuron-1", new long[]{7}, 3, false);

        String json = p.toJson();

        assertThat(json).contains("neuron-1");
        assertThat(json).contains("false");
        assertThat(json).startsWith("{");
    }

    @Test
    void shouldDeserializeFromJson() {
        DecisionProvenance original = DecisionProvenance.of("neuron-42", new long[]{0b11L}, 2, true);
        String json = original.toJson();

        DecisionProvenance deserialized = DecisionProvenance.fromJson(json);

        assertThat(deserialized.decisionId()).isEqualTo(original.decisionId());
        assertThat(deserialized.neuronId()).isEqualTo("neuron-42");
        assertThat(deserialized.input()).containsExactly(0b11L);
        assertThat(deserialized.inputK()).isEqualTo(2);
        assertThat(deserialized.output()).isTrue();
    }

    @Test
    void shouldSerializeWithExplanations() {
        DecisionProvenance p = DecisionProvenance.of("n", new long[]{3}, 2, true)
                .withExplanations(
                        List.of(ExplanationPrimitive.P1, ExplanationPrimitive.P11),
                        Map.of("P1_result", "b0=0.5, b1=0.5", "P11_result", "b0_flipped")
                );

        String json = p.toJson();
        DecisionProvenance deserialized = DecisionProvenance.fromJson(json);

        assertThat(deserialized.hasExplanations()).isTrue();
        assertThat(deserialized.explanationPrimitives())
                .containsExactly(ExplanationPrimitive.P1, ExplanationPrimitive.P11);
        assertThat(deserialized.explanationResults())
                .containsEntry("P1_result", "b0=0.5, b1=0.5");
    }

    @Test
    void shouldSupportWithConfidence() {
        DecisionProvenance p = DecisionProvenance.of("n", new long[]{0}, 1, true)
                .withConfidence(0.85);

        assertThat(p.confidence()).isEqualTo(0.85);
    }

    @Test
    void shouldCountSteps() {
        DecisionProvenance p = new DecisionProvenance(
                "id", "neuron", Instant.now(),
                new long[]{0}, 1, List.of(),
                null, 0L,
                List.of(), Map.of("step1", "result1", "step2", "result2"),
                true, 1.0,
                List.of(), Map.of()
        );

        assertThat(p.stepCount()).isEqualTo(2);
    }

    @Test
    void shouldGenerateSummary() {
        DecisionProvenance p = DecisionProvenance.of("n", new long[]{0}, 1, true);

        String summary = p.summary();

        assertThat(summary).contains("DecisionProvenance{");
        assertThat(summary).contains("output=true");
        assertThat(summary).contains("neuron=n");
    }

    @Test
    void shouldHandleNullFields() {
        DecisionProvenance p = new DecisionProvenance(
                null, null, null,
                null, 0, null,
                null, 0L,
                null, null,
                false, 0.5,
                null, null
        );

        assertThat(p.decisionId()).isNotBlank();
        assertThat(p.neuronId()).isEqualTo("unknown");
        assertThat(p.timestamp()).isNotNull();
        assertThat(p.input()).isEmpty();
        assertThat(p.inputLabels()).isEmpty();
        assertThat(p.neuronActivations()).isEmpty();
        assertThat(p.intermediateResults()).isEmpty();
        assertThat(p.explanationPrimitives()).isEmpty();
        assertThat(p.explanationResults()).isEmpty();
    }

    @Test
    void shouldPreserveImmutability() {
        DecisionProvenance p = DecisionProvenance.of("n", new long[]{7}, 3, true);

        // Records are immutable — modifying the copy doesn't affect original
        DecisionProvenance withConf = p.withConfidence(0.5);
        assertThat(p.confidence()).isEqualTo(1.0);
        assertThat(withConf.confidence()).isEqualTo(0.5);
    }
}
