package io.matrix.knowledge;

import io.matrix.cognitive.CognitiveWriterConflictException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W48-08 — the measurement engine of the restore drill.
 *
 * <p><b>What the drill is.</b> The recovery runbook describes how to back the epistemic
 * database up and how to restore it, and it says in §5 that the restore has never been
 * performed. A procedure that has never been performed is a procedure, not a capability —
 * so this engine performs it, and it performs it <b>against a copy</b>.</p>
 *
 * <p><b>Why it is an engine and not a script.</b> Half of the drill is arithmetic over
 * SQLite — row counts, per-table fingerprints, integrity — and the other half is the
 * current code's own behaviour: the additive migration, the dangling references, the writer
 * lock, the clean shutdown. The second half can only be measured by the current code
 * itself, so the drill needs a program that <b>is</b> the current code and reports what it
 * sees. This class is that program; {@code scripts/restore-drill.sh} is the procedure that
 * drives it.</p>
 *
 * <p><b>The rule that makes the drill safe.</b> The live database is opened READ-ONLY, and
 * only through a read-only URI. A read-only connection cannot checkpoint the write-ahead
 * log into the database file, so the drill cannot change a byte of the original even by
 * accident — and the drill verifies that claim by digest before and after. The application
 * itself is never pointed at the original: the store migrates on open, and a migration of the
 * owner's live database during a drill would be exactly the damage the drill exists to
 * prevent (this is the W47 incident recorded in {@code docs-v2/engineering/W48-BASELINE.md}
 * §3).</p>
 *
 * <p><b>What each command does.</b></p>
 * <ul>
 *   <li>{@code backup} — the WAL-safe copy. The source is opened read-only and the copy is
 *       produced with {@code VACUUM INTO}, the SQLite backup API, so the copy is a complete,
 *       self-contained image of the live database <b>including</b> the rows that the
 *       write-ahead log still holds.</li>
 *   <li>{@code measure} — the fidelity measurement: the integrity check, the schema version,
 *       the table list, the row count of every table, and a per-table fingerprint.</li>
 *   <li>{@code compare} — the comparison of two measurements. It exits non-zero when they
 *       differ, so the drill cannot report a match it did not find.</li>
 *   <li>{@code open} — the current-code compatibility: the store opens the database, the
 *       additive migration runs if the file is behind, the integrity check runs again, the
 *       dangling references are counted, a second writer is refused while the first holds the
 *       lock, and the application shuts down cleanly.</li>
 *   <li>{@code downgrade-v16} — the derivation of the pre-v17 image of a database, so that
 *       the additive migration path can be exercised on the drill's own data.</li>
 * </ul>
 *
 * <p><b>The fingerprint.</b> A row count says how many rows a table holds; it does not say
 * <i>which</i> rows. The fingerprint is the digest of the table's rows in a canonical
 * encoding — every cell typed, every row sorted — so two databases with the same row counts
 * and the same fingerprints hold the same content, whatever order either of them returns the
 * rows in.</p>
 */
public final class RestoreDrillProbe {

    /** The command that measures a database and writes the measurement as JSON. */
    private static final String COMMAND_MEASURE = "measure";

    /** The command that compares two measurements. */
    private static final String COMMAND_COMPARE = "compare";

    /** The command that opens a database under the current code. */
    private static final String COMMAND_OPEN = "open";

    /** The command that produces the WAL-safe copy. */
    private static final String COMMAND_BACKUP = "backup";

    /** The command that derives the pre-v17 image of a database. */
    private static final String COMMAND_DOWNGRADE = "downgrade-v16";

    /** The command a forked second writer runs. */
    private static final String COMMAND_SECOND_WRITER = "second-writer";

    /** The exit code of a refused second writer: the refusal is the expected outcome. */
    public static final int EXIT_REFUSED = 3;

    /** The exit code of a second writer that was NOT refused: the drill's failure. */
    public static final int EXIT_NOT_REFUSED = 4;

    /** The exit code of a command that failed for a reason of its own. */
    public static final int EXIT_FAILED = 5;

    /** The tables that the migrations from v16 to v19 add. */
    private static final List<String> TABLES_ADDED_AFTER_V16 = List.of(
            "observation_disposition",
            "observation_occurrence",
            "observation_adjudication",
            "workspace_snapshot");

    /** The schema version the derived pre-v17 image carries. */
    private static final int VERSION_V16 = 16;

    /** The separator between the cells of a canonical row. */
    private static final String CELL_SEPARATOR = "\u001f";

    /** The separator between the canonical rows of a table. */
    private static final String ROW_SEPARATOR = "\u001e";

    private RestoreDrillProbe() {
    }

    // ── the entry point ───────────────────────────────────────────────────────

    /**
     * The probe's entry point.
     *
     * @param args the command and its options
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        String command = args[0];
        try {
            int exit;
            switch (command) {
                case COMMAND_BACKUP -> exit = backupCommand(args);
                case COMMAND_MEASURE -> exit = measureCommand(args);
                case COMMAND_COMPARE -> exit = compareCommand(args);
                case COMMAND_OPEN -> exit = openCommand(args);
                case COMMAND_DOWNGRADE -> exit = downgradeCommand(args);
                case COMMAND_SECOND_WRITER -> exit = secondWriterCommand(args);
                default -> {
                    usage();
                    exit = 2;
                }
            }
            System.exit(exit);
        } catch (Exception e) {
            System.err.println("the probe failed: " + e);
            e.printStackTrace(System.err);
            System.exit(EXIT_FAILED);
        }
    }

    private static void usage() {
        System.err.println("usage: RestoreDrillProbe <command> [options]");
        System.err.println("  backup --source <db> --target <db>");
        System.err.println("  measure --db <db> [--out <json>]");
        System.err.println("  compare --a <json> --b <json>");
        System.err.println("  open --db <db> [--expected <json>] [--out <json>]");
        System.err.println("  downgrade-v16 --db <db> [--out <json>]");
        System.err.println("  second-writer --db <db>");
    }

    // ── the commands ──────────────────────────────────────────────────────────

    /**
     * The WAL-safe copy.
     *
     * <p>The source is opened READ-ONLY and the copy is produced with {@code VACUUM INTO}:
     * the copy is a complete, self-contained image of the database as it stands, including
     * the rows the write-ahead log still holds. A plain copy of the main file is NOT that —
     * it is the state of the last checkpoint, and the drill measures the difference.</p>
     *
     * @param args the options
     * @return the exit code
     * @throws Exception when the copy cannot be produced
     */
    private static int backupCommand(String[] args) throws Exception {
        Path source = Paths.get(requiredOption(args, "--source"));
        Path target = Paths.get(requiredOption(args, "--target"));
        Map<String, Object> report = backup(source, target);
        System.out.println(json(report));
        return 0;
    }

    /**
     * The WAL-safe copy of a database.
     *
     * @param source the database
     * @param target where the copy is written
     * @return the report of the copy
     * @throws Exception when the copy cannot be produced
     */
    static Map<String, Object> backup(Path source, Path target) throws Exception {
        if (Files.exists(target)) {
            Files.delete(target);
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (Connection connection = readOnly(source);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("method", "VACUUM INTO (the SQLite backup API) over a read-only connection");
        report.put("source", source.toAbsolutePath().toString());
        report.put("source_sha256", sha256(source));
        report.put("target", target.toAbsolutePath().toString());
        report.put("target_sha256", sha256(target));
        report.put("target_bytes", Files.size(target));
        return report;
    }

    /**
     * The fidelity measurement of a database.
     *
     * @param args the options
     * @return the exit code
     * @throws Exception when the measurement fails
     */
    private static int measureCommand(String[] args) throws Exception {
        Path database = Paths.get(requiredOption(args, "--db"));
        Map<String, Object> measurement = measure(database);
        String rendered = json(measurement);
        writeIfRequested(args, "--out", rendered);
        System.out.println(rendered);
        return 0;
    }

    /**
     * The comparison of two measurements.
     *
     * @param args the options
     * @return zero when they agree, one when they do not
     * @throws Exception when a measurement cannot be read
     */
    private static int compareCommand(String[] args) throws Exception {
        Map<String, Object> left = readMeasurement(Paths.get(requiredOption(args, "--a")));
        Map<String, Object> right = readMeasurement(Paths.get(requiredOption(args, "--b")));
        List<String> differences = compare(left, right);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("identical", differences.isEmpty());
        report.put("differences", differences);
        System.out.println(json(report));
        return differences.isEmpty() ? 0 : 1;
    }

    /**
     * The current-code compatibility of a database.
     *
     * @param args the options
     * @return zero when everything held, one when something did not
     * @throws Exception when the check fails
     */
    private static int openCommand(String[] args) throws Exception {
        Path database = Paths.get(requiredOption(args, "--db"));
        Map<String, Object> expected = null;
        String expectedPath = optionalOption(args, "--expected");
        if (expectedPath != null) {
            expected = readMeasurement(Paths.get(expectedPath));
        }
        Map<String, Object> report = openUnderCurrentCode(database);
        List<String> differences = new ArrayList<>();
        if (expected != null) {
            differences.addAll(compare(expected, measurementOf(report)));
        }
        report.put("matches_expected", differences.isEmpty());
        report.put("differences_from_expected", differences);
        String rendered = json(report);
        writeIfRequested(args, "--out", rendered);
        System.out.println(rendered);
        boolean held = "ok".equals(report.get("integrity_check"))
                && Integer.valueOf(0).equals(report.get("dangling_reference_count"))
                && Boolean.TRUE.equals(report.get("application_started"))
                && Boolean.TRUE.equals(report.get("application_stopped_cleanly"))
                && Boolean.TRUE.equals(report.get("second_writer_refused"))
                && differences.isEmpty();
        return held ? 0 : 1;
    }

    /**
     * The derivation of the pre-v17 image of a database.
     *
     * <p>The migration path from v16 to v19 can only be exercised on a database that is at
     * v16, and the drill's own snapshot is at v19. The image is therefore DERIVED: the
     * tables the migrations after v16 added are dropped and the version is set back to v16.
     * The derivation is additive in reverse — it removes exactly what v17, v18 and v19
     * added — and the drill reports the derived image as a fixture, never as the
     * snapshot.</p>
     *
     * @param args the options
     * @return the exit code
     * @throws Exception when the derivation fails
     */
    private static int downgradeCommand(String[] args) throws Exception {
        Path database = Paths.get(requiredOption(args, "--db"));
        Map<String, Object> report = downgradeToV16(database);
        String rendered = json(report);
        writeIfRequested(args, "--out", rendered);
        System.out.println(rendered);
        return 0;
    }

    /**
     * The derivation of the pre-v17 image of a database.
     *
     * @param database the database
     * @return the report of the derivation
     * @throws Exception when the derivation fails
     */
    static Map<String, Object> downgradeToV16(Path database) throws Exception {
        List<String> dropped = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            for (String table : TABLES_ADDED_AFTER_V16) {
                if (tableExists(connection, table)) {
                    statement.execute("DROP TABLE \"" + table + "\"");
                    dropped.add(table);
                }
            }
            statement.execute("UPDATE schema_meta SET value='" + VERSION_V16 + "' WHERE key='version'");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("database", database.toAbsolutePath().toString());
        report.put("dropped_tables", dropped);
        report.put("derived_version", VERSION_V16);
        report.put("measurement", measure(database));
        return report;
    }

    /**
     * The forked second writer.
     *
     * <p>The contract of the store is ONE WRITER PROCESS per database file, and the refusal
     * of a second writer is the guard that makes the recovery of an interrupted request
     * safe. Within one process a second store JOINS the lock — the contract is about
     * processes — so the refusal can only be observed from a second process. This command is
     * what that process runs.</p>
     *
     * @param args the options
     * @return {@link #EXIT_REFUSED} when the writer was refused
     */
    private static int secondWriterCommand(String[] args) {
        Path database = Paths.get(requiredOption(args, "--db"));
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            System.err.println("THE SECOND WRITER WAS NOT REFUSED: the store opened "
                    + database + " while another writer held it");
            return EXIT_NOT_REFUSED;
        } catch (CognitiveWriterConflictException refusal) {
            System.err.println(refusal.getMessage());
            return EXIT_REFUSED;
        } catch (RuntimeException e) {
            System.err.println("the second writer failed for another reason: " + e);
            return EXIT_FAILED;
        }
    }

    // ── the measurement ───────────────────────────────────────────────────────

    /**
     * The fidelity measurement of a database.
     *
     * @param database the database
     * @return the measurement
     * @throws Exception when the database cannot be read
     */
    static Map<String, Object> measure(Path database) throws Exception {
        Map<String, Object> measurement = new LinkedHashMap<>();
        measurement.put("database", database.toAbsolutePath().toString());
        measurement.put("bytes", Files.size(database));
        measurement.put("sha256", sha256(database));
        try (Connection connection = readOnly(database)) {
            measurement.put("journal_mode", scalar(connection, "PRAGMA journal_mode"));
            measurement.put("integrity_check", scalar(connection, "PRAGMA integrity_check"));
            measurement.put("schema_version", schemaVersion(connection));
            List<String> tables = tables(connection);
            measurement.put("table_count", tables.size());
            measurement.put("tables", tables);
            Map<String, Long> counts = new LinkedHashMap<>();
            Map<String, String> fingerprints = new LinkedHashMap<>();
            long total = 0;
            for (String table : tables) {
                long count = rowCount(connection, table);
                counts.put(table, count);
                fingerprints.put(table, fingerprint(connection, table));
                total += count;
            }
            measurement.put("row_count_total", total);
            measurement.put("row_counts", counts);
            measurement.put("fingerprints", fingerprints);
            measurement.put("schema_digest", schemaDigest(connection));
        }
        return measurement;
    }

    /**
     * The comparison of two measurements.
     *
     * <p>Every difference is named. A comparison that only said "not identical" would leave
     * the reader to guess what differed, and a drill that reports its own differences is
     * worth more than one that reports a verdict.</p>
     *
     * @param left  the first measurement
     * @param right the second measurement
     * @return the differences, empty when they agree
     */
    static List<String> compare(Map<String, Object> left, Map<String, Object> right) {
        List<String> differences = new ArrayList<>();
        compareScalar(differences, left, right, "integrity_check");
        compareScalar(differences, left, right, "schema_version");
        compareScalar(differences, left, right, "table_count");
        compareScalar(differences, left, right, "row_count_total");
        compareScalar(differences, left, right, "schema_digest");
        compareList(differences, left, right, "tables");
        compareMap(differences, left, right, "row_counts");
        compareMap(differences, left, right, "fingerprints");
        // The journal mode is deliberately NOT compared: the copy is a self-contained
        // image without a write-ahead log, so its mode is `delete` while the live
        // database's is `wal`. Both measurements record the mode, and the drill prints
        // both — but a difference of mode is not a difference of content.
        return differences;
    }

    private static void compareScalar(List<String> differences,
                                       Map<String, Object> left, Map<String, Object> right, String key) {
        Object a = left.get(key);
        Object b = right.get(key);
        // A number read back from a JSON document is a Long, and a number measured
        // live is an Integer or a Long; the comparison is about the VALUE, not
        // about the box the value happens to sit in.
        if (a instanceof Number numberA && b instanceof Number numberB) {
            if (numberA.doubleValue() != numberB.doubleValue()) {
                differences.add(key + ": " + a + " != " + b);
            }
            return;
        }
        if (a == null ? b != null : !a.equals(b)) {
            differences.add(key + ": " + a + " != " + b);
        }
    }

    private static void compareList(List<String> differences,
                                    Map<String, Object> left, Map<String, Object> right, String key) {
        Object a = left.get(key);
        Object b = right.get(key);
        if (a == null ? b != null : !a.equals(b)) {
            differences.add(key + ": " + a + " != " + b);
        }
    }

    private static void compareMap(List<String> differences,
                                   Map<String, Object> left, Map<String, Object> right, String key) {
        Object a = left.get(key);
        Object b = right.get(key);
        if (a instanceof Map<?, ?> mapA && b instanceof Map<?, ?> mapB) {
            List<String> keys = new ArrayList<>();
            for (Object name : mapA.keySet()) {
                keys.add(String.valueOf(name));
            }
            for (Object other : mapB.keySet()) {
                if (!keys.contains(String.valueOf(other))) {
                    keys.add(String.valueOf(other));
                }
            }
            java.util.Collections.sort(keys);
            for (String name : keys) {
                Object valueA = mapA.get(name);
                Object valueB = mapB.get(name);
                if (valueA == null ? valueB != null : !valueA.equals(valueB)) {
                    differences.add(key + "[" + name + "]: " + valueA + " != " + valueB);
                }
            }
        } else if (a == null ? b != null : !a.equals(b)) {
            differences.add(key + ": " + a + " != " + b);
        }
    }

    // ── the current code ──────────────────────────────────────────────────────

    /**
     * Opens a database under the current code and measures what the current code does.
     *
     * @param database the database
     * @return the report
     * @throws Exception when the check fails
     */
    static Map<String, Object> openUnderCurrentCode(Path database) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("database", database.toAbsolutePath().toString());
        int before = schemaVersion(readOnly(database));
        report.put("schema_version_before", before);
        report.put("state_before", summaryOf(measure(database)));

        boolean started = false;
        boolean stopped = false;
        int after = before;
        String refusal = "";
        int refusalExit = 0;
        boolean refused = false;
        KnowledgeWorkbench workbench = null;
        try {
            workbench = KnowledgeWorkbench.at(database.toAbsolutePath().toString());
            workbench.integrity();
            started = true;
            after = workbench.store().schemaVersion();
            Path lockFile = lockFileOf(database);
            report.put("lock_file", lockFile.toAbsolutePath().toString());
            report.put("lock_file_present_while_writing", Files.exists(lockFile));
            ProcessResult second = secondWriter(database);
            refusalExit = second.exitCode();
            refusal = second.output();
            refused = second.exitCode() == EXIT_REFUSED;
        } finally {
            if (workbench != null) {
                workbench.close();
                stopped = true;
            }
        }
        report.put("schema_version_after", after);
        report.put("migration_applied", after != before);
        report.put("migration_path", before + " -> " + after);
        report.put("application_started", started);
        report.put("application_stopped_cleanly", stopped);
        report.put("lock_file_present_after_close", Files.exists(lockFileOf(database)));
        report.put("second_writer_refused", refused);
        report.put("second_writer_exit_code", refusalExit);
        report.put("second_writer_message", refusal.trim());

        Map<String, Object> after2 = measure(database);
        report.put("integrity_check", after2.get("integrity_check"));
        report.put("measurement_after", after2);
        List<String> dangling = danglingReferences(database);
        report.put("dangling_reference_count", dangling.size());
        report.put("dangling_references", dangling);
        return report;
    }

    /**
     * The summary of a measurement: the numbers an operator reads first.
     *
     * @param measurement the measurement
     * @return the summary
     */
    private static Map<String, Object> summaryOf(Map<String, Object> measurement) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sha256", measurement.get("sha256"));
        summary.put("bytes", measurement.get("bytes"));
        summary.put("journal_mode", measurement.get("journal_mode"));
        summary.put("integrity_check", measurement.get("integrity_check"));
        summary.put("schema_version", measurement.get("schema_version"));
        summary.put("table_count", measurement.get("table_count"));
        summary.put("row_count_total", measurement.get("row_count_total"));
        summary.put("schema_digest", measurement.get("schema_digest"));
        return summary;
    }

    /**
     * The measurement of a report, so that a report can be compared with a measurement.
     *
     * @param report the report
     * @return the measurement the report carries
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> measurementOf(Map<String, Object> report) {
        Object measurement = report.get("measurement_after");
        return measurement instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /**
     * The dangling references of a database.
     *
     * <p>The schema declares no foreign keys, so nothing in the database itself would ever
     * report a row that points at a row that is not there. The drill has to ask the question
     * itself, and the map below is the answer: every column that names another row's
     * identifier, and the table that identifier belongs to.</p>
     *
     * @param database the database
     * @return the dangling references, empty when there are none
     * @throws Exception when the database cannot be read
     */
    static List<String> danglingReferences(Path database) throws Exception {
        List<String> dangling = new ArrayList<>();
        try (Connection connection = readOnly(database)) {
            for (String[] reference : REFERENCES) {
                String table = reference[0];
                String column = reference[1];
                String target = reference[2];
                if (!tableExists(connection, table) || !tableExists(connection, target)) {
                    continue;
                }
                if (!columnsOf(connection, table).contains(column)) {
                    continue;
                }
                String sql = "SELECT COUNT(*) FROM \"" + table + "\" WHERE \"" + column
                        + "\" IS NOT NULL AND \"" + column + "\" NOT IN (SELECT id FROM \""
                        + target + "\")";
                long count = scalarLong(connection, sql);
                if (count > 0) {
                    dangling.add(table + "." + column + " -> " + target + ": " + count + " row(s)");
                }
            }
            // The revision's target is named by its type, so the table it points at is
            // decided by the row rather than by the column.
            if (tableExists(connection, "revision") && tableExists(connection, "concept")) {
                long count = scalarLong(connection,
                        "SELECT COUNT(*) FROM revision WHERE target_type='concept' AND target_id NOT IN "
                                + "(SELECT id FROM concept)");
                if (count > 0) {
                    dangling.add("revision.target_id -> concept: " + count + " row(s)");
                }
            }
        }
        return dangling;
    }

    /**
     * The columns of every table that point at another row.
     *
     * <p>Each entry is {@code {the referring table, the referring column, the referred
     * table}}.</p>
     */
    private static final String[][] REFERENCES = {
            {"claim", "concept_id", "concept"},
            {"claim", "introduced_by_source_id", "source"},
            {"evidence", "claim_id", "claim"},
            {"evidence", "source_id", "source"},
            {"evidence", "case_id", "epistemic_case"},
            {"representation", "concept_id", "concept"},
            {"prerequisite", "concept_id", "concept"},
            {"prerequisite", "requires_concept_id", "concept"},
            {"epistemic_case", "concept_id", "concept"},
            {"epistemic_case", "source_id", "source"},
            {"observation_occurrence", "case_id", "epistemic_case"},
            {"observation_occurrence", "source_id", "source"},
            {"observation_occurrence", "evidence_id", "evidence"},
            {"observation_disposition", "case_id", "epistemic_case"},
            {"observation_adjudication", "case_id", "epistemic_case"},
            {"derivation", "conclusion_claim_id", "claim"},
            {"derivation_premise", "derivation_id", "derivation"},
            {"derivation_premise", "claim_id", "claim"},
            {"contradiction", "claim_a_id", "claim"},
            {"contradiction", "claim_b_id", "claim"},
            {"learning_state", "concept_id", "concept"},
            {"learning_transition", "concept_id", "concept"},
            {"learning_transition", "outcome_id", "assessment_outcome"},
            {"learning_error", "item_id", "assessment_item"},
            {"learning_error", "concept_id", "concept"},
            {"assessment_item", "concept_id", "concept"},
            {"assessment_outcome", "item_id", "assessment_item"},
            {"assessment_outcome", "concept_id", "concept"},
            {"hebbian_binding", "concept_id", "concept"},
            {"learning_goal_subtask", "goal_id", "learning_goal"},
            {"workspace_snapshot", "workspace_id", "workspace"},
            {"cognitive_event", "episode_id", "cognitive_episode"},
            {"cognitive_prediction", "episode_id", "cognitive_episode"},
            {"cognitive_error", "prediction_id", "cognitive_prediction"},
    };

    // ── the second writer ─────────────────────────────────────────────────────

    /**
     * The result of a forked process.
     *
     * @param exitCode the exit code
     * @param output   what it printed
     */
    record ProcessResult(int exitCode, String output) {
    }

    /**
     * Forks a second writer and waits for it.
     *
     * @param database the database the second writer tries to open
     * @return the result of the fork
     * @throws Exception when the fork fails
     */
    private static ProcessResult secondWriter(Path database) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(
                java,
                "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"),
                RestoreDrillProbe.class.getName(),
                COMMAND_SECOND_WRITER,
                "--db", database.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new ProcessResult(exit, output);
    }

    // ── the SQLite helpers ───────────────────────────────────────────────────

    /**
     * A read-only connection.
     *
     * <p>The {@code mode=ro} URI is what makes the drill safe: a read-only connection cannot
     * checkpoint the write-ahead log into the database file, so the drill cannot change a
     * byte of the database it is measuring.</p>
     *
     * @param database the database
     * @return the connection
     * @throws SQLException when the database cannot be opened
     */
    private static Connection readOnly(Path database) throws SQLException {
        String uri = "jdbc:sqlite:file:" + database.toAbsolutePath().toString().replace(" ", "%20")
                + "?mode=ro";
        return DriverManager.getConnection(uri);
    }

    /**
     * The lock file that sits next to a database.
     *
     * @param database the database
     * @return the lock file
     */
    private static Path lockFileOf(Path database) {
        Path absolute = database.toAbsolutePath().normalize();
        return absolute.resolveSibling(absolute.getFileName() + ".writer.lock");
    }

    /**
     * The schema version of a database, or minus one when it has no epistemic schema.
     *
     * @param connection the connection
     * @return the version
     * @throws SQLException when the version cannot be read
     */
    static int schemaVersion(Connection connection) throws SQLException {
        if (!tableExists(connection, "schema_meta")) {
            return -1;
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT value FROM schema_meta WHERE key='version'")) {
            if (!rs.next()) {
                return 0;
            }
            return Integer.parseInt(rs.getString(1));
        }
    }

    /**
     * The names of the tables of a database, in order.
     *
     * @param connection the connection
     * @return the names
     * @throws SQLException when the names cannot be read
     */
    static List<String> tables(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /**
     * Whether a table is there.
     *
     * @param connection the connection
     * @param table      the table
     * @return true when it is there
     * @throws SQLException when the answer cannot be read
     */
    static boolean tableExists(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='"
                             + table.replace("'", "''") + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * The columns of a table.
     *
     * @param connection the connection
     * @param table      the table
     * @return the column names
     * @throws SQLException when the columns cannot be read
     */
    private static List<String> columnsOf(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    /**
     * The row count of a table.
     *
     * @param connection the connection
     * @param table      the table
     * @return the count
     * @throws SQLException when the count cannot be read
     */
    private static long rowCount(Connection connection, String table) throws SQLException {
        return scalarLong(connection, "SELECT COUNT(*) FROM \"" + table + "\"");
    }

    /**
     * The digest of the schema of a database.
     *
     * @param connection the connection
     * @return the digest
     * @throws SQLException when the schema cannot be read
     */
    private static String schemaDigest(Connection connection) throws SQLException {
        StringBuilder schema = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT type, name, sql FROM sqlite_master ORDER BY type, name")) {
            while (rs.next()) {
                schema.append(rs.getString(1)).append(' ')
                        .append(rs.getString(2)).append(' ')
                        .append(rs.getString(3)).append('\n');
            }
        }
        return digest(schema.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The fingerprint of a table: the digest of its rows in a canonical encoding.
     *
     * @param connection the connection
     * @param table      the table
     * @return the fingerprint
     * @throws SQLException when the rows cannot be read
     */
    static String fingerprint(Connection connection, String table) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM \"" + table + "\"")) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int index = 1; index <= columns; index++) {
                    if (index > 1) {
                        row.append(CELL_SEPARATOR);
                    }
                    row.append(cell(rs, index, meta.getColumnType(index)));
                }
                rows.add(row.toString());
            }
        }
        java.util.Collections.sort(rows);
        StringBuilder canonical = new StringBuilder();
        for (String row : rows) {
            canonical.append(row).append(ROW_SEPARATOR);
        }
        return digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * One cell of a row, in the canonical encoding.
     *
     * @param rs    the result set
     * @param index the column, counted from one
     * @param type  the column's type
     * @return the encoded cell
     * @throws SQLException when the cell cannot be read
     */
    private static String cell(ResultSet rs, int index, int type) throws SQLException {
        switch (type) {
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                long value = rs.getLong(index);
                return rs.wasNull() ? "N" : "I" + value;
            }
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> {
                double value = rs.getDouble(index);
                return rs.wasNull() ? "N" : "R" + Double.toString(value);
            }
            case Types.BLOB, Types.VARBINARY, Types.LONGVARBINARY -> {
                byte[] value = rs.getBytes(index);
                return rs.wasNull() ? "N" : "B" + hex(value);
            }
            default -> {
                String value = rs.getString(index);
                return rs.wasNull() ? "N" : "T" + value;
            }
        }
    }

    /**
     * One value of a query.
     *
     * @param connection the connection
     * @param sql        the query
     * @return the value
     * @throws SQLException when the value cannot be read
     */
    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    /**
     * One number of a query.
     *
     * @param connection the connection
     * @param sql        the query
     * @return the number
     * @throws SQLException when the number cannot be read
     */
    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    // ── the digests ───────────────────────────────────────────────────────────

    /**
     * The digest of a file.
     *
     * @param file the file
     * @return the digest, in hexadecimal
     * @throws IOException when the file cannot be read
     */
    static String sha256(Path file) throws IOException {
        return digest(Files.readAllBytes(file));
    }

    /**
     * The digest of bytes.
     *
     * @param bytes the bytes
     * @return the digest, in hexadecimal
     */
    static String digest(byte[] bytes) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return hex(sha.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Bytes in hexadecimal.
     *
     * @param bytes the bytes
     * @return the hexadecimal
     */
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            text.append(Character.forDigit((b >> 4) & 0xF, 16));
            text.append(Character.forDigit(b & 0xF, 16));
        }
        return text.toString();
    }

    // ── the arguments and the JSON ────────────────────────────────────────────

    /**
     * A required option.
     *
     * @param args the arguments
     * @param name the option's name
     * @return the value
     */
    private static String requiredOption(String[] args, String name) {
        String value = optionalOption(args, name);
        if (value == null) {
            throw new IllegalArgumentException("the option " + name + " is required");
        }
        return value;
    }

    /**
     * An optional option.
     *
     * @param args the arguments
     * @param name the option's name
     * @return the value, or null when the option was not given
     */
    private static String optionalOption(String[] args, String name) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equals(name)) {
                return args[index + 1];
            }
        }
        return null;
    }

    /**
     * Writes a rendered report when the option asks for it.
     *
     * @param args     the arguments
     * @param name     the option's name
     * @param rendered the report
     * @throws IOException when the file cannot be written
     */
    private static void writeIfRequested(String[] args, String name, String rendered) throws IOException {
        String path = optionalOption(args, name);
        if (path != null) {
            Files.writeString(Paths.get(path), rendered, StandardCharsets.UTF_8);
        }
    }

    /**
     * Reads a measurement back.
     *
     * @param path the file
     * @return the measurement
     * @throws IOException when the file cannot be read
     */
    private static Map<String, Object> readMeasurement(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = parseJson(text);
        if (parsed instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> measurement = (Map<String, Object>) map;
            return measurement;
        }
        throw new IOException("the file " + path + " does not hold a measurement");
    }

    /**
     * A measurement as JSON.
     *
     * <p>The JSON is rendered across several lines, because a drill that prints its
     * measurements on one line of several thousand characters is a drill nobody reads.</p>
     *
     * @param value the value
     * @return the JSON
     */
    static String json(Object value) {
        StringBuilder text = new StringBuilder();
        writeJson(text, value, 0);
        return text.toString();
    }

    private static void writeJson(StringBuilder text, Object value, int depth) {
        if (value == null) {
            text.append("null");
        } else if (value instanceof String string) {
            text.append('"').append(escape(string)).append('"');
        } else if (value instanceof Boolean || value instanceof Number) {
            text.append(value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                text.append("{}");
                return;
            }
            text.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    text.append(",\n");
                }
                first = false;
                indent(text, depth + 1);
                text.append('"').append(escape(String.valueOf(entry.getKey()))).append("\": ");
                writeJson(text, entry.getValue(), depth + 1);
            }
            text.append('\n');
            indent(text, depth);
            text.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            List<Object> elements = new ArrayList<>();
            iterable.forEach(elements::add);
            if (elements.isEmpty()) {
                text.append("[]");
                return;
            }
            text.append("[\n");
            boolean first = true;
            for (Object element : elements) {
                if (!first) {
                    text.append(",\n");
                }
                first = false;
                indent(text, depth + 1);
                writeJson(text, element, depth + 1);
            }
            text.append('\n');
            indent(text, depth);
            text.append(']');
        } else {
            text.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void indent(StringBuilder text, int depth) {
        for (int level = 0; level < depth; level++) {
            text.append("  ");
        }
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * A JSON document, read back.
     *
     * <p>The probe writes JSON and reads it back, so it needs a reader. The grammar the
     * probe writes is small — objects, arrays, strings, numbers, booleans and null — and
     * this reader is exactly that grammar.</p>
     *
     * @param text the document
     * @return the value
     */
    static Object parseJson(String text) {
        JsonReader reader = new JsonReader(text);
        Object value = reader.value();
        return value;
    }

    /** A reader of the small JSON grammar the probe writes. */
    private static final class JsonReader {

        private final String text;
        private int at;

        JsonReader(String text) {
            this.text = text;
        }

        Object value() {
            skip();
            if (at >= text.length()) {
                throw new IllegalArgumentException("the document is empty");
            }
            char character = text.charAt(at);
            return switch (character) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            skip();
            if (at < text.length() && text.charAt(at) == '}') {
                at++;
                return map;
            }
            while (true) {
                skip();
                String key = string();
                skip();
                at++; // the colon
                map.put(key, value());
                skip();
                char character = text.charAt(at);
                at++;
                if (character == '}') {
                    return map;
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            at++;
            skip();
            if (at < text.length() && text.charAt(at) == ']') {
                at++;
                return list;
            }
            while (true) {
                list.add(value());
                skip();
                char character = text.charAt(at);
                at++;
                if (character == ']') {
                    return list;
                }
            }
        }

        private String string() {
            StringBuilder value = new StringBuilder();
            at++;
            while (true) {
                char character = text.charAt(at++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    char escaped = text.charAt(at++);
                    switch (escaped) {
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            value.append((char) Integer.parseInt(
                                    text.substring(at, at + 4), 16));
                            at += 4;
                        }
                        default -> value.append(escaped);
                    }
                } else {
                    value.append(character);
                }
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, at)) {
                throw new IllegalArgumentException("the document is malformed at " + at);
            }
            at += word.length();
            return value;
        }

        private Object number() {
            int start = at;
            while (at < text.length() && "-+.eE0123456789".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            String number = text.substring(start, at);
            if (number.contains(".") || number.contains("e") || number.contains("E")) {
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        }

        private void skip() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }
    }
}
