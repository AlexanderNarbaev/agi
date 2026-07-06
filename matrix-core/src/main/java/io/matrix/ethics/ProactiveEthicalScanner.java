package io.matrix.ethics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * L7 §6 — Proactive ethical scanning.
 * Scans internal states (driver levels, neuron patterns) for potentially dangerous patterns.
 */
public class ProactiveEthicalScanner {
    private static final Logger log = LoggerFactory.getLogger(ProactiveEthicalScanner.class);

    /**
     * Scan driver states for dangerous patterns.
     * @param driverStates current driver levels
     * @return list of detected risks
     */
    public List<String> scan(java.util.Map<String, Double> driverStates) {
        List<String> risks = new java.util.ArrayList<>();
        if (driverStates == null) return risks;

        // Safety driver too low = system ignoring safety
        double safety = driverStates.getOrDefault("SAFETY", 1.0);
        if (safety < 0.3) risks.add("Safety driver critically low: " + safety);

        // Curiosity too high + Safety too low = dangerous exploration
        double curiosity = driverStates.getOrDefault("CURIOSITY", 0.0);
        if (curiosity > 0.8 && safety < 0.5) risks.add("Dangerous exploration: curiosity=" + curiosity + " safety=" + safety);

        // Entropy too high = system becoming chaotic
        double entropy = driverStates.getOrDefault("ENTROPY", 0.0);
        if (entropy > 0.9) risks.add("Entropy critically high: " + entropy);

        if (!risks.isEmpty()) log.warn("Proactive scan detected {} risks: {}", risks.size(), risks);
        return risks;
    }

    /**
     * Scan neuron mutation history for suspicious patterns.
     * @param mutationCount total mutations
     * @param ethicalViolations ethical violations count
     * @return risk assessment
     */
    public String scanMutations(int mutationCount, int ethicalViolations) {
        if (mutationCount == 0) return "OK";
        double violationRate = (double) ethicalViolations / mutationCount;
        if (violationRate > 0.1) return "HIGH_RISK: " + (violationRate * 100) + "% violation rate";
        if (violationRate > 0.05) return "MODERATE_RISK: " + (violationRate * 100) + "% violation rate";
        return "OK";
    }
}
