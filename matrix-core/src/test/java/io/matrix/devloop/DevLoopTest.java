package io.matrix.devloop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the SPEC-000 developmental-loop core
 * ({@code docs/spec/SPEC-000-developmental-loop.md#задачи}).
 */
class DevLoopTest {

    // --- MaturityLevel ---

    @Test
    void maturityLevelsStrictlyOrdered() {
        assertThat(MaturityLevel.MA_0_SANDBOX.level())
                .isLessThan(MaturityLevel.MA_1_LOCAL.level())
                .isLessThan(MaturityLevel.MA_2_NETWORK.level());
        assertThat(MaturityLevel.MA_2_NETWORK.level())
                .isLessThan(MaturityLevel.MA_3_SELF_MODIFY.level());
        assertThat(MaturityLevel.MA_3_SELF_MODIFY.level())
                .isLessThan(MaturityLevel.MA_4_AUTONOMOUS.level());
        assertThat(MaturityLevel.MA_4_AUTONOMOUS.level())
                .isLessThan(MaturityLevel.MA_5_MENTOR.level());
    }

    @Test
    void maturityNextHasCeilingAndPreviousHasFloor() {
        assertThat(MaturityLevel.MA_0_SANDBOX.next()).isEqualTo(MaturityLevel.MA_1_LOCAL);
        assertThat(MaturityLevel.MA_4_AUTONOMOUS.next()).isEqualTo(MaturityLevel.MA_5_MENTOR);
        assertThat(MaturityLevel.MA_5_MENTOR.next()).isEqualTo(MaturityLevel.MA_5_MENTOR);
        assertThat(MaturityLevel.MA_5_MENTOR.previous()).isEqualTo(MaturityLevel.MA_4_AUTONOMOUS);
    }

    // --- ScenarioSpec acceptance predicate ---

    @Test
    void scenarioAcceptancePredicate() {
        ScenarioSpec xor = new ScenarioSpec(
                "a-xor", "logic", "xor",
                new DifficultyBand(0.0, 0.5),
                Set.of(),
                "all effects present",
                o -> o.effects().contains("table-built") && o.unmetCriteria().isEmpty());

        Outcome good = new Outcome("a-xor", "xor", true,
                Set.of("table-built"), Set.of());
        Outcome partial = new Outcome("a-xor", "xor", true,
                Set.of(), Set.of("table-missing"));

        assertThat(xor.accepts(good)).isTrue();
        assertThat(xor.accepts(partial)).isFalse();
    }

    // --- CompetenceAssessor (EWMA) ---

    @Test
    void competenceBoundedAndResponsive() {
        CompetenceAssessor assessor = new CompetenceAssessor();
        String skill = "xor";
        for (int i = 0; i < 10; i++) {
            assessor.record(new Outcome("s", skill, true, Set.of("ok"), Set.of()));
        }
        double afterSuccess = assessor.competence(skill);
        assertThat(afterSuccess).isBetween(0.0, 1.0).isGreaterThan(0.5);

        assessor.record(new Outcome("s", skill, false, Set.of(), Set.of("x")));
        assertThat(assessor.competence(skill)).isLessThan(afterSuccess);
        assertThat(assessor.competence("unknown-skill")).isEqualTo(0.0);
    }

    // --- CurriculumEngine (ZPD selection) ---

    @Test
    void zpdPicksLowestIdBracketingScenario() {
        CurriculumEngine engine = new CurriculumEngine();
        ScenarioSpec easy = new ScenarioSpec("b-easy", "d", "k",
                new DifficultyBand(0.0, 0.3), Set.of(), "e", o -> true);
        ScenarioSpec mid = new ScenarioSpec("c-mid", "d", "k",
                new DifficultyBand(0.3, 0.7), Set.of(), "m", o -> true);
        ScenarioSpec hard = new ScenarioSpec("a-hard", "d", "k",
                new DifficultyBand(0.8, 1.0), Set.of(), "h", o -> true);

        Map<String, Double> competence = Map.of("k", 0.5);
        assertThat(engine.nextScenario(competence, List.of(hard, mid, easy)))
                .hasValue(mid);

        assertThat(engine.nextScenario(Map.of("k", 0.99), List.of(easy, mid)))
                .isEmpty();
    }

    // --- FeedbackComposer ---

    @Test
    void feedbackTypesMapToOutcomeDiff() {
        FeedbackComposer composer = new FeedbackComposer();
        Outcome expected = Outcome.expected("s", "k", Set.of("a", "b"));

        Feedback correct = composer.compose(expected,
                new Outcome("s", "k", true, Set.of("a", "b"), Set.of()));
        assertThat(correct.type()).isEqualTo(Feedback.FeedbackType.CORRECT);

        Feedback partial = composer.compose(expected,
                new Outcome("s", "k", true, Set.of("a"), Set.of()));
        assertThat(partial.type()).isEqualTo(Feedback.FeedbackType.PARTIAL);
        assertThat(partial.missingEffects()).containsExactly("b");

        Feedback wrong = composer.compose(expected,
                new Outcome("s", "k", false, Set.of("zzz"), Set.of()));
        assertThat(wrong.type()).isEqualTo(Feedback.FeedbackType.COUNTEREXAMPLE);
    }

    // --- ScaffoldingManager ---

    @Test
    void scaffoldDecaysAfterStreakAndRaisesOnFailure() {
        ScaffoldingManager sm = new ScaffoldingManager();
        assertThat(sm.level("k")).isEqualTo(ScaffoldingManager.INITIAL_LEVEL);

        for (int i = 0; i < ScaffoldingManager.SUCCESSES_TO_DECAY; i++) {
            sm.recordSuccess("k");
        }
        assertThat(sm.level("k")).isEqualTo(ScaffoldingManager.INITIAL_LEVEL - 1);

        for (int i = 0; i < 20; i++) {
            sm.recordFailure("k");
        }
        assertThat(sm.level("k")).isEqualTo(ScaffoldingManager.MAX_LEVEL);
    }

    // --- MaturityGateKeeper ---

    @Test
    void gateKeeperAdvancesOnlyWhenCriteriaMetAndNeverBackwardViaAdvance() {
        // Criteria are keyed by TARGET gate level (see advance(): criteria.get(target)).
        Map<MaturityLevel, java.util.function.Predicate<GateCriteria>> criteria =
                Map.of(MaturityLevel.MA_1_LOCAL, g -> g.metric("competence") >= 0.6);
        MaturityGateKeeper keeper = new MaturityGateKeeper(criteria);

        assertThat(keeper.current()).isEqualTo(MaturityLevel.MA_0_SANDBOX);

        var denied = keeper.advance(new GateCriteria(Map.of("competence", 0.4)));
        assertThat(denied.approved()).isFalse();
        assertThat(keeper.current()).isEqualTo(MaturityLevel.MA_0_SANDBOX);

        var approved = keeper.advance(new GateCriteria(Map.of("competence", 0.9)));
        assertThat(approved.approved()).isTrue();
        assertThat(keeper.current()).isEqualTo(MaturityLevel.MA_1_LOCAL);

        // Criteria satisfied again must not skip levels or regress.
        var again = keeper.advance(new GateCriteria(Map.of("competence", 0.9)));
        assertThat(keeper.current().level()).isGreaterThanOrEqualTo(MaturityLevel.MA_1_LOCAL.level());
    }
}
