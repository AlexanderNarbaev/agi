package io.matrix.cognitive.report;

import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.AlgorithmFamilies;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.ProblemRequest;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The promotion's MEASURED cost, and the first decision it carries.
 *
 * <p><b>Why this test exists.</b> A procedure's <b>first</b> selection is made before it has
 * ever run, so the selector weighs it by its <b>declared</b> cost — and a declared cost is a
 * promise. The promotion, on the other hand, has already run the restricted reading on its
 * held-out cases and therefore knows what that reading actually costs. This test pins that the
 * measurement reaches the decision, so the first decision rests on a measurement too.</p>
 *
 * <p><b>What it pins.</b> That the promotion records the cost it measured, that the method
 * declares <b>that</b> cost rather than the constant, and that the decision's own stated
 * reason says the cost was measured — so a reader of the trace can tell a promise from a
 * measurement.</p>
 */
class ReportProcedureMeasuredCostTest {

    @Test
    @DisplayName("the promotion carries the cost it measured, and the first decision names it")
    void theFirstDecisionRestsOnAMeasurement(@TempDir Path directory) throws Exception {
        Path database = directory.resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            CognitiveStore store = cognitive.store();

            // the support set is the PRODUCTION source ids of the claims the REFERENCE
            // cited for the held-out questions — the package's own keys are not them, and
            // the reference's own sources are what makes the restricted reading agree
            var snapshot = bench.workspace(KnowledgeWorkbench.DEFAULT_WORKSPACE);
            // REAL questions, not invented ones: the agreement gate compares the two
            // readings' cited claims, and an invented question retrieves nothing, so the
            // restricted reading would answer differently for a reason that has nothing to
            // do with the support set
            List<String> heldOutQuestions = List.of(
                    "What is the role of water in photosynthesis?",
                    "Which organisms perform oxygenic photosynthesis?",
                    "What is the Calvin-Benson cycle?",
                    "How does RuBisCO compete with oxygenation?",
                    "What limits photosynthetic output in angiosperms?");
            // the support is the SOURCES the reference drew on for EVERY held-out
            // question: a restricted reading that covers them all agrees with the reference,
            // and a narrower one would answer differently — which is what the agreement gate
            // is there to catch
            java.util.Set<String> support = snapshot.sources().stream()
                    .map(io.matrix.knowledge.Source::id)
                    .collect(java.util.stream.Collectors.toCollection(
                            java.util.LinkedHashSet::new));
            java.util.Set<String> claimIds = new java.util.LinkedHashSet<>();
            for (String question : heldOutQuestions) {
                claimIds.addAll(bench.ask(KnowledgeWorkbench.DEFAULT_WORKSPACE, question,
                        AbstractionLevel.INTRODUCTORY).claims().stream()
                        .map(io.matrix.knowledge.Claim::id).toList());
            }
            assertThat(support).as("the workspace has sources").isNotEmpty();

            // ── the producer: the promotion measures its own restricted reading ─────
            ReportProcedureLearner learner = new ReportProcedureLearner(bench, store);
            ReportProcedureLearner.Outcome outcome = learner.learn(
                    KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    List.of(new ReportProcedureLearner.Observation("episode-0",
                            "What is photosynthesis, observed 0?", AbstractionLevel.INTRODUCTORY,
                            List.copyOf(claimIds), List.copyOf(support))),
                    heldOutQuestions.stream()
                            .map(question -> new ReportProcedureLearner.Case(
                                    "case-" + heldOutQuestions.indexOf(question), question,
                                    AbstractionLevel.INTRODUCTORY))
                            .toList(),
                    Set.of(), "2026-11-21T00:00:00Z");
            assertThat(outcome.promoted()).as("the procedure was promoted: " + outcome.reason())
                    .isTrue();
            Procedure procedure = outcome.procedure();

            // ── the measurement is in the store, and it is a MEASUREMENT ────────────
            Long measured = store.procedureMeasuredCostNanos(
                    KnowledgeWorkbench.DEFAULT_WORKSPACE, procedure.id(), procedure.version());
            assertThat(measured)
                    .as("the promotion recorded the cost it measured")
                    .isNotNull();
            assertThat(measured)
                    .as("and the measurement is a real reading's cost, not the constant")
                    .isNotEqualTo(ReportProcedureMethod.MEASURED_COST_MILLIS * 1_000_000L);

            // ── the method declares the MEASUREMENT, not the constant ───────────────
            ReportProcedureMethod method = new ReportProcedureMethod(procedure, measured);
            assertThat(method.declaredCostMillis())
                    .as("the method declares the measured cost")
                    .isNotEqualTo(ReportProcedureMethod.MEASURED_COST_MILLIS);
            assertThat(method.capability().complexity().time())
                    .as("and the declaration carries the MEASUREMENT as a structured marker, "
                            + "so the selector can tell a measurement from a promise")
                    .contains("costMeasuredNanos=")
                    .contains("held-out case");
            assertThat(io.matrix.cognitive.algorithms.DeclaredCost
                    .parse(method.capability().complexity().time()).measured())
                    .as("and the parsed cost says it was measured")
                    .isTrue();

            // ── and the FIRST decision names it ────────────────────────────────────
            ReportJourney.Problem problem = new ReportJourney.Problem(
                    KnowledgeWorkbench.DEFAULT_WORKSPACE, "What is photosynthesis?",
                    "identity-0", "report.md", AbstractionLevel.INTRODUCTORY, bench, null, null,
                    "fingerprint");
            // the procedure's PRECONDITION is established against the workspace as it is
            // now: its support set must still be present. The route does this for every
            // request, and a method asked to run directly must hold the same condition.
            Set<String> facts = Set.of(ReportProcedure.supportPresentFact(procedure.id()));
            ReportJourneyPlanner.Plan plan = new ReportJourneyPlanner().plan(
                    new ProblemRequest(AlgorithmFamilies.KNOWLEDGE_REPORT, problem, facts,
                            ComputeBudget.unbounded()),
                    null, List.of(method));
            assertThat(plan.method()).isNotNull();
            assertThat(plan.reason())
                    .as("the decision's own reason says the cost was MEASURED, not declared")
                    .contains("MEASURED_COMPLEXITY")
                    .doesNotContain("DECLARED_COMPLEXITY");
            store.close();
        } finally {
            bench.close();
        }
    }
}
