package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveEarlyExitTest {

    @Test
    void nullReturnsInput() {
        CognitiveEarlyExit.EarlyExitResult r =
            CognitiveEarlyExit.process(null, 3, 0.5, null, null);
        assertThat(r.layersUsed()).isEqualTo(0);
        assertThat(r.exited()).isTrue();
    }

    @Test
    void zeroLayersReturnsInput() {
        CognitiveEarlyExit.EarlyExitResult r =
            CognitiveEarlyExit.process(makeProfile(0.5), 0, 0.5, null, null);
        assertThat(r.layersUsed()).isEqualTo(0);
    }

    @Test
    void exitsAtFirstLayerIfConfidenceHigh() {
        CognitiveEarlyExit.CognitiveLayer layer = (p, i) ->
            new CognitiveGenesisProfile(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9,
                50.0, 0.9, 0.9, 2, 0.9, 2.0);
        CognitiveEarlyExit.ConfidenceEstimator est = p -> p.phiBinary();
        CognitiveEarlyExit.EarlyExitResult r =
            CognitiveEarlyExit.process(makeProfile(0.5), 5, 0.7, layer, est);
        assertThat(r.exited()).isTrue();
        assertThat(r.layersUsed()).isEqualTo(1);
    }

    @Test
    void runsAllLayersIfConfidenceLow() {
        CognitiveEarlyExit.CognitiveLayer layer = (p, i) ->
            new CognitiveGenesisProfile(0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.3,
                50.0, 0.3, 0.3, 2, 0.3, 2.0);
        CognitiveEarlyExit.ConfidenceEstimator est = p -> p.phiBinary();
        CognitiveEarlyExit.EarlyExitResult r =
            CognitiveEarlyExit.process(makeProfile(0.3), 5, 0.7, layer, est);
        assertThat(r.exited()).isFalse();
        assertThat(r.layersUsed()).isEqualTo(5);
    }

    @Test
    void confidenceHistoryTracked() {
        CognitiveEarlyExit.CognitiveLayer layer = (p, i) -> p;
        CognitiveEarlyExit.ConfidenceEstimator est = p -> 0.5;
        CognitiveEarlyExit.EarlyExitResult r =
            CognitiveEarlyExit.process(makeProfile(0.5), 3, 0.7, layer, est);
        assertThat(r.confidenceHistory()).hasSize(3);
    }

    @Test
    void computeSavedReturnsValue() {
        List<CognitiveEarlyExit.EarlyExitResult> results = new ArrayList<>();
        results.add(new CognitiveEarlyExit.EarlyExitResult(
            makeProfile(0.5), 2, 0.8, true, new ArrayList<>()));
        results.add(new CognitiveEarlyExit.EarlyExitResult(
            makeProfile(0.5), 5, 0.5, false, new ArrayList<>()));
        double saved = CognitiveEarlyExit.computeSaved(results, 5);
        assertThat(saved).isBetween(0.0, 1.0);
    }

    @Test
    void computeSavedEmpty() {
        assertThat(CognitiveEarlyExit.computeSaved(new ArrayList<>(), 5)).isEqualTo(0.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
