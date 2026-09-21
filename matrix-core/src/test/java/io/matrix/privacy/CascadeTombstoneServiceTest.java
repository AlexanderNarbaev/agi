package io.matrix.privacy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeTombstoneServiceTest {

    @Test
    void simpleCascadeTombstonesDependents() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base);

        // When an Agent is tombstoned, also tombstone its 2 Neurons.
        cascade.register(CascadeTombstoneService.rule("Agent", "Neuron",
                (agentId, depth) -> List.of(agentId + "-neuron-a", agentId + "-neuron-b"),
                "cascade.from.agent"));

        var tombstones = cascade.tombstoneAndCascade(
                "user-1", "Agent", "agent-7", "gdpr.erasure", "op-1");

        // 1 root + 2 cascades = 3 total
        assertThat(tombstones).hasSize(3);
        assertThat(base.count()).isEqualTo(3);
        assertThat(base.isTombstoned("Agent", "agent-7")).isTrue();
        assertThat(base.isTombstoned("Neuron", "agent-7-neuron-a")).isTrue();
        assertThat(base.isTombstoned("Neuron", "agent-7-neuron-b")).isTrue();
    }

    @Test
    void cascadeChainsThroughMultipleLevels() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base, 5);

        // Agent → Neuron → Snapshot → TrainingData (3 levels)
        cascade.register(CascadeTombstoneService.rule("Agent", "Neuron",
                (id, d) -> List.of(id + "-n1"), "cascade.from.agent"));
        cascade.register(CascadeTombstoneService.rule("Neuron", "Snapshot",
                (id, d) -> List.of(id + "-snap"), "cascade.from.neuron"));
        cascade.register(CascadeTombstoneService.rule("Snapshot", "TrainingData",
                (id, d) -> List.of(id + "-data"), "cascade.from.snapshot"));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "agent-A", "gdpr.erasure", "op");

        // 1 + 1 + 1 + 1 = 4
        assertThat(tombstones).hasSize(4);
        assertThat(base.count()).isEqualTo(4);
        assertThat(cascade.cascadeCount()).isEqualTo(3);
    }

    @Test
    void cascadeRespectsMaxDepth() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base, 2);

        // Chain would go deeper than 2 but is bounded.
        cascade.register(CascadeTombstoneService.rule("A", "B",
                (id, d) -> List.of(id + "-b"), "from-a"));
        cascade.register(CascadeTombstoneService.rule("B", "C",
                (id, d) -> List.of(id + "-c"), "from-b"));
        cascade.register(CascadeTombstoneService.rule("C", "D",
                (id, d) -> List.of(id + "-d"), "from-c"));

        var tombstones = cascade.tombstoneAndCascade("u", "A", "a1", "r", "op");
        // A → B → C → (D skipped, depth >= 2)
        assertThat(tombstones).hasSize(3);
        assertThat(cascade.cascadeSkipped()).isEqualTo(1);
    }

    @Test
    void cascadeSkipsAlreadyTombstonedDependents() {
        TombstoneService base = new TombstoneService();
        // Pre-tombstone one dependent.
        base.tombstone("u", "Neuron", "agent-1-neuron-a", "pre-existing", "", "op");

        CascadeTombstoneService cascade = new CascadeTombstoneService(base);
        cascade.register(CascadeTombstoneService.rule("Agent", "Neuron",
                (id, d) -> List.of(id + "-neuron-a", id + "-neuron-b"),
                "cascade.from.agent"));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "agent-1", "gdpr.erasure", "op");
        // tombstones list contains: 1 root + 1 cascade (neuron-b). neuron-a was skipped
        // (already tombstoned). The pre-existing tombstone isn't in the result list
        // because the cascade didn't create it.
        assertThat(tombstones).hasSize(2);
        // Total in base: pre-existing (1) + root (1) + cascade (1) = 3
        assertThat(base.count()).isEqualTo(3);
        assertThat(base.isTombstoned("Neuron", "agent-1-neuron-a")).isTrue();
        assertThat(base.isTombstoned("Neuron", "agent-1-neuron-b")).isTrue();
    }

    @Test
    void noRulesMeansNoCascade() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base);
        // No rules registered.
        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "a", "r", "op");
        assertThat(tombstones).hasSize(1);  // just the root
    }

    @Test
    void multipleRulesTargetSameTypeCreateMultipleTombs() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base);
        cascade.register(CascadeTombstoneService.rule("Agent", "Snapshot",
                (id, d) -> List.of(id + "-snap1"), "rule-a"));
        cascade.register(CascadeTombstoneService.rule("Agent", "Snapshot",
                (id, d) -> List.of(id + "-snap2"), "rule-b"));

        var tombstones = cascade.tombstoneAndCascade("u", "Agent", "a", "r", "op");
        // 1 root + 2 cascade rules = 3
        assertThat(tombstones).hasSize(3);
    }

    @Test
    void ruleCountReflectsRegistrations() {
        var cascade = new CascadeTombstoneService(new TombstoneService());
        assertThat(cascade.ruleCount()).isZero();
        cascade.register(CascadeTombstoneService.rule("A", "B", (id, d) -> List.of(), "x"));
        assertThat(cascade.ruleCount()).isEqualTo(1);
        cascade.register(CascadeTombstoneService.rule("A", "C", (id, d) -> List.of(), "y"));
        assertThat(cascade.ruleCount()).isEqualTo(2);
    }

    @Test
    void cascadeReasonContainsParentReference() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base);
        cascade.register(CascadeTombstoneService.rule("Agent", "Neuron",
                (id, d) -> List.of(id + "-n"), "cascade.from.agent"));

        cascade.tombstoneAndCascade("u", "Agent", "agent-x", "gdpr.erasure", "op");

        var n = base.find("Neuron", "agent-x-n");
        assertThat(n).isNotNull();
        assertThat(n.reason()).startsWith("cascade.from.agent.Agent.agent-x");
    }
}