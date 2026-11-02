package io.matrix.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The durable store for learned knowledge (owner directive §17).
 *
 * <p>SQLite is the storage, chosen because it is already in the repository's
 * dependency set ({@code org.xerial:sqlite-jdbc}), it is a single file that
 * a user can copy, it is transactional, and it works offline with no server —
 * which is what a single-user local application needs. Postgres is
 * configured in {@code application.properties} but is not required for the
 * knowledge store to work.
 *
 * <p>What the store guarantees, and what it therefore must be tested for:
 *
 * <ul>
 *   <li><b>Atomic imports.</b> An import runs in one transaction. A snapshot
 *       that fails validation imports NOTHING — there is no half-imported
 *       workspace.</li>
 *   <li><b>Durability.</b> {@code synchronous=FULL} and WAL journalling, so a
 *       committed import survives an interrupted process.</li>
 *   <li><b>Versioned schema.</b> {@code schema_meta} carries the version and
 *       every change has a migration. The schema is never edited in place.</li>
 *   <li><b>Provenance is never lost.</b> A claim's status may only be
 *       established when the snapshot carries the evidence for it — the store
 *       refuses an import that asserts a verified claim with no source.</li>
 *   <li><b>History is never lost.</b> Revisions are appended, never replaced;
 *       a superseded claim keeps its row.</li>
 * </ul>
 *
 * <p>This class is not thread-safe: it owns a single JDBC connection and is
 * meant to be owned by one component.
 */
public final class KnowledgeStore implements AutoCloseable {

    /** The schema version this build writes. */
    public static final int SCHEMA_VERSION = 15;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Connection connection;

    public KnowledgeStore(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            this.connection.setAutoCommit(true);
            configure();
            migrate();
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Cannot open the knowledge store at " + databaseFile, e);
        }
    }

    private void configure() throws SQLException {
        // Durability first: a committed write must be on disk before the
        // call returns, so an interrupted process cannot lose it.
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    // ── schema ────────────────────────────────────────────────────────────────

    private void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_meta ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        int version = readSchemaVersion();
        if (version == 0) {
            createSchemaV1();
            // The version is recorded as 1 and the migration path then runs,
            // so a fresh database is built by the same code that upgrades an
            // old one. A database that skips a migration is a database whose
            // schema nobody has ever tested.
            writeSchemaVersion(1);
            version = 1;
        }
        while (version < SCHEMA_VERSION) {
            version = migrateFrom(version);
        }
    }

    /**
     * The schema version this database is currently at.
     *
     * <p>Exposed so that a caller - and a test - can tell whether the
     * migration chain ran, rather than assuming it did.
     */
    public int schemaVersion() {
        try {
            return readSchemaVersion();
        } catch (SQLException e) {
            return 0;
        }
    }

    private int readSchemaVersion() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM schema_meta WHERE key='version'")) {
            if (!rs.next()) {
                return 0;
            }
            return Integer.parseInt(rs.getString(1));
        } catch (SQLException e) {
            return 0;
        }
    }

    private void writeSchemaVersion(int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO schema_meta(key, value) VALUES('version', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, Integer.toString(version));
            ps.executeUpdate();
        }
    }

    private void createSchemaV1() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS workspace (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    version INTEGER NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS concept (
                    id TEXT PRIMARY KEY,
                    workspace_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    definition TEXT,
                    domain TEXT,
                    created_at TEXT NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS source (
                    id TEXT PRIMARY KEY,
                    workspace_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    locator TEXT,
                    authority INTEGER NOT NULL,
                    freshness INTEGER NOT NULL,
                    excerpt TEXT,
                    added_at TEXT NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS claim (
                    id TEXT PRIMARY KEY,
                    workspace_id TEXT NOT NULL,
                    concept_id TEXT NOT NULL,
                    proposition TEXT NOT NULL,
                    scope TEXT,
                    context TEXT,
                    status TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    version INTEGER NOT NULL,
                    introduced_by_source_id TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS evidence (
                    id TEXT PRIMARY KEY,
                    workspace_id TEXT NOT NULL,
                    claim_id TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    relation TEXT NOT NULL,
                    excerpt TEXT,
                    quality INTEGER NOT NULL,
                    recorded_at TEXT NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS representation (
                    id TEXT PRIMARY KEY,
                    workspace_id TEXT NOT NULL,
                    concept_id TEXT NOT NULL,
                    level TEXT NOT NULL,
                    audience TEXT,
                    statement TEXT NOT NULL,
                    scope TEXT,
                    assumptions TEXT NOT NULL,
                    included_facts TEXT NOT NULL,
                    omitted TEXT NOT NULL,
                    validity_conditions TEXT NOT NULL,
                    prerequisites TEXT NOT NULL,
                    refines TEXT,
                    confidence REAL NOT NULL,
                    created_at TEXT NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS prerequisite (
                    workspace_id TEXT NOT NULL,
                    concept_id TEXT NOT NULL,
                    requires_concept_id TEXT NOT NULL,
                    required INTEGER NOT NULL,
                    note TEXT,
                    PRIMARY KEY (workspace_id, concept_id, requires_concept_id)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS revision (
                    id TEXT PRIMARY KEY,
                    workspace_id TEXT NOT NULL,
                    target_type TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    added_facts TEXT NOT NULL,
                    still_valid TEXT NOT NULL,
                    no_longer_valid TEXT NOT NULL,
                    newly_explained TEXT NOT NULL,
                    evidence_ids TEXT NOT NULL,
                    recorded_at TEXT NOT NULL
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_concept_ws ON concept(workspace_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_claim_ws ON claim(workspace_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_claim_concept ON claim(concept_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_evidence_claim ON evidence(claim_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_repr_concept ON representation(concept_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_revision_target ON revision(target_id)");
        }
    }

    /**
     * Recovers the warrant of the claims that predate the axis.
     *
     * <p>A claim's warrant follows from the kind of the source behind its
     * evidence, which the store already holds. A claim whose source was an
     * observation or an experiment was established by perception; one whose
     * source was a publication, a reference or a report was established by
     * testimony; one whose source was a mathematical derivation was
     * inferred. A claim with no source behind it gets no warrant: it was
     * asserted, and asserting is not knowing.
     *
     * <p>The evidence is consulted rather than the claim's own source
     * field, because a claim that only CONTRADICTS another claim is
     * established by that contradicting source: what we know about it is
     * that it does not hold.
     */
    private void backfillWarrant() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT c.id, s.kind FROM claim c LEFT JOIN source s ON s.id = ("
                             + "SELECT e.source_id FROM evidence e WHERE e.claim_id = c.id "
                             + "ORDER BY e.id LIMIT 1)")) {
            List<String[]> pending = new ArrayList<>();
            while (rs.next()) {
                String kind = rs.getString("kind");
                pending.add(new String[]{rs.getString("id"), warrantOfKind(kind).name()});
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE claim SET warrant = ?, derivation_status = ? WHERE id = ?")) {
                for (String[] row : pending) {
                    ps.setString(1, row[1]);
                    ps.setString(2, DerivationStatus.NOT_DERIVED.name());
                    ps.setString(3, row[0]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    /**
     * The warrant a source of this kind provides.
     *
     * @param kind the source kind, or null when there is no source
     * @return the warrant the source establishes
     */
    public static ClaimWarrant warrantOfKind(String kind) {
        if (kind == null) {
            return ClaimWarrant.NONE;
        }
        return switch (SourceKind.valueOf(kind)) {
            case OBSERVATION, EXPERIMENT -> ClaimWarrant.OBSERVED;
            case AUTHORITATIVE_REFERENCE, PUBLICATION, SECONDARY_REPORT, DATASET, HUMAN_ASSERTION ->
                    ClaimWarrant.TESTIMONY;
            case MATHEMATICAL_DERIVATION -> ClaimWarrant.INFERRED;
            case MODEL_GENERATED -> ClaimWarrant.NONE;
        };
    }

    /**
     * Fills the provenance of records that predate the provenance fields.
     *
     * <p>What can honestly be recovered is recovered: a source's content
     * hash is computed from the excerpt that was actually stored, so the
     * material it was read from is still identifiable. What cannot be
     * recovered is marked as unknown rather than invented: no old record can
     * say how it was extracted, and the migration will not pretend it did.
     */
    private void backfillProvenance() throws SQLException {
        String unknown = "unknown: the record predates the provenance fields";
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE claim SET extraction_method = ? WHERE extraction_method IS NULL")) {
            ps.setString(1, unknown);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE claim SET validation_method = ? WHERE validation_method IS NULL")) {
            ps.setString(1, unknown);
            ps.executeUpdate();
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, excerpt FROM source WHERE content_hash IS NULL")) {
            List<String[]> pending = new ArrayList<>();
            while (rs.next()) {
                pending.add(new String[]{rs.getString("id"),
                        rs.getString("excerpt") == null ? "" : rs.getString("excerpt")});
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE source SET content_hash = ? WHERE id = ?")) {
                for (String[] row : pending) {
                    ps.setString(1, Ids.digest(row[1]));
                    ps.setString(2, row[0]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private int migrateFrom(int version) throws SQLException {
        if (version == 14) {
            // v15 adds the table of the HEBBIAN BINDINGS.
            //
            // The owner's decision D-012: the shadow classes are wired to
            // the live engines. The ErrorDrivenLearner is the live engine
            // of the error-driven forgetting, and the consolidation is the
            // live phase that forgets. The state the decay acts on has to
            // survive a restart - the mission's goal says the improvement
            // must remain after a forced termination.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS hebbian_binding ("
                        + "workspace_id TEXT NOT NULL, concept_id TEXT NOT NULL, "
                        + "weights TEXT NOT NULL, accumulator TEXT NOT NULL, "
                        + "updated_at TEXT NOT NULL, "
                        + "PRIMARY KEY(workspace_id, concept_id))");
            }
            writeSchemaVersion(15);
            return 15;
        }
        if (version == 13) {
            // v14 adds the table of the GENERATED assessment items.
            //
            // The assessment dataset came from the knowledge package, so a
            // concept the workspace acquired from a document had nothing
            // to practise with. The generated items are part of the
            // workspace's assessment, and they must survive a restart -
            // the mission's goal says the improvement must remain after a
            // forced termination.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS assessment_item ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "concept_id TEXT NOT NULL, dimension TEXT NOT NULL, "
                        + "prompt TEXT NOT NULL, key_terms TEXT NOT NULL, "
                        + "difficulty INTEGER NOT NULL, variant TEXT NOT NULL, "
                        + "critical INTEGER NOT NULL, held_out INTEGER NOT NULL, "
                        + "recorded_at TEXT NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_assessment_item_workspace "
                        + "ON assessment_item(workspace_id, id)");
            }
            writeSchemaVersion(14);
            return 14;
        }
        if (version == 12) {
            // v13 adds the language of a source.
            //
            // The importer DETECTS the language of a document and the
            // corpus manager reports it, but the store dropped it on the
            // way in - so the registry could not say what language a
            // source was written in, which is part of the mission's §5.5's
            // "structure of the source".
            try (Statement st = connection.createStatement()) {
                if (!sourceColumnExists(st, "language")) {
                    st.execute("ALTER TABLE source ADD COLUMN language TEXT");
                }
                if (!sourceColumnExists(st, "format")) {
                    st.execute("ALTER TABLE source ADD COLUMN format TEXT");
                }
            }
            writeSchemaVersion(13);
            return 13;
        }
        if (version == 11) {
            // v12 adds the table of the conversation's turns.
            //
            // The mission's §6 asks for a bounded PERSISTED conversation
            // history. The context was held in memory, so a restart lost
            // it - and "explain this in more detail" had no referent after
            // a restart, which is exactly the case the mission names.
            //
            // The table is bounded the way the context is: the live turns
            // are the last MAXIMUM_TURNS. The turns that fall out of the
            // working memory are MARKED, not deleted - the mission's §8
            // forbids destroying the historical evidence, and the record
            // of what was asked and how it was classified is evidence.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS dialogue_turn ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "question TEXT NOT NULL, concept_ids TEXT NOT NULL, "
                        + "concept_names TEXT NOT NULL, claim_ids TEXT NOT NULL, "
                        + "language TEXT NOT NULL, answered INTEGER NOT NULL, "
                        + "act TEXT NOT NULL, recorded_at TEXT NOT NULL, "
                        + "forgotten_at INTEGER)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_dialogue_turn_workspace "
                        + "ON dialogue_turn(workspace_id, recorded_at)");
            }
            writeSchemaVersion(12);
            return 12;
        }
        if (version == 10) {
            // v11 adds the table of the verified learning errors.
            //
            // The mission's §9: the error-driven learning integration
            // happens only after the error is verified. A record of an
            // error that has not been repeated is not evidence of a gap,
            // and the outcomes keep it - the table holds the VERIFIED
            // errors only.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS learning_error ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "item_id TEXT NOT NULL, concept_id TEXT NOT NULL, "
                        + "dimension TEXT NOT NULL, learner_answer TEXT NOT NULL, "
                        + "expected_answer TEXT NOT NULL, verified INTEGER NOT NULL, "
                        + "recorded_at TEXT NOT NULL, "
                        + "UNIQUE (workspace_id, item_id))");
                st.execute("CREATE INDEX IF NOT EXISTS idx_learning_error_workspace "
                        + "ON learning_error(workspace_id, concept_id)");
            }
            writeSchemaVersion(11);
            return 11;
        }
        if (version == 9) {
            // v10 adds the tombstone columns of the dialogue memory.
            //
            // A forgotten record is MARKED, never deleted: the mission's
            // §8 forbids destroying the historical evidence, and the record
            // of what the user asked and what the system could not answer
            // is exactly that evidence.
            //
            // The first version of this migration sat in the v2 block,
            // where it only ever ran on a database that had never had the
            // asked-question table - so on an existing database the columns
            // were never added, and the consolidation endpoint answered
            // with "no such column: forgotten_at". The migration belongs
            // to the version it belongs to.
            try (Statement st = connection.createStatement()) {
                if (!askedColumnExists(st, "forgotten_at")) {
                    st.execute("ALTER TABLE asked_question ADD COLUMN forgotten_at INTEGER");
                }
                if (!askedColumnExists(st, "forgotten_kind")) {
                    st.execute("ALTER TABLE asked_question ADD COLUMN forgotten_kind TEXT");
                }
                if (!askedColumnExists(st, "forgotten_reason")) {
                    st.execute("ALTER TABLE asked_question ADD COLUMN forgotten_reason TEXT");
                }
            }
            writeSchemaVersion(10);
            return 10;
        }
        if (version == 8) {
            // v9 adds the learning state machine's tables: the state a
            // capability is in, and the transitions it went through.
            //
            // The transitions are the point. A recomputed stage can say what
            // the state is now; only a recorded transition can say when it
            // changed, what changed it, and what it was before - and only a
            // recorded transition can preserve the earlier mastery when new
            // evidence contradicts the model.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS learning_state ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "concept_id TEXT NOT NULL, dimension TEXT NOT NULL, "
                        + "stage TEXT NOT NULL, score REAL NOT NULL, "
                        + "evidence_count INTEGER NOT NULL, "
                        + "transitioned_at TEXT NOT NULL, note TEXT, "
                        + "UNIQUE (workspace_id, concept_id, dimension))");
                st.execute("CREATE TABLE IF NOT EXISTS learning_transition ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "concept_id TEXT NOT NULL, dimension TEXT NOT NULL, "
                        + "from_stage TEXT, to_stage TEXT NOT NULL, "
                        + "reason TEXT NOT NULL, outcome_id TEXT, "
                        + "occurred_at TEXT NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_learning_state_workspace "
                        + "ON learning_state(workspace_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_learning_transition_workspace "
                        + "ON learning_transition(workspace_id, occurred_at)");
            }
            writeSchemaVersion(9);
            return 9;
        }
        if (version == 7) {
            // v8 adds the persistent goals. A goal is not part of the
            // knowledge: the knowledge store can be restored and the goal
            // still not be, which is exactly what the mission warns about.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS learning_goal ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, description TEXT NOT NULL, "
                        + "success_criteria TEXT NOT NULL, prerequisites TEXT NOT NULL, "
                        + "status TEXT NOT NULL, progress_evidence TEXT NOT NULL, "
                        + "failed_attempts TEXT NOT NULL, remaining_work TEXT, "
                        + "budget_units INTEGER NOT NULL, budget_used INTEGER NOT NULL, "
                        + "last_checkpoint TEXT, recovery_instructions TEXT, "
                        + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS learning_goal_subtask ("
                        + "goal_id TEXT NOT NULL, subtask_id TEXT NOT NULL, description TEXT NOT NULL, "
                        + "required INTEGER NOT NULL, prerequisites TEXT NOT NULL, "
                        + "status TEXT NOT NULL, evidence TEXT NOT NULL, note TEXT, "
                        + "PRIMARY KEY (goal_id, subtask_id))");
                st.execute("CREATE INDEX IF NOT EXISTS idx_goal_workspace "
                        + "ON learning_goal(workspace_id)");
            }
            writeSchemaVersion(8);
            return 8;
        }
        if (version == 6) {
            // v7 adds the recorded conflicts. A conflict is a record ABOUT two
            // claims; it neither deletes nor demotes either of them, and the
            // store keeps no count of records agreeing with one side.
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS contradiction ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "claim_a_id TEXT NOT NULL, claim_b_id TEXT NOT NULL, "
                        + "kind TEXT NOT NULL, detected_by TEXT NOT NULL, recorded_at TEXT NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_contradiction_workspace "
                        + "ON contradiction(workspace_id)");
            }
            writeSchemaVersion(7);
            return 7;
        }
        if (version == 5) {
            // v6 adds the epistemic axes the claim now carries, and the
            // table of recorded derivations.
            //
            // The warrant of an existing claim is recovered from the kind of
            // the source behind it: an observation or an experiment
            // established it by perception, a publication or a reference by
            // testimony. A claim with no source behind it keeps no warrant
            // - it was asserted, and asserting is not knowing.
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE claim ADD COLUMN warrant TEXT");
                st.execute("ALTER TABLE claim ADD COLUMN derivation_status TEXT");
                st.execute("CREATE TABLE IF NOT EXISTS derivation ("
                        + "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                        + "rule_id TEXT NOT NULL, rule_name TEXT NOT NULL, "
                        + "connective TEXT NOT NULL, conclusion_claim_id TEXT NOT NULL, "
                        + "status TEXT NOT NULL, recorded_at TEXT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS derivation_premise ("
                        + "derivation_id TEXT NOT NULL, ordinal INTEGER NOT NULL, "
                        + "claim_id TEXT NOT NULL, PRIMARY KEY (derivation_id, ordinal))");
                st.execute("CREATE INDEX IF NOT EXISTS idx_derivation_workspace "
                        + "ON derivation(workspace_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_derivation_premise_claim "
                        + "ON derivation_premise(claim_id)");
            }
            backfillWarrant();
            writeSchemaVersion(6);
            return 6;
        }
        if (version == 4) {
            // NOTE: the v1 CREATE TABLE above must stay the v1 shape. A
            // fresh database is built by the same code that upgrades an old
            // one (see migrate()), so a column added here AND here would
            // fail with "duplicate column name" on every fresh database.
            // v5 adds the provenance the mission's §5.2 requires: the
            // source's content hash and publication date, and the claim's
            // extraction and validation methods. A claim that says where it
            // came from must also say HOW it was extracted and HOW it was
            // validated, or "it has provenance" means nothing.
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE source ADD COLUMN content_hash TEXT");
                st.execute("ALTER TABLE source ADD COLUMN published_at TEXT");
                st.execute("ALTER TABLE claim ADD COLUMN extraction_method TEXT");
                st.execute("ALTER TABLE claim ADD COLUMN validation_method TEXT");
            }
            backfillProvenance();
            writeSchemaVersion(5);
            return 5;
        }
        if (version == 3) {
            // v4 adds the flag that says whether the workspace answered the
            // question when it was last asked. Without it the refusal rate
            // can only be guessed from the uncovered terms.
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE asked_question ADD COLUMN answered INTEGER NOT NULL DEFAULT 0");
            }
            writeSchemaVersion(4);
            return 4;
        }
        if (version == 2) {
            // v3 adds the episodic log of the questions the workspace was
            // asked. A learning application needs to know what it was asked
            // and could not answer, or it cannot show the user what it does
            // not know.
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS asked_question (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        canonical_key TEXT NOT NULL,
                        question TEXT NOT NULL,
                        uncovered_terms TEXT NOT NULL,
                        times_asked INTEGER NOT NULL,
                        first_asked_at INTEGER NOT NULL,
                        last_asked_at INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        resolution_note TEXT
                    )""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_asked_ws ON asked_question(workspace_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_asked_key ON asked_question(workspace_id, canonical_key)");
            }
            writeSchemaVersion(3);
            return 3;
        }
        if (version == 1) {
            // v2 adds the assessment history: the learning state must
            // survive a restart, and the state is derived from the recorded
            // outcomes, so the outcomes themselves have to be durable.
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS assessment_outcome (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        concept_id TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        correct INTEGER NOT NULL,
                        score REAL NOT NULL,
                        claimed_confidence REAL NOT NULL,
                        learner_answer TEXT,
                        session_id TEXT,
                        answered_at INTEGER NOT NULL,
                        held_out INTEGER NOT NULL
                    )""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_outcome_ws ON assessment_outcome(workspace_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_outcome_concept ON assessment_outcome(concept_id)");
            }
            writeSchemaVersion(2);
            return 2;
        }
        throw new IllegalStateException("No migration is defined from schema version " + version);
    }

    // ── import ────────────────────────────────────────────────────────────────

    /**
     * Import a whole workspace atomically.
     *
     * <p>The snapshot is validated BEFORE anything is written, so an invalid
     * snapshot leaves the store exactly as it was.
     */
    public void importWorkspace(KnowledgeSnapshot snapshot) {
        validate(snapshot);
        try {
            connection.setAutoCommit(false);
            try {
                deleteWorkspace(snapshot.workspaceId());
                insertWorkspace(snapshot);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to import workspace " + snapshot.workspaceId(), e);
        }
    }

    private void deleteWorkspace(String workspaceId) throws SQLException {
        for (String table : List.of("revision", "prerequisite", "representation", "evidence",
                "derivation", "contradiction", "learning_goal", "claim", "source", "concept")) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE workspace_id = ?")) {
                ps.setString(1, workspaceId);
                ps.executeUpdate();
            }
        }
        // The premise table has no workspace of its own: a premise belongs
        // to its derivation, so the derivations' premises go with them. The
        // same holds for a subtask, which belongs to its goal.
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM derivation_premise WHERE derivation_id IN "
                        + "(SELECT id FROM derivation WHERE workspace_id = ?)")) {
            ps.setString(1, workspaceId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM learning_goal_subtask WHERE goal_id IN "
                        + "(SELECT id FROM learning_goal WHERE workspace_id = ?)")) {
            ps.setString(1, workspaceId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM workspace WHERE id = ?")) {
            ps.setString(1, workspaceId);
            ps.executeUpdate();
        }
    }

    private void insertWorkspace(KnowledgeSnapshot snapshot) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO workspace(id, name, created_at, version) VALUES (?,?,?,?)")) {
            ps.setString(1, snapshot.workspaceId());
            ps.setString(2, snapshot.workspaceName() == null ? snapshot.workspaceId() : snapshot.workspaceName());
            ps.setString(3, now());
            ps.setInt(4, 1);
            ps.executeUpdate();
        }
        for (Concept c : snapshot.concepts()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO concept(id, workspace_id, name, definition, domain, created_at) "
                            + "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, c.id());
                ps.setString(2, c.workspaceId());
                ps.setString(3, c.name());
                ps.setString(4, c.definition());
                ps.setString(5, c.domain());
                ps.setString(6, c.createdAt());
                ps.executeUpdate();
            }
        }
        for (Source s : snapshot.sources()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO source(id, workspace_id, kind, title, locator, authority, "
                            + "freshness, excerpt, added_at, content_hash, published_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, s.id());
                ps.setString(2, s.workspaceId());
                ps.setString(3, s.kind().name());
                ps.setString(4, s.title());
                ps.setString(5, s.locator());
                ps.setInt(6, s.authority());
                ps.setInt(7, s.freshness());
                ps.setString(8, s.excerpt());
                ps.setString(9, s.addedAt());
                ps.setString(10, s.contentHash());
                ps.setString(11, s.publishedAt());
                ps.executeUpdate();
            }
        }
        for (Claim c : snapshot.claims()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO claim(id, workspace_id, concept_id, proposition, scope, "
                            + "context, status, confidence, version, introduced_by_source_id, "
                            + "extraction_method, validation_method, warrant, derivation_status, "
                            + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, c.id());
                ps.setString(2, c.workspaceId());
                ps.setString(3, c.conceptId());
                ps.setString(4, c.proposition());
                ps.setString(5, c.scope());
                ps.setString(6, c.context());
                ps.setString(7, c.status().name());
                ps.setDouble(8, c.confidence());
                ps.setInt(9, c.version());
                ps.setString(10, c.introducedBySourceId());
                ps.setString(11, c.extractionMethod());
                ps.setString(12, c.validationMethod());
                ps.setString(13, c.warrant().name());
                ps.setString(14, c.derivationStatus().name());
                ps.setString(15, c.createdAt());
                ps.setString(16, c.updatedAt());
                ps.executeUpdate();
            }
        }
        for (Derivation derivation : snapshot.derivations()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO derivation(id, workspace_id, rule_id, rule_name, "
                            + "connective, conclusion_claim_id, status, recorded_at) "
                            + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, derivation.id());
                ps.setString(2, derivation.workspaceId());
                ps.setString(3, derivation.ruleId());
                ps.setString(4, derivation.ruleName());
                ps.setString(5, derivation.connective().name());
                ps.setString(6, derivation.conclusionClaimId());
                ps.setString(7, derivation.status().name());
                ps.setString(8, derivation.recordedAt());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO derivation_premise(derivation_id, ordinal, claim_id) "
                            + "VALUES (?,?,?)")) {
                for (int i = 0; i < derivation.premiseClaimIds().size(); i++) {
                    ps.setString(1, derivation.id());
                    ps.setInt(2, i);
                    ps.setString(3, derivation.premiseClaimIds().get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        for (Evidence e : snapshot.evidence()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO evidence(id, workspace_id, claim_id, source_id, relation, "
                            + "excerpt, quality, recorded_at) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, e.id());
                ps.setString(2, e.workspaceId());
                ps.setString(3, e.claimId());
                ps.setString(4, e.sourceId());
                ps.setString(5, e.relation().name());
                ps.setString(6, e.excerpt());
                ps.setInt(7, e.quality());
                ps.setString(8, e.recordedAt());
                ps.executeUpdate();
            }
        }
        for (Representation r : snapshot.representations()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO representation(id, workspace_id, concept_id, level, audience, "
                            + "statement, scope, assumptions, included_facts, omitted, validity_conditions, "
                            + "prerequisites, refines, confidence, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, r.id());
                ps.setString(2, r.workspaceId());
                ps.setString(3, r.conceptId());
                ps.setString(4, r.level().name());
                ps.setString(5, r.audience());
                ps.setString(6, r.statement());
                ps.setString(7, r.scope());
                ps.setString(8, writeJson(r.assumptions()));
                ps.setString(9, writeJson(r.includedFacts()));
                ps.setString(10, writeJson(r.deliberatelyOmitted()));
                ps.setString(11, writeJson(r.validityConditions()));
                ps.setString(12, writeJson(r.prerequisiteConceptIds()));
                ps.setString(13, r.refinesRepresentationId());
                ps.setDouble(14, r.confidence());
                ps.setString(15, r.createdAt());
                ps.executeUpdate();
            }
        }
        for (Prerequisite p : snapshot.prerequisites()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO prerequisite(workspace_id, concept_id, requires_concept_id, "
                            + "required, note) VALUES (?,?,?,?,?)")) {
                ps.setString(1, p.workspaceId());
                ps.setString(2, p.conceptId());
                ps.setString(3, p.requiresConceptId());
                ps.setInt(4, p.required() ? 1 : 0);
                ps.setString(5, p.note());
                ps.executeUpdate();
            }
        }
        for (KnowledgeRevision rev : snapshot.revisions()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO revision(id, workspace_id, target_type, target_id, kind, "
                            + "summary, added_facts, still_valid, no_longer_valid, newly_explained, "
                            + "evidence_ids, recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, rev.id());
                ps.setString(2, rev.workspaceId());
                ps.setString(3, rev.targetType());
                ps.setString(4, rev.targetId());
                ps.setString(5, rev.kind().name());
                ps.setString(6, rev.summary());
                ps.setString(7, writeJson(rev.addedFacts()));
                ps.setString(8, writeJson(rev.stillValidAssumptions()));
                ps.setString(9, writeJson(rev.noLongerValidAssumptions()));
                ps.setString(10, writeJson(rev.newlyExplainedCases()));
                ps.setString(11, writeJson(rev.evidenceIds()));
                ps.setString(12, rev.recordedAt());
                ps.executeUpdate();
            }
        }
    }

    // ── validation ───────────────────────────────────────────────────────────

    /**
     * The rules a snapshot must satisfy. These are the rules that make a
     * claim's status mean something.
     */
    static void validate(KnowledgeSnapshot snapshot) {
        if (snapshot.workspaceId() == null || snapshot.workspaceId().isBlank()) {
            throw new IllegalArgumentException("workspace id must not be blank");
        }
        Set<String> conceptIds = new HashSet<>();
        for (Concept c : snapshot.concepts()) {
            if (!conceptIds.add(c.id())) {
                throw new IllegalArgumentException("duplicate concept id: " + c.id());
            }
        }
        Set<String> sourceIds = new HashSet<>();
        for (Source s : snapshot.sources()) {
            if (!sourceIds.add(s.id())) {
                throw new IllegalArgumentException("duplicate source id: " + s.id());
            }
        }
        Set<String> claimIds = new HashSet<>();
        for (Claim c : snapshot.claims()) {
            if (!claimIds.add(c.id())) {
                throw new IllegalArgumentException("duplicate claim id: " + c.id());
            }
            if (!conceptIds.contains(c.conceptId())) {
                throw new IllegalArgumentException(
                        "claim " + c.id() + " references unknown concept " + c.conceptId());
            }
        }
        Set<String> representationIds = new HashSet<>();
        for (Representation r : snapshot.representations()) {
            representationIds.add(r.id());
        }
        for (Representation r : snapshot.representations()) {
            if (!conceptIds.contains(r.conceptId())) {
                throw new IllegalArgumentException(
                        "representation " + r.id() + " references unknown concept " + r.conceptId());
            }
            if (r.refinesRepresentationId() != null
                    && !representationIds.contains(r.refinesRepresentationId())) {
                throw new IllegalArgumentException(
                        "representation " + r.id() + " refines unknown representation "
                                + r.refinesRepresentationId());
            }
            for (String prerequisite : r.prerequisiteConceptIds()) {
                if (!conceptIds.contains(prerequisite)) {
                    throw new IllegalArgumentException(
                            "representation " + r.id() + " requires unknown concept " + prerequisite);
                }
            }
        }
        Map<String, Integer> evidencePerClaim = new HashMap<>();
        for (Evidence e : snapshot.evidence()) {
            if (!claimIds.contains(e.claimId())) {
                throw new IllegalArgumentException(
                        "evidence " + e.id() + " references unknown claim " + e.claimId());
            }
            if (!sourceIds.contains(e.sourceId())) {
                throw new IllegalArgumentException(
                        "evidence " + e.id() + " references unknown source " + e.sourceId());
            }
            evidencePerClaim.merge(e.claimId(), 1, Integer::sum);
        }
        for (Claim c : snapshot.claims()) {
            // A source being available does not prove a claim. Only a claim
            // with evidence may assert that it is supported, verified,
            // contested, refuted or superseded.
            boolean needsEvidence = c.status() == ClaimStatus.SUPPORTED
                    || c.status() == ClaimStatus.VERIFIED
                    || c.status() == ClaimStatus.CONTESTED
                    || c.status() == ClaimStatus.REFUTED
                    || c.status() == ClaimStatus.SUPERSEDED;
            if (needsEvidence && evidencePerClaim.getOrDefault(c.id(), 0) == 0) {
                throw new IllegalArgumentException(
                        "claim " + c.id() + " is " + c.status() + " but carries no evidence");
            }
        }
        // Prerequisites must be acyclic: a learner cannot require a concept
        // that requires it back.
        Map<String, Set<String>> graph = new HashMap<>();
        for (Prerequisite p : snapshot.prerequisites()) {
            if (!conceptIds.contains(p.conceptId()) || !conceptIds.contains(p.requiresConceptId())) {
                throw new IllegalArgumentException(
                        "prerequisite references an unknown concept: " + p.conceptId()
                                + " -> " + p.requiresConceptId());
            }
            graph.computeIfAbsent(p.conceptId(), k -> new HashSet<>()).add(p.requiresConceptId());
        }
        for (String start : graph.keySet()) {
            Set<String> visited = new HashSet<>();
            if (hasCycle(graph, start, visited)) {
                throw new IllegalArgumentException(
                        "prerequisite cycle detected at concept " + start);
            }
        }
    }

    private static boolean hasCycle(Map<String, Set<String>> graph, String node, Set<String> visited) {
        if (!visited.add(node)) {
            return true;
        }
        for (String next : graph.getOrDefault(node, Set.of())) {
            if (hasCycle(graph, next, visited)) {
                return true;
            }
        }
        visited.remove(node);
        return false;
    }

    /**
     * Apply incremental changes to a workspace, in one transaction.
     *
     * <p>Claims are upserted, evidence is inserted, and revisions are
     * appended. A superseded claim keeps its row with its new status, so the
     * history of what was believed is never destroyed.
     *
     * <p>The evidence rule still holds: a change that asserts an established
     * status without evidence for it is rejected, and the rejection leaves
     * the workspace exactly as it was.
     */
    public void applyChanges(String workspaceId, KnowledgeChanges changes) {
        KnowledgeSnapshot current = exportWorkspace(workspaceId);
        List<Claim> allClaims = new ArrayList<>(current.claims());
        allClaims.removeIf(c -> changes.claims().stream()
                .anyMatch(changed -> changed.id().equals(c.id())));
        allClaims.addAll(changes.claims());

        List<Evidence> allEvidence = new ArrayList<>(current.evidence());
        allEvidence.addAll(changes.evidence());

        // The rule is checked against the evidence the workspace will have
        // after the change, not only against the evidence being added.
        Map<String, Integer> evidencePerClaim = new HashMap<>();
        for (Evidence evidence : allEvidence) {
            evidencePerClaim.merge(evidence.claimId(), 1, Integer::sum);
        }
        for (Claim claim : changes.claims()) {
            boolean needsEvidence = claim.status() == ClaimStatus.SUPPORTED
                    || claim.status() == ClaimStatus.VERIFIED
                    || claim.status() == ClaimStatus.CONTESTED
                    || claim.status() == ClaimStatus.REFUTED
                    || claim.status() == ClaimStatus.SUPERSEDED;
            if (needsEvidence && evidencePerClaim.getOrDefault(claim.id(), 0) == 0) {
                throw new IllegalArgumentException(
                        "claim " + claim.id() + " is " + claim.status() + " but carries no evidence");
            }
        }
        for (Evidence evidence : changes.evidence()) {
            boolean claimKnown = allClaims.stream()
                    .anyMatch(c -> c.id().equals(evidence.claimId()));
            if (!claimKnown) {
                throw new IllegalArgumentException(
                        "evidence " + evidence.id() + " references unknown claim " + evidence.claimId());
            }
            boolean sourceKnown = current.sources().stream()
                    .anyMatch(s -> s.id().equals(evidence.sourceId()))
                    || changes.sources().stream()
                    .anyMatch(s -> s.id().equals(evidence.sourceId()));
            if (!sourceKnown) {
                throw new IllegalArgumentException(
                        "evidence " + evidence.id() + " references unknown source " + evidence.sourceId());
            }
        }

        try {
            connection.setAutoCommit(false);
            try {
                for (Source source : changes.sources()) {
                    upsertSource(source);
                }
                for (Claim claim : changes.claims()) {
                    upsertClaim(claim);
                }
                for (Evidence evidence : changes.evidence()) {
                    insertEvidence(evidence);
                }
                for (Representation representation : changes.representations()) {
                    upsertRepresentation(representation);
                }
                for (KnowledgeRevision revision : changes.revisions()) {
                    insertRevision(revision);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply the knowledge changes", e);
        }
    }

    private void upsertSource(Source s) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO source(id, workspace_id, kind, title, locator, authority, "
                        + "freshness, excerpt, added_at, content_hash, published_at, language, "
                        + "format) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, s.id());
            ps.setString(2, s.workspaceId());
            ps.setString(3, s.kind().name());
            ps.setString(4, s.title());
            ps.setString(5, s.locator());
            ps.setInt(6, s.authority());
            ps.setInt(7, s.freshness());
            ps.setString(8, s.excerpt());
            ps.setString(9, s.addedAt());
            ps.setString(10, s.contentHash());
            ps.setString(11, s.publishedAt());
            ps.setString(12, s.language());
            ps.setString(13, s.format());
            ps.executeUpdate();
        }
    }

    private void upsertClaim(Claim c) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO claim(id, workspace_id, concept_id, proposition, scope, "
                        + "context, status, confidence, version, introduced_by_source_id, "
                        + "extraction_method, validation_method, warrant, derivation_status, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, c.id());
            ps.setString(2, c.workspaceId());
            ps.setString(3, c.conceptId());
            ps.setString(4, c.proposition());
            ps.setString(5, c.scope());
            ps.setString(6, c.context());
            ps.setString(7, c.status().name());
            ps.setDouble(8, c.confidence());
            ps.setInt(9, c.version());
            ps.setString(10, c.introducedBySourceId());
            ps.setString(11, c.extractionMethod());
            ps.setString(12, c.validationMethod());
            ps.setString(13, c.warrant().name());
            ps.setString(14, c.derivationStatus().name());
            ps.setString(15, c.createdAt());
            ps.setString(16, c.updatedAt());
            ps.executeUpdate();
        }
    }

    private void insertEvidence(Evidence e) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO evidence(id, workspace_id, claim_id, source_id, relation, "
                        + "excerpt, quality, recorded_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, e.id());
            ps.setString(2, e.workspaceId());
            ps.setString(3, e.claimId());
            ps.setString(4, e.sourceId());
            ps.setString(5, e.relation().name());
            ps.setString(6, e.excerpt());
            ps.setInt(7, e.quality());
            ps.setString(8, e.recordedAt());
            ps.executeUpdate();
        }
    }

    private void upsertRepresentation(Representation r) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO representation(id, workspace_id, concept_id, level, audience, "
                        + "statement, scope, assumptions, included_facts, omitted, validity_conditions, "
                        + "prerequisites, refines, confidence, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, r.id());
            ps.setString(2, r.workspaceId());
            ps.setString(3, r.conceptId());
            ps.setString(4, r.level().name());
            ps.setString(5, r.audience());
            ps.setString(6, r.statement());
            ps.setString(7, r.scope());
            ps.setString(8, writeJson(r.assumptions()));
            ps.setString(9, writeJson(r.includedFacts()));
            ps.setString(10, writeJson(r.deliberatelyOmitted()));
            ps.setString(11, writeJson(r.validityConditions()));
            ps.setString(12, writeJson(r.prerequisiteConceptIds()));
            ps.setString(13, r.refinesRepresentationId());
            ps.setDouble(14, r.confidence());
            ps.setString(15, r.createdAt());
            ps.executeUpdate();
        }
    }

    private void insertRevision(KnowledgeRevision rev) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO revision(id, workspace_id, target_type, target_id, kind, "
                        + "summary, added_facts, still_valid, no_longer_valid, newly_explained, "
                        + "evidence_ids, recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, rev.id());
            ps.setString(2, rev.workspaceId());
            ps.setString(3, rev.targetType());
            ps.setString(4, rev.targetId());
            ps.setString(5, rev.kind().name());
            ps.setString(6, rev.summary());
            ps.setString(7, writeJson(rev.addedFacts()));
            ps.setString(8, writeJson(rev.stillValidAssumptions()));
            ps.setString(9, writeJson(rev.noLongerValidAssumptions()));
            ps.setString(10, writeJson(rev.newlyExplainedCases()));
            ps.setString(11, writeJson(rev.evidenceIds()));
            ps.setString(12, rev.recordedAt());
            ps.executeUpdate();
        }
    }

    // ── the episodic log of asked questions ───────────────────────────────────

    /**
     * Record that a question was asked, and fold it into the record of its
     * canonical key.
     *
     * <p>Two wordings of the same question share one record: the count
     * grows, the earliest asking is kept, and the most recent wording is
     * kept because it is the one the user last used.
     */
    public AskedQuestion recordAskedQuestion(String workspaceId, String question,
                                             List<String> uncoveredTerms, boolean answered,
                                             long askedAt) {
        String key = QuestionConsolidator.canonicalKey(question);
        String id = Ids.askedQuestion(workspaceId, key);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT times_asked, answered, first_asked_at, last_asked_at, status, "
                        + "resolution_note FROM asked_question WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int times = rs.getInt("times_asked") + 1;
                    boolean wasAnswered = answered || rs.getInt("answered") == 1;
                    // A question the workspace could answer is not a gap.
                    // The status says whether there is still something it
                    // does not know, so an answered question is RESOLVED
                    // from the start - and the record of the asking is kept
                    // either way.
                    AskedQuestion.Status newStatus = wasAnswered
                            ? AskedQuestion.Status.RESOLVED
                            : AskedQuestion.Status.OPEN;
                    long first = rs.getLong("first_asked_at");
                    AskedQuestion.Status status = AskedQuestion.Status.valueOf(rs.getString("status"));
                    String note = rs.getString("resolution_note");
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE asked_question SET question = ?, uncovered_terms = ?, "
                                    + "times_asked = ?, last_asked_at = ?, status = ?, "
                                    + "resolution_note = ?, answered = ? WHERE id = ?")) {
                        update.setString(1, question);
                        update.setString(2, writeJson(uncoveredTerms));
                        update.setInt(3, times);
                        update.setLong(4, askedAt);
                        update.setString(5, newStatus.name());
                        update.setString(6, note);
                        update.setInt(7, wasAnswered ? 1 : 0);
                        update.setString(8, id);
                        update.executeUpdate();
                    }
                    return new AskedQuestion(id, workspaceId, key, question, uncoveredTerms,
                            times, wasAnswered, Instant.ofEpochMilli(first).toString(),
                            Instant.ofEpochMilli(askedAt).toString(), newStatus, note,
                            null, null, null);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read the asked question", e);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO asked_question(id, workspace_id, canonical_key, question, "
                        + "uncovered_terms, times_asked, first_asked_at, last_asked_at, status, "
                        + "resolution_note, answered) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, workspaceId);
            ps.setString(3, key);
            ps.setString(4, question);
            ps.setString(5, writeJson(uncoveredTerms));
            ps.setInt(6, 1);
            ps.setLong(7, askedAt);
            ps.setLong(8, askedAt);
            ps.setString(9, answered ? AskedQuestion.Status.RESOLVED.name()
                    : AskedQuestion.Status.OPEN.name());
            ps.setString(10, answered
                    ? "The workspace answered this question when it was asked."
                    : null);
            ps.setInt(11, answered ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record the asked question", e);
        }
        return new AskedQuestion(id, workspaceId, key, question, uncoveredTerms, 1, answered,
                Instant.ofEpochMilli(askedAt).toString(), Instant.ofEpochMilli(askedAt).toString(),
                answered ? AskedQuestion.Status.RESOLVED : AskedQuestion.Status.OPEN,
                answered ? "The workspace answered this question when it was asked." : null,
                null, null, null);
    }

    /** The asked questions of a workspace, most recently asked first. */
    public List<AskedQuestion> askedQuestions(String workspaceId) {
        return query("SELECT id, workspace_id, canonical_key, question, uncovered_terms, "
                + "times_asked, first_asked_at, last_asked_at, status, resolution_note, answered, "
                + "forgotten_at, forgotten_kind, forgotten_reason "
                + "FROM asked_question WHERE workspace_id = ? ORDER BY times_asked DESC, "
                + "last_asked_at DESC", workspaceId, rs -> new AskedQuestion(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("canonical_key"),
                rs.getString("question"), readJsonList(rs.getString("uncovered_terms")),
                rs.getInt("times_asked"), rs.getInt("answered") == 1,
                Instant.ofEpochMilli(rs.getLong("first_asked_at")).toString(),
                Instant.ofEpochMilli(rs.getLong("last_asked_at")).toString(),
                AskedQuestion.Status.valueOf(rs.getString("status")),
                rs.getString("resolution_note"),
                rs.getLong("forgotten_at") == 0 ? null
                        : Instant.ofEpochMilli(rs.getLong("forgotten_at")).toString(),
                rs.getString("forgotten_kind"),
                rs.getString("forgotten_reason")));
    }

    /**
     * Marks an asked question as forgotten.
     *
     * <p>The record is kept and marked, with the instant and the grounds of
     * the forgetting. An audit that cannot see this cannot tell a
     * forgetting from a loss.
     *
     * @param workspaceId the workspace
     * @param id the record's id
     * @param kind the rule that named the record
     * @param reason why the rule named it
     * @param nowMs the instant of the forgetting
     * @return true when a record was marked
     */
    public boolean forgetAskedQuestion(String workspaceId, String id, String kind, String reason,
                                       long nowMs) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE asked_question SET forgotten_at = ?, forgotten_kind = ?, "
                        + "forgotten_reason = ? WHERE workspace_id = ? AND id = ? "
                        + "AND forgotten_at IS NULL")) {
            ps.setLong(1, nowMs);
            ps.setString(2, kind);
            ps.setString(3, reason);
            ps.setString(4, workspaceId);
            ps.setString(5, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to forget the asked question", e);
        }
    }

    /**
     * Reads the Hebbian state of a workspace's concept.
     *
     * @param workspaceId the workspace
     * @param conceptId the concept
     * @return the state, or null when the workspace holds none
     */
    public io.matrix.neuron.HebbianUpdater.State hebbianState(String workspaceId, String conceptId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT weights, accumulator FROM hebbian_binding "
                        + "WHERE workspace_id = ? AND concept_id = ?")) {
            ps.setString(1, workspaceId);
            ps.setString(2, conceptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new io.matrix.neuron.HebbianUpdater.State(
                        io.matrix.neuron.HdcEncoding.fromBits(rs.getString(1)),
                        io.matrix.neuron.HdcEncoding.fromAccumulator(rs.getString(2)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read the hebbian state", e);
        }
    }

    /**
     * Writes the Hebbian state of a workspace's concept.
     *
     * @param workspaceId the workspace
     * @param conceptId the concept
     * @param state the state
     */
    public void saveHebbianState(String workspaceId, String conceptId,
                                  io.matrix.neuron.HebbianUpdater.State state) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hebbian_binding(workspace_id, concept_id, weights, accumulator, "
                        + "updated_at) VALUES(?, ?, ?, ?, ?) "
                        + "ON CONFLICT(workspace_id, concept_id) DO UPDATE SET "
                        + "weights = excluded.weights, accumulator = excluded.accumulator, "
                        + "updated_at = excluded.updated_at")) {
            ps.setString(1, workspaceId);
            ps.setString(2, conceptId);
            ps.setString(3, io.matrix.neuron.HdcEncoding.toBits(state.weights));
            ps.setString(4, io.matrix.neuron.HdcEncoding.toAccumulator(state.accumulator));
            ps.setString(5, Long.toString(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save the hebbian state", e);
        }
    }

    /**
     * The concepts of a workspace that hold a Hebbian state.
     *
     * @param workspaceId the workspace
     * @return the concept ids
     */
    public java.util.Set<String> hebbianConcepts(String workspaceId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT concept_id FROM hebbian_binding WHERE workspace_id = ?")) {
            ps.setString(1, workspaceId);
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
            return ids;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read the hebbian concepts", e);
        }
    }

    /**
     * Brings a forgotten record back.
     *
     * <p>RECON-W42.7. The consolidation's acceptance criterion needs to be
     * able to undo its own proposal: the sleep consolidator's rule is that
     * a proposal is taken only if the held-out measurement does not fall,
     * and the proposal is rolled back as a batch when it does. The record
     * is marked forgotten, not deleted, so the rollback is a matter of
     * clearing the mark.
     *
     * @param workspaceId the workspace
     * @param id the record
     * @return true when a record was brought back
     */
    public boolean reviveAskedQuestion(String workspaceId, String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE asked_question SET forgotten_at = NULL, forgotten_kind = NULL, "
                        + "forgotten_reason = NULL WHERE workspace_id = ? AND id = ? "
                        + "AND forgotten_at IS NOT NULL")) {
            ps.setString(1, workspaceId);
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revive the asked question", e);
        }
    }

    /**
     * Records the verified learning errors of a workspace.
     *
     * <p>A record of an error that has not been repeated is not evidence
     * of a gap, and the outcomes keep it - the table holds the VERIFIED
     * errors only.
     *
     * @param workspaceId the workspace
     * @param errors the errors
     */
    public void recordLearningErrors(String workspaceId, List<LearningError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO learning_error(id, workspace_id, item_id, concept_id, "
                        + "dimension, learner_answer, expected_answer, verified, recorded_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)")) {
            for (LearningError error : errors) {
                ps.setString(1, error.id());
                ps.setString(2, error.workspaceId());
                ps.setString(3, error.itemId());
                ps.setString(4, error.conceptId());
                ps.setString(5, error.dimension().name());
                ps.setString(6, error.learnerAnswer());
                ps.setString(7, error.expectedAnswer());
                ps.setInt(8, error.verified() ? 1 : 0);
                ps.setString(9, Instant.now().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record the learning errors", e);
        }
    }

    /**
     * Records the generated assessment items of a workspace.
     *
     * @param workspaceId the workspace
     * @param items the items
     */
    public void recordAssessmentItems(String workspaceId, List<AssessmentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO assessment_item(id, workspace_id, concept_id, dimension, "
                        + "prompt, key_terms, difficulty, variant, critical, held_out, "
                        + "recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            for (AssessmentItem item : items) {
                ps.setString(1, item.id());
                ps.setString(2, workspaceId);
                ps.setString(3, item.conceptId());
                ps.setString(4, item.dimension().name());
                ps.setString(5, item.prompt());
                ps.setString(6, String.join(",", item.keyTerms()));
                ps.setInt(7, item.difficulty());
                ps.setString(8, item.variant());
                ps.setInt(9, item.critical() ? 1 : 0);
                ps.setInt(10, item.heldOut() ? 1 : 0);
                ps.setString(11, java.time.Instant.now().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record the assessment items", e);
        }
    }

    /**
     * The generated assessment items of a workspace.
     *
     * @param workspaceId the workspace
     * @return the items
     */
    public List<AssessmentItem> assessmentItems(String workspaceId) {
        return query("SELECT id, workspace_id, concept_id, dimension, prompt, key_terms, "
                + "difficulty, variant, critical, held_out FROM assessment_item "
                + "WHERE workspace_id = ? ORDER BY id", workspaceId, rs -> new AssessmentItem(
                rs.getString("id"), rs.getString("concept_id"),
                LearningDimension.valueOf(rs.getString("dimension")),
                rs.getString("prompt"), splitList(rs.getString("key_terms")),
                rs.getInt("difficulty"), rs.getString("variant"),
                rs.getInt("critical") != 0, rs.getInt("held_out") != 0,
                List.of(), null));
    }

    /**
     * Records a turn of the conversation.
     *
     * @param workspaceId the workspace
     * @param turn the turn
     */
    public void recordDialogueTurn(String workspaceId, DialogueContext.Turn turn) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO dialogue_turn(id, workspace_id, question, concept_ids, "
                        + "concept_names, claim_ids, language, answered, act, recorded_at, "
                        + "forgotten_at) VALUES (?,?,?,?,?,?,?,?,?,?,NULL)")) {
            ps.setString(1, turn.id());
            ps.setString(2, workspaceId);
            ps.setString(3, turn.question());
            ps.setString(4, String.join(",", turn.conceptIds()));
            ps.setString(5, String.join(",", turn.conceptNames()));
            ps.setString(6, String.join(",", turn.claimIds()));
            ps.setString(7, turn.language());
            ps.setInt(8, turn.answered() ? 1 : 0);
            ps.setString(9, turn.act().name());
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record the dialogue turn", e);
        }
    }

    /**
     * The live turns of a workspace's conversation, oldest first.
     *
     * @param workspaceId the workspace
     * @param limit how many to read
     * @return the turns
     */
    public List<DialogueContext.Turn> dialogueTurns(String workspaceId, int limit) {
        List<DialogueContext.Turn> turns = query("SELECT id, workspace_id, question, "
                + "concept_ids, concept_names, claim_ids, language, answered, act "
                + "FROM dialogue_turn WHERE workspace_id = ? AND forgotten_at IS NULL "
                + "ORDER BY recorded_at DESC, id DESC LIMIT " + Math.max(1, limit),
                workspaceId, rs -> new DialogueContext.Turn(
                rs.getString("id"), rs.getString("question"),
                splitList(rs.getString("concept_ids")), splitList(rs.getString("concept_names")),
                splitList(rs.getString("claim_ids")), rs.getString("language"),
                rs.getInt("answered") != 0,
                DialogueAct.Act.valueOf(rs.getString("act"))));
        // The store reads the newest first; the conversation is oldest
        // first.
        java.util.Collections.reverse(turns);
        return turns;
    }

    /**
     * Marks the turns that fall out of the working memory.
     *
     * <p>The turns are MARKED, not deleted: the mission's §8 forbids
     * destroying the historical evidence, and the record of what was asked
     * and how it was classified is evidence.
     *
     * @param workspaceId the workspace
     * @param keep how many live turns to keep
     * @return the number of turns that were marked
     */
    public int forgetOldDialogueTurns(String workspaceId, int keep) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE dialogue_turn SET forgotten_at = ? WHERE workspace_id = ? "
                        + "AND forgotten_at IS NULL AND id NOT IN ("
                        + "SELECT id FROM dialogue_turn WHERE workspace_id = ? "
                        + "AND forgotten_at IS NULL ORDER BY recorded_at DESC, id DESC LIMIT ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, workspaceId);
            ps.setString(3, workspaceId);
            ps.setInt(4, Math.max(0, keep));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to forget the old dialogue turns", e);
        }
    }

    /** Whether the source table already has the column. */
    private static boolean sourceColumnExists(Statement st, String column) throws SQLException {
        try (var rs = st.executeQuery("PRAGMA table_info(source)")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Splits a comma-separated list. */
    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * The learning errors of a workspace.
     *
     * @param workspaceId the workspace
     * @return the errors
     */
    public List<LearningError> learningErrors(String workspaceId) {
        return query("SELECT id, workspace_id, item_id, concept_id, dimension, learner_answer, "
                + "expected_answer, verified FROM learning_error WHERE workspace_id = ? "
                + "ORDER BY concept_id, item_id", workspaceId, rs -> new LearningError(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("item_id"),
                rs.getString("concept_id"),
                LearningDimension.valueOf(rs.getString("dimension")),
                rs.getString("learner_answer"), rs.getString("expected_answer"),
                rs.getInt("verified") != 0));
    }

    /**
     * The records that were forgotten, and why.
     *
     * @param workspaceId the workspace
     * @return the forgotten records
     */
    public List<AskedQuestion> forgottenAskedQuestions(String workspaceId) {
        return askedQuestions(workspaceId).stream().filter(AskedQuestion::forgotten).toList();
    }

    /** Whether the asked-question table already has the column. */
    private static boolean askedColumnExists(Statement st, String column) throws SQLException {
        try (var rs = st.executeQuery("PRAGMA table_info(asked_question)")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Mark a question resolved.
     *
     * <p>The record is kept. A question that was once unanswerable and now
     * is not is evidence that the workspace learned something, and deleting
     * it would throw that evidence away.
     */
    public void resolveAskedQuestion(String workspaceId, String canonicalKey, String note) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE asked_question SET status = ?, resolution_note = ? "
                        + "WHERE workspace_id = ? AND canonical_key = ?")) {
            ps.setString(1, AskedQuestion.Status.RESOLVED.name());
            ps.setString(2, note);
            ps.setString(3, workspaceId);
            ps.setString(4, canonicalKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve the asked question", e);
        }
    }

    // ── assessment history ───────────────────────────────────────────────────
    /**
     * Record one assessment outcome.
     *
     * <p>The outcome is stored with the concept and dimension it belongs to,
     * so the learning state can be derived after a restart without the
     * dataset having to be present. Re-running the same item in the same
     * session replaces the earlier outcome rather than doubling it.
     */
    public void recordOutcome(String workspaceId, AssessmentItem item, AssessmentOutcome outcome) {
        String id = workspaceId + "|" + item.id() + "|" + outcome.sessionId() + "|"
                + outcome.answeredAt();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO assessment_outcome(id, workspace_id, item_id, concept_id, "
                        + "dimension, correct, score, claimed_confidence, learner_answer, session_id, "
                        + "answered_at, held_out) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, workspaceId);
            ps.setString(3, item.id());
            ps.setString(4, item.conceptId());
            ps.setString(5, item.dimension().name());
            ps.setInt(6, outcome.correct() ? 1 : 0);
            ps.setDouble(7, outcome.score());
            ps.setDouble(8, outcome.claimedConfidence());
            ps.setString(9, outcome.learnerAnswer());
            ps.setString(10, outcome.sessionId());
            ps.setLong(11, outcome.answeredAt());
            ps.setInt(12, item.heldOut() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record the assessment outcome", e);
        }
    }

    /** Every assessment outcome recorded for a workspace, oldest first. */
    public List<AssessmentOutcome> outcomes(String workspaceId) {
        return query("SELECT item_id, correct, score, claimed_confidence, learner_answer, "
                + "session_id, answered_at, held_out FROM assessment_outcome WHERE workspace_id = ? "
                + "ORDER BY answered_at, session_id, item_id", workspaceId, rs -> new AssessmentOutcome(
                rs.getString("item_id"), rs.getInt("correct") != 0, rs.getDouble("score"),
                rs.getDouble("claimed_confidence"), rs.getString("learner_answer"),
                rs.getString("session_id"), rs.getLong("answered_at"),
                rs.getInt("held_out") != 0));
    }

    /** The number of assessment outcomes recorded for a workspace. */
    public int outcomeCount(String workspaceId) {
        Integer count = queryOne("SELECT COUNT(*) AS n FROM assessment_outcome WHERE workspace_id = ?",
                workspaceId, rs -> rs.getInt("n"));
        return count == null ? 0 : count;
    }

    // ── reads ─────────────────────────────────────────────────────────────────

    public List<Concept> concepts(String workspaceId) {
        return query("SELECT id, workspace_id, name, definition, domain, created_at "
                + "FROM concept WHERE workspace_id = ? ORDER BY name", workspaceId, rs -> new Concept(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("name"),
                rs.getString("definition"), rs.getString("domain"), rs.getString("created_at")));
    }

    public List<Source> sources(String workspaceId) {
        return query("SELECT id, workspace_id, kind, title, locator, authority, freshness, excerpt, "
                + "added_at, content_hash, published_at, language, format "
                + "FROM source WHERE workspace_id = ? ORDER BY title", workspaceId, rs -> new Source(
                rs.getString("id"), rs.getString("workspace_id"),
                SourceKind.valueOf(rs.getString("kind")), rs.getString("title"),
                rs.getString("locator"), rs.getInt("authority"), rs.getInt("freshness"),
                rs.getString("excerpt"), rs.getString("added_at"),
                rs.getString("content_hash"), rs.getString("published_at"),
                rs.getString("language"), rs.getString("format")));
    }

    public List<Claim> claims(String workspaceId) {
        return query("SELECT id, workspace_id, concept_id, proposition, scope, context, status, "
                + "confidence, version, introduced_by_source_id, extraction_method, validation_method, "
                + "warrant, derivation_status, created_at, updated_at "
                + "FROM claim WHERE workspace_id = ? ORDER BY id", workspaceId, rs -> new Claim(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("concept_id"),
                rs.getString("proposition"), rs.getString("scope"), rs.getString("context"),
                ClaimStatus.valueOf(rs.getString("status")), rs.getDouble("confidence"),
                rs.getInt("version"), rs.getString("introduced_by_source_id"),
                rs.getString("extraction_method"), rs.getString("validation_method"),
                rs.getString("warrant") == null ? ClaimWarrant.NONE
                        : ClaimWarrant.valueOf(rs.getString("warrant")),
                rs.getString("derivation_status") == null ? DerivationStatus.NOT_DERIVED
                        : DerivationStatus.valueOf(rs.getString("derivation_status")),
                rs.getString("created_at"), rs.getString("updated_at")));
    }

    /**
     * Records an inference: the rule, its premises in order, and the
     * conclusion it produced.
     *
     * <p>A derivation that is not recorded cannot be shown, and the
     * mission's §6.1 requires the answer to be explainable "through its
     * supporting claims and operations".
     */
    public void recordDerivation(Derivation derivation) {
        validateDerivation(derivation);
        try {
            connection.setAutoCommit(false);
            try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO derivation(id, workspace_id, rule_id, rule_name, "
                            + "connective, conclusion_claim_id, status, recorded_at) "
                            + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, derivation.id());
                ps.setString(2, derivation.workspaceId());
                ps.setString(3, derivation.ruleId());
                ps.setString(4, derivation.ruleName());
                ps.setString(5, derivation.connective().name());
                ps.setString(6, derivation.conclusionClaimId());
                ps.setString(7, derivation.status().name());
                ps.setString(8, derivation.recordedAt());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM derivation_premise WHERE derivation_id = ?")) {
                ps.setString(1, derivation.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO derivation_premise(derivation_id, ordinal, claim_id) "
                            + "VALUES (?,?,?)")) {
                for (int i = 0; i < derivation.premiseClaimIds().size(); i++) {
                    ps.setString(1, derivation.id());
                    ps.setInt(2, i);
                    ps.setString(3, derivation.premiseClaimIds().get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the derivation", e);
        }
    }

    /**
     * Checks that a derivation can be recorded at all.
     *
     * <p>Three things must hold: the conclusion and every premise must be
     * claims this workspace holds; the one-premise connectives must have
     * exactly one premise, because IMPLIES and NOT read the first premise
     * and a rule that gives them more has no determined meaning; and the
     * derivation must name its rule.
     */
    private void validateDerivation(Derivation derivation) {
        if (derivation == null) {
            throw new IllegalArgumentException("the derivation must not be null");
        }
        if (derivation.ruleId() == null || derivation.ruleId().isBlank()) {
            throw new IllegalArgumentException("a derivation must name its rule");
        }
        if (derivation.conclusionClaimId() == null || derivation.conclusionClaimId().isBlank()) {
            throw new IllegalArgumentException("a derivation must name its conclusion");
        }
        if (derivation.premiseClaimIds().isEmpty()) {
            throw new IllegalArgumentException("a derivation must have at least one premise");
        }
        if (derivation.connective().usesFirstPremiseOnly() && derivation.premiseClaimIds().size() > 1) {
            throw new IllegalArgumentException("the connective " + derivation.connective()
                    + " reads the first premise only, so the derivation must have exactly one");
        }
        for (String claimId : derivation.premiseClaimIds()) {
            if (!claimExists(derivation.workspaceId(), claimId)) {
                throw new IllegalArgumentException(
                        "the premise " + claimId + " is not a claim of this workspace");
            }
        }
        if (!claimExists(derivation.workspaceId(), derivation.conclusionClaimId())) {
            throw new IllegalArgumentException(
                    "the conclusion " + derivation.conclusionClaimId() + " is not a claim of this workspace");
        }
    }

    private boolean claimExists(String workspaceId, String claimId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM claim WHERE id = ? AND workspace_id = ?")) {
            ps.setString(1, claimId);
            ps.setString(2, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to check the claim " + claimId, e);
        }
    }

    /** The derivations recorded for a workspace, oldest first. */
    public List<Derivation> derivations(String workspaceId) {
        Map<String, List<String>> premises = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT derivation_id, claim_id FROM derivation_premise "
                        + "WHERE derivation_id IN (SELECT id FROM derivation WHERE workspace_id = ?) "
                        + "ORDER BY derivation_id, ordinal")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    premises.computeIfAbsent(rs.getString("derivation_id"), key -> new ArrayList<>())
                            .add(rs.getString("claim_id"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the derivation premises", e);
        }
        return query("SELECT id, workspace_id, rule_id, rule_name, connective, "
                + "conclusion_claim_id, status, recorded_at FROM derivation "
                + "WHERE workspace_id = ? ORDER BY recorded_at, id", workspaceId, rs -> new Derivation(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("rule_id"),
                rs.getString("rule_name"),
                Derivation.Connective.valueOf(rs.getString("connective")),
                rs.getString("conclusion_claim_id"),
                premises.getOrDefault(rs.getString("id"), List.of()),
                DerivationStatus.valueOf(rs.getString("status")),
                rs.getString("recorded_at")));
    }

    /**
     * Records a learning goal, replacing any earlier record of it.
     *
     * <p>The goal is written whole, in one statement, so a goal is never
     * half-written: a process that dies mid-write leaves the previous
     * version, not a mixture.
     */
    public void recordGoal(LearningGoal goal) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO learning_goal(id, workspace_id, description, "
                        + "success_criteria, prerequisites, status, progress_evidence, "
                        + "failed_attempts, remaining_work, budget_units, budget_used, "
                        + "last_checkpoint, recovery_instructions, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, goal.id());
            ps.setString(2, goal.workspaceId());
            ps.setString(3, goal.description());
            ps.setString(4, join(goal.successCriteria()));
            ps.setString(5, join(goal.prerequisiteConceptIds()));
            ps.setString(6, goal.status().name());
            ps.setString(7, join(goal.progressEvidenceIds()));
            ps.setString(8, join(goal.failedAttempts().stream()
                    .map(attempt -> attempt.id() + "|" + attempt.attempt() + "|" + attempt.reason()
                            + "|" + attempt.recordedAt())
                    .toList()));
            ps.setString(9, goal.remainingWork());
            ps.setInt(10, goal.budgetUnits());
            ps.setInt(11, goal.budgetUsed());
            ps.setString(12, goal.lastCheckpoint());
            ps.setString(13, goal.recoveryInstructions());
            ps.setString(14, goal.createdAt());
            ps.setString(15, goal.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the goal " + goal.id(), e);
        }
    }

    /** The goals of a workspace. */
    public List<LearningGoal> goals(String workspaceId) {
        return query("SELECT id, workspace_id, description, success_criteria, "
                + "prerequisites, status, progress_evidence, failed_attempts, remaining_work, "
                + "budget_units, budget_used, last_checkpoint, recovery_instructions, "
                + "created_at, updated_at FROM learning_goal WHERE workspace_id = ? ORDER BY id",
                workspaceId, rs -> {
                    List<LearningGoal.FailedAttempt> failures = new ArrayList<>();
                    for (String entry : split(rs.getString("failed_attempts"))) {
                        String[] parts = entry.split("\\|", 4);
                        failures.add(new LearningGoal.FailedAttempt(
                                parts.length > 0 ? parts[0] : "",
                                parts.length > 1 ? parts[1] : "",
                                parts.length > 2 ? parts[2] : "",
                                parts.length > 3 ? parts[3] : ""));
                    }
                    return new LearningGoal(
                            rs.getString("id"), rs.getString("workspace_id"),
                            rs.getString("description"),
                            split(rs.getString("success_criteria")),
                            split(rs.getString("prerequisites")),
                            List.of(),
                            LearningGoal.Status.valueOf(rs.getString("status")),
                            split(rs.getString("progress_evidence")),
                            failures,
                            rs.getString("remaining_work"),
                            rs.getInt("budget_units"), rs.getInt("budget_used"),
                            rs.getString("last_checkpoint"), rs.getString("recovery_instructions"),
                            rs.getString("created_at"), rs.getString("updated_at"));
                });
    }

    /**
     * Records a goal together with its subtasks.
     *
     * <p>The subtasks live in their own table so that a resumed goal can be
     * inspected piece by piece, and so that a subtask's own evidence is
     * reachable without parsing the whole goal.
     */
    public void recordGoalWithSubtasks(LearningGoal goal) {
        recordGoal(goal);
        try {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM learning_goal_subtask WHERE goal_id = ?")) {
                    ps.setString(1, goal.id());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO learning_goal_subtask(goal_id, subtask_id, description, "
                                + "required, prerequisites, status, evidence, note) "
                                + "VALUES (?,?,?,?,?,?,?,?)")) {
                    for (LearningGoal.Subtask subtask : goal.subtasks()) {
                        ps.setString(1, goal.id());
                        ps.setString(2, subtask.id());
                        ps.setString(3, subtask.description());
                        ps.setBoolean(4, subtask.required());
                        ps.setString(5, join(subtask.prerequisiteSubtaskIds()));
                        ps.setString(6, subtask.status().name());
                        ps.setString(7, join(subtask.evidenceIds()));
                        ps.setString(8, subtask.note());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the subtasks of " + goal.id(), e);
        }
    }

    /** The goals of a workspace, with their subtasks attached. */
    public List<LearningGoal> goalsWithSubtasks(String workspaceId) {
        List<LearningGoal> goals = goals(workspaceId);
        List<LearningGoal> withSubtasks = new ArrayList<>();
        for (LearningGoal goal : goals) {
            List<LearningGoal.Subtask> subtasks = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT subtask_id, description, required, prerequisites, status, evidence, note "
                            + "FROM learning_goal_subtask WHERE goal_id = ? ORDER BY subtask_id")) {
                ps.setString(1, goal.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        subtasks.add(new LearningGoal.Subtask(
                                rs.getString("subtask_id"), rs.getString("description"),
                                rs.getBoolean("required"),
                                split(rs.getString("prerequisites")),
                                LearningGoal.Status.valueOf(rs.getString("status")),
                                split(rs.getString("evidence")), rs.getString("note")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("failed to read the subtasks of " + goal.id(), e);
            }
            withSubtasks.add(new LearningGoal(goal.id(), goal.workspaceId(), goal.description(),
                    goal.successCriteria(), goal.prerequisiteConceptIds(), subtasks, goal.status(),
                    goal.progressEvidenceIds(), goal.failedAttempts(), goal.remainingWork(),
                    goal.budgetUnits(), goal.budgetUsed(), goal.lastCheckpoint(),
                    goal.recoveryInstructions(), goal.createdAt(), goal.updatedAt()));
        }
        return withSubtasks;
    }

    /** Joins a list with the separator the store uses. */
    private static String join(List<String> values) {
        return values == null ? "" : String.join("\u001f", values);
    }

    /** Splits a stored list back apart. */
    private static List<String> split(String stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return List.of(stored.split("\u001f", -1));
    }

    /**
     * Records a conflict between two claims.
     *
     * <p>The record is about the two claims, not about either of them.
     * Recording a conflict changes nothing else: no claim's status moves,
     * and nothing is deleted.
     */
    public void recordContradiction(Contradiction contradiction) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO contradiction(id, workspace_id, claim_a_id, claim_b_id, "
                        + "kind, detected_by, recorded_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, contradiction.id());
            ps.setString(2, contradiction.workspaceId());
            ps.setString(3, contradiction.claimAId());
            ps.setString(4, contradiction.claimBId());
            ps.setString(5, contradiction.kind().name());
            ps.setString(6, contradiction.detectedBy());
            ps.setString(7, contradiction.recordedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the contradiction", e);
        }
    }

    /**
     * Records the learning state of a workspace and the transitions it went
     * through, in one transaction.
     *
     * <p>The two tables move together: a state without its transition
     * history is a label, and a transition without the state it produced is
     * an orphan. Writing them atomically means a reader never sees one
     * without the other.
     */
    public void recordLearningStates(String workspaceId, Map<String, LearningState> states,
                                      List<LearningTransition> transitions) {
        try {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR REPLACE INTO learning_state(id, workspace_id, concept_id, "
                                + "dimension, stage, score, evidence_count, transitioned_at, note) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                    for (LearningState state : states.values()) {
                        ps.setString(1, Ids.digest(workspaceId + "|" + state.conceptId() + "|"
                                + state.dimension().name()));
                        ps.setString(2, workspaceId);
                        ps.setString(3, state.conceptId());
                        ps.setString(4, state.dimension().name());
                        ps.setString(5, state.stage().name());
                        ps.setDouble(6, state.score());
                        ps.setInt(7, state.evidenceCount());
                        ps.setString(8, java.time.Instant.now().toString());
                        ps.setString(9, state.note());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR REPLACE INTO learning_transition(id, workspace_id, concept_id, "
                                + "dimension, from_stage, to_stage, reason, outcome_id, occurred_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                    for (LearningTransition transition : transitions) {
                        ps.setString(1, transition.id());
                        ps.setString(2, transition.workspaceId());
                        ps.setString(3, transition.conceptId());
                        ps.setString(4, transition.dimension().name());
                        ps.setString(5, transition.fromStage() == null ? null : transition.fromStage().name());
                        ps.setString(6, transition.toStage().name());
                        ps.setString(7, transition.reason());
                        ps.setString(8, transition.outcomeId());
                        ps.setString(9, transition.occurredAt());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the learning states of " + workspaceId, e);
        }
    }

    /** The recorded learning states of a workspace, keyed by concept and dimension. */
    public Map<String, LearningState> recordedLearningStates(String workspaceId) {
        Map<String, LearningState> states = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT concept_id, dimension, stage, score, evidence_count, note "
                        + "FROM learning_state WHERE workspace_id = ? ORDER BY concept_id, dimension")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LearningDimension dimension =
                            LearningDimension.valueOf(rs.getString("dimension"));
                    LearningState state = new LearningState(
                            rs.getString("concept_id"), dimension,
                            LearningStage.valueOf(rs.getString("stage")),
                            rs.getDouble("score"), rs.getInt("evidence_count"),
                            0, 0, 0, List.of(), rs.getString("note"));
                    states.put(LearningStateMachine.key(state.conceptId(), dimension), state);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the learning states of " + workspaceId, e);
        }
        return states;
    }

    /** The recorded transitions of a workspace, oldest first. */
    public List<LearningTransition> learningTransitions(String workspaceId) {
        List<LearningTransition> transitions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, workspace_id, concept_id, dimension, from_stage, to_stage, reason, "
                        + "outcome_id, occurred_at FROM learning_transition WHERE workspace_id = ? "
                        + "ORDER BY occurred_at, id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transitions.add(new LearningTransition(
                            rs.getString("id"), rs.getString("workspace_id"),
                            rs.getString("concept_id"),
                            LearningDimension.valueOf(rs.getString("dimension")),
                            rs.getString("from_stage") == null ? null
                                    : LearningStage.valueOf(rs.getString("from_stage")),
                            LearningStage.valueOf(rs.getString("to_stage")),
                            rs.getString("reason"), rs.getString("outcome_id"),
                            rs.getString("occurred_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the transitions of " + workspaceId, e);
        }
        return transitions;
    }

    /**
     * The concept with this name in the workspace, created if it is absent.
     *
     * <p>RECON-W42.2. The store had no way to add a concept: the concepts
     * came from the curated knowledge packages, and `KnowledgeRefiner`
     * could not create one either — so a genuinely new topic had no runtime
     * entry point even through the correction path. The acquisition
     * pipeline needs one: a document's subject IS a new concept.
     *
     * <p>The identity is derived from the workspace and the name, so
     * importing the same document twice finds the same concept rather than
     * making a second one.
     *
     * @param workspaceId the workspace
     * @param name the concept's name
     * @param definition what the concept is, when the document says so
     * @param domain the concept's domain, when the document says so
     * @return the concept, existing or new
     */
    public Concept ensureConcept(String workspaceId, String name, String definition,
                                  String domain) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("a workspace is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a concept name is required");
        }
        String id = Ids.digest(workspaceId + "|concept|" + name.strip().toLowerCase(java.util.Locale.ROOT));
        for (Concept concept : concepts(workspaceId)) {
            if (concept.id().equals(id)) {
                return concept;
            }
        }
        Concept concept = new Concept(id, workspaceId, name.strip(), definition, domain,
                java.time.Instant.now().toString());
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO concept(id, workspace_id, name, definition, domain, created_at) "
                        + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, concept.id());
            ps.setString(2, concept.workspaceId());
            ps.setString(3, concept.name());
            ps.setString(4, concept.definition());
            ps.setString(5, concept.domain());
            ps.setString(6, concept.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create the concept " + name, e);
        }
        return concept;
    }

    /** The recorded conflicts of a workspace. */
    public List<Contradiction> contradictions(String workspaceId) {
        return query("SELECT id, workspace_id, claim_a_id, claim_b_id, kind, detected_by, "
                + "recorded_at FROM contradiction WHERE workspace_id = ? ORDER BY id",
                workspaceId, rs -> new Contradiction(
                        rs.getString("id"), rs.getString("workspace_id"),
                        rs.getString("claim_a_id"), rs.getString("claim_b_id"),
                        Contradiction.Kind.valueOf(rs.getString("kind")),
                        rs.getString("detected_by"), rs.getString("recorded_at")));
    }

    public List<Evidence> evidence(String workspaceId) {
        return query("SELECT id, workspace_id, claim_id, source_id, relation, excerpt, quality, recorded_at "
                + "FROM evidence WHERE workspace_id = ? ORDER BY id", workspaceId, rs -> new Evidence(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("claim_id"),
                rs.getString("source_id"), EvidenceRelation.valueOf(rs.getString("relation")),
                rs.getString("excerpt"), rs.getInt("quality"), rs.getString("recorded_at")));
    }

    public List<Representation> representations(String workspaceId) {
        return query("SELECT id, workspace_id, concept_id, level, audience, statement, scope, assumptions, "
                + "included_facts, omitted, validity_conditions, prerequisites, refines, confidence, created_at "
                + "FROM representation WHERE workspace_id = ? ORDER BY concept_id, level", workspaceId,
                rs -> new Representation(
                        rs.getString("id"), rs.getString("workspace_id"), rs.getString("concept_id"),
                        AbstractionLevel.valueOf(rs.getString("level")), rs.getString("audience"),
                        rs.getString("statement"), rs.getString("scope"),
                        readJsonList(rs.getString("assumptions")),
                        readJsonList(rs.getString("included_facts")),
                        readJsonList(rs.getString("omitted")),
                        readJsonList(rs.getString("validity_conditions")),
                        readJsonList(rs.getString("prerequisites")),
                        rs.getString("refines"), rs.getDouble("confidence"),
                        rs.getString("created_at")));
    }

    public List<Prerequisite> prerequisites(String workspaceId) {
        return query("SELECT workspace_id, concept_id, requires_concept_id, required, note "
                + "FROM prerequisite WHERE workspace_id = ? ORDER BY concept_id", workspaceId,
                rs -> new Prerequisite(rs.getString("workspace_id"), rs.getString("concept_id"),
                        rs.getString("requires_concept_id"), rs.getInt("required") != 0,
                        rs.getString("note")));
    }

    public List<KnowledgeRevision> revisions(String workspaceId) {
        return query("SELECT id, workspace_id, target_type, target_id, kind, summary, added_facts, "
                + "still_valid, no_longer_valid, newly_explained, evidence_ids, recorded_at "
                + "FROM revision WHERE workspace_id = ? ORDER BY recorded_at, id", workspaceId,
                rs -> new KnowledgeRevision(
                        rs.getString("id"), rs.getString("workspace_id"), rs.getString("target_type"),
                        rs.getString("target_id"), RevisionKind.valueOf(rs.getString("kind")),
                        rs.getString("summary"), readJsonList(rs.getString("added_facts")),
                        readJsonList(rs.getString("still_valid")),
                        readJsonList(rs.getString("no_longer_valid")),
                        readJsonList(rs.getString("newly_explained")),
                        readJsonList(rs.getString("evidence_ids")), rs.getString("recorded_at")));
    }

    /** Export the whole workspace as one snapshot. */
    public KnowledgeSnapshot exportWorkspace(String workspaceId) {
        String name = queryOne("SELECT name FROM workspace WHERE id = ?", workspaceId,
                rs -> rs.getString("name"));
        return new KnowledgeSnapshot(workspaceId, name,
                concepts(workspaceId), sources(workspaceId), claims(workspaceId), evidence(workspaceId),
                representations(workspaceId), prerequisites(workspaceId), revisions(workspaceId),
                derivations(workspaceId), now());
    }

    /** The workspace identifiers this store holds. */
    public List<String> workspaces() {
        List<String> ids = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM workspace ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list workspaces", e);
        }
        return ids;
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private <T> List<T> query(String sql, String workspaceId, RowMapper<T> mapper) {
        List<T> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
        return out;
    }

    private <T> T queryOne(String sql, String workspaceId, RowMapper<T> mapper) {
        List<T> rows = query(sql, workspaceId, mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String writeJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise a list", e);
        }
    }

    private static List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialise a list: " + json, e);
        }
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to close the knowledge store", e);
        }
    }
}
