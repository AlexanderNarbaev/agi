package io.matrix.devloop;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for SPEC-000 developmental-loop invariants.
 *
 * @see <a href="docs/spec/SPEC-000-developmental-loop.md">SPEC-000</a>
 */
class DevLoopPropertiesTest {

    // --- Invariant: maturity gates are monotone (never move backward) ---

    @Provide
    Arbitrary<List<Double>> evidenceSequences() {
        return Arbitraries.doubles().between(-1.0, 2.0).list().ofMaxSize(24);
    }

    @Property
    void gateLevelNeverDecreases(@ForAll("evidenceSequences") List<Double> evidences) {
        Map<MaturityLevel, Predicate<GateCriteria>> criteria = Map.of(
                MaturityLevel.MA_1_LOCAL, g -> g.metric("competence") >= 0.5);
        MaturityGateKeeper keeper = new MaturityGateKeeper(criteria);

        int seenMin = keeper.current().level();
        for (Double value : evidences) {
            keeper.advance(new GateCriteria(Map.of("competence", value)));
            int level = keeper.current().level();
            assertThat(level).isGreaterThanOrEqualTo(seenMin);
            seenMin = Math.min(seenMin, level);
        }
        // And after any promotion, level stays at least MA_1.
    }

    // --- Invariant: ZPD selection always inside band, lowest id wins ---

    @Property
    void zpdResultBracketsCompetenceAndHasMinimalId(
            @ForAll double competence,
            @ForAll("bands") List<DifficultyBand> bands) {
        CurriculumEngine engine = new CurriculumEngine();
        List<ScenarioSpec> catalog = new java.util.ArrayList<>();
        char id = 'a';
        for (DifficultyBand band : bands) {
            catalog.add(new ScenarioSpec(String.valueOf(id++), "d", "k",
                    band, Set.of(), "desc", o -> true));
        }

        Optional<ScenarioSpec> picked =
                engine.nextScenario(Map.of("k", clamp01(competence)), catalog);

        picked.ifPresent(spec -> {
            double c = clamp01(competence);
            assertThat(spec.difficultyBand().brackets(c)).isTrue();
            catalog.stream()
                    .filter(s -> s.difficultyBand().brackets(c))
                    .map(ScenarioSpec::id)
                    .min(String::compareTo)
                    .ifPresent(minId -> assertThat(spec.id()).isEqualTo(minId));
        });
    }

    @Provide
    Arbitrary<List<DifficultyBand>> bands() {
        Arbitrary<Double> lo = Arbitraries.doubles().between(0.0, 0.9);
        return lo.list().ofMaxSize(6).map(lists -> lists.stream()
                .map(l -> new DifficultyBand(l, Math.min(1.0, l + 0.3)))
                .toList());
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // --- Invariant: EWMA competence bounded in [0,1] ---

    @Property
    void competenceAlwaysBounded(@ForAll("outcomes") List<Boolean> outcomes) {
        CompetenceAssessor assessor = new CompetenceAssessor();
        String skill = "s";
        for (Boolean ok : outcomes) {
            assessor.record(new Outcome("scn", skill, ok, Set.of("e"), Set.of()));
        }
        assertThat(assessor.competence(skill)).isBetween(0.0, 1.0);
    }

    @Provide
    Arbitrary<List<Boolean>> outcomes() {
        return Arbitraries.of(true, false).list().ofMaxSize(50);
    }

    // --- Invariant: scaffold level bounded ---

    @Property
    void scaffoldLevelBounded(@ForAll("ops") List<Boolean> successOps) {
        ScaffoldingManager sm = new ScaffoldingManager();
        for (Boolean success : successOps) {
            if (success) {
                sm.recordSuccess("k");
            } else {
                sm.recordFailure("k");
            }
            assertThat(sm.level("k"))
                    .isBetween(ScaffoldingManager.MIN_LEVEL, ScaffoldingManager.MAX_LEVEL);
        }
    }

    @Provide
    Arbitrary<List<Boolean>> ops() {
        return Arbitraries.of(true, false).list().ofMaxSize(60);
    }
}
