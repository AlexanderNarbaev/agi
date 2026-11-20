package io.matrix.knowledge;

import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HARD REVIEW — P1, the stability of the concept ids.
 *
 * <p>The concept id is the digest of the concept's own name, so an id is stable exactly as
 * long as the name is. This probe records every id with its name, imports again, and
 * compares the two sets ID BY ID.</p>
 */
class HardReviewConceptIdStabilityTest {

    private static final String WORKSPACE = "id-stability";

    @TempDir
    Path directory;

    @Test
    @DisplayName("the concept ids, compared one by one across a re-import")
    void theIdsAcrossAReImport() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        List<ClasspathCorpus.Entry> entries = seed.entries();

        bench.importDocument(WORKSPACE, entries.get(0).path(), entries.get(0).file(),
                entries.get(0).language());
        Map<String, String> before = ids(bench);

        bench.importDocument(WORKSPACE, entries.get(1).path(), entries.get(1).file(),
                entries.get(1).language());
        Map<String, String> after = ids(bench);

        System.out.println("### the concept ids across the imports");
        System.out.println("  after the first document: " + before.size() + " concepts");
        System.out.println("  after the second document: " + after.size() + " concepts");

        int vanished = 0;
        int renamed = 0;
        int sameName = 0;
        List<String> examples = new ArrayList<>();
        for (Map.Entry<String, String> entry : before.entrySet()) {
            String now = after.get(entry.getKey());
            if (now == null) {
                vanished++;
                if (examples.size() < 5) {
                    examples.add("  VANISHED: " + entry.getKey() + " :: " + entry.getValue());
                }
                continue;
            }
            if (now.equals(entry.getValue())) {
                sameName++;
            } else {
                renamed++;
                if (examples.size() < 5) {
                    examples.add("  RENAMED: " + entry.getKey() + " :: " + entry.getValue()
                            + " -> " + now);
                }
            }
        }
        System.out.println("  the earlier ids still present: " + (before.size() - vanished)
                + " of " + before.size());
        System.out.println("  of those, the same name: " + sameName + ", a different name: " + renamed);
        System.out.println("  ids that disappeared: " + vanished);
        for (String example : examples) {
            System.out.println(example);
        }
        System.out.println("  the ids that are the digest of their own name: " + digestNamed(bench)
                + " of " + after.size());
    }

    /** The concept ids and their names. */
    private static Map<String, String> ids(KnowledgeWorkbench bench) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (Concept concept : bench.store().concepts(WORKSPACE)) {
            ids.put(concept.id(), concept.name());
        }
        return ids;
    }

    /** How many of the concepts carry the id that is the digest of their own name. */
    private static int digestNamed(KnowledgeWorkbench bench) {
        int named = 0;
        for (Concept concept : bench.store().concepts(WORKSPACE)) {
            if (io.matrix.knowledge.Ids.digest(WORKSPACE + "|concept|"
                    + concept.name().strip().toLowerCase(java.util.Locale.ROOT))
                    .equals(concept.id())) {
                named++;
            }
        }
        return named;
    }
}
