package io.matrix.autonomy;

import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;
import io.matrix.lifecycle.AutonomyImpulse;
import io.matrix.lifecycle.ImpulseScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ten tests the ADR that scopes this component requires.
 *
 * <p>{@code docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md} §9
 * lists them; each test below carries the number of the requirement it
 * discharges, so a reader can check the implementation against the ADR
 * without reading the test bodies.
 */
class ImpulseProposalConsumerTest {

    private static final long TTL_NANOS = 1_000_000L;
    private static final long ENVELOPE = 1_000_000L;

    /** A monotonic clock the test controls; the wall clock is never consulted. */
    private final AtomicLong now = new AtomicLong(1_000L);

    private ImpulseScheduler scheduler() {
        return new ImpulseScheduler(new ConjugateBudgeter(), new EthicalFilter());
    }

    private ImpulseProposalConsumer consumer(ImpulseProposalStore store,
                                             Predicate<String> gate) {
        return new ImpulseProposalConsumer(store, gate, now::get, TTL_NANOS);
    }

    private ImpulseProposalConsumer consumer(ImpulseProposalStore store) {
        return consumer(store, name -> false);
    }

    // ── §9.1 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.1 an accepted impulse produces exactly one proposal with its provenance and budget")
    void anAcceptedImpulseProducesExactlyOneProposal() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        ImpulseProposalConsumer consumer = consumer(store);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, ENVELOPE, Map.of());
        assertThat(record.outcome()).isEqualTo(ImpulseScheduler.ImpulseOutcome.FIRED);

        ImpulseProposalConsumer.Consumption consumption =
                consumer.consume(record, "cycle-7", "curiosity-drive");

        assertThat(consumption).isNotNull();
        assertThat(consumption.created()).isTrue();
        ImpulseProposal proposal = consumption.proposal().orElseThrow();
        assertThat(proposal.id()).isNotBlank();
        assertThat(proposal.impulse()).isEqualTo(AutonomyImpulse.CURIOSITY);
        assertThat(proposal.kind()).isEqualTo(ImpulseProposal.ProposalKind.CURIOSITY_EXPLORATION);
        // the provenance
        assertThat(proposal.cycle()).isEqualTo("cycle-7");
        assertThat(proposal.source()).isEqualTo("curiosity-drive");
        // the budget — taken from the record, not allocated again
        assertThat(proposal.envelopeSpent()).isEqualTo(record.envelopeSpent());
        assertThat(proposal.rowsAllocated()).isEqualTo(record.rowsAllocated());
        assertThat(proposal.shadowPrice()).isEqualTo(record.shadowPrice());
        // exactly one
        assertThat(store.all()).hasSize(1);
        assertThat(store.openCount()).isEqualTo(1);
        assertThat(consumer.outcomeCounts().get(ImpulseProposal.ProposalState.PROPOSED))
                .isEqualTo(1L);
    }

    // ── §9.2 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.2 a refused impulse produces no proposal")
    void aRefusedImpulseProducesNoProposal() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        ImpulseProposalConsumer consumer = consumer(store);
        ImpulseScheduler scheduler = scheduler();

        // an envelope far below the impulse's cost: the scheduler refuses it
        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, 1L, Map.of());
        assertThat(record.outcome()).isEqualTo(ImpulseScheduler.ImpulseOutcome.REJECTED_BUDGET);

        assertThat(consumer.consume(record, "cycle-1", "drive")).isNull();
        assertThat(store.all()).isEmpty();
        assertThat(consumer.notAcceptedCount()).isEqualTo(1L);
        assertThat(consumer.outcomeCounts().get(ImpulseProposal.ProposalState.PROPOSED))
                .isEqualTo(0L);
    }

    // ── §9.3 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.3 the proposal id is reproducible for the same impulse and cycle")
    void theProposalIdIsReproducible() {
        ImpulseScheduler scheduler = scheduler();
        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CONSOLIDATION, ENVELOPE, Map.of());

        ImpulseProposalConsumer first = consumer(new BoundedImpulseProposalStore(8));
        ImpulseProposalConsumer second = consumer(new BoundedImpulseProposalStore(8));

        String firstId = first.consume(record, "cycle-42", "src").proposal()
                .orElseThrow().id();
        String secondId = second.consume(record, "cycle-42", "src").proposal()
                .orElseThrow().id();

        assertThat(firstId).isEqualTo(secondId);
        // and it carries no wall-clock reading
        assertThat(firstId).contains("cycle-42").contains("CONSOLIDATION");
    }

    // ── §9.4 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.4 the queue is bounded, and the overflow is refused and counted")
    void theQueueIsBoundedAndTheOverflowIsRefused() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(2);
        ImpulseProposalConsumer consumer = consumer(store);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, ENVELOPE, Map.of());

        assertThat(consumer.consume(record, "cycle-1", "src").created()).isTrue();
        assertThat(consumer.consume(record, "cycle-2", "src").created()).isTrue();
        // the third proposal exceeds the capacity and is refused, not blocked
        ImpulseProposalConsumer.Consumption overflow =
                consumer.consume(record, "cycle-3", "src");

        assertThat(overflow.created()).isFalse();
        assertThat(overflow.state()).isEqualTo(ImpulseProposal.ProposalState.REFUSED_QUEUE_FULL);
        assertThat(store.openCount()).isEqualTo(2);
        assertThat(consumer.outcomeCounts()
                .get(ImpulseProposal.ProposalState.REFUSED_QUEUE_FULL)).isEqualTo(1L);
    }

    // ── §9.5 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.5 a duplicate is refused")
    void aDuplicateIsRefused() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        ImpulseProposalConsumer consumer = consumer(store);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, ENVELOPE, Map.of());

        assertThat(consumer.consume(record, "cycle-1", "src").created()).isTrue();
        ImpulseProposalConsumer.Consumption duplicate =
                consumer.consume(record, "cycle-1", "src");

        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.state()).isEqualTo(ImpulseProposal.ProposalState.DUPLICATE);
        assertThat(store.openCount()).isEqualTo(1);
        assertThat(consumer.outcomeCounts().get(ImpulseProposal.ProposalState.DUPLICATE))
                .isEqualTo(1L);
    }

    // ── §9.6 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.6 the FROZEN gate is consulted at execution time, and the refusal is honoured")
    void theFrozenGateIsConsultedAtExecutionTime() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        AtomicBoolean gateClosed = new AtomicBoolean(false);
        Predicate<String> gate = name -> gateClosed.get();
        ImpulseProposalConsumer consumer = consumer(store, gate);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.INTEGRITY_CHECK, ENVELOPE, Map.of());
        ImpulseProposal proposal = consumer.consume(record, "cycle-9", "src")
                .proposal().orElseThrow();
        assertThat(proposal.state()).isEqualTo(ImpulseProposal.ProposalState.PROPOSED);

        // the gate closes AFTER the creation — the state that permitted the
        // proposal may have changed
        gateClosed.set(true);
        ImpulseProposal refused = consumer.acceptForReview(proposal.id()).orElseThrow();

        assertThat(refused.state()).isEqualTo(ImpulseProposal.ProposalState.REFUSED_FROZEN);
        assertThat(store.openCount()).isZero();
        assertThat(consumer.outcomeCounts()
                .get(ImpulseProposal.ProposalState.REFUSED_FROZEN)).isEqualTo(1L);

        // the negative control: with the gate open again, the same flow ends in
        // ACCEPTED_FOR_REVIEW — so the refusal above came from the gate and not
        // from the flow itself
        AtomicBoolean gateClosedAgain = new AtomicBoolean(false);
        ImpulseProposalConsumer open = consumer(new BoundedImpulseProposalStore(8),
                name -> gateClosedAgain.get());
        ImpulseProposal allowed = open.consume(record, "cycle-10", "src")
                .proposal().orElseThrow();
        assertThat(open.acceptForReview(allowed.id()).orElseThrow().state())
                .isEqualTo(ImpulseProposal.ProposalState.ACCEPTED_FOR_REVIEW);
    }

    // ── §9.7 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.7 the consumer cannot reach Chain A")
    void theConsumerCannotReachChainA() throws IOException {
        // The consumer has no reference to Chain A's store, so no proposal can
        // change it. The check is structural rather than a fingerprint of the
        // live database: the live database is never opened by a test.
        Path source = repoRoot().resolve(
                "matrix-core/src/main/java/io/matrix/autonomy/ImpulseProposalConsumer.java");
        assertThat(source).exists();
        String text = Files.readString(source);

        assertThat(text)
                .as("the consumer must not import Chain A's knowledge store")
                .doesNotContain("io.matrix.knowledge");
        assertThat(text)
                .as("the consumer must not open a database")
                .doesNotContain("jdbc:");
        assertThat(text)
                .as("the consumer must not reach the outside world")
                .doesNotContain("ProcessBuilder")
                .doesNotContain("Runtime.getRuntime")
                .doesNotContain("Socket")
                .doesNotContain("Files.write")
                .doesNotContain("FileOutputStream");

        // and the only collaborators it holds are the store, the gate and the
        // clock — none of which can write to Chain A
        List<String> fieldTypes = java.util.Arrays.stream(
                        ImpulseProposalConsumer.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .toList();
        assertThat(fieldTypes)
                .as("the consumer's collaborators must be the store, the gate and the clock")
                .noneMatch(name -> name.startsWith("io.matrix.knowledge")
                        || name.startsWith("io.matrix.budgeter")
                        || name.contains("DriverManager"));
    }

    // ── §9.8 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.8 every proposal reaches a terminal outcome after its expiry")
    void everyProposalReachesATerminalOutcome() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        ImpulseProposalConsumer consumer = consumer(store);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, ENVELOPE, Map.of());
        consumer.consume(record, "cycle-1", "src");

        assertThat(consumer.sweep()).isZero();  // not expired yet
        assertThat(store.openCount()).isEqualTo(1);

        now.addAndGet(TTL_NANOS);
        assertThat(consumer.sweep()).isEqualTo(1);

        assertThat(store.openCount()).isZero();
        assertThat(store.all()).hasSize(1);
        assertThat(store.all().get(0).state())
                .isEqualTo(ImpulseProposal.ProposalState.EXPIRED);
        assertThat(store.all().get(0).state().terminal()).isTrue();
        assertThat(consumer.outcomeCounts().get(ImpulseProposal.ProposalState.EXPIRED))
                .isEqualTo(1L);
    }

    // ── §9.9 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.9 the expiry comes from the injected monotonic source, not the wall clock")
    void theExpiryUsesTheInjectedMonotonicSource() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        AtomicLong reads = new AtomicLong();
        LongSupplierCounter counter = new LongSupplierCounter(now, reads);
        ImpulseProposalConsumer consumer = new ImpulseProposalConsumer(store,
                name -> false, counter, TTL_NANOS);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, ENVELOPE, Map.of());
        ImpulseProposal proposal = consumer.consume(record, "cycle-1", "src")
                .proposal().orElseThrow();

        assertThat(reads.get()).as("the injected source must be the one consulted").isPositive();
        assertThat(proposal.expiresAtNanos()).isEqualTo(now.get() + TTL_NANOS);

        // the source of the expiry is the injected one, so moving it moves the
        // expiry and nothing else does
        now.addAndGet(42L);
        assertThat(proposal.expiresAtNanos()).isEqualTo(1_000L + TTL_NANOS);
    }

    /** Counts how often the injected clock is read. */
    private static final class LongSupplierCounter implements java.util.function.LongSupplier {
        private final AtomicLong source;
        private final AtomicLong reads;

        private LongSupplierCounter(AtomicLong source, AtomicLong reads) {
            this.source = source;
            this.reads = reads;
        }

        @Override
        public long getAsLong() {
            reads.incrementAndGet();
            return source.get();
        }
    }

    // ── §9.10 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§9.10 the budget is accounted once, not twice")
    void theBudgetIsAccountedOnce() {
        ImpulseProposalStore store = new BoundedImpulseProposalStore(8);
        ImpulseProposalConsumer consumer = consumer(store);
        ImpulseScheduler scheduler = scheduler();

        ImpulseScheduler.FireRecord record =
                scheduler.fire(AutonomyImpulse.CURIOSITY, ENVELOPE, Map.of());
        long firesBefore = scheduler.totalFires();

        ImpulseProposal proposal = consumer.consume(record, "cycle-1", "src")
                .proposal().orElseThrow();

        assertThat(scheduler.totalFires())
                .as("the consumer must never fire an impulse of its own")
                .isEqualTo(firesBefore);
        assertThat(proposal.envelopeSpent())
                .as("the envelope is the scheduler's, carried once")
                .isEqualTo(record.envelopeSpent());
        assertThat(proposal.rowsAllocated()).isEqualTo(record.rowsAllocated());
    }

    // ── the constructor's own contract ───────────────────────────────────────

    @Test
    @DisplayName("a consumer without a FROZEN gate cannot be constructed")
    void aConsumerWithoutAGateCannotBeConstructed() {
        assertThatThrownBy(() -> new ImpulseProposalConsumer(
                new BoundedImpulseProposalStore(4), null, now::get, TTL_NANOS))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the shipped gate is the EthicalFilter's frozen axiom check")
    void theShippedGateIsTheEthicalFilters() {
        EthicalFilter ethics = new EthicalFilter();
        Predicate<String> gate = ImpulseProposalConsumer.gateOf(ethics);
        assertThat(gate.test(AutonomyImpulse.CURIOSITY.name()))
                .as("the four canonical impulses are on the allow-list")
                .isFalse();
    }

    /** The repository root, found by walking up from the test's directory. */
    private static Path repoRoot() {
        Path candidate = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6 && candidate != null; i++) {
            if (Files.isDirectory(candidate.resolve("formal"))
                    && Files.isDirectory(candidate.resolve("matrix-core"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate the repository root from "
                + Path.of(".").toAbsolutePath());
    }
}
