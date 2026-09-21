package io.matrix.simulation;

import org.junit.jupiter.api.Test;

import static io.matrix.simulation.CellType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GridTest {

    @Test
    void shouldCreateGridWithGivenDimensions() {
        Grid grid = new Grid(10, 10);
        assertThat(grid.width()).isEqualTo(10);
        assertThat(grid.height()).isEqualTo(10);
    }

    @Test
    void newGridShouldBeAllEmpty() {
        Grid grid = new Grid(5, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                assertThat(grid.cellAt(new Position(x, y))).isEqualTo(EMPTY);
            }
        }
    }

    @Test
    void shouldSetAndGetCell() {
        Grid grid = new Grid(5, 5);
        Position pos = new Position(2, 3);
        grid.setCell(pos, WALL);
        assertThat(grid.cellAt(pos)).isEqualTo(WALL);
    }

    @Test
    void shouldWrapAround() {
        Grid grid = new Grid(10, 10);
        grid.setCell(new Position(0, 0), RESOURCE);
        grid.setCell(new Position(9, 9), WALL);

        assertThat(grid.cellAt(new Position(10, 10))).isEqualTo(RESOURCE);
        assertThat(grid.cellAt(new Position(-1, -1))).isEqualTo(WALL);
    }

    @Test
    void shouldRejectInvalidDimensions() {
        assertThatThrownBy(() -> new Grid(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Grid(10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Grid(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCountCellsOfType() {
        Grid grid = new Grid(4, 4);
        grid.setCell(new Position(0, 0), WALL);
        grid.setCell(new Position(1, 1), WALL);
        grid.setCell(new Position(2, 2), RESOURCE);

        assertThat(grid.count(WALL)).isEqualTo(2);
        assertThat(grid.count(RESOURCE)).isEqualTo(1);
        assertThat(grid.count(EMPTY)).isEqualTo(13);
    }

    @Test
    void shouldCheckIfCellIsWalkable() {
        Grid grid = new Grid(3, 3);
        grid.setCell(new Position(0, 0), WALL);

        assertThat(grid.isWalkable(new Position(0, 0))).isFalse();
        assertThat(grid.isWalkable(new Position(1, 1))).isTrue();
    }

    @Test
    void shouldListEmptyPositions() {
        Grid grid = new Grid(3, 3);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                grid.setCell(new Position(x, y), WALL);
            }
        }
        grid.setCell(new Position(1, 1), EMPTY);

        var empty = grid.emptyPositions();
        assertThat(empty).containsExactly(new Position(1, 1));
    }
}
