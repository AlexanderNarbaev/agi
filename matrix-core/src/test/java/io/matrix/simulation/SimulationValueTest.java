package io.matrix.simulation;

import org.junit.jupiter.api.Test;

import static io.matrix.simulation.CellType.EMPTY;
import static io.matrix.simulation.CellType.RESOURCE;
import static io.matrix.simulation.CellType.WALL;
import static io.matrix.simulation.Direction.*;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationValueTest {

    @Test
    void positionShouldStoreCoordinates() {
        Position p = new Position(3, 7);
        assertThat(p.x()).isEqualTo(3);
        assertThat(p.y()).isEqualTo(7);
    }

    @Test
    void positionShouldEqualSameCoordinates() {
        assertThat(new Position(1, 2)).isEqualTo(new Position(1, 2));
        assertThat(new Position(1, 2)).isNotEqualTo(new Position(2, 1));
    }

    @Test
    void positionShouldHashConsistently() {
        assertThat(new Position(3, 5).hashCode()).isEqualTo(new Position(3, 5).hashCode());
    }

    @Test
    void directionShouldHaveFourCardinalsAndStay() {
        assertThat(Direction.values()).containsExactly(N, S, W, E, STAY);
    }

    @Test
    void directionShouldComputeDelta() {
        assertThat(N.dx()).isEqualTo(0);
        assertThat(N.dy()).isEqualTo(-1);
        assertThat(S.dx()).isEqualTo(0);
        assertThat(S.dy()).isEqualTo(1);
        assertThat(W.dx()).isEqualTo(-1);
        assertThat(W.dy()).isEqualTo(0);
        assertThat(E.dx()).isEqualTo(1);
        assertThat(E.dy()).isEqualTo(0);
        assertThat(STAY.dx()).isEqualTo(0);
        assertThat(STAY.dy()).isEqualTo(0);
    }

    @Test
    void cellTypeShouldHaveThreeValues() {
        assertThat(CellType.values()).containsExactly(EMPTY, WALL, RESOURCE);
    }

    @Test
    void cellTypeShouldEncodeToBits() {
        assertThat(EMPTY.bits()).isEqualTo(0);
        assertThat(WALL.bits()).isEqualTo(1);
        assertThat(RESOURCE.bits()).isEqualTo(2);
    }

    @Test
    void cellTypeShouldDecodeFromBits() {
        assertThat(CellType.fromBits(0)).isEqualTo(EMPTY);
        assertThat(CellType.fromBits(1)).isEqualTo(WALL);
        assertThat(CellType.fromBits(2)).isEqualTo(RESOURCE);
    }

    @Test
    void cellTypeFromBitsShouldDefaultToWallForOutOfRange() {
        assertThat(CellType.fromBits(3)).isEqualTo(WALL);
        assertThat(CellType.fromBits(-1)).isEqualTo(WALL);
    }
}
