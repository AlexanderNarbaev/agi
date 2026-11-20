package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1 — the concept ids' stability, as a comparison and not as a count.
 *
 * <p><b>Why this test exists.</b> The acceptance item A8 says that no concept id may
 * change. A count of the concepts cannot show that: a corpus of 326 concepts before and
 * 326 after is exactly what a rename would also produce. The claim has to be backed by a
 * comparison — every id, one by one, before and after.</p>
 *
 * <p><b>What is compared.</b> The seed corpus is imported document by document. The first
 * document's import writes the definitions of the concepts it touched; the second
 * document's import writes more of them. The ids of the concepts are captured after the
 * first import and compared with the ids after the second: every id that was there is
 * still there, under the same name, and every id in the later workspace is still the
 * digest of that name.</p>
 *
 * <p><b>Where it runs.</b> Over a temporary database. The owner's store is never
 * touched: the import writes into a copy that this test creates and removes.</p>
 */
class ConceptIdentityStabilityTest {

    /** The workspace the corpus is imported into. */
    private static final String WORKSPACE = "seed";

    @TempDir
    Path directory;

    /** The workbenches this test opened, so that every one of them is closed. */
    private final List<KnowledgeWorkbench> workbenches = new ArrayList<>();

    @AfterEach
    void tearDown() {
        workbenches.forEach(KnowledgeWorkbench::close);
    }

    @Test
    @DisplayName("A8 — no concept id changed: the ids before and after, one by one")
    void noConceptIdChanged() throws IOException {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("seed.sqlite")
                        .toString());
        workbenches.add(bench);
        List<ClasspathCorpus.Entry> entries = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS)
                .entries();
        assertThat(entries)
                .as("the seed corpus is more than one document, so the comparison is between "
                        + "two imports and not between one import and itself")
                .hasSizeGreaterThan(1);

        // THE IDS BEFORE: the first document's import, definitions and all
        ClasspathCorpus.Entry first = entries.get(0);
        bench.importDocument(WORKSPACE, first.path(), first.file(), first.language());
        Map<String, String> before = conceptIds(bench);

        // THE IDS AFTER: the second document's import writes more definitions
        ClasspathCorpus.Entry second = entries.get(1);
        bench.importDocument(WORKSPACE, second.path(), second.file(), second.language());
        Map<String, String> after = conceptIds(bench);

        assertThat(before)
                .as("the first import must have produced concepts")
                .isNotEmpty();
        assertThat(after.keySet())
                .as("every id of the earlier workspace is still there")
                .containsAll(before.keySet());
        for (Map.Entry<String, String> entry : before.entrySet()) {
            assertThat(after.get(entry.getKey()))
                    .as("the concept %s kept its id and its name", entry.getValue())
                    .isEqualTo(entry.getValue());
        }
        System.out.println("### the concept ids: " + before.size() + " before the second "
                + "import, " + after.size() + " after; every earlier id is still there and "
                + "every id is still the digest of its name");
        for (Map.Entry<String, String> entry : after.entrySet()) {
            assertThat(entry.getKey())
                    .as("the id of the concept %s is still the digest of its name",
                            entry.getValue())
                    .isEqualTo(Ids.digest(WORKSPACE + "|concept|"
                            + entry.getValue().strip().toLowerCase(Locale.ROOT)));
        }
    }

    /** The ids of the workspace's concepts, and the names they belong to. */
    private static Map<String, String> conceptIds(KnowledgeWorkbench bench) {
        Map<String, String> byId = new LinkedHashMap<>();
        bench.workspace(WORKSPACE).concepts().forEach(concept ->
                byId.put(concept.id(), concept.name()));
        return byId;
    }
}
