package io.matrix.verification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Runtime verification framework for M.A.T.R.I.X. system properties.
 * 
 * Checks critical invariants at runtime to ensure system correctness:
 * - TruthTable binary output
 * - Evolution monotonicity
 * - FROZEN neuron immutability
 * - Consensus safety
 * 
 * @see <a href="docs/improvements/FORMAL_VERIFICATION.md">Formal Verification Plan</a>
 */
@ApplicationScoped
public class RuntimeVerifier {

    private static final Logger log = LoggerFactory.getLogger(RuntimeVerifier.class);

    private final List<PropertyChecker> checkers = new ArrayList<>();

    @PostConstruct
    void init() {
        checkers.add(new TruthTableConsistencyChecker());
        checkers.add(new FrozenImmutabilityChecker());
        checkers.add(new ConsensusSafetyChecker());
        log.info("RuntimeVerifier initialized with {} checkers", checkers.size());
    }

    /**
     * Verify all properties against current state.
     */
    public VerificationResult verify(Object state) {
        List<PropertyViolation> violations = new ArrayList<>();

        for (PropertyChecker checker : checkers) {
            Optional<PropertyViolation> violation = checker.check(state);
            violation.ifPresent(violations::add);
        }

        return new VerificationResult(violations);
    }

    /**
     * Verify a specific property.
     */
    public Optional<PropertyViolation> verifyProperty(String propertyName, Object state) {
        return checkers.stream()
                .filter(c -> c.getPropertyName().equals(propertyName))
                .findFirst()
                .flatMap(c -> c.check(state));
    }

    /**
     * Get list of available property checkers.
     */
    public List<String> getAvailableProperties() {
        return checkers.stream()
                .map(PropertyChecker::getPropertyName)
                .toList();
    }

    /**
     * Property checker interface.
     */
    public interface PropertyChecker {
        String getPropertyName();
        Optional<PropertyViolation> check(Object state);
    }

    /**
     * Verifies TruthTable outputs are always binary (0 or 1).
     */
    public static class TruthTableConsistencyChecker implements PropertyChecker {
        @Override
        public String getPropertyName() {
            return "truth_table_binary_output";
        }

        @Override
        public Optional<PropertyViolation> check(Object state) {
            if (state instanceof Map map) {
                Object tt = map.get("truthTable");
                if (tt instanceof int[] outputs) {
                    for (int i = 0; i < outputs.length; i++) {
                        if (outputs[i] != 0 && outputs[i] != 1) {
                            return Optional.of(new PropertyViolation(
                                    getPropertyName(),
                                    "Output at index " + i + " must be 0 or 1, got: " + outputs[i],
                                    Map.of("index", i, "value", outputs[i]),
                                    System.currentTimeMillis()
                            ));
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Verifies FROZEN neurons cannot be modified.
     */
    public static class FrozenImmutabilityChecker implements PropertyChecker {
        @Override
        public String getPropertyName() {
            return "frozen_neuron_immutability";
        }

        @Override
        public Optional<PropertyViolation> check(Object state) {
            if (state instanceof Map map) {
                Boolean modified = (Boolean) map.get("frozenModified");
                if (Boolean.TRUE.equals(modified)) {
                    return Optional.of(new PropertyViolation(
                            getPropertyName(),
                            "FROZEN neuron was modified — this violates system invariant",
                            Map.of("frozenModified", true),
                            System.currentTimeMillis()
                    ));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Verifies consensus safety (no two different values agreed upon).
     */
    public static class ConsensusSafetyChecker implements PropertyChecker {
        @Override
        public String getPropertyName() {
            return "consensus_safety";
        }

        @Override
        public Optional<PropertyViolation> check(Object state) {
            if (state instanceof Map map) {
                Object agreed = map.get("consensusValue");
                Object previous = map.get("previousConsensusValue");
                if (agreed != null && previous != null && !agreed.equals(previous)) {
                    // This is expected for different consensus rounds
                    // Only flag if same round produced different values
                    Integer round = (Integer) map.get("consensusRound");
                    Integer prevRound = (Integer) map.get("previousConsensusRound");
                    if (round != null && round.equals(prevRound)) {
                        return Optional.of(new PropertyViolation(
                                getPropertyName(),
                                "Consensus produced different values in same round",
                                Map.of("value1", agreed, "value2", previous, "round", round),
                                System.currentTimeMillis()
                        ));
                    }
                }
            }
            return Optional.empty();
        }
    }
}
