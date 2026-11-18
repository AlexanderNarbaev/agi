package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.KnowledgeStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3 — the owner's live store: the LAUNCHER, exercised.
 *
 * <p><b>What this is.</b> The launcher's own logic. {@code LiveStoreMigrationDryRunTest}
 * exercises {@code ReferentKeyMigration} directly, so everything the launcher itself
 * decides — the order, the backup, the abort, the atomic replacement — was untested: the
 * dry run would stay green if the launcher replaced the live file <i>before</i> the audit,
 * or wrote into it in place, or dropped the backup. This test runs the launcher itself,
 * against a <b>copy</b> of the preserved pre-migration backup in a temporary directory.
 * The owner's live store is never opened.</p>
 *
 * <p><b>What it pins.</b> Five claims:</p>
 * <ol>
 *   <li><b>The abort.</b> A dirty audit stops the launcher <b>before</b> the live file is
 *       touched: the live file keeps its exact bytes, and the abort says so.</li>
 *   <li><b>The way back.</b> After a successful run the backup still holds the original
 *       <b>content</b> — the same content digest the pre-migration store had. The backup is a
 *       consistent snapshot (SQLite's {@code VACUUM INTO}), not a byte copy: the store runs in
 *       WAL mode, and a copy of the main file alone can lose the frames that are not yet
 *       checkpointed into it (the owner's decision on R-EPIS-08, option B).</li>
 *   <li><b>The atomic replace.</b> The migrated store is in place afterwards: the schema
 *       version, the counts and the audit are the published ones.</li>
 *   <li><b>No partial write.</b> The live file is <b>replaced</b>, not written into: its
 *       inode changes (a rename carries the inode of the work file), so a reader can
 *       never see a half-written store. The bytes that were there before are recoverable
 *       from the backup.</li>
 *   <li><b>The WAL.</b> A store whose WAL still holds un-checkpointed frames loses nothing:
 *       the backup is a snapshot, so the WAL's committed row is in it — the regression test
 *       for R-EPIS-08.</li>
 * </ol>
 *
 * <p><b>Why the inode is the probe for "never partially written".</b> A file that is
 * written in place is observable in a half-written state; a file that is <b>renamed</b> over
 * is not — a reader either opens the old inode or the new one, never a mixture. The inode
 * of the live file therefore <b>changes</b> exactly when the launcher replaced it, and the
 * two inodes cannot collide: the work file's inode was allocated while the live file's was
 * still in use.</p>
 *
 * <p><b>Why the class was changed to make this testable.</b> The launcher aborted with
 * {@code System.exit(1)}, which kills the test JVM: the abort path — the very path this
 * test exists for — could not be exercised at all. The aborts are now raised as
 * {@link LiveStoreMigration.Aborted} and {@code main} turns them into the exit code; the
 * log the launcher writes is unchanged. The launcher's own javadoc also promised
 * "the check that the backup still holds the original bytes" and the code never performed
 * it; the check is now performed — before the replacement, so a broken way back stops the
 * run, and after it, so a broken way back is reported. The check is over the backup's
 * <b>content</b>, because the backup is now a consistent snapshot and not a byte copy.</p>
 *
 * <p>The test is <b>skipped, not failed</b>, when the preserved backup is not in the
 * checkout (CI).</p>
 */
@DisplayName("P3 — the owner's live store: the launcher, exercised")
class LiveStoreLauncherTest {

    /** The preserved pre-migration backup the copy is taken from. */
    private static final String BACKUP_NAME =
            "workbench.pre-migration-2026-11-18T16-04-34-729638675Z.sqlite";

    /** The concept id of the row the dirty audit finds. */
    private static final String ORPHAN_CONCEPT = "launcher-probe-orphan";

    @TempDir
    Path directory;

    @Test
    @DisplayName("a dirty audit aborts the launcher before the live file is touched")
    void aDirtyAuditAbortsBeforeTheLiveFileIsTouched() throws Exception {
        Path source = preservedBackup();
        Assumptions.assumeTrue(source != null,
                "the preserved pre-migration backup is not in this checkout — the launcher check "
                        + "is skipped, not failed");

        // The copy of the owner's store, with one row that points at a concept that does
        // not exist: the audit will find it and the launcher must abort.
        Path live = directory.resolve("workbench.sqlite");
        Files.copy(source, live, StandardCopyOption.COPY_ATTRIBUTES);
        seedAnOrphan(live);
        String bytesBefore = sha256(live);
        Object inodeBefore = inode(live);

        assertThatThrownBy(() -> LiveStoreMigration.run(live))
                .as("the launcher on a store with a dangling learning row")
                .isInstanceOf(LiveStoreMigration.Aborted.class)
                .hasMessageContaining("ABORTED")
                .hasMessageContaining("the audit of photosynthesis is not clean")
                .hasMessageContaining("The live store was NOT touched");

        assertThat(sha256(live)).as("the live file's bytes after the abort")
                .isEqualTo(bytesBefore);
        assertThat(inode(live)).as("the live file's inode after the abort")
                .isEqualTo(inodeBefore);
        assertThat(leftovers("workbench.migrating-")).as("the work file after the abort")
                .isEmpty();
        assertThat(leftovers("workbench.rollback-")).as("the rollback copy after the abort")
                .isEmpty();
        // The backup was taken before the abort, and it holds the original CONTENT.
        List<Path> backups = leftovers("workbench.pre-migration-");
        assertThat(backups).as("the backups after the abort").hasSize(1);
        assertThat(LiveStoreMigration.contentDigest(backups.get(0)))
                .as("the backup's content after the abort")
                .isEqualTo(LiveStoreMigration.contentDigest(live));
    }

    @Test
    @DisplayName("a clean run replaces the live file atomically and keeps the original bytes")
    void aCleanRunReplacesTheLiveFileAndKeepsTheWayBack() throws Exception {
        Path source = preservedBackup();
        Assumptions.assumeTrue(source != null,
                "the preserved pre-migration backup is not in this checkout — the launcher check "
                        + "is skipped, not failed");

        Path live = directory.resolve("workbench.sqlite");
        Files.copy(source, live, StandardCopyOption.COPY_ATTRIBUTES);
        String bytesBefore = sha256(live);
        Object inodeBefore = inode(live);

        LiveStoreMigration.Outcome outcome = LiveStoreMigration.run(live);

        // ── the way back: the backup holds the original CONTENT ────────────────
        // The backup is a consistent snapshot (SQLite's VACUUM INTO), so it does NOT
        // have the live file's bytes — it has the live store's content, and the
        // content is what is proved.
        assertThat(outcome.backup()).as("the backup the launcher kept")
                .isRegularFile();
        assertThat(LiveStoreMigration.contentDigest(outcome.backup()))
                .as("the backup's content after the run")
                .isEqualTo(LiveStoreMigration.contentDigest(source));
        assertThat(outcome.originalContentDigest())
                .as("the launcher's record of the original content")
                .isEqualTo(LiveStoreMigration.contentDigest(source));
        assertThat(outcome.originalSha256()).as("the launcher's record of the original bytes")
                .isEqualTo(bytesBefore);

        // ── the atomic replace: the migrated store is in place ─────────────────
        assertThat(outcome.live()).as("the live store the launcher replaced").isEqualTo(live);
        assertThat(inode(live)).as("the live file's inode after the replacement")
                .as("the live file was REPLACED (a rename carries the work file's inode), "
                        + "so it was never written into in place")
                .isNotEqualTo(inodeBefore);
        assertThat(schemaVersion(live)).as("the migrated store's schema version")
                .isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        assertThat(count(live, "concept")).as("the migrated store's concepts").isEqualTo(67);
        assertThat(count(live, "claim")).as("the migrated store's claims").isEqualTo(52);
        assertThat(count(live, "claim_scoped_record")).as("the claim-scoped records")
                .isEqualTo(23);
        assertThat(count(live, "prerequisite")).as("the prerequisites").isEqualTo(9);
        assertThat(count(live, "representation")).as("the representations").isEqualTo(27);
        assertThat(count(live, "concept_lineage")).as("the concept-lineage rows").isEqualTo(26);
        assertThat(count(live, "claim_lineage")).as("the claim-lineage rows").isEqualTo(52);
        assertThat(count(live, "concept_alias")).as("the alias rows").isZero();
        for (String workspace : List.of("logic", "photosynthesis")) {
            ReferentKeyMigration.OrphanAudit audit = ReferentKeyMigration.audit(live, workspace);
            assertThat(audit.totalOrphans()).as("the orphans of %s", workspace).isZero();
            assertThat(audit.totalStale()).as("the stale pointers of %s", workspace).isZero();
        }

        // ── nothing of the run is left behind ──────────────────────────────────
        assertThat(leftovers("workbench.migrating-")).as("the work file after the run")
                .isEmpty();
        assertThat(leftovers("workbench.rollback-")).as("the rollback copy after the run")
                .isEmpty();
        assertThat(leftovers("workbench.migrated-")).as("the intermediate file after the run")
                .isEmpty();
        assertThat(leftovers("workbench.pre-migration-")).as("the backups after the run")
                .hasSize(1);
    }

    @Test
    @DisplayName("the backup holds the content of a store whose WAL is not yet checkpointed")
    void theBackupHoldsTheContentOfAStoreWithAnUncheckpointedWal() throws Exception {
        Path source = preservedBackup();
        Assumptions.assumeTrue(source != null,
                "the preserved pre-migration backup is not in this checkout — the launcher check "
                        + "is skipped, not failed");

        Path live = directory.resolve("workbench.sqlite");
        Files.copy(source, live, StandardCopyOption.COPY_ATTRIBUTES);

        // A row committed in the WAL and not yet checkpointed into the main file. The
        // connection stays OPEN on purpose: closing the last connection to a WAL database
        // checkpoints it, and the defect this reproduces is exactly the state in which the
        // frames are still in the WAL.
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
        Statement statement = connection.createStatement();
        statement.execute("PRAGMA journal_mode=WAL");
        statement.execute("PRAGMA wal_autocheckpoint=0");
        statement.executeUpdate("CREATE TABLE wal_probe (id INTEGER PRIMARY KEY, note TEXT)");
        statement.executeUpdate("INSERT INTO wal_probe (note) VALUES "
                + "('the frame lives in the WAL')");
        Path wal = Path.of(live + "-wal");
        assertThat(Files.exists(wal)).as("the WAL sidecar exists").isTrue();
        assertThat(Files.size(wal)).as("the WAL sidecar holds the un-checkpointed frames")
                .isPositive();
        // The MAIN FILE alone — copied without its sidecar — does not hold the table: the
        // frames are still in the WAL. That is the defect the snapshot fixes.
        Path mainFileOnly = directory.resolve("main-file-only.sqlite");
        Files.copy(live, mainFileOnly, StandardCopyOption.COPY_ATTRIBUTES);
        assertThat(count(mainFileOnly, "wal_probe"))
                .as("the main file alone does NOT hold the WAL's table — the defect is reproduced")
                .isEqualTo(-1);

        LiveStoreMigration.Outcome outcome = LiveStoreMigration.run(live);

        // The backup is a consistent snapshot, so the WAL's committed row is IN it: a
        // main-file-only copy would have lost it.
        assertThat(count(outcome.backup(), "wal_probe"))
                .as("the rows of the table committed in the WAL, as the backup holds them")
                .isEqualTo(1);
        connection.close();
    }

    /**
     * The preserved pre-migration backup.
     *
     * @return the backup, or {@code null} when this checkout holds none
     */
    private static Path preservedBackup() {
        Path named = Path.of("data/knowledge", BACKUP_NAME).toAbsolutePath().normalize();
        if (Files.isRegularFile(named)) {
            return named;
        }
        try (var backups = Files.list(Path.of("data/knowledge"))) {
            return backups.filter(path -> path.getFileName().toString()
                            .startsWith("workbench.pre-migration-")
                            && path.getFileName().toString().endsWith(".sqlite"))
                    .max(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize())
                    .orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Seeds a row that points at a concept that does not exist.
     *
     * <p>The learning tables are not touched by the copy-forward beyond their
     * {@code concept_id}, and the audit walks every table that carries one, so this is the
     * cheapest way to make the audit dirty without corrupting the knowledge core.</p>
     *
     * @param live the store to dirty
     */
    private static void seedAnOrphan(Path live) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO hebbian_binding(workspace_id, concept_id, weights, "
                    + "accumulator, updated_at) SELECT workspace_id, '" + ORPHAN_CONCEPT + "', "
                    + "weights, accumulator, updated_at FROM hebbian_binding "
                    + "WHERE workspace_id = 'photosynthesis'");
        }
    }

    /**
     * The files in the run's directory whose name starts with a prefix.
     *
     * @param prefix the prefix
     * @return the files
     */
    private List<Path> leftovers(String prefix) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .toList();
        }
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

    /** A count over a table — {@code -1} when the table does not exist. */
    private static int count(Path database, String table) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getInt(1) : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    /** The sha256 of a file's bytes. */
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** The inode of a file — the probe for "the file was replaced, not written into". */
    private static Object inode(Path path) throws IOException {
        return Files.getAttribute(path, "unix:ino");
    }
}
