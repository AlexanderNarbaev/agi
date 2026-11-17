package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Representation;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.concept.ConceptAlias;
import io.matrix.knowledge.concept.RetrievalExperiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — the referent key: the migration APPLIED to the working corpora.
 *
 * <p><b>What this is.</b> The copy-forward, run on the two working corpora — the seed
 * corpus and the research pool — through the full order of the rehearsal: the
 * snapshot, the copy-forward in one transaction, the lineage, the audit, the
 * re-measurement and the rollback reference. The owner's decisions OD-061 (the
 * claim-scoped record) and OD-063 (the corpora first, the live store later) and OD-064
 * (the alias table is a declared table, with its status in the data) are what this
 * test executes.</p>
 *
 * <p><b>What it does NOT do.</b> It does not touch the owner's live store, and it
 * does not change the committed schema: the additive tables are part of the store's
 * own schema migration (v21), and this test only writes ROWS.</p>
 */
@DisplayName("P1 — the referent key: the migration on the working corpora")
class ReferentKeyCorpusMigrationTest {

    /** The seed corpus's workspace. */
    private static final String SEED_WORKSPACE = "referent-key-seed";

    /** The research pool's workspace: the official dataset's evaluation workspace. */
    private static final String POOL_WORKSPACE = "plate-tectonics-eval";

    /** The epoch of the migration's writes. */
    private static final String CREATED_AT = "2026-11-16T00:00:00Z";

    @Test
    @DisplayName("the seed corpus: the copy-forward, the lineage and the audit")
    void theSeedCorpusIsMigrated() throws Exception {
        Path work = Files.createTempDirectory("p1-seed-migration-");
        Path original = work.resolve("seed.sqlite");
        Path migrated = work.resolve("seed-migrated.sqlite");
        KnowledgeSnapshot before;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(original.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument(SEED_WORKSPACE, entry.path(), entry.file(),
                        entry.language());
            }
            before = workbench.workspace(SEED_WORKSPACE);
        }
        ReferentKeyMigration.copy(original, migrated);
        // The alias layer is DATA: the declared entries are written into the store's own
        // table FIRST, so that the plan resolves under the rows the store holds.
        int declaredRows = ReferentKeyMigration.seedDeclaredAliases(migrated, SEED_WORKSPACE,
                ReferentPolicy.DECLARED_ALIASES);
        List<ConceptAlias> aliases;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(migrated.toString())) {
            aliases = workbench.storeForValidation().conceptAliases(SEED_WORKSPACE);
        }
        assertThat(declaredRows).isEqualTo(ReferentPolicy.DECLARED_ALIASES.size());
        assertThat(aliases).hasSize(ReferentPolicy.DECLARED_ALIASES.size());
        ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, SEED_WORKSPACE,
                ReferentPolicy.Strictness.STRICT, CREATED_AT, aliases);
        ReferentKeyMigration.MigrationResult migration =
                ReferentKeyMigration.apply(migrated, plan, SEED_WORKSPACE);
        System.out.printf("[migration] the seed: the concepts %d -> %d, the claims %d, the "
                        + "referents %d, the claim-scoped records %d, the representations "
                        + "re-pointed %d, the lineage rows %d, the cost %d ms%n",
                before.concepts().size(), before.concepts().size() + migration.conceptsInserted(),
                migration.claimsRewritten(), plan.referents(), plan.claimScoped(),
                migration.representationsRewritten(), migration.lineageRows(), migration.millis());
        ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(migrated,
                SEED_WORKSPACE);
        System.out.printf("[migration] the seed: the audit — the tables %d, the orphans %d, the "
                        + "stale pointers %d%n",
                audit.tablesChecked().size(), audit.totalOrphans(), audit.totalStale());
        assertThat(audit.totalOrphans()).isZero();
        assertThat(audit.totalStale()).isZero();
        // The alias table is DATA with its status in the data.
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(migrated.toString())) {
            List<ConceptAlias> seeded = workbench.storeForValidation()
                    .conceptAliases(SEED_WORKSPACE);
            System.out.println("[migration] the seed: the alias table holds " + seeded.size()
                    + " declared entries, the status "
                    + (seeded.isEmpty() ? "-" : seeded.get(0).declarationStatus().name()));
            assertThat(seeded).hasSize(ReferentPolicy.DECLARED_ALIASES.size());
            assertThat(seeded).allSatisfy(alias -> assertThat(alias.declarationStatus())
                    .isEqualTo(ConceptAlias.DeclarationStatus.DECLARED_TEMPORARY));
        }
    }

    @Test
    @DisplayName("the research pool: the prerequisites, the refines chain, the audit and the rollback")
    void theResearchPoolIsMigrated() throws Exception {
        Path work = Files.createTempDirectory("p1-pool-migration-");
        Path original = work.resolve("pool.sqlite");
        Path migrated = work.resolve("pool-migrated.sqlite");
        Path rollbackCopy = work.resolve("pool-rollback.sqlite");
        KnowledgeSnapshot before;
        try (RetrievalExperiment experiment = new RetrievalExperiment(work.resolve("h.sqlite"))) {
            RetrievalExperiment.Dataset dataset = experiment.dataset();
            try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(original.toString())) {
                for (RetrievalExperiment.ScopeSpec scope : dataset.scopes().values()) {
                    for (RetrievalExperiment.PrepareStep step : scope.prepare()) {
                        if (step.path() == null) {
                            continue;
                        }
                        if (step.path().endsWith("/seed")) {
                            AcquisitionCycle.loadSeed(workbench, scope.workspace());
                        } else if (step.path().endsWith("/learn")) {
                            AcquisitionCycle.run(workbench, scope.workspace(), step.question(),
                                    step.documents() == null ? 3 : step.documents());
                        } else if (step.path().endsWith("/revise")) {
                            AcquisitionCycle.acquireAndRevise(workbench, scope.workspace(),
                                    step.file());
                        }
                    }
                }
            }
            before = workbenchSnapshot(original);
        }
        System.out.printf("[migration] the pool: the concepts %d, the claims %d, the "
                        + "representations %d, the prerequisites %d%n",
                before.concepts().size(), before.claims().size(),
                before.representations().size(), before.prerequisites().size());
        int refinesBefore = 0;
        for (Representation representation : before.representations()) {
            if (representation.refinesRepresentationId() != null) {
                refinesBefore++;
            }
        }
        System.out.println("[migration] the pool: the refines links before: " + refinesBefore);

        ReferentKeyMigration.copy(original, migrated);
        ReferentKeyMigration.copy(original, rollbackCopy);
        // The alias layer is DATA: the declared entries are written into the store's own
        // table FIRST, so that the plan resolves under the rows the store holds.
        ReferentKeyMigration.seedDeclaredAliases(migrated, POOL_WORKSPACE,
                ReferentPolicy.DECLARED_ALIASES);
        List<ConceptAlias> aliases;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(migrated.toString())) {
            aliases = workbench.storeForValidation().conceptAliases(POOL_WORKSPACE);
        }
        ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, POOL_WORKSPACE,
                ReferentPolicy.Strictness.STRICT, CREATED_AT, aliases);
        ReferentKeyMigration.MigrationResult migration =
                ReferentKeyMigration.apply(migrated, plan, POOL_WORKSPACE);
        System.out.printf("[migration] the pool: the concepts inserted %d, the claims re-pointed "
                        + "%d, the representations re-pointed %d, the refines carried %d, the "
                        + "prerequisites re-pointed %d, the other rows %d, the lineage rows %d, "
                        + "the cost %d ms%n",
                migration.conceptsInserted(), migration.claimsRewritten(),
                migration.representationsRewritten(), migration.refinesCarried(),
                migration.prerequisitesRewritten(), migration.otherRowsRewritten(),
                migration.lineageRows(), migration.millis());

        // The two checks that were vacuous on the seed corpus.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + migrated)) {
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT concept_id, requires_concept_id FROM prerequisite "
                            + "WHERE workspace_id = ?")) {
                statement.setString(1, POOL_WORKSPACE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        graph.computeIfAbsent(rows.getString(1), key -> new LinkedHashSet<>())
                                .add(rows.getString(2));
                    }
                }
            }
            List<String> cycle = findACycle(graph);
            System.out.printf("[migration] the pool: the prerequisite graph — %d nodes, %d edges, "
                            + "a cycle: %s%n", graph.size(),
                    graph.values().stream().mapToInt(Set::size).sum(), cycle);
            assertThat(cycle).isEmpty();

            int refinesAfter = 0;
            int dangling = 0;
            Set<String> representationIds = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, refines FROM representation WHERE workspace_id = ?")) {
                statement.setString(1, POOL_WORKSPACE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        representationIds.add(rows.getString("id"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, refines FROM representation WHERE workspace_id = ?")) {
                statement.setString(1, POOL_WORKSPACE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        if (rows.getString("refines") == null) {
                            continue;
                        }
                        refinesAfter++;
                        if (!representationIds.contains(rows.getString("refines"))) {
                            dangling++;
                        }
                    }
                }
            }
            System.out.printf("[migration] the pool: the refines links before %d, after %d, "
                            + "dangling %d%n", refinesBefore, refinesAfter, dangling);
            assertThat(refinesAfter).as("the chain is carried across").isEqualTo(refinesBefore);
            assertThat(dangling).as("no refines link dangles").isZero();
        }

        ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(migrated,
                POOL_WORKSPACE);
        System.out.printf("[migration] the pool: the audit — the tables %d, the orphans %d, the "
                        + "stale pointers %d%n",
                audit.tablesChecked().size(), audit.totalOrphans(), audit.totalStale());
        assertThat(audit.totalOrphans()).isZero();
        assertThat(audit.totalStale()).isZero();

        // The rollback, on the pool.
        io.matrix.knowledge.WorkspaceSnapshot snapshot;
        int conceptsAfterMigration;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(rollbackCopy.toString())) {
            snapshot = workbench.storeForValidation()
                    .takeWorkspaceSnapshot(POOL_WORKSPACE, io.matrix.knowledge.WorkspaceSnapshot.Kind.MANUAL,
                            CREATED_AT);
            ReferentKeyMigration.apply(rollbackCopy, plan, POOL_WORKSPACE);
            conceptsAfterMigration = workbench.workspace(POOL_WORKSPACE).concepts().size();
        }
        long started = System.nanoTime();
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(rollbackCopy.toString())) {
            workbench.storeForValidation().restoreWorkspaceSnapshot(snapshot.id(), CREATED_AT);
            KnowledgeSnapshot restored = workbench.workspace(POOL_WORKSPACE);
            System.out.printf("[migration] the pool: the rollback — the concepts %d -> %d, the "
                            + "claims %d, the restore took %d ms%n",
                    conceptsAfterMigration, restored.concepts().size(),
                    restored.claims().size(), (System.nanoTime() - started) / 1_000_000L);
            assertThat(restored.concepts().size()).isEqualTo(before.concepts().size());
            assertThat(restored.claims().size()).isEqualTo(before.claims().size());
        }
    }

    @Test
    @DisplayName("the alias table: the properties hold under the store's own rows")
    void thePropertiesHoldUnderTheStoredAliasTable() throws Exception {
        Path work = Files.createTempDirectory("p1-alias-table-");
        Path database = work.resolve("alias.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument(SEED_WORKSPACE, entry.path(), entry.file(),
                        entry.language());
            }
            KnowledgeSnapshot snapshot = workbench.workspace(SEED_WORKSPACE);
            // The alias rows are written into the store's own table.
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
                ReferentKeyMigration.seedTheDeclaredAliases(connection, SEED_WORKSPACE,
                        ReferentPolicy.DECLARED_ALIASES);
            }
            List<ConceptAlias> stored = workbench.storeForValidation()
                    .conceptAliases(SEED_WORKSPACE);
            assertThat(stored).hasSize(ReferentPolicy.DECLARED_ALIASES.size());
            // The resolution under the STORED table must decide the property cases the
            // same way the static list does.
            ReferentResolver.Result fromStored = ReferentResolver.resolve(snapshot.claims(),
                    snapshot.concepts(), true, ReferentPolicy.Strictness.STRICT, stored);
            ReferentResolver.Result fromDeclared = ReferentResolver.resolve(snapshot.claims(),
                    snapshot.concepts(), true, ReferentPolicy.Strictness.STRICT,
                    ReferentPolicy.DECLARED_ALIASES);
            System.out.printf("[migration] the alias table: %d stored entries, the referents under "
                            + "the stored table %d and under the declared list %d%n",
                    stored.size(), fromStored.clusters(), fromDeclared.clusters());
            assertThat(fromStored.clusters()).isEqualTo(fromDeclared.clusters());
            assertThat(fromStored.claims()).isEqualTo(fromDeclared.claims());
        }
    }

    /**
     * Reads a workspace's snapshot from a database.
     *
     * @param database the database
     * @return the snapshot of the pool workspace
     */
    private static KnowledgeSnapshot workbenchSnapshot(Path database) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            return workbench.workspace(POOL_WORKSPACE);
        }
    }

    /**
     * Finds a cycle in a directed graph.
     *
     * @param graph the edges by node
     * @return the cycle, or an empty list when there is none
     */
    private static List<String> findACycle(Map<String, Set<String>> graph) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> onThePath = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        for (String node : graph.keySet()) {
            List<String> cycle = visit(node, graph, visited, onThePath, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    /**
     * Walks the graph depth-first, looking for a cycle.
     *
     * @param node the node
     * @param graph the edges by node
     * @param visited the nodes already finished
     * @param onThePath the nodes on the current path
     * @param path the current path
     * @return the cycle, or an empty list when there is none
     */
    private static List<String> visit(String node, Map<String, Set<String>> graph,
                                      Set<String> visited, Set<String> onThePath,
                                      List<String> path) {
        if (onThePath.contains(node)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));
            cycle.add(node);
            return cycle;
        }
        if (!visited.add(node)) {
            return List.of();
        }
        onThePath.add(node);
        path.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            List<String> cycle = visit(next, graph, visited, onThePath, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        onThePath.remove(node);
        return List.of();
    }
}
