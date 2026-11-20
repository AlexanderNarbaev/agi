package io.matrix.cognitive.report;

import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;
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
 * The cost basis of a promoted procedure's selection.
 *
 * <p><b>Why this test exists.</b> A procedure is promoted with a <b>declared</b> cost — an
 * estimate written by a person — because at the moment of its promotion nothing has measured
 * it yet. The selector weighs a method by its <b>measured</b> cost whenever the ledger holds a
 * measurement, and by its declared complexity otherwise. So the FIRST selection of a promoted
 * procedure rests on a promise, and the SECOND one must rest on a measurement.
 *
 * <p>That is the claim this test pins: the decision's own stated basis changes from
 * {@code DECLARED_COMPLEXITY} to {@code MEASURED} after the procedure has run once — and it
 * says so in the trace, so the reason a procedure was chosen is readable rather than
 * inferred.</p>
 */
class ReportProcedureCostBasisTest {

    @Test
    @DisplayName("the second selection of a promoted procedure rests on a MEASURED cost")
    void theSecondSelectionRestsOnAMeasurement(@TempDir Path directory) throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                directory.resolve("workbench.sqlite").toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        CognitiveService cognitive = new CognitiveService();
        cognitive.workbench(bench);
        CognitiveStore store = cognitive.store();

        Procedure procedure = ReportProcedure.record(
                Set.of("s-publication-540a1d14"), "the probe's domain",
                5, 1.0, "the episode the probe learned from", "the probe's validation note",
                "2026-01-01T00:00:00Z");

        ReportJourneyPlanner planner = new ReportJourneyPlanner();
        ReportJourney.Problem problem = new ReportJourney.Problem(
                KnowledgeWorkbench.DEFAULT_WORKSPACE, "What is photosynthesis?",
                "report-cost-basis", "report-cost-basis.md", AbstractionLevel.INTRODUCTORY,
                bench, null, null, "the fingerprint");

        // ── the FIRST selection: nothing has measured the procedure yet ──────
        ReportJourneyPlanner.Plan first = planner.plan(
                new io.matrix.cognitive.algorithms.ProblemRequest(
                        io.matrix.cognitive.algorithms.AlgorithmFamilies.KNOWLEDGE_REPORT,
                        problem, Set.of(), io.matrix.cognitive.algorithms.ComputeBudget.unbounded()),
                null, List.of(new ReportProcedureMethod(procedure)));
        assertThat(first.method()).as("the procedure is offered to the kernel").isNotNull();
        assertThat(first.reason())
                .as("the first decision rests on the procedure's DECLARED cost, because nothing "
                        + "has measured it yet")
                .contains("DECLARED_COMPLEXITY");

        // ── the procedure runs, and the ledger records what it cost ──────────
        planner.measure(first.method().capability().id(), () -> {
            bench.ask(KnowledgeWorkbench.DEFAULT_WORKSPACE, "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            return null;
        });

        // ── the SECOND selection: the ledger holds a measurement now ─────────
        ReportJourneyPlanner.Plan second = planner.plan(
                new io.matrix.cognitive.algorithms.ProblemRequest(
                        io.matrix.cognitive.algorithms.AlgorithmFamilies.KNOWLEDGE_REPORT,
                        problem, Set.of(), io.matrix.cognitive.algorithms.ComputeBudget.unbounded()),
                null, List.of(new ReportProcedureMethod(procedure)));
        assertThat(second.method()).isNotNull();
        assertThat(second.reason())
                .as("the second decision rests on the MEASURED cost, and it says so")
                .contains("MEASURED")
                .doesNotContain("DECLARED_COMPLEXITY");
        store.close();
        bench.close();
    }
}
