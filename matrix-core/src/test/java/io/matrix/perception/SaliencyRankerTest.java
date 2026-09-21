package io.matrix.perception;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 212 — SaliencyRanker unit tests. */
class SaliencyRankerTest {

    @Test
    void rankTopKReturnsHighestScoring() {
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] allOnes = new boolean[100];
        for (int i = 0; i < allOnes.length; i++) allOnes[i] = true;
        boolean[] half = new boolean[100];
        for (int i = 0; i < 50; i++) half[i] = true;
        boolean[] allZero = new boolean[100];
        List<SaliencyEngine.SaliencyScore> sources = new ArrayList<>();
        sources.add(eng.score("a", half));
        sources.add(eng.score("b", allOnes));
        sources.add(eng.score("c", allZero));
        List<SaliencyEngine.SaliencyScore> top2 = SaliencyRanker.rankTopK(sources, 2);
        assertThat(top2).hasSize(2);
        // Top 2 should be b and a (highest score first)
        assertThat(top2.get(0).source()).isEqualTo("b");
        assertThat(top2.get(1).source()).isEqualTo("a");
    }

    @Test
    void rankTopKReturnsAllWhenUnderK() {
        SaliencyEngine eng = new SaliencyEngine();
        List<SaliencyEngine.SaliencyScore> sources = new ArrayList<>();
        sources.add(eng.score("a", new boolean[10]));
        List<SaliencyEngine.SaliencyScore> top5 = SaliencyRanker.rankTopK(sources, 5);
        assertThat(top5).hasSize(1);
    }

    @Test
    void rankTopKEmpty() {
        List<SaliencyEngine.SaliencyScore> top = SaliencyRanker.rankTopK(
                new ArrayList<>(), 3);
        assertThat(top).isEmpty();
    }

    @Test
    void isDiverseTrueForRange() {
        assertThat(SaliencyRanker.isDiverse(
                new double[]{0.1, 0.5, 0.9}, 0.5)).isTrue();
    }

    @Test
    void isDiverseFalseForRange() {
        assertThat(SaliencyRanker.isDiverse(
                new double[]{0.5, 0.5, 0.5}, 0.1)).isFalse();
    }

    @Test
    void isDiverseEmptyOrSingleton() {
        assertThat(SaliencyRanker.isDiverse(new double[0], 0.5)).isTrue();
        assertThat(SaliencyRanker.isDiverse(new double[]{0.5}, 0.5)).isTrue();
    }

    @Test
    void bucketDividesScoreSpace() {
        assertThat(SaliencyRanker.bucket(0.0)).isZero();
        assertThat(SaliencyRanker.bucket(0.2)).isEqualTo(1);
        assertThat(SaliencyRanker.bucket(0.5)).isEqualTo(2);
        assertThat(SaliencyRanker.bucket(0.7)).isEqualTo(3);
        assertThat(SaliencyRanker.bucket(0.95)).isEqualTo(4);
    }
}
