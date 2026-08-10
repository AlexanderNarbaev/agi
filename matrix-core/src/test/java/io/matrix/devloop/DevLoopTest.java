package io.matrix.devloop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DevLoopTest {

    @Test
    void maturityLevelsOrdered() {
        assertTrue(MaturityLevel.MA_0_SANDBOX.level() < MaturityLevel.MA_1_LOCAL.level());
        assertTrue(MaturityLevel.MA_1_LOCAL.level() < MaturityLevel.MA_2_NETWORK.level());
        assertTrue(MaturityLevel.MA_2_NETWORK.level() < MaturityLevel.MA_3_SELF_MODIFY.level());
        assertTrue(MaturityLevel.MA_3_SELF_MODIFY.level() < MaturityLevel.MA_4_AUTONOMOUS.level());
    }

    @Test
    void maturityNextAndPrevious() {
        assertEquals(MaturityLevel.MA_1_LOCAL, MaturityLevel.MA_0_SANDBOX.next());
        assertEquals(MaturityLevel.MA_0_SANDBOX, MaturityLevel.MA_1_LOCAL.previous());
        assertEquals(MaturityLevel.MA_4_AUTONOMOUS, MaturityLevel.MA_4_AUTONOMOUS.next()); // ceiling
        assertEquals(MaturityLevel.MA_0_SANDBOX, MaturityLevel.MA_0_SANDBOX.previous()); // floor
    }

    @Test
    void scenarioSpecBattery() {
        List<ScenarioSpec> battery = ScenarioSpec.standardBattery();
        assertEquals(3, battery.size());
        assertEquals("xor", battery.get(0).id());
        assertEquals("gridworld", battery.get(1).id());
        assertEquals("craft", battery.get(2).id());
    }

    @Test
    void competenceAssessorAggregates() {
        var assessor = new CompetenceAssessor();
        var scenario = ScenarioSpec.standardBattery().get(0); // xor
        var report = assessor.assess(scenario, s ->
                CompetenceAssessor.ScenarioResult.success(50, "xor solved"));
        assertTrue(report.success());
        assertEquals("xor", report.scenarioId());
        assertTrue(report.competenceScore() > 0);
        assertTrue(assessor.aggregateCompetence() > 0);
    }

    @Test
    void gateKeeperPromotionFlow() {
        var assessor = new CompetenceAssessor();
        var keeper = new MaturityGateKeeper(assessor);
        assertEquals(MaturityLevel.MA_0_SANDBOX, keeper.current());

        // Not ready — no competence
        var result = keeper.requestPromotion("op1", "exp-001");
        assertFalse(result.approved());

        // Build competence
        var scenario = ScenarioSpec.standardBattery().get(0);
        for (int i = 0; i < 5; i++) {
            assessor.assess(scenario, s -> CompetenceAssessor.ScenarioResult.success(30, "ok"));
        }
        result = keeper.requestPromotion("op1", "exp-001");
        // With 5 successes at 30 steps each, competence should be > 0.6
        assertTrue(result.approved());
        assertEquals(MaturityLevel.MA_1_LOCAL, keeper.current());
    }

    @Test
    void gateKeeperDemotion() {
        var assessor = new CompetenceAssessor();
        var keeper = new MaturityGateKeeper(assessor);
        // Force to MA_1
        var scenario = ScenarioSpec.standardBattery().get(0);
        for (int i = 0; i < 10; i++) {
            assessor.assess(scenario, s -> CompetenceAssessor.ScenarioResult.success(10, "ok"));
        }
        keeper.requestPromotion("op1", "exp-001");
        assertEquals(MaturityLevel.MA_1_LOCAL, keeper.current());

        // Demote on drift
        var result = keeper.demote("drift detected");
        assertTrue(result.approved());
        assertEquals(MaturityLevel.MA_0_SANDBOX, keeper.current());
        assertEquals(2, keeper.transitions().size());
    }
}
