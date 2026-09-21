package io.matrix.simulation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationTypesTest {

    @Test
    void shouldCreatePosition() {
        Position p = new Position(3, 5);
        assertThat(p.x()).isEqualTo(3);
        assertThat(p.y()).isEqualTo(5);
    }

    @Test
    void shouldEqualPositions() {
        assertThat(new Position(1, 2)).isEqualTo(new Position(1, 2));
        assertThat(new Position(1, 2)).isNotEqualTo(new Position(2, 1));
    }

    @Test
    void shouldComputeDirectionDeltas() {
        assertThat(Direction.N.dx()).isEqualTo(0);
        assertThat(Direction.N.dy()).isEqualTo(-1);
        assertThat(Direction.S.dx()).isEqualTo(0);
        assertThat(Direction.S.dy()).isEqualTo(1);
        assertThat(Direction.W.dx()).isEqualTo(-1);
        assertThat(Direction.W.dy()).isEqualTo(0);
        assertThat(Direction.E.dx()).isEqualTo(1);
        assertThat(Direction.E.dy()).isEqualTo(0);
        assertThat(Direction.STAY.dx()).isEqualTo(0);
        assertThat(Direction.STAY.dy()).isEqualTo(0);
    }

    @Test
    void shouldComputeSimulationResultRawScore() {
        SimulationResult result = new SimulationResult(100, 5, 3, 200, 150);

        assertThat(result.steps()).isEqualTo(100);
        assertThat(result.foodCollected()).isEqualTo(5);
        assertThat(result.wallCollisions()).isEqualTo(3);
        assertThat(result.initialEnergy()).isEqualTo(200);
        assertThat(result.finalEnergy()).isEqualTo(150);
        assertThat(result.rawScore()).isEqualTo(5 * 100 - 3 * 10 + 150);
    }

    @Test
    void shouldComputeRawScoreWithNegative() {
        SimulationResult result = new SimulationResult(50, 0, 10, 100, 0);

        assertThat(result.rawScore()).isEqualTo(0 - 100 + 0);
    }

    @Test
    void shouldComputeRawScoreWithNoFood() {
        SimulationResult result = new SimulationResult(200, 0, 0, 100, 50);

        assertThat(result.rawScore()).isEqualTo(50);
    }

    @Test
    void shouldEncodeCellTypes() {
        assertThat(CellType.EMPTY.bits()).isEqualTo(0);
        assertThat(CellType.WALL.bits()).isEqualTo(1);
        assertThat(CellType.RESOURCE.bits()).isEqualTo(2);
    }

    @Test
    void shouldDecodeCellTypesFromBits() {
        assertThat(CellType.fromBits(0)).isEqualTo(CellType.EMPTY);
        assertThat(CellType.fromBits(1)).isEqualTo(CellType.WALL);
        assertThat(CellType.fromBits(2)).isEqualTo(CellType.RESOURCE);
    }

    @Test
    void shouldDefaultToWallForInvalidBits() {
        assertThat(CellType.fromBits(3)).isEqualTo(CellType.WALL);
        assertThat(CellType.fromBits(99)).isEqualTo(CellType.WALL);
        assertThat(CellType.fromBits(-1)).isEqualTo(CellType.WALL);
    }

    @Test
    void shouldRoundtripCellTypeBits() {
        for (CellType type : CellType.values()) {
            assertThat(CellType.fromBits(type.bits())).isEqualTo(type);
        }
    }
}
