package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Ids;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.Representation;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RESEARCH ONLY — the migration REHEARSAL of the referent key.
 *
 * <p><b>What this is.</b> The rehearsal of the copy-forward that the owner's decisions
 * OD-061 (the claim-scoped record) and OD-062 (the STRICT operating point) make
 * possible. It is executed <b>on a copy</b> of a workspace, never on the owner's store.
 * The rehearsal is what makes the real migration safe to propose: it produces the
 * lineage, the recomputed ids, the orphan audit and the rollback, with the evidence.
 *
 * <p><b>What the rehearsal changes and what it does not.</b>
 * <ul>
 *   <li>the <b>committed schema is not touched</b>: the additive tables
 *       ({@link #TABLE_CONCEPT_LINEAGE}, {@link #TABLE_CLAIM_LINEAGE},
 *       {@link #TABLE_CLAIM_SCOPED_RECORD}) are created in the COPY's database by this
 *       class, and the store's own DDL is never edited;</li>
 *   <li><b>nothing is deleted</b>: the old concept rows stay, marked superseded by the
 *       lineage table;</li>
 *   <li>the <b>claim ids do not change</b>: a claim's id is a digest of the workspace,
 *       the content hash and the proposition, and none of the three moves — so the
 *       claims, the evidence and the proposition-based golds survive the migration;</li>
 *   <li>the <b>concept ids change</b>: a referent's id is now derived from its
 *       <b>referent key</b> (the lemma, the source and the anchor) and a claim-scoped
 *       record's id from its claim;</li>
 *   <li>the <b>representation ids change</b>: they embed the concept id
 *       ({@code Ids.representation(workspace, conceptId, level, statement)}), so they are
 *       recomputed, and the {@code refines} chain is carried across.</li>
 * </ul>
 *
 * <p><b>The owner-gated part.</b> The migration of the owner's store and the change of
 * the committed schema are the owner's decisions. This class performs neither; it
 * rehearses them on a copy so that the decision can be taken with evidence.
 * </p>
 */
public final class ReferentKeyMigration {

    /** The table of the concept lineage: what superseded what, and why. */
    public static final String TABLE_CONCEPT_LINEAGE = "concept_lineage";

    /** The table of the claim lineage: the claim keeps its id, its concept changes. */
    public static final String TABLE_CLAIM_LINEAGE = "claim_lineage";

    /** The table of the claim-scoped records: a claim with no referent, on its own. */
    public static final String TABLE_CLAIM_SCOPED_RECORD = "claim_scoped_record";

    /**
     * The table of the merged definitions.
     *
     * <p><b>The collision the rehearsal found.</b> The definitions live in their own
     * table, keyed by (workspace, concept id). The copy-forward COLLAPSES many old
     * concepts into one referent, so two old definitions can land on the same new
     * concept id — and the table's primary key refuses that. The rehearsal therefore
     * keeps the first definition (deterministically: the lineage's order) and records
     * every merged one here, so that <b>nothing is lost</b>.</p>
     */
    public static final String TABLE_DEFINITION_MERGE = "concept_definition_merge";

    /** The kind of a new concept row: a referent of the key. */
    public static final String KIND_REFERENT = "REFERENT";

    /** The kind of a new concept row: a claim-scoped record, not a referent. */
    public static final String KIND_CLAIM_SCOPED = "CLAIM_SCOPED";

    /** The reason the copy-forward writes into the concept lineage. */
    public static final String REASON_REFERENT = "the referent key: the lemma, the source and the anchor";

    /** The reason the copy-forward writes into the claim lineage for a referent. */
    public static final String REASON_CLAIM_RESOLVED = "the claim resolves to a referent";

    /** The reason the copy-forward writes into the claim lineage for a record. */
    public static final String REASON_CLAIM_SCOPED = "the policy rejected the surface form: no referent";

    /**
     * The timestamp the rehearsal's own writes carry.
     *
     * <p>The rehearsal is a measurement, not a production write, so the lineage's time
     * is the rehearsal's own constant and not the clock: a rehearsal that produced a
     * different lineage on every run would not be comparable.</p>
     */
    public static final String REHEARSAL_TIMESTAMP = "2026-11-16T00:00:00Z";

    /** The concept table of the store: the rows the copy-forward adds to. */
    private static final String CONCEPT_TABLE = "concept";

    private ReferentKeyMigration() {
    }

    /**
     * One concept row the copy-forward creates.
     *
     * @param id the new concept id
     * @param name the concept's name: the referent's display name, or the claim's own
     *             text for a claim-scoped record
     * @param kind {@link #KIND_REFERENT} or {@link #KIND_CLAIM_SCOPED}
     * @param reason why the row exists
     */
    public record NewConcept(String id, String name, String kind, String reason) {
    }

    /**
     * One row of the concept lineage.
     *
     * @param oldId the id that was superseded
     * @param newId the id that superseded it
     * @param reason why
     * @param createdAt when the row was written
     */
    public record ConceptLineage(String oldId, String newId, String reason, String createdAt) {
    }

    /**
     * One row of the claim lineage.
     *
     * @param claimId the claim — <b>unchanged</b>
     * @param oldConceptId the concept the claim pointed at
     * @param newConceptId the concept the claim points at now
     * @param reason why
     * @param createdAt when the row was written
     */
    public record ClaimLineage(String claimId, String oldConceptId, String newConceptId,
                               String reason, String createdAt) {
    }

    /**
     * The plan the copy-forward executes: the new rows, the rewiring and the lineage.
     *
     * @param newConcepts the concept rows to insert
     * @param newConceptIdByClaimId the claim id → the concept id it now points at
     * @param conceptLineage the concept lineage rows
     * @param claimLineage the claim lineage rows
     * @param newRepresentationIdByOldId the representation id → its recomputed id
     * @param referents how many referents the policy produced
     * @param claimScoped how many claims became claim-scoped records
     * @param rejected how many claims the policy rejected
     * @param oldConceptIdByClaimId the claim id → the concept id it pointed at
     */
    public record Plan(List<NewConcept> newConcepts,
                       Map<String, String> newConceptIdByClaimId,
                       List<ConceptLineage> conceptLineage,
                       List<ClaimLineage> claimLineage,
                       Map<String, String> newRepresentationIdByOldId,
                       int referents,
                       int claimScoped,
                       int rejected,
                       Map<String, String> oldConceptIdByClaimId,
                       List<io.matrix.knowledge.concept.ConceptAlias> aliases) {

        /**
         * The concept id → the concept id that superseded it.
         *
         * <p>Many old concepts can collapse into one referent, so this map is not a
         * bijection: it is the rewiring table the copy-forward applies to every table
         * that references a concept id.</p>
         *
         * @return the rewiring table
         */
        public Map<String, String> oldToNew() {
            Map<String, String> rewiring = new LinkedHashMap<>();
            for (ConceptLineage row : conceptLineage) {
                rewiring.put(row.oldId(), row.newId());
            }
            return rewiring;
        }
    }

    /**
     * What the copy-forward did.
     *
     * @param conceptsInserted how many concept rows were inserted
     * @param claimsRewritten how many claims were re-pointed
     * @param representationsRewritten how many representations were re-pointed
     * @param refinesCarried how many refines links were carried
     * @param prerequisitesRewritten how many prerequisite rows were re-pointed
     * @param otherRowsRewritten how many rows of the other referencing tables were
     *                           re-pointed
     * @param lineageRows how many lineage rows were written
     * @param millis how long the copy-forward took
     */
    public record MigrationResult(int conceptsInserted, int claimsRewritten,
                                  int representationsRewritten, int refinesCarried,
                                  int prerequisitesRewritten, int otherRowsRewritten,
                                  int lineageRows, long millis) {
    }

    /**
     * The result of the orphan audit.
     *
     * @param tablesChecked the referencing tables the audit walked
     * @param orphansByTable the rows whose referenced concept does not exist
     * @param staleByTable the rows that still point at a superseded concept
     * @param totalOrphans the sum of the orphans
     * @param totalStale the sum of the stale pointers
     */
    public record OrphanAudit(List<String> tablesChecked,
                               Map<String, Integer> orphansByTable,
                               Map<String, Integer> staleByTable,
                               int totalOrphans, int totalStale) {
    }

    // ── the plan ──────────────────────────────────────────────────────────────

    /**
     * Builds the copy-forward plan of a workspace.
     *
     * @param snapshot the workspace's snapshot
     * @param workspace the workspace id
     * @param strictness the operating point of the policy
     * @param createdAt when the lineage is written
     * @return the plan
     */
    public static Plan plan(KnowledgeSnapshot snapshot, String workspace,
                            ReferentPolicy.Strictness strictness, String createdAt) {
        return plan(snapshot, workspace, strictness, createdAt,
                ReferentPolicy.DECLARED_ALIASES);
    }

    /**
     * Builds the copy-forward plan of a workspace, under an ALIAS TABLE.
     *
     * <p>The alias layer is DATA (the owner's decision OD-064), so the plan resolves
     * under the table the store holds and not under the research package's static
     * list.</p>
     *
     * @param snapshot the workspace's snapshot
     * @param workspace the workspace id
     * @param strictness the operating point of the policy
     * @param createdAt when the lineage is written
     * @param aliases the alias table
     * @return the plan
     */
    public static Plan plan(KnowledgeSnapshot snapshot, String workspace,
                            ReferentPolicy.Strictness strictness, String createdAt,
                            List<io.matrix.knowledge.concept.ConceptAlias> aliases) {
        ReferentResolver.Result resolved = ReferentResolver.resolve(snapshot.claims(),
                snapshot.concepts(), true, strictness, aliases);
        Map<String, String> oldConceptIdByClaimId = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            oldConceptIdByClaimId.put(claim.id(), claim.conceptId());
        }
        Map<String, Claim> claimById = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            claimById.put(claim.id(), claim);
        }

        Map<String, NewConcept> concepts = new LinkedHashMap<>();
        Map<String, String> newConceptIdByClaimId = new LinkedHashMap<>();
        List<ConceptLineage> conceptLineage = new ArrayList<>();
        List<ClaimLineage> claimLineage = new ArrayList<>();

        // 1. the referents: every accepted claim gets the referent it resolved to.
        for (ReferentResolver.Cluster cluster : resolved.clustersDetail()) {
            String id = Ids.digest(workspace + "|referent|" + cluster.key().identity());
            concepts.put(id, new NewConcept(id, cluster.displayName(), KIND_REFERENT,
                    REASON_REFERENT + " " + cluster.key().identity()));
            for (String claimId : cluster.claimIds()) {
                newConceptIdByClaimId.put(claimId, id);
                String oldConceptId = oldConceptIdByClaimId.get(claimId);
                conceptLineage.add(new ConceptLineage(oldConceptId, id,
                        REASON_REFERENT + " " + cluster.key().identity(), createdAt));
                claimLineage.add(new ClaimLineage(claimId, oldConceptId, id,
                        REASON_CLAIM_RESOLVED, createdAt));
            }
        }

        // 2. the claim-scoped records: every rejected claim keeps its own text as the
        //    retrievable unit. The record's NAME is the claim's own text, NOT the
        //    rejected surface form — otherwise the record would put the fragment the
        //    policy rejected back into the searchable text.
        int claimScoped = 0;
        for (ReferentResolver.Occurrence occurrence : resolved.occurrences()) {
            if (occurrence.accepted()) {
                continue;
            }
            Claim claim = claimById.get(occurrence.claimId());
            if (claim == null) {
                continue;
            }
            claimScoped++;
            String id = Ids.digest(workspace + "|claim|" + claim.id());
            concepts.put(id, new NewConcept(id, claim.proposition(), KIND_CLAIM_SCOPED,
                    REASON_CLAIM_SCOPED + " (" + occurrence.rejection() + ")"));
            newConceptIdByClaimId.put(claim.id(), id);
            claimLineage.add(new ClaimLineage(claim.id(), claim.conceptId(), id,
                    REASON_CLAIM_SCOPED + " (" + occurrence.rejection() + ")", createdAt));
        }

        // 3. the representation ids: they embed the concept id, so they are recomputed.
        Map<String, String> newRepresentationIdByOldId = new LinkedHashMap<>();
        Map<String, String> rewiring = oldConceptIdByClaimId.isEmpty()
                ? Map.of() : rewiringOf(conceptLineage);
        for (Representation representation : snapshot.representations()) {
            String newConceptId = rewiring.get(representation.conceptId());
            if (newConceptId == null) {
                continue;
            }
            newRepresentationIdByOldId.put(representation.id(),
                    Ids.representation(workspace, newConceptId, representation.level(),
                            representation.statement()));
        }

        return new Plan(new ArrayList<>(concepts.values()), newConceptIdByClaimId,
                conceptLineage, claimLineage, newRepresentationIdByOldId,
                resolved.clusters(), claimScoped, resolved.rejected(),
                oldConceptIdByClaimId, aliases);
    }

    /**
     * The rewiring table of a lineage: the old concept id → the id that superseded it.
     *
     * @param lineage the concept lineage
     * @return the rewiring table
     */
    private static Map<String, String> rewiringOf(List<ConceptLineage> lineage) {
        Map<String, String> rewiring = new LinkedHashMap<>();
        for (ConceptLineage row : lineage) {
            rewiring.put(row.oldId(), row.newId());
        }
        return rewiring;
    }

    // ── the copy-forward ──────────────────────────────────────────────────────

    /**
     * Runs the copy-forward on a COPY of a workspace's database.
     *
     * @param database the copy's database file
     * @param plan the plan
     * @param workspace the workspace id
     * @return what the copy-forward did
     * @throws SQLException when the database refuses the write
     */
    public static MigrationResult apply(Path database, Plan plan, String workspace)
            throws SQLException {
        long started = System.nanoTime();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.setAutoCommit(false);
            try {
                seedTheDeclaredAliases(connection, workspace, plan.aliases());
                int inserted = insertTheConcepts(connection, plan, workspace);
                int claims = rewriteTheClaims(connection, plan, workspace);
                int[] representations = rewriteTheRepresentations(connection, plan, workspace);
                int prerequisites = rewriteThePrerequisites(connection, plan, workspace);
                int other = rewriteTheOtherTables(connection, plan, workspace);
                int lineage = writeTheLineage(connection, plan, workspace);
                connection.commit();
                return new MigrationResult(inserted, claims, representations[0],
                        representations[1], prerequisites, other, lineage,
                        (System.nanoTime() - started) / 1_000_000L);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Writes the DECLARED alias table into a workspace's own table, BEFORE the plan.
     *
     * <p>The alias layer is DATA (the owner's decision OD-064), so the plan must resolve
     * under the table the store holds. The seeding therefore has to happen first: the plan
     * reads the store's rows, and the copy-forward writes them. This method is the
     * seeding step, idempotent ({@code INSERT OR IGNORE}), and it is what makes the order
     * of the migration explicit rather than accidental.</p>
     *
     * @param database the workspace's database file
     * @param workspace the workspace id
     * @param aliases the declared entries
     * @return how many rows were written
     * @throws SQLException when the database refuses the write
     */
    public static int seedDeclaredAliases(Path database, String workspace,
                                           List<io.matrix.knowledge.concept.ConceptAlias> aliases)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.setAutoCommit(false);
            try {
                int written = seedTheDeclaredAliases(connection, workspace, aliases);
                connection.commit();
                return written;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Writes the DECLARED alias table into the store's own table.
     *
     * <p>The alias layer is DATA in the committed schema (the owner's decision
     * OD-064), and the status travels with every row: the
     * {@code declaration_status} column says that the entry is a hand-written,
     * corpus-fitted and temporary claim, so that a later reader of the DATA alone
     * cannot mistake it for a derived fact.</p>
     *
     * @param connection the connection
     * @param workspace the workspace id
     * @param aliases the declared entries
     * @return how many rows were written
     * @throws SQLException when the database refuses the write
     */
    public static int seedTheDeclaredAliases(Connection connection, String workspace,
                                             List<io.matrix.knowledge.concept.ConceptAlias> aliases)
            throws SQLException {
        int written = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO concept_alias(workspace_id, canonical_lemma, "
                        + "variant_lemma, kind, reason, declared_by, declaration_status, "
                        + "recorded_at) VALUES (?,?,?,?,?,?,?,?)")) {
            for (io.matrix.knowledge.concept.ConceptAlias alias : aliases) {
                statement.setString(1, workspace);
                statement.setString(2, alias.canonicalLemma());
                statement.setString(3, alias.variantLemma());
                statement.setString(4, alias.kind().name());
                statement.setString(5, alias.reason());
                statement.setString(6, alias.declaredBy());
                statement.setString(7, alias.declarationStatus().name());
                statement.setString(8, alias.recordedAt());
                statement.executeUpdate();
                written++;
            }
        }
        return written;
    }

    /**
     * Inserts the new concept rows.
     *
     * @param connection the connection
     * @param plan the plan
     * @param workspace the workspace id
     * @return how many rows were inserted
     * @throws SQLException when the database refuses the write
     */
    private static int insertTheConcepts(Connection connection, Plan plan, String workspace)
            throws SQLException {
        int inserted = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + CONCEPT_TABLE
                        + "(id, workspace_id, name, definition, domain, created_at) "
                        + "VALUES (?,?,?,?,?,?)")) {
            for (NewConcept concept : plan.newConcepts()) {
                statement.setString(1, concept.id());
                statement.setString(2, workspace);
                statement.setString(3, concept.name());
                statement.setString(4, null);
                statement.setString(5, null);
                statement.setString(6, REHEARSAL_TIMESTAMP);
                statement.executeUpdate();
                inserted++;
            }
        }
        return inserted;
    }

    /**
     * Re-points the claims at their new concepts.
     *
     * @param connection the connection
     * @param plan the plan
     * @param workspace the workspace id
     * @return how many claims were re-pointed
     * @throws SQLException when the database refuses the write
     */
    private static int rewriteTheClaims(Connection connection, Plan plan, String workspace)
            throws SQLException {
        int rewritten = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE claim SET concept_id = ?, updated_at = ? WHERE id = ? AND workspace_id = ?")) {
            for (Map.Entry<String, String> entry : plan.newConceptIdByClaimId().entrySet()) {
                statement.setString(1, entry.getValue());
                statement.setString(2, REHEARSAL_TIMESTAMP);
                statement.setString(3, entry.getKey());
                statement.setString(4, workspace);
                rewritten += statement.executeUpdate();
            }
        }
        return rewritten;
    }

    /**
     * Re-points the representations, recomputing their ids and carrying the refines
     * chain across.
     *
     * @param connection the connection
     * @param plan the plan
     * @param workspace the workspace id
     * @return the rows re-pointed and the refines links carried
     * @throws SQLException when the database refuses the write
     */
    private static int[] rewriteTheRepresentations(Connection connection, Plan plan,
                                                  String workspace) throws SQLException {
        int rewritten = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE representation SET id = ?, concept_id = ? WHERE id = ? AND workspace_id = ?")) {
            for (Map.Entry<String, String> entry : plan.newRepresentationIdByOldId().entrySet()) {
                String oldId = entry.getKey();
                String newId = entry.getValue();
                try (PreparedStatement lookup = connection.prepareStatement(
                        "SELECT concept_id FROM representation WHERE id = ?")) {
                    lookup.setString(1, oldId);
                    try (ResultSet rows = lookup.executeQuery()) {
                        if (!rows.next()) {
                            continue;
                        }
                        String newConceptId = plan.oldToNew().get(rows.getString(1));
                        if (newConceptId == null) {
                            continue;
                        }
                        statement.setString(1, newId);
                        statement.setString(2, newConceptId);
                        statement.setString(3, oldId);
                        statement.setString(4, workspace);
                        rewritten += statement.executeUpdate();
                    }
                }
            }
        }
        int carried = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE representation SET refines = ? WHERE refines = ? AND workspace_id = ?")) {
            for (Map.Entry<String, String> entry : plan.newRepresentationIdByOldId().entrySet()) {
                statement.setString(1, entry.getValue());
                statement.setString(2, entry.getKey());
                statement.setString(3, workspace);
                carried += statement.executeUpdate();
            }
        }
        return new int[] {rewritten, carried};
    }

    /**
     * Re-points the prerequisite graph.
     *
     * @param connection the connection
     * @param plan the plan
     * @param workspace the workspace id
     * @return how many rows were re-pointed
     * @throws SQLException when the database refuses the write
     */
    private static int rewriteThePrerequisites(Connection connection, Plan plan, String workspace)
            throws SQLException {
        int rewritten = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE prerequisite SET concept_id = ? WHERE concept_id = ? AND workspace_id = ?");
                PreparedStatement requires = connection.prepareStatement(
                        "UPDATE prerequisite SET requires_concept_id = ? "
                                + "WHERE requires_concept_id = ? AND workspace_id = ?")) {
            for (Map.Entry<String, String> entry : plan.oldToNew().entrySet()) {
                statement.setString(1, entry.getValue());
                statement.setString(2, entry.getKey());
                statement.setString(3, workspace);
                rewritten += statement.executeUpdate();
                requires.setString(1, entry.getValue());
                requires.setString(2, entry.getKey());
                requires.setString(3, workspace);
                rewritten += requires.executeUpdate();
            }
        }
        return rewritten;
    }

    /**
     * Re-points every other table that references the concept id.
     *
     * <p>The tables are DISCOVERED from the live schema, so the audit and the rewiring
     * walk the same set: every table with a {@code concept_id} column, plus the tables
     * that hold a list of concept ids in a text column.</p>
     *
     * @param connection the connection
     * @param plan the plan
     * @param workspace the workspace id
     * @return how many rows were re-pointed
     * @throws SQLException when the database refuses the write
     */
    private static int rewriteTheOtherTables(Connection connection, Plan plan, String workspace)
            throws SQLException {
        int rewritten = 0;
        Set<String> handled = Set.of("claim", "concept", "representation", "prerequisite",
                "concept_definition", TABLE_CONCEPT_LINEAGE, TABLE_CLAIM_LINEAGE,
                TABLE_CLAIM_SCOPED_RECORD, TABLE_DEFINITION_MERGE);
        for (String table : tablesWithAConceptIdColumn(connection)) {
            if (handled.contains(table)) {
                continue;
            }
            for (Map.Entry<String, String> entry : plan.oldToNew().entrySet()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + table + " SET concept_id = ? WHERE concept_id = ? "
                                + "AND workspace_id = ?")) {
                    statement.setString(1, entry.getValue());
                    statement.setString(2, entry.getKey());
                    statement.setString(3, workspace);
                    rewritten += statement.executeUpdate();
                }
            }
        }
        // The definitions collide: the copy-forward collapses many old concepts into
        // one referent, and the definitions table is keyed by the concept id. The
        // first definition wins (the lineage's order) and every merged one is
        // recorded — nothing is lost.
        if (tableExists(connection, "concept_definition")) {
            for (Map.Entry<String, String> entry : plan.oldToNew().entrySet()) {
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT definition FROM concept_definition WHERE workspace_id = ? "
                                + "AND concept_id = ?")) {
                    read.setString(1, workspace);
                    read.setString(2, entry.getKey());
                    try (ResultSet rows = read.executeQuery()) {
                        if (!rows.next()) {
                            continue;
                        }
                        String definition = rows.getString(1);
                        boolean taken = false;
                        try (PreparedStatement exists = connection.prepareStatement(
                                "SELECT COUNT(*) FROM concept_definition WHERE workspace_id = ? "
                                        + "AND concept_id = ?")) {
                            exists.setString(1, workspace);
                            exists.setString(2, entry.getValue());
                            try (ResultSet counted = exists.executeQuery()) {
                                taken = counted.next() && counted.getInt(1) > 0;
                            }
                        }
                        if (taken) {
                            try (PreparedStatement merge = connection.prepareStatement(
                                    "INSERT OR REPLACE INTO " + TABLE_DEFINITION_MERGE
                                            + "(new_concept_id, old_concept_id, definition, reason, "
                                            + "created_at) VALUES (?,?,?,?,?)")) {
                                merge.setString(1, entry.getValue());
                                merge.setString(2, entry.getKey());
                                merge.setString(3, definition);
                                merge.setString(4, "the referent already carries a definition; "
                                        + "the merged one is recorded here");
                                merge.setString(5, REHEARSAL_TIMESTAMP);
                                merge.executeUpdate();
                            }
                            try (PreparedStatement drop = connection.prepareStatement(
                                    "DELETE FROM concept_definition WHERE workspace_id = ? "
                                            + "AND concept_id = ?")) {
                                drop.setString(1, workspace);
                                drop.setString(2, entry.getKey());
                                drop.executeUpdate();
                            }
                            continue;
                        }
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE concept_definition SET concept_id = ? "
                                        + "WHERE workspace_id = ? AND concept_id = ?")) {
                            update.setString(1, entry.getValue());
                            update.setString(2, workspace);
                            update.setString(3, entry.getKey());
                            rewritten += update.executeUpdate();
                        }
                    }
                }
            }
        }
        // The dialogue turn holds a LIST of concept ids in one text column.
        if (tableExists(connection, "dialogue_turn")) {
            for (Map.Entry<String, String> entry : plan.oldToNew().entrySet()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE dialogue_turn SET concept_ids = REPLACE(concept_ids, ?, ?) "
                                + "WHERE workspace_id = ? AND concept_ids LIKE ?")) {
                    statement.setString(1, entry.getKey());
                    statement.setString(2, entry.getValue());
                    statement.setString(3, workspace);
                    statement.setString(4, "%" + entry.getKey() + "%");
                    rewritten += statement.executeUpdate();
                }
            }
        }
        return rewritten;
    }

    /**
     * Writes the lineage: the concept lineage, the claim lineage and the claim-scoped
     * records.
     *
     * @param connection the connection
     * @param plan the plan
     * @param workspace the workspace id
     * @return how many rows were written
     * @throws SQLException when the database refuses the write
     */
    private static int writeTheLineage(Connection connection, Plan plan, String workspace)
            throws SQLException {
        int rows = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + TABLE_CONCEPT_LINEAGE
                        + "(old_id, new_id, reason, created_at) VALUES (?,?,?,?)")) {
            for (ConceptLineage row : plan.conceptLineage()) {
                statement.setString(1, row.oldId());
                statement.setString(2, row.newId());
                statement.setString(3, row.reason());
                statement.setString(4, row.createdAt());
                statement.executeUpdate();
                rows++;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + TABLE_CLAIM_LINEAGE
                        + "(claim_id, old_concept_id, new_concept_id, reason, created_at) "
                        + "VALUES (?,?,?,?,?)")) {
            for (ClaimLineage row : plan.claimLineage()) {
                statement.setString(1, row.claimId());
                statement.setString(2, row.oldConceptId());
                statement.setString(3, row.newConceptId());
                statement.setString(4, row.reason());
                statement.setString(5, row.createdAt());
                statement.executeUpdate();
                rows++;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + TABLE_CLAIM_SCOPED_RECORD
                        + "(concept_id, workspace_id, claim_id, text, reason, created_at) "
                        + "VALUES (?,?,?,?,?,?)")) {
            for (NewConcept concept : plan.newConcepts()) {
                if (!KIND_CLAIM_SCOPED.equals(concept.kind())) {
                    continue;
                }
                String claimId = null;
                for (Map.Entry<String, String> entry : plan.newConceptIdByClaimId().entrySet()) {
                    if (entry.getValue().equals(concept.id())) {
                        claimId = entry.getKey();
                        break;
                    }
                }
                statement.setString(1, concept.id());
                statement.setString(2, workspace);
                statement.setString(3, claimId);
                statement.setString(4, concept.name());
                statement.setString(5, concept.reason());
                statement.setString(6, REHEARSAL_TIMESTAMP);
                statement.executeUpdate();
                rows++;
            }
        }
        return rows;
    }

    // ── the orphan audit ──────────────────────────────────────────────────────

    /**
     * The explicit orphan audit.
     *
     * <p><b>Why it exists.</b> SQLite declares no foreign key for the concept id, so
     * {@code validate} will NOT see a partial migration: the store's own validation
     * walks the rows it knows, and a row that points at a concept that no longer exists
     * is invisible to it. The audit therefore walks <b>every table that references the
     * concept id</b> — discovered from the live schema, not from a hand-written list —
     * and reports two numbers per table:</p>
     * <ul>
     *   <li>the <b>orphans</b>: the rows whose referenced concept does not exist. These
     *       must be zero;</li>
     *   <li>the <b>stale pointers</b>: the rows that still point at a concept the
     *       lineage marks as superseded. These must be zero after a correct
     *       copy-forward, and they are what a half-migrated database shows.</li>
     * </ul>
     *
     * @param database the database to audit
     * @param workspace the workspace id
     * @return the audit's result
     * @throws SQLException when the database refuses the read
     */
    public static OrphanAudit audit(Path database, String workspace) throws SQLException {
        Map<String, Integer> orphans = new LinkedHashMap<>();
        Map<String, Integer> stale = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            Set<String> superseded = supersededConceptIds(connection, workspace);
            for (String table : tablesWithAConceptIdColumn(connection)) {
                tables.add(table);
                orphans.put(table, countRowsWhoseConceptIsMissing(connection, table, workspace));
                stale.put(table, countRowsPointingAtASupersededConcept(connection, table, workspace,
                        superseded));
            }
            // The prerequisite graph has a SECOND reference: the required concept.
            if (tableExists(connection, "prerequisite")) {
                tables.add("prerequisite.requires_concept_id");
                orphans.put("prerequisite.requires_concept_id",
                        countMissing(connection, "prerequisite", "requires_concept_id", workspace));
                stale.put("prerequisite.requires_concept_id",
                        countStale(connection, "prerequisite", "requires_concept_id", workspace,
                                superseded));
            }
        }
        int totalOrphans = orphans.values().stream().mapToInt(Integer::intValue).sum();
        int totalStale = stale.values().stream().mapToInt(Integer::intValue).sum();
        return new OrphanAudit(tables, orphans, stale, totalOrphans, totalStale);
    }

    /**
     * The concept ids the lineage marks as superseded.
     *
     * @param connection the connection
     * @param workspace the workspace id
     * @return the superseded ids
     * @throws SQLException when the database refuses the read
     */
    private static Set<String> supersededConceptIds(Connection connection, String workspace)
            throws SQLException {
        Set<String> superseded = new LinkedHashSet<>();
        if (!tableExists(connection, TABLE_CONCEPT_LINEAGE)) {
            return superseded;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT old_id FROM " + TABLE_CONCEPT_LINEAGE);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                superseded.add(rows.getString(1));
            }
        }
        return superseded;
    }

    /**
     * Counts the rows of a table whose referenced concept does not exist.
     *
     * @param connection the connection
     * @param table the table
     * @param workspace the workspace id
     * @return the count
     * @throws SQLException when the database refuses the read
     */
    private static int countRowsWhoseConceptIsMissing(Connection connection, String table,
                                                       String workspace) throws SQLException {
        return countMissing(connection, table, "concept_id", workspace);
    }

    /**
     * Counts the rows of a table whose referenced concept does not exist.
     *
     * @param connection the connection
     * @param table the table
     * @param column the reference column
     * @param workspace the workspace id
     * @return the count
     * @throws SQLException when the database refuses the read
     */
    private static int countMissing(Connection connection, String table, String column,
                                    String workspace) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE workspace_id = ? AND " + column
                        + " NOT IN (SELECT id FROM " + CONCEPT_TABLE + " WHERE workspace_id = ?)")) {
            statement.setString(1, workspace);
            statement.setString(2, workspace);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    /**
     * Counts the rows of a table that still point at a superseded concept.
     *
     * @param connection the connection
     * @param table the table
     * @param column the reference column
     * @param workspace the workspace id
     * @param superseded the superseded concept ids
     * @return the count
     * @throws SQLException when the database refuses the read
     */
    private static int countStale(Connection connection, String table, String column,
                                  String workspace, Set<String> superseded) throws SQLException {
        if (superseded.isEmpty()) {
            return 0;
        }
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE workspace_id = ? AND " + column + " = ?")) {
            statement.setString(1, workspace);
            for (String id : superseded) {
                statement.setString(2, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        count += rows.getInt(1);
                    }
                }
            }
        }
        return count;
    }

    /**
     * Counts the rows of a table that still point at a superseded concept.
     *
     * @param connection the connection
     * @param table the table
     * @param workspace the workspace id
     * @param superseded the superseded concept ids
     * @return the count
     * @throws SQLException when the database refuses the read
     */
    private static int countRowsPointingAtASupersededConcept(Connection connection, String table,
                                                              String workspace,
                                                              Set<String> superseded)
            throws SQLException {
        return countStale(connection, table, "concept_id", workspace, superseded);
    }

    /**
     * The tables of the live schema that carry a concept id.
     *
     * @param connection the connection
     * @return the table names
     * @throws SQLException when the database refuses the read
     */
    static List<String> tablesWithAConceptIdColumn(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String table = rows.getString(1);
                if (table.startsWith("sqlite_")) {
                    continue;
                }
                if (hasColumn(connection, table, "concept_id")) {
                    tables.add(table);
                }
            }
        }
        return tables;
    }

    /**
     * Whether a table has a column.
     *
     * @param connection the connection
     * @param table the table
     * @param column the column
     * @return whether it does
     * @throws SQLException when the database refuses the read
     */
    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "PRAGMA table_info(" + table + ")");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                if (column.equals(rows.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a table exists.
     *
     * @param connection the connection
     * @param table the table
     * @return whether it does
     * @throws SQLException when the database refuses the read
     */
    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        }
    }

    /**
     * Copies a database file into a consistent copy.
     *
     * <p>The store runs SQLite in WAL mode, so a byte copy of the main file would miss
     * the committed rows that still live in the write-ahead log. SQLite's
     * {@code VACUUM INTO} writes a consistent, self-contained copy instead.</p>
     *
     * @param source the source database
     * @param target the copy
     * @throws SQLException when the database refuses the copy
     */
    public static void copy(Path source, Path target) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
                Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + target.toAbsolutePath() + "'");
        }
    }

    /**
     * The size of a database file.
     *
     * @param database the file
     * @return the size in bytes
     * @throws java.io.IOException when the file cannot be read
     */
    public static long sizeOf(Path database) throws java.io.IOException {
        return java.nio.file.Files.size(database);
    }
}
