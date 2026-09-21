package io.matrix.research;

import io.matrix.neuron.ConwayGameOfLife;
import io.matrix.neuron.PersistentHomology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 398 — DESIGN-48/50 implementations (Persistent Homology, Conway).
 */
class Exp398PersistentHomologyConwayTest {

    @Test
    void conwayStillLife() {
        // Block (2x2) is a still life
        boolean[][] block = {
                {true, true, false, false},
                {true, true, false, false},
                {false, false, false, false},
                {false, false, false, false}
        };
        boolean[][] next = ConwayGameOfLife.step(block);
        assertThat(next[0][0]).isTrue();
        assertThat(next[0][1]).isTrue();
        assertThat(next[1][0]).isTrue();
        assertThat(next[1][1]).isTrue();
        // Same as before — still life
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertThat(next[i][j]).isEqualTo(block[i][j]);
            }
        }
    }

    @Test
    void conwayBlinker() {
        // 3 in a row oscillates between horizontal and vertical
        boolean[][] blinkerH = {
                {false, false, false, false, false},
                {false, false, false, false, false},
                {false, true, true, true, false},
                {false, false, false, false, false},
                {false, false, false, false, false}
        };
        boolean[][] blinkerV = ConwayGameOfLife.step(blinkerH);
        // After 1 step: vertical
        assertThat(blinkerV[1][2]).isTrue();
        assertThat(blinkerV[2][2]).isTrue();
        assertThat(blinkerV[3][2]).isTrue();
        // After 2 steps: back to horizontal
        boolean[][] back = ConwayGameOfLife.step(blinkerV);
        assertThat(ConwayGameOfLife.liveCount(back)).isEqualTo(3);
    }

    @Test
    void conwayLiveCount() {
        boolean[][] g = {
                {true, false, true},
                {false, true, false},
                {true, false, true}
        };
        assertThat(ConwayGameOfLife.liveCount(g)).isEqualTo(5);
    }

    @Test
    void conwayStepNPure() {
        // Same input → same output (CONSTITUTION I)
        boolean[][] g = {
                {true, false, true, false},
                {false, true, false, true}
        };
        boolean[][] a = ConwayGameOfLife.stepN(g, 5);
        boolean[][] b = ConwayGameOfLife.stepN(g, 5);
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                assertThat(a[i][j]).isEqualTo(b[i][j]);
            }
        }
    }

    @Test
    void persistentHomologyConnectedComponents() {
        // 4 points in 2D, two pairs far apart
        List<PersistentHomology.Point> points = List.of(
                new PersistentHomology.Point(new double[]{0, 0}),
                new PersistentHomology.Point(new double[]{0.1, 0}),
                new PersistentHomology.Point(new double[]{10, 0}),
                new PersistentHomology.Point(new double[]{10.1, 0})
        );
        var diagram = PersistentHomology.compute0D(points, 100);
        // Should have at least 1 pair (merging of the two clusters)
        assertThat(diagram.pairs0D()).isNotEmpty();
        // The first merge has small distance (within clusters)
        assertThat(diagram.pairs0D().get(0).birth()).isLessThan(1.0);
    }

    @Test
    void persistentHomologyAllSamePoint() {
        // 5 points at same location — all merge at distance 0
        List<PersistentHomology.Point> points = List.of(
                new PersistentHomology.Point(new double[]{0, 0}),
                new PersistentHomology.Point(new double[]{0, 0}),
                new PersistentHomology.Point(new double[]{0, 0}),
                new PersistentHomology.Point(new double[]{0, 0}),
                new PersistentHomology.Point(new double[]{0, 0})
        );
        var diagram = PersistentHomology.compute0D(points, 100);
        // 4 merges all at distance 0
        assertThat(diagram.pairs0D()).hasSize(4);
        for (var p : diagram.pairs0D()) {
            assertThat(p.death()).isEqualTo(0.0);
        }
    }
}
