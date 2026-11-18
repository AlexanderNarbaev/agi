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
 * measured by hand and never pinned.
 *
 * <p><b>What it pins.</b> Two things, and the second is the one that matters. (1) <b>No
 * dangling pointer</b>: every row in every table with a {@code concept_id} must point at a
 * concept that exists. (2) <b>No semantic drift</b>: a learning row must still point at
 * the concept it pointed at before the migration — the referent key renames a concept
 * ("Photosynthesis" becomes "Photosynthetic organisms"), and the row must follow the
 * rename rather than land on an unrelated concept.
 *
 * <p><b>What it does not claim.</b> It does not claim the learning subsystem learned
 * anything; it claims the migration did not silently redirect it.
 *
 * <p>The test is <b>skipped, not failed</b>, when the store is not in the checkout (CI).</p>
 */
@DisplayName("P2 — the learning tables after the identity migration")
class LearningTablesAfterMigrationTest {

    private static final Path LIVE = Path.of("data/knowledge/workbench.sqlite");

    /** The tables that carry a concept id outside the knowledge core. */
    private static final List<String> LEARNING_TABLES = List.of(
            "assessment_item", "assessment_outcome", "hebbian_binding", "learning_error",
            "learning_state", "learning_transition");

    @Test
    @DisplayName("every learning row points at a concept that exists")
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
}
