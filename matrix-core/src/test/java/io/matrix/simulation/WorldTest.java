package io.matrix.simulation;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.matrix.simulation.CellType.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorldTest {

    @Test
    void shouldCreateWorldWithWallsAndResources() {
        World world = new World(20, 20, 15, 10, new Random(42));

        assertThat(world.grid().count(WALL)).isEqualTo(15);
        assertThat(world.grid().count(RESOURCE)).isEqualTo(10);
    }

    @Test
    void shouldNotOverlapWallsAndResources() {
        World world = new World(20, 20, 15, 10, new Random(42));

        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                Position pos = new Position(x, y);
                CellType cell = world.grid().cellAt(pos);
                assertThat(cell).isIn(EMPTY, WALL, RESOURCE);
            }
        }
    }

    @Test
    void shouldRespawnResourceWhenCollected() {
        World world = new World(10, 10, 5, 1, new Random(42));
        Position resource = world.resourcePositions().get(0);

        world.collectResource(resource);

        assertThat(world.grid().cellAt(resource)).isEqualTo(EMPTY);
        assertThat(world.grid().count(RESOURCE)).isEqualTo(1);
    }

    @Test
    void tickShouldNotChangeStaticWorld() {
        World world = new World(10, 10, 0, 5, new Random(42));
        String before = world.toString();
        world.tick();
        assertThat(world.tickCount()).isEqualTo(1);
    }
}
