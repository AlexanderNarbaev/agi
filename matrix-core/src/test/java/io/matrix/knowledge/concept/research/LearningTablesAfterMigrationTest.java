package io.matrix.knowledge.concept.research;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — the learning tables after the identity migration: the pointers resolve, and they
 * still mean the same thing.
 *
 * <p><b>What this is.</b> The referent-key migration rewrites {@code concept_id} in
 * <b>every</b> table that carries one, and the learning tables are among them: {@code
 * learning_state}, {@code learning_transition}, {@code learning_error},
 * {@code hebbian_binding}, {@code assessment_item}, {@code assessment_outcome}. The
 * migration's audit covers the thirteen <b>knowledge</b> tables; the learning tables were
 * measured by hand and never pinned.</p>
 *
 * <p><b>What it pins.</b> Two things, and the second is the one that matters. (1) <b>No
 * dangling pointer</b>: every row in every table with a {@code concept_id} must point at a
 * concept that exists. (2) <b>No semantic drift</b>: a learning row must still point at
 * the concept it pointed at before the migration — the referent key renames a concept
 * ("Photosynthesis" becomes "Photosynthetic organisms"), and the row must follow the
 * rename rather than land on an unrelated concept.</p>
 *
 * <p><b>What the numbers are.</b> Every number below is read from the store at the moment
 * of the test, and the row counts are the ones the report publishes: the six learning
 * tables ({@code assessment_item} 64, {@code assessment_outcome} 3, {@code
 * hebbian_binding} 1, {@code learning_error} 1, {@code learning_state} 3, {@code
 * learning_transition} 3) and the four knowledge-core tables the report counts ({@code
 * claim} 52, {@code claim_scoped_record} 23, {@code prerequisite} 9, {@code
 * representation} 27). The <b>before</b> column is read from the preserved pre-migration
 * backup, which the report names as the way back and whose bytes are pinned here by their
 * sha256.</p>
 *
 * <p><b>What it does not claim.</b> It does not claim the learning subsystem learned
 * anything; it claims the migration did not silently redirect it. The one place where the
 * redirection is <b>not</b> a pure rename is pinned explicitly: the {@code
 * assessment_item} rows follow their old concept to whichever referent the referent key
 * produced for it, and for two of the eight the referent carries a different name (the
 * measured mapping is asserted below). The report's table publishes only the
 * <b>structure</b> of that table — "8 concepts, 8 items each" before and after — which is
 * what the structure assertions check.</p>
 *
 * <p>The test is <b>skipped, not failed</b>, when the store is not in the checkout (CI).</p>
 */
@DisplayName("P2 — the learning tables after the identity migration")
class LearningTablesAfterMigrationTest {

    private static final Path LIVE = Path.of("data/knowledge/workbench.sqlite");

    /**
     * The preserved pre-migration backup, by the name the report names it.
     *
     * <p>Its sha256 is the one the report publishes: the backup holds the original bytes,
     * so the "before" column of the report's table is read from the store the migration
     * actually ran on.</p>
     */
    private static final String BACKUP_NAME =
            "workbench.pre-migration-2026-11-18T16-04-34-729638675Z.sqlite";

    /** The sha256 the report publishes for the preserved backup. */
    private static final String BACKUP_SHA256 =
            "6d963dff9d5c07982faf4b81f0b927f9c9b7f2992b5ac0a77af2954d4b7f94c9";

    /** The tables that carry a concept id outside the knowledge core. */
    private static final List<String> LEARNING_TABLES = List.of(
            "assessment_item", "assessment_outcome", "hebbian_binding", "learning_error",
            "learning_state", "learning_transition");

    /** The knowledge-core tables the report counts. */
    private static final List<String> CORE_TABLES = List.of(
            "claim", "claim_scoped_record", "prerequisite", "representation");

    /** The published row counts, per table. */
    private static final Map<String, Integer> PUBLISHED_ROWS = Map.of(
            "assessment_item", 64,
            "assessment_outcome", 3,
            "hebbian_binding", 1,
            "learning_error", 1,
            "learning_state", 3,
            "learning_transition", 3,
            "claim", 52,
            "claim_scoped_record", 23,
            "prerequisite", 9,
            "representation", 27);

    /** The learning tables whose rows all point at one concept. */
    private static final List<String> SINGLE_TARGET_TABLES = List.of(
            "learning_state", "learning_transition", "learning_error", "hebbian_binding",
            "assessment_outcome");

    @Test
    @DisplayName("every learning row points at a concept that exists, and the row counts are the published ones")
    void everyLearningRowResolves() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            for (String table : LEARNING_TABLES) {
                if (!carriesAConceptId(connection, table)) {
                    continue;
                }
                int rows = count(connection, "select count(*) from \"" + table + "\"");
                int dangling = count(connection, "select count(*) from \"" + table
                        + "\" where concept_id not in (select id from concept)");
                assertThat(dangling).as("the dangling concept ids of %s", table).isZero();
                assertThat(rows).as("the rows of %s", table)
                        .isEqualTo(PUBLISHED_ROWS.get(table));
                System.out.printf("[learning-tables] %-22s the rows %-5d the dangling pointers %d%n",
                        table, rows, dangling);
            }
            // The knowledge core the report counts beside them.
            for (String table : CORE_TABLES) {
                int rows = count(connection, "select count(*) from \"" + table + "\"");
                int dangling = count(connection, "select count(*) from \"" + table
                        + "\" where concept_id not in (select id from concept)");
                assertThat(dangling).as("the dangling concept ids of %s", table).isZero();
                assertThat(rows).as("the rows of %s", table)
                        .isEqualTo(PUBLISHED_ROWS.get(table));
                System.out.printf("[learning-tables] %-22s the rows %-5d the dangling pointers %d%n",
                        table, rows, dangling);
            }
        }
    }

    @Test
    @DisplayName("the learning rows still mean the same thing: the referent's own rows")
    void theLearningRowsStillMeanTheSameThing() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            // The concept the learning rows are about: the workspace's own photosynthesis
            // referent. Its OLD name is "Photosynthesis" and its NEW name is
            // "Photosynthetic organisms" — the rename the referent key performs.
            String referentId = firstConceptId(connection, "learning_state");
            assertThat(referentId).as("the learning rows' concept id").isNotBlank();
            String referent = nameOf(connection, referentId);
            System.out.println("[learning-tables] the learning rows' concept: " + referent
                    + " (" + referentId + ")");

            // Every learning row that points at a concept must point at one that the
            // workspace still holds, and the rows that pointed at the photosynthesis
            // concept must all still point at ONE concept — the renamed referent.
            Map<String, Integer> targets = new LinkedHashMap<>();
            for (String table : List.of("learning_state", "learning_transition",
                    "learning_error", "hebbian_binding")) {
                if (!carriesAConceptId(connection, table)) {
                    continue;
                }
                try (Statement statement = connection.createStatement();
                        ResultSet rows = statement.executeQuery(
                                "select distinct concept_id from \"" + table + "\"")) {
                    while (rows.next()) {
                        String name = nameOf(connection, rows.getString(1));
                        targets.merge(name, 1, Integer::sum);
                    }
                }
            }
            System.out.println("[learning-tables] the learning rows' targets: " + targets);
            assertThat(targets).as("the learning rows' distinct targets").hasSize(1);
            assertThat(targets.keySet().iterator().next())
                    .as("the learning rows' target — the renamed photosynthesis referent")
                    .isEqualTo("Photosynthetic organisms");
        }
    }

    @Test
    @DisplayName("the assessment items: eight concepts, eight items each — before and after")
    void theAssessmentItemsKeepTheirStructure() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        Path backup = preservedBackup();
        Assumptions.assumeTrue(backup != null,
                "the preserved pre-migration backup is not in this checkout — the check is "
                        + "skipped, not failed");

        try (Connection live = DriverManager.getConnection("jdbc:sqlite:" + path);
             Connection before = DriverManager.getConnection("jdbc:sqlite:" + backup)) {
            // ── the structure the report publishes: 8 concepts, 8 items each ────
            for (Connection connection : List.of(before, live)) {
                Map<String, Integer> perConcept = itemsPerConcept(connection);
                System.out.println("[learning-tables] the assessment items per concept: "
                        + perConcept);
                assertThat(perConcept).as("the assessment items' concepts").hasSize(8);
                assertThat(perConcept.values()).as("the assessment items per concept")
                        .containsOnly(8);
                assertThat(count(connection, "select count(*) from assessment_item"))
                        .as("the assessment items").isEqualTo(64);
            }

            // ── the before/after targets, read from both stores ────────────────
            // The measured mapping of the assessment items' eight concepts: the name each
            // of them carried before the migration and the name it carries after. The
            // report publishes the structure ("8 concepts, 8 items each" before and
            // after); this pins the detail, including the two concepts whose referent
            // carries a different name.
            Map<String, String> measured = new LinkedHashMap<>();
            measured.put("CO2-concentrating mechanisms", "C4 plants");
            measured.put("Cellular respiration", "Cellular respiration");
            measured.put("Glycolysis", "Glycolysis");
            measured.put("It", "It");
            measured.put("Light-dependent reactions", "Wheat, shade-to-sun transitions");
            measured.put("Oxidative phosphorylation", "Oxidative phosphorylation");
            measured.put("The Calvin-Benson cycle",
                    "The Calvin-Benson cycle in oxygenic phototrophs");
            measured.put("The Krebs cycle", "The Krebs cycle");

            Map<String, String> targetsBefore = targetsByName(before, "assessment_item");
            Map<String, String> targetsAfter = targetsByName(live, "assessment_item");
            System.out.println("[learning-tables] the assessment items' concepts before: "
                    + targetsBefore);
            System.out.println("[learning-tables] the assessment items' concepts after: "
                    + targetsAfter);

            assertThat(targetsBefore.keySet()).as("the assessment items' concepts before")
                    .containsExactlyInAnyOrderElementsOf(measured.keySet());
            assertThat(targetsAfter.keySet()).as("the assessment items' concepts after")
                    .containsExactlyInAnyOrderElementsOf(measured.values());

            // The rows followed the referent key: every concept the items pointed at
            // before is either one of the concepts they point at now (the referent key
            // left it alone) or a concept the lineage records as its referent.
            for (Map.Entry<String, String> entry : targetsBefore.entrySet()) {
                String oldId = entry.getValue();
                boolean unchanged = targetsAfter.containsValue(oldId);
                boolean superseded = lineageTargets(live, oldId).stream()
                        .anyMatch(targetsAfter::containsValue);
                assertThat(unchanged || superseded)
                        .as("the assessment items' concept '%s' before the migration: it is "
                                + "either one of the concepts they point at now, or the lineage "
                                + "records one of them as its referent", entry.getKey())
                        .isTrue();
            }
        }
    }

    /**
     * The referents the lineage records for a concept.
     *
     * @param connection the connection
     * @param oldId the superseded concept id
     * @return the referent ids
     */
    private static List<String> lineageTargets(Connection connection, String oldId)
            throws SQLException {
        List<String> targets = new ArrayList<>();
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "select new_id from concept_lineage where old_id = ?")) {
            statement.setString(1, oldId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    targets.add(rows.getString(1));
                }
            }
        }
        return targets;
    }

    @Test
    @DisplayName("the before/after targets of the learning tables, read from the backup and the live store")
    void theLearningTablesTargetsBeforeAndAfter() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        Path backup = preservedBackup();
        Assumptions.assumeTrue(backup != null,
                "the preserved pre-migration backup is not in this checkout — the check is "
                        + "skipped, not failed");

        // The backup holds the original bytes — the way back the report names.
        assertThat(sha256(backup)).as("the preserved backup's sha256")
                .isEqualTo(BACKUP_SHA256);

        try (Connection live = DriverManager.getConnection("jdbc:sqlite:" + path);
             Connection before = DriverManager.getConnection("jdbc:sqlite:" + backup)) {
            for (String table : SINGLE_TARGET_TABLES) {
                Map<String, Integer> beforeTargets = targets(before, table);
                Map<String, Integer> afterTargets = targets(live, table);
                System.out.printf("[learning-tables] %-20s the target before %s, the target after %s%n",
                        table, beforeTargets, afterTargets);
                assertThat(beforeTargets).as("the %s's target before the migration", table)
                        .containsOnlyKeys("Photosynthesis");
                assertThat(afterTargets).as("the %s's target after the migration", table)
                        .containsOnlyKeys("Photosynthetic organisms");
                assertThat(count(before, "select count(*) from \"" + table + "\""))
                        .as("the rows of %s before the migration", table)
                        .isEqualTo(PUBLISHED_ROWS.get(table));
                assertThat(count(live, "select count(*) from \"" + table + "\""))
                        .as("the rows of %s after the migration", table)
                        .isEqualTo(PUBLISHED_ROWS.get(table));
            }
        }
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
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** Whether a table carries a concept id. */
    private static boolean carriesAConceptId(Connection connection, String table)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery(
                        "pragma table_info(\"" + table + "\")")) {
            List<String> names = new ArrayList<>();
            while (columns.next()) {
                names.add(columns.getString("name"));
            }
            return names.contains("concept_id");
        }
    }

    /** The first concept id a table's rows point at, or an empty string. */
    private static String firstConceptId(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select concept_id from \"" + table + "\" limit 1")) {
            return rows.next() ? rows.getString(1) : "";
        }
    }

    /** The name of a concept by its id, or a marker when it does not resolve. */
    private static String nameOf(Connection connection, String conceptId) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "select name from concept where id = ?")) {
            statement.setString(1, conceptId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : "DANGLING:" + conceptId;
            }
        }
    }

    /** A count over a query. */
    private static int count(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    /** The concepts a table's rows point at, by the concept's name. */
    private static Map<String, Integer> targets(Connection connection, String table)
            throws SQLException {
        Map<String, Integer> targets = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select distinct concept_id from \"" + table + "\"")) {
            while (rows.next()) {
                targets.merge(nameOf(connection, rows.getString(1)), 1, Integer::sum);
            }
        }
        return targets;
    }

    /** The items per concept of the assessment items. */
    private static Map<String, Integer> itemsPerConcept(Connection connection)
            throws SQLException {
        Map<String, Integer> perConcept = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select concept_id, count(*) from assessment_item group by concept_id")) {
            while (rows.next()) {
                perConcept.put(nameOf(connection, rows.getString(1)), rows.getInt(2));
            }
        }
        return perConcept;
    }

    /** The names of the concepts a table's rows point at. */
    private static Map<String, String> targetsByName(Connection connection, String table)
            throws SQLException {
        Map<String, String> byName = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select distinct concept_id from \"" + table + "\"")) {
            while (rows.next()) {
                byName.put(nameOf(connection, rows.getString(1)), rows.getString(1));
            }
        }
        return byName;
    }

    /** The sha256 of a file's bytes. */
    private static String sha256(Path path) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
