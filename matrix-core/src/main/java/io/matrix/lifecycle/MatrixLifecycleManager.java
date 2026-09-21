package io.matrix.lifecycle;

import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.learning.ContinuousLearningLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Managed Matrix MVP — complete agent lifecycle manager.
 *
 * <p>Orchestrates: Initialize → Train → Deploy → Monitor → Retrain → Shutdown.
 * This is the "glue" that wires all L.1-L.5 components together into
 * a coherent agent lifecycle.
 *
 * <p>Phase transitions:
 * <pre>
 *   INIT → TRAINING → DEPLOYED → MONITORING → RETRAINING
 *                                            ↓
 *                          DEPLOYED ←─────────┘ (on success)
 *
 *   Any phase → SHUTDOWN (terminal)
 * </pre>
 *
 * <p>Thread-safety: all state transitions are atomic via {@link AtomicReference}
 * and guarded by a {@link ReentrantLock}. History is backed by
 * {@link Collections#synchronizedList}.
 *
 * <p>Design based on: Yandex architectural review (agent lifecycle patterns),
 * BPMN workflow (sequential phase gates), Continuous Learning research
 * (feedback-driven retraining loops).
 */
public final class MatrixLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(MatrixLifecycleManager.class);

    /**
     * Lifecycle phases in execution order.
     */
    public enum LifecyclePhase {
        INIT,
        TRAINING,
        DEPLOYED,
        MONITORING,
        RETRAINING,
        SHUTDOWN
    }

    /**
     * Immutable snapshot of lifecycle state at a point in time.
     *
     * @param phase      current lifecycle phase
     * @param epoch      generation (TRAINING) or retrain count (MONITORING/retrain)
     * @param avgFitness average fitness score (0.0–1.0), or 0 if not applicable
     * @param testCount  ticks/generations/iterations processed
     * @param uptimeMs   milliseconds since lifecycle start
     */
    public record LifecycleState(
            LifecyclePhase phase,
            int epoch,
            double avgFitness,
            int testCount,
            long uptimeMs) {
        public LifecycleState {
            if (avgFitness < 0.0 || avgFitness > 1.0) {
                throw new IllegalArgumentException("avgFitness must be in [0.0, 1.0], got " + avgFitness);
            }
        }

        static LifecycleState initial() {
            return new LifecycleState(LifecyclePhase.INIT, 0, 0.0, 0, 0);
        }
    }

    // ── Dependencies ──

    private final AgentBrainService brain;
    private final AgentLoop agentLoop;
    private final ContinuousLearningLoop learningLoop;
    private final KnowledgeGraphStore knowledgeGraph;
    private final long lifecycleStartNs;

    // ── Thread-safe state ──

    private final AtomicReference<LifecycleState> currentState;
    private final List<LifecycleState> history;
    private final ReentrantLock phaseLock = new ReentrantLock();

    /**
     * Creates a lifecycle manager with all dependencies.
     *
     * @param brain          agent brain service (training, online learning)
     * @param agentLoop      classic agent loop for deployment
     * @param learningLoop   continuous learning loop for monitoring/feedback
     * @param knowledgeGraph knowledge graph for entity storage
     */
    public MatrixLifecycleManager(AgentBrainService brain,
                                   AgentLoop agentLoop,
                                   ContinuousLearningLoop learningLoop,
                                   KnowledgeGraphStore knowledgeGraph) {
        this.brain = Objects.requireNonNull(brain, "brain must not be null");
        this.agentLoop = Objects.requireNonNull(agentLoop, "agentLoop must not be null");
        this.learningLoop = Objects.requireNonNull(learningLoop, "learningLoop must not be null");
        this.knowledgeGraph = Objects.requireNonNull(knowledgeGraph, "knowledgeGraph must not be null");
        this.lifecycleStartNs = System.nanoTime();
        this.currentState = new AtomicReference<>(LifecycleState.initial());
        this.history = Collections.synchronizedList(new ArrayList<>());
        history.add(LifecycleState.initial());
    }

    // ── Phase 1: Initialize ──

    /**
     * Initializes the brain with random weights and seeds the knowledge graph.
     * Must be called first. Idempotent — if already past INIT, returns current state.
     *
     * @return lifecycle state after initialization
     */
    public LifecycleState initialize() {
        phaseLock.lock();
        try {
            LifecycleState current = currentState();
            if (current.phase() != LifecyclePhase.INIT) {
                log.info("Already initialized — current phase: {}", current.phase());
                return current;
            }

            brain.initializeRandom();

            // Seed knowledge graph with lifecycle entities
            knowledgeGraph.addEntity(new KnowledgeGraphStore.Entity("lifecycle", "LifecycleRoot"));
            knowledgeGraph.addEntity(new KnowledgeGraphStore.Entity("brain", "AgentBrain"));
            try {
                knowledgeGraph.addRelation(new KnowledgeGraphStore.Relation(
                        "lifecycle", "brain", "manages", 1.0));
            } catch (IllegalArgumentException e) {
                // Relation may already exist; ignore
            }

            LifecycleState state = recordState(LifecyclePhase.INIT, 0, 0.0, 0);
            log.info("Lifecycle initialized: brain={}, kgEntities={}",
                    brain.brain(), knowledgeGraph.entityCount());
            return state;
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Phase 2: Train ──

    /**
     * Trains the brain through evolution.
     *
     * <p>Phase transition: INIT/TRAINING/RETRAINING → TRAINING → DEPLOYED.
     * After training completes, automatically transitions to DEPLOYED.
     *
     * @param generations number of evolution generations
     * @param population  size of evolved population
     * @param k           neuron input size (K constant)
     * @return lifecycle state after training (DEPLOYED phase)
     * @throws IllegalStateException if lifecycle is in SHUTDOWN
     */
    public LifecycleState train(int generations, int population, int k) {
        phaseLock.lock();
        try {
            LifecycleState current = currentState();
            assertNotShutdown(current);

            LifecycleState trainingState = recordState(
                    LifecyclePhase.TRAINING, 0, current.avgFitness(), 0);

            AgentBrainService.EvolutionResult result = brain.train(generations, population, k);

            // Normalize bestFitness: fitness values can be large, cap at Long.MAX_VALUE
            double normalizedFitness = normalizeFitness(result.bestFitness());

            LifecycleState deployedState = recordState(
                    LifecyclePhase.DEPLOYED, result.generations(), normalizedFitness,
                    result.generations());

            log.info("Training complete: generations={}, bestFitness={}, normalized={}",
                    result.generations(), result.bestFitness(), normalizedFitness);
            return deployedState;
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Phase 3: Deploy ──

    /**
     * Deploys the agent loop — begins the Observe→Think→Act cycle.
     *
     * <p>Phase transition: DEPLOYED → DEPLOYED.
     *
     * @param maxTicks maximum ticks to run the agent loop
     * @return lifecycle state after deployment
     * @throws IllegalStateException if not in DEPLOYED or SHUTDOWN
     */
    public LifecycleState deploy(int maxTicks) {
        phaseLock.lock();
        try {
            LifecycleState current = currentState();
            assertNotShutdown(current);

            List<AgentState> states = agentLoop.run(maxTicks);

            LifecycleState state = recordState(
                    LifecyclePhase.DEPLOYED,
                    current.epoch(),
                    current.avgFitness(),
                    states.size());

            log.info("Deployment complete: ticks={}, actionsInHistory={}",
                    states.size(), states.size());
            return state;
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Phase 4: Monitor ──

    /**
     * Monitors agent performance and collects feedback through the continuous
     * learning loop.
     *
     * <p>Phase transition: DEPLOYED → MONITORING → DEPLOYED.
     * The learning loop automatically retrains at batch boundaries.
     *
     * @param tickCount number of ticks to run through monitoring
     * @return lifecycle state after monitoring
     * @throws IllegalStateException if lifecycle is in SHUTDOWN
     */
    public LifecycleState monitor(int tickCount) {
        phaseLock.lock();
        try {
            LifecycleState current = currentState();
            assertNotShutdown(current);

            LifecycleState monitoringState = recordState(
                    LifecyclePhase.MONITORING,
                    learningLoop.retrainCount(),
                    current.avgFitness(),
                    0);

            List<AgentState> states = learningLoop.run(tickCount);

            double avgFitness = computeAvgFitness(learningLoop);

            LifecycleState deployedState = recordState(
                    LifecyclePhase.DEPLOYED,
                    learningLoop.retrainCount(),
                    avgFitness,
                    states.size());

            log.info("Monitoring complete: ticks={}, retrains={}, avgFitness={}",
                    states.size(), learningLoop.retrainCount(), avgFitness);
            return deployedState;
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Phase 5: Retrain ──

    /**
     * Performs explicit retraining based on collected feedback.
     *
     * <p>Phase transition: MONITORING/DEPLOYED → RETRAINING → DEPLOYED.
     * Uses {@link AgentBrainService#onlineTrain(int)} for hill-climbing.
     *
     * @param iterations number of hill-climbing iterations per neuron
     * @return lifecycle state after retraining
     * @throws IllegalStateException if lifecycle is in SHUTDOWN
     */
    public LifecycleState retrain(int iterations) {
        phaseLock.lock();
        try {
            LifecycleState current = currentState();
            assertNotShutdown(current);

            LifecycleState retrainingState = recordState(
                    LifecyclePhase.RETRAINING,
                    current.epoch() + 1,
                    current.avgFitness(),
                    0);

            brain.onlineTrain(iterations);

            // After retraining, fitness may have changed — recompute from learning loop
            double avgFitness = computeAvgFitness(learningLoop);

            LifecycleState deployedState = recordState(
                    LifecyclePhase.DEPLOYED,
                    retrainingState.epoch(),
                    avgFitness,
                    iterations);

            log.info("Retraining complete: iterations={}, avgFitness={}",
                    iterations, avgFitness);
            return deployedState;
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Full Lifecycle ──

    /**
     * Runs the complete lifecycle: init → train → deploy → monitor → retrain.
     *
     * @param generations       evolution generations for training
     * @param population        population size for training
     * @param k                 neuron input size
     * @param ticks             deployment/monitoring ticks
     * @param retrainIterations hill-climbing iterations for retraining
     * @return immutable list of all lifecycle state transitions
     */
    public List<LifecycleState> runFullLifecycle(int generations, int population,
                                                   int k, int ticks,
                                                   int retrainIterations) {
        phaseLock.lock();
        try {
            List<LifecycleState> snapshot = new ArrayList<>();
            snapshot.add(initialize());
            snapshot.add(train(generations, population, k));
            snapshot.add(deploy(ticks));
            snapshot.add(monitor(ticks));
            snapshot.add(retrain(retrainIterations));
            return Collections.unmodifiableList(snapshot);
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Phase 6: Shutdown ──

    /**
     * Shuts down the lifecycle. Terminal state — no further transitions allowed.
     *
     * @return final lifecycle state
     */
    public LifecycleState shutdown() {
        phaseLock.lock();
        try {
            LifecycleState current = currentState();
            if (current.phase() == LifecyclePhase.SHUTDOWN) {
                return current;
            }

            // Stop the agent loop if running
            if (agentLoop.isRunning()) {
                agentLoop.stop();
            }

            LifecycleState state = recordState(LifecyclePhase.SHUTDOWN,
                    current.epoch(), current.avgFitness(), current.testCount());
            log.info("Lifecycle shut down: totalUptimeMs={}", state.uptimeMs());
            return state;
        } finally {
            phaseLock.unlock();
        }
    }

    // ── Accessors ──

    /** Returns the current lifecycle state snapshot. */
    public LifecycleState currentState() {
        return currentState.get();
    }

    /** Returns an immutable copy of the full state transition history. */
    public List<LifecycleState> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    /** Returns the agent brain service. */
    public AgentBrainService brain() {
        return brain;
    }

    /** Returns the knowledge graph store. */
    public KnowledgeGraphStore knowledgeGraph() {
        return knowledgeGraph;
    }

    // ── Internal helpers ──

    /**
     * Records a state transition: creates a new LifecycleState,
     * atomically updates currentState, and appends to history.
     *
     * @return the newly recorded state
     */
    private LifecycleState recordState(LifecyclePhase phase, int epoch,
                                        double avgFitness, int testCount) {
        long uptimeMs = (System.nanoTime() - lifecycleStartNs) / 1_000_000;
        LifecycleState state = new LifecycleState(phase, epoch, avgFitness, testCount, uptimeMs);
        currentState.set(state);
        history.add(state);
        return state;
    }

    /**
     * Normalizes a raw fitness value to [0.0, 1.0] range.
     * Fitness values from evolution can be large (0–Long.MAX_VALUE).
     * Uses logarithmic scaling: normalized = log2(raw + 1) / log2(MAX + 1).
     */
    private static double normalizeFitness(long rawFitness) {
        if (rawFitness <= 0) return 0.0;
        double logMax = Math.log(Long.MAX_VALUE + 1.0);
        double logRaw = Math.log(rawFitness + 1.0);
        double normalized = logRaw / logMax;
        return Math.min(1.0, Math.max(0.0, normalized));
    }

    /**
     * Computes average fitness from the learning loop's fitness history.
     * Returns 0.0 if no history yet.
     */
    private static double computeAvgFitness(ContinuousLearningLoop loop) {
        List<Double> fitnessHistory = loop.fitnessHistory();
        if (fitnessHistory.isEmpty()) {
            return 0.0;
        }
        return fitnessHistory.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Asserts the lifecycle is not in SHUTDOWN.
     */
    private static void assertNotShutdown(LifecycleState state) {
        if (state.phase() == LifecyclePhase.SHUTDOWN) {
            throw new IllegalStateException(
                    "Lifecycle is shut down — no further operations allowed");
        }
    }
}
