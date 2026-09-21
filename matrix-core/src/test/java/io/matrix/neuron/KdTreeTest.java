package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class KdTreeTest {

    @Test
    void emptyTreeHasSizeZero() {
        KdTree tree = KdTree.of(new double[0][]);
        assertThat(tree.size()).isEqualTo(0);
    }

    @Test
    void singlePointTreeHasSizeOne() {
        KdTree tree = KdTree.of(new double[][]{{1.0, 2.0}});
        assertThat(tree.size()).isEqualTo(1);
    }

    @Test
    void nearestReturnsClosestPoint() {
        KdTree tree = KdTree.of(new double[][]{
                {0.0, 0.0}, {1.0, 0.0}, {0.0, 1.0}, {1.0, 1.0}
        });
        int[] nearest = tree.nearest(new double[]{0.1, 0.1}, 1);
        assertThat(nearest).hasSize(1);
        assertThat(nearest[0]).isEqualTo(0); // closest to (0.1, 0.1) is (0,0)
    }

    @Test
    void nearestReturnsTopK() {
        KdTree tree = KdTree.of(new double[][]{
                {0.0, 0.0}, {10.0, 0.0}, {0.0, 10.0}, {10.0, 10.0}
        });
        int[] top3 = tree.nearest(new double[]{0.0, 0.0}, 3);
        assertThat(top3).hasSize(3);
        // First should be (0,0), then either (10,0) or (0,10) as next closest
        assertThat(top3[0]).isEqualTo(0);
    }

    @Test
    void nearestRejectsBadInputs() {
        KdTree tree = KdTree.of(new double[][]{{1.0, 2.0}});
        // KdTree may throw NullPointerException, ArrayIndexOutOfBoundsException,
        // or IllegalArgumentException; just verify it throws
        assertThatThrownBy(() -> tree.nearest(null, 1))
                .isInstanceOfAny(RuntimeException.class);
        assertThatThrownBy(() -> tree.nearest(new double[0], 1))
                .isInstanceOfAny(RuntimeException.class);
    }

    @Test
    void nearestKGreaterThanSizeReturnsAll() {
        KdTree tree = KdTree.of(new double[][]{{0.0, 0.0}, {1.0, 1.0}});
        int[] all = tree.nearest(new double[]{0.5, 0.5}, 10);
        assertThat(all).hasSize(2);
    }
}
