package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.WorkspaceSnapshot;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — the owner's LIVE store: the identity migration, on a COPY.
 *
 * <p><b>What this is.</b> The owner's decision OD-067 chose <b>variant A</b>: the live store
 * is copied, the copy is migrated, and the numbers are measured — the live store itself is
 * never opened for writing. This test executes that dry run and publishes the numbers:
 * the schema version the copy lands on, the time the schema chain took, the copy-forward's
 * own counts (including the representation collisions of OD-065), the orphan audit over the
 * thirteen referencing tables, and the logical rollback.
 *
 * <p><b>What it deliberately does NOT do.</b> It does not write a single row into the
 * owner's live store, it does not seed the alias layer (the declared aliases are the
 * <i>corpus's</i> geology lemmas and this store's concepts are biology and logic, so
 * seeding them would put a provenance on declarations this store never made), and it does
 * not decide anything: the numbers it prints are the evidence for the owner's SECOND
 * decision, which this test does not presume.
 *
 * <p><b>When the store is absent</b> (a clean CI checkout), the test is <b>skipped</b>, not
 * failed: it is a dry run of the owner's own data, and that data is not in the repository
 * ({@code matrix-core/data/.gitignore} ignores everything under {@code data/}).
 */
@DisplayName("P1 — the owner's live store: the identity migration on a copy")
class LiveStoreMigrationDryRunTest {

    /** The workspaces of the live store, in the order they are migrated. */
    private static final List<String> WORKSPACES = List.of("logic", "photosynthesis");

    /** The epoch of the migration's writes. */
    private static final String CREATED_AT = "2026-11-18T00:00:00Z";

    @Test
    @DisplayName("the live store on a copy: the schema, the two workspaces, the audit and the rollback")
    void theLiveStoreOnACopy() throws Exception {
        Path live = liveStore();
        Assumptions.assumeTrue(live != null,
                "the owner's live store is not in this checkout — the dry run is skipped, not failed");

        Path work = Files.createTempDirectory("live-migration-dry-run-");
        Path migrated = work.resolve("live-migrated.sqlite");
        Path rollbackCopy = work.resolve("live-rollback.sqlite");
        ReferentKeyMigration.copy(live, migrated);
        ReferentKeyMigration.copy(live, rollbackCopy);

        // ── the schema chain, on the copy ─────────────────────────────────────
        int versionBefore = schemaVersion(migrated);
        long schemaStarted = System.nanoTime();
        try (KnowledgeStore store = new KnowledgeStore(migrated)) {
            assertThat(store.workspaces()).containsAll(WORKSPACES);
        }
        long schemaMillis = (System.nanoTime() - schemaStarted) / 1_000_000L;
        int versionAfter = schemaVersion(migrated);
        System.out.printf("[live-migration] the schema: the copy's version %d -> %d, the chain took "
                + "%d ms%n", versionBefore, versionAfter, schemaMillis);
        assertThat(versionAfter).isEqualTo(KnowledgeStore.SCHEMA_VERSION);

        // ── the copy-forward, one workspace at a time ─────────────────────────
        for (String workspace : WORKSPACES) {
            KnowledgeSnapshot before;
            try (KnowledgeStore store = new KnowledgeStore(migrated)) {
                before = store.exportWorkspace(workspace);
            }
            // The alias layer stays empty on purpose: the declared aliases belong to the
            // corpora, not to this store.
            ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, workspace,
                    ReferentPolicy.Strictness.STRICT, CREATED_AT, List.of());
            ReferentKeyMigration.MigrationResult result =
                    ReferentKeyMigration.apply(migrated, plan, workspace);
            ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(migrated, workspace);

            System.out.printf("[live-migration] the workspace %s: the concepts %d -> %d (inserted %d), "
                            + "the claims %d (re-pointed %d), the representations re-pointed %d, the "
                            + "representation collisions %d, the prerequisites %d, the prerequisite "
                            + "collapses %d, the prerequisite self-loops %d, the other rows %d, "
                            + "the lineage %d, the plan's referents %d, the rejected %d, "
                            + "the copy-forward took %d ms%n",
                    workspace, before.concepts().size(),
                    before.concepts().size() + result.conceptsInserted(), result.conceptsInserted(),
                    before.claims().size(), result.claimsRewritten(),
                    result.representationsRewritten(), result.representationCollisions(),
                    result.prerequisitesRewritten(), result.prerequisiteCollisions(),
                    result.prerequisiteSelfLoopsDropped(), result.otherRowsRewritten(),
                    result.lineageRows(), plan.referents(), plan.rejected(), result.millis());
            System.out.printf("[live-migration] the workspace %s: the audit walked %d tables, the "
                            + "orphans %d, the stale pointers %d%n",
                    workspace, audit.tablesChecked().size(), audit.totalOrphans(), audit.totalStale());

            assertThat(audit.totalOrphans()).as("the orphans of %s", workspace).isZero();
            assertThat(audit.totalStale()).as("the stale pointers of %s", workspace).isZero();
            for (Map.Entry<String, Integer> entry : audit.orphansByTable().entrySet()) {
                assertThat(entry.getValue()).as("the orphans in %s of %s", entry.getKey(), workspace)
                        .isZero();
            }

            // ── the rollback, on its own copy ────────────────────────────────
            WorkspaceSnapshot snapshot;
            int conceptsAfterMigration;
            try (KnowledgeStore store = new KnowledgeStore(rollbackCopy)) {
                snapshot = store.takeWorkspaceSnapshot(workspace, WorkspaceSnapshot.Kind.MANUAL,
                        CREATED_AT);
                ReferentKeyMigration.apply(rollbackCopy, plan, workspace);
                conceptsAfterMigration = store.exportWorkspace(workspace).concepts().size();
            }
            long started = System.nanoTime();
            try (KnowledgeStore store = new KnowledgeStore(rollbackCopy)) {
                store.restoreWorkspaceSnapshot(snapshot.id(), CREATED_AT);
                KnowledgeSnapshot restored = store.exportWorkspace(workspace);
                System.out.printf("[live-migration] the workspace %s: the rollback — the concepts %d -> "
                                + "%d, the claims %d, the restore took %d ms%n",
                        workspace, conceptsAfterMigration, restored.concepts().size(),
                        restored.claims().size(),
                        (System.nanoTime() - started) / 1_000_000L);
                assertThat(restored.concepts().size()).isEqualTo(before.concepts().size());
                assertThat(restored.claims().size()).isEqualTo(before.claims().size());
            }
        }

        // ── the live store is untouched ───────────────────────────────────────
        assertThat(schemaVersion(live)).as("the live store's schema version").isEqualTo(versionBefore);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM concept WHERE workspace_id = 'photosynthesis'")) {
            assertThat(rows.next()).isTrue();
            System.out.printf("[live-migration] the live store: the schema version %d, the "
                    + "photosynthesis concepts %d — untouched%n", versionBefore, rows.getInt(1));
        }
    }

    /** The owner's live store, or {@code null} when this checkout does not hold it. */
    private static Path liveStore() {
        for (String candidate : List.of("data/knowledge/workbench.sqlite",
                "../matrix-core/data/knowledge/workbench.sqlite")) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    /** The store's schema version, read directly. */
    private static int schemaVersion(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT value FROM schema_meta WHERE key = 'version'")) {
            return rows.next() ? Integer.parseInt(rows.getString(1)) : 0;
        }
    }
}
