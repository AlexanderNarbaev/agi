package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W48-08 — the measurement engine of the restore drill must hold on its own.
 *
 * <p>The drill in {@code scripts/restore-drill.sh} measures the owner's live database and
 * draws the conclusion that a restore works. A measurement that is only ever run against
 * the owner's data is a measurement nobody can check: it could be wrong in a way that only
 * shows itself on the day it matters. So the engine is tested here on databases the test
 * builds itself — the properties the drill relies on are asserted where they can be
 * falsified.</p>
 *
 * <p>The four properties are the ones the drill's verdict rests on:</p>
 * <ul>
 *   <li>the backup API holds the rows the write-ahead log holds, and a plain copy of the
 *       main file does not — this is what makes the copy WAL-safe;</li>
 *   <li>the current code migrates the pre-v17 image forward and the migrated image holds
 *       exactly what the image held — this is what makes the copy restorable;</li>
 *   <li>the second writer is refused while the first holds the lock, and the lock file is
 *       gone after a clean shutdown — this is what makes the copy safe to open;</li>
 *   <li>the fingerprint changes when a row changes — this is what makes the fingerprint
 *       worth comparing.</li>
 * </ul>
 */
class RestoreDrillProbeTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("the backup holds the rows the write-ahead log holds, and a plain copy does not")
    void theBackupHoldsTheRowsTheWriteAheadLogHolds() throws Exception {
        Path live = directory.resolve("live.sqlite");
        Path backup = directory.resolve("backup.sqlite");
        Path naive = directory.resolve("naive.sqlite");

        // The store is OPEN while the copy is taken, which is the situation the operator
        // is in: the rows that were just written are in the write-ahead log and not yet
        // in the database file.
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(live.toString());
        Map<String, Object> liveCounts;
        try {
            workbench.loadBundledPackage("photosynthesis");
            workbench.assess("photosynthesis", "e-recall-1",
                    "light carbon dioxide water sugar", 0.8, "restore-drill-probe");
            liveCounts = RestoreDrillProbe.measure(live);

            RestoreDrillProbe.backup(live, backup);
            Files.copy(live, naive, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            workbench.close();
        }

        Map<String, Object> backupCounts = RestoreDrillProbe.measure(backup);
        Map<String, Object> naiveCounts = RestoreDrillProbe.measure(naive);

        assertThat(backupCounts.get("integrity_check")).isEqualTo("ok");
        assertThat(backupCounts.get("row_counts"))
                .as("the backup holds exactly the rows the live database holds")
                .isEqualTo(liveCounts.get("row_counts"));
        assertThat(backupCounts.get("fingerprints"))
                .as("the backup holds exactly the rows the live database holds")
                .isEqualTo(liveCounts.get("fingerprints"));
        assertThat(((Number) naiveCounts.get("row_count_total")).longValue())
                .as("a plain copy of the main file does not hold the rows the log holds")
                .isLessThan(((Number) liveCounts.get("row_count_total")).longValue());
    }

    @Test
    @DisplayName("the current code migrates the pre-v17 image forward and holds what it held")
    void theCurrentCodeMigratesThePreV17ImageForward() throws Exception {
        Path current = directory.resolve("current.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(current.toString())) {
            workbench.loadBundledPackage("photosynthesis");
        }
        Map<String, Object> before = RestoreDrillProbe.measure(current);

        Path image = directory.resolve("pre-v17.sqlite");
        Files.copy(current, image, StandardCopyOption.REPLACE_EXISTING);
        Map<String, Object> derivation = RestoreDrillProbe.downgradeToV16(image);

        @SuppressWarnings("unchecked")
        java.util.List<String> dropped = (java.util.List<String>) derivation.get("dropped_tables");
        assertThat(dropped)
                .contains("observation_disposition", "observation_occurrence",
                        "observation_adjudication", "workspace_snapshot");
        assertThat(derivation.get("measurement"))
                .as("the derived image is at the older schema version")
                .isNotNull();

        Map<String, Object> report = RestoreDrillProbe.openUnderCurrentCode(image);

        assertThat(report.get("schema_version_before")).isEqualTo(16);
        assertThat(report.get("schema_version_after")).isEqualTo(19);
        assertThat(report.get("migration_applied")).isEqualTo(true);
        assertThat(report.get("integrity_check")).isEqualTo("ok");
        assertThat(report.get("dangling_reference_count")).isEqualTo(0);
        assertThat(RestoreDrillProbe.compare(before, measurementOf(report)))
                .as("the migrated image holds exactly what the current image held")
                .isEmpty();
    }

    @Test
    @DisplayName("the second writer is refused, and the lock file is gone after the shutdown")
    void theSecondWriterIsRefusedAndTheLockFileIsGone() throws Exception {
        Path database = directory.resolve("locked.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            workbench.loadBundledPackage("photosynthesis");
        }

        Map<String, Object> report = RestoreDrillProbe.openUnderCurrentCode(database);

        assertThat(report.get("second_writer_refused"))
                .as("one writer process per database file is the contract")
                .isEqualTo(true);
        assertThat(report.get("second_writer_exit_code")).isEqualTo(RestoreDrillProbe.EXIT_REFUSED);
        assertThat((String) report.get("second_writer_message"))
                .contains("held by another writer");
        assertThat(report.get("application_started")).isEqualTo(true);
        assertThat(report.get("application_stopped_cleanly")).isEqualTo(true);
        assertThat(report.get("lock_file_present_while_writing")).isEqualTo(true);
        assertThat(report.get("lock_file_present_after_close"))
                .as("a clean shutdown leaves no lock file behind")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("the fingerprint changes when a row changes")
    void theFingerprintChangesWhenARowChanges() throws Exception {
        Path database = directory.resolve("fingerprinted.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            workbench.loadBundledPackage("photosynthesis");
        }
        Map<String, Object> before = RestoreDrillProbe.measure(database);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE concept SET name = name || ' (revised)' WHERE id = "
                    + "(SELECT id FROM concept ORDER BY id LIMIT 1)");
        }
        Map<String, Object> after = RestoreDrillProbe.measure(database);

        assertThat(after.get("fingerprints"))
                .as("a changed row must change the fingerprint of its table")
                .isNotEqualTo(before.get("fingerprints"));
        assertThat(RestoreDrillProbe.compare(before, after))
                .isNotEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> measurementOf(Map<String, Object> report) {
        return (Map<String, Object>) report.get("measurement_after");
    }
}
