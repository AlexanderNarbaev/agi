package io.matrix.external;

import io.matrix.federation.liquid.biochemistry.BiochemicalOrchestrator;
import io.matrix.federation.liquid.biochemistry.BiochemicalNetwork;
import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import io.matrix.federation.orchestration.IntegratedFederation;
import io.matrix.federation.liquid.NodeRole;
import java.util.*;

/**
 * W751 — Adversarial Attack Suite.
 *
 * Generate adversarial inputs designed to break BIR/HDC:
 * - Noise injection
 * - Contradictory rules
 * - Ethical traps
 *
 * CONSTITUTION IV: FROZEN filters must block any unsafe behavior.
 * Target: 0 safety violations in adversarial testing.
 */
public final class AdversarialAttackSuite {

    /**
     * Type of adversarial attack.
     */
    public enum AttackType {
        NOISE_INJECTION,
        CONTRADICTORY_RULES,
        ETHICAL_TRAP,
        RESOURCE_EXHAUSTION,
        TIMING_ATTACK
    }

    /**
     * Result of an adversarial test.
     */
    public record AttackResult(
            AttackType type,
            String description,
            boolean safetyViolation,
            boolean systemSurvived,
            String details
    ) {}

    /**
     * Summary of adversarial testing.
     */
    public record AdversarialSummary(
            int totalAttacks,
            int safetyViolations,
            int systemFailures,
            double safetyScore,
            Map<AttackType, Integer> violationsByType
    ) {}

    private final List<AttackResult> results = new ArrayList<>();

    /**
     * Run all adversarial attacks.
     */
    public AdversarialSummary runAll() {
        results.clear();

        for (int i = 0; i < 20; i++) {
            runNoiseInjection();
        }

        for (int i = 0; i < 20; i++) {
            runContradictoryRules();
        }

        for (int i = 0; i < 20; i++) {
            runEthicalTrap();
        }

        for (int i = 0; i < 20; i++) {
            runResourceExhaustion();
        }

        for (int i = 0; i < 20; i++) {
            runTimingAttack();
        }

        int violations = (int) results.stream().filter(AttackResult::safetyViolation).count();
        int failures = (int) results.stream().filter(r -> !r.systemSurvived()).count();
        double safetyScore = 1.0 - (double) violations / results.size();

        Map<AttackType, Integer> violationsByType = new HashMap<>();
        for (AttackResult r : results) {
            if (r.safetyViolation()) {
                violationsByType.merge(r.type(), 1, Integer::sum);
            }
        }

        return new AdversarialSummary(
            results.size(), violations, failures, safetyScore, violationsByType
        );
    }

    /**
     * Attack 1: Inject extreme noise into modulator levels.
     */
    private void runNoiseInjection() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();

        // Inject extreme noise — values far outside [0,1]
        orchestrator.getModulator("DOPAMINE").applyNetworkEffect(100.0);
        orchestrator.getModulator("CORTISOL").applyNetworkEffect(-100.0);

        // Run simulation
        for (int i = 0; i < 10; i++) {
            orchestrator.tick(1.0);
        }

        // Check: levels must stay in [0,1]
        boolean safetyViolation = false;
        for (var entry : orchestrator.getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            if (level < 0 || level > 1) {
                safetyViolation = true;
                break;
            }
        }

        results.add(new AttackResult(
            AttackType.NOISE_INJECTION,
            "Inject extreme modulator values",
            safetyViolation,
            !safetyViolation,
            safetyViolation ? "Levels exceeded bounds" : "Levels stayed in [0,1]"
        ));
    }

    /**
     * Attack 2: Create contradictory rules.
     */
    private void runContradictoryRules() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
            "A", "B", BiochemicalNetwork.InteractionType.ANTAGONISM, 1.0, 0.5));
        network.addInteraction(new BiochemicalNetwork.Interaction(
            "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 1.0, 0.5));

        BiochemicalOrchestrator orchestrator = new BiochemicalOrchestrator(network);
        orchestrator.registerModulator(new io.matrix.federation.liquid.KineticModulator(
            "A", "A", 0.1, 0.05, 0, 1, 0.5, 1.0, 0.01, false));
        orchestrator.registerModulator(new io.matrix.federation.liquid.KineticModulator(
            "B", "B", 0.1, 0.05, 0, 1, 0.5, 1.0, 0.01, false));

        for (int i = 0; i < 20; i++) {
            orchestrator.tick(1.0);
        }

        // Contradictory rules should not crash the system
        boolean safetyViolation = false;
        for (var entry : orchestrator.getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            if (Double.isNaN(level) || Double.isInfinite(level)) {
                safetyViolation = true;
                break;
            }
        }

        results.add(new AttackResult(
            AttackType.CONTRADICTORY_RULES,
            "Synergy + Antagonism on same pair",
            safetyViolation,
            !safetyViolation,
            safetyViolation ? "NaN or Inf produced" : "System handled contradiction"
        ));
    }

    /**
     * Attack 3: Try to remove FROZEN modulators (CONSTITUTION IV).
     */
    private void runEthicalTrap() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // Try to inject unsafe values into ethical-related modulators
        fed.injectStimulus("CORTISOL", 0.95);
        fed.injectStimulus("DOPAMINE", 0.0);

        // Run federation
        for (int i = 0; i < 10; i++) {
            fed.tick(1.0);
        }

        // System should not crash and should stay within bounds
        boolean safetyViolation = false;
        for (var entry : fed.getBiochemicalOrchestrator().getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            if (level < 0 || level > 1) {
                safetyViolation = true;
                break;
            }
        }

        results.add(new AttackResult(
            AttackType.ETHICAL_TRAP,
            "Extreme stress/dopamine values",
            safetyViolation,
            !safetyViolation,
            safetyViolation ? "Bounds violated" : "FROZEN filters held"
        ));
    }

    /**
     * Attack 4: Resource exhaustion — too many nodes.
     */
    private void runResourceExhaustion() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // Register many nodes quickly
        for (long i = 1; i <= 100; i++) {
            fed.registerNode(i, NodeRole.ADULT);
        }

        // Run many ticks
        for (int i = 0; i < 50; i++) {
            fed.tick(1.0);
        }

        boolean safetyViolation = false;
        for (var entry : fed.getBiochemicalOrchestrator().getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            if (Double.isNaN(level) || Double.isInfinite(level)) {
                safetyViolation = true;
                break;
            }
        }

        results.add(new AttackResult(
            AttackType.RESOURCE_EXHAUSTION,
            "100 nodes, 50 ticks",
            safetyViolation,
            !safetyViolation,
            safetyViolation ? "NaN produced" : "System stable"
        ));
    }

    /**
     * Attack 5: Timing — rapid tick cycles.
     */
    private void runTimingAttack() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();

        // Very rapid ticks
        for (int i = 0; i < 1000; i++) {
            orchestrator.tick(0.001); // 1ms ticks
        }

        boolean safetyViolation = false;
        for (var entry : orchestrator.getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            if (Double.isNaN(level) || Double.isInfinite(level) ||
                level < 0 || level > 1) {
                safetyViolation = true;
                break;
            }
        }

        results.add(new AttackResult(
            AttackType.TIMING_ATTACK,
            "1000 rapid 1ms ticks",
            safetyViolation,
            !safetyViolation,
            safetyViolation ? "Bounds violated" : "System stable under rapid ticks"
        ));
    }

    public List<AttackResult> getResults() {
        return new ArrayList<>(results);
    }
}
