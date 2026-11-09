package io.matrix.w51;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the end-to-end demonstration, under test.
 *
 * <p>The demonstration is the wave's principal deliverable, and a deliverable that only
 * runs when somebody runs it by hand is not a deliverable. This test runs it in a temporary
 * directory and checks the claims it makes — every one of the fifteen acceptance points, the
 * trace it writes, and the reproducibility of both.</p>
 *
 * <p>It runs TWICE, in two temporary directories, because a demonstration that produces a
 * different answer the second time is not a demonstration of a deterministic system.</p>
 */
class W51DemonstrationTest {

    @TempDir
    Path directory;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> acceptanceOf(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("acceptance");
    }

    @Test
    @DisplayName("every acceptance point of the mandate is demonstrated")
    void everyAcceptancePointIsDemonstrated() throws Exception {
        Map<String, Object> report = W51Demonstration.run(directory.resolve("first"));

        List<Map<String, Object>> acceptance = acceptanceOf(report);
        assertEquals(15, acceptance.size(), "the mandate names fifteen acceptance points");
        for (Map<String, Object> point : acceptance) {
            assertEquals("DEMONSTRATED", point.get("status"),
                    "acceptance point " + point.get("number") + " (" + point.get("requirement")
                            + ") was not demonstrated; the evidence says: "
                            + point.get("evidence"));
            assertNotNull(point.get("evidence"));
            assertTrue(String.valueOf(point.get("evidence")).length() > 20,
                    "an acceptance point must carry evidence, not a label");
        }
        assertEquals(15L, ((Number) report.get("acceptanceDemonstrated")).longValue());
    }

    @Test
    @DisplayName("the trace connects the observation, the method, the prediction and the phases")
    void theTraceIsConnected() throws Exception {
        Map<String, Object> report = W51Demonstration.run(directory.resolve("second"));

        @SuppressWarnings("unchecked")
        Map<String, Object> cycle = (Map<String, Object>) report.get("cycle");
        assertNotNull(cycle);
        assertEquals("COMPLETED", cycle.get("status"));
        assertEquals("ep-sig-w51-1", cycle.get("episodeId"));
        assertEquals("micro-world-hypothesis-enumeration", cycle.get("selectedMethod"));
        assertEquals("false", cycle.get("predictedValue"));
        assertEquals("surviving-hypotheses", cycle.get("predictionBasis"));

        @SuppressWarnings("unchecked")
        List<String> phases = (List<String>) cycle.get("tracePhases");
        assertTrue(phases.contains("PERCEIVE"), "the trace must begin with the perception");
        assertTrue(phases.contains("PREDICT"), "the trace must hold the prediction");
        assertTrue(phases.contains("PROBE"), "the trace must hold the action");
        assertTrue(phases.contains("OBSERVE"), "the trace must hold the observation");
        assertTrue(phases.contains("LEARN"), "the trace must hold the learning");
        assertTrue(phases.indexOf("PREDICT") < phases.indexOf("PROBE"),
                "the prediction must come BEFORE the probe, or it is not a prediction");
        assertTrue(phases.indexOf("PROBE") < phases.indexOf("OBSERVE"),
                "the action must come BEFORE the observation");
        assertTrue(phases.contains("WORLD"),
                "the trace must hold the world-state transition the action caused");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("the system learns the rule through interaction, and the learning is durable")
    void theSystemLearnsThroughInteraction() throws Exception {
        Map<String, Object> report = W51Demonstration.run(directory.resolve("learning"));

        Map<String, Object> episode = (Map<String, Object>) report.get("episode");
        assertNotNull(episode, "the report must carry the learning episode");

        int fresh = ((Number) episode.get("freshHypotheses")).intValue();
        int learned = ((Number) episode.get("finalSurvivors")).intValue();
        assertEquals(16, fresh, "the micro-world starts with sixteen hypotheses");
        assertEquals(1, learned,
                "the system must learn the rule: one hypothesis must survive the episode");

        List<Integer> perCycle = (List<Integer>) episode.get("survivorsPerCycle");
        assertTrue(perCycle.size() >= 1, "the learning loop must have run at least one cycle");
        assertEquals(1, perCycle.get(perCycle.size() - 1),
                "the LAST cycle must leave the model settled");
        for (int i = 1; i < perCycle.size(); i++) {
            assertTrue(perCycle.get(i) <= perCycle.get(i - 1),
                    "the hypothesis space must narrow, not widen: " + perCycle);
        }
        assertTrue(((Number) episode.get("rememberedObservations")).intValue() >= perCycle.size(),
                "every probe must be durable");

        // W51 §5 — the system remembers what its methods cost. The first cycle has no
        // measurement and falls back on the declared complexity; the later ones weigh the
        // measurement the kernel recorded.
        assertEquals(Boolean.TRUE, episode.get("costBasisMeasured"),
                "the method's cost must be MEASURED and recorded after the first cycle");
        List<String> decisions = (List<String>) episode.get("methodDecisions");
        assertTrue(decisions.get(0).contains("DECLARED_COMPLEXITY"),
                "the first cycle has no measurement yet: " + decisions.get(0));
        assertTrue(decisions.get(decisions.size() - 1).contains("MEASURED"),
                "the last cycle must weigh the measurement: "
                        + decisions.get(decisions.size() - 1));

        Map<String, Object> restart = (Map<String, Object>) report.get("restart");
        assertNotNull(restart);
        assertEquals(((Number) restart.get("casesBefore")).intValue(),
                ((Number) restart.get("casesAfter")).intValue(),
                "the durable cases must survive the restart");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("the report carries every section of the trace, not only the cycle")
    void theReportCarriesEverySection() throws Exception {
        Map<String, Object> report = W51Demonstration.run(directory.resolve("sections"));

        for (String section : List.of("runtime", "cycle", "metaMemory", "episode", "restart",
                "plugin", "bridge", "acceptance")) {
            assertNotNull(report.get(section), "the report must carry the section " + section);
        }
        Map<String, Object> plugin = (Map<String, Object>) report.get("plugin");
        assertEquals("ACTIVE", plugin.get("finalStage"),
                "the generated plugin must have reached ACTIVE");
        List<String> lifecycle = (List<String>) plugin.get("lifecycle");
        // PROPOSED is where the plugin starts; the five moves out of it are the chain the
        // mandate names.
        assertEquals(5, lifecycle.size(),
                "the lifecycle must name every move out of PROPOSED: " + lifecycle);
        assertTrue(lifecycle.get(0).contains("BUILDING"), lifecycle.get(0));
        assertTrue(lifecycle.get(lifecycle.size() - 1).contains("ACTIVE"),
                lifecycle.get(lifecycle.size() - 1));
        Map<String, Object> cycle = (Map<String, Object>) report.get("cycle");
        Map<String, Object> canonical = (Map<String, Object>) cycle.get("canonical");
        assertNotNull(canonical, "the report must carry the canonical form of the cycle");
        @SuppressWarnings("unchecked")
        List<String> produced = (List<String>) canonical.get("produced");
        assertTrue(produced.containsAll(List.of("Entity", "Relation", "Property", "WorldState",
                        "StateTransition", "Event", "Observation", "Evidence", "Belief",
                        "Hypothesis", "Prediction", "Experiment", "Action", "Outcome", "Goal",
                        "Task", "Algorithm", "Evaluation", "Reflection")),
                "the cycle must fill the canonical contracts it can: " + produced);
        @SuppressWarnings("unchecked")
        List<String> absent = (List<String>) canonical.get("absent");
        assertTrue(absent.containsAll(List.of("Skill", "Procedure")),
                "in the FIRST cycle the model has not settled, so there is no procedure to "
                        + "carry, and the report says so: " + absent);
        assertEquals(22, produced.size() + absent.size(),
                "every contract of the mandate is either produced or named as absent");

        Map<String, Object> meta = (Map<String, Object>) report.get("metaMemory");
        assertNotNull(meta.get("statusCounts"), "the meta-memory must report what the system holds");
        assertNotNull(meta.get("evaluation"), "the meta-memory must produce an evaluation");

        Map<String, Object> bridge = (Map<String, Object>) report.get("bridge");
        assertEquals("ACCEPTED", bridge.get("decision"));
        assertEquals(1, ((Number) bridge.get("candidateClaims")).intValue(),
                "the bridged observation must arrive as exactly one candidate");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("W53: the procedure is written when the model settles, and a later cycle "
            + "consults it")
    void theProcedureIsWrittenAndReused() throws Exception {
        Map<String, Object> report = W51Demonstration.run(directory.resolve("procedure"));

        Map<String, Object> procedure = (Map<String, Object>) report.get("procedure");
        assertNotNull(procedure, "the report must carry the procedure the system learned");
        assertNotNull(procedure.get("written"), "the procedure must be written down");
        assertNotNull(procedure.get("learnedInEpisode"), "the procedure must say where it "
                + "was learned");
        assertEquals("IN_SAMPLE", procedure.get("validationKind"),
                "the procedure rests on the model's own cases, and the record says so");
        assertTrue(((Number) procedure.get("validationCases")).intValue() > 0,
                "the consistency check must have cases to rest on");
        assertNotNull(procedure.get("whatItRestsOn"), "the record must say what its standing "
                + "rests on");
        assertTrue(String.valueOf(procedure.get("theCycleThatWroteIt")).contains("was written"),
                "the cycle that wrote the procedure must say so");
        assertTrue(String.valueOf(procedure.get("theCycleThatConsultedIt"))
                        .contains("was consulted"),
                "a later cycle must consult the procedure, and say so");

        // And the mandate's acceptance point 11 now carries the stronger evidence.
        for (Map<String, Object> point : acceptanceOf(report)) {
            if (Integer.valueOf(11).equals(point.get("number"))) {
                assertTrue(String.valueOf(point.get("evidence")).toLowerCase().contains("consult"),
                        "the reuse of a procedure is the acceptance point 11, and the "
                                + "evidence must say it: " + point.get("evidence"));
            }
        }
    }

    @Test
    @DisplayName("two runs of the demonstration agree")
    void twoRunsAgree() throws Exception {
        Map<String, Object> first = W51Demonstration.run(directory.resolve("run-a"));
        Map<String, Object> second = W51Demonstration.run(directory.resolve("run-b"));

        assertEquals(((Number) first.get("acceptanceDemonstrated")).longValue(),
                ((Number) second.get("acceptanceDemonstrated")).longValue());
        for (int i = 0; i < acceptanceOf(first).size(); i++) {
            Map<String, Object> a = acceptanceOf(first).get(i);
            Map<String, Object> b = acceptanceOf(second).get(i);
            assertEquals(a.get("status"), b.get("status"), "point " + a.get("number"));
            assertEquals(a.get("requirement"), b.get("requirement"));
        }
    }
}
