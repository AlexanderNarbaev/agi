package io.matrix.knowledge;

import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.SourceSpanDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HARD REVIEW — the THIRD gate of the definition rule (P1): the definitional predicate.
 */
class HardReviewThirdGateTest {

    private static final String WORKSPACE = "third-gate";
    private static final String NAME = "A trench";

    @TempDir
    Path directory;

    /** What the corrected rule produces on the seed corpus. */
    @Test
    @DisplayName("the definitions of the third-gate rule on the seed corpus")
    void theDefinitionsOfTheThirdGate() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        Map<String, String> names = new HashMap<>();
        for (Concept concept : bench.store().concepts(WORKSPACE)) {
            names.put(concept.id(), concept.name());
        }
        List<ConceptDefinition> definitions = bench.store().conceptDefinitions(WORKSPACE);
        System.out.println("### the third gate - the seed corpus");
        System.out.println("  concepts=" + bench.store().concepts(WORKSPACE).size()
                + " definitions=" + definitions.size());
        int index = 0;
        for (ConceptDefinition definition : definitions) {
            index++;
            System.out.println("  [" + index + "] " + names.getOrDefault(definition.conceptId(),
                    definition.conceptId()));
            System.out.println("       -> " + definition.definition());
        }
    }

    /** The gate attacked with my own sentences. */
    @Test
    @DisplayName("the definitional-predicate gate, attacked with my own sentences")
    void theGateAttacked() {
        System.out.println("### the definitional-predicate gate");
        String[][] probes = {
                {"a definition (genus)", "A trench is a long narrow depression in the ocean floor."},
                {"a definition (identity)", "A trench is one of the deepest parts of the ocean floor."},
                {"a definition (classification)", "A trench is of two types: active and inactive."},
                {"a definition (naming)", "A trench is called a deep-ocean trench."},
                {"a definition (composition)", "A trench consists of two plates in contact."},
                {"an action", "A trench is carved by subducting plates."},
                {"a comparison", "A trench is deeper than a canyon."},
                {"a measurement", "A trench is 100 km long."},
                {"an origin", "A trench is formed at a subduction zone."},
                {"a state", "A trench is still open."},
                {"a reported belief", "A trench is thought to be a long narrow depression."},
                {"a discourse", "A trench is the key to understanding island arcs."},
                {"a discourse (the corpus's own fourth case)",
                        "The trenches are the key to understanding how island arcs such as the "
                                + "Marianas and the Aleutian Islands have formed and why they "
                                + "experience numerous strong earthquakes."},
                {"a definition with 'any'", "A trench is any long narrow depression at a boundary."},
                {"a definition with 'each'", "A trench is each narrow depression at a boundary."},
        };
        for (String[] probe : probes) {
            System.out.println("  " + (SourceSpanDefinitions.statesDefinition(probe[1], NAME)
                    ? "ACCEPTED" : "rejected") + " :: " + probe[0] + " :: " + probe[1]);
        }
    }

    /** The negation gate, re-checked and extended. */
    @Test
    @DisplayName("the negation gate, re-checked")
    void theNegationGate() {
        System.out.println("### the negation gate");
        String[][] probes = {
                {"the plain denial", "A trench is not a long narrow depression."},
                {"the qualified denial", "A trench is largely not a long narrow depression."},
                {"the adverbial denial", "A trench is sometimes not a deep-ocean trench."},
                {"the negation after a multi-word copula", "A trench is defined as not a canyon."},
                {"the negation after 'is called'", "A trench is called not a canyon."},
                {"the negation after 'consists of'", "A trench consists of not two plates."},
                {"the negation separated by an adverbial",
                        "A trench is, in the strict sense, not a canyon."},
                {"the double negation", "A trench is not not a canyon."},
                {"the negation in the complement", "A trench is a feature that is not a canyon."},
                {"the negation far away", "A trench is a long narrow depression that is not a canyon."},
                {"a definition", "A trench is a long narrow depression."},
        };
        for (String[] probe : probes) {
            System.out.println("  " + (SourceSpanDefinitions.statesDefinition(probe[1], NAME)
                    ? "ACCEPTED" : "rejected") + " :: " + probe[0] + " :: " + probe[1]);
        }
    }
}
