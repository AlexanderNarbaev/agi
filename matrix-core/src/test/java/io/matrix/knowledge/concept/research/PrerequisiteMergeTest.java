package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — the referent key: the MERGE COLLAPSE of the prerequisite graph.
 *
 * <p><b>What this is.</b> The regression test of the defect the owner's live store's dry
 * run found: when two old concepts become ONE referent, their prerequisite edges can land
 * on the same triple, and the blind UPDATE the migration used to run then violated the
 * primary key — the migration crashed and the collapse was never counted.
 *
 * <p><b>Why it is synthetic.</b> The corpora carry <b>no</b> prerequisites at all (the
 * measured baseline: 0 edges), so the corpora never exercised this path; and the owner's
 * live store is not in the repository, so the dry run that found the defect is skipped
 * in a clean checkout. This test builds the smallest store that reproduces both
 * outcomes:
 *
 * <ul>
 *   <li>a <b>collapse</b>: two edges that share their target after the merge keep ONE
 *       edge, and the collapse is counted and published;</li>
 *   <li>a <b>self-loop</b>: an edge whose two ends become the same referent cannot
 *       survive — a concept may not require itself — so it is DROPPED, and the drop is
 *       counted and published.</li>
 * </ul>
 *
 * <p><b>What it proves.</b> The migration completes, the graph stays a SET of triples,
 * the store holds no self-require, the audit reports zero orphans, and both losses are
 * in the numbers the migration publishes.
 */
@DisplayName("P1 — the referent key: the merge collapse of the prerequisite graph")
class PrerequisiteMergeTest {

    /** The workspace of the test. */
    private static final String WORKSPACE = "merge-test";

    /** The epoch of the test's writes. */
    private static final String CREATED_AT = "2026-11-18T00:00:00Z";

    @Test
    @DisplayName("two merged concepts: the shared edge collapses, the self-require is dropped")
    void theMergeCollapsesTheGraph() throws Exception {
        Path work = Files.createTempDirectory("p1-prerequisite-merge-");
        Path database = work.resolve("merge.sqlite");

        // ── the store, with the schema the migration runs on ──────────────────
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.workspaces()).isEmpty();
        }
        writeTheWorkspace(database);

        // ── the plan ──────────────────────────────────────────────────────────
        KnowledgeSnapshot before;
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            before = store.exportWorkspace(WORKSPACE);
        }
        assertThat(before.concepts()).hasSize(3);
        assertThat(before.claims()).hasSize(3);

        ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, WORKSPACE,
                ReferentPolicy.Strictness.STRICT, CREATED_AT, List.of());
        System.out.printf("[merge] the plan: the referents %d, the new concepts %d, "
                + "the rejected %d%n", plan.referents(), plan.newConcepts().size(),
                plan.rejected());

        // The two claims that name the same thing must land on one referent, or the
        // test would prove nothing about the merge.
        String alpha = plan.oldToNew().get("c-alpha");
        String beta = plan.oldToNew().get("c-beta");
        String gamma = plan.oldToNew().get("c-gamma");
        assertThat(alpha).as("the referent of Alpha").isNotNull();
        assertThat(beta).as("the referent of Beta").isNotNull();
        assertThat(alpha).as("the two merged concepts share one referent").isEqualTo(beta);
        assertThat(gamma).as("the referent of Gamma").isNotNull().isNotEqualTo(alpha);

        // ── the copy-forward ─────────────────────────────────────────────────
        ReferentKeyMigration.MigrationResult result =
                ReferentKeyMigration.apply(database, plan, WORKSPACE);
        System.out.printf("[merge] the copy-forward: the concepts inserted %d, the claims "
                + "re-pointed %d, the prerequisites re-pointed %d, the collapses %d, the "
                + "self-loops %d%n", result.conceptsInserted(), result.claimsRewritten(),
                result.prerequisitesRewritten(), result.prerequisiteCollisions(),
                result.prerequisiteSelfLoopsDropped());

        // The two edges that shared their target collapse onto one triple, and the
        // edge that became a self-require is dropped: both are published.
        assertThat(result.prerequisiteCollisions()).as("the collapsed edges").isEqualTo(1);
        assertThat(result.prerequisiteSelfLoopsDropped()).as("the dropped self-requires")
                .isEqualTo(1);

        // ── the graph after the copy-forward ──────────────────────────────────
        List<String[]> edges = edges(database);
        System.out.printf("[merge] the graph after the copy-forward: %d edge(s) %s%n",
                edges.size(), edges.stream()
                        .map(edge -> edge[0] + " -> " + edge[1]).toList());
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0)[0]).as("the merged concept").isEqualTo(alpha);
        assertThat(edges.get(0)[1]).as("the concept the merged one requires").isEqualTo(gamma);
        assertThat(edges.get(0)[0]).as("a concept never requires itself")
                .isNotEqualTo(edges.get(0)[1]);

        // ── the audit ─────────────────────────────────────────────────────────
        ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(database, WORKSPACE);
        System.out.printf("[merge] the audit: the tables %d, the orphans %d, the stale %d%n",
                audit.tablesChecked().size(), audit.totalOrphans(), audit.totalStale());
        assertThat(audit.totalOrphans()).isZero();
        assertThat(audit.totalStale()).isZero();
    }

    /** The workspace's edges, as (concept, requires) pairs. */
    private static List<String[]> edges(Path database) throws SQLException {
        List<String[]> edges = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT concept_id, requires_concept_id FROM prerequisite "
                             + "WHERE workspace_id = '" + WORKSPACE + "' ORDER BY concept_id")) {
            while (rows.next()) {
                edges.add(new String[] { rows.getString(1), rows.getString(2) });
            }
        }
        return edges;
    }

    /**
     * Writes the workspace: two concepts that name the same thing (so the merge joins
     * them), a third one, and the three edges whose collapse and self-loop the migration
     * has to resolve.
     */
    private static void writeTheWorkspace(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO source (id, workspace_id, kind, title, locator, "
                    + "authority, freshness, excerpt, added_at) VALUES "
                    + "('src-1', '" + WORKSPACE + "', 'OBSERVATION', 'A synthetic document', null, 1, 1, "
                    + "null, '" + CREATED_AT + "')");
            for (String[] concept : new String[][] {
                    { "c-alpha", "Alpha" }, { "c-beta", "Beta" }, { "c-gamma", "Gamma" } }) {
                statement.executeUpdate("INSERT INTO concept (id, workspace_id, name, definition, "
                        + "domain, created_at) VALUES ('" + concept[0] + "', '" + WORKSPACE + "', '"
                        + concept[1] + "', null, null, '" + CREATED_AT + "')");
            }
            // The two claims that name the SAME thing: the resolver groups them by the
            // lemma and the source, so the merge joins them into one referent.
            statement.executeUpdate("INSERT INTO claim (id, workspace_id, concept_id, proposition, "
                    + "scope, context, status, confidence, version, introduced_by_source_id, "
                    + "created_at, updated_at) VALUES ('cl-1', '" + WORKSPACE + "', 'c-alpha', "
                    + "'Alpha is the first letter of the Greek alphabet.', 'Alpha', null, "
                    + "'SUPPORTED', 0.9, 1, 'src-1', '" + CREATED_AT + "', '" + CREATED_AT + "')");
            statement.executeUpdate("INSERT INTO claim (id, workspace_id, concept_id, proposition, "
                    + "scope, context, status, confidence, version, introduced_by_source_id, "
                    + "created_at, updated_at) VALUES ('cl-2', '" + WORKSPACE + "', 'c-beta', "
                    + "'Beta is the second letter of the Greek alphabet.', 'Alpha', null, "
                    + "'SUPPORTED', 0.9, 1, 'src-1', '" + CREATED_AT + "', '" + CREATED_AT + "')");
            statement.executeUpdate("INSERT INTO claim (id, workspace_id, concept_id, proposition, "
                    + "scope, context, status, confidence, version, introduced_by_source_id, "
                    + "created_at, updated_at) VALUES ('cl-3', '" + WORKSPACE + "', 'c-gamma', "
                    + "'Gamma follows Beta in the Greek alphabet.', 'Gamma', null, "
                    + "'SUPPORTED', 0.9, 1, 'src-1', '" + CREATED_AT + "', '" + CREATED_AT + "')");
            // The edges: Alpha -> Gamma and Beta -> Gamma collapse onto one triple,
            // and Alpha -> Beta becomes a self-require once the merge joins them.
            statement.executeUpdate("INSERT INTO prerequisite (workspace_id, concept_id, "
                    + "requires_concept_id, required, note) VALUES ('" + WORKSPACE + "', "
                    + "'c-alpha', 'c-gamma', 1, 'the first letter comes before the third')");
            statement.executeUpdate("INSERT INTO prerequisite (workspace_id, concept_id, "
                    + "requires_concept_id, required, note) VALUES ('" + WORKSPACE + "', "
                    + "'c-beta', 'c-gamma', 1, 'the second letter comes before the third')");
            statement.executeUpdate("INSERT INTO prerequisite (workspace_id, concept_id, "
                    + "requires_concept_id, required, note) VALUES ('" + WORKSPACE + "', "
                    + "'c-alpha', 'c-beta', 1, 'the first letter comes before the second')");
        }
    }
}
