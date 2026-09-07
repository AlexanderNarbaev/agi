package io.matrix.perception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 152 — SaliencyEngine unit tests. */
class SaliencyEngineTest {

    @Test
    void allZerosLowDensity() {
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] bits = new boolean[100];
        var score = eng.score("text", bits);
        assertThat(score.density()).isZero();
        assertThat(score.bitCount()).isZero();
        assertThat(score.surprise()).isEqualTo(1.0); // max surprise at 0%
    }

    @Test
    void allOnesHighDensity() {
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] bits = new boolean[100];
        for (int i = 0; i < bits.length; i++) bits[i] = true;
        var score = eng.score("text", bits);
        assertThat(score.density()).isEqualTo(1.0);
        assertThat(score.bitCount()).isEqualTo(100);
        assertThat(score.surprise()).isEqualTo(1.0);
    }

    @Test
    void halfDensityLowSurprise() {
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] bits = new boolean[100];
        for (int i = 0; i < 50; i++) bits[i] = true;
        var score = eng.score("text", bits);
        assertThat(score.density()).isEqualTo(0.5);
        assertThat(score.surprise()).isZero(); // exactly 50%, no surprise
    }

    @Test
    void deterministicScoring() {
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] bits = {true, false, true, true, false};
        SaliencyEngine.SaliencyScore a = eng.score("X", bits);
        SaliencyEngine.SaliencyScore b = eng.score("X", bits);
        assertThat(a.score()).isEqualTo(b.score());
        assertThat(a.bitCount()).isEqualTo(b.bitCount());
    }

    @Test
    void rankOrdersHighestFirst() {
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] allOnes = new boolean[100];
        for (int i = 0; i < allOnes.length; i++) allOnes[i] = true;
        boolean[] half = new boolean[100];
        for (int i = 0; i < 50; i++) half[i] = true;
        boolean[] allZero = new boolean[100];

        Map<String, boolean[]> sources = new HashMap<>();
        sources.put("a-z", half);
        sources.put("0011", allOnes);
        sources.put("empty", allZero);

        List<SaliencyEngine.SaliencyScore> ranked = eng.rank(sources);
        // sorted descending, so first has higher score
        for (int i = 0; i < ranked.size() - 1; i++) {
            assertThat(ranked.get(i).score()).isGreaterThanOrEqualTo(
                    ranked.get(i + 1).score());
        }
    }

    @Test
    void emptyInputEdgeCase() {
        SaliencyEngine eng = new SaliencyEngine();
        var score = eng.score("X", new boolean[0]);
        assertThat(score.density()).isZero();
        assertThat(score.bitCount()).isZero();
    }

    @Test
    void scoreContainsSourceName() {
        SaliencyEngine eng = new SaliencyEngine();
        var score = eng.score("audio-channel", new boolean[]{true, false, true});
        assertThat(score.source()).isEqualTo("audio-channel");
    }
}
