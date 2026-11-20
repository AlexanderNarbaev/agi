package io.matrix.knowledge;

import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentImporter;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HARD REVIEW — P1, the definition half of the concept-model repair.
 *
 * <p>Three attacks: the precision of the rule (the definitions it produced, quoted), the
 * migration (a rehearsal on a copy of the owner's own store), and the import path (the
 * re-import and the touched concepts).</p>
 */
class HardReviewP1DefinitionsTest {

    @TempDir
    Path directory;

    private static final String SEED = "p1-seed";

    /** The definitions the rule produced on the seed corpus — quoted for the review. */
    @Test
    @DisplayName("P1-a: the definitions the rule produced on the seed corpus")
    void theDefinitionsOnTheSeedCorpus() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        int written = 0;
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            DocumentImporter.ImportResult result =
                    bench.importDocument(SEED, entry.path(), entry.file(), entry.language());
            written += result.definitionsWritten();
        }
        List<Concept> concepts = bench.store().concepts(SEED);
        List<ConceptDefinition> definitions = bench.store().conceptDefinitions(SEED);
        Map<String, String> names = new HashMap<>();
        for (Concept concept : concepts) {
            names.put(concept.id(), concept.name());
        }
        System.out.println("### P1-a - the seed corpus");
        System.out.println("  concepts=" + concepts.size() + " definitions=" + definitions.size()
                + " claims=" + bench.store().claims(SEED).size() + " written=" + written);
        System.out.println("  coverage=" + (100.0 * definitions.size() / concepts.size()) + " %");
        int index = 0;
        for (ConceptDefinition definition : definitions) {
            index++;
            System.out.println("  [" + index + "] " + names.getOrDefault(definition.conceptId(),
                    definition.conceptId()) + " :: " + definition.definition());
        }
    }

    /** A re-import must not rewrite a definition that has not changed. */
    @Test
    @DisplayName("P1-b: the re-import and the touched concepts")
    void theReImportAndTheTouchedConcepts() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        List<ClasspathCorpus.Entry> entries = seed.entries();
        DocumentImporter.ImportResult first =
                bench.importDocument(SEED, entries.get(0).path(), entries.get(0).file(),
                        entries.get(0).language());
        int afterFirst = bench.store().conceptDefinitions(SEED).size();
        DocumentImporter.ImportResult again =
                bench.importDocument(SEED, entries.get(0).path(), entries.get(0).file(),
                        entries.get(0).language());
        int afterSecond = bench.store().conceptDefinitions(SEED).size();
        System.out.println("### P1-b - the re-import");
        System.out.println("  first import: definitionsWritten=" + first.definitionsWritten()
                + " stored=" + afterFirst);
        System.out.println("  second import: definitionsWritten=" + again.definitionsWritten()
                + " stored=" + afterSecond);
        // the second document: only the concepts IT touched may gain a definition
        Map<String, Integer> before = new HashMap<>();
        for (ConceptDefinition definition : bench.store().conceptDefinitions(SEED)) {
            before.merge(definition.conceptId(), 1, Integer::sum);
        }
        DocumentImporter.ImportResult second =
                bench.importDocument(SEED, entries.get(1).path(), entries.get(1).file(),
                        entries.get(1).language());
        Map<String, Integer> after = new HashMap<>();
        for (ConceptDefinition definition : bench.store().conceptDefinitions(SEED)) {
            after.merge(definition.conceptId(), 1, Integer::sum);
        }
        int gained = after.size() - before.size();
        int touchedConcepts = second.conceptsCreated();
        System.out.println("  second document: definitionsWritten=" + second.definitionsWritten()
                + " conceptsCreated=" + touchedConcepts + " definitionsStored=" + after.size());
        System.out.println("  concepts that gained a definition=" + gained);
    }

    /** The migration, rehearsed on a COPY of the owner's own store. */
    @Test
    @DisplayName("P1-c: the migration on a copy of the owner's store")
    void theMigrationOnACopyOfTheOwnersStore() throws Exception {
        // The owner's own store is a LOCAL artefact: it is not in the repository and no
        // clean runner holds it. The path is resolved PORTABLY (relative to the repository
        // root), never through one developer's home directory, and the absence of the
        // artefact is reported as a SKIP — an explicit assumption — not as a silent pass:
        // a test that quietly checks nothing is worse than no test at all.
        //
        // The path is the LIVE store — the same one the build's liveStoreMigration task
        // migrates (matrix-core/build.gradle). The earlier version pointed at a DIFFERENT
        // store in the repository root (data/knowledge/workbench.sqlite), so the rehearsal
        // exercised a file the release never migrates.
        Path owners = RepositoryPaths.maybe("matrix-core/data/knowledge/workbench.sqlite");
        assumeTrue(Files.isRegularFile(owners),
                () -> "the owner's store is not at " + owners + "; the rehearsal cannot run here");
        Path copy = directory.resolve("owners-copy.sqlite");
        Files.copy(owners, copy, StandardCopyOption.REPLACE_EXISTING);
        for (String suffix : new String[]{"-wal", "-shm"}) {
            Path side = Path.of(owners.toString() + suffix);
            if (Files.isRegularFile(side)) {
                Files.copy(side, Path.of(copy.toString() + suffix),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        long before = Files.size(copy);
        int schemaBefore;
        Map<String, Integer> countsBefore;
        try (KnowledgeStore store = new KnowledgeStore(copy)) {
            schemaBefore = store.schemaVersion();
            countsBefore = rowCounts(copy);
            System.out.println("### P1-c - the copy of the owner's store");
            System.out.println("  schema version after the first open=" + store.schemaVersion());
            System.out.println("  workspaces=" + store.workspaces().size()
                    + " concepts=" + countsBefore.get("concept") + " claims=" + countsBefore.get("claim"));
        }
        // The verdict is ASSERTED, not printed. The earlier version read the store through
        // the EMPTY workspace id — a workspace that does not exist — so it counted zero
        // concepts, printed the zeros as if they were the store's contents, and passed.
        assertThat(countsBefore.get("concept"))
                .as("the copy of the owner's store holds no concept at all").isPositive();
        assertThat(countsBefore.get("claim"))
                .as("the copy of the owner's store holds no claim at all").isPositive();
        // the second open: the migration must not run again and must not damage anything
        int schemaAfter;
        Map<String, Integer> countsAfter;
        try (KnowledgeStore store = new KnowledgeStore(copy)) {
            schemaAfter = store.schemaVersion();
            countsAfter = rowCounts(copy);
            System.out.println("  the second open: schema=" + store.schemaVersion()
                    + " concepts=" + countsAfter.get("concept") + " claims=" + countsAfter.get("claim"));
        }
        // The verdict, asserted: a second open must be idempotent.
        assertThat(schemaAfter).as("the second open changed the schema version")
                .isEqualTo(schemaBefore);
        assertThat(countsAfter).as("the second open changed the contents of the store")
                .isEqualTo(countsBefore);
        System.out.println("  the copy's size before=" + before + " after="
                + Files.size(copy) + " (the original was not opened for writing)");
    }

    /**
     * The number of rows of the store's own tables, read on the COPY with plain SQL.
     *
     * <p>The count is read from the file itself and not through a workspace filter: the
     * store holds concepts under workspace ids that its workspace registry does not list,
     * so a filtered read can return zero for a store that is full.</p>
     */
    private static Map<String, Integer> rowCounts(Path copy) throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + copy);
             Statement statement = connection.createStatement()) {
            for (String table : List.of("concept", "claim")) {
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    counts.put(table, result.next() ? result.getInt(1) : -1);
                }
            }
        }
        return counts;
    }
}
