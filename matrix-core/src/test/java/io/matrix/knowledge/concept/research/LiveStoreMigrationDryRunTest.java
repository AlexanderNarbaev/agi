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
 * <p><b>What this is.</b> The owner's decision OD-067 chose <b>variant A</b>: the store
 * is copied, the copy is migrated, and the numbers are measured — the source itself is
 * never opened for writing. After the owner's live store was migrated (OD-070), the source
 * is the <b>preserved pre-migration backup</b>, so the dry run stays a measurement of the
 * original store rather than of an already migrated one. This test executes that dry run
 * and publishes the numbers:
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

    /**
     * The numbers the report publishes, per workspace.
     *
     * <p><b>Why these and not the first migration's.</b> The report's dry-run table
     * (§J-bis) carries the <b>first</b> migration's counts for the {@code logic} workspace
     * — the concepts 11 → 22, the referents 8, the rejected 3 — and the report itself
     * marks them superseded: the prepositional-phrase rule (OD-071) changed the policy,
     * the store was re-migrated from the preserved original, and the current counts are
     * the ones the report publishes in §J-quinque ({@code logic}: the referents 8 → 6, the
     * rejected 3 → 13, the concepts 11 → 30). These are the §J-quinque numbers, measured
     * against the preserved original.</p>
     *
     * @param concepts the concepts before the migration
     * @param inserted the concepts the copy-forward inserted
     * @param claims the claims the copy-forward re-pointed
     * @param representations the representations the copy-forward re-pointed
     * @param representationCollisions the representation collisions
     * @param prerequisites the prerequisite edges the copy-forward re-pointed
     * @param prerequisiteCollisions the prerequisite triples that collapsed onto one
     * @param prerequisiteSelfLoops the prerequisite edges dropped as self-requires
     * @param otherRows the rows of the other referencing tables that were re-pointed
     * @param lineage the lineage rows the copy-forward wrote
     * @param referents the referents the plan produced
     * @param rejected the claims the policy rejected
     */
    private record Expected(int concepts, int inserted, int claims, int representations,
                            int representationCollisions, int prerequisites,
                            int prerequisiteCollisions, int prerequisiteSelfLoops,
                            int otherRows, int lineage, int referents, int rejected) {
    }

    /** The published counts of the {@code logic} workspace. */
    private static final Expected LOGIC = new Expected(11, 19, 28, 8, 0, 6, 1, 1, 88, 56, 6, 13);

    /** The published counts of the {@code photosynthesis} workspace. */
    private static final Expected PHOTOSYNTHESIS =
            new Expected(13, 24, 24, 14, 0, 5, 0, 0, 272, 48, 14, 10);

    /** The published counts, per workspace. */
    private static final Map<String, Expected> PUBLISHED = Map.of(
            "logic", LOGIC,
            "photosynthesis", PHOTOSYNTHESIS);

    /** The number of referencing tables the audit walks. */
    private static final int AUDIT_TABLES = 13;

    @Test
    @DisplayName("the live store on a copy: the schema, the two workspaces, the audit and the rollback")
    void theLiveStoreOnACopy() throws Exception {
        Path source = liveStore();
        Assumptions.assumeTrue(source != null,
                "neither the owner's live store nor its pre-migration backup is in this checkout — "
                        + "the dry run is skipped, not failed");

        Path work = Files.createTempDirectory("live-migration-dry-run-");
        Path migrated = work.resolve("live-migrated.sqlite");
        Path rollbackCopy = work.resolve("live-rollback.sqlite");
        ReferentKeyMigration.copy(source, migrated);
        ReferentKeyMigration.copy(source, rollbackCopy);

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
        assertThat(versionAfter).as("the copy's schema version")
                .isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        assertThat(versionAfter).as("the copy's schema version did not move")
                .isGreaterThanOrEqualTo(versionBefore);

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
            assertThat(audit.tablesChecked().size()).as("the tables the audit walked of %s",
                    workspace).isEqualTo(AUDIT_TABLES);
            for (Map.Entry<String, Integer> entry : audit.orphansByTable().entrySet()) {
                assertThat(entry.getValue()).as("the orphans in %s of %s", entry.getKey(), workspace)
                        .isZero();
            }

            // ── the published numbers, asserted ─────────────────────────────────
            Expected expected = PUBLISHED.get(workspace);
            assertThat(before.concepts().size()).as("the concepts of %s before the migration",
                    workspace).isEqualTo(expected.concepts());
            assertThat(result.conceptsInserted()).as("the concepts inserted into %s", workspace)
                    .isEqualTo(expected.inserted());
            assertThat(before.concepts().size() + result.conceptsInserted())
                    .as("the concepts of %s after the migration", workspace)
                    .isEqualTo(expected.concepts() + expected.inserted());
            assertThat(before.claims().size()).as("the claims of %s before the migration",
                    workspace).isEqualTo(expected.claims());
            assertThat(result.claimsRewritten()).as("the claims re-pointed in %s", workspace)
                    .as("every claim of %s is re-pointed", workspace)
                    .isEqualTo(expected.claims());
            assertThat(result.representationsRewritten())
                    .as("the representations re-pointed in %s", workspace)
                    .isEqualTo(expected.representations());
            assertThat(result.representationCollisions())
                    .as("the representation collisions in %s", workspace)
                    .isEqualTo(expected.representationCollisions());
            assertThat(result.prerequisitesRewritten())
                    .as("the prerequisite edges re-pointed in %s", workspace)
                    .isEqualTo(expected.prerequisites());
            assertThat(result.prerequisiteCollisions())
                    .as("the prerequisite triples that collapsed in %s", workspace)
                    .isEqualTo(expected.prerequisiteCollisions());
            assertThat(result.prerequisiteSelfLoopsDropped())
                    .as("the prerequisite self-loops dropped in %s", workspace)
                    .isEqualTo(expected.prerequisiteSelfLoops());
            assertThat(result.otherRowsRewritten()).as("the other rows re-pointed in %s",
                    workspace).isEqualTo(expected.otherRows());
            assertThat(result.lineageRows()).as("the lineage rows written in %s", workspace)
                    .isEqualTo(expected.lineage());
            assertThat(plan.referents()).as("the plan's referents in %s", workspace)
                    .isEqualTo(expected.referents());
            assertThat(plan.rejected()).as("the claims the policy rejected in %s", workspace)
                    .isEqualTo(expected.rejected());
            // The referents and the rejections account for every inserted concept.
            assertThat(plan.referents() + plan.rejected())
                    .as("the referents and the rejections of %s", workspace)
                    .isEqualTo(expected.inserted());

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
                // The published rollback: the workspace goes 11 -> 30 -> 11 (logic) and
                // 13 -> 37 -> 13 (photosynthesis), and the claims come back whole.
                assertThat(conceptsAfterMigration).as("the concepts of %s after the migration",
                        workspace).isEqualTo(expected.concepts() + expected.inserted());
                assertThat(restored.concepts().size()).isEqualTo(before.concepts().size());
                assertThat(restored.concepts().size()).as("the concepts of %s after the rollback",
                        workspace).isEqualTo(expected.concepts());
                assertThat(restored.claims().size()).isEqualTo(before.claims().size());
                assertThat(restored.claims().size()).as("the claims of %s after the rollback",
                        workspace).isEqualTo(expected.claims());
            }
        }

        // ── the source store is untouched ─────────────────────────────────────
        assertThat(schemaVersion(source)).as("the source store's schema version")
                .isEqualTo(versionBefore);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM concept WHERE workspace_id = 'photosynthesis'")) {
            assertThat(rows.next()).isTrue();
            System.out.printf("[live-migration] the source store: the schema version %d, the "
                    + "photosynthesis concepts %d — untouched%n", versionBefore, rows.getInt(1));
            // The published number: the source still holds the pre-migration concepts.
            assertThat(rows.getInt(1)).as("the source store's photosynthesis concepts")
                    .isEqualTo(PHOTOSYNTHESIS.concepts());
        }
    }

    /**
     * The store the dry run migrates.
     *
     * <p>After the owner's live store has been migrated (OD-070), the live file is already
     * at the current schema version and re-migrating it would mean migrating an already
     * migrated store. The dry run therefore migrates the <b>preserved original</b> — the
     * timestamped backup the migration launcher kept, which still holds the pre-migration
     * bytes — and falls back to the live store when no backup is there (a checkout that
     * holds the store but not its backup).</p>
     *
     * @return the store to migrate, or {@code null} when this checkout holds neither
     */
    private static Path liveStore() {
        Path newest = null;
        try (java.util.stream.Stream<Path> backups = java.nio.file.Files.list(
                Path.of("data/knowledge"))) {
            newest = backups.filter(path -> path.getFileName().toString()
                            .startsWith("workbench.pre-migration-")
                            && path.getFileName().toString().endsWith(".sqlite"))
                    .max(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
        } catch (java.io.IOException | RuntimeException e) {
            newest = null;
        }
        if (newest != null) {
            return newest.toAbsolutePath().normalize();
        }
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
