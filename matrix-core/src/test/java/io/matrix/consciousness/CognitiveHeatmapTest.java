package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveHeatmapTest {

    @Test
    void emptyProfilesReturnsEmptyHeatmap() {
        assertThat(CognitiveHeatmap.toHeatmap(new ArrayList<>())).isEmpty();
    }

    @Test
    void singleProfileReturnsOneRow() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        assertThat(heatmap.length).isEqualTo(1);
        assertThat(heatmap[0].length).isEqualTo(CognitiveHeatmap.fieldCount());
    }

    @Test
    void fieldNamesHasCorrectLength() {
        assertThat(CognitiveHeatmap.fieldNames().length).isEqualTo(CognitiveHeatmap.fieldCount());
    }

    @Test
    void columnMeansForConstantData() {
        double[][] data = {
            {0.5, 0.5, 0.5},
            {0.5, 0.5, 0.5},
            {0.5, 0.5, 0.5}
        };
        double[] means = CognitiveHeatmap.columnMeans(data);
        for (double m : means) {
            assertThat(m).isEqualTo(0.5);
        }
    }

    @Test
    void columnVariancesForConstantDataIsZero() {
        double[][] data = {
            {0.5, 0.5, 0.5},
            {0.5, 0.5, 0.5},
        };
        double[] variances = CognitiveHeatmap.columnVariances(data);
        for (double v : variances) {
            assertThat(v).isEqualTo(0.0);
        }
    }

    @Test
    void profileFieldsMappedToHeatmap() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.3));
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        // phiBinary = 0.3 should be at column 0
        assertThat(heatmap[0][0]).isEqualTo(0.3);
        // memristorConductance should be at column 11
        assertThat(heatmap[0][11]).isEqualTo(MemristorSwitch.conductance(0.5));
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
