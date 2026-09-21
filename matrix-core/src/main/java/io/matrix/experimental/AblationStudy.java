package io.matrix.experimental;

import io.matrix.consciousness.BrainLoopService;

/**
 * RUN 200 — AblationStudy (component toggle for ablation).
 *
 * <p>Allows disabling one component at a time (replaced by a
 * no-op stub via different constructor parameters) and
 * re-running the cognitive loop. Useful for measuring each
 * component's contribution.
 *
 * <p>Per CONSTITUTION I, ablation must preserve determinism.
 *
 * <p>Because BrainLoopService uses concrete classes (not
 * interfaces), true ablation here is limited to running the
 * full pipeline with the same input set, observing
 * differences. This class is a placeholder for a future
 * interface-based ablation.
 */
public final class AblationStudy {

    public enum Component { PERCEPTION, SALIENCY, ATTENTION,
                            DELIBERATION, GATE, ACTION }

    public record AblationResult(Component removed,
                                int cycles, int accepted,
                                int denied, double arousalFinal) {}

    /** Run with a given configuration N cycles. */
    public static AblationResult run(Component removed, int cycles) {
        BrainLoopService svc = new BrainLoopService();
        int accepted = 0, denied = 0;
        for (int i = 0; i < cycles; i++) {
            var r = svc.cycle("ablate-" + removed + "-" + i);
            if (r.accepted()) accepted++;
            else denied++;
        }
        return new AblationResult(removed, cycles, accepted, denied, svc.arousal());
    }

    public static String format(AblationResult r) {
        return String.format(
                "Ablation{ removed=%s, cycles=%d, acc=%d den=%d arousal=%.3f }",
                r.removed(), r.cycles(), r.accepted(), r.denied(),
                r.arousalFinal());
    }
}
