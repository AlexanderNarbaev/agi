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
    private CognitiveKernel kernel;
    private CognitiveSolver solver;
    private EpisodeIdAllocator allocator;
    private IdempotencyRegistry registry;

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
        this.idSource = idSource;
        this.clock = clock;
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

    /** The cognitive store, opened on first use. */
    public synchronized CognitiveStore store() {
        if (store == null) {
            store = new CognitiveStore(Paths.get(workbench.databasePath()));
        }
        return store;
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
            registry = new IdempotencyRegistry(store(), clock);
        }
        return registry;
    }

    private CognitiveKernel build() {
        MicroWorldEnvironment environment = new LampEnvironment();
        MicroWorldSession.Factory sessionFactory = new MicroWorldSession.DefaultFactory(environment);
        // ONE registry holds the whole portfolio and the two micro-world methods, and ONE
        // ledger holds the measured costs of all of them. A method's cost is only comparable
        // with another method's cost when they were measured the same way.
        CostLedger ledger = new CostLedger();
        AlgorithmRegistry registry = Portfolio.defaultRegistry()
                .register(new MicroWorldReferenceMethod())
                .register(new MicroWorldSettledModelMethod());
        this.solver = new CognitiveSolver(new AlgorithmSelector(registry, ledger), ledger);
        CognitiveKernel built = new CognitiveKernel(
                workbench,
                store(),
                sessionFactory,
                environment,
                new KernelSelector(registry, ledger),
                new KernelRegulator());
        // W45.0. The regulatory state is restored ONCE, when the application starts. ADR-W44-01
        // lists the drives among the cognitive state the store persists, and until W45 they
        // were the one item on that list that was not persisted: the adapter held them in
        // memory, so every restart reset every drive to neutral.
        built.restoreRegulatoryState();
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
            return runCycle(payload).outcome();
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
            default -> {
                try {
                    Cycle cycle = runCycle(payload);
                    registry.complete(key, cycle.outcome().episodeId(), cycle.signalId(),
                            cycle.outcome());
                    return cycle.outcome();
                } catch (RuntimeException e) {
                    // A request that failed is not a completed one: the key is released so
                    // that the caller may try again.
                    registry.release(key);
                    throw e;
                }
            }
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
        EpisodeIdAllocator allocator = allocator();
        String signalId = allocator.nextSignalId();
        long sequence = allocator.nextSignalSequence();
        CognitiveSignal signal = CognitiveSignal.structured(signalId, payload,
                ENDPOINT_PROVENANCE, sequence);
        return new Cycle(kernel().process(signal), signalId);
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
