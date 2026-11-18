package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.WorkspaceSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The owner's live store: the identity migration, executed under OD-067 and the owner's
 * answer to D1-LIVE-STORE-MIGRATION-019 (2026-11-18, option A — migrate it now).
 *
 * <p><b>What it does, in this order, and why this order.</b></p>
 * <ol>
 *   <li>the live store is snapshotted into a <b>backup</b> with a timestamped name, with
 *       SQLite's own {@code VACUUM INTO} — the store runs in WAL mode, so a copy of the main
 *       file alone can miss the frames that are not yet checkpointed into it, and the snapshot
 *       merges them into one file. The backup is the way back, it is taken before anything is
 *       written, and its <b>content</b> is compared with the live store's, table by table, so
 *       the way back is <b>proved</b> and not assumed (the owner's decision on R-EPIS-08,
 *       option B);</li>
 *   <li>the live store is snapshotted into a <b>work file</b>, and the work file is migrated:
 *       the
 *       schema chain first, then one workspace at a time, with a re-export between the
 *       workspaces, because the plan of a workspace is built from that workspace's own
 *       rows;</li>
 *   <li>every workspace is <b>audited</b>: the audit must report zero orphans and zero
 *       stale pointers in every table — a non-zero count <b>aborts</b> before the live
 *       file is touched;</li>
 *   <li>the <b>rollback</b> is rehearsed on a second snapshot: the store's own snapshot
 *       restores the workspace to its pre-migration size — if that fails, the live file is
 *       not touched;</li>
 *   <li>only then is the live file <b>replaced</b> by the migrated work file, atomically
 *       through a rename in the same directory — and only when the backup still holds the
 *       original content. The live store's WAL is checkpointed and its sidecars are removed
 *       first, so the new file never sits next to a stale WAL;</li>
 *   <li>the live file is <b>re-verified</b> afterwards: the schema version, the counts and
 *       the audit, plus the check that the backup still holds the original content.</li>
 * </ol>
 *
 * <p><b>What it deliberately does not do.</b> It does not seed the alias layer: the declared
 * aliases are the corpora's geology lemmas and this store's concepts are biology and logic,
 * so seeding them would put a provenance on declarations this store never made. It does not
 * delete the old concept rows — the lineage marks them superseded.</p>
 *
 * <p><b>Usage:</b> {@code java -cp <classes> io.matrix.knowledge.concept.research.LiveStoreMigration
 * <live-store-path>} — the script {@code scripts/migrate-live-store.sh} compiles and runs it.</p>
 *
 * <p><b>Why the run is a method and not only a {@code main}.</b> The launcher aborts on a
 * dirty audit and on a failed rollback, and both aborts mean <i>the live store was NOT
 * touched</i> — a claim that is only worth anything if it can be <b>exercised</b>. A
 * {@code System.exit} inside the class would kill the test JVM, so the aborts are raised as
 * {@link Aborted} and {@code main} turns them into the exit code. The contract is therefore
 * testable, and {@code LiveStoreLauncherTest} tests it.</p>
 */
public final class LiveStoreMigration {

    /** The workspaces of the live store, in the order they are migrated. */
    private static final List<String> WORKSPACES = List.of("logic", "photosynthesis");

    /** The epoch of the migration's writes. */
    private static final String CREATED_AT = "2026-11-18T00:00:00Z";

    /** The size of the buffer the digest is computed over. */
    private static final int DIGEST_BUFFER_BYTES = 8192;

    private LiveStoreMigration() {
    }

    /**
     * The launcher aborted before the live file was touched.
     *
     * <p>An abort is never a partial success: the live file still holds its original
     * bytes when this is thrown.</p>
     */
    public static final class Aborted extends Exception {

        Aborted(String message) {
            super(message);
        }
    }

    /**
     * What a launcher run did.
     *
     * @param live the live store, replaced by the migrated work file
     * @param backup the backup the launcher kept — it holds the original CONTENT (a
     *               consistent snapshot), not a byte copy of the live file
     * @param originalSha256 the sha256 of the live file before the run
     * @param originalContentDigest the content digest of the live store before the run
     */
    public record Outcome(Path live, Path backup, String originalSha256,
            String originalContentDigest) {
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
        try {
            run(live);
        } catch (Aborted aborted) {
            System.err.println(aborted.getMessage());
            System.exit(1);
        }
        System.out.println("[live-migration] done");
    }

    /**
     * Runs the launcher against a live store.
     *
     * <p>The order is the contract: the backup first, then the work file, then the audit,
     * then the rehearsed rollback, and only then the atomic replacement of the live file.
     * Every abort happens <b>before</b> the live file is touched.</p>
     *
     * @param live the live store to migrate
     * @return what the run did
     * @throws Aborted when the audit is not clean or the rollback does not restore the
     *                 workspace — the live file is untouched when this is thrown
     * @throws IllegalStateException when the backup does not hold the original bytes
     */
    public static Outcome run(Path live) throws Exception {
        live = live.toAbsolutePath().normalize();
        if (!Files.isRegularFile(live)) {
            throw new IllegalArgumentException("there is no live store at " + live);
        }
        Path directory = live.getParent();
        String stamp = Instant.now().toString().replace(':', '-').replace('.', '-');
        Path backup = directory.resolve("workbench.pre-migration-" + stamp + ".sqlite");
        Path work = directory.resolve("workbench.migrating-" + stamp + ".sqlite");
        Path rollback = directory.resolve("workbench.rollback-" + stamp + ".sqlite");

        // ── 1. the backup, before anything is written ───────────────────────────
        // The backup is a CONSISTENT SNAPSHOT (SQLite's own VACUUM INTO), not a copy
        // of the main file: the store runs in WAL mode, and a main-file-only copy
        // loses the frames that are not yet checkpointed into it (the owner's
        // decision on R-EPIS-08, option B). The snapshot merges the WAL into one
        // file, so the backup holds the store's whole content — and the CONTENT is
        // then compared with the live store's, table by table, so the way back is
        // PROVED and not assumed.
        String originalSha256 = sha256(live);
        String originalContent = contentDigest(live);
        vacuumInto(live, backup);
        System.out.printf("[live-migration] the backup: %s (%d bytes, the sha256 %s)%n",
                backup, Files.size(backup), sha256(backup));
        if (!originalContent.equals(contentDigest(backup))) {
            Files.deleteIfExists(backup);
            throw new Aborted("[live-migration] ABORTED: the backup does not hold the original "
                    + "content. The live store was NOT touched.");
        }

        // ── 2. the work file: the schema chain, then one workspace at a time ──
        vacuumInto(live, work);
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
                Files.deleteIfExists(work);
                Files.deleteIfExists(rollback);
                throw new Aborted("[live-migration] ABORTED: the audit of " + workspace
                        + " is not clean. The live store was NOT touched.");
            }
        }

        // ── 4. the rollback, rehearsed on a second copy ───────────────────────
        vacuumInto(live, rollback);
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
                    Files.deleteIfExists(work);
                    Files.deleteIfExists(rollback);
                    throw new Aborted("[live-migration] ABORTED: the rollback of " + workspace
                            + " did not restore the workspace. The live store was NOT touched.");
                }
            }
        }
        Files.deleteIfExists(rollback);

        // ── 5. the live file is replaced, atomically ───────────────────────────
        // The way back must still be there when the live file is replaced: the backup's
        // content is checked one last time before the swap.
        if (!originalContent.equals(contentDigest(backup))) {
            Files.deleteIfExists(work);
            throw new Aborted("[live-migration] ABORTED: the backup no longer holds the original "
                    + "content. The live store was NOT touched.");
        }
        // The live store's own WAL is checkpointed into its main file and its sidecars
        // are removed, so the file that replaces it never sits next to a stale WAL: a
        // stale -wal beside a new database is replayed onto it by SQLite, which is
        // exactly the corruption the snapshot above exists to prevent.
        checkpointAndDropSidecars(live);
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
        // The way back, checked AFTER the replacement: the backup must still hold the
        // original content of the store the live file has just replaced.
        String backupContent = contentDigest(backup);
        if (!originalContent.equals(backupContent)) {
            throw new IllegalStateException("the backup " + backup + " does not hold the original "
                    + "content any more: its content digest is " + backupContent + " and the "
                    + "pre-migration live store's was " + originalContent);
        }
        System.out.printf("[live-migration] the backup is kept: %s (%d bytes)%n",
                backup, Files.size(backup));
        return new Outcome(live, backup, originalSha256, originalContent);
    }

    /**
     * A consistent snapshot of a database, taken with SQLite's own {@code VACUUM INTO}.
     *
     * <p>The store runs in WAL mode, so a copy of the main file alone can miss the
     * frames that are not yet checkpointed into it. {@code VACUUM INTO} merges them
     * into the new file, so the snapshot holds the database's whole content.</p>
     *
     * @param source the database to snapshot
     * @param target the file to write — it must not exist
     */
    private static void vacuumInto(Path source, Path target) throws SQLException, IOException {
        Files.deleteIfExists(target);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'");
        }
    }

    /**
     * Checkpoints a database's WAL into its main file and removes its sidecars.
     *
     * <p>Called immediately before the live file is replaced: a stale {@code -wal}
     * beside a new database would be replayed onto it by SQLite.</p>
     *
     * @param live the live database
     */
    private static void checkpointAndDropSidecars(Path live) throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
        Files.deleteIfExists(Path.of(live + "-wal"));
        Files.deleteIfExists(Path.of(live + "-shm"));
    }

    /**
     * A digest of a database's CONTENT: every user table, every row, order-independent.
     *
     * <p>Two databases with the same content have the same digest whatever order the
     * rows are read in — the per-row digests are sorted before they are folded
     * together. The digest is what proves that the backup holds the store's content;
     * a byte digest cannot, because the backup is now a snapshot and not a copy.</p>
     *
     * @param database the database to digest
     * @return the digest, as a lower-case hex string
     */
    static String contentDigest(Path database)
            throws SQLException, IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("SELECT name FROM sqlite_master "
                    + "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            for (String table : tables) {
                List<String> rowDigests = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("SELECT * FROM \""
                        + table.replace("\"", "\"\"") + "\"")) {
                    ResultSetMetaData meta = rows.getMetaData();
                    int columns = meta.getColumnCount();
                    while (rows.next()) {
                        MessageDigest row = MessageDigest.getInstance("SHA-256");
                        for (int column = 1; column <= columns; column++) {
                            Object value = rows.getObject(column);
                            if (value == null) {
                                row.update((byte) 0);
                            } else if (value instanceof byte[] bytes) {
                                row.update(bytes);
                            } else {
                                row.update(value.toString().getBytes(StandardCharsets.UTF_8));
                            }
                            row.update((byte) 0);
                        }
                        rowDigests.add(HexFormat.of().formatHex(row.digest()));
                    }
                }
                rowDigests.sort(null);
                digest.update(table.getBytes(StandardCharsets.UTF_8));
                for (String rowDigest : rowDigests) {
                    digest.update(rowDigest.getBytes(StandardCharsets.UTF_8));
                }
                digest.update(Integer.toString(rowDigests.size()).getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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

    /**
     * The sha256 of a file's bytes.
     *
     * @param path the file
     * @return the digest, as a lower-case hex string
     */
    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[DIGEST_BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
