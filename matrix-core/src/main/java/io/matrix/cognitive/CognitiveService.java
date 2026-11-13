package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.Portfolio;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.knowledge.KnowledgeWorkbench;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * W44 — the bean that owns the cognitive kernel and everything it needs.
 *
 * <p>The kernel is a plain object, not a bean: it takes its collaborators as constructor
 * arguments, which is what makes it testable without a container. This service is the one place
 * that knows how to build it.</p>
 *
 * <p>Everything is created lazily on the first use, because the kernel opens the knowledge
 * store's file and the application must not touch that file until something asks it to. The
 * store it opens is the SAME file the workbench uses, so a case written by the kernel is visible
 * to the workbench's Epistemic panel and survives a restart.</p>
 *
 * <h2>W45.0 — the identity of a request</h2>
 * <p>W44 built the signal id from the payload's {@code String.hashCode()}. That is not an
 * identity: two identical payloads produced the same id, and different payloads collided. Since
 * every write in the store was an {@code INSERT OR REPLACE}, a repeated request overwrote the
 * previous episode and everything in it — the system kept the LAST request and lost the
 * history.</p>
 *
 * <p>Now every request gets its own signal id from the {@link EpisodeIdAllocator}, and the
 * arrival number of the signal comes from the durable sequence in the store, so the order of
 * the signals survives a restart. The episode of a request follows from its signal id, and the
 * events of the episode follow from the episode — four identities, four namespaces.</p>
 *
 * <h2>W45.0 — the idempotency key</h2>
 * <p>A caller may name a request with an idempotency key. Without a key every request is a new
 * request, even when the payload is byte-identical to an earlier one. With a key the request is
 * ONE request: a repeated delivery of the same key with the same payload returns the original
 * outcome and does no work at all, and a repeated delivery with a DIFFERENT payload is an
 * explicit conflict — never a second episode, never a silent overwrite. The mapping is durable,
 * so it survives a restart.</p>
 */
@ApplicationScoped
public class CognitiveService {

    /** Where a signal that arrived through this endpoint came from. */
    public static final String ENDPOINT_PROVENANCE = "the cognitive endpoint";

    @Inject
    KnowledgeWorkbench workbench;

    private final IdSource idSource;
    private final Supplier<String> clock;

    private CognitiveStore store;
    private CostLedger ledger;
    private CognitiveKernel kernel;
    private CognitiveSolver solver;
    private EpisodeIdAllocator allocator;
    private IdempotencyRegistry registry;
    private RequestFailpoint failpoint;
    private OwnershipToken ownerToken;
    private java.util.function.LongSupplier monotonicNanos;

    /**
     * Replaces the monotonic clock of the cycle's deadline.
     *
     * <p>W45.1. The clock is the seam a test uses to make the budget expire deterministically.
     * It is NOT a decision input: the deadline is a resource boundary, and the wall clock is
     * still what the records carry.</p>
     *
     * @param clock the monotonic clock, in nanoseconds, or null for the system clock
     */
    public synchronized void monotonicClock(java.util.function.LongSupplier clock) {
        this.monotonicNanos = clock;
        this.kernel = null;
    }

    /** The container's constructor: a random identity source and the system clock. */
    @Inject
    public CognitiveService() {
        this(IdSource.random(), () -> java.time.Instant.now().toString());
    }

    /**
     * A service with an explicit identity source and clock.
     *
     * <p>Both are replaceable so that a test can say exactly which id a request received and
     * what instant was written with it. The production service uses a UUID and the system
     * clock; neither is used to DECIDE anything.</p>
     *
     * @param idSource where the unique tokens of the signal ids come from
     * @param clock    the source of the timestamps written with the durable records
     */
    public CognitiveService(IdSource idSource, Supplier<String> clock) {
        this(idSource, clock, RequestFailpoint.fromSystemProperties());
    }

    /**
     * A service with an explicit identity source, clock and fault injection point.
     *
     * <p>The failpoint is the seam the tests use to fail a request at an exact point of its
     * lifecycle. It is NOT an endpoint and it is absent unless it is given.</p>
     *
     * @param idSource  where the unique tokens of the signal ids come from
     * @param clock     the source of the timestamps written with the durable records
     * @param failpoint the fault injection point, or the absent one
     */
    public CognitiveService(IdSource idSource, Supplier<String> clock, RequestFailpoint failpoint) {
        this.idSource = idSource;
        this.clock = clock;
        this.failpoint = failpoint;
    }

    /** The kernel, built on first use. */
    public synchronized CognitiveKernel kernel() {
        if (kernel == null) {
            kernel = build();
        }
        return kernel;
    }

    /** The solver over the algorithm portfolio, built on first use. */
    public synchronized CognitiveSolver solver() {
        if (solver == null) {
            kernel();
        }
        return solver;
    }

    /**
     * The file this service's cognitive store lives in.
     *
     * <p><b>Why it is asked for.</b> The distributed resource opens a second store, on the
     * file of the dispatches. Two stores on one file are two writers on one database, and the
     * writer lock does not catch it — both holders are in this process and merely join the
     * lock. The resource therefore compares its own file with this one before it opens
     * anything.</p>
     *
     * @return the path of the store's file
     */
    public synchronized java.nio.file.Path storePath() {
        return Paths.get(workbench.databasePath());
    }

    /** The cognitive store, opened on first use. */
    public synchronized CognitiveStore store() {
        if (store == null) {
            store = new CognitiveStore(Paths.get(workbench.databasePath()));
        }
        return store;
    }

    /**
     * The ledger of measured methods, opened on first use.
     *
     * <p><b>Why it is one ledger for the whole process.</b> A ledger is the profile of the
     * system's own methods: what they cost, and what was concluded about their answers. Two
     * ledgers over one store would be two profiles that disagree until a restart, and a
     * profile that nobody can read back is not a profile. The ledger is therefore built once
     * and kept, and it is the same object the selector reads and the same one a remote
     * verdict is recorded into.</p>
     *
     * @return the ledger of this process
     */
    public synchronized CostLedger ledger() {
        if (ledger == null) {
            ledger = new CostLedger(store(), io.matrix.cognitive.CognitiveKernel.WORKSPACE_ID);
        }
        return ledger;
    }

    /** The allocator of the identities of a cycle, built on first use. */
    public synchronized EpisodeIdAllocator allocator() {
        if (allocator == null) {
            allocator = new EpisodeIdAllocator(idSource, store());
        }
        return allocator;
    }

    /** The registry of the idempotency keys, built on first use. */
    public synchronized IdempotencyRegistry registry() {
        if (registry == null) {
            registry = new IdempotencyRegistry(store(), clock, allocator(), ownerToken());
        }
        return registry;
    }

    /**
     * The ownership token of this service instance.
     *
     * <p>W45.1. The token identifies the INSTANCE, not the JVM, so a service that is rebuilt
     * inside the same process does not inherit the claims of the instance it replaced. The
     * registry and the recovery use the same token, so they agree about who owns what.</p>
     *
     * @return the token of this instance
     */
    public synchronized OwnershipToken ownerToken() {
        if (ownerToken == null) {
            ownerToken = OwnershipToken.current();
        }
        return ownerToken;
    }

    /**
     * The deterministic fault injection point of this process.
     *
     * <p>It is absent unless the system property {@code w45.failpoint} names a checkpoint, so
     * the production behaviour does not depend on it. There is no endpoint for it: a request
     * can never make the process stop.</p>
     *
     * @return the failpoint of this process
     */
    public synchronized RequestFailpoint failpoint() {
        if (failpoint == null) {
            failpoint = RequestFailpoint.fromSystemProperties();
        }
        return failpoint;
    }
    /**
     * The recovery of the requests that a terminated process left behind.
     *
     * <p>W45.1. A request whose owner is gone is classified from its last durable checkpoint:
     * a request that had written nothing may be executed again, and a request that had
     * committed work may not. Either way the episode it opened is closed with a terminal
     * event, because an episode that is still open is an episode whose outcome is unknown —
     * and the store must never say that a cycle is still running when it is not.</p>
     *
     * <p>The recovery never steals the work of a live process: a request whose owner is alive
     * is left exactly as it is.</p>
     *
     * @return what the recovery did, one line per request
     */
    public synchronized java.util.List<String> recoverInterruptedRequests() {
        java.util.List<String> report = new java.util.ArrayList<>();
        IdempotencyRegistry registry = registry();
        for (CognitiveStore.IdempotencyRecord record : registry.unfinished()) {
            java.util.Optional<OwnershipToken> claimedBy = record.owner();
            if (claimedBy.isPresent()) {
                OwnershipToken.Ownership ownership = OwnershipToken.classify(claimedBy.get(), ownerToken);
                if (ownership == OwnershipToken.Ownership.MINE
                        || ownership == OwnershipToken.Ownership.LIVE_OTHER) {
                    // The owner is alive: the work is not stolen.
                    continue;
                }
            }
            RequestCheckpoint checkpoint = record.lastCheckpoint().orElse(null);
            boolean effect = checkpoint != null && checkpoint.durableEffect();
            RequestExecutionState state = effect
                    ? RequestExecutionState.INTERRUPTED_PARTIAL
                    : RequestExecutionState.INTERRUPTED_NO_EFFECT;
            String detail = effect
                    ? "the process that owned this request is gone and the request had reached "
                            + checkpoint + "; the outcome cannot be established"
                    : "the process that owned this request is gone and the request had written "
                            + "nothing; it may be executed again";
            // The owner is gone, so the recovery writes the classification on its behalf.
            registry.recordRecovery(record.key(), state, checkpoint, detail);
            if (record.episodeId() != null && !record.episodeId().isBlank()) {
                store().interruptEpisode(record.episodeId(), detail, clock.get());
            }
            report.add(record.key() + " → " + state + " (checkpoint "
                    + (checkpoint == null ? "none" : checkpoint) + ")");
        }
        return report;
    }

    private CognitiveKernel build() {
        MicroWorldEnvironment environment = new LampEnvironment();
        MicroWorldSession.Factory sessionFactory = new MicroWorldSession.DefaultFactory(environment);
        // ONE registry holds the whole portfolio and the two micro-world methods, and ONE
        // ledger holds the measured costs of all of them. A method's cost is only comparable
        // with another method's cost when they were measured the same way.
        // W52: the ledger of measured costs is DURABLE. It used to live in the heap, so
        // every restart forgot how the system's own methods behave and the selector fell
        // back to the DECLARED complexity — a person's estimate where a measurement
        // belongs. The profile is written by the single writer of the cognitive database
        // and read back when the application starts.
        CostLedger profile = ledger();
        AlgorithmRegistry registry = Portfolio.defaultRegistry()
                .register(new MicroWorldReferenceMethod())
                .register(new MicroWorldSettledModelMethod());
        this.solver = new CognitiveSolver(new AlgorithmSelector(registry, profile), profile);
        CognitiveKernel built = new CognitiveKernel(
                workbench,
                store(),
                sessionFactory,
                environment,
                new KernelSelector(registry, profile),
                new KernelRegulator(),
                () -> java.time.Instant.now().toString(),
                monotonicNanos == null ? System::nanoTime : monotonicNanos);
        // W45.0. The regulatory state is restored ONCE, when the application starts. ADR-W44-01
        // lists the drives among the cognitive state the store persists, and until W45 they
        // were the one item on that list that was not persisted: the adapter held them in
        // memory, so every restart reset every drive to neutral.
        built.restoreRegulatoryState();
        // W45.1. The requests that a terminated process left behind are recovered ONCE, when
        // the application starts. A request whose owner is alive is left alone: its work is
        // not stolen. A request whose owner is gone is classified from its last durable
        // checkpoint, and the episode it opened is closed with a terminal event.
        recoverInterruptedRequests();
        return built;
    }

    /**
     * Runs one cognitive cycle for a payload.
     *
     * <p>Every call is a NEW request: it gets its own signal id, its own episode and its own
     * events, even when the payload is byte-identical to an earlier one. A caller that wants
     * the same request to be recognised uses {@link #observe(String, String)}.</p>
     *
     * @param payload the assignment to reason about
     * @return the outcome
     */
    public CognitiveOutcome observe(String payload) {
        return observe(payload, null);
    }

    /**
     * Runs one cognitive cycle for a payload, under an optional idempotency key.
     *
     * <p>Without a key this is exactly {@link #observe(String)}. With a key the request is
     * recognised: the first delivery runs the cycle and records the outcome under the key; a
     * repeated delivery with the same payload returns that outcome and does NO work — no
     * episode, no events, no prediction; a repeated delivery with a DIFFERENT payload is a
     * {@link CognitiveConflictException}.</p>
     *
     * @param payload        the assignment to reason about
     * @param idempotencyKey the key that names the logical request, or null
     * @return the outcome
     */
    public CognitiveOutcome observe(String payload, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return runCycle(payload, RequestProgressListener.NO_OP).outcome();
        }
        String key = idempotencyKey.trim();
        String fingerprint = IdempotencyRegistry.fingerprintOf(payload);
        IdempotencyRegistry registry = registry();
        IdempotencyRegistry.Claim claim = registry.claim(key, fingerprint);
        switch (claim.kind()) {
            case REPLAY -> {
                return claim.outcome();
            }
            case CONFLICT -> {
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IDEMPOTENCY_KEY,
                        "the idempotency key " + key + " was already used for a different payload; "
                                + "the first request keeps its outcome");
            }
            case IN_PROGRESS -> {
                Optional<CognitiveOutcome> finished = registry.await(key, fingerprint);
                if (finished.isPresent()) {
                    return finished.get();
                }
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IDEMPOTENCY_KEY,
                        "a request with the idempotency key " + key + " is still running");
            }
            case INDETERMINATE -> {
                // W45.1. The request was interrupted after it had already written something.
                // The outcome cannot be established, and the request may NOT be executed again:
                // the caller is told the truth instead of being given a duplicate.
                throw new CognitiveRequestFailure(CognitiveRequestFailure.Kind.INDETERMINATE,
                        "the request with the idempotency key " + key + " was interrupted after "
                                + "it had already committed durable work"
                                + (claim.checkpoint() == null ? "" : " (it had reached "
                                        + claim.checkpoint() + ")")
                                + "; its outcome cannot be established and the request will not "
                                + "be executed again");
            }
            default -> {
                return execute(key, payload, claim);
            }
        }
    }

    /**
     * Executes a claimed request and records its terminal state.
     *
     * <p>W45.1. The mapping of the request is NEVER deleted. A request that fails inside the
     * cycle ends in a {@code FAILED} outcome, which is durable and replayable: the next
     * delivery of the same key is answered with that outcome and does no work. A failure
     * OUTSIDE the cycle is recorded with the classification that says whether the work may be
     * repeated — and the classification is derived from the last durable checkpoint, not from
     * an assumption.</p>
     *
     * @param key     the idempotency key
     * @param payload the assignment to reason about
     * @param claim   the claim the caller owns
     * @return the outcome
     */
    private CognitiveOutcome execute(String key, String payload, IdempotencyRegistry.Claim claim) {
        IdempotencyRegistry registry = registry();
        java.util.concurrent.atomic.AtomicReference<RequestCheckpoint> last =
                new java.util.concurrent.atomic.AtomicReference<>();
        RequestProgressListener listener = checkpoint -> {
            registry.checkpoint(key, checkpoint);
            last.set(checkpoint);
            failpoint().apply(checkpoint);
        };
        try {
            // W45.1. The CLAIMED checkpoint is reported BEFORE the cycle starts: the claim
            // is already durable, and this is the one boundary at which the request has
            // written nothing at all. It is the boundary that decides whether a request
            // that dies here may be executed again — and it is the boundary a test must be
            // able to kill the process at.
            listener.at(RequestCheckpoint.CLAIMED);
            Cycle cycle = runCycle(payload, listener, claim.signalId(), claim.sequence());
            registry.complete(key, cycle.outcome().episodeId(), claim.signalId(), cycle.outcome());
            // The completion is the last durable write of the request. A process that dies
            // after it died with a COMPLETED request: the retry replays the outcome and
            // does no work.
            listener.at(RequestCheckpoint.IDEMPOTENCY_COMPLETED);
            return cycle.outcome();
        } catch (RuntimeException e) {
            RequestCheckpoint reached = last.get();
            RequestExecutionState state = reached == null || !reached.durableEffect()
                    ? RequestExecutionState.FAILED_NO_EFFECT
                    : RequestExecutionState.INTERRUPTED_PARTIAL;
            registry.fail(key, state, reached, e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            throw e;
        }
    }

    /** One cycle: the signal it received and the outcome it produced. */
    private record Cycle(CognitiveOutcome outcome, String signalId) {
    }

    /**
     * Runs one cycle with a fresh identity.
     *
     * <p>The signal id is unique per REQUEST — it is not derived from the payload — and the
     * arrival number of the signal comes from the durable sequence of the store.</p>
     *
     * @param payload the assignment to reason about
     * @return the cycle
     */
    private Cycle runCycle(String payload) {
        return runCycle(payload, RequestProgressListener.NO_OP);
    }

    /**
     * Runs one cycle with a fresh identity and a progress listener.
     *
     * @param payload  the assignment to reason about
     * @param listener the listener of the durable checkpoints
     * @return the cycle
     */
    private Cycle runCycle(String payload, RequestProgressListener listener) {
        EpisodeIdAllocator allocator = allocator();
        String signalId = allocator.nextSignalId();
        long sequence = allocator.nextSignalSequence();
        return runCycle(payload, listener, signalId, sequence);
    }

    /**
     * Runs one cycle with the identities the caller already holds.
     *
     * <p>W45.1: a request that is executed again — after an interruption that wrote nothing —
     * keeps the identities it was given when it was claimed. It is the SAME logical request,
     * in the SAME episode, and never a second one.</p>
     *
     * @param payload  the assignment to reason about
     * @param listener the listener of the durable checkpoints
     * @param signalId the signal id of the request
     * @param sequence the arrival number of the signal
     * @return the cycle
     */
    private Cycle runCycle(String payload, RequestProgressListener listener, String signalId,
                           long sequence) {
        CognitiveSignal signal = CognitiveSignal.structured(signalId, payload,
                ENDPOINT_PROVENANCE, sequence);
        return new Cycle(kernel().process(signal, listener), signalId);
    }

    @PreDestroy
    void shutdown() {
        if (store != null) {
            store.close();
            store = null;
        }
        allocator = null;
        registry = null;
        kernel = null;
    }
}
