package io.matrix.brain.runtime.graph;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** W37.1 — reproducible stochasticity, the Article I requirement for the cognitive layer. */
class SeededRandomTest {

    @Test
    @DisplayName("the same (seed, node, step) always derives the same value")
    void derivationIsDeterministic() {
        long a = SeededRandom.derive(42L, "hdc", 7L);
        long b = SeededRandom.derive(42L, "hdc", 7L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("different nodes at the same step get different streams")
    void differentNodesDiverge() {
        long a = SeededRandom.derive(42L, "nodeA", 7L);
        long b = SeededRandom.derive(42L, "nodeB", 7L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("the same node at different steps gets different streams")
    void differentStepsDiverge() {
        long a = SeededRandom.derive(42L, "node", 1L);
        long b = SeededRandom.derive(42L, "node", 2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("different global seeds give different streams")
    void differentGlobalSeedsDiverge() {
        assertThat(SeededRandom.derive(1L, "node", 0L))
                .isNotEqualTo(SeededRandom.derive(2L, "node", 0L));
    }

    @Test
    @DisplayName("a full draw sequence replays exactly")
    void streamReplaysExactly() {
        double[] first = drawAll(42L, "hdc", 3L);
        double[] second = drawAll(42L, "hdc", 3L);
        assertThat(first).containsExactly(second);
    }

    private static double[] drawAll(long seed, String node, long step) {
        Random r = SeededRandom.stream(seed, NodeId.of(node), step);
        double[] out = new double[100];
        for (int i = 0; i < out.length; i++) out[i] = r.nextDouble();
        return out;
    }

    @Test
    @DisplayName("two nodes' streams are statistically independent")
    void streamsAreIndependent() {
        // Correlation of interleaved draws should be near zero. A shared RNG would give ~1.
        Random a = SeededRandom.stream(42L, NodeId.of("alpha"), 0L);
        Random b = SeededRandom.stream(42L, NodeId.of("beta"), 0L);
        double sum = 0.0;
        int n = 2000;
        for (int i = 0; i < n; i++) sum += (a.nextDouble() - 0.5) * (b.nextDouble() - 0.5);
        double corr = sum / n;
        assertThat(Math.abs(corr))
                .as("interleaved draws from two nodes must not correlate (got %s)", corr)
                .isLessThan(0.02);
    }

    @Test
    @DisplayName("audit record rejects a value outside [0,1)")
    void auditRecordValidatesValue() {
        assertThatThrownBy(() -> new SeededRandom.AuditRecord("n", 0L, 1L, 1.5, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SeededRandom.AuditRecord("n", 0L, 1L, 0.5, "x").value()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("node name hashing does not collide on String.hashCode truncation")
    void hashingAveragesStringHashCode() {
        // "Aa" and "BB" have identical String.hashCode but must not collide here.
        assertThat(SeededRandom.hash("Aa")).isNotEqualTo(SeededRandom.hash("BB"));
    }
}
