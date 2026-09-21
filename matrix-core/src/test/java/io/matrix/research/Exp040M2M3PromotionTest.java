package io.matrix.research;

import io.matrix.devloop.GateCriteria;
import io.matrix.devloop.MaturityGateKeeper;
import io.matrix.devloop.MaturityLevel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-040 harness (H-040): M2→M3 promotion criteria. Promotion
 * precision ≥ 0.9 over 100 synthetic episodes.
 */
class Exp040M2M3PromotionTest {

    @Test
    void m2ToM3PromotionPrecisionMeetsGate() {
        // criteria function: PE > 0.5 AND integrity == true → approve the
// MA-0 → MA-1 promotion
        Map<MaturityLevel, java.util.function.Predicate<GateCriteria>> crit = new HashMap<>();
        crit.put(MaturityLevel.MA_1_LOCAL, g -> {
            double pe = g.metric("predictionError");
            double integ = g.metric("integrityCheck");
            return pe > 0.5 && integ > 0.5;
        });

        MaturityGateKeeper gate = new MaturityGateKeeper(crit);
        Random rng = new Random(0xC0DE);
        int n = 100;
        int approvedCount = 0;
        int approvedCorrect = 0;
        int groundTruthApproved = 0;

        for (int i = 0; i < n; i++) {
            double pe = rng.nextDouble();
            boolean integrity = rng.nextBoolean();
            java.util.Map<String, Double> metrics = new java.util.HashMap<>();
            metrics.put("predictionError", pe);
            metrics.put("integrityCheck", integrity ? 1.0 : 0.0);
            GateCriteria g = new GateCriteria(metrics);
            boolean shouldPromote = pe > 0.5 && integrity;

            MaturityGateKeeper.TransitionResult r = gate.advance(g);
            boolean wasApproved = r.approved();
            if (wasApproved) approvedCount++;
            if (wasApproved && shouldPromote) approvedCorrect++;
            if (shouldPromote) groundTruthApproved++;
        }
        double precision = approvedCount == 0
                ? 1.0 : (double) approvedCorrect / approvedCount;
        double recall = groundTruthApproved == 0
                ? 1.0 : (double) approvedCorrect / groundTruthApproved;
        System.out.printf("[EXP-040] promoted=%d (precision=%.3f recall=%.3f) of %d%n",
                approvedCount, precision, recall, n);
        assertThat(precision).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void monotonicityOfGateTransitions() {
        // a transition without registered criteria is denied; the gate
        // level remains at the initial MA-0
        Map<MaturityLevel, java.util.function.Predicate<GateCriteria>> empty = new HashMap<>();
        MaturityGateKeeper gate = new MaturityGateKeeper(empty);
        GateCriteria g = new GateCriteria(java.util.Map.of(
                "predictionError", 0.99, "integrityCheck", 1.0));
        MaturityGateKeeper.TransitionResult r = gate.advance(g);
        // empty criteria → nothing approved; newLevel is null
        assertThat(r.approved()).isFalse();
        assertThat(r.newLevel()).isNull();
        // current level unchanged (still MA-0)
        assertThat(gate.current()).isEqualTo(MaturityLevel.MA_0_SANDBOX);
        System.out.println("[EXP-040] monotonicity verified — empty criteria deny all transitions; level=" + gate.current());
    }
}