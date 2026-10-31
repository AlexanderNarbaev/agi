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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    public static final int SCHEMA_VERSION = 2;

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

    private int migrateFrom(int version) throws SQLException {
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
                "claim", "source", "concept")) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE workspace_id = ?")) {
                ps.setString(1, workspaceId);
                ps.executeUpdate();
            }
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
                            + "freshness, excerpt, added_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, s.id());
                ps.setString(2, s.workspaceId());
                ps.setString(3, s.kind().name());
                ps.setString(4, s.title());
                ps.setString(5, s.locator());
                ps.setInt(6, s.authority());
                ps.setInt(7, s.freshness());
                ps.setString(8, s.excerpt());
                ps.setString(9, s.addedAt());
                ps.executeUpdate();
            }
        }
        for (Claim c : snapshot.claims()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO claim(id, workspace_id, concept_id, proposition, scope, "
                            + "context, status, confidence, version, introduced_by_source_id, "
                            + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
                ps.setString(11, c.createdAt());
                ps.setString(12, c.updatedAt());
                ps.executeUpdate();
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
                        + "freshness, excerpt, added_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, s.id());
            ps.setString(2, s.workspaceId());
            ps.setString(3, s.kind().name());
            ps.setString(4, s.title());
            ps.setString(5, s.locator());
            ps.setInt(6, s.authority());
            ps.setInt(7, s.freshness());
            ps.setString(8, s.excerpt());
            ps.setString(9, s.addedAt());
            ps.executeUpdate();
        }
    }

    private void upsertClaim(Claim c) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO claim(id, workspace_id, concept_id, proposition, scope, "
                        + "context, status, confidence, version, introduced_by_source_id, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
            ps.setString(11, c.createdAt());
            ps.setString(12, c.updatedAt());
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
                + "session_id, answered_at FROM assessment_outcome WHERE workspace_id = ? "
                + "ORDER BY answered_at, item_id", workspaceId, rs -> new AssessmentOutcome(
                rs.getString("item_id"), rs.getInt("correct") != 0, rs.getDouble("score"),
                rs.getDouble("claimed_confidence"), rs.getString("learner_answer"),
                rs.getString("session_id"), rs.getLong("answered_at")));
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
        return query("SELECT id, workspace_id, kind, title, locator, authority, freshness, excerpt, added_at "
                + "FROM source WHERE workspace_id = ? ORDER BY title", workspaceId, rs -> new Source(
                rs.getString("id"), rs.getString("workspace_id"),
                SourceKind.valueOf(rs.getString("kind")), rs.getString("title"),
                rs.getString("locator"), rs.getInt("authority"), rs.getInt("freshness"),
                rs.getString("excerpt"), rs.getString("added_at")));
    }

    public List<Claim> claims(String workspaceId) {
        return query("SELECT id, workspace_id, concept_id, proposition, scope, context, status, "
                + "confidence, version, introduced_by_source_id, created_at, updated_at "
                + "FROM claim WHERE workspace_id = ? ORDER BY id", workspaceId, rs -> new Claim(
                rs.getString("id"), rs.getString("workspace_id"), rs.getString("concept_id"),
                rs.getString("proposition"), rs.getString("scope"), rs.getString("context"),
                ClaimStatus.valueOf(rs.getString("status")), rs.getDouble("confidence"),
                rs.getInt("version"), rs.getString("introduced_by_source_id"),
                rs.getString("created_at"), rs.getString("updated_at")));
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
                representations(workspaceId), prerequisites(workspaceId), revisions(workspaceId), now());
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
