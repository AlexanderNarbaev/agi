package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.WorkspaceSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The owner's live store: the identity migration, executed under OD-067 and the owner's
 * answer to D1-LIVE-STORE-MIGRATION-019 (2026-11-18, option A — migrate it now).
 *
 * <p><b>What it does, in this order, and why this order.</b></p>
 * <ol>
 *   <li>the live file is copied to a <b>backup</b> with a timestamped name — the backup is
 *       the way back, and it is taken before anything is written;</li>
 *   <li>the live file is copied to a <b>work file</b>, and the work file is migrated: the
 *       schema chain first, then one workspace at a time, with a re-export between the
 *       workspaces, because the plan of a workspace is built from that workspace's own
 *       rows;</li>
 *   <li>every workspace is <b>audited</b>: the audit must report zero orphans and zero
 *       stale pointers in every table — a non-zero count <b>aborts</b> before the live
 *       file is touched;</li>
 *   <li>the <b>rollback</b> is rehearsed on a second copy: the store's own snapshot
 *       restores the workspace to its pre-migration size — if that fails, the live file is
 *       not touched;</li>
 *   <li>only then is the live file <b>replaced</b> by the migrated work file, atomically
 *       through a rename in the same directory;</li>
 *   <li>the live file is <b>re-verified</b> afterwards: the schema version, the counts and
 *       the audit, plus the check that the backup still holds the original bytes.</li>
 * </ol>
 *
 * <p><b>What it deliberately does not do.</b> It does not seed the alias layer: the declared
 * aliases are the corpora's geology lemmas and this store's concepts are biology and logic,
 * so seeding them would put a provenance on declarations this store never made. It does not
 * delete the old concept rows — the lineage marks them superseded.</p>
 *
 * <p><b>Usage:</b> {@code java -cp <classes> io.matrix.knowledge.concept.research.LiveStoreMigration
 * <live-store-path>} — the script {@code scripts/migrate-live-store.sh} compiles and runs it.</p>
 */
public final class LiveStoreMigration {

    /** The workspaces of the live store, in the order they are migrated. */
    private static final List<String> WORKSPACES = List.of("logic", "photosynthesis");

    /** The epoch of the migration's writes. */
    private static final String CREATED_AT = "2026-11-18T00:00:00Z";

    private LiveStoreMigration() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: LiveStoreMigration <live-store-path>");
            System.exit(2);
        }
        Path live = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(live)) {
            System.err.println("there is no live store at " + live);
            System.exit(2);
        }
        Path directory = live.getParent();
        String stamp = Instant.now().toString().replace(':', '-').replace('.', '-');
        Path backup = directory.resolve("workbench.pre-migration-" + stamp + ".sqlite");
        Path work = directory.resolve("workbench.migrating-" + stamp + ".sqlite");
        Path rollback = directory.resolve("workbench.rollback-" + stamp + ".sqlite");

        // ── 1. the backup, before anything is written ───────────────────────────
        Files.copy(live, backup, StandardCopyOption.COPY_ATTRIBUTES);
        System.out.printf("[live-migration] the backup: %s (%d bytes)%n",
                backup, Files.size(backup));

        // ── 2. the work file: the schema chain, then one workspace at a time ──
        Files.copy(live, work, StandardCopyOption.COPY_ATTRIBUTES);
        int versionBefore = schemaVersion(work);
        long started = System.nanoTime();
        try (KnowledgeStore store = new KnowledgeStore(work)) {
            // The chain runs on construction: the store migrates the file to the
            // current schema version.
            System.out.printf("[live-migration] the schema: the copy's version %d -> %d, the chain "
                    + "took %d ms%n", versionBefore, KnowledgeStore.SCHEMA_VERSION,
                    (System.nanoTime() - started) / 1_000_000L);
        }
        for (String workspace : WORKSPACES) {
            KnowledgeSnapshot before;
            try (KnowledgeStore store = new KnowledgeStore(work)) {
                before = store.exportWorkspace(workspace);
            }
            ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, workspace,
                    ReferentPolicy.Strictness.STRICT, CREATED_AT, List.of());
            ReferentKeyMigration.MigrationResult result =
                    ReferentKeyMigration.apply(work, plan, workspace);
            System.out.printf("[live-migration] the workspace %s: the concepts %d -> %d, the claims "
                    + "%d (re-pointed %d), the representations %d, the representation collisions %d, "
                    + "the prerequisites %d, the prerequisite collapses %d, the prerequisite "
                    + "self-loops %d, the other rows %d, the lineage %d, the referents %d, the "
                    + "rejected %d, the copy-forward %d ms%n", workspace,
                    before.concepts().size(), before.concepts().size() + result.conceptsInserted(),
                    before.claims().size(), result.claimsRewritten(),
                    result.representationsRewritten(), result.representationCollisions(),
                    result.prerequisitesRewritten(), result.prerequisiteCollisions(),
                    result.prerequisiteSelfLoopsDropped(), result.otherRowsRewritten(),
                    result.lineageRows(), plan.referents(), plan.rejected(), result.millis());
        }

        // ── 3. the audit: a non-zero count aborts before the live file is touched ─
        for (String workspace : WORKSPACES) {
            ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(work, workspace);
            System.out.printf("[live-migration] the workspace %s: the audit walked %d tables, the "
                    + "orphans %d, the stale pointers %d%n", workspace,
                    audit.tablesChecked().size(), audit.totalOrphans(), audit.totalStale());
            if (audit.totalOrphans() != 0 || audit.totalStale() != 0) {
                System.err.printf("[live-migration] ABORTED: the audit of %s is not clean. The live "
                        + "store was NOT touched.%n", workspace);
                Files.deleteIfExists(work);
                Files.deleteIfExists(rollback);
                System.exit(1);
            }
        }

        // ── 4. the rollback, rehearsed on a second copy ───────────────────────
        Files.copy(live, rollback, StandardCopyOption.COPY_ATTRIBUTES);
        try (KnowledgeStore store = new KnowledgeStore(rollback)) {
            for (String workspace : WORKSPACES) {
                KnowledgeSnapshot before = store.exportWorkspace(workspace);
                WorkspaceSnapshot snapshot = store.takeWorkspaceSnapshot(workspace,
                        WorkspaceSnapshot.Kind.MANUAL, CREATED_AT);
                ReferentKeyMigration.Plan plan = ReferentKeyMigration.plan(before, workspace,
                        ReferentPolicy.Strictness.STRICT, CREATED_AT, List.of());
                ReferentKeyMigration.apply(rollback, plan, workspace);
                int after = store.exportWorkspace(workspace).concepts().size();
                store.restoreWorkspaceSnapshot(snapshot.id(), CREATED_AT);
                int restored = store.exportWorkspace(workspace).concepts().size();
                System.out.printf("[live-migration] the workspace %s: the rollback — the concepts "
                        + "%d -> %d -> %d%n", workspace, before.concepts().size(), after, restored);
                if (restored != before.concepts().size()) {
                    System.err.printf("[live-migration] ABORTED: the rollback of %s did not restore "
                            + "the workspace. The live store was NOT touched.%n", workspace);
                    Files.deleteIfExists(work);
                    Files.deleteIfExists(rollback);
                    System.exit(1);
                }
            }
        }
        Files.deleteIfExists(rollback);

        // ── 5. the live file is replaced, atomically ───────────────────────────
        Path target = directory.resolve("workbench.migrated-" + stamp + ".sqlite");
        Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
        Files.move(target, live, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("[live-migration] the live store is replaced: %s%n", live);

        // ── 6. the re-verification ─────────────────────────────────────────────
        int versionAfter = schemaVersion(live);
        System.out.printf("[live-migration] the live store after: the schema version %d%n", versionAfter);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
             Statement statement = connection.createStatement()) {
            for (String table : List.of("concept", "claim", "prerequisite", "source",
                    "representation", "concept_lineage", "claim_lineage", "claim_scoped_record")) {
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    System.out.printf("[live-migration] the live store: the %s rows %d%n", table,
                            rows.next() ? rows.getInt(1) : -1);
                }
            }
        }
        for (String workspace : WORKSPACES) {
            ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(live, workspace);
            System.out.printf("[live-migration] the live store: the workspace %s — the orphans %d, "
                    + "the stale pointers %d%n", workspace, audit.totalOrphans(), audit.totalStale());
        }
        System.out.printf("[live-migration] the backup is kept: %s (%d bytes)%n",
                backup, Files.size(backup));
        System.out.println("[live-migration] done");
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
