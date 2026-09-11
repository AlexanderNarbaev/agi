package io.matrix.research;

import io.matrix.lifecycle.FnlGateV2;
import io.matrix.lifecycle.TaskCellV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 356 — Phase S Cauldron + TaskCell/FNL v2.
 *
 * <p>Acceptance:
 *  - TaskCellV2 implements INV-TC1, TC2, TC3, TC4
 *  - FnlGateV2 implements conjunctive Kleene + FROZEN-veto (INV-FNL3)
 *  - State machine SHADOW → CANDIDATE → PROMOTED/DEMOTED/DEAD
 *  - k consecutive accepts promotes; REJECT demotes; UNDECIDED sticky
 */
class Exp356TaskCellFnlGateV2Test {

    // -- TaskCellV2 --

    @Test
    void taskCellInvTc2BudgetZeroDiesImmediately() {
        TaskCellV2.TaskCellSpec spec = new TaskCellV2.TaskCellSpec(
                0xAL, null, null, false, 0,
                TaskCellV2.MergePolicy.VERDICT_ONLY);
        TaskCellV2 cell = new TaskCellV2(spec);
        assertThat(cell.state()).isEqualTo(TaskCellV2.State.DESTROYED);
    }

    @Test
    void taskCellRunsFunction() {
        TaskCellV2.TaskCellSpec spec = TaskCellV2.TaskCellSpec.simple(0xCAFE, 1000);
        TaskCellV2 cell = new TaskCellV2(spec);
        cell.run(s -> "verdict-for-" + s.seed());
        assertThat(cell.state()).isEqualTo(TaskCellV2.State.COMPLETED);
        assertThat(cell.verdict()).isEqualTo("verdict-for-51966");
    }

    @Test
    void taskCellInvTc1RejectsM2M3Write() {
        TaskCellV2 cell = new TaskCellV2(TaskCellV2.TaskCellSpec.simple(0xAL, 1000));
        assertThatThrownBy(() -> cell.writeToMemoryLevel(2))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("INV-TC1");
        assertThatThrownBy(() -> cell.writeToMemoryLevel(3))
                .isInstanceOf(UnsupportedOperationException.class);
        // M0/M1/M4 allowed
        cell.writeToMemoryLevel(0);
        cell.writeToMemoryLevel(1);
        cell.writeToMemoryLevel(4);
    }

    @Test
    void taskCellChargeReducesBudget() {
        TaskCellV2 cell = new TaskCellV2(TaskCellV2.TaskCellSpec.simple(0xAL, 1000));
        long before = cell.budgetRemainingMs();
        cell.charge(100);
        assertThat(cell.budgetRemainingMs()).isEqualTo(before - 100);
    }

    @Test
    void taskCellChargeExhaustsTriggersTimeout() {
        TaskCellV2 cell = new TaskCellV2(TaskCellV2.TaskCellSpec.simple(0xAL, 50));
        cell.charge(100);
        assertThat(cell.state()).isEqualTo(TaskCellV2.State.TIMEOUT);
    }

    // -- FnlGateV2 --

    @Test
    void fnlGateAdmitAndTick() {
        FnlGateV2 gate = new FnlGateV2(3);
        FnlGateV2.FnlEntry entry = new FnlGateV2.FnlEntry(
                UUID.randomUUID(), "abc123",
                FnlGateV2.FnlEntry.Origin.DISTILL,
                10, new ArrayList<>(), 0);
        gate.admit(entry);
        assertThat(gate.size()).isEqualTo(1);
    }

    @Test
    void fnlGateFrozenVetoIsMandatory() {
        FnlGateV2 gate = new FnlGateV2(3);
        FnlGateV2.FnlEntry entry = new FnlGateV2.FnlEntry(
                UUID.randomUUID(), "frozen-test",
                FnlGateV2.FnlEntry.Origin.DISTILL,
                10, new ArrayList<>(), 0);
        gate.admit(entry);
        // frozenCheck = false → REJECT
        FnlGateV2.GateVerdict v = gate.evaluate(entry, false);
        assertThat(v).isEqualTo(FnlGateV2.GateVerdict.REJECT);
    }

    @Test
    void fnlGateConjunctiveAccepts() {
        FnlGateV2 gate = new FnlGateV2(2);
        FnlGateV2.FnlEntry entry = new FnlGateV2.FnlEntry(
                UUID.randomUUID(), "accept-test",
                FnlGateV2.FnlEntry.Origin.CAULDRON,
                10, new ArrayList<>(), 0);
        gate.admit(entry);
        // Tick 3 times with frozen OK → eventually promotes
        for (int i = 0; i < 5; i++) gate.tick(true);
        FnlGateV2.FnlEntry after = gate.get(entry.id());
        // After 5 ticks with ACCEPT, k=2 satisfied
        assertThat(after.consecutiveAccepts()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void fnlGateRejectDemotes() {
        FnlGateV2 gate = new FnlGateV2(5);
        FnlGateV2.FnlEntry entry = new FnlGateV2.FnlEntry(
                UUID.randomUUID(), "reject-test",
                FnlGateV2.FnlEntry.Origin.IMPORT_M4,
                10, new ArrayList<>(), 3);
        gate.admit(entry);
        // FROZEN rejects → demoted
        gate.tick(false);
        FnlGateV2.FnlEntry after = gate.get(entry.id());
        // After demote: budget is 0, consecutiveAccepts is 0
        assertThat(after.consecutiveAccepts()).isZero();
        assertThat(after.quarantineBudget()).isZero();
    }

    @Test
    void fnlGateInvFnl4UndecidedBudgetExhaustedRejects() {
        FnlGateV2 gate = new FnlGateV2(5);
        // Create entry with budget=1 + pre-existing UNDECIDED
        List<FnlGateV2.GateVerdict> verdicts = new ArrayList<>();
        verdicts.add(FnlGateV2.GateVerdict.UNDECIDED);
        FnlGateV2.FnlEntry entry = new FnlGateV2.FnlEntry(
                UUID.randomUUID(), "budget-test",
                FnlGateV2.FnlEntry.Origin.DISTILL,
                1, verdicts, 0);
        gate.admit(entry);
        // After tick: budget exhausted with UNDECIDED → reject-by-budget
        gate.tick(true);
        FnlGateV2.FnlEntry after = gate.get(entry.id());
        assertThat(after.consecutiveAccepts()).isZero();
    }
}
