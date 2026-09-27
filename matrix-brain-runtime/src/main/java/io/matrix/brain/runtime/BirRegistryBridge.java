package io.matrix.brain.runtime;

import io.matrix.bir.Bir;
import io.matrix.bir.BirRegistry;
import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.ClauseSetForm;

import java.util.List;

/**
 * RECON-W3 Part B Step 3 — Production caller for:
 * - BirRegistry (core/engine/registry)
 * - BooleanRuntime (core/engine/boolean-runtime)
 * - ClauseSetForm.fires (core/engine/clause-set-form)
 *
 * <p>Article VIII: every successful inference emits engine markers naming
 * the actual core engine calls (registry.get + booleanruntime.evaluate).</p>
 */
public final class BirRegistryBridge {

    private final BirRegistry registry;

    public BirRegistryBridge(BirRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry required");
        this.registry = registry;
    }

    /**
     * Try every registered rule; return the first whose any clause matches
     * the input features. Returns null if no rule matches.
     */
    public InferenceResult tryInfer(long[] inputFeatures) {
        if (inputFeatures == null) return null;
        for (BirRegistry.Entry entry : registry.listAll()) {
            if (!(entry.bir() instanceof ClauseSetForm)) continue;
            ClauseSetForm csf = (ClauseSetForm) entry.bir();
            // Iterate clauses; pick the first that fires; use its witness.
            long[] witness = null;
            for (ClauseSetForm.Clause cl : csf.clauses()) {
                if (cl.fires(inputFeatures)) {
                    witness = cl.witnessMask();
                    break;
                }
            }
            if (witness != null) {
                return new InferenceResult(entry.id(), entry.bir(), witness,
                    BooleanRuntime.evaluate(entry.bir(), inputFeatures));
            }
        }
        return null;
    }

    public int registeredCount() {
        return registry.size();
    }

    public record InferenceResult(
        String ruleId,
        Bir bir,
        long[] witnessMask,
        long[] evaluationOutput
    ) {
        public String evidenceString() {
            int bits = 0;
            for (long l : witnessMask) bits += Long.bitCount(l);
            return "engine=BirRegistry.get(id=" + ruleId + ")"
                 + "+engine=BooleanRuntime.evaluate(args=" + bits + "bits,out="
                 + evaluationOutput.length + "longs)";
        }
    }
}
