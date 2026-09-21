package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * RUN 247 — BrainLoopRouter (phase routing).
 *
 * <p>Routes input through the cognitive loop phases based on
 * simple rules. Each phase can be enabled/disabled.
 */
public final class BrainLoopRouter {

    public enum Phase { PERCEPTION, SALIENCY, ATTENTION,
                        DELIBERATION, GATE, ACTION, TRACE }

    private final Map<Phase, Boolean> enabled = new HashMap<>();

    public BrainLoopRouter() {
        for (Phase p : Phase.values()) {
            enabled.put(p, true);
        }
    }

    public boolean isEnabled(Phase p) { return enabled.get(p); }

    public void setEnabled(Phase p, boolean on) {
        enabled.put(p, on);
    }

    public boolean shouldProcess(Phase p) {
        return enabled.getOrDefault(p, true);
    }

    /** All enabled phases that should run. */
    public Phase[] activePhases() {
        return enabled.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toArray(Phase[]::new);
    }
}
