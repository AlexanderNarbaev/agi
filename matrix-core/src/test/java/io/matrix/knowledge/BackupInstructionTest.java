package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.0 — the backup instruction must produce a backup.
 *
 * <p>The Persistence screen used to say: *"none: the store is a single SQLite
 * file. A copy of workbench.sqlite is the backup, and taking one is the
 * operator's job."* The store runs in WAL mode, so the committed rows may
 * live in the write-ahead log rather than in the database file. An
 * experiment on this wave's own store showed the naive copy losing **every
 * assessment outcome and every asked question** — the records the same
 * screen calls *"the evidence that something was learned"*.
 *
 * <p>These tests make the instruction and the store agree. The first test
 * fails if the instruction ever stops naming the write-ahead log; the
 * second fails if a copy taken the way the instruction says loses anything.
 */
class BackupInstructionTest {

    @Test
    @DisplayName("the backup instruction names the write-ahead log, not just the database file")
    void theBackupInstructionNamesTheWriteAheadLog(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            Map<String, Object> integrity = workbench.integrity();

            String instruction = (String) integrity.get("backupState");
            assertThat(instruction).contains("-wal");
            assertThat(instruction).contains("-shm");
            assertThat(instruction).doesNotContain("is the backup");

            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) integrity.get("backupFiles");
            assertThat(files).containsExactly("w.sqlite", "w.sqlite-wal", "w.sqlite-shm");
        }
    }

    @Test
    @DisplayName("a copy taken the way the instruction says loses nothing")
    void aCopyTakenTheWayTheInstructionSaysLosesNothing(@TempDir Path dir) throws Exception {
        Path live = dir.resolve("live.sqlite");
        Path backup = dir.resolve("backup");

        // The copy is taken while the store is OPEN, which is the situation
        // the operator is in: the application is running. SQLite checkpoints
        // and removes the write-ahead log when the last connection closes
        // cleanly, so a copy taken after a graceful shutdown is safe by
        // accident - and a copy taken after a crash is not safe at all.
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(live.toString());
        try {
            workbench.loadBundledPackage("photosynthesis");
            workbench.assess("photosynthesis", "e-recall-1", "light carbon dioxide water sugar",
                    0.8, "backup-test");
            workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);

            Files.createDirectories(backup);
            for (String suffix : List.of("", "-wal", "-shm")) {
                Path from = live.resolveSibling(live.getFileName() + suffix);
                if (Files.exists(from)) {
                    Files.copy(from, backup.resolve(live.getFileName() + suffix),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            workbench.close();
        }

        Map<String, Integer> liveCounts = counts(live);
        Map<String, Integer> backupCounts = counts(backup.resolve(live.getFileName()));

        assertThat(liveCounts.get("assessment_outcome")).isGreaterThan(0);
        assertThat(liveCounts.get("asked_question")).isGreaterThan(0);
        // The backup taken the way the instruction says holds the same rows
        // as the live store - including the ones the write-ahead log held.
        assertThat(backupCounts.get("assessment_outcome"))
                .isEqualTo(liveCounts.get("assessment_outcome"));
        assertThat(backupCounts.get("asked_question"))
                .isEqualTo(liveCounts.get("asked_question"));
        assertThat(backupCounts.get("claim")).isEqualTo(liveCounts.get("claim"));
    }

    /** The row count of every table, for the comparison. */
    private Map<String, Integer> counts(Path database) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            // A SEPARATE statement for the counts: executing a query on the
            // same statement resets the cursor of the result set it is
            // reading, which silently truncates the table list.
            //
            // A table that is not in the database at all counts as -1: the
            // naive copy of a store whose schema has never been checkpointed
            // has no tables at all, and "the table is missing" is the
            // strongest possible form of "the rows are missing".
            try (Statement counter = connection.createStatement()) {
                for (String table : tables) {
                    try (ResultSet rs = counter.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        rs.next();
                        counts.put(table, rs.getInt(1));
                    }
                }
            }
            for (String table : List.of("assessment_outcome", "asked_question", "claim")) {
                counts.putIfAbsent(table, -1);
            }
        }
        return counts;
    }

    @Test
    @DisplayName("a copy of the database file ALONE does lose the uncheckpointed rows")
    void aCopyOfTheDatabaseFileAloneDoesLoseTheUncheckpointedRows(@TempDir Path dir) throws Exception {
        // This is the failure the instruction used to cause. It is recorded as
        // a test so that the reason the instruction names three files is not
        // forgotten - and so that if SQLite's default ever changes, the
        // difference is noticed rather than assumed.
        //
        // The copy is taken while the store is open, because that is when the
        // write-ahead log holds rows that the database file does not.
        Path live = dir.resolve("live.sqlite");
        Path naive = dir.resolve("naive.sqlite");

        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(live.toString());
        Map<String, Integer> liveCounts;
        try {
            workbench.loadBundledPackage("photosynthesis");
            workbench.assess("photosynthesis", "e-recall-1", "light carbon dioxide water sugar",
                    0.8, "naive-test");

            Files.copy(live, naive, StandardCopyOption.REPLACE_EXISTING);
            liveCounts = counts(live);
        } finally {
            workbench.close();
        }

        Map<String, Integer> naiveCounts = counts(naive);

        assertThat(liveCounts.get("assessment_outcome")).isGreaterThan(0);
        // The naive copy has fewer assessment outcomes than the live store.
        // When the schema has never been checkpointed the copy has no table
        // at all, and -1 says exactly that.
        assertThat(naiveCounts.get("assessment_outcome"))
                .isLessThan(liveCounts.get("assessment_outcome"));
    }

    @Test
    @DisplayName("the store is in WAL mode, which is why the instruction names three files")
    void theStoreIsInWalMode(@TempDir Path dir) throws Exception {
        Path live = dir.resolve("wal.sqlite");
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(live.toString());
        try {
            workbench.loadBundledPackage("photosynthesis");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
                    Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
                }
            }
            assertThat(Files.exists(live.resolveSibling("wal.sqlite-wal"))).isTrue();
        } finally {
            workbench.close();
        }
    }

    @Test
    @DisplayName("the warning appears when the write-ahead log holds committed rows")
    void theWarningAppearsWhenTheWriteAheadLogHoldsCommittedRows(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.assess("photosynthesis", "e-recall-1", "light carbon dioxide water sugar",
                    0.8, "warning-test");

            Map<String, Object> integrity = workbench.integrity();

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) integrity.get("recoveryWarnings");
            assertThat(warnings).anySatisfy(warning ->
                    assertThat(warning).contains("-wal"));
            assertThat((long) integrity.get("writeAheadLogBytes")).isGreaterThan(0L);
        }
    }
}
