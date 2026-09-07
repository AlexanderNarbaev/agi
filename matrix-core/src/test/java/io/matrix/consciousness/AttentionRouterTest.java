package io.matrix.consciousness;

import io.matrix.perception.SaliencyEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 154 — AttentionRouter unit tests. */
class AttentionRouterTest {

    @Test
    void mergeReturnsEmptyForEmptyInputs() {
        AttentionRouter r = new AttentionRouter();
        List<AttentionRouter.FocusItem> result = r.merge(List.of(), List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void mergePreservesImpulses() {
        AttentionRouter r = new AttentionRouter();
        Impulse imp = new Impulse(Impulse.Source.CURIOSITY, 0.8, 0.5, "x");
        var result = r.merge(List.of(imp), List.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).impulseContribution()).isEqualTo(0.4);
    }

    @Test
    void mergePreservesSaliencies() {
        AttentionRouter r = new AttentionRouter();
        SaliencyEngine eng = new SaliencyEngine();
        var sal = eng.score("text", new boolean[100]); // all zero, score 0
        var result = r.merge(List.of(), List.of(sal));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).saliencyContribution()).isEqualTo(0.0);
    }

    @Test
    void mergeSortsByScoreDescending() {
        AttentionRouter r = new AttentionRouter();
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] allOnes = new boolean[100];
        for (int i = 0; i < allOnes.length; i++) allOnes[i] = true;
        boolean[] half = new boolean[100];
        for (int i = 0; i < 50; i++) half[i] = true;
        var lowSal = eng.score("a", new boolean[100]);
        var highSal = eng.score("b", allOnes);

        var result = r.merge(List.of(), List.of(lowSal, highSal));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("saliency:b");
        assertThat(result.get(1).name()).isEqualTo("saliency:a");
    }

    @Test
    void mergeIsDeterministic() {
        AttentionRouter r = new AttentionRouter();
        SaliencyEngine eng = new SaliencyEngine();
        var s1 = eng.score("a", new boolean[100]);
        var s2 = eng.score("b", new boolean[100]);
        var a = r.merge(List.of(), List.of(s1, s2));
        var b = r.merge(List.of(), List.of(s1, s2));
        assertThat(a.toString()).isEqualTo(b.toString());
    }

    @Test
    void takeReturnsTopN() {
        AttentionRouter r = new AttentionRouter();
        SaliencyEngine eng = new SaliencyEngine();
        boolean[] allOnes = new boolean[100];
        for (int i = 0; i < allOnes.length; i++) allOnes[i] = true;
        var s1 = eng.score("a", allOnes);
        var s2 = eng.score("b", allOnes);
        var s3 = eng.score("c", allOnes);
        var merged = r.merge(List.of(), List.of(s1, s2, s3));
        var top2 = r.take(merged, 2);
        assertThat(top2).hasSize(2);
    }

    @Test
    void takeAllWhenUnderLimit() {
        AttentionRouter r = new AttentionRouter();
        SaliencyEngine eng = new SaliencyEngine();
        var s1 = eng.score("a", new boolean[10]);
        var merged = r.merge(List.of(), List.of(s1));
        var taken = r.take(merged, 5);
        assertThat(taken).hasSize(1);
    }

    @Test
    void focusItemFields() {
        AttentionRouter r = new AttentionRouter();
        var merged = r.merge(List.of(), List.of());
        assertThat(merged).isEmpty();
        // Sanity check on FocusItem structure
        var item = new AttentionRouter.FocusItem("X", 0.5, 0.4, 0.1, "saliency");
        assertThat(item.name()).isEqualTo("X");
        assertThat(item.mergedScore()).isEqualTo(0.5);
        assertThat(item.source()).isEqualTo("saliency");
    }
}
