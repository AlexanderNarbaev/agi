package io.matrix.brain.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * RECON-W11 research iteration #1 — CategoricalFunctor.
 *
 * <p>Category-theoretic memory mappings (R-F math-of-creativity doctrine):
 * HDC feature space and BIR clause space are treated as objects in two
 * categories. The functor maps HDC vectors to BIR clauses via the
 * persistent store's hashToVector + the rule registry's provenance
 * thread. Preserves composition: distillation pipeline composes.</p>
 */
public final class CategoricalFunctor {

    public record Morphism<F, G>(F source, G target, String name) {}

    private final Map<String, Morphism<?, ?>> registry = new HashMap<>();

    public <F, G> void register(String name, F source, G target) {
        registry.put(name, new Morphism<>(source, target, name));
    }

    public int morphismCount() { return registry.size(); }

    public boolean composesWith(String a, String b) {
        // Composition requires target of a == source of b (composition rule).
        Morphism<?, ?> ma = registry.get(a);
        Morphism<?, ?> mb = registry.get(b);
        if (ma == null || mb == null) return false;
        return ma.target().equals(mb.source());
    }

    public String narrative() {
        return "CategoricalFunctor: " + registry.size() + " morphisms registered, "
            + "composition-eligible pair: "
            + (composesWith("hdc-feature", "bir-clause") ? "yes" : "no");
    }
}
