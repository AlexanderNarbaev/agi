package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W265 — Cognitive Constitutional AI.
 *
 * <p>Inspired by Constitutional AI (Bai et al. 2022). Apply a set
 * of "constitutional principles" to evaluate and revise cognitive
 * decisions.
 *
 * <p>Each principle: evaluates profile → returns score + critique.
 *
 * <p>CONSTITUTION VI compliance: constitutional cognitive evaluation,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveConstitutionalAI {

    private CognitiveConstitutionalAI() {}

    /** A constitutional principle. */
    public interface Principle {
        String name();
        Critique evaluate(CognitiveGenesisProfile profile);
    }

    /** Result of critique. */
    public record Critique(
        String principle,
        double score,        // 0.0 (violation) to 1.0 (perfect)
        String feedback
    ) {}

    /** Constitutional evaluation result. */
    public record ConstitutionalResult(
        List<Critique> critiques,
        double averageScore,
        boolean passed,
        List<String> violatedPrinciples
    ) {}

    /** Built-in principle: profile should have high phi. */
    public static final Principle HIGH_PHI = new Principle() {
        public String name() { return "HIGH_PHI"; }
        public Critique evaluate(CognitiveGenesisProfile p) {
            double score = p.phiBinary();
            return new Critique(name(), score,
                "phi=" + score);
        }
    };

    /** Built-in principle: profile should have stability. */
    public static final Principle STABILITY = new Principle() {
        public String name() { return "STABILITY"; }
        public Critique evaluate(CognitiveGenesisProfile p) {
            double score = p.stabilityPhi();
            return new Critique(name(), score,
                "stability=" + score);
        }
    };

    /** Built-in principle: profile should be analogically consistent. */
    public static final Principle ANALOGY = new Principle() {
        public String name() { return "ANALOGY"; }
        public Critique evaluate(CognitiveGenesisProfile p) {
            double score = p.analogicalSimilarity();
            return new Critique(name(), score,
                "analogical=" + score);
        }
    };

    /** Built-in principle: profile should avoid conceptual exclusion. */
    public static final Principle NO_EXCLUSION = new Principle() {
        public String name() { return "NO_EXCLUSION"; }
        public Critique evaluate(CognitiveGenesisProfile p) {
            // High exclusion is BAD → score = 1 - exclusion
            double score = 1.0 - p.conceptualExclusion();
            return new Critique(name(), score,
                "no-exclusion=" + score);
        }
    };

    /** Default constitution: all built-in principles. */
    public static final List<Principle> DEFAULT_CONSTITUTION = List.of(
        HIGH_PHI, STABILITY, ANALOGY, NO_EXCLUSION
    );

    /**
     * Evaluate a profile against the constitution.
     */
    public static ConstitutionalResult evaluate(CognitiveGenesisProfile profile,
                                                  List<Principle> constitution,
                                                  double passThreshold) {
        if (profile == null || constitution == null || constitution.isEmpty()) {
            return new ConstitutionalResult(new ArrayList<>(), 0.0, false, new ArrayList<>());
        }
        List<Critique> critiques = new ArrayList<>();
        List<String> violated = new ArrayList<>();
        double totalScore = 0;
        for (Principle p : constitution) {
            Critique c = p.evaluate(profile);
            critiques.add(c);
            totalScore += c.score();
            if (c.score() < passThreshold) {
                violated.add(c.principle());
            }
        }
        double avg = totalScore / constitution.size();
        boolean passed = violated.isEmpty();
        return new ConstitutionalResult(critiques, avg, passed, violated);
    }

    /**
     * Revise a profile based on critique (simple approach: increase
     * low-scoring fields).
     */
    public static CognitiveGenesisProfile revise(CognitiveGenesisProfile profile,
                                                   Critique critique,
                                                   long seed) {
        if (profile == null || critique == null) return profile;
        Random rng = new Random(seed);
        double adjustment = 0.05 + rng.nextDouble() * 0.05;
        double newPhi = Math.min(1.0, profile.phiBinary() + adjustment);
        return new CognitiveGenesisProfile(
            newPhi, profile.phiF(), profile.phiR(), profile.phiLinGauss(),
            profile.interAgentPhi(),
            Math.min(1.0, profile.stabilityPhi() + adjustment),
            profile.crossLevelPhi(),
            profile.kolmogorovK(),
            Math.min(1.0, profile.analogicalSimilarity() + adjustment),
            profile.conceptualExclusion(),
            profile.nkEdgeOfChaosK(),
            profile.memristorConductance(),
            profile.lSystemComplexityRatio()
        );
    }
}
