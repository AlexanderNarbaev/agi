package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W48-07 — the snapshot payload has a bound, and exceeding it is an explicit refusal.
 *
 * <p>The owner's decision OD-039: the payload gets a SIZE BOUND, and the refusal is explicit —
 * the snapshot is not written and the caller is told. <b>Truncation is never the policy</b>: a
 * snapshot is the only recoverable copy of what a replacement destroyed, and a shortened one is
 * a corrupt one that restores a workspace with rows missing and says nothing.</p>
 *
 * <p>These tests are the proof of the three promises:</p>
 * <ul>
 *   <li>a payload over the bound is REFUSED, with a typed error that names what exceeded which
 *       bound;</li>
 *   <li>the refusal leaves the database EXACTLY as it was — no snapshot row, no erasure, no
 *       half-written anything;</li>
 *   <li>the bound is CONFIGURED, and a bound that cannot be read is refused rather than
 *       ignored.</li>
 * </ul>
 */
class SnapshotRetentionBoundTest {

    private static final String INSTANT = "2026-11-06T00:00:00Z";
    private static final String WORKSPACE = "demo";

    /** A bound far below any real payload: the fixture's payload is several kilobytes. */
    private static final long TINY_BOUND = 512L;

    @TempDir
    Path directory;

    // ── the refusal ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a payload over the bound is refused with a typed error")
    void aPayloadOverTheBoundIsRefusedWithATypedError() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"),
                TINY_BOUND)) {
            store.importWorkspace(demoSnapshot());

            assertThatThrownBy(() -> store.takeWorkspaceSnapshot(WORKSPACE,
                    WorkspaceSnapshot.Kind.MANUAL, INSTANT))
                    .as("the snapshot must be refused, not written")
                    .isInstanceOf(KnowledgeStore.SnapshotPayloadTooLargeException.class)
                    .hasMessageContaining(KnowledgeStore.SnapshotPayloadTooLargeException.IDENTIFIER)
                    .hasMessageContaining(WORKSPACE)
                    .hasMessageContaining("bound");
        }
    }

    @Test
    @DisplayName("the refusal says what exceeded which bound")
    void theRefusalSaysWhatExceededWhichBound() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"),
                TINY_BOUND)) {
            store.importWorkspace(demoSnapshot());

            assertThatThrownBy(() -> store.takeWorkspaceSnapshot(WORKSPACE,
                    WorkspaceSnapshot.Kind.MANUAL, INSTANT))
                    .isInstanceOfSatisfying(KnowledgeStore.SnapshotPayloadTooLargeException.class,
                            refusal -> {
                                assertThat(refusal.workspaceId()).isEqualTo(WORKSPACE);
                                assertThat(refusal.maxPayloadBytes()).isEqualTo(TINY_BOUND);
                                assertThat(refusal.payloadBytes())
                                        .as("the refusal carries the FULL size, not a trimmed one")
                                        .isGreaterThan(TINY_BOUND);
                                assertThat(refusal.getMessage())
                                        .contains(Long.toString(refusal.payloadBytes()))
                                        .contains(Long.toString(TINY_BOUND))
                                        .contains(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY);
                            });
        }
    }

    @Test
    @DisplayName("a refused snapshot leaves the database exactly as it was")
    void aRefusedSnapshotLeavesTheDatabaseExactlyAsItWas() throws Exception {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"),
                TINY_BOUND)) {
            store.importWorkspace(demoSnapshot());
            String before = fingerprint();

            assertThatThrownBy(() -> store.takeWorkspaceSnapshot(WORKSPACE,
                    WorkspaceSnapshot.Kind.MANUAL, INSTANT))
                    .isInstanceOf(KnowledgeStore.SnapshotPayloadTooLargeException.class);

            assertThat(fingerprint())
                    .as("the refusal must not write a row, and must not write a SHORT one")
                    .isEqualTo(before);
            assertThat(snapshotRows()).isZero();
        }
    }

    @Test
    @DisplayName("a payload under the bound is written")
    void aPayloadUnderTheBoundIsWritten() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());

            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE,
                    WorkspaceSnapshot.Kind.MANUAL, INSTANT);

            assertThat(taken).isNotNull();
            assertThat(taken.coverage()).isEqualTo(SnapshotCoverage.FULL);
            assertThat(snapshotRows()).isEqualTo(1);
        }
    }

    // ── the bound is a refusal, never a trim ─────────────────────────────────

    @Test
    @DisplayName("a refused replacement destroys nothing")
    void aRefusedReplacementDestroysNothing() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"),
                TINY_BOUND)) {
            store.importWorkspace(demoSnapshot());

            assertThatThrownBy(() -> store.importWorkspaceWithSnapshot(demoSnapshot(), true,
                    INSTANT))
                    .as("the replacement must be refused before the erasure")
                    .isInstanceOf(KnowledgeStore.SnapshotPayloadTooLargeException.class);

            assertThat(store.exportWorkspace(WORKSPACE).concepts()).hasSize(1);
            assertThat(store.exportWorkspace(WORKSPACE).claims()).hasSize(1);
            assertThat(store.exportWorkspace(WORKSPACE).evidence()).hasSize(1);
            assertThat(snapshotRows())
                    .as("no snapshot row, not even a shortened one")
                    .isZero();
        }
    }

    @Test
    @DisplayName("a refused restore leaves the database exactly as it was")
    void aRefusedRestoreLeavesTheDatabaseExactlyAsItWas() throws Exception {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE,
                    WorkspaceSnapshot.Kind.MANUAL, INSTANT);
            String before = fingerprint();

            try (KnowledgeStore bounded = new KnowledgeStore(directory.resolve("knowledge.sqlite"),
                    TINY_BOUND)) {
                assertThatThrownBy(() -> bounded.restoreWorkspaceSnapshot(taken.id(), INSTANT))
                        .as("the restore takes a snapshot of what it replaces, and that "
                                + "snapshot is over the bound")
                        .isInstanceOf(KnowledgeStore.SnapshotPayloadTooLargeException.class);
            }

            assertThat(fingerprint()).isEqualTo(before);
        }
    }

    // ── the bound is configured ──────────────────────────────────────────────

    @Test
    @DisplayName("the bound is configurable through the system property")
    void theBoundIsConfigurableThroughTheSystemProperty() {
        String previous = System.getProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY);
        System.setProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY,
                Long.toString(TINY_BOUND));
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());

            assertThatThrownBy(() -> store.takeWorkspaceSnapshot(WORKSPACE,
                    WorkspaceSnapshot.Kind.MANUAL, INSTANT))
                    .as("the property must be the bound the store uses")
                    .isInstanceOf(KnowledgeStore.SnapshotPayloadTooLargeException.class);
        } finally {
            if (previous == null) {
                System.clearProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY);
            } else {
                System.setProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("the default bound is the documented one")
    void theDefaultBoundIsTheDocumentedOne() {
        assertThat(KnowledgeStore.DEFAULT_MAX_SNAPSHOT_PAYLOAD_BYTES)
                .as("8 MiB — 81 times the payload the owner's own workspace produces")
                .isEqualTo(8L * 1024L * 1024L);
    }

    @Test
    @DisplayName("a bound that is not a number is refused at start-up")
    void aBoundThatIsNotANumberIsRefusedAtStartUp() {
        String previous = System.getProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY);
        System.setProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY, "eight megabytes");
        try {
            assertThatThrownBy(() -> new KnowledgeStore(directory.resolve("knowledge.sqlite")))
                    .as("a bound that cannot be read is a bound that cannot be trusted")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY);
        } finally {
            if (previous == null) {
                System.clearProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY);
            } else {
                System.setProperty(KnowledgeStore.SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("a bound that is not positive is refused")
    void aBoundThatIsNotPositiveIsRefused() {
        assertThatThrownBy(() -> new KnowledgeStore(directory.resolve("knowledge.sqlite"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeStore(directory.resolve("knowledge.sqlite"), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── the invariant ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a replacement leaves a complete recoverable snapshot")
    void aReplacementLeavesACompleteRecoverableSnapshot() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());

            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(demoSnapshot(), true,
                    INSTANT);

            assertThat(taken).isNotNull();
            assertThat(taken.coverage())
                    .as("no replacement is committed without a complete recoverable snapshot")
                    .isEqualTo(SnapshotCoverage.FULL);
            KnowledgeStore.requireCompleteSnapshot(taken);
        }
    }

    @Test
    @DisplayName("a snapshot that is not complete is refused before the erasure")
    void aSnapshotThatIsNotCompleteIsRefusedBeforeTheErasure() {
        WorkspaceSnapshot incomplete = new WorkspaceSnapshot("snap-demo-1", WORKSPACE, INSTANT,
                WorkspaceSnapshot.Kind.LOAD, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0,
                SnapshotCoverage.KNOWLEDGE_ONLY, java.util.Map.of());

        assertThatThrownBy(() -> KnowledgeStore.requireCompleteSnapshot(incomplete))
                .isInstanceOf(KnowledgeStore.IncompleteSnapshotException.class)
                .hasMessageContaining(KnowledgeStore.IncompleteSnapshotException.IDENTIFIER)
                .hasMessageContaining("the learning state");
    }

    // ── the fixture ──────────────────────────────────────────────────────────

    private static KnowledgeSnapshot demoSnapshot() {
        String conceptId = "cpt-" + WORKSPACE;
        String sourceId = "src-" + WORKSPACE;
        String claimId = "clm-" + WORKSPACE;
        String evidenceId = "evd-" + WORKSPACE;
        String representationId = "rpr-" + WORKSPACE;
        return new KnowledgeSnapshot(WORKSPACE, "Demo workspace",
                List.of(new Concept(conceptId, WORKSPACE, "Photosynthesis",
                        "Conversion of light energy into chemical energy.", "biology", INSTANT)),
                List.of(new Source(sourceId, WORKSPACE, SourceKind.AUTHORITATIVE_REFERENCE,
                        "A textbook", "urn:isbn:978-0-00", 90, 70, "Plants photosynthesise.",
                        INSTANT, "hash-of-the-textbook-excerpt", null)),
                List.of(new Claim(claimId, WORKSPACE, conceptId,
                        "Plants use light to make sugars.", "In green plants", "general biology",
                        ClaimStatus.VERIFIED, 0.95, 1, sourceId, "transcribed from the excerpt",
                        "checked against the excerpt", ClaimWarrant.TESTIMONY,
                        DerivationStatus.NOT_DERIVED, INSTANT, INSTANT)),
                List.of(new Evidence(evidenceId, WORKSPACE, claimId, sourceId,
                        EvidenceRelation.SUPPORTS, "Plants photosynthesise.", 90, INSTANT)),
                List.of(new Representation(representationId, WORKSPACE, conceptId,
                        AbstractionLevel.INTRODUCTORY, "beginner",
                        "Plants make sugars from light, water and CO2.", "introductory",
                        List.of("plants photosynthesise"), List.of("light energy is converted"),
                        List.of("the Calvin cycle"), List.of("only in green plants"), List.of(),
                        null, 0.9, INSTANT)),
                List.of(), List.of(), List.of(), List.of(), List.of(), INSTANT);
    }

    // ── the helpers ──────────────────────────────────────────────────────────

    /** How many snapshot rows the database holds. */
    private int snapshotRows() {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("knowledge.sqlite"));
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM workspace_snapshot")) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A fingerprint of the whole database: every table, every row, in a stable order.
     *
     * <p>It is what proves that a refusal changed nothing — not "the counts are the same",
     * but the bytes of every row of every table.</p>
     */
    private String fingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("knowledge.sqlite"));
             Statement statement = connection.createStatement()) {
            var tables = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
            while (tables.next()) {
                String table = tables.getString(1);
                fingerprint.append("== ").append(table).append('\n');
                var rows = statement.executeQuery("SELECT * FROM " + table);
                int columns = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    for (int column = 1; column <= columns; column++) {
                        fingerprint.append(rows.getMetaData().getColumnLabel(column)).append('=')
                                .append(rows.getObject(column)).append('|');
                    }
                    fingerprint.append('\n');
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return fingerprint.toString();
    }
}
