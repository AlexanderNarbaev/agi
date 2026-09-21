package io.matrix.lifecycle;

import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-J tests for {@link ImpulseScheduler}: every impulse passes the
 * FROZEN-ethics gate, respects the budget, and is reported faithfully.
 */
class ImpulseSchedulerTest {

    @Test
    void allFourImpulseTypesCanFireWhenBudgetAndEthicsAllow() {
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        EthicalFilter ethics = new EthicalFilter(); // default-permissive
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, ethics);

        long envelope = 1_000_000L;
        for (AutonomyImpulse imp : AutonomyImpulse.values()) {
            ImpulseScheduler.FireRecord r =
                    scheduler.fire(imp, envelope, Map.of());
            assertThat(r.outcome())
                    .as("impulse %s must fire under large envelope", imp)
                    .isEqualTo(ImpulseScheduler.ImpulseOutcome.FIRED);
            assertThat(r.envelopeSpent()).isGreaterThan(0);
        }
        assertThat(scheduler.totalFires()).isEqualTo(4L);
    }

    @Test
    void smallEnvelopeRejectsMostImpulsesAsOverBudget() {
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, new EthicalFilter());

        long smallEnvelope = 1L;
        for (AutonomyImpulse imp : AutonomyImpulse.values()) {
            ImpulseScheduler.FireRecord r =
                    scheduler.fire(imp, smallEnvelope, Map.of());
            assertThat(r.outcome())
                    .as("impulse %s must be rejected under tiny envelope", imp)
                    .isEqualTo(ImpulseScheduler.ImpulseOutcome.REJECTED_BUDGET);
        }
        assertThat(scheduler.totalRejections()).isEqualTo(4L);
    }

    @Test
    void curiosityClaimingMoreThanEnvelopeIsRejected() {
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, new EthicalFilter());
        // CURIOSITY cost = envelope / 4; if envelope < 4, cost > envelope
        ImpulseScheduler.FireRecord r =
                scheduler.fire(AutonomyImpulse.CURIOSITY, 3L, Map.of());
        assertThat(r.outcome()).isEqualTo(ImpulseScheduler.ImpulseOutcome.REJECTED_BUDGET);
    }

    @Test
    void countersAdvanceIndependentlyOfOutcomes() {
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, new EthicalFilter());

        scheduler.fire(AutonomyImpulse.CURIOSITY, 1_000L, Map.of());
        scheduler.fire(AutonomyImpulse.SHARE_DIGEST, 1L, Map.of()); // rejected
        scheduler.fire(AutonomyImpulse.INTEGRITY_CHECK, 1_000L, Map.of());
        assertThat(scheduler.totalFires()).isEqualTo(3L);
        assertThat(scheduler.totalRejections()).isEqualTo(1L);
    }

    @Test
    void everyImpulseNameIsHandled() {
        // ensure all 4 enum values are mapped in costFor/valueFor — we
        // assert by firing each one with a generous envelope and
        // verifying the outcome is FIRED (not REJECTED_UNKNOWN).
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, new EthicalFilter());
        for (AutonomyImpulse imp : AutonomyImpulse.values()) {
            ImpulseScheduler.FireRecord r =
                    scheduler.fire(imp, 100_000L, Map.of());
            assertThat(r.outcome()).isNotEqualTo(
                    ImpulseScheduler.ImpulseOutcome.REJECTED_UNKNOWN);
        }
    }
}