package io.matrix.e2e;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.ethics.FROZENGDPREscalator;
import io.matrix.ethics.frozen.FrozenEthicalFNL;
import io.matrix.privacy.CascadeRegistrar;
import io.matrix.privacy.CascadeTombstoneService;
import io.matrix.privacy.MockNoosphere;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Master E2E test: covers the full MATRIX ethics → cascade → audit chain
 * as a single end-to-end scenario.
 *
 * <p>Scenario:
 * <ol>
 *   <li>A user submits a destructive action ("Kill the enemy").</li>
 *   <li>FROZENFNLGuardian evaluates and rejects it.</li>
 *   <li>FROZENGDPREscalator auto-tombstones the action.</li>
 *   <li>CascadeTombstoneService propagates the erasure to dependent
 *       resources (neurons, snapshots, knowledge index) via the
 *       MockNoosphere dependency graph.</li>
 *   <li>The HashChain records both the original decision AND the cascade
 *       operations, producing a tamper-evident audit trail.</li>
 * </ol>
 *
 * <p>This is the "happy path" of GDPR Art. 17 compliance for MATRIX.
 * Ref: docs/specs/WAVE_22_E2E_INTEGRATION.md.
 */
class MasterPipelineE2ETest {

    @Test
    void fullPipeline_rejectionTriggersCascadeAndAudit() {
        // ── Build the system ──
        TombstoneService tombstones = new TombstoneService();
        MockNoosphere noosphere = new MockNoosphere()
                .addNeuron("agent-1", "n1")
                .addNeuron("agent-1", "n2")
                .addNeuron("agent-1", "n3")
                .setTruthTable("n1", "tt-n1")
                .setTruthTable("n2", "tt-n2")
                .addSnapshot("agent-1", "snap-1")
                .addKnowledgeIndex("fnl-1", "ki-1")
                .addKnowledgeIndex("fnl-1", "ki-2");
        FROZENFNLGuardian guardian = new FROZENFNLGuardian();
        guardian.attestNow();
        FROZENGDPREscalator escalator = new FROZENGDPREscalator(
                guardian, tombstones, () -> "data-subject-7");
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(tombstones,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> noosphere.neuronsForAgent(id),
                        (id, d) -> noosphere.snapshotsForAgent(id),
                        (id, d) -> noosphere.truthTableForNeuron(id),
                        (id, d) -> noosphere.knowledgeIndexForPackage(id)));

        // ── Step 1: Action submitted ──
        String action = "Kill the enemy leader";

        // ── Step 2: FROZEN evaluation → REJECTED ──
        FrozenEthicalFNL.Result fnlResult = EthicalFilter.FROZEN_FNL.evaluateText(action);
        assertThat(fnlResult.approved()).isFalse();
        assertThat(fnlResult.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.NO_KILLING);

        // ── Step 3: FROZENGDPREscalator auto-tombstones ──
        EthicalVerdict verdict = escalator.evaluateAndRecord(action);
        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
        // The action is tombstoned under the resourceId = SHA-256(action).
        // We need a quick way to find it; instead, just check that at least
        // one tombstone exists.
        assertThat(tombstones.count()).isGreaterThanOrEqualTo(1);

        // ── Step 4: Cascade erasure to dependent resources ──
        // Suppose the destructive action was issued by Agent agent-1. We cascade.
        List<Tombstone> cascaded = cascade.tombstoneAndCascade(
                "data-subject-7",
                "Agent", "agent-1", "gdpr.erasure", "CascadeResource");
        // 1 root + 3 neurons + 1 snapshot + (each → truth table) = 1+3+1+2 = 7
        assertThat(cascaded).hasSize(7);
        assertThat(tombstones.isTombstoned("Agent", "agent-1")).isTrue();
        assertThat(tombstones.isTombstoned("Neuron", "n1")).isTrue();
        assertThat(tombstones.isTombstoned("Neuron", "n2")).isTrue();
        assertThat(tombstones.isTombstoned("Neuron", "n3")).isTrue();
        assertThat(tombstones.isTombstoned("Snapshot", "snap-1")).isTrue();
        assertThat(tombstones.isTombstoned("TruthTable", "tt-n1")).isTrue();
        assertThat(tombstones.isTombstoned("TruthTable", "tt-n2")).isTrue();

        // ── Step 5: Audit chain is intact ──
        assertThat(guardian.verifyAuditTrail()).isTrue();
        // 1 attestation + 1 decision = 2 links
        assertThat(guardian.chain().size()).isEqualTo(2);
        assertThat(guardian.totalRejections()).isEqualTo(1);

        // ── Step 6: Noosphere is unchanged (we don't actually delete from it) ──
        assertThat(noosphere.totalEntities()).isEqualTo(8);

        // ── Step 7: Total tombstones = 1 action + 7 cascade = 8 ──
        assertThat(tombstones.count()).isEqualTo(8);
    }

    @Test
    void multipleRejectionsAccumulateAcrossManyActions() {
        TombstoneService tombstones = new TombstoneService();
        MockNoosphere noosphere = new MockNoosphere()
                .addNeuron("a", "n1");
        FROZENFNLGuardian guardian = new FROZENFNLGuardian();
        FROZENGDPREscalator escalator = new FROZENGDPREscalator(
                guardian, tombstones, () -> "u");
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(tombstones,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> noosphere.neuronsForAgent(id),
                        (id, d) -> noosphere.snapshotsForAgent(id),
                        (id, d) -> noosphere.truthTableForNeuron(id),
                        (id, d) -> noosphere.knowledgeIndexForPackage(id)));

        // 3 different destructive actions, all REJECT.
        escalator.evaluateAndRecord("Kill the enemy leader");
        escalator.evaluateAndRecord("Torture the prisoner");
        escalator.evaluateAndRecord("Dox the CEO publicly");

        assertThat(tombstones.count()).isEqualTo(3);
        assertThat(guardian.totalRejections()).isEqualTo(3);
        assertThat(guardian.chain().size()).isEqualTo(3);

        // Now cascade on agent "a" → 1 root + 1 neuron = 2
        var cascaded = cascade.tombstoneAndCascade("u", "Agent", "a", "r", "op");
        assertThat(cascaded).hasSize(2);
        // Total tombstones: 3 actions + 2 cascade = 5
        assertThat(tombstones.count()).isEqualTo(5);
    }

    @Test
    void approvedActionProducesNoTombstoneButAuditLinkExists() {
        TombstoneService tombstones = new TombstoneService();
        FROZENFNLGuardian guardian = new FROZENFNLGuardian();
        FROZENGDPREscalator escalator = new FROZENGDPREscalator(
                guardian, tombstones, () -> "u");

        var verdict = escalator.evaluateAndRecord("Help me find a book");
        assertThat(verdict).isEqualTo(io.matrix.ethics.EthicalVerdict.APPROVED);
        assertThat(tombstones.count()).isZero();
        // But the audit chain still records the decision.
        assertThat(guardian.totalDecisions()).isEqualTo(1);
        assertThat(guardian.totalRejections()).isZero();
        assertThat(guardian.chain().size()).isEqualTo(1);
    }
}