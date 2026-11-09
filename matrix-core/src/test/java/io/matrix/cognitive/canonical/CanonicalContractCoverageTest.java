package io.matrix.cognitive.canonical;

import io.matrix.cognitive.CognitiveKernel;
import io.matrix.cognitive.CognitiveOutcome;
import io.matrix.cognitive.CognitiveSignal;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.KernelRegulator;
import io.matrix.cognitive.KernelSelector;
import io.matrix.cognitive.MicroWorldReferenceMethod;
import io.matrix.cognitive.MicroWorldSettledModelMethod;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.contract.CognitiveCycle;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W52 §3 — the coverage of the canonical contracts.
 *
 * <p><b>What this test is for.</b> W51 measured that fourteen of the canonical contracts
 * had no producer in the runtime: they were a vocabulary the system never filled, and a
 * vocabulary nobody fills is a shape without a meaning. The acceptance for this wave is not
 * a claim in a report — it is <b>this test</b>, which drives the real cognitive cycle and
 * asks the canonical form of what it produced which contracts it carries.</p>
 *
 * <p><b>Why it is an end-to-end test and not a unit test.</b> A unit test of the mapper would
 * prove that the mapper can build a record; this proves that <b>the runtime</b> produces the
 * facts the mapper needs. A contract that only a hand-built fact can fill is not a contract
 * the system has.</p>
 *
 * <p><b>Why the cycle walks the whole domain (W53).</b> The procedure and the skill come from
 * the procedural memory, and the memory fills when the model settles — that is, when the
 * system has observed every assignment the environment has. The test therefore asks about
 * all four assignments, in order, and lets the system settle on its own; nothing is seeded
 * into the store. In W52 this test had to SEED a promoted procedure, because the wave's
 * promotion rule refused to write anything without independent evidence, and the micro-world
 * cannot produce any. W53 changed the rule: the procedure of the settled model is written
 * down with the standing it actually has, and the test now proves that the runtime itself
 * fills the two contracts.</p>
 */
class CanonicalContractCoverageTest {

    /** The contracts the mandate names. */
    private static final List<String> THE_MANDATE_CONTRACTS = List.of(
            "Entity", "Relation", "Property", "WorldState", "StateTransition", "Event",
            "Observation", "Evidence", "Belief", "Hypothesis", "Prediction", "Experiment",
            "Action", "Outcome", "Goal", "Task", "Skill", "Procedure", "Algorithm", "Plugin",
            "Evaluation", "Reflection");

    /** The assignment every cycle asks about. */
    private static final String ASSIGNMENT = "{\"power\":true,\"switch\":true}";

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;
    private final AtomicInteger clock = new AtomicInteger();

    @BeforeEach
    void setUp() {
        Path database = directory.resolve("cognitive.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        store = new CognitiveStore(database);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
        if (workbench != null) {
            workbench.close();
        }
    }

    /** The assignments the environment has. */
    private static final List<String> THE_ASSIGNMENTS = List.of(
            "{\"power\":false,\"switch\":false}",
            "{\"power\":false,\"switch\":true}",
            "{\"power\":true,\"switch\":false}",
            "{\"power\":true,\"switch\":true}");

    /**
     * The world that disagrees twice, so that the system has something to validate against.
     */
    private static final class ContradictingEnvironment implements MicroWorldEnvironment {

        private final LampEnvironment delegate = new LampEnvironment();
        private int contradictionsLeft;

        ContradictingEnvironment(int contradictions) {
            this.contradictionsLeft = contradictions;
        }

        @Override
        public Observation probe(WorldInput input) {
            Observation observation = delegate.probe(input);
            if (contradictionsLeft > 0) {
                contradictionsLeft--;
                return new Observation(observation.id(), observation.input(), !observation.lamp(),
                        false, observation.observedAt());
            }
            return observation;
        }

        @Override
        public java.util.Set<String> variables() {
            return delegate.variables();
        }

        @Override
        public String outcomeVariable() {
            return delegate.outcomeVariable();
        }
    }

    private CognitiveKernel kernel(MicroWorldEnvironment environment) {
        return new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                new KernelRegulator(),
                () -> "2026-11-09T00:00:0" + (clock.incrementAndGet() % 10) + "Z");
    }

    @Test
    @DisplayName("the cognitive cycle fills the canonical contracts the mandate names")
    void theCycleFillsTheCanonicalContracts() {
        MicroWorldEnvironment environment = new LampEnvironment();
        CognitiveKernel kernel = kernel(environment);

        List<CognitiveCycle> cycles = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            CognitiveOutcome outcome = kernel.process(CognitiveSignal.structured(
                    "sig-canon-" + index, THE_ASSIGNMENTS.get(index % THE_ASSIGNMENTS.size()),
                    "the coverage run", index + 1L));
            if (outcome.canonical() != null) {
                cycles.add(outcome.canonical());
            }
        }

        // The PROCEDURE and the SKILL come from the procedural memory, and NOTHING was
        // seeded into it: the system settled on one hypothesis, wrote its procedure down,
        // and the later cycles found it. That is the W53 acceptance — the two contracts
        // are filled by the runtime's own record, not by a test's.
        CognitiveCycle withProcedure = cycles.stream()
                .filter(cycle -> cycle.produced("Procedure"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no cycle carried a procedure: the runtime did not fill the contract"));
        assertThat(withProcedure.produced("Skill")).isTrue();
        assertThat(withProcedure.procedure().parameters())
                .containsEntry("validationKind", "IN_SAMPLE");
        assertThat(withProcedure.procedure().parameters().get("learnedInEpisode"))
                .isNotBlank();
        assertThat(withProcedure.skill().reusable()).isTrue();
        assertThat(withProcedure.skill().learnedFromEpisodeIds()).isNotEmpty();

        // The PLUGIN contract is not produced by a cognitive cycle — it is produced by the
        // plugin registry, which is the runtime path that owns it.
        io.matrix.plugin.PluginAudit audit = new io.matrix.plugin.PluginAudit();
        io.matrix.plugin.PluginRegistry registry = new io.matrix.plugin.PluginRegistry(audit);
        io.matrix.plugin.CreatorMode creator =
                new io.matrix.plugin.CreatorMode(new io.matrix.plugin.DeclarativeSandbox());
        io.matrix.plugin.CreatorMode.CapabilityGap gap = new io.matrix.plugin.CreatorMode.CapabilityGap(
                "gap-coverage", "reasoning.threshold",
                "decides whether a measured score is high enough",
                "a map with the field score", "a map with the field verdict",
                "score", 0.75, true, "verdict", List.of("read.score"),
                List.of(new io.matrix.plugin.PluginArtifact.Case("above",
                                Map.of("score", 0.9), Map.of("verdict", true)),
                        new io.matrix.plugin.PluginArtifact.Case("below",
                                Map.of("score", 0.1), Map.of("verdict", false))),
                "the coverage run needed a threshold rule");
        io.matrix.plugin.CreatorMode.CreationResult created =
                creator.create(gap, registry, List.of("read.score"), "2026-11-09T00:00:00Z");
        assertThat(created.manifest()).isNotNull();
        assertThat(created.manifest().id()).isNotBlank();

        Set<String> produced = new LinkedHashSet<>();
        for (CognitiveCycle cycle : cycles) {
            for (String contract : THE_MANDATE_CONTRACTS) {
                if (cycle.produced(contract)) {
                    produced.add(contract);
                }
            }
        }
        produced.add("Plugin");

        assertThat(produced).as("the canonical contracts the runtime fills")
                .containsAll(THE_MANDATE_CONTRACTS);
    }

    @Test
    @DisplayName("the canonical form carries the facts of the cycle, not placeholders")
    void theCanonicalFormCarriesTheFacts() {
        ContradictingEnvironment environment = new ContradictingEnvironment(2);
        CognitiveKernel kernel = kernel(environment);

        CognitiveCycle cycle = null;
        for (int index = 0; index < 12 && cycle == null; index++) {
            CognitiveOutcome outcome = kernel.process(CognitiveSignal.structured(
                    "sig-canon-one-" + index, ASSIGNMENT, "the coverage run", index + 1L));
            if (outcome.canonical() != null
                    && outcome.status() == CognitiveOutcome.Status.COMPLETED) {
                cycle = outcome.canonical();
            }
        }
        assertThat(cycle).as("a completed cycle produced a canonical form").isNotNull();

        assertThat(cycle.goal()).isNotNull();
        assertThat(cycle.goal().id()).isNotBlank();
        assertThat(cycle.goal().successCriteria()).isNotEmpty();
        assertThat(cycle.task()).isNotNull();
        assertThat(cycle.task().goalId()).isEqualTo(cycle.goal().id());
        assertThat(cycle.algorithm()).isNotNull();
        assertThat(cycle.algorithm().id()).isNotBlank();
        assertThat(cycle.algorithm().family()).isNotBlank();
        assertThat(cycle.reflection()).isNotNull();
        assertThat(cycle.evaluation()).isNotNull();
        assertThat(cycle.evaluation().metrics()).isNotEmpty();
        assertThat(cycle.correlation()).isNotNull();
        assertThat(cycle.correlation().episodeId()).isEqualTo(cycle.episodeId());
        assertThat(cycle.provenance()).isNotNull();
        assertThat(cycle.provenance().sourceId()).isNotBlank();
        assertThat(cycle.event()).isNotNull();
        assertThat(cycle.event().kind()).isEqualTo(
                CanonicalCycleBuilder.EVENT_KIND_COGNITIVE_CYCLE);
        assertThat(cycle.hypotheses()).isNotEmpty();
        for (io.matrix.contract.Hypothesis hypothesis : cycle.hypotheses()) {
            assertThat(hypothesis.proposition()).isNotBlank();
            assertThat(hypothesis.confidence()).isNotNull();
        }
        assertThat(cycle.worldState()).isNotNull();
        assertThat(cycle.worldState().entities()).isNotEmpty();
        assertThat(cycle.worldState().properties()).isNotEmpty();
        assertThat(cycle.worldState().relations()).isNotEmpty();
    }
}
