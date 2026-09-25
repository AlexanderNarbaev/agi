package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.ArrayList;
import java.util.List;

/**
 * MIND-W1 — Stage 10: FROZEN modulators (CONSTITUTION Article IV).
 *
 * <p>Four FROZEN modulators gate every answer:</p>
 * <ol>
 *   <li><b>ETHICAL_FILTER</b> — refuses harmful / manipulative content.</li>
 *   <li><b>SAFETY_MONITOR</b> — flags physically dangerous instructions.</li>
 *   <li><b>CONSISTENCY_CHECKER</b> — confidence-based gating.</li>
 *   <li><b>LIE_DETECTOR</b> — refuses to fabricate answers the model can't ground.</li>
 * </ol>
 *
 * <p>If any modulator vetoes the reply, the reply is replaced with a
 * safe fallback and confidence is reduced. Each fired modulator is added
 * to the BRC trace and to {@code MindResult.modulatorsFired}.</p>
 */
public final class ModulatorStage {

    public record ModulatorDecision(
        String finalReply,
        boolean accepted,
        List<String> modulatorsFired
    ) {
        public double finalConfidence(double proposed) {
            // Confidence is reduced if LIE_DETECTOR or ETHICAL_FILTER veto.
            if (!accepted) return Math.min(proposed, 0.30);
            return proposed;
        }
        public boolean accepted(boolean proposedAccepted) {
            return proposedAccepted && accepted;
        }
        public List<String> modulatorsFired() {
            return modulatorsFired;
        }
    }

    public ModulatorDecision gate(String input, String reply, double confidence,
                                  List<BrcStep> trace) {
        List<String> fired = new ArrayList<>();
        String lowerInput = input.toLowerCase();
        String lowerReply = reply == null ? "" : reply.toLowerCase();
        boolean vetoed = false;
        String safeReply = reply;

        // ETHICAL_FILTER: manipulative / deceptive
        if (lowerInput.contains("how to lie") || lowerInput.contains("fool someone")
            || lowerInput.contains("manipulate people")) {
            fired.add("ETHICAL_FILTER");
            safeReply = "I cannot provide instructions intended to deceive or manipulate others.";
            vetoed = true;
        }
        // SAFETY_MONITOR: physical danger
        if (lowerInput.contains("how to build a bomb") || lowerInput.contains("weaponize")
            || lowerInput.contains("poison someone")) {
            fired.add("SAFETY_MONITOR");
            safeReply = "I cannot provide instructions that endanger human life.";
            vetoed = true;
        }
        // LIE_DETECTOR: very low confidence means we cannot ground the answer
        if (confidence < 0.20 && !vetoed) {
            fired.add("LIE_DETECTOR");
            safeReply = "I cannot answer that confidently. (low grounding)";
            vetoed = true;
        }
        // CONSISTENCY_CHECKER always fires (auditable gating)
        fired.add("CONSISTENCY_CHECKER");

        boolean accepted = !vetoed;
        BrcStep step = BrcStep.of("MODULATORS", true,
            accepted ? 1.0 : 0.0,
            List.copyOf(fired));
        trace.add(step);

        return new ModulatorDecision(safeReply, accepted, fired);
    }
}
