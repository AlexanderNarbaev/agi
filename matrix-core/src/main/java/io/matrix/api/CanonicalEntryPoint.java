package io.matrix.api;

import java.util.List;
import java.util.Optional;

/**
 * W45.2 — the canonical entry point of each serving application.
 *
 * <h2>Why this class exists</h2>
 * <p>RECON-W44.0's bytecode archaeology found that MATRIX has no single canonical cognitive
 * cycle: five cycles coexist, none delegates to the others, and they live in two serving
 * applications that do not meet. The ADR that named {@code TrueMindCycle} the conductor was
 * written before the bytecode was examined, and it reads as if one cycle governed the whole
 * system — which the compiled code says otherwise.</p>
 *
 * <p>The owner's decision (W45.2) is <b>not</b> to merge the cycles and <b>not</b> to declare a
 * global conductor. It is to keep the five cycles, each with its distinct responsibility, and
 * to <b>name the canonical entry point of each actual serving application</b>. This class is
 * that naming, in code, so that it is a fact a test can check and a reader can look up —
 * rather than a sentence in a document that drifts.</p>
 *
 * <h2>What the declaration asserts, and what it does not</h2>
 * <p>It asserts <b>which entry point serves which application, and which cycles that entry
 * point runs</b>. It does not assert that the cycles are merged, that one cycle is global, or
 * that the two applications ever meet — they do not, and Decision 2 of the same wave keeps
 * them apart.</p>
 *
 * <h2>Why the classes are named by NAME and not by reference</h2>
 * <p>Chain B's entry point lives in {@code matrix-api-gateway} and its cycle in
 * {@code matrix-brain-runtime}; this class lives in {@code matrix-core}, which both depend on.
 * A direct reference would make {@code matrix-core} depend on the gateway — the wrong way
 * round, and a cycle between the modules. The names are therefore strings, and a test
 * resolves each one it can reach, so a typo cannot hide.</p>
 */
public final class CanonicalEntryPoint {

    /** What a declared cycle is, in the architecture. */
    public enum Kind {
        /**
         * The canonical entry point of a serving application: the path a user request
         * actually takes.
         */
        SERVING_APPLICATION,
        /**
         * A specialist loop: it does one job inside a subsystem and is reached from the
         * serving path or from the command line. It is RETAINED, not merged away.
         */
        SPECIALIST_LOOP,
        /**
         * A research or simulation loop: it is measured rather than served, and it has no
         * production caller.
         */
        RESEARCH_CLUSTER
    }

    /**
     * One declared entry point or loop.
     *
     * @param kind           what the declaration is
     * @param application    the serving application it belongs to, or the subsystem
     * @param entryPointClass the class a request enters through, by name
     * @param routes         the routes that reach it
     * @param cycleClasses   the cycles it runs, by name
     * @param note           what the declaration rests on
     */
    public record Declaration(Kind kind, String application, String entryPointClass,
                               String routes, String cycleClasses, String note) {

        /** A declaration for a serving application. */
        public static Declaration serving(String application, String entryPointClass,
                                           String routes, String cycleClasses, String note) {
            return new Declaration(Kind.SERVING_APPLICATION, application, entryPointClass,
                    routes, cycleClasses, note);
        }

        /** A declaration for a specialist loop. */
        public static Declaration specialist(String application, String cycleClass, String note) {
            return new Declaration(Kind.SPECIALIST_LOOP, application, cycleClass, "",
                    cycleClass, note);
        }

        /** A declaration for a research loop. */
        public static Declaration research(String application, String cycleClass, String note) {
            return new Declaration(Kind.RESEARCH_CLUSTER, application, cycleClass, "",
                    cycleClass, note);
        }
    }

    /** Chain A — the epistemic and workbench application, in matrix-core. */
    public static final String EPISTEMIC_APPLICATION = "the epistemic and workbench application";

    /** Chain B — the brain runtime, behind the hand-written HTTP server. */
    public static final String BRAIN_RUNTIME_APPLICATION = "the brain runtime";

    private static final List<Declaration> DECLARATIONS = List.of(
            Declaration.serving(EPISTEMIC_APPLICATION,
                    "io.matrix.knowledge.LearningWorkbenchResource",
                    "/v1/workbench/**",
                    "io.matrix.cognitive.CognitiveKernel",
                    "the learning loop of the W44 kernel, driven by the workbench and by "
                            + "/v1/cognitive/**; the cognitive kernel is reached through "
                            + "CognitiveResource, and the workbench resource is the other "
                            + "entry point of the same application"),
            Declaration.serving(EPISTEMIC_APPLICATION,
                    "io.matrix.api.OpenAIChatResource",
                    "/v1/chat/completions",
                    "io.matrix.reasoning.BrainLoopService",
                    "the nine-stage loop that OpenAIChatResource drives. It is a SECOND "
                            + "serving path inside the same application, and naming it is "
                            + "the point: the application runs two loops, and pretending "
                            + "otherwise would be the same error in the other direction"),
            Declaration.serving(BRAIN_RUNTIME_APPLICATION,
                    "io.matrix.api.MinimalHttpServer",
                    "POST /v1/analyze",
                    "io.matrix.brain.runtime.TrueMindCycle",
                    "the hand-written HTTP server of the gateway, which builds the "
                            + "ProductionBrainClient, which builds the TrueMindCycle. The "
                            + "gateway's own JAX-RS facade is NOT this path: it is unmounted "
                            + "and constructs a stub cycle"),
            Declaration.specialist(EPISTEMIC_APPLICATION, "io.matrix.brain.BirBrainCycle",
                    "the BIR/HDC/Tsetlin/MCTS portfolio. The TrueMindCycle already delegates "
                            + "to it through its BirInferenceStage, so it is a specialist of "
                            + "the brain runtime's cycle and a method of the kernel's "
                            + "portfolio"),
            Declaration.specialist(EPISTEMIC_APPLICATION, "io.matrix.reasoning.ConsciousnessLoop",
                    "the loop that io.matrix.reasoning.BrainLoopService drives; one caller"),
            Declaration.specialist(EPISTEMIC_APPLICATION, "io.matrix.consciousness.BrainLoopService",
                    "the loop of the RUN wave. Its production callers are all inside "
                            + "io.matrix.consciousness, a package of nearly three hundred "
                            + "types of which only eleven are served, so the caller count "
                            + "measures the package rather than a serving path"),
            Declaration.specialist("the brain runtime", "io.matrix.brain.runtime.MindCycle",
                    "the two-phase loop of the brain runtime, reached from the command line"),
            Declaration.research("the research cluster", "io.matrix.neuron.ConsciousBrain",
                    "the measurement cluster: inactive-but-tested, zero production callers. "
                            + "Its metrics, its profile builders and BrainLoopServiceV2 hang "
                            + "off it, which is why the callers of that loop do not "
                            + "make it a serving path"));

    private CanonicalEntryPoint() {
    }

    /**
     * Every declaration.
     *
     * @return the declarations, in the order they are read
     */
    public static List<Declaration> declarations() {
        return DECLARATIONS;
    }

    /**
     * The serving applications and their canonical entry points.
     *
     * @return the declarations of kind {@link Kind#SERVING_APPLICATION}
     */
    public static List<Declaration> servingApplications() {
        return DECLARATIONS.stream()
                .filter(declaration -> declaration.kind() == Kind.SERVING_APPLICATION)
                .toList();
    }

    /**
     * The declarations of one application.
     *
     * @param application the application's name
     * @return its declarations
     */
    public static List<Declaration> of(String application) {
        return DECLARATIONS.stream()
                .filter(declaration -> declaration.application().equals(application))
                .toList();
    }

    /**
     * The declaration of the serving application with the given name.
     *
     * @param application the application's name
     * @return its first declaration, or empty when there is none
     */
    public static Optional<Declaration> canonicalOf(String application) {
        return of(application).stream()
                .filter(declaration -> declaration.kind() == Kind.SERVING_APPLICATION)
                .findFirst();
    }

    /**
     * The classes this declaration names, as names.
     *
     * @param declaration the declaration
     * @return the entry point class and the cycle classes
     */
    public static List<String> classesOf(Declaration declaration) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (declaration.entryPointClass() != null && !declaration.entryPointClass().isBlank()) {
            names.add(declaration.entryPointClass());
        }
        for (String cycle : declaration.cycleClasses().split(",")) {
            String trimmed = cycle.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return List.copyOf(names);
    }
}
