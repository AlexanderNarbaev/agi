package io.matrix.explainability;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLoggerTest {

    @Test
    void shouldHaveDefaultRetentionPeriod() {
        AuditLogger logger = new AuditLogger();

        assertThat(logger.retentionPeriod()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    void shouldAllowCustomRetentionPeriod() {
        AuditLogger logger = new AuditLogger();
        logger.setRetentionPeriod(Duration.ofDays(30));

        assertThat(logger.retentionPeriod()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void shouldRejectNullRetentionPeriod() {
        AuditLogger logger = new AuditLogger();

        assertThatThrownBy(() -> logger.setRetentionPeriod(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeRetentionPeriod() {
        AuditLogger logger = new AuditLogger();

        assertThatThrownBy(() -> logger.setRetentionPeriod(Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroRetentionPeriod() {
        AuditLogger logger = new AuditLogger();

        assertThatThrownBy(() -> logger.setRetentionPeriod(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateProvenanceForLogging() {
        DecisionProvenance provenance = DecisionProvenance.of("neuron-1", new long[]{3}, 2, true);

        assertThat(provenance.decisionId()).isNotBlank();
        assertThat(provenance.toJson()).isNotBlank();
    }

    @Test
    void shouldSerializeProvenanceForAudit() {
        DecisionProvenance provenance = DecisionProvenance.of("neuron-42", new long[]{0b111L}, 3, false)
                .withConfidence(0.95);

        String json = provenance.toJson();
        DecisionProvenance restored = DecisionProvenance.fromJson(json);

        assertThat(restored.neuronId()).isEqualTo("neuron-42");
        assertThat(restored.output()).isFalse();
        assertThat(restored.confidence()).isEqualTo(0.95);
    }
}
