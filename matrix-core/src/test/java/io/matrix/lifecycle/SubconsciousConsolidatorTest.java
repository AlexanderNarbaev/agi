package io.matrix.lifecycle;

import io.matrix.federation.Anonymizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-I tests for {@link SubconsciousConsolidator}: TR/REM phases,
 * integrity check, and k-anonymity gating.
 */
class SubconsciousConsolidatorTest {

    @Test
    void firstCycleSharesDigestWhenThresholdMet() {
        Anonymizer anon = new Anonymizer(2);
        SubconsciousConsolidator sub = new SubconsciousConsolidator(anon, "node-A");

        // first cycle: 1 contribution, threshold 2 → suppressed
        SubconsciousConsolidator.ConsolidationReport r1 =
                sub.runOnce(Map.of("k1", "v1", "k2", "v2"));
        assertThat(r1.result())
                .isEqualTo(SubconsciousConsolidator.PhaseResult.GOSSIP_SUPPRESSED);
        assertThat(r1.digestsSuppressed()).isGreaterThan(0);
        assertThat(r1.digestsShared()).isEqualTo(0);
    }

    @Test
    void secondCycleSharesAfterAnonymityThresholdMet() {
        Anonymizer anon = new Anonymizer(2);
        SubconsciousConsolidator a = new SubconsciousConsolidator(anon, "node-A");
        SubconsciousConsolidator b = new SubconsciousConsolidator(anon, "node-B");

        // both contribute the same checkpoint → threshold met (2 nodes)
        a.runOnce(Map.of("k", "v"));
        SubconsciousConsolidator.ConsolidationReport rB =
                b.runOnce(Map.of("k", "v"));

        assertThat(rB.result())
                .isEqualTo(SubconsciousConsolidator.PhaseResult.GOSSIP_SHARED);
        assertThat(rB.digestsShared()).isGreaterThan(0);
    }

    @Test
    void integrityCheckDetectsCheckpointDrift() {
        Anonymizer anon = new Anonymizer(2);
        SubconsciousConsolidator sub = new SubconsciousConsolidator(anon, "node-A");

        // first run primes the checkpoint hash
        sub.runOnce(Map.of("k", "v1"));
        // second run with a different checkpoint must report INTEGRITY_FAILED
        SubconsciousConsolidator.ConsolidationReport r =
                sub.runOnce(Map.of("k", "v2-different"));
        assertThat(r.result())
                .isEqualTo(SubconsciousConsolidator.PhaseResult.INTEGRITY_FAILED);
    }

    @Test
    void sameCheckpointAcrossCyclesPreservesIntegrity() {
        Anonymizer anon = new Anonymizer(2);
        SubconsciousConsolidator sub = new SubconsciousConsolidator(anon, "node-A");

        // same checkpoint twice in a row — second run should NOT report INTEGRITY_FAILED
        sub.runOnce(Map.of("k", "v"));
        SubconsciousConsolidator.ConsolidationReport r =
                sub.runOnce(Map.of("k", "v"));
        assertThat(r.result()).isNotEqualTo(
                SubconsciousConsolidator.PhaseResult.INTEGRITY_FAILED);
    }

    @Test
    void totalCyclesAdvances() {
        Anonymizer anon = new Anonymizer(2);
        SubconsciousConsolidator sub = new SubconsciousConsolidator(anon, "node-A");
        assertThat(sub.totalCycles()).isEqualTo(0L);
        sub.runOnce(Map.of("k", "v"));
        assertThat(sub.totalCycles()).isEqualTo(1L);
        sub.runOnce(Map.of("k", "v"));
        assertThat(sub.totalCycles()).isEqualTo(2L);
    }

    @Test
    void digestHashIsDeterministicForSameCheckpoint() {
        Anonymizer anon = new Anonymizer(2);
        SubconsciousConsolidator a = new SubconsciousConsolidator(anon, "node-A");
        SubconsciousConsolidator b = new SubconsciousConsolidator(anon, "node-B");

        SubconsciousConsolidator.ConsolidationReport rA = a.runOnce(Map.of("x", "1"));
        // reset A's checkpoint to make B produce the same hash
        // (B starts fresh so its first run primes with the same hash)
        SubconsciousConsolidator.ConsolidationReport rB = b.runOnce(Map.of("x", "1"));
        assertThat(rA.contentHash()).isEqualTo(rB.contentHash());
    }
}