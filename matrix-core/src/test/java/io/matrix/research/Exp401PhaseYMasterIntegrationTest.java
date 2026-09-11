package io.matrix.research;

import io.matrix.neuron.AStarSearch;
import io.matrix.neuron.ConwayGameOfLife;
import io.matrix.neuron.EchoStateProperty;
import io.matrix.neuron.GillespieSimulator;
import io.matrix.neuron.PersistentHomology;
import io.matrix.neuron.QLearning;
import io.matrix.neuron.RandomForest;
import io.matrix.neuron.SARSA;
import io.matrix.neuron.SimplexSolver;
import io.matrix.neuron.TSNE;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 401 — Phase Y final integration: exercises all 10 new algorithms
 * (DESIGN-44..53) in a single test, ~1s.
 */
class Exp401PhaseYMasterIntegrationTest {

    @Test
    void allTenNewAlgorithmsWorkTogether() {
        // ===== 1. A* (Hart 1968) =====
        Map<Integer, AStarSearch.Node> nodes = new HashMap<>();
        nodes.put(0, new AStarSearch.Node(0, 0, 0));
        nodes.put(1, new AStarSearch.Node(1, 1, 0));
        nodes.put(2, new AStarSearch.Node(2, 2, 0));
        var astar = AStarSearch.search(0, 2, nodes,
                List.of(new AStarSearch.Edge(0, 1, 1.0),
                        new AStarSearch.Edge(1, 2, 1.0)));
        assertThat(astar.path()).hasSize(3);

        // ===== 2. Simplex LP (Dantzig 1947) =====
        var lp = SimplexSolver.solve(new SimplexSolver.LinearProgram(
                new double[]{1, 1}, new double[][]{{1, 1}}, new double[]{1}));
        assertThat(lp.objectiveValue()).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(0.2));

        // ===== 3. Q-Learning (Watkins 1989) =====
        var ql = QLearning.update(new double[][]{{0.5, 0.0}, {0.7, 0.0}},
                0, 0, 1.0, 1, 0.1, 0.9);
        assertThat(ql.newQ()[0][0]).isCloseTo(0.613,
                org.assertj.core.data.Offset.offset(1e-9));

        // ===== 4. Gillespie SSA (1976) =====
        GillespieSimulator.Trajectory traj = GillespieSimulator.simulate(
                new int[]{100},
                List.of(new GillespieSimulator.Reaction("decay", new int[]{-1}, 1.0)),
                50.0, 500, 0xCAFE);
        assertThat(traj.finalPopulation()[0]).isLessThan(100);

        // ===== 5. Persistent Homology (Edelsbrunner 2010) =====
        var diagram = PersistentHomology.compute0D(
                List.of(
                        new PersistentHomology.Point(new double[]{0, 0}),
                        new PersistentHomology.Point(new double[]{0.1, 0}),
                        new PersistentHomology.Point(new double[]{10, 0}),
                        new PersistentHomology.Point(new double[]{10.1, 0})),
                100);
        assertThat(diagram.pairs0D()).isNotEmpty();

        // ===== 6. Random Forest (Breiman 2001) =====
        List<RandomForest.Sample> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(new RandomForest.Sample(
                    new double[]{i % 3, i % 5}, i % 2));
        }
        var forest = RandomForest.train(data, 5, 4, 0xCAFE);
        assertThat(forest).hasSize(5);

        // ===== 7. Conway Game of Life (Gardner 1970) =====
        boolean[][] block = {
                {true, true, false, false},
                {true, true, false, false},
                {false, false, false, false},
                {false, false, false, false}};
        var next = ConwayGameOfLife.step(block);
        assertThat(ConwayGameOfLife.liveCount(next)).isEqualTo(4);

        // ===== 8. Echo State Property (Jaeger 2001) =====
        double[][] W = {{0.3, 0.1}, {0.0, 0.2}};
        assertThat(EchoStateProperty.hasEchoStateProperty(W, 0.95)).isTrue();

        // ===== 9. SARSA (Rummery 1994) =====
        var sa = SARSA.update(new double[][]{{0.5, 0.0}, {0.7, 0.4}},
                0, 0, 1.0, 1, 1, 0.1, 0.9);
        assertThat(sa[0][0]).isCloseTo(0.586,
                org.assertj.core.data.Offset.offset(1e-9));

        // ===== 10. t-SNE (van der Maaten 2008) =====
        double[][] X = {
                {0, 0, 0, 0}, {0.1, 0.1, 0.1, 0.1},
                {10, 10, 10, 10}, {10.1, 10.1, 10.1, 10.1}};
        var proj = TSNE.project(X, 5.0, 30, 100.0, 0xCAFE);
        assertThat(proj.y().length).isEqualTo(4);
        assertThat(proj.y()[0].length).isEqualTo(2);

        System.out.println("[Exp401] ALL 10 NEW ALGORITHMS (DESIGN-44..53) PASS");
    }

    // Local imports
    private interface Map<K, V> extends java.util.Map<K, V> {}
    @SuppressWarnings("unused")
    private static class HashMap<K, V> extends java.util.HashMap<K, V>
            implements Map<K, V> {}
}
