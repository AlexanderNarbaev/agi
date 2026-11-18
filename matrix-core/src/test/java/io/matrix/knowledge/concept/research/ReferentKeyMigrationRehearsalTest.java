package io.matrix.knowledge.concept.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.WorkspaceSnapshot;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.concept.RetrievalExperiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — the referent key: the MIGRATION REHEARSAL.
 *
 * <p><b>What this is.</b> The rehearsal of the copy-forward that the owner's decisions
 * make possible — OD-061 (the claim-scoped record: a claim with no concept keeps its own
 * text as the retrievable unit, additively, and {@code concept_id} stays
 * {@code NOT NULL}) and OD-062 (the STRICT operating point).
 *
 * <p><b>Where it runs.</b> On <b>copies</b>. The owner's store is never opened for
 * writing: the rehearsal builds a temporary workspace with the production importer,
 * takes the store's own snapshot, and then works on copies taken with SQLite's
 * {@code VACUUM INTO} (the store runs WAL, so a byte copy of the main file would miss
 * the committed rows that still live in the write-ahead log).
 *
 * <p><b>What it proves.</b>
 * <ol>
 *   <li>the copy-forward: every claim that resolves to a referent gets that referent,
 *       every claim that does not becomes a claim-scoped record, and <b>nothing is
 *       deleted</b> — the old concept rows stay, marked superseded by the lineage;</li>
 *   <li>the lineage: {@code concept_lineage(old_id, new_id, reason, created_at)} and the
 *       claim lineage beside it — the claim's id does <b>not</b> change, so the claims,
 *       the evidence and the proposition-based golds survive;</li>
 *   <li>the representation ids are recomputed (they embed the concept id) and the
 *       {@code refines} chain is carried across;</li>
 *   <li>the prerequisite graph is still acyclic after the copy-forward;</li>
 *   <li>the <b>orphan audit</b> — the audit {@code validate} cannot do, because SQLite
 *       declares no foreign key — reports <b>zero</b> on the correct copy and
 *       <b>non-zero</b> on a deliberately broken copy;</li>
 *   <li>the rollback: the store's own snapshot restores the workspace;</li>
 *   <li>the re-measurement on the migrated copy.</li>
 * </ol>
 *
 * <p><b>What it does NOT do.</b> It does not migrate the owner's store and it does not
 * change the committed schema: the additive tables exist only in the copies.
 * </p>
 */
@DisplayName("P1 — the referent key: the migration rehearsal")
class ReferentKeyMigrationRehearsalTest {

    /** The workspace of the rehearsal. */
    private static final String WORKSPACE = "referent-key-rehearsal";

    /** The seed corpus: the two licensed documents of the measured baseline. */
    private static final String SEED_CORPUS = AcquisitionCycle.SEED_CORPUS;

    /** The epoch of the rehearsal's writes. */
    private static final String CREATED_AT = "2026-11-16T00:00:00Z";

    /** The official evaluation's dataset. */
    private static final String OFFICIAL_DATASET =
            "matrix-core/src/main/resources/epistemic-eval/cases.json";

    /** The frozen held-out set. */
    private static final String HELD_OUT_SET = "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    /** The workspace of the held-out measurement. */
    private static final String HELD_OUT_WORKSPACE = "heldout-referent-key";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("the rehearsal: the copy-forward, the lineage, the audit and the rollback")
    void theRehearsalRuns() throws Exception {
        Path work = Files.createTempDirectory("p1-rehearsal-");
        Path original = work.resolve("original.sqlite");
        Path migrated = work.resolve("migrated.sqlite");
        Path broken = work.resolve("broken.sqlite");
        Path rollbackCopy = work.resolve("rollback.sqlite");

        // ── 1. the workspace, built by the production importer ─────────────────
        KnowledgeSnapshot before;
        WorkspaceSnapshot snapshot;
        long snapshotMillis;
        long sizeBefore;
        long sizeAfter;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(original.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            before = workbench.workspace(WORKSPACE);
            sizeBefore = ReferentKeyMigration.sizeOf(original);
            long started = System.nanoTime();
            snapshot = workbench.storeForValidation()
                    .takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, CREATED_AT);
            snapshotMillis = (System.nanoTime() - started) / 1_000_000L;
            sizeAfter = ReferentKeyMigration.sizeOf(original);
        }
        System.out.printf("[rehearsal] the workspace: concepts=%d claims=%d sources=%d "
                        + "representations=%d prerequisites=%d%n",
                before.concepts().size(), before.claims().size(), before.sources().size(),
                before.representations().size(), before.prerequisites().size());
        System.out.printf("[rehearsal] the snapshot: id=%s kind=%s concepts=%d claims=%d "
                        + "cost=%d ms, the database grew %d bytes (%d -> %d)%n",
                snapshot.id(), snapshot.kind(), snapshot.conceptCount(), snapshot.claimCount(),
                snapshotMillis, sizeAfter - sizeBefore, sizeBefore, sizeAfter);
        assertThat(snapshot.conceptCount()).isEqualTo(before.concepts().size());

        // ── 2. the plan ────────────────────────────────────────────────────────
        ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, WORKSPACE,
                ReferentPolicy.Strictness.STRICT, CREATED_AT);
        System.out.printf("[rehearsal] the plan (STRICT): referents=%d claim-scoped records=%d "
                        + "rejected claims=%d, the new concept rows=%d, the lineage rows=%d%n",
                plan.referents(), plan.claimScoped(), plan.rejected(),
                plan.newConcepts().size(), plan.conceptLineage().size());
        assertThat(plan.referents()).isPositive();
        assertThat(plan.claimScoped()).isPositive();

        // ── 3. the copies ──────────────────────────────────────────────────────
        ReferentKeyMigration.copy(original, migrated);
        ReferentKeyMigration.copy(original, broken);
        ReferentKeyMigration.copy(original, rollbackCopy);
        assertThat(ReferentKeyMigration.sizeOf(migrated)).isPositive();

        Map<String, Integer> tablesBefore = rowCounts(migrated);

        // ── 4. the copy-forward ───────────────────────────────────────────────
        ReferentKeyMigration.MigrationResult migration =
                ReferentKeyMigration.apply(migrated, plan, WORKSPACE);
        System.out.printf("[rehearsal] the copy-forward: concepts inserted=%d, claims "
                        + "re-pointed=%d, representations re-pointed=%d, refines carried=%d, "
                        + "prerequisites re-pointed=%d, other rows re-pointed=%d, lineage rows=%d, "
                        + "representation collisions=%d, prerequisite collapses=%d, "
                        + "prerequisite self-loops=%d, cost=%d ms%n",
                migration.conceptsInserted(), migration.claimsRewritten(),
                migration.representationsRewritten(), migration.refinesCarried(),
                migration.prerequisitesRewritten(), migration.otherRowsRewritten(),
                migration.lineageRows(), migration.representationCollisions(),
                migration.prerequisiteCollisions(), migration.prerequisiteSelfLoopsDropped(),
                migration.millis());
        assertThat(migration.claimsRewritten()).isEqualTo(before.claims().size());

        Map<String, Integer> tablesAfter = rowCounts(migrated);
        System.out.println("[rehearsal] the tables before: " + tablesBefore);
        System.out.println("[rehearsal] the tables after:  " + tablesAfter);
        assertThat(tablesAfter.get("concept"))
                .isEqualTo(tablesBefore.get("concept") + plan.newConcepts().size());
        assertThat(tablesAfter.get("claim")).isEqualTo(tablesBefore.get("claim"));

        // ── 5. the lineage ─────────────────────────────────────────────────────
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + migrated);
                Statement statement = connection.createStatement()) {
            assertThat(scalar(statement, "SELECT COUNT(*) FROM "
                    + ReferentKeyMigration.TABLE_CONCEPT_LINEAGE)).isPositive();
            assertThat(scalar(statement, "SELECT COUNT(*) FROM "
                    + ReferentKeyMigration.TABLE_CLAIM_LINEAGE)).isPositive();
            assertThat(scalar(statement, "SELECT COUNT(*) FROM "
                    + ReferentKeyMigration.TABLE_CLAIM_SCOPED_RECORD)).isEqualTo(plan.claimScoped());
            // The claim ids do NOT change: the lineage re-points the claim's concept,
            // the claim itself keeps its id.
            int changedClaims = 0;
            try (PreparedStatement lineage = connection.prepareStatement(
                    "SELECT claim_id, old_concept_id, new_concept_id FROM "
                            + ReferentKeyMigration.TABLE_CLAIM_LINEAGE);
                    PreparedStatement claims = connection.prepareStatement(
                            "SELECT id, concept_id FROM claim WHERE workspace_id = ?")) {
                claims.setString(1, WORKSPACE);
                Map<String, String> conceptByClaim = new LinkedHashMap<>();
                try (ResultSet rows = claims.executeQuery()) {
                    while (rows.next()) {
                        conceptByClaim.put(rows.getString(1), rows.getString(2));
                    }
                }
                try (ResultSet rows = lineage.executeQuery()) {
                    while (rows.next()) {
                        String claimId = rows.getString(1);
                        String newConceptId = rows.getString(3);
                        if (newConceptId.equals(conceptByClaim.get(claimId))) {
                            changedClaims++;
                        }
                    }
                }
            }
            System.out.printf("[rehearsal] the lineage: %d of the %d claims now point at the "
                            + "concept the lineage names%n", changedClaims,
                    scalar(statement, "SELECT COUNT(*) FROM "
                            + ReferentKeyMigration.TABLE_CLAIM_LINEAGE));
            assertThat(changedClaims).isEqualTo(before.claims().size());
            // The old concept rows are NOT deleted: they are marked superseded.
            try (PreparedStatement concepts = connection.prepareStatement(
                    "SELECT COUNT(*) FROM concept WHERE workspace_id = ?")) {
                concepts.setString(1, WORKSPACE);
                try (ResultSet rows = concepts.executeQuery()) {
                    assertThat(rows.next() ? rows.getInt(1) : 0)
                            .isEqualTo(tablesBefore.get("concept") + plan.newConcepts().size());
                }
            }
        }

        // ── 6. the representation ids and the refines chain ────────────────────
        List<String> representationsBefore = new ArrayList<>();
        for (io.matrix.knowledge.Representation representation : before.representations()) {
            representationsBefore.add(representation.id());
        }
        int refinesBefore = 0;
        for (io.matrix.knowledge.Representation representation : before.representations()) {
            if (representation.refinesRepresentationId() != null) {
                refinesBefore++;
            }
        }
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(migrated.toString())) {
            KnowledgeSnapshot after = workbench.workspace(WORKSPACE);
            Set<String> oldIds = new LinkedHashSet<>(representationsBefore);
            int kept = 0;
            int changed = 0;
            int refinesAfter = 0;
            int refinesDangling = 0;
            Set<String> newIds = new LinkedHashSet<>();
            for (io.matrix.knowledge.Representation representation : after.representations()) {
                newIds.add(representation.id());
                if (oldIds.contains(representation.id())) {
                    kept++;
                } else {
                    changed++;
                }
                if (representation.refinesRepresentationId() != null) {
                    refinesAfter++;
                    if (!newIds.contains(representation.refinesRepresentationId())
                            && !oldIds.contains(representation.refinesRepresentationId())) {
                        refinesDangling++;
                    }
                }
            }
            System.out.printf("[rehearsal] the representations: before=%d (the old ids kept=%d, "
                            + "recomputed=%d), the refines links before=%d after=%d dangling=%d%n",
                    before.representations().size(), kept, changed, refinesBefore, refinesAfter,
                    refinesDangling);
            assertThat(changed).as("the representation ids embed the concept id").isPositive();
            assertThat(refinesDangling).as("the refines chain is carried across").isZero();
        }

        // ── 6a. the definitions' coverage under the key (A2) ──────────────────
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + migrated);
                Statement statement = connection.createStatement()) {
            int referentsWithADefinition = scalar(statement,
                    "SELECT COUNT(DISTINCT c.id) FROM concept c JOIN concept_definition d "
                            + "ON d.concept_id = c.id AND d.workspace_id = c.workspace_id "
                            + "WHERE c.workspace_id = '" + WORKSPACE + "'");
            int definitions = scalar(statement, "SELECT COUNT(*) FROM concept_definition WHERE "
                    + "workspace_id = '" + WORKSPACE + "'");
            int merged = scalar(statement, "SELECT COUNT(*) FROM "
                    + ReferentKeyMigration.TABLE_DEFINITION_MERGE);
            System.out.printf("[rehearsal] the definitions after the copy-forward: %d "
                            + "definitions, %d of the %d referents carry one (%.4f), the merged "
                            + "ones recorded: %d%n",
                    definitions, referentsWithADefinition, plan.referents(),
                    plan.referents() == 0 ? 0
                            : (double) referentsWithADefinition / plan.referents(), merged);
        }

        // ── 7. the prerequisite graph's acyclicity ────────────────────────────
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + migrated)) {
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT concept_id, requires_concept_id FROM prerequisite "
                            + "WHERE workspace_id = ?")) {
                statement.setString(1, WORKSPACE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        graph.computeIfAbsent(rows.getString(1), key -> new LinkedHashSet<>())
                                .add(rows.getString(2));
                    }
                }
            }
            List<String> cycle = findACycle(graph);
            System.out.printf("[rehearsal] the prerequisite graph: %d nodes, %d edges, a cycle: %s%n",
                    graph.size(), graph.values().stream().mapToInt(Set::size).sum(), cycle);
            assertThat(cycle).as("the prerequisite graph must stay acyclic").isEmpty();
        }

        // ── 8. the orphan audit: zero on the correct copy ──────────────────────
        ReferentKeyMigration.OrphanAudit clean =
                ReferentKeyMigration.audit(migrated, WORKSPACE);
        System.out.printf("[rehearsal] the audit (the correct copy): the tables=%d, the orphans=%d, "
                        + "the stale pointers=%d%n",
                clean.tablesChecked().size(), clean.totalOrphans(), clean.totalStale());
        System.out.println("[rehearsal] the audit's per-table orphans: " + clean.orphansByTable());
        System.out.println("[rehearsal] the audit's per-table stale: " + clean.staleByTable());
        assertThat(clean.tablesChecked().size())
                .as("the audit walks every table that references the concept id")
                .isGreaterThanOrEqualTo(10);
        assertThat(clean.totalOrphans()).as("no row may reference a concept that is gone")
                .isZero();
        assertThat(clean.totalStale()).as("no row may still point at a superseded concept")
                .isZero();

        // ── 9. the audit on a deliberately broken copy ─────────────────────────
        // The broken copy is migrated FIRST and broken afterwards: the audit must see
        // the damage the copy-forward would have prevented.
        ReferentKeyMigration.apply(broken, plan, WORKSPACE);
        breakTheCopy(broken);
        ReferentKeyMigration.OrphanAudit damaged =
                ReferentKeyMigration.audit(broken, WORKSPACE);
        System.out.printf("[rehearsal] the audit (the broken copy): the orphans=%d, the stale "
                        + "pointers=%d, the tables that found them=%s%n",
                damaged.totalOrphans(), damaged.totalStale(),
                damaged.orphansByTable().entrySet().stream()
                        .filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey).toList());
        assertThat(damaged.totalOrphans()).as("the audit must SEE a broken copy").isPositive();
        assertThat(damaged.totalStale()).as("the audit must SEE a stale pointer").isPositive();

        // ── 10. the rollback ───────────────────────────────────────────────────
        WorkspaceSnapshot rollbackSnapshot;
        int conceptsBeforeRestore;
        int claimsBeforeRestore;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(rollbackCopy.toString())) {
            rollbackSnapshot = workbench.storeForValidation()
                    .takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, CREATED_AT);
            // Something is changed on the copy: the migration.
            ReferentKeyMigration.apply(rollbackCopy, plan, WORKSPACE);
            KnowledgeSnapshot changed = workbench.workspace(WORKSPACE);
            conceptsBeforeRestore = changed.concepts().size();
            claimsBeforeRestore = changed.claims().size();
        }
        long restoreStarted = System.nanoTime();
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(rollbackCopy.toString())) {
            workbench.storeForValidation()
                    .restoreWorkspaceSnapshot(rollbackSnapshot.id(), CREATED_AT);
            KnowledgeSnapshot restored = workbench.workspace(WORKSPACE);
            System.out.printf("[rehearsal] the rollback: the snapshot=%s, the concepts %d -> %d, "
                            + "the claims %d -> %d, the restore took %d ms%n",
                    rollbackSnapshot.id(), conceptsBeforeRestore, restored.concepts().size(),
                    claimsBeforeRestore, restored.claims().size(),
                    (System.nanoTime() - restoreStarted) / 1_000_000L);
            assertThat(restored.concepts().size())
                    .as("the restore brings the workspace back")
                    .isEqualTo(before.concepts().size());
            assertThat(restored.claims().size())
                    .as("the restore brings the claims back")
                    .isEqualTo(before.claims().size());
        }
    }

    @Test
    @DisplayName("the re-measurement on the migrated copy")
    void theMigratedCopyIsReMeasured() throws Exception {
        Path work = Files.createTempDirectory("p1-rehearsal-measure-");
        Path original = work.resolve("original.sqlite");
        Path migrated = work.resolve("migrated.sqlite");
        KnowledgeSnapshot before;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(original.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            before = workbench.workspace(WORKSPACE);
        }
        ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, WORKSPACE,
                ReferentPolicy.Strictness.STRICT, CREATED_AT);
        ReferentKeyMigration.copy(original, migrated);
        ReferentKeyMigration.apply(migrated, plan, WORKSPACE);

        // The official ask cases, measured on the MIGRATED workspaces: every scope of
        // the official dataset is prepared, migrated and asked on its own workspace,
        // exactly as the A4/A5 instrument does.
        try (RetrievalExperiment experiment = new RetrievalExperiment(work.resolve("harness.sqlite"))) {
            RetrievalExperiment.Dataset dataset = experiment.dataset();
            Path datasetDatabase = work.resolve("dataset.sqlite");
            Path datasetMigrated = work.resolve("dataset-migrated.sqlite");
            try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(datasetDatabase.toString())) {
                for (RetrievalExperiment.ScopeSpec scope : dataset.scopes().values()) {
                    prepare(workbench, scope);
                }
            }
            ReferentKeyMigration.copy(datasetDatabase, datasetMigrated);
            for (RetrievalExperiment.ScopeSpec scope : dataset.scopes().values()) {
                try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(datasetMigrated.toString())) {
                    KnowledgeSnapshot snapshot = workbench.workspace(scope.workspace());
                    ReferentKeyMigration.Plan scopePlan = ReferentKeyMigration.plan(snapshot,
                            scope.workspace(), ReferentPolicy.Strictness.STRICT, CREATED_AT);
                    ReferentKeyMigration.apply(datasetMigrated, scopePlan, scope.workspace());
                }
            }
            int passed = 0;
            int answered = 0;
            int abstentions = 0;
            int unanswerable = 0;
            int unanswerableAbstained = 0;
            int cases = 0;
            List<String> failures = new ArrayList<>();
            try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(datasetMigrated.toString())) {
                for (RetrievalExperiment.CaseSpec entry : dataset.cases()) {
                    if (entry.question() == null || entry.question().isBlank()) {
                        continue;
                    }
                    cases++;
                    String workspace = dataset.scopes().get(entry.scope()).workspace();
                    Answer answer = workbench.ask(workspace, entry.question(),
                            AbstractionLevel.valueOf((entry.level() == null ? "INTRODUCTORY"
                                    : entry.level()).toUpperCase(java.util.Locale.ROOT)));
                    boolean ok = RetrievalExperiment.judgeAnswer(entry.expect(), answer).isEmpty();
                    if (ok) {
                        passed++;
                    } else {
                        failures.add(entry.id());
                    }
                    if (answer.answered()) {
                        answered++;
                    } else {
                        abstentions++;
                    }
                    if ("unanswerable".equals(entry.category())) {
                        unanswerable++;
                        if (!answer.answered()) {
                            unanswerableAbstained++;
                        }
                    }
                }
            }
            System.out.printf("[rehearsal] the re-measurement (the migrated copy): the official ask "
                            + "cases %d/%d, answered=%d, abstentions=%d, the unanswerable "
                            + "abstained=%d/%d, the failures=%s%n",
                    passed, cases, answered, abstentions,
                    unanswerableAbstained, unanswerable, failures);
            assertThat(unanswerableAbstained)
                    .as("the refusal must not regress on the migrated copy")
                    .isEqualTo(unanswerable);
        }

        // The frozen set, measured on a migrated copy of the held-out workspace.
        Path heldOutOriginal = work.resolve("heldout.sqlite");
        Path heldOutMigrated = work.resolve("heldout-migrated.sqlite");
        KnowledgeSnapshot heldOutBefore;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(heldOutOriginal.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument(HELD_OUT_WORKSPACE, entry.path(), entry.file(),
                        entry.language());
            }
            heldOutBefore = workbench.workspace(HELD_OUT_WORKSPACE);
        }
        ReferentKeyMigration.Plan heldOutPlan = ReferentKeyMigration.plan(heldOutBefore,
                HELD_OUT_WORKSPACE, ReferentPolicy.Strictness.STRICT, CREATED_AT);
        ReferentKeyMigration.copy(heldOutOriginal, heldOutMigrated);
        ReferentKeyMigration.apply(heldOutMigrated, heldOutPlan, HELD_OUT_WORKSPACE);

        Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = root; directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve(HELD_OUT_SET))) {
                root = directory;
                break;
            }
        }
        JsonNode set = JSON.readTree(Files.readAllBytes(root.resolve(HELD_OUT_SET)));
        int passed = 0;
        int answered = 0;
        int abstentions = 0;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(heldOutMigrated.toString())) {
            for (JsonNode entry : set.path("cases")) {
                Answer answer = workbench.ask(HELD_OUT_WORKSPACE, entry.path("question").asText(),
                        AbstractionLevel.INTRODUCTORY);
                if (answer.answered()) {
                    answered++;
                } else {
                    abstentions++;
                }
                String expected = entry.path("expectedDisposition").asText();
                boolean expectedAnswered = !"UNANSWERED".equals(expected)
                        && !"REFUSED".equals(expected);
                Set<String> cited = new LinkedHashSet<>();
                for (Claim claim : answer.claims()) {
                    cited.add(claim.proposition());
                }
                boolean goldCited = true;
                for (JsonNode gold : entry.path("goldClaims")) {
                    if (!cited.contains(gold.path("proposition").asText())) {
                        goldCited = false;
                    }
                }
                if (answer.answered() == expectedAnswered && goldCited) {
                    passed++;
                }
            }
        }
        System.out.printf("[rehearsal] the frozen set (the migrated copy): %d/%d passed, "
                        + "answered=%d, abstentions=%d%n",
                passed, set.path("cases").size(), answered, abstentions);
    }

    // ── the helpers ───────────────────────────────────────────────────────────

    /**
     * Prepares a scope of the official dataset with the production methods.
     *
     * @param workbench the workbench
     * @param scope the scope
     * @throws Exception when the preparation fails
     */
    private static void prepare(KnowledgeWorkbench workbench, RetrievalExperiment.ScopeSpec scope)
            throws Exception {
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
                AcquisitionCycle.acquireAndRevise(workbench, scope.workspace(), step.file());
            } else {
                throw new IllegalStateException("the dataset names a preparation this instrument "
                        + "does not know: " + step.path());
            }
        }
    }

    /**
     * Counts the rows of every table of a database.
     *
     * @param database the database
     * @return the counts by table
     * @throws SQLException when the database refuses the read
     */
    private static Map<String, Integer> rowCounts(Path database) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            List<String> tables = new ArrayList<>();
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
            for (String table : tables) {
                if (table.startsWith("sqlite_")) {
                    continue;
                }
                try (Statement counter = connection.createStatement();
                        ResultSet counted = counter.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    counts.put(table, counted.next() ? counted.getInt(1) : 0);
                }
            }
        }
        return counts;
    }

    /**
     * Reads one scalar.
     *
     * @param statement the statement
     * @param sql the query
     * @return the value
     * @throws SQLException when the database refuses the read
     */
    private static int scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    /**
     * Breaks a copy on purpose: a concept row is deleted and one claim is re-pointed at
     * the concept it had before the migration.
     *
     * @param database the copy to break
     * @throws SQLException when the database refuses the write
     */
    private static void breakTheCopy(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM concept WHERE workspace_id = '" + WORKSPACE
                    + "' AND id IN (SELECT concept_id FROM "
                    + ReferentKeyMigration.TABLE_CLAIM_SCOPED_RECORD + " LIMIT 3)");
            // A STALE pointer: a claim that points at the concept it had BEFORE the
            // migration — a concept the lineage marks as superseded and that still
            // exists. This is what a half-migrated database looks like, and the audit
            // must see it.
            statement.execute("UPDATE claim SET concept_id = (SELECT old_id FROM "
                    + ReferentKeyMigration.TABLE_CONCEPT_LINEAGE + " LIMIT 1) WHERE workspace_id = '"
                    + WORKSPACE + "' AND id IN (SELECT claim_id FROM "
                    + ReferentKeyMigration.TABLE_CLAIM_LINEAGE + " WHERE old_concept_id = "
                    + "(SELECT old_id FROM " + ReferentKeyMigration.TABLE_CONCEPT_LINEAGE
                    + " LIMIT 1) LIMIT 1)");
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
