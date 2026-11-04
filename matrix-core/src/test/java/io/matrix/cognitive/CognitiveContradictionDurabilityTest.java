package io.matrix.cognitive;

import io.matrix.cognitive.microworld.HypothesisPrediction;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.0 — a contradiction must stay a contradiction.
 *
 * <p>The W44 wave left a defect that only a restart could show. A session that saw an
 * observation contradicting one of its own predictions deliberately did NOT apply that
 * observation to its hypothesis set: the cause of the disagreement was unresolved, and a model
 * that quietly adopts every contradicting datum cannot be wrong loudly. But the knowledge store
 * recorded the observation as a CASE and nothing else — no record of the DECISION — and the
 * session that rebuilt itself from those cases had no prediction history to compare against.
 * So the rebuilt session applied the contradiction the previous session had refused to apply,
 * and the model it produced was not the model that had been reasoned about.</p>
 *
 * <p>These tests are the proof of the fix. They drive the durable store, restart it, and check
 * three things: the contradiction is still visible, it is still held out of the active model,
 * and the reference computation reads the same data the model was built from.</p>
 */
class CognitiveContradictionDurabilityTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-03T00:00:00Z";

    /** The workspace the micro-world's records live in. */
    private static final String WORKSPACE = CognitiveKernel.WORKSPACE_ID;

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;
    private final LampEnvironment environment = new LampEnvironment();

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

    /**
     * A session whose model has SETTLED on the conjunction.
     *
     * <p>A contradiction is a disagreement with a BELIEF, so the model has to have one: the
     * session holds an observation only when the prediction it contradicts was unanimous.</p>
     *
     * @return a session with one surviving hypothesis
     */
    private MicroWorldSession settledSession() {
        MicroWorldSession session = new MicroWorldSession("ep-1", environment);
        for (WorldInput input : allAssignments()) {
            session.observe(new Observation("obs-" + input.key(), input,
                    input.get("power") && input.get("switch"), false, INSTANT));
        }
        assertThat(session.survivingHypotheses()).hasSize(1);
        return session;
    }

    /** The four assignments of the lamp world. */
    private List<WorldInput> allAssignments() {
        return List.of(
                MicroWorldCodec.parseAssignment("power=0,switch=0"),
                MicroWorldCodec.parseAssignment("power=0,switch=1"),
                MicroWorldCodec.parseAssignment("power=1,switch=0"),
                MicroWorldCodec.parseAssignment("power=1,switch=1"));
    }

    // ── the defect, and the fix ──────────────────────────────────────────────

    @Test
    @DisplayName("a session that sees a contradicting observation holds it out of its hypothesis set")
    void aSessionHoldsAContradictingObservation() {
        MicroWorldSession session = settledSession();
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=0,switch=0");
        HypothesisPrediction prediction = session.predict(assignment);
        assertThat(prediction.unanimous())
                .withFailMessage("the contradiction must be against a belief")
                .isTrue();

        Observation contradiction = new Observation("obs-c", assignment, !prediction.value(),
                false, INSTANT);
        session.observe(contradiction);

        assertThat(session.observations()).hasSize(5);
        assertThat(session.appliedObservations())
                .withFailMessage("the contradiction must not constrain the model")
                .hasSize(4);
        assertThat(session.quarantinedObservations()).hasSize(1);
        assertThat(session.survivingHypotheses())
                .withFailMessage("the hypothesis set must be left standing")
                .hasSize(1);
        assertThat(session.predictionErrors()).hasSize(1);
    }

    @Test
    @DisplayName("the W44 restoration path applied a held contradiction — the characterisation of the defect")
    void theW44RestorationPathAppliedAHeldContradiction() {
        // This is the defect, kept as a test so that it cannot come back unnoticed. The W44
        // codec read every case as an ordinary observation, with no disposition, so the
        // rebuilt session had no way to know that the contradiction had been held.
        MicroWorldSession live = settledSession();
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=0,switch=0");
        HypothesisPrediction prediction = live.predict(assignment);
        live.observe(new Observation("obs-c", assignment, !prediction.value(), false, INSTANT));
        assertThat(live.survivingHypotheses()).hasSize(1);

        List<Observation> asW44ReadThem = new ArrayList<>();
        for (WorldInput input : allAssignments()) {
            asW44ReadThem.add(new Observation("obs-" + input.key(), input,
                    input.get("power") && input.get("switch"), false, INSTANT));
        }
        asW44ReadThem.add(new Observation("obs-c", assignment, !prediction.value(), false, INSTANT));

        MicroWorldSession rebuilt = new MicroWorldSession.DefaultFactory(environment)
                .create("ep-2", asW44ReadThem);

        assertThat(rebuilt.appliedObservations())
                .withFailMessage("the W44 path applied the contradiction: this is the defect")
                .hasSize(5);
        assertThat(rebuilt.survivingHypotheses())
                .withFailMessage("the W44 path destroyed the model with a held contradiction")
                .isEmpty();
    }

    @Test
    @DisplayName("a held contradiction survives a restart and stays out of the active model")
    void aHeldContradictionSurvivesARestart() {
        // 1. a model with a SETTLED prediction
        MicroWorldSession session = settledSession();
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=0,switch=0");
        HypothesisPrediction prediction = session.predict(assignment);

        // 2. an observation that contradicts it
        Observation contradiction = new Observation("obs-c", assignment, !prediction.value(),
                false, INSTANT);
        session.observe(contradiction);
        assertThat(session.isApplied(contradiction)).isFalse();

        // 3. the contradiction is persisted, with the decision that it is HELD
        String caseId = MicroWorldCodec.caseIdFor(assignment, contradiction.lamp());
        workbench.recordObservation(WORKSPACE, modelClaim().id(),
                MicroWorldCodec.describeOutcome(contradiction.lamp()),
                MicroWorldCodec.describe(assignment),
                true, caseId,
                ObservationDisposition.UNRESOLVED,
                "the observation contradicts a prediction the session made; its cause is unresolved");

        // 4. the process restarts: the cases are read back
        workbench.close();
        workbench = KnowledgeWorkbench.at(directory.resolve("cognitive.sqlite").toString());
        List<Observation> restored = MicroWorldCodec.readCases(workbench.storeForValidation(),
                WORKSPACE);

        // 5. the restored observation is still held
        assertThat(restored).hasSize(1);
        assertThat(restored.get(0).disposition()).isEqualTo(ObservationDisposition.UNRESOLVED);
        assertThat(restored.get(0).constrainsModel()).isFalse();

        // 6. and the session rebuilt from it does not apply it
        MicroWorldSession rebuilt = new MicroWorldSession.DefaultFactory(environment)
                .create("ep-2", restored);
        assertThat(rebuilt.appliedObservations()).isEmpty();
        assertThat(rebuilt.quarantinedObservations()).hasSize(1);
        assertThat(rebuilt.survivingHypotheses()).hasSize(16);
    }

    @Test
    @DisplayName("a later independent observation does not turn the old contradiction into active data")
    void aLaterObservationDoesNotReleaseAHeldContradiction() {
        MicroWorldSession session = settledSession();
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=0,switch=0");
        HypothesisPrediction prediction = session.predict(assignment);
        Observation contradiction = new Observation("obs-c", assignment, !prediction.value(),
                false, INSTANT);
        session.observe(contradiction);

        // A second, ordinary observation arrives afterwards.
        WorldInput other = MicroWorldCodec.parseAssignment("power=1,switch=1");
        session.observe(new Observation("obs-o", other, true, false, INSTANT));

        assertThat(session.appliedObservations()).hasSize(5);
        assertThat(session.quarantinedObservations()).hasSize(1);
        assertThat(session.survivingHypotheses()).hasSize(1);
        assertThat(session.quarantinedObservations().get(0).id()).isEqualTo("obs-c");
    }

    // ── the reference computation reads the same data as the model ───────────

    @Test
    @DisplayName("the reference method reads the model-eligible observations, not every observation")
    void theReferenceMethodReadsTheModelEligibleObservations() {
        // A model that has settled: all four rows are observed and consistent.
        MicroWorldSession session = new MicroWorldSession("ep-1", environment);
        List<WorldInput> all = List.of(
                MicroWorldCodec.parseAssignment("power=0,switch=0"),
                MicroWorldCodec.parseAssignment("power=0,switch=1"),
                MicroWorldCodec.parseAssignment("power=1,switch=0"),
                MicroWorldCodec.parseAssignment("power=1,switch=1"));
        for (WorldInput input : all) {
            session.observe(new Observation("obs-" + input.key(), input,
                    input.get("power") && input.get("switch"), false, INSTANT));
        }
        assertThat(session.survivingHypotheses()).hasSize(1);

        // A contradiction arrives and is HELD.
        HypothesisPrediction prediction = session.predict(all.get(0));
        Observation contradiction = new Observation("obs-c", all.get(0), !prediction.value(),
                false, INSTANT);
        session.observe(contradiction);

        // The reference method must answer from the model-eligible data. Under the W44 code it
        // read every observation, the contradiction made the truth table inconsistent, and it
        // refused with "no hypothesis fits the recorded observations".
        WorldInput question = MicroWorldCodec.parseAssignment("power=1,switch=1");
        Boolean answer = new MicroWorldReferenceMethod().execute(
                new MicroWorldComputation(question, session,
                        MicroWorldCodec.facts(question, true), null),
                io.matrix.cognitive.algorithms.ComputeBudget.millis(250));

        assertThat(answer).isTrue();
        assertThat(session.appliedObservations()).hasSize(4);
        assertThat(session.quarantinedObservations()).hasSize(1);
    }

    @Test
    @DisplayName("the reference method and the fast path read the same observations")
    void theReferenceMethodAndTheFastPathReadTheSameObservations() {
        MicroWorldSession session = new MicroWorldSession("ep-1", environment);
        List<WorldInput> all = List.of(
                MicroWorldCodec.parseAssignment("power=0,switch=0"),
                MicroWorldCodec.parseAssignment("power=0,switch=1"),
                MicroWorldCodec.parseAssignment("power=1,switch=0"),
                MicroWorldCodec.parseAssignment("power=1,switch=1"));
        for (WorldInput input : all) {
            session.observe(new Observation("obs-" + input.key(), input,
                    input.get("power") && input.get("switch"), false, INSTANT));
        }
        HypothesisPrediction prediction = session.predict(all.get(0));
        session.observe(new Observation("obs-c", all.get(0), !prediction.value(), false, INSTANT));

        // ONE rule: the observations the model was built from are the applied ones, and the
        // held ones are the rest. There is no second, competing definition.
        assertThat(session.appliedObservations()).hasSize(4);
        assertThat(session.quarantinedObservations()).hasSize(1);
        assertThat(session.appliedObservations().size()
                + session.quarantinedObservations().size())
                .isEqualTo(session.observations().size());
        assertThat(session.dispositionOf(session.observations().get(0)))
                .isEqualTo(ObservationDisposition.ELIGIBLE);
        assertThat(session.dispositionOf(session.quarantinedObservations().get(0)))
                .isEqualTo(ObservationDisposition.UNRESOLVED);
    }

    // ── the durable record ───────────────────────────────────────────────────

    @Test
    @DisplayName("the case, its source, its evidence, the link and the disposition are all written")
    void theObservationIsWrittenWhole() {
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=0,switch=0");
        Observation observation = new Observation("obs-c", assignment, true, false, INSTANT);
        String caseId = MicroWorldCodec.caseIdFor(assignment, observation.lamp());

        workbench.recordObservation(WORKSPACE, modelClaim().id(),
                MicroWorldCodec.describeOutcome(observation.lamp()),
                MicroWorldCodec.describe(assignment),
                false, caseId,
                ObservationDisposition.ELIGIBLE,
                "the observation is ordinary model data");

        KnowledgeStore knowledge = workbench.storeForValidation();
        List<Case> cases = knowledge.cases(WORKSPACE);
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).id()).isEqualTo(caseId);

        List<Evidence> evidence = knowledge.evidence(WORKSPACE);
        assertThat(evidence).hasSize(1);
        assertThat(knowledge.caseIdForEvidence(evidence.get(0).id())).isEqualTo(caseId);
        assertThat(evidence.get(0).relation()).isEqualTo(EvidenceRelation.EXEMPLIFIES);

        // The workspace holds the observation's source and the source of the model's own
        // claim; the observation's source is the one the evidence points at.
        List<Source> sources = knowledge.sources(WORKSPACE);
        assertThat(sources).hasSize(2);
        assertThat(sources).extracting(Source::id).contains(evidence.get(0).sourceId());
        assertThat(cases.get(0).sourceId()).isEqualTo(evidence.get(0).sourceId());

        Map<String, ObservationDisposition> dispositions = knowledge.caseDispositions(WORKSPACE);
        assertThat(dispositions).containsEntry(caseId, ObservationDisposition.ELIGIBLE);
        assertThat(knowledge.dispositionReason(WORKSPACE, caseId)).isNotBlank();
    }

    @Test
    @DisplayName("a review can adjudicate a held observation without deleting anything")
    void aReviewCanAdjudicateAHeldObservation() {
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=0,switch=0");
        Observation observation = new Observation("obs-c", assignment, true, false, INSTANT);
        String caseId = MicroWorldCodec.caseIdFor(assignment, observation.lamp());
        workbench.recordObservation(WORKSPACE, modelClaim().id(),
                MicroWorldCodec.describeOutcome(observation.lamp()),
                MicroWorldCodec.describe(assignment),
                true, caseId,
                ObservationDisposition.UNRESOLVED,
                "the observation contradicts a prediction the session made");

        KnowledgeStore knowledge = workbench.storeForValidation();
        knowledge.adjudicateObservation(WORKSPACE, caseId, ObservationDisposition.ACCEPTED,
                "a review accepted the observation into the model",
                "prd-ep-1", "the owner", "2026-11-03T01:00:00Z", "rev-1", "2026-11-03T01:00:00Z");

        assertThat(knowledge.caseDispositions(WORKSPACE))
                .containsEntry(caseId, ObservationDisposition.ACCEPTED);
        // Nothing is deleted: the case, the evidence and the link are all still there.
        assertThat(knowledge.cases(WORKSPACE)).hasSize(1);
        assertThat(knowledge.evidence(WORKSPACE)).hasSize(1);
        assertThat(knowledge.caseIdForEvidence(knowledge.evidence(WORKSPACE).get(0).id()))
                .isEqualTo(caseId);
    }

    // ── the schema migration ─────────────────────────────────────────────────

    @Test
    @DisplayName("a v16 database is migrated to v17 and its dispositions are backfilled")
    void aV16DatabaseIsMigratedAndBackfilled() throws Exception {
        Path database = directory.resolve("legacy.sqlite");
        KnowledgeStore legacy = new KnowledgeStore(database);
        legacy.close();

        // Build a v16-shaped database: the case table, an evidence link, and no disposition
        // table. This is what a database written by the W44 wave looks like.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("DROP TABLE observation_disposition");
            st.execute("UPDATE schema_meta SET value='16' WHERE key='version'");
            st.execute("INSERT INTO epistemic_case(id, workspace_id, concept_id, statement, "
                    + "conditions, outcome, source_id, origin, observed_at) VALUES "
                    + "('case-supported','" + WORKSPACE + "','c1','supported','power=1,switch=1',"
                    + "'the lamp was on','src-1','OBSERVED','" + INSTANT + "')");
            st.execute("INSERT INTO epistemic_case(id, workspace_id, concept_id, statement, "
                    + "conditions, outcome, source_id, origin, observed_at) VALUES "
                    + "('case-contradicted','" + WORKSPACE + "','c1','contradicted','power=0,switch=0',"
                    + "'the lamp was on','src-2','OBSERVED','" + INSTANT + "')");
            st.execute("INSERT INTO epistemic_case(id, workspace_id, concept_id, statement, "
                    + "conditions, outcome, source_id, origin, observed_at) VALUES "
                    + "('case-orphan','" + WORKSPACE + "','c1','orphan','power=0,switch=1',"
                    + "'the lamp was off','src-3','OBSERVED','" + INSTANT + "')");
            st.execute("INSERT INTO evidence(id, workspace_id, claim_id, source_id, relation, "
                    + "excerpt, quality, recorded_at, case_id) VALUES "
                    + "('evd-1','" + WORKSPACE + "','cl-1','src-1','EXEMPLIFIES','e',1.0,'"
                    + INSTANT + "','case-supported')");
            st.execute("INSERT INTO evidence(id, workspace_id, claim_id, source_id, relation, "
                    + "excerpt, quality, recorded_at, case_id) VALUES "
                    + "('evd-2','" + WORKSPACE + "','cl-1','src-2','CONTRADICTS','e',1.0,'"
                    + INSTANT + "','case-contradicted')");
        }

        KnowledgeStore upgraded = new KnowledgeStore(database);
        try {
            // W45.1: the knowledge schema reached 18 (the adjudication history and the
            // observation occurrences). The v16 -> v17 -> v18 chain still arrives here.
            assertThat(upgraded.schemaVersion()).isEqualTo(18);
            Map<String, ObservationDisposition> dispositions =
                    upgraded.caseDispositions(WORKSPACE);
            assertThat(dispositions)
                    .containsEntry("case-supported", ObservationDisposition.ELIGIBLE)
                    .containsEntry("case-contradicted", ObservationDisposition.UNRESOLVED);
            assertThat(dispositions)
                    .withFailMessage("a case with no evidence link cannot be classified: it is held")
                    .containsEntry("case-orphan", ObservationDisposition.UNRESOLVED);
        } finally {
            upgraded.close();
        }
    }

    @Test
    @DisplayName("a fresh database is built at the current schema version")
    void aFreshDatabaseIsAtTheCurrentVersion() {
        assertThat(workbench.storeForValidation().schemaVersion())
                .isEqualTo(KnowledgeStore.SCHEMA_VERSION);
    }

    @Test
    @DisplayName("the case's origin is not the disposition: a reported case can still be eligible")
    void theOriginIsNotTheDisposition() {
        // The disposition answers "may this constrain the model"; the origin answers "where did
        // it come from". They are different questions and neither is derived from the other.
        WorldInput assignment = MicroWorldCodec.parseAssignment("power=1,switch=1");
        Observation reported = new Observation("obs-r", assignment, true, false, INSTANT);
        assertThat(reported.disposition()).isEqualTo(ObservationDisposition.ELIGIBLE);

        Case reportedCase = new Case("case-r", WORKSPACE, "c1", "reported", "power=1,switch=1",
                "the lamp was on", "src-r", Case.Origin.REPORTED, INSTANT);
        assertThat(reportedCase.origin()).isEqualTo(Case.Origin.REPORTED);
        assertThat(ObservationDisposition.ELIGIBLE.constrainsModel()).isTrue();
        assertThat(ObservationDisposition.UNRESOLVED.constrainsModel()).isFalse();
        assertThat(ObservationDisposition.REJECTED.constrainsModel()).isFalse();
        assertThat(ObservationDisposition.ACCEPTED.constrainsModel()).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Claim modelClaim() {
        KnowledgeStore knowledge = workbench.storeForValidation();
        for (Claim claim : knowledge.claims(WORKSPACE)) {
            if (CognitiveKernel.MODEL_CLAIM_PROPOSITION.equals(claim.proposition())) {
                return claim;
            }
        }
        return workbench.recordClaim(WORKSPACE, CognitiveKernel.MODEL_CLAIM_PROPOSITION,
                CognitiveKernel.MODEL_CLAIM_SCOPE, "the model's own hypothesis");
    }

    @Test
    @DisplayName("the disposition table is the one the store reads")
    void theDispositionTableIsReadable() throws Exception {
        Path database = directory.resolve("direct.sqlite");
        KnowledgeStore knowledge = new KnowledgeStore(database);
        try {
            knowledge.adjudicateObservation(WORKSPACE, "case-x", ObservationDisposition.REJECTED,
                    "a review rejected the observation", null, "the owner",
                    "2026-11-03T01:00:00Z", null, "2026-11-03T01:00:00Z");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT disposition, resolved_by FROM observation_disposition "
                                 + "WHERE case_id='case-x'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("disposition")).isEqualTo("REJECTED");
                assertThat(rs.getString("resolved_by")).isEqualTo("the owner");
            }
        } finally {
            knowledge.close();
        }
    }

    @Test
    @DisplayName("an environment that contradicts the model produces a held observation in a cycle")
    void aContradictingEnvironmentProducesAHeldObservation() {
        // The kernel's own path: a model that has settled, a regulation that asks for a
        // confirmation, and a world that answers differently once.
        Path database = directory.resolve("kernel.sqlite");
        KnowledgeWorkbench kernelWorkbench = KnowledgeWorkbench.at(database.toString());
        CognitiveStore kernelStore = new CognitiveStore(database);
        try {
            ContradictingEnvironment contradicting = new ContradictingEnvironment();
            MicroWorldSession.DefaultFactory factory =
                    new MicroWorldSession.DefaultFactory(contradicting);
            KernelRegulator regulator = new KernelRegulator();
            regulator.evolve(regulator.state(), List.of(new CognitiveKernel.RegulatoryEvent(
                    io.matrix.cognitive.neuromodulation.RegulatoryEvent.Kind.PREDICTION_ERROR,
                    1.0, "the test raises the uncertainty")));
            CognitiveKernel kernel = new CognitiveKernel(kernelWorkbench, kernelStore, factory,
                    contradicting,
                    new KernelSelector(new io.matrix.cognitive.algorithms.AlgorithmRegistry()
                            .register(new MicroWorldReferenceMethod())
                            .register(new MicroWorldSettledModelMethod())),
                    regulator,
                    () -> INSTANT);

            // Four cycles settle the model on the conjunction.
            for (String assignment : List.of("power=0,switch=0", "power=0,switch=1",
                    "power=1,switch=0", "power=1,switch=1")) {
                kernel.process(CognitiveSignal.structured("sig-" + assignment, assignment,
                        "the test", 1L));
            }
            assertThat(contradicting.probes()).isGreaterThanOrEqualTo(4);

            // The next probe contradicts the model.
            contradicting.contradictNext();
            CognitiveOutcome outcome = kernel.process(CognitiveSignal.structured("sig-again",
                    "power=0,switch=0", "the test", 2L));

            // The observation is written, and it is written as HELD.
            Map<String, ObservationDisposition> dispositions =
                    kernelWorkbench.storeForValidation().caseDispositions(WORKSPACE);
            assertThat(dispositions).isNotEmpty();
            assertThat(dispositions.values())
                    .withFailMessage("the contradicting observation must be held")
                    .contains(ObservationDisposition.UNRESOLVED);
            assertThat(outcome.trace().phase("OBSERVE")).isNotEmpty();
        } finally {
            kernelStore.close();
            kernelWorkbench.close();
        }
    }

    /**
     * A world that behaves like the lamp world until it is told to contradict it once.
     *
     * <p>The contradiction is a plain observation, not an adversarial one: the world simply
     * answers differently from what the model believes, which is the case the W45 directive
     * is about.</p>
     */
    private static final class ContradictingEnvironment implements MicroWorldEnvironment {

        private final LampEnvironment delegate = new LampEnvironment();
        private boolean contradictNext;
        private int probes;

        @Override
        public Observation probe(WorldInput input) {
            probes++;
            Observation observation = delegate.probe(input);
            if (contradictNext) {
                contradictNext = false;
                return new Observation(observation.id(), observation.input(), !observation.lamp(),
                        false, observation.observedAt());
            }
            return observation;
        }

        void contradictNext() {
            contradictNext = true;
        }

        int probes() {
            return probes;
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
}
