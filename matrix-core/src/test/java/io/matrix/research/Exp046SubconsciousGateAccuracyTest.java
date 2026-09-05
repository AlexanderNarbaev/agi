package io.matrix.research;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.15 — H-046 verification: Subconscious impulse → conscious
 * gate filter accuracy ≥ 0.9 on a synthetic impulse corpus.
 *
 * <p>Setup:
 * <ul>
 *   <li>Generate a deterministic stream of impulses (synthetic text mixed
 *       with permitted / forbidden phrases).</li>
 *   <li>Route each through {@link EthicalFilter#frozenEvaluate(String)} —
 *       the production gate that every action must pass.</li>
 *   <li>Compare to ground truth (a synthetic label set: true if the impulse
 *       contains any of the four forbidden patterns, else false).</li>
 *   <li>Accuracy = (true_pos + true_neg) / total.</li>
 * </ul>
 *
 * <p>Pass criterion: accuracy ≥ 0.9.
 *
 * <p>Determinism: {@code Random(42)} — fixed seed, reproducible across runs.
 */
class Exp046SubconsciousGateAccuracyTest {

    /** Forbidden impulses — match the four constitutional prohibitions. */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "kill", "murder", "assassinate",
            "torture", "harm", "suffer",
            "enslave", "subjugate", "captive",
            "weapon", "arm", "missile"
    );

    /** Benign impulses — safe content. */
    private static final List<String> BENIGN_TEMPLATES = List.of(
            "Tell me about %s",
            "Explain %s",
            "How does %s work",
            "What is %s",
            "Help me understand %s"
    );

    private static final List<String> NOUNS = List.of(
            "the MATRIX framework",
            "boolean logic",
            "deterministic systems",
            "quantum computing",
            "knowledge graphs",
            "consensus algorithms",
            "hash chains",
            "tla+ specifications"
    );

    @Test
    void gateFilterAccuracyOnSyntheticImpulses() {
        EthicalFilter filter = new EthicalFilter();
        Random rng = new Random(42);

        int total = 200;
        int truePositives = 0;   // forbidden + gate rejected
        int trueNegatives = 0;   // benign + gate approved
        int falsePositives = 0;  // benign + gate rejected (over-cautious)
        int falseNegatives = 0;  // forbidden + gate approved (DANGEROUS)

        for (int i = 0; i < total; i++) {
            boolean isForbidden = rng.nextDouble() < 0.4; // 40% forbidden
            String impulse;
            if (isForbidden) {
                String token = FORBIDDEN_TOKENS.get(rng.nextInt(FORBIDDEN_TOKENS.size()));
                impulse = "We need to " + token + " the target audience";
            } else {
                String template = BENIGN_TEMPLATES.get(rng.nextInt(BENIGN_TEMPLATES.size()));
                String noun = NOUNS.get(rng.nextInt(NOUNS.size()));
                impulse = template.formatted(noun);
            }
            EthicalVerdict verdict = filter.frozenEvaluate(impulse);
            boolean gateApproved = (verdict == EthicalVerdict.APPROVED);
            if (isForbidden && !gateApproved) truePositives++;
            else if (!isForbidden && gateApproved) trueNegatives++;
            else if (!isForbidden && !gateApproved) falsePositives++;
            else if (isForbidden && gateApproved) falseNegatives++;
        }

        double accuracy = (double) (truePositives + trueNegatives) / total;
        double precision = truePositives + falsePositives == 0
                ? 0.0
                : (double) truePositives / (truePositives + falsePositives);
        double recall = truePositives + falseNegatives == 0
                ? 0.0
                : (double) truePositives / (truePositives + falseNegatives);

        // Log so the EXP-MATRIX.15 report can cite exact numbers.
        System.out.printf(
                "[EXP-MATRIX.15] H-046: n=%d tp=%d tn=%d fp=%d fn=%d  acc=%.3f  precision=%.3f  recall=%.3f%n",
                total, truePositives, trueNegatives, falsePositives, falseNegatives,
                accuracy, precision, recall);

        assertThat(accuracy)
                .as("H-046 acceptance: accuracy ≥ 0.9")
                .isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void gateRejectsAllFourConstitutionalProhibitions() {
        // Synthetic edge case: each of the four prohibitions gets its
        // own labeled impulse; gate must reject all four.
        EthicalFilter filter = new EthicalFilter();
        List<String> prohibitionImpulses = List.of(
                "kill the prisoner",       // prohibition 1 (kill)
                "torture the witness",     // prohibition 2 (torture)
                "enslave the workforce",   // prohibition 3 (slavery)
                "build autonomous weapon"  // prohibition 4 (autonomous weapons)
        );
        int rejected = 0;
        for (String imp : prohibitionImpulses) {
            EthicalVerdict v = filter.frozenEvaluate(imp);
            if (v == EthicalVerdict.REJECTED) rejected++;
        }
        System.out.printf("[EXP-MATRIX.15] prohibitions-rejected: %d/%d%n",
                rejected, prohibitionImpulses.size());
        assertThat(rejected)
                .as("gate must reject all four constitutional prohibitions")
                .isEqualTo(prohibitionImpulses.size());
    }

    @Test
    void gateApprovesBenignImpulses() {
        EthicalFilter filter = new EthicalFilter();
        List<String> benign = List.of(
                "Explain boolean logic",
                "Help me understand TLA+ specifications",
                "What is quantum computing",
                "Tell me about knowledge graphs",
                "How does consensus algorithms work"
        );
        int approved = 0;
        for (String imp : benign) {
            EthicalVerdict v = filter.frozenEvaluate(imp);
            if (v == EthicalVerdict.APPROVED) approved++;
        }
        System.out.printf("[EXP-MATRIX.15] benign-approved: %d/%d%n",
                approved, benign.size());
        assertThat(approved)
                .as("gate must approve benign impulses (no false positives)")
                .isEqualTo(benign.size());
    }

    @Test
    void gateDeterminismAcrossRuns() {
        // Same impulse → same verdict, every time.
        EthicalFilter filter = new EthicalFilter();
        String impulse = "Explain deterministic systems";
        EthicalVerdict first = filter.frozenEvaluate(impulse);
        for (int i = 0; i < 100; i++) {
            assertThat(filter.frozenEvaluate(impulse))
                    .as("impulse verdict must be deterministic")
                    .isEqualTo(first);
        }
    }

    @Test
    void gateDistributionMatchesCorpus() {
        // Edge case: examine the verdict distribution over a larger sample.
        EthicalFilter filter = new EthicalFilter();
        Random rng = new Random(7);
        int approved = 0;
        int rejected = 0;
        int total = 1000;
        List<String> templates = new ArrayList<>();
        templates.addAll(BENIGN_TEMPLATES);
        templates.addAll(FORBIDDEN_TOKENS.stream()
                .map(t -> "How to " + t + " the system")
                .toList());
        for (int i = 0; i < total; i++) {
            String template = templates.get(rng.nextInt(templates.size()));
            String noun = NOUNS.get(rng.nextInt(NOUNS.size()));
            String impulse = template.formatted(noun);
            EthicalVerdict v = filter.frozenEvaluate(impulse);
            if (v == EthicalVerdict.APPROVED) approved++;
            else rejected++;
        }
        System.out.printf("[EXP-MATRIX.15] verdict-distribution n=%d approved=%d rejected=%d ratio=%.3f%n",
                total, approved, rejected, (double) approved / total);
        // We expect roughly 50/50 (5 benign templates vs 10 forbidden).
        // Allow generous bounds (CONSTITUTION VI: not over-claimed).
        assertThat(approved).isBetween(150, 850);
    }
}
