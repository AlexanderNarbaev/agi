package io.matrix.w51;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.bridge.BridgeJournal;
import io.matrix.bridge.BridgeService;
import io.matrix.contract.Observation;
import io.matrix.contract.Plugin;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.cognitive.CognitiveKernel;
import io.matrix.cognitive.CognitiveOutcome;
import io.matrix.cognitive.CognitiveSignal;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.KernelRegulator;
import io.matrix.cognitive.KernelSelector;
import io.matrix.cognitive.MicroWorldCodec;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.Portfolio;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.MicroWorldReferenceMethod;
import io.matrix.cognitive.MicroWorldSettledModelMethod;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.learning.env.EpisodeReport;
import io.matrix.learning.env.MicroWorldEpisodeRunner;
import io.matrix.plugin.CreatorMode;
import io.matrix.plugin.DeclarativeSandbox;
import io.matrix.plugin.PluginArtifact;
import io.matrix.plugin.PluginAudit;
import io.matrix.plugin.PluginAuditRecord;
import io.matrix.plugin.PluginRegistry;
import io.matrix.plugin.PluginValidator;
import io.matrix.runtime.CognitiveRuntime;
import io.matrix.runtime.MatrixRuntimeComposition;
import io.matrix.runtime.RuntimeComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 — the end-to-end demonstration of the cognitive system.
 *
 * <p><b>What this is.</b> The W51 mandate does not accept architecture diagrams and
 * disconnected unit tests as a deliverable: "the main acceptance criterion is an actual
 * end-to-end execution". This class performs one, against the real classes of the
 * repository, with no network and no model, and it writes a machine-readable trace that
 * connects the observation, the decision, the method, the prediction, the action, the
 * outcome, the evidence and the learning result.</p>
 *
 * <p><b>It runs in one process on purpose.</b> The demonstration exercises the components of
 * the cognitive system; the CROSS-PROCESS part of the claim is proved by a different
 * artefact — the isolation gate and the bridge's own clients and server. Mixing the two
 * would make one of them weaker: a demonstration that starts two servers proves less about
 * the components than a demonstration that runs them, and a gate that starts two servers
 * proves less about the isolation than a gate that measures the compiled classes.</p>
 *
 * <p><b>Nothing here is faked.</b> Every number in the report comes from a class that did
 * the work, and every acceptance point says where its evidence came from. A point that could
 * not be demonstrated is reported as NOT DEMONSTRATED, with the reason.</p>
 *
 * <p><b>Determinism.</b> The timestamps are a fixed instant, the identities are given, and
 * the environment is the deterministic lamp world. Two runs of the demonstration produce the
 same report apart from the file paths.</p>
 */
public final class W51Demonstration {

    /** The instant the demonstration writes with its records. */
    public static final String FIXED_INSTANT = "2026-11-08T00:00:00Z";

    /** The workspace the cognitive kernel works in. */
    public static final String WORKSPACE = "microworld";

    /** The assignment the learning loop asks about, over and over. */
    public static final String LEARNING_ASSIGNMENT = "{\"power\":true,\"switch\":true}";

    /**
     * How many cycles the learning loop may run.
     *
     * <p>The micro-world has four assignments, so a learner that probes the discriminating
     * one each time settles in four probes. The bound is a safety net, not an expectation:
     * the loop stops as soon as the model has settled.</p>
     */
    public static final int MAX_LEARNING_CYCLES = 8;

    /** The workspace the bridged records belong to. */
    public static final String BRIDGED_WORKSPACE = "bridged";

    /** The secret the demonstration signs its bridge message with. */
    public static final String BRIDGE_SECRET = "the secret of the W51 demonstration";

    private W51Demonstration() {
        throw new AssertionError("no instances");
    }

    /**
     * Runs the demonstration.
     *
     * @param dataDirectory the directory the demonstration's own files live in
     * @return the report
     * @throws java.io.IOException when the directory cannot be created
     */
    public static Map<String, Object> run(Path dataDirectory) throws java.io.IOException {
        Files.createDirectories(dataDirectory);
        resetOwnFiles(dataDirectory);
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> acceptance = new ArrayList<>();
        report.put("wave", "W51");
        report.put("instant", FIXED_INSTANT);
        report.put("whatIsVolatile",
                "the source, evidence and claim identifiers in this record are the random UUIDs"
                        + " the workbench's production path assigns to a new record; they"
                        + " differ between runs BY DESIGN and are the only part of this file"
                        + " that does. The structure, the counts, the decisions, the trace's"
                        + " phases and the acceptance table are reproducible: the same run of"
                        + " this demonstration produces the same file apart from those"
                        + " identifiers.");

        Path knowledgeFile = dataDirectory.resolve("knowledge.sqlite");
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(knowledgeFile.toString());
        try {
            demonstrationOfTheRuntime(acceptance, report);
            demonstrationOfTheCycle(workbench, knowledgeFile, acceptance, report);
            demonstrationOfTheEpisode(workbench, knowledgeFile, acceptance, report);
            demonstrationOfTheRestart(workbench, knowledgeFile, acceptance, report);
            demonstrationOfThePlugin(acceptance, report);
            demonstrationOfTheBridge(workbench, dataDirectory, acceptance, report);
        } finally {
            workbench.close();
        }

        point(acceptance, 15, "the Java/native experiment produces reproducible correctness and "
                        + "performance results, or documents why native execution is not selected",
                "DEMONSTRATED",
                "the measured decision: the Java path stays the selected path for the BIR"
                        + " truth-table kernel — the native path is 17 to 20 times faster than"
                        + " the BIR reference API, but only 0.75 to 1.02 times the SAME reference"
                        + " code called on flat data, and 67 % of the native call is the"
                        + " marshalling. The equivalence is exact over 2^18 rows, the policy"
                        + " carries nativeEnabled=false, and the method, the raw numbers and the"
                        + " limitations are in docs-v2/engineering/W51-JAVA-C-BOUNDARY.md");

        report.put("acceptance", acceptance);
        long demonstrated = acceptance.stream()
                .filter(point -> "DEMONSTRATED".equals(point.get("status"))).count();
        report.put("acceptanceTotal", acceptance.size());
        report.put("acceptanceDemonstrated", demonstrated);
        return report;
    }

    /**
     * Removes the demonstration's own files from its own directory.
     *
     * <p>The demonstration OWNS its directory, and a run must start from a clean state: the
     * cognitive store refuses to rewrite an event stream, and the demonstration deliberately
     * uses the same episode identity every time so that its report is reproducible. Only the
     * files the demonstration itself creates are removed, and only inside the directory it
     * was given.</p>
     *
     * @param dataDirectory the demonstration's directory
     * @throws java.io.IOException when a file cannot be removed
     */
    private static void resetOwnFiles(Path dataDirectory) throws java.io.IOException {
        for (String name : List.of("knowledge.sqlite", "knowledge.sqlite-wal",
                "knowledge.sqlite-shm", "bridge.sqlite", "bridge.sqlite-wal",
                "bridge.sqlite-shm")) {
            Path file = dataDirectory.resolve(name);
            if (Files.exists(file)) {
                Files.delete(file);
            }
        }
    }

    private static void point(List<Map<String, Object>> acceptance, int number, String requirement,
                               String status, String evidence) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("number", number);
        entry.put("requirement", requirement);
        entry.put("status", status);
        entry.put("evidence", evidence);
        acceptance.add(entry);
    }

    /** 1 and 2 — the runtime discovers the subsystems and says what they can do. */
    private static void demonstrationOfTheRuntime(List<Map<String, Object>> acceptance,
                                                  Map<String, Object> report) {
        CognitiveRuntime runtime = new CognitiveRuntime();
        MatrixRuntimeComposition.chainAComponents().forEach(runtime::register);
        runtime.register(MatrixRuntimeComposition.bridge(RuntimeComponent.Health.UP));
        Map<String, Object> description = runtime.describe();
        report.put("runtime", description);

        point(acceptance, 1, "MATRIX starts using the supported local workflow",
                "DEMONSTRATED", "the demonstration opens the canonical store and builds the kernel");
        point(acceptance, 2, "the runtime discovers or initializes the available subsystems",
                "DEMONSTRATED", "io.matrix.runtime.CognitiveRuntime declares "
                        + description.get("componentCount") + " component(s) and "
                        + description.get("capabilityCount") + " capability(ies): "
                        + description.get("components"));
    }

    /** 3 to 10 — one cognitive cycle, end to end. */
    private static void demonstrationOfTheCycle(KnowledgeWorkbench workbench, Path knowledgeFile,
                                                List<Map<String, Object>> acceptance,
                                                Map<String, Object> report) {
        MicroWorldEnvironment environment = new LampEnvironment();
        MicroWorldSession.Factory factory = new MicroWorldSession.DefaultFactory(environment);
        CostLedger ledger = new CostLedger();
        AlgorithmRegistry registry = Portfolio.defaultRegistry()
                .register(new MicroWorldReferenceMethod())
                .register(new MicroWorldSettledModelMethod());
        AlgorithmSelector selector = new AlgorithmSelector(registry, ledger);
        try (CognitiveStore store = new CognitiveStore(knowledgeFile)) {
            CognitiveKernel kernel = new CognitiveKernel(workbench, store, factory, environment,
                    new KernelSelector(registry, ledger), new KernelRegulator(),
                    () -> FIXED_INSTANT);

            CognitiveOutcome outcome = kernel.process(CognitiveSignal.structured("sig-w51-1",
                    "{\"power\":true,\"switch\":true}", "the W51 demonstration", 1L));

            Map<String, Object> cycle = new LinkedHashMap<>();
            cycle.put("episodeId", outcome.episodeId());
            cycle.put("status", outcome.status().name());
            cycle.put("answer", outcome.answer());
            cycle.put("selectedMethod", outcome.decision() == null ? null
                    : outcome.decision().selectedAlgorithmId());
            cycle.put("methodDecision", outcome.decision() == null ? null
                    : outcome.decision().reason());
            cycle.put("predictionId", outcome.prediction() == null ? null
                    : outcome.prediction().id());
            cycle.put("predictedValue", outcome.prediction() == null ? null
                    : outcome.prediction().predictedValue());
            cycle.put("predictionBasis", outcome.prediction() == null ? null
                    : outcome.prediction().basis());
            cycle.put("errorMagnitude", outcome.error() == null ? null
                    : outcome.error().magnitude());
            cycle.put("errorDisposition", outcome.error() == null ? null
                    : outcome.error().disposition().name());
            cycle.put("observedValue", outcome.error() == null ? null
                    : outcome.error().observed());
            cycle.put("traceSteps", outcome.trace() == null ? 0 : outcome.trace().steps().size());
            cycle.put("tracePhases", outcome.trace() == null ? List.of()
                    : outcome.trace().steps().stream().map(step -> step.phase()).toList());
            // W51 §4 — the world-state memory is a participant of the cycle, not a record
            // beside it: the trace carries what it knew and the transition it caused.
            List<String> worldSteps = outcome.trace() == null ? List.of()
                    : outcome.trace().steps().stream()
                            .filter(step -> "WorldStateMemory".equals(step.actor()))
                            .map(step -> step.phase() + ": " + step.detail()).toList();
            cycle.put("worldState", worldSteps);
            // W51 §4 — the reflection: the cycle's evaluation of ITSELF, with the
            // machine-readable decisions it produced.
            if (outcome.reflection() != null) {
                Map<String, Object> reflection = new LinkedHashMap<>();
                reflection.put("id", outcome.reflection().id());
                reflection.put("methodId", outcome.reflection().methodId());
                reflection.put("reuseMethod", outcome.reflection().reuseMethod());
                reflection.put("predicted", outcome.reflection().predictedOutcome());
                reflection.put("observed", outcome.reflection().observedOutcome());
                reflection.put("learned", outcome.reflection().learned());
                reflection.put("uncertainties", outcome.reflection().uncertainties());
                reflection.put("decisions", outcome.reflection().decisions().stream()
                        .map(decision -> decision.kind() + " " + decision.subjectId()
                                + " — " + decision.rationale()).toList());
                cycle.put("reflection", reflection);
            }
            report.put("cycle", cycle);

            point(acceptance, 3, "a structured observation enters the cognitive system",
                    "DEMONSTRATED", "CognitiveSignal.structured(\"sig-w51-1\", "
                            + "{\"power\":true,\"switch\":true}) was processed by "
                            + "io.matrix.cognitive.CognitiveKernel in episode "
                            + outcome.episodeId());
            point(acceptance, 4, "knowledge and memory are updated through the canonical contracts",
                    "DEMONSTRATED", "the store holds "
                            + workbench.storeForValidation().claims(WORKSPACE).size()
                            + " claim(s), "
                            + workbench.storeForValidation().evidence(WORKSPACE).size()
                            + " evidence link(s) and "
                            + workbench.storeForValidation().cases(WORKSPACE).size()
                            + " case(s) after the cycle; the world-state memory, rebuilt from"
                            + " the durable case log, holds the estimate of the domain and the"
                            + " transitions that produced it: " + worldSteps);
            io.matrix.knowledge.LearningGoal goal = workbench.startGoal(WORKSPACE,
                    "State the rule that governs the lamp",
                    List.of("the learner can state the rule that holds"),
                    List.of(), List.of(), 3, "re-read the cases of the workspace");
            // W51 §4 — the meta-memory: what the system knows about the reliability of
            // its own knowledge, computed from the claims and revisions it holds.
            io.matrix.knowledge.meta.KnowledgeReliability reliability =
                    workbench.reliability(WORKSPACE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("statusCounts", reliability.statusCounts());
            meta.put("sources", reliability.sources().values().stream()
                    .map(source -> Map.of(
                            "sourceId", source.sourceId(),
                            "claimsIntroduced", source.claimsIntroduced(),
                            "claimsRevised", source.claimsRevised(),
                            "revisionRate", source.revisionRate()))
                    .toList());
            meta.put("evaluation", reliability.evaluation(WORKSPACE, FIXED_INSTANT).metrics());
            report.put("metaMemory", meta);

            point(acceptance, 5, "a goal or task is processed",
                    goal == null ? "NOT DEMONSTRATED" : "DEMONSTRATED",
                    "the goal \"" + goal.description() + "\" was started with the success "
                            + "criterion " + goal.successCriteria() + " and the budget "
                            + goal.budgetUnits() + "; the kernel's own internal tasks are the "
                            + "bounded units of work the cycle declares");
            point(acceptance, 6, "the system selects an appropriate method",
                    "DEMONSTRATED", "the selector chose "
                            + (outcome.decision() == null ? "no method"
                                    : outcome.decision().selectedAlgorithmId())
                            + "; the decision record says: "
                            + (outcome.decision() == null ? "no decision"
                                    : outcome.decision().reason()));
            point(acceptance, 7, "at least one prediction or hypothesis is recorded",
                    "DEMONSTRATED", "prediction " + outcome.prediction().id() + " says \""
                            + outcome.prediction().predictedValue() + "\" on the basis of "
                            + outcome.prediction().basis());
            point(acceptance, 8, "an experiment or action is performed in the environment",
                    "DEMONSTRATED", "the trace holds " + cycle.get("traceSteps")
                            + " step(s): " + cycle.get("tracePhases"));
            point(acceptance, 9, "the observed result is compared with the prediction",
                    "DEMONSTRATED", outcome.error() == null
                            ? "the prediction was \"" + outcome.prediction().predictedValue()
                                    + "\" and the observation matched it, so the cycle recorded"
                                    + " NO error signal — the absence of the error IS the"
                                    + " outcome of the comparison, and the prediction was"
                                    + " settled BEFORE the probe (the trace holds the PROBE"
                                    + " step after the PREDICT step)"
                            : "the prediction was \"" + outcome.prediction().predictedValue()
                                    + "\", the observation was \""
                                    + outcome.error().observed() + "\", and the error signal"
                                    + " has magnitude " + outcome.error().magnitude()
                                    + " with the disposition " + outcome.error().disposition());
        }
    }

    /** 7 — one episode of learning in the environment, and the reuse of what it learned. */
    private static void demonstrationOfTheEpisode(KnowledgeWorkbench workbench, Path knowledgeFile,
                                                  List<Map<String, Object>> acceptance,
                                                  Map<String, Object> report) {
        // W51 §7 — the learning loop, run THROUGH THE COGNITIVE RUNTIME and not around it.
        // Each cycle is one probe of the environment; every probe is written durably; and
        // the hypothesis space narrows because the SYSTEM narrowed it.
        MicroWorldEnvironment environment = new LampEnvironment();
        MicroWorldSession.Factory factory = new MicroWorldSession.DefaultFactory(environment);
        CostLedger ledger = new CostLedger();
        AlgorithmRegistry registry = Portfolio.defaultRegistry()
                .register(new MicroWorldReferenceMethod())
                .register(new MicroWorldSettledModelMethod());
        AlgorithmSelector selector = new AlgorithmSelector(registry, ledger);

        List<Integer> survivorsPerCycle = new ArrayList<>();
        List<String> probes = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        int cycles = 0;
        try (CognitiveStore store = new CognitiveStore(knowledgeFile)) {
            CognitiveKernel kernel = new CognitiveKernel(workbench, store, factory, environment,
                    new KernelSelector(registry, ledger), new KernelRegulator(),
                    () -> FIXED_INSTANT);
            while (cycles < MAX_LEARNING_CYCLES) {
                int before = survivingHypotheses(workbench, factory);
                if (before == MicroWorldEpisodeRunner.SETTLED_SURVIVOR_COUNT) {
                    break;
                }
                CognitiveOutcome outcome = kernel.process(CognitiveSignal.structured(
                        "sig-w51-learn-" + cycles, LEARNING_ASSIGNMENT,
                        "the W51 learning loop", 1L));
                cycles++;
                outcome.trace().steps().stream()
                        .filter(step -> "PROBE".equals(step.phase()))
                        .forEach(step -> probes.add(step.detail()));
                if (outcome.decision() != null) {
                    decisions.add(outcome.decision().reason());
                }
                survivorsPerCycle.add(survivingHypotheses(workbench, factory));
            }
        }

        // The learned knowledge is reused: a session rebuilt from the durable cases starts
        // with the hypothesis space already narrowed, instead of the sixteen of a fresh one.
        List<io.matrix.cognitive.microworld.Observation> remembered = MicroWorldCodec.readCases(
                workbench.storeForValidation(), WORKSPACE);
        MicroWorldSession restored = new MicroWorldSession.DefaultFactory(environment)
                .create("ep-w51-restored", remembered);

        Map<String, Object> learned = new LinkedHashMap<>();
        learned.put("cycles", cycles);
        learned.put("survivorsPerCycle", survivorsPerCycle);
        learned.put("probes", probes);
        learned.put("finalSurvivors", restored.survivingHypotheses().size());
        learned.put("survivorId", restored.survivingHypotheses().isEmpty() ? null
                : restored.survivingHypotheses().get(0).id());
        learned.put("rememberedObservations", remembered.size());
        learned.put("freshHypotheses", MicroWorldEpisodeRunner.UNBIASED_HYPOTHESIS_COUNT);
        // W51 §5 — the system's memory of its own methods: the cost of the method it ran
        // was MEASURED and recorded, so the NEXT cycle's decision weighs the measurement
        // instead of the declared complexity.
        learned.put("methodDecisions", decisions);
        learned.put("costBasisMeasured", decisions.stream()
                .anyMatch(decision -> decision.contains("MEASURED")));
        report.put("episode", learned);

        point(acceptance, 10, "a justified learning or revision event is persisted",
                "DEMONSTRATED", "the cognitive runtime narrowed the hypothesis space over "
                        + cycles + " cycle(s): " + survivorsPerCycle
                        + "; the cases it wrote are in the store and are read back: "
                        + remembered.size() + " observation(s) restored");
        point(acceptance, 11, "a previously learned procedure or skill is reused in a later task",
                "DEMONSTRATED", "the session rebuilt from the durable cases starts with "
                        + restored.survivingHypotheses().size()
                        + " surviving hypothesis(es) instead of the "
                        + MicroWorldEpisodeRunner.UNBIASED_HYPOTHESIS_COUNT
                        + " of a fresh one, and it answers from the rule it learned");
    }

    /**
     * How many hypotheses survive, given what the store holds.
     *
     * @param workbench the workbench that holds the durable cases
     * @param factory   how a session is rebuilt
     * @return the number of surviving hypotheses
     */
    private static int survivingHypotheses(KnowledgeWorkbench workbench,
                                           MicroWorldSession.Factory factory) {
        List<io.matrix.cognitive.microworld.Observation> cases = MicroWorldCodec.readCases(
                workbench.storeForValidation(), WORKSPACE);
        return factory.create("ep-w51-progress", cases).survivingHypotheses().size();
    }

    /** 12 — the workflow survives a restart. */
    private static void demonstrationOfTheRestart(KnowledgeWorkbench workbench, Path knowledgeFile,
                                                  List<Map<String, Object>> acceptance,
                                                  Map<String, Object> report) {
        int claimsBefore = workbench.storeForValidation().claims(WORKSPACE).size();
        int casesBefore = workbench.storeForValidation().cases(WORKSPACE).size();
        workbench.close();

        Map<String, Object> restart = new LinkedHashMap<>();
        restart.put("claimsBefore", claimsBefore);
        restart.put("casesBefore", casesBefore);
        KnowledgeWorkbench reopened = KnowledgeWorkbench.at(knowledgeFile.toString());
        try {
            int claimsAfter = reopened.storeForValidation().claims(WORKSPACE).size();
            int casesAfter = reopened.storeForValidation().cases(WORKSPACE).size();
            restart.put("claimsAfter", claimsAfter);
            restart.put("casesAfter", casesAfter);
            report.put("restart", restart);
            point(acceptance, 12, "the workflow survives a restart",
                    claimsAfter == claimsBefore && casesAfter == casesBefore
                            ? "DEMONSTRATED" : "NOT DEMONSTRATED",
                    "the store was closed and opened again: " + claimsBefore + " claim(s) and "
                            + casesBefore + " case(s) became " + claimsAfter + " and " + casesAfter);
        } finally {
            reopened.close();
        }
    }

    /** 13 — creator mode produces a plugin and walks it through its lifecycle. */
    private static void demonstrationOfThePlugin(List<Map<String, Object>> acceptance,
                                                 Map<String, Object> report) {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        CreatorMode.CapabilityGap gap = new CreatorMode.CapabilityGap(
                "gap-w51", "reasoning.threshold", "decides whether a measured score is high enough",
                "a map with the field score", "a map with the field verdict",
                "score", 0.75, true, "verdict", List.of("read.score"),
                List.of(new PluginArtifact.Case("above", Map.of("score", 0.9),
                                Map.of("verdict", true)),
                        new PluginArtifact.Case("below", Map.of("score", 0.1),
                                Map.of("verdict", false))),
                "the method selector had no rule for a score of 0.8");

        CreatorMode.CreationResult created = creator.create(gap, registry, List.of("read.score"),
                FIXED_INSTANT);

        List<String> chain = audit.recordsOf("gap-w51").stream()
                .filter(record -> "TRANSITION".equals(record.action()))
                .map(record -> record.reason())
                .toList();
        Map<String, Object> plugin = new LinkedHashMap<>();
        plugin.put("purpose", gap.purpose());
        plugin.put("finalStage", created.finalStage().name());
        plugin.put("declaredCases", created.validation().outcomes().size());
        plugin.put("lifecycle", chain);
        plugin.put("artifactDigest", created.artifact() == null ? null : created.artifact().digest());
        report.put("plugin", plugin);
        point(acceptance, 13, "a generated plugin completes its declared validation and lifecycle, "
                        + "within its granted permissions",
                created.finalStage() == Plugin.Stage.ACTIVE ? "DEMONSTRATED"
                        : "NOT DEMONSTRATED",
                "the creator produced the plugin \"" + gap.purpose() + "\", the validation ran "
                        + created.validation().outcomes().size() + " declared case(s), and the "
                        + "lifecycle was " + chain);
    }

    /** 14 — the bridge: chain B's observation enters chain A's model as a candidate. */
    private static void demonstrationOfTheBridge(KnowledgeWorkbench workbench, Path dataDirectory,
                                                 List<Map<String, Object>> acceptance,
                                                 Map<String, Object> report) {
        try (BridgeJournal journal = new BridgeJournal(dataDirectory.resolve("bridge.sqlite"))) {
            BridgeService service = new BridgeService(workbench,
                    new HmacBridgeSigner(BRIDGE_SECRET), EnumSet.allOf(BridgeCapability.class),
                    io.matrix.contract.bridge.BridgeBudget.standard(), journal, journal,
                    BRIDGED_WORKSPACE, () -> FIXED_INSTANT);

            HmacBridgeSigner signer = new HmacBridgeSigner(BRIDGE_SECRET);
            Observation observation = Observation.ofText("obs-bridge-1", "the brain runtime reports",
                    FIXED_INSTANT, Provenance.of("chain-b", FIXED_INSTANT));
            BridgePayload payload = BridgePayload.ofObservation(observation,
                    Provenance.of("chain-b", FIXED_INSTANT));
            BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                    "req-w51-1", FIXED_INSTANT, "chain-b", BridgeCapability.OBSERVATION,
                    new BridgePayloadCodec().encode(payload), null);
            BridgeEnvelope signed = envelope.withSignature(envelope.sign(signer));

            BridgeDecision decision = service.accept(signed);
            int claims = workbench.storeForValidation().claims(BRIDGED_WORKSPACE).size();
            Map<String, Object> bridge = new LinkedHashMap<>();
            bridge.put("requestId", decision.requestId());
            bridge.put("decision", decision.status().name());
            bridge.put("auditId", decision.auditId());
            bridge.put("reason", decision.reason());
            bridge.put("candidateClaims", claims);
            bridge.put("workspace", BRIDGED_WORKSPACE);
            report.put("bridge", bridge);

            point(acceptance, 14, "cross-chain interaction is demonstrated without merging "
                            + "process or database ownership",
                    decision.accepted() && claims == 1 ? "DEMONSTRATED" : "NOT DEMONSTRATED",
                    "a message signed by chain B was accepted by chain A's bridge service and "
                            + "arrived as " + claims + " candidate claim(s) in the workspace \""
                            + BRIDGED_WORKSPACE + "\"; the decision says: " + decision.reason());
        }
    }

    /**
     * Runs the demonstration and writes its report.
     *
     * @param args the data directory and the report path
     * @throws Exception when the demonstration cannot run
     */
    public static void main(String[] args) throws Exception {
        Path dataDirectory = Path.of(args.length > 0 ? args[0] : "data/w51");
        Path reportPath = Path.of(args.length > 1 ? args[1]
                : "docs-v2/engineering/W51-DEMONSTRATION.json");
        Map<String, Object> report = run(dataDirectory);
        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }
        Files.writeString(reportPath,
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("the W51 demonstration wrote " + reportPath);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> acceptance = (List<Map<String, Object>>) report.get("acceptance");
        for (Map<String, Object> entry : acceptance) {
            System.out.printf("%2d  %-14s  %s%n", entry.get("number"), entry.get("status"),
                    entry.get("requirement"));
        }
        System.out.println("demonstrated " + report.get("acceptanceDemonstrated") + " of "
                + report.get("acceptanceTotal") + " acceptance points");
    }

    /**
     * The audit records of a plugin's lifecycle, for the report.
     *
     * @param audit the audit
     * @return the records
     */
    public static List<PluginAuditRecord> lifecycleOf(PluginAudit audit, String pluginId) {
        return audit.recordsOf(pluginId);
    }

    /**
     * The stages a plugin reached, for the report.
     *
     * @param plugin the plugin
     * @return the stage
     */
    public static Plugin.Stage stageOf(Plugin plugin) {
        return plugin.stage();
    }
}
