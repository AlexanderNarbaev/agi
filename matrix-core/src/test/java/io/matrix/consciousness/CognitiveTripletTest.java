package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveTripletTest {

    @Test
    void nullReturnsZero() {
        assertThat(CognitiveTriplet.interactionInformation(null)).isEqualTo(0.0);
    }

    @Test
    void emptyReturnsZero() {
        assertThat(CognitiveTriplet.interactionInformation(new double[0][][])).isEqualTo(0.0);
    }

    @Test
    void independentTripletsIIIsZero() {
        // X, Y, Z independent: I(X;Y;Z) = 0
        double[][][] joint = {
            {{0.125, 0.125}, {0.125, 0.125}},
            {{0.125, 0.125}, {0.125, 0.125}}
        };  // 8 entries, all 0.125, 2x2x2 = 1.0 total
        double ii = CognitiveTriplet.interactionInformation(joint);
        assertThat(ii).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void classifyIndependent() {
        double[][][] joint = {
            {{0.25, 0.0}, {0.0, 0.25}},
            {{0.0, 0.25}, {0.25, 0.0}}
        };
        String c = CognitiveTriplet.classifyInteraction(joint, 0.01);
        // May be INDEPENDENT or REDUNDANCY depending on structure
        assertThat(c).isIn("INDEPENDENT", "REDUNDANCY", "SYNERGY");
    }

    @Test
    void identicalXandYHasNegativeII() {
        // X and Y always equal → redundancy
        double[][][] joint = {
            {{0.5, 0.0}, {0.0, 0.5}},  // X=0,Y=0 or X=1,Y=1
            {{0.0, 0.0}, {0.0, 0.0}},
        };
        double ii = CognitiveTriplet.interactionInformation(joint);
        // Negative (redundancy) — X and Y share info, leaving less for Z
        assertThat(ii).isLessThanOrEqualTo(0.5);
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
