package io.matrix.autonomy;

import io.matrix.lifecycle.AutonomyImpulse;
import io.matrix.lifecycle.ImpulseScheduler;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The consumer that turns an ACCEPTED autonomy impulse into a bounded internal
 * proposal.
 *
 * <p>This is the W49 implementation of
 * {@code docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md} §5, built
 * under the owner's decision OD-045 (option A). It is deliberately NOT an
 * executor:
 *
 * <ul>
 *   <li>it creates and records a typed internal proposal and nothing else;</li>
 *   <li>it sends no message, runs no command, touches no external service;</li>
 *   <li>it never writes outside the proposal store, and it has no reference to
 *       Chain A's store, so it cannot change an epistemic claim, a confidence, a
 *       learning goal or a safety rule;</li>
 *   <li>the scheduler's {@code FIRED} outcome still means <i>accepted</i> — it
 *       never means <i>executed</i>.</li>
 * </ul>
 *
 * <p>The safety invariants of the ADR's §6 hold here by construction:
 * S1 (only the store is written), S2 (no route to Chain A), S3 (the FROZEN
 * gate is consulted at creation AND at execution), S4 (the envelope is taken
 * from the {@link ImpulseScheduler.FireRecord}; the consumer never allocates),
 * S5 (the queue is bounded and the overflow is refused), S6 (every proposal
 * reaches a terminal outcome — the sweep closes the expired ones), S7 (the
 * expiry comes from an injected monotonic source, never the wall clock) and S8
 * (the proposal id is derived from the impulse, the cycle and a counter).
 */
public final class ImpulseProposalConsumer {

    /**
     * The result of one consumption attempt.
     *
     * @param state    the state the attempt ended in — a proposal state, and
     *                 for a refused attempt the reason it was refused
     * @param proposal the recorded proposal, or empty when the attempt was
     *                 refused; {@link #PROPOSED} always carries one
     */
    public record Consumption(ImpulseProposal.ProposalState state,
                              Optional<ImpulseProposal> proposal) {
        public Consumption {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(proposal, "proposal");
        }

        /** Whether a proposal was actually recorded. */
        public boolean created() {
            return state == ImpulseProposal.ProposalState.PROPOSED
                    && proposal.isPresent();
        }
    }

    private final ImpulseProposalStore store;
    private final Predicate<String> frozenGate;
    private final LongSupplier nanoClock;
    private final long ttlNanos;
    private final Map<String, Long> sequences = new HashMap<>();
    private final Map<ImpulseProposal.ProposalState, LongAdder> outcomes =
            new EnumMap<>(ImpulseProposal.ProposalState.class);
    private final LongAdder notAccepted = new LongAdder();

    /**
     * @param store     the store the proposals are recorded in — the only thing
     *                  this consumer writes
     * @param frozenGate the FROZEN ethical gate: it answers "does this action
     *                  name violate a frozen axiom?". It is consulted at
     *                  creation and again at execution time. The shipped
     *                  wiring passes {@link #gateOf(io.matrix.ethics.EthicalFilter)};
     *                  a {@code null} gate is refused at construction, because
     *                  a consumer without a gate would be a consumer that
     *                  cannot refuse.
     * @param nanoClock the monotonic time source in nanoseconds; the expiry is
     *                  measured with it and never with the wall clock
     * @param ttlNanos  how long a proposal stays open, in nanoseconds; must be
     *                  positive
     */
    public ImpulseProposalConsumer(ImpulseProposalStore store,
                                    Predicate<String> frozenGate,
                                    LongSupplier nanoClock,
                                    long ttlNanos) {
        this.store = Objects.requireNonNull(store, "store");
        this.frozenGate = Objects.requireNonNull(frozenGate, "frozenGate");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        if (ttlNanos <= 0) {
            throw new IllegalArgumentException("ttlNanos must be positive");
        }
        this.ttlNanos = ttlNanos;
        for (ImpulseProposal.ProposalState state : ImpulseProposal.ProposalState.values()) {
            outcomes.put(state, new LongAdder());
        }
    }

    /**
     * Consume one fire record.
     *
     * <p>A record the scheduler did not accept ({@code REJECTED_BUDGET},
     * {@code REJECTED_ETHICS}, {@code REJECTED_UNKNOWN}) is <b>not</b> a
     * proposal and produces none — the refusal reason stays observable in the
     * scheduler's own counters.
     *
     * @param record the scheduler's fire record
     * @param cycle  the cycle that fired the impulse
     * @param source the source of that cycle
     * @return the recorded proposal, or empty when nothing was proposed
     */
    public Consumption consume(ImpulseScheduler.FireRecord record, String cycle, String source) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(source, "source");

        if (record.outcome() != ImpulseScheduler.ImpulseOutcome.FIRED) {
            notAccepted.increment();
            return null;
        }
        // The scheduler accepted the impulse but allocated nothing: there is no
        // work to propose, and the budget was not spent on anything.
        if (record.rowsAllocated() <= 0) {
            return refuse(ImpulseProposal.ProposalState.REFUSED_BUDGET);
        }
        // S3, first half: the FROZEN gate is consulted at CREATION time too.
        if (violatesFrozenGate(record.impulse())) {
            return refuse(ImpulseProposal.ProposalState.REFUSED_FROZEN);
        }

        ImpulseProposal.ProposalKind kind = ImpulseProposal.ProposalKind.of(record.impulse());
        String dedupKey = record.impulse().name() + '|' + cycle + '|' + kind.name();
        if (store.hasOpen(dedupKey)) {
            return refuse(ImpulseProposal.ProposalState.DUPLICATE);
        }
        if (store.openCount() >= store.capacity()) {
            return refuse(ImpulseProposal.ProposalState.REFUSED_QUEUE_FULL);
        }

        // S8: the id is derived from the impulse, the cycle and a per-key
        // counter — never from a wall clock, so it is reproducible.
        long sequence = sequences.merge(dedupKey, 1L, Long::sum);
        String id = record.impulse().name() + ':' + cycle + ':' + sequence;
        long now = nanoClock.getAsLong();
        ImpulseProposal proposal = new ImpulseProposal(id, record.impulse(), kind, cycle, source,
                record.envelopeSpent(), record.rowsAllocated(), record.shadowPrice(),
                now + ttlNanos, ImpulseProposal.ProposalState.PROPOSED);

        if (!store.record(proposal)) {
            return refuse(ImpulseProposal.ProposalState.REFUSED_QUEUE_FULL);
        }
        outcomes.get(ImpulseProposal.ProposalState.PROPOSED).increment();
        return new Consumption(ImpulseProposal.ProposalState.PROPOSED, Optional.of(proposal));
    }

    /**
     * The execution-time step: the FROZEN gate is consulted AGAIN before the
     * proposal is accepted for review, because the state that permitted it at
     * creation may have changed.
     *
     * <p>An expired proposal is closed as {@code EXPIRED} instead — it is never
     * accepted for review after its lifetime.
     *
     * @param proposalId the proposal id
     * @return the proposal in its new state, or empty when the store has never
     *         seen it
     */
    public Optional<ImpulseProposal> acceptForReview(String proposalId) {
        Objects.requireNonNull(proposalId, "proposalId");
        Optional<ImpulseProposal> found = store.byId(proposalId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ImpulseProposal proposal = found.get();
        if (proposal.state() != ImpulseProposal.ProposalState.PROPOSED) {
            return found;
        }
        if (nanoClock.getAsLong() >= proposal.expiresAtNanos()) {
            return close(proposalId, ImpulseProposal.ProposalState.EXPIRED);
        }
        // S3, second half: the gate is consulted at EXECUTION time.
        if (violatesFrozenGate(proposal.impulse())) {
            return close(proposalId, ImpulseProposal.ProposalState.REFUSED_FROZEN);
        }
        return close(proposalId, ImpulseProposal.ProposalState.ACCEPTED_FOR_REVIEW);
    }

    /**
     * Close every open proposal whose expiry has passed.
     *
     * <p>This is the sweep that guarantees S6: no proposal is left open past
     * its expiry.
     *
     * @return the number of proposals that were closed
     */
    public int sweep() {
        long now = nanoClock.getAsLong();
        int closed = 0;
        for (ImpulseProposal proposal : store.open()) {
            if (now >= proposal.expiresAtNanos()) {
                close(proposal.id(), ImpulseProposal.ProposalState.EXPIRED);
                closed++;
            }
        }
        return closed;
    }

    /** The count of every proposal outcome, including the refusals. */
    public Map<ImpulseProposal.ProposalState, Long> outcomeCounts() {
        Map<ImpulseProposal.ProposalState, Long> counts =
                new EnumMap<>(ImpulseProposal.ProposalState.class);
        for (Map.Entry<ImpulseProposal.ProposalState, LongAdder> entry : outcomes.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().sum());
        }
        return Map.copyOf(counts);
    }

    /** How many fire records the scheduler did not accept, and that therefore never became proposals. */
    public long notAcceptedCount() {
        return notAccepted.sum();
    }

    /** The store the proposals are recorded in. */
    public ImpulseProposalStore store() {
        return store;
    }

    private Consumption refuse(ImpulseProposal.ProposalState state) {
        outcomes.get(state).increment();
        return new Consumption(state, Optional.empty());
    }

    private Optional<ImpulseProposal> close(String id, ImpulseProposal.ProposalState state) {
        Optional<ImpulseProposal> closed = store.close(id, state);
        if (closed.isPresent()) {
            outcomes.get(state).increment();
        }
        return closed;
    }

    private boolean violatesFrozenGate(AutonomyImpulse impulse) {
        return frozenGate.test(impulse.name());
    }

    /**
     * The capacity of the serving proposal queue: how many proposals may be
     * open at once before the consumer starts refusing.
     */
    public static final int SERVING_CAPACITY = 64;

    /**
     * How long a serving proposal stays open, in nanoseconds — ten minutes.
     *
     * <p>The drives fire every 30, 120 and 300 seconds, so a proposal that
     * outlives ten minutes is a proposal nobody reviewed. The engine's idle
     * cycle sweeps the expired ones.
     */
    public static final long SERVING_TTL_NANOS = 600_000_000_000L;

    /**
     * The consumer the serving paths use: a bounded store, the FROZEN gate of
     * the given filter, and the monotonic clock of the JVM.
     *
     * @param ethics the ethical filter whose frozen gate the consumer consults
     * @return the consumer
     */
    public static ImpulseProposalConsumer forServing(io.matrix.ethics.EthicalFilter ethics) {
        return new ImpulseProposalConsumer(
                new BoundedImpulseProposalStore(SERVING_CAPACITY),
                gateOf(ethics),
                System::nanoTime,
                SERVING_TTL_NANOS);
    }

    /**
     * The FROZEN gate of an {@link io.matrix.ethics.EthicalFilter}, in the form
     * the consumer takes: "does this action name violate a frozen axiom?".
     *
     * @param ethics the filter; must not be null
     * @return the gate
     */
    public static Predicate<String> gateOf(io.matrix.ethics.EthicalFilter ethics) {
        Objects.requireNonNull(ethics, "ethics");
        return name -> ethics.frozenViolatedAxiom(name) != null;
    }
}
