package io.matrix.runtime;

import io.matrix.contract.Capability;

import java.util.List;

/**
 * W51 — the declaration of MATRIX's own subsystems.
 *
 * <p><b>What this replaces.</b> Nothing declared the parts of the system. The W51 end-to-end
 * acceptance list begins with "the runtime discovers or initializes the available
 * subsystems", and no code could answer it: the kernel knew its collaborators because they
 * were constructor arguments, the capability registry knew capabilities without saying who
 * offered them, and the isolation gate knew classes without knowing what they could do.</p>
 *
 * <p><b>The declarations are of REAL subsystems, not of aspirations.</b> Every capability named
 * here is offered by a class that exists in this repository and that is reachable from a
 * serving path. Where a subsystem exists but is not served, this class says so by leaving it
 * out — the honesty is the point, and it is why the W44 wave's capability registry and this
 * class have to agree.</p>
 *
 * <p><b>Health is not asserted here.</b> This class declares the SHAPE of the system — which
 * parts exist, what each can do, what each needs. Whether a part is up is a separate
 * question, answered by the runtime from observation, and a declaration that claimed
 * {@link RuntimeComponent.Health#UP} for something nobody had checked would be a lie with a
 * class name.</p>
 *
 * <p><b>The chain B peer is declared as remote.</b> When the brain runtime answers a
 * handshake, its capabilities join the runtime as a remote component. Until then it is
 * absent: the runtime must not report a capability it cannot reach.</p>
 */
public final class MatrixRuntimeComposition {

    /** The identity of the epistemic store, which holds the claims and the evidence. */
    public static final String EPISTEMIC_STORE = "epistemic-store";

    /** The identity of the cognitive store, which holds the episodes and the traces. */
    public static final String COGNITIVE_STORE = "cognitive-store";

    /** The identity of the cognitive kernel, which runs the cycle. */
    public static final String COGNITIVE_KERNEL = "cognitive-kernel";

    /** The identity of the algorithm portfolio, which chooses the method. */
    public static final String ALGORITHM_PORTFOLIO = "algorithm-portfolio";

    /** The identity of the micro-world environment, which is where an action has effect. */
    public static final String MICRO_WORLD = "micro-world";

    /** The identity of the regulatory layer. */
    public static final String REGULATION = "regulation";

    /** The identity of the bridge, which is the one crossing between the two chains. */
    public static final String BRIDGE = "bridge";

    /** The identity of the brain runtime, when it is reachable. */
    public static final String BRAIN_RUNTIME = "brain-runtime";

    /** The capability of classifying an incoming signal. */
    public static final String CAPABILITY_CLASSIFY = "perception.classify";

    /** The capability of storing and reading claims and evidence. */
    public static final String CAPABILITY_CLAIMS = "memory.claims";

    /** The capability of storing and reading episodes and traces. */
    public static final String CAPABILITY_EPISODES = "memory.episodes";

    /** The capability of running one cognitive cycle. */
    public static final String CAPABILITY_CYCLE = "reasoning.cycle";

    /** The capability of choosing a method for a request. */
    public static final String CAPABILITY_METHOD_SELECTION = "reasoning.method-selection";

    /** The capability of performing a probe in an environment. */
    public static final String CAPABILITY_PROBE = "action.probe";

    /** The capability of changing the regulatory state. */
    public static final String CAPABILITY_REGULATION = "governance.regulation";

    /** The capability of accepting a bridged observation. */
    public static final String CAPABILITY_BRIDGE_INGEST = "bridge.ingest";

    /** The capability the brain runtime offers when it answers a handshake. */
    public static final String CAPABILITY_BRAIN_THINK = "brain.think";

    private MatrixRuntimeComposition() {
        throw new AssertionError("no instances");
    }

    /**
     * The declaration of the chain A subsystems, in the order they are declared.
     *
     * @return the declarations
     */
    public static List<RuntimeComponent> chainAComponents() {
        return List.of(
                new RuntimeComponent(EPISTEMIC_STORE, RuntimeComponent.Kind.MEMORY,
                        List.of(new Capability(CAPABILITY_CLAIMS, EPISTEMIC_STORE,
                                "io.matrix.knowledge.KnowledgeWorkbench",
                                "holds the claims, the evidence and the sources of a workspace",
                                List.of("the store is not open", "the workspace does not exist"),
                                false)),
                        List.of(), false, RuntimeComponent.Health.UNKNOWN),

                new RuntimeComponent(COGNITIVE_STORE, RuntimeComponent.Kind.MEMORY,
                        List.of(new Capability(CAPABILITY_EPISODES, COGNITIVE_STORE,
                                "io.matrix.cognitive.CognitiveStore",
                                "holds the episodes, the predictions, the traces and the procedures",
                                List.of("the store is not open", "the owner does not hold the lock"),
                                false)),
                        List.of(EPISTEMIC_STORE), false, RuntimeComponent.Health.UNKNOWN),

                new RuntimeComponent(COGNITIVE_KERNEL, RuntimeComponent.Kind.REASONING,
                        List.of(new Capability(CAPABILITY_CYCLE, COGNITIVE_KERNEL,
                                "io.matrix.cognitive.CognitiveKernel",
                                "runs one cognitive cycle: perceive, predict, act, learn, reflect",
                                List.of("the cycle budget was exhausted", "the signal was refused"),
                                true)),
                        List.of(COGNITIVE_STORE, MICRO_WORLD, ALGORITHM_PORTFOLIO), false,
                        RuntimeComponent.Health.UNKNOWN),

                new RuntimeComponent(ALGORITHM_PORTFOLIO, RuntimeComponent.Kind.REASONING,
                        List.of(new Capability(CAPABILITY_METHOD_SELECTION, ALGORITHM_PORTFOLIO,
                                "io.matrix.cognitive.algorithms.AlgorithmSelector",
                                "chooses a method for a request from its declared preconditions"
                                        + " and the measurements taken of it",
                                List.of("no method is admitted for the family",
                                        "the budget does not fit the cheapest method"),
                                false)),
                        List.of(), false, RuntimeComponent.Health.UNKNOWN),

                new RuntimeComponent(MICRO_WORLD, RuntimeComponent.Kind.ACTION,
                        List.of(new Capability(CAPABILITY_PROBE, MICRO_WORLD,
                                "io.matrix.cognitive.microworld.MicroWorldEnvironment",
                                "answers a probe of an assignment of its input variables",
                                List.of("the assignment names a variable the world does not have"),
                                true)),
                        List.of(), false, RuntimeComponent.Health.UNKNOWN),

                new RuntimeComponent(REGULATION, RuntimeComponent.Kind.LEARNING,
                        List.of(new Capability(CAPABILITY_REGULATION, REGULATION,
                                "io.matrix.cognitive.neuromodulation.RegulationController",
                                "evolves the regulatory state from the cycle's error signal",
                                List.of("the regulatory state could not be persisted"), false)),
                        List.of(), false, RuntimeComponent.Health.UNKNOWN));
    }

    /**
     * The declaration of the bridge, which is the one crossing between the chains.
     *
     * @param health the health of the bridge
     * @return the declaration
     */
    public static RuntimeComponent bridge(RuntimeComponent.Health health) {
        return new RuntimeComponent(BRIDGE, RuntimeComponent.Kind.BRIDGE,
                List.of(new Capability(CAPABILITY_BRIDGE_INGEST, BRIDGE,
                        "io.matrix.bridge.BridgeService",
                        "accepts an observation from the brain runtime and records it as an"
                                + " unverified observation of the epistemic store",
                        List.of("the signature did not verify", "the budget was exhausted",
                                "the contract version is not supported"), true)),
                List.of(EPISTEMIC_STORE), false, health);
    }

    /**
     * The declaration of the brain runtime, when it answered a handshake.
     *
     * @param health the health the peer reported
     * @return the declaration
     */
    public static RuntimeComponent brainRuntime(RuntimeComponent.Health health) {
        return new RuntimeComponent(BRAIN_RUNTIME, RuntimeComponent.Kind.REASONING,
                List.of(new Capability(CAPABILITY_BRAIN_THINK, BRAIN_RUNTIME,
                        "io.matrix.brain.runtime.TrueMindCycle",
                        "runs the true mind cycle over an input and reports what it concluded",
                        List.of("the harmful-intent gate refused the input"), false)),
                List.of(), true, health);
    }
}
